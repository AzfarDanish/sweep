package com.example.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.example.model.FileCategory
import com.example.model.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SafFolderRoot(
    val uri: Uri,
    val name: String,
)

class SafFilesRepository(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver
    private val trashBinStore = TrashBinStore(appContext)

    fun getAuthorizedFolders(): List<SafFolderRoot> {
        return contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .mapNotNull { perm ->
                val doc = DocumentFile.fromTreeUri(appContext, perm.uri) ?: return@mapNotNull null
                if (!doc.exists()) return@mapNotNull null
                SafFolderRoot(
                    uri = perm.uri,
                    name = doc.name?.takeIf { it.isNotBlank() } ?: "Folder",
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    fun persistFolderPermission(treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            contentResolver.takePersistableUriPermission(treeUri, flags)
        }.onFailure {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    fun releaseFolderPermission(treeUri: Uri) {
        runCatching {
            contentResolver.releasePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure {
            runCatching {
                contentResolver.releasePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    suspend fun loadCategoryFiles(category: FileCategory): List<FileEntry> = withContext(Dispatchers.IO) {
        val trashedUris = trashBinStore.getTrashedUris()
        val entries = when (category) {
            FileCategory.IMAGES -> queryMediaCategory(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                isImage = true,
                isVideo = false,
            )
            FileCategory.VIDEOS -> queryMediaCategory(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                isImage = false,
                isVideo = true,
            )
            FileCategory.AUDIO -> queryAudioFiles()
            FileCategory.DOCUMENTS -> queryDocumentFiles()
            FileCategory.DOWNLOADS -> queryDownloads()
        }
        entries.filterNot { it.uri in trashedUris }
    }

    suspend fun loadCategoryCounts(): Map<FileCategory, Int> = withContext(Dispatchers.IO) {
        FileCategory.entries.associateWith { category ->
            loadCategoryFiles(category).size
        }
    }

    suspend fun listSafDirectory(folderUri: Uri): List<FileEntry> = withContext(Dispatchers.IO) {
        val dir = DocumentFile.fromTreeUri(appContext, folderUri)
            ?: DocumentFile.fromSingleUri(appContext, folderUri)
            ?: return@withContext emptyList()
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()

        dir.listFiles().mapNotNull { child ->
            val name = child.name ?: return@mapNotNull null
            val mime = child.type.orEmpty()
            val isDir = child.isDirectory
            val isImg = !isDir && mime.startsWith("image/")
            val isVid = !isDir && mime.startsWith("video/")
            FileEntry(
                id = child.uri.toString(),
                uri = child.uri,
                mediaStoreId = null,
                name = name,
                sizeBytes = if (isDir) 0L else child.length(),
                lastModifiedMillis = child.lastModified(),
                mimeType = if (isDir) "vnd.android.document/directory" else mime.ifBlank { "*/*" },
                isDirectory = isDir,
                isMediaStoreBacked = false,
                isImage = isImg,
                isVideo = isVid,
                relativePath = "",
            )
        }
    }

    private fun queryMediaCategory(
        collectionUri: Uri,
        isImage: Boolean,
        isVideo: Boolean,
    ): List<FileEntry> {
        val entries = mutableListOf<FileEntry>()
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            }
            if (isVideo) add(MediaStore.Video.VideoColumns.DURATION)
        }.toTypedArray()

        querySafe(collectionUri, projection)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val modCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            } else -1
            val durCol = if (isVideo) c.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val uri = ContentUris.withAppendedId(collectionUri, id)
                entries.add(
                    FileEntry(
                        id = uri.toString(),
                        uri = uri,
                        mediaStoreId = id,
                        name = if (nameCol >= 0) c.getString(nameCol).orEmpty().ifBlank { "Untitled" } else "Untitled",
                        sizeBytes = if (sizeCol >= 0) c.getLong(sizeCol) else 0L,
                        lastModifiedMillis = if (modCol >= 0) c.getLong(modCol) * 1000L else 0L,
                        mimeType = if (mimeCol >= 0) c.getString(mimeCol).orEmpty() else "*/*",
                        isDirectory = false,
                        isMediaStoreBacked = true,
                        isImage = isImage,
                        isVideo = isVideo,
                        durationMillis = if (durCol >= 0) c.getLong(durCol) else 0L,
                        relativePath = if (relCol >= 0) c.getString(relCol).orEmpty() else "",
                    ),
                )
            }
        }
        return entries
    }

    private fun queryAudioFiles(): List<FileEntry> {
        val entries = mutableListOf<FileEntry>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.Audio.AudioColumns.DURATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            }
        }.toTypedArray()

        querySafe(uri, projection)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val modCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val durCol = c.getColumnIndex(MediaStore.Audio.AudioColumns.DURATION)
            val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            } else -1

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val itemUri = ContentUris.withAppendedId(uri, id)
                entries.add(
                    FileEntry(
                        id = itemUri.toString(),
                        uri = itemUri,
                        mediaStoreId = id,
                        name = if (nameCol >= 0) c.getString(nameCol).orEmpty().ifBlank { "Audio" } else "Audio",
                        sizeBytes = if (sizeCol >= 0) c.getLong(sizeCol) else 0L,
                        lastModifiedMillis = if (modCol >= 0) c.getLong(modCol) * 1000L else 0L,
                        mimeType = if (mimeCol >= 0) c.getString(mimeCol).orEmpty().ifBlank { "audio/*" } else "audio/*",
                        isDirectory = false,
                        isMediaStoreBacked = true,
                        isImage = false,
                        isVideo = false,
                        durationMillis = if (durCol >= 0) c.getLong(durCol) else 0L,
                        relativePath = if (relCol >= 0) c.getString(relCol).orEmpty() else "",
                    ),
                )
            }
        }
        return entries
    }

    private fun queryDocumentFiles(): List<FileEntry> {
        val entries = mutableListOf<FileEntry>()
        val filesUri = MediaStore.Files.getContentUri("external")
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.Files.FileColumns.MEDIA_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            }
        }.toTypedArray()

        querySafe(filesUri, projection)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val modCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val mediaTypeCol = c.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            } else -1

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = if (nameCol >= 0) c.getString(nameCol).orEmpty() else ""
                val mime = if (mimeCol >= 0) c.getString(mimeCol).orEmpty() else ""
                val mediaType = if (mediaTypeCol >= 0) c.getInt(mediaTypeCol) else 0
                val size = if (sizeCol >= 0) c.getLong(sizeCol) else 0L
                val isDocType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT
                } else {
                    mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_NONE && size > 0L
                }
                val hasDocMime = mime.startsWith("application/") || mime.startsWith("text/")
                if (!isDocType && !hasDocMime) continue
                if (name.isBlank()) continue

                val itemUri = ContentUris.withAppendedId(filesUri, id)
                entries.add(
                    FileEntry(
                        id = itemUri.toString(),
                        uri = itemUri,
                        mediaStoreId = id,
                        name = name,
                        sizeBytes = size,
                        lastModifiedMillis = if (modCol >= 0) c.getLong(modCol) * 1000L else 0L,
                        mimeType = mime.ifBlank { "application/octet-stream" },
                        isDirectory = false,
                        isMediaStoreBacked = true,
                        isImage = false,
                        isVideo = false,
                        relativePath = if (relCol >= 0) c.getString(relCol).orEmpty() else "",
                    ),
                )
            }
        }
        return entries
    }

    private fun queryDownloads(): List<FileEntry> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val entries = mutableListOf<FileEntry>()
        val uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )

        querySafe(uri, projection)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val modCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val relCol = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = if (nameCol >= 0) c.getString(nameCol).orEmpty() else ""
                val mime = if (mimeCol >= 0) c.getString(mimeCol).orEmpty() else ""
                val itemUri = when {
                    mime.startsWith("image/") -> ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id,
                    )
                    mime.startsWith("video/") -> ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id,
                    )
                    else -> ContentUris.withAppendedId(uri, id)
                }
                entries.add(
                    FileEntry(
                        id = itemUri.toString(),
                        uri = itemUri,
                        mediaStoreId = id,
                        name = name.ifBlank { "Download" },
                        sizeBytes = if (sizeCol >= 0) c.getLong(sizeCol) else 0L,
                        lastModifiedMillis = if (modCol >= 0) c.getLong(modCol) * 1000L else 0L,
                        mimeType = mime.ifBlank { "*/*" },
                        isDirectory = false,
                        isMediaStoreBacked = true,
                        isImage = mime.startsWith("image/"),
                        isVideo = mime.startsWith("video/"),
                        relativePath = if (relCol >= 0) c.getString(relCol).orEmpty() else "",
                    ),
                )
            }
        }
        return entries
    }

    private fun querySafe(uri: Uri, projection: Array<String>) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val args = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_EXCLUDE)
                putStringArray(
                    ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.MediaColumns.DATE_MODIFIED),
                )
                putInt(
                    ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    ContentResolver.QUERY_SORT_DIRECTION_DESCENDING,
                )
            }
            runCatching { contentResolver.query(uri, projection, args, null) }.getOrNull()
        } else {
            runCatching {
                contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
                )
            }.getOrNull()
        }
}
