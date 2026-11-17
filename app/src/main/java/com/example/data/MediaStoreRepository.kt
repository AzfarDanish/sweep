package com.example.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.example.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class MediaScanResult(
    val activeMedia: List<MediaItem>,
    val trashedMedia: List<MediaItem>,
)

class MediaStoreRepository(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver
    private val trashBinStore = TrashBinStore(appContext)

    fun observeMediaChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
        contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    suspend fun scanAllMedia(): MediaScanResult = withContext(Dispatchers.IO) {
        val nonOsTrashedImages = queryCollection(
            collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            isVideo = false,
            trashedOnly = false,
        )
        val nonOsTrashedVideos = queryCollection(
            collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            isVideo = true,
            trashedOnly = false,
        )
        val allNonOsTrashed = (nonOsTrashedImages + nonOsTrashedVideos)
            .sortedWith(
                compareByDescending<MediaItem> { it.effectiveTimestampMillis }
                    .thenByDescending { it.id },
            )

        val osTrashed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val images = queryCollection(
                collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                isVideo = false,
                trashedOnly = true,
            )
            val videos = queryCollection(
                collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                isVideo = true,
                trashedOnly = true,
            )
            (images + videos)
        } else {
            emptyList()
        }

        // Prune any URI from app TrashBinStore that no longer exists on the device
        val existingNonOsUris = allNonOsTrashed.map { it.contentUri }.toSet()
        trashBinStore.pruneMissingUris(existingNonOsUris)

        val appTrashedUris = trashBinStore.getTrashedUris()
        val activeMedia = allNonOsTrashed.filterNot { it.contentUri in appTrashedUris }
        val appTrashedItems = allNonOsTrashed
            .filter { it.contentUri in appTrashedUris }
            .map { it.copy(isTrashed = true, isOsTrashed = false) }

        val combinedTrashed = (appTrashedItems + osTrashed)
            .distinctBy { it.contentUri }
            .sortedByDescending { it.dateModifiedMillis }

        MediaScanResult(
            activeMedia = activeMedia,
            trashedMedia = combinedTrashed,
        )
    }

    /**
     * Moves existing media into Sweep's Trash Bin immediately without a confirmation dialog.
     * Attempts a silent OS-level IS_TRASHED update when permitted by scoped storage
     * (e.g. app-owned media or MANAGE_MEDIA), and otherwise tracks the verified URI in TrashBinStore.
     */
    suspend fun moveToTrashImmediate(
        uris: List<Uri>,
        skipExistenceCheck: Boolean = false,
    ): List<Uri> = withContext(Dispatchers.IO) {
        val distinctUris = uris.distinct()
        if (distinctUris.isEmpty()) return@withContext emptyList()
        val existing = if (skipExistenceCheck) distinctUris else filterExistingUris(distinctUris)
        if (existing.isEmpty()) return@withContext emptyList()

        val canSilentOsTrash = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            MediaStore.canManageMedia(appContext)

        if (canSilentOsTrash) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_TRASHED, 1)
            }
            val semaphore = Semaphore(PARALLEL_IO_CONCURRENCY)
            val remainingForStore = coroutineScope {
                existing.map { uri ->
                    async {
                        val updated = semaphore.withPermit {
                            runCatching {
                                contentResolver.update(uri, values, null, null) > 0
                            }.getOrDefault(false)
                        }
                        if (updated) null else uri
                    }
                }.awaitAll().filterNotNull()
            }
            if (remainingForStore.isNotEmpty()) {
                trashBinStore.addToTrash(remainingForStore)
            }
        } else {
            trashBinStore.addToTrash(existing)
        }
        existing
    }

    /**
     * Restores app-trashed URIs immediately and silently restores OS-trashed URIs when permitted.
     * Returns any OS-trashed URIs that require system IntentSender authorization to un-trash.
     */
    suspend fun restoreImmediateOrNeedsOsAuth(items: List<MediaItem>): Pair<List<Uri>, List<Uri>> =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) return@withContext emptyList<Uri>() to emptyList()
            val appTrackedUris = items.filter { !it.isOsTrashed }.map { it.contentUri }
            val osTrashedUris = items.filter { it.isOsTrashed }.map { it.contentUri }

            val restoredSilently = mutableListOf<Uri>()
            if (appTrackedUris.isNotEmpty()) {
                val existing = filterExistingUris(appTrackedUris)
                trashBinStore.removeFromTrash(appTrackedUris)
                restoredSilently.addAll(existing)
            }

            val needsOsConfirmation = mutableListOf<Uri>()
            if (osTrashedUris.isNotEmpty()) {
                val canSilent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    MediaStore.canManageMedia(appContext)
                if (canSilent) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_TRASHED, 0)
                    }
                    for (uri in osTrashedUris) {
                        val updated = runCatching {
                            contentResolver.update(uri, values, null, null) > 0
                        }.getOrDefault(false)
                        if (updated) {
                            restoredSilently.add(uri)
                        } else {
                            needsOsConfirmation.add(uri)
                        }
                    }
                } else {
                    needsOsConfirmation.addAll(filterExistingUris(osTrashedUris))
                }
            }
            restoredSilently to needsOsConfirmation
        }

    fun removeFromAppTrashStore(uris: List<Uri>) {
        trashBinStore.removeFromTrash(uris)
    }

    fun getAppTrashedUris(): Set<Uri> = trashBinStore.getTrashedUris()

    suspend fun filterExistingUris(uris: List<Uri>): List<Uri> = withContext(Dispatchers.IO) {
        val distinct = uris.distinct()
        if (distinct.isEmpty()) return@withContext emptyList()
        if (distinct.size == 1) {
            val uri = distinct.first()
            return@withContext if (uriExists(uri)) listOf(uri) else emptyList()
        }

        val existingSet = HashSet<Uri>(distinct.size)
        val imageUris = ArrayList<Pair<Long, Uri>>()
        val videoUris = ArrayList<Pair<Long, Uri>>()
        val otherUris = ArrayList<Uri>()

        for (uri in distinct) {
            val id = runCatching { ContentUris.parseId(uri) }.getOrNull()
            when {
                id != null && uri.toString().startsWith(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString()) ->
                    imageUris.add(id to uri)
                id != null && uri.toString().startsWith(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString()) ->
                    videoUris.add(id to uri)
                else -> otherUris.add(uri)
            }
        }

        if (imageUris.isNotEmpty()) {
            queryExistingIdsInCollection(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageUris,
                existingSet,
            )
        }
        if (videoUris.isNotEmpty()) {
            queryExistingIdsInCollection(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoUris,
                existingSet,
            )
        }
        if (otherUris.isNotEmpty()) {
            val semaphore = Semaphore(PARALLEL_IO_CONCURRENCY)
            coroutineScope {
                otherUris.map { uri ->
                    async {
                        if (semaphore.withPermit { uriExists(uri) }) uri else null
                    }
                }.awaitAll().filterNotNullTo(existingSet)
            }
        }

        distinct.filter { it in existingSet }
    }

    private fun queryExistingIdsInCollection(
        collectionUri: Uri,
        idUriPairs: List<Pair<Long, Uri>>,
        outExisting: MutableSet<Uri>,
    ) {
        val uriById = HashMap<Long, Uri>(idUriPairs.size)
        for ((id, uri) in idUriPairs) {
            uriById[id] = uri
        }
        for (chunk in idUriPairs.chunked(SQLITE_IN_CHUNK_SIZE)) {
            val placeholders = chunk.joinToString(",") { "?" }
            val selection = "${MediaStore.MediaColumns._ID} IN ($placeholders)"
            val selectionArgs = chunk.map { it.first.toString() }.toTypedArray()
            val queried = runCatching {
                contentResolver.query(
                    collectionUri,
                    arrayOf(MediaStore.MediaColumns._ID),
                    selection,
                    selectionArgs,
                    null,
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    if (idCol >= 0) {
                        while (cursor.moveToNext()) {
                            val foundId = cursor.getLong(idCol)
                            uriById[foundId]?.let { outExisting.add(it) }
                        }
                        true
                    } else {
                        false
                    }
                } ?: false
            }.getOrDefault(false)

            // Fallback per-URI only if the provider rejected IN selection (e.g., strict custom mock)
            if (!queried) {
                for ((_, uri) in chunk) {
                    if (uriExists(uri)) outExisting.add(uri)
                }
            }
        }
    }

    private fun uriExists(uri: Uri): Boolean {
        return runCatching {
            contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                null,
                null,
                null,
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        }.getOrDefault(false)
    }

    fun buildTrashIntentSender(uris: List<Uri>, trash: Boolean): IntentSender? {
        if (uris.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                MediaStore.createTrashRequest(contentResolver, uris, trash).intentSender
            }.getOrNull()
        } else {
            null
        }
    }

    fun buildDeleteIntentSender(uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                MediaStore.createDeleteRequest(contentResolver, uris).intentSender
            }.getOrNull()
        } else {
            null
        }
    }

    /**
     * Moves a single media item into [targetRelativePath], a MediaStore RELATIVE_PATH such as
     * "Pictures/Trips/". Returns true only when the row was actually updated.
     */
    suspend fun moveToAlbum(uri: Uri, targetRelativePath: String): Boolean =
        withContext(Dispatchers.IO) {
            val relativePath = targetRelativePath.trim().let { path ->
                when {
                    path.isEmpty() -> return@withContext false
                    path.endsWith("/") -> path
                    else -> "$path/"
                }
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            runCatching { contentResolver.update(uri, values, null, null) > 0 }
                .getOrDefault(false)
        }

    /**
     * Requests one-shot system write consent for [uris] (API 30+). This is required before a
     * RELATIVE_PATH update can touch media owned by another app.
     */
    fun buildWriteIntentSender(uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                MediaStore.createWriteRequest(contentResolver, uris).intentSender
            }.getOrNull()
        } else {
            null
        }
    }

    /**
     * Attempts direct deletion via ContentResolver using bounded parallelism (succeeds without a
     * system prompt on API < 30, for app-owned media, or when MANAGE_MEDIA is granted). Returns
     * the pair (deletedDirectly, failedOrProtectedUris).
     */
    suspend fun deleteDirectlyWherePermitted(uris: List<Uri>): Pair<List<Uri>, List<Uri>> =
        withContext(Dispatchers.IO) {
            val distinctUris = uris.distinct()
            if (distinctUris.isEmpty()) return@withContext emptyList<Uri>() to emptyList()

            val semaphore = Semaphore(PARALLEL_IO_CONCURRENCY)
            val outcomes = coroutineScope {
                distinctUris.map { uri ->
                    async {
                        val rows = semaphore.withPermit {
                            runCatching { contentResolver.delete(uri, null, null) }.getOrDefault(0)
                        }
                        uri to (rows > 0)
                    }
                }.awaitAll()
            }
            val deleted = ArrayList<Uri>(distinctUris.size)
            val failed = ArrayList<Uri>()
            for ((uri, ok) in outcomes) {
                if (ok) deleted.add(uri) else failed.add(uri)
            }
            deleted to failed
        }

    suspend fun deleteDirectlyLegacy(uris: List<Uri>): Boolean = withContext(Dispatchers.IO) {
        val (_, failed) = deleteDirectlyWherePermitted(uris)
        failed.isEmpty()
    }

    companion object {
        private const val PARALLEL_IO_CONCURRENCY = 6
        private const val SQLITE_IN_CHUNK_SIZE = 450
    }

    private fun queryCollection(
        collectionUri: Uri,
        isVideo: Boolean,
        trashedOnly: Boolean,
    ): List<MediaItem> {
        val items = mutableListOf<MediaItem>()

        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.BUCKET_ID)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            }
            if (isVideo) {
                add(MediaStore.Video.VideoColumns.DURATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.MediaColumns.IS_TRASHED)
            }
        }.toTypedArray()

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = Bundle().apply {
                putInt(
                    MediaStore.QUERY_ARG_MATCH_TRASHED,
                    if (trashedOnly) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE,
                )
                putStringArray(
                    ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(
                        MediaStore.MediaColumns.DATE_TAKEN,
                        MediaStore.MediaColumns.DATE_MODIFIED,
                    ),
                )
                putInt(
                    ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    ContentResolver.QUERY_SORT_DIRECTION_DESCENDING,
                )
            }
            runCatching {
                contentResolver.query(collectionUri, projection, queryArgs, null)
            }.getOrNull()
        } else {
            if (trashedOnly) return emptyList()
            runCatching {
                contentResolver.query(
                    collectionUri,
                    projection,
                    null,
                    null,
                    "${MediaStore.MediaColumns.DATE_TAKEN} DESC, ${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
                )
            }.getOrNull()
        }

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val takenCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
            val modifiedCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val widthCol = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val heightCol = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val bucketIdCol = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameCol = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            } else -1
            val durationCol = if (isVideo) {
                c.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
            } else -1
            val trashedCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                c.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
            } else -1

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(collectionUri, id)
                val displayName = if (nameCol >= 0) c.getString(nameCol).orEmpty() else ""
                val mimeType = if (mimeCol >= 0) c.getString(mimeCol).orEmpty() else ""
                val dateTaken = if (takenCol >= 0) c.getLong(takenCol) else 0L
                val dateModifiedSec = if (modifiedCol >= 0) c.getLong(modifiedCol) else 0L
                val sizeBytes = if (sizeCol >= 0) c.getLong(sizeCol) else 0L
                val width = if (widthCol >= 0) c.getInt(widthCol) else 0
                val height = if (heightCol >= 0) c.getInt(heightCol) else 0
                val bucketId = if (bucketIdCol >= 0) c.getLong(bucketIdCol) else 0L
                val bucketName = if (bucketNameCol >= 0) {
                    c.getString(bucketNameCol)?.takeIf { it.isNotBlank() } ?: "Storage"
                } else {
                    "Storage"
                }
                val relPath = if (relPathCol >= 0) c.getString(relPathCol).orEmpty() else ""
                val duration = if (durationCol >= 0) c.getLong(durationCol) else 0L
                val isTrashed = if (trashedCol >= 0) c.getInt(trashedCol) == 1 else false

                if (trashedOnly && !isTrashed) continue
                if (!trashedOnly && isTrashed) continue

                items.add(
                    MediaItem(
                        id = id,
                        contentUri = contentUri,
                        displayName = displayName.ifBlank { "Untitled" },
                        mimeType = mimeType.ifBlank { if (isVideo) "video/*" else "image/*" },
                        dateTakenMillis = dateTaken,
                        dateModifiedMillis = dateModifiedSec * 1000L,
                        sizeBytes = sizeBytes,
                        durationMillis = duration,
                        width = width,
                        height = height,
                        bucketId = bucketId,
                        bucketName = bucketName,
                        relativePath = relPath,
                        isVideo = isVideo,
                        isTrashed = isTrashed,
                        isOsTrashed = isTrashed,
                    ),
                )
            }
        }
        return items
    }
}
