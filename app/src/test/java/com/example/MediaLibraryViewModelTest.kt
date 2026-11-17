package com.example

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.TrashBinStore
import com.example.ui.state.MediaLibraryViewModel
import com.example.ui.state.PendingIntentOperation
import com.example.ui.state.SnackbarNotification
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaLibraryViewModelTest {

    private lateinit var app: Application
    private lateinit var provider: FakeMediaStoreProvider

    private val image1Uri: Uri =
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 101L)
    private val image2Uri: Uri =
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 102L)
    private val image3Uri: Uri =
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 103L)

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(app).grantPermissions(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        provider = FakeMediaStoreProvider().apply {
            addImage(id = 101L, name = "IMG_0101.jpg", dateTaken = 1_700_000_300_000L)
            addImage(id = 102L, name = "IMG_0102.jpg", dateTaken = 1_700_000_200_000L)
            addImage(id = 103L, name = "IMG_0103.jpg", dateTaken = 1_700_000_100_000L)
            attachInfo(
                app,
                ProviderInfo().apply { authority = MediaStore.AUTHORITY },
            )
        }
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, provider)
        TrashBinStore(app).removeFromTrash(TrashBinStore(app).getTrashedUris())
    }

    @Test
    fun `requestPermanentDelete deletes directly without emitting second confirmation when permitted`() {
        val viewModel = MediaLibraryViewModel(app)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val uiJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { viewModel.uiState.collect {} }
        waitUntil { viewModel.uiState.value.allMedia.size == 3 }

        val pendingOps = mutableListOf<PendingIntentOperation>()
        val opJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.pendingOperation.collect { pendingOps.add(it) }
        }

        val items = viewModel.uiState.value.allMedia
        viewModel.openViewerSession(items)
        viewModel.requestTrash(listOf(image1Uri, image2Uri), fromViewer = true)
        waitUntil { viewModel.uiState.value.trashedMedia.size == 2 }

        viewModel.requestPermanentDelete(listOf(image1Uri, image2Uri))
        waitUntil {
            viewModel.uiState.value.trashedMedia.isEmpty() &&
                provider.remainingIds() == listOf(103L) &&
                TrashBinStore(app).getTrashedUris().isEmpty()
        }

        assertTrue("Expected no second confirmation IntentSender", pendingOps.isEmpty())
        assertEquals(listOf(103L), provider.remainingIds())
        assertTrue(TrashBinStore(app).getTrashedUris().isEmpty())
        opJob.cancel()
        uiJob.cancel()
    }

    @Test
    fun `requestPermanentDelete handles non-permitted OS deletion failure gracefully`() {
        val viewModel = MediaLibraryViewModel(app)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val uiJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { viewModel.uiState.collect {} }
        val snackbars = mutableListOf<SnackbarNotification>()
        val snackbarJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.snackbarEvents.collect { snackbars.add(it) }
        }
        waitUntil { viewModel.uiState.value.allMedia.size == 3 }

        viewModel.openViewerSession(viewModel.uiState.value.allMedia)
        viewModel.requestTrash(listOf(image1Uri), fromViewer = true)
        waitUntil { viewModel.uiState.value.trashedMedia.size == 1 }

        provider.blockDirectDelete = true
        val pendingOps = mutableListOf<PendingIntentOperation>()
        val opJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.pendingOperation.collect { pendingOps.add(it) }
        }

        viewModel.requestPermanentDelete(listOf(image1Uri))
        waitUntil {
            snackbars.any { it.message == "Could not delete selected items" } &&
                viewModel.uiState.value.trashedMedia.map { it.id } == listOf(101L)
        }

        assertTrue("No second OS delete modal should ever be emitted", pendingOps.isEmpty())
        assertEquals(listOf(101L, 102L, 103L), provider.remainingIds())
        snackbarJob.cancel()
        opJob.cancel()
        uiJob.cancel()
    }

    @Test
    fun `triggerUndo restores item to original viewer position silently with no snackbar`() {
        val viewModel = MediaLibraryViewModel(app)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val uiJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { viewModel.uiState.collect {} }
        waitUntil { viewModel.uiState.value.allMedia.size == 3 }

        val snackbars = mutableListOf<SnackbarNotification>()
        val focusIds = mutableListOf<Long>()
        val snackbarJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.snackbarEvents.collect { snackbars.add(it) }
        }
        val focusJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.viewerFocusMediaId.collect { focusIds.add(it) }
        }

        val initialItems = viewModel.uiState.value.allMedia
        assertEquals(listOf(101L, 102L, 103L), initialItems.map { it.id })
        viewModel.openViewerSession(initialItems)

        viewModel.requestTrash(listOf(image1Uri), fromViewer = true)
        waitUntil {
            viewModel.viewerItems.value.map { it.id } == listOf(102L, 103L) &&
                viewModel.uiState.value.undoRedoState.canUndo
        }
        assertEquals(1, viewModel.uiState.value.trashedMedia.size)

        snackbars.clear()
        viewModel.triggerUndo()
        waitUntil {
            viewModel.viewerItems.value.map { it.id } == listOf(101L, 102L, 103L) &&
                viewModel.uiState.value.trashedMedia.isEmpty()
        }

        assertEquals(listOf(101L), focusIds)
        assertFalse(viewModel.uiState.value.undoRedoState.canUndo)
        assertTrue(viewModel.uiState.value.undoRedoState.canRedo)
        assertTrue("Undo must execute silently with zero snackbars: $snackbars", snackbars.isEmpty())

        // Verify unrelated Restore from Trash Bin still keeps its notification.
        viewModel.requestTrash(listOf(image1Uri), fromViewer = true)
        waitUntil { viewModel.uiState.value.trashedMedia.size == 1 }
        snackbars.clear()
        viewModel.requestRestore(listOf(image1Uri))
        waitUntil { snackbars.isNotEmpty() }
        assertEquals("Restored from trash", snackbars.last().message)

        snackbarJob.cancel()
        focusJob.cancel()
        uiJob.cancel()
    }

    @Test
    fun `rapid consecutive triggerUndo calls restore distinct items without duplicates or snackbars`() {
        val viewModel = MediaLibraryViewModel(app)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val uiJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { viewModel.uiState.collect {} }
        waitUntil { viewModel.uiState.value.allMedia.size == 3 }

        val snackbars = mutableListOf<SnackbarNotification>()
        val snackbarJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.snackbarEvents.collect { snackbars.add(it) }
        }

        viewModel.openViewerSession(viewModel.uiState.value.allMedia)
        viewModel.requestTrash(listOf(image1Uri), fromViewer = true)
        waitUntil { viewModel.viewerItems.value.map { it.id } == listOf(102L, 103L) }

        viewModel.requestTrash(listOf(image2Uri), fromViewer = true)
        waitUntil {
            viewModel.viewerItems.value.map { it.id } == listOf(103L) &&
                viewModel.uiState.value.trashedMedia.size == 2
        }

        snackbars.clear()
        // Trigger two consecutive Undos rapidly without waiting between them
        viewModel.triggerUndo()
        viewModel.triggerUndo()

        waitUntil {
            viewModel.viewerItems.value.map { it.id } == listOf(101L, 102L, 103L) &&
                viewModel.uiState.value.trashedMedia.isEmpty()
        }

        assertEquals(listOf(101L, 102L, 103L), viewModel.viewerItems.value.map { it.id })
        assertTrue("Consecutive Undos must emit no snackbars: $snackbars", snackbars.isEmpty())
        snackbarJob.cancel()
        uiJob.cancel()
    }

    @Test
    fun `requestTrash from grid updates UI optimistically and handles large batches immediately`() {
        val extraUris = (200L..259L).map { id ->
            provider.addImage(id = id, name = "BATCH_$id.jpg", dateTaken = 1_690_000_000_000L + id)
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        }
        val viewModel = MediaLibraryViewModel(app)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val uiJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { viewModel.uiState.collect {} }
        waitUntil { viewModel.uiState.value.allMedia.size == 63 }

        // Select 60 items using selectUris (deduplicates & never deselects existing)
        viewModel.toggleSelection(extraUris.first())
        viewModel.selectUris(extraUris + extraUris.take(10))
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertEquals(60, viewModel.uiState.value.selectedUris.size)

        // Batch trash from grid updates active list, trash bin, and selection immediately
        viewModel.requestTrash(extraUris, fromViewer = false)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertEquals(3, viewModel.uiState.value.allMedia.size)
        assertEquals(60, viewModel.uiState.value.trashedMedia.size)
        assertTrue(viewModel.uiState.value.selectedUris.isEmpty())

        waitUntil { TrashBinStore(app).getTrashedUris().size == 60 }
        uiJob.cancel()
    }

    @Test
    fun `requestPermanentDelete handles large batches and partial failures accurately`() {
        val batchUris = (300L..359L).map { id ->
            provider.addImage(id = id, name = "DEL_$id.jpg", dateTaken = 1_680_000_000_000L + id)
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        }
        val viewModel = MediaLibraryViewModel(app)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val uiJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { viewModel.uiState.collect {} }
        val snackbars = mutableListOf<SnackbarNotification>()
        val snackbarJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.snackbarEvents.collect { snackbars.add(it) }
        }
        val pendingOps = mutableListOf<PendingIntentOperation>()
        val opJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.pendingOperation.collect { pendingOps.add(it) }
        }
        waitUntil { viewModel.uiState.value.allMedia.size == 63 }

        // Move 1 single item and permanently delete 1
        viewModel.requestTrash(listOf(image1Uri), fromViewer = false)
        waitUntil { TrashBinStore(app).getTrashedUris().size == 1 }
        snackbars.clear()
        viewModel.requestPermanentDelete(listOf(image1Uri))
        waitUntil { snackbars.any { it.message == "Deleted permanently" } }
        assertTrue(provider.remainingIds().none { it == 101L })

        // Move 60 items to trash and protect 1 item (310L) to test partial failure reconciliation
        viewModel.requestTrash(batchUris, fromViewer = false)
        waitUntil { TrashBinStore(app).getTrashedUris().size == 60 }
        provider.protectedIds.add(310L)

        snackbars.clear()
        viewModel.requestPermanentDelete(batchUris)
        waitUntil {
            snackbars.any { it.message == "Deleted 59 items; 1 could not be deleted" } &&
                viewModel.uiState.value.trashedMedia.map { it.id } == listOf(310L)
        }
        assertEquals(setOf(102L, 103L, 310L), provider.remainingIds().toSet())
        assertEquals(setOf(batchUris[10]), TrashBinStore(app).getTrashedUris())
        assertTrue(pendingOps.isEmpty())

        snackbarJob.cancel()
        opJob.cancel()
        uiJob.cancel()
    }

    private fun waitUntil(timeoutMs: Long = 3_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            if (predicate()) return
            Thread.sleep(15L)
        }
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertTrue("Timed out waiting for condition", predicate())
    }

    private class FakeMediaStoreProvider : ContentProvider() {
        private data class Row(
            val id: Long,
            val name: String,
            val dateTakenMillis: Long,
        )

        private val lock = Any()
        private val rows = mutableListOf<Row>()
        @Volatile var blockDirectDelete = false
        val protectedIds = java.util.Collections.synchronizedSet(HashSet<Long>())

        fun addImage(id: Long, name: String, dateTaken: Long) {
            synchronized(lock) {
                rows.add(Row(id = id, name = name, dateTakenMillis = dateTaken))
            }
        }

        fun remainingIds(): List<Long> = synchronized(lock) { rows.map { it.id } }

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            if (uri.toString().contains("/video/")) {
                return MatrixCursor(projection ?: arrayOf(MediaStore.MediaColumns._ID))
            }
            val idFilter = runCatching { ContentUris.parseId(uri) }.getOrNull()
            val cols = projection ?: arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT,
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            )
            val cursor = MatrixCursor(cols)
            synchronized(lock) {
                rows.filter { idFilter == null || it.id == idFilter }
                    .sortedByDescending { it.dateTakenMillis }
                    .forEach { row ->
                        val values = cols.map<String, Any?> { col ->
                            when (col) {
                                MediaStore.MediaColumns._ID -> row.id
                                MediaStore.MediaColumns.DISPLAY_NAME -> row.name
                                MediaStore.MediaColumns.MIME_TYPE -> "image/jpeg"
                                MediaStore.MediaColumns.DATE_TAKEN -> row.dateTakenMillis
                                MediaStore.MediaColumns.DATE_MODIFIED -> row.dateTakenMillis / 1000L
                                MediaStore.MediaColumns.SIZE -> 4096L
                                MediaStore.MediaColumns.WIDTH -> 1080
                                MediaStore.MediaColumns.HEIGHT -> 1920
                                MediaStore.MediaColumns.BUCKET_ID -> 1L
                                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME -> "Camera"
                                MediaStore.MediaColumns.RELATIVE_PATH -> "DCIM/Camera/"
                                MediaStore.MediaColumns.IS_TRASHED -> 0
                                else -> 0
                            }
                        }.toTypedArray()
                        cursor.addRow(values)
                    }
            }
            return cursor
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            if (blockDirectDelete) {
                throw SecurityException("Permission required")
            }
            val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return 0
            if (id in protectedIds) {
                throw SecurityException("Protected item")
            }
            synchronized(lock) {
                val removed = rows.removeAll { it.id == id }
                return if (removed) 1 else 0
            }
        }

        override fun getType(uri: Uri): String = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }
}
