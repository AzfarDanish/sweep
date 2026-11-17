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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.TrashBinStore
import com.example.model.AlbumCollection
import com.example.model.AlbumKind
import com.example.model.AlbumSortOption
import com.example.model.MediaItem
import com.example.model.sortAlbumMedia
import com.example.ui.screens.AlbumDetailScreen
import com.example.ui.state.MediaLibraryViewModel
import com.example.ui.theme.SweepTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class AlbumSortingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var provider: SortingFakeMediaProvider

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(app).grantPermissions(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        provider = SortingFakeMediaProvider().apply {
            // Album 1 ("Camera"): intentionally varied creation vs modified dates & sizes
            addMedia(
                id = 1L,
                name = "oldest_modified_later.jpg",
                dateTaken = 1_600_000_000_000L,
                dateModifiedSec = 1_900_000_000L, // modified AFTER others, but taken FIRST
                sizeBytes = 5_000L,
                bucketId = 10L,
                bucketName = "Camera",
            )
            addMedia(
                id = 2L,
                name = "middle_largest.jpg",
                dateTaken = 1_700_000_000_000L,
                dateModifiedSec = 1_700_000_000L,
                sizeBytes = 90_000L,
                bucketId = 10L,
                bucketName = "Camera",
            )
            addMedia(
                id = 3L,
                name = "newest_smallest.jpg",
                dateTaken = 1_800_000_000_000L,
                dateModifiedSec = 1_800_000_000L,
                sizeBytes = 1_000L,
                bucketId = 10L,
                bucketName = "Camera",
            )
            // Missing creation date (0L) falls back to modified date
            addMedia(
                id = 4L,
                name = "fallback_modified.jpg",
                dateTaken = 0L,
                dateModifiedSec = 1_650_000_000L, // 1_650_000_000_000 ms (between id 1 and id 2)
                sizeBytes = 20_000L,
                bucketId = 10L,
                bucketName = "Camera",
            )

            // Album 2 ("Downloads"): independent collection
            addMedia(
                id = 21L,
                name = "dl_new.jpg",
                dateTaken = 1_750_000_000_000L,
                dateModifiedSec = 1_750_000_000L,
                sizeBytes = 3_000L,
                bucketId = 20L,
                bucketName = "Downloads",
            )
            addMedia(
                id = 22L,
                name = "dl_old.jpg",
                dateTaken = 1_550_000_000_000L,
                dateModifiedSec = 1_550_000_000L,
                sizeBytes = 8_000L,
                bucketId = 20L,
                bucketName = "Downloads",
            )

            attachInfo(
                app,
                ProviderInfo().apply { authority = MediaStore.AUTHORITY },
            )
        }
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, provider)
        TrashBinStore(app).removeFromTrash(TrashBinStore(app).getTrashedUris())
    }

    @Test
    fun `sortAlbumMedia_handles_creation_date_missing_dates_and_all_four_modes`() {
        val oldestTakenModifiedLate = sampleItem(
            id = 1L,
            dateTaken = 1_000L,
            dateModified = 9_999_999L,
            size = 200L,
        )
        val invalidTakenValidModified = sampleItem(
            id = 2L,
            dateTaken = -5L,
            dateModified = 2_000L,
            size = 500L,
        )
        val missingAllDates = sampleItem(
            id = 3L,
            dateTaken = 0L,
            dateModified = 0L,
            size = 100L,
        )
        val newest = sampleItem(
            id = 4L,
            dateTaken = 5_000L,
            dateModified = 5_000L,
            size = 300L,
        )
        val items = listOf(newest, oldestTakenModifiedLate, missingAllDates, invalidTakenValidModified)

        assertEquals(0L, missingAllDates.effectiveTimestampMillis)
        assertEquals(2_000L, invalidTakenValidModified.effectiveTimestampMillis)
        assertEquals(1_000L, oldestTakenModifiedLate.effectiveTimestampMillis)

        // Oldest first: missing/0 -> 1000 -> 2000 -> 5000
        assertEquals(
            listOf(3L, 1L, 2L, 4L),
            sortAlbumMedia(items, AlbumSortOption.OLDEST_FIRST).map { it.id },
        )
        // Newest first: 5000 -> 2000 -> 1000 -> missing/0
        assertEquals(
            listOf(4L, 2L, 1L, 3L),
            sortAlbumMedia(items, AlbumSortOption.NEWEST_FIRST).map { it.id },
        )
        // Largest first: 500 -> 300 -> 200 -> 100
        assertEquals(
            listOf(2L, 4L, 1L, 3L),
            sortAlbumMedia(items, AlbumSortOption.LARGEST_FIRST).map { it.id },
        )
        // Smallest first: 100 -> 200 -> 300 -> 500
        assertEquals(
            listOf(3L, 1L, 4L, 2L),
            sortAlbumMedia(items, AlbumSortOption.SMALLEST_FIRST).map { it.id },
        )
    }

    @Test
    fun `viewmodel_defaults_all_albums_to_oldest_first_keeps_per_album_sort_and_survives_refresh`() {
        val viewModel = MediaLibraryViewModel(app)
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        val uiJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { viewModel.uiState.collect {} }
        waitUntil { viewModel.uiState.value.albums.size >= 3 }

        // Even though MediaStore scan returns newest first, all albums default to Oldest first
        // using original creation date (DATE_TAKEN), not DATE_MODIFIED.
        val cameraDefault = viewModel.getAlbumById("bucket_10")!!
        val downloadsDefault = viewModel.getAlbumById("bucket_20")!!
        val logicalPhotosDefault = viewModel.getAlbumById("logical_photos")!!

        assertEquals(listOf(1L, 4L, 2L, 3L), cameraDefault.items.map { it.id })
        assertEquals(listOf(22L, 21L), downloadsDefault.items.map { it.id })
        assertEquals(listOf(22L, 1L, 4L, 2L, 21L, 3L), logicalPhotosDefault.items.map { it.id })

        // Switch Camera to Largest first; Downloads must stay Oldest first independently
        viewModel.setAlbumSortOption("bucket_10", AlbumSortOption.LARGEST_FIRST)
        waitUntil {
            viewModel.getAlbumById("bucket_10")?.items?.map { it.id } == listOf(2L, 4L, 1L, 3L)
        }
        assertEquals(AlbumSortOption.LARGEST_FIRST, viewModel.uiState.value.albumSortOptions["bucket_10"])
        assertEquals(listOf(22L, 21L), viewModel.getAlbumById("bucket_20")!!.items.map { it.id })

        // Switch Downloads to Newest first while Camera remains Largest first
        viewModel.setAlbumSortOption("bucket_20", AlbumSortOption.NEWEST_FIRST)
        waitUntil {
            viewModel.getAlbumById("bucket_20")?.items?.map { it.id } == listOf(21L, 22L)
        }
        assertEquals(listOf(2L, 4L, 1L, 3L), viewModel.getAlbumById("bucket_10")!!.items.map { it.id })

        // Refresh media (with a newly added photo in Camera) -> preserves selected sort!
        provider.addMedia(
            id = 5L,
            name = "new_huge.jpg",
            dateTaken = 1_900_000_000_000L,
            dateModifiedSec = 1_900_000_000L,
            sizeBytes = 120_000L,
            bucketId = 10L,
            bucketName = "Camera",
        )
        viewModel.refreshMedia()
        waitUntil { viewModel.getAlbumById("bucket_10")?.count == 5 }

        assertEquals(AlbumSortOption.LARGEST_FIRST, viewModel.uiState.value.albumSortOptions["bucket_10"])
        assertEquals(listOf(5L, 2L, 4L, 1L, 3L), viewModel.getAlbumById("bucket_10")!!.items.map { it.id })
        assertEquals(listOf(21L, 22L), viewModel.getAlbumById("bucket_20")!!.items.map { it.id })
        // Uncustomized logical album still defaults to Oldest first
        assertEquals(listOf(22L, 1L, 4L, 2L, 21L, 3L, 5L), viewModel.getAlbumById("logical_photos")!!.items.map { it.id })

        uiJob.cancel()
    }

    @Test
    fun `album_detail_header_sort_control_switches_order_and_persists_per_album_across_navigation`() {
        val album1Items = listOf(
            sampleItem(id = 10L, dateTaken = 3_000L, dateModified = 3_000L, size = 1_000L), // newest & smallest
            sampleItem(id = 11L, dateTaken = 1_000L, dateModified = 9_000L, size = 5_000L), // oldest
            sampleItem(id = 12L, dateTaken = 2_000L, dateModified = 2_000L, size = 9_000L), // middle & largest
        )
        val album2Items = listOf(
            sampleItem(id = 20L, dateTaken = 4_000L, dateModified = 4_000L, size = 2_000L),
            sampleItem(id = 21L, dateTaken = 1_500L, dateModified = 1_500L, size = 6_000L),
        )
        val album1 = AlbumCollection(
            id = "bucket_10",
            name = "Camera",
            kind = AlbumKind.FOLDER,
            coverUri = album1Items.first().contentUri,
            coverIsVideo = false,
            count = album1Items.size,
            subtitle = "DCIM/Camera",
            items = album1Items,
        )
        val album2 = AlbumCollection(
            id = "bucket_20",
            name = "Downloads",
            kind = AlbumKind.FOLDER,
            coverUri = album2Items.first().contentUri,
            coverIsVideo = false,
            count = album2Items.size,
            subtitle = "Download",
            items = album2Items,
        )

        composeTestRule.setContent {
            SweepTheme {
                var activeAlbumId by remember { mutableStateOf("bucket_10") }
                val sortMap = remember { mutableStateMapOf<String, AlbumSortOption>() }
                val activeAlbum = if (activeAlbumId == "bucket_10") album1 else album2
                val activeSort = sortMap[activeAlbumId] ?: AlbumSortOption.OLDEST_FIRST

                AlbumDetailScreen(
                    album = activeAlbum,
                    selectedUris = emptySet(),
                    gridColumns = 1, // single column so bounds.top strictly reflects sort order
                    selectedSort = activeSort,
                    onSortChange = { sortMap[activeAlbumId] = it },
                    onBack = { activeAlbumId = if (activeAlbumId == "bucket_10") "bucket_20" else "bucket_10" },
                    onOpenViewer = { _, _ -> },
                    onToggleSelection = {},
                    onSelectAll = {},
                    onClearSelection = {},
                    onTrashSelected = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        // Sort control is on the right side of the header
        val backBounds = composeTestRule.onNodeWithTag("album_detail_back_button").fetchSemanticsNode().boundsInRoot
        val sortBounds = composeTestRule.onNodeWithTag("album_sort_button").fetchSemanticsNode().boundsInRoot
        assertTrue("Sort icon should be on the right side of the header", sortBounds.left > backBounds.right)

        // Default order in Album 1 is Oldest first: 11 -> 12 -> 10
        assertVerticalOrder(listOf("media_tile_11", "media_tile_12", "media_tile_10"))

        // Open Sort menu and verify Oldest first is selected by default
        composeTestRule.onNodeWithTag("album_sort_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("album_sort_menu").assertIsDisplayed()
        composeTestRule.onNodeWithTag("album_sort_option_oldest_first")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeTestRule.onNodeWithTag("album_sort_option_newest_first")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))

        // Select Newest first -> 10 -> 12 -> 11
        composeTestRule.onNodeWithTag("album_sort_option_newest_first").performClick()
        composeTestRule.waitForIdle()
        assertVerticalOrder(listOf("media_tile_10", "media_tile_12", "media_tile_11"))

        // Select Largest first -> 12 -> 11 -> 10
        composeTestRule.onNodeWithTag("album_sort_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("album_sort_option_newest_first")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeTestRule.onNodeWithTag("album_sort_option_largest_first").performClick()
        composeTestRule.waitForIdle()
        assertVerticalOrder(listOf("media_tile_12", "media_tile_11", "media_tile_10"))

        // Select Smallest first -> 10 -> 11 -> 12
        composeTestRule.onNodeWithTag("album_sort_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("album_sort_option_smallest_first").performClick()
        composeTestRule.waitForIdle()
        assertVerticalOrder(listOf("media_tile_10", "media_tile_11", "media_tile_12"))

        // Switch to Album 2 -> defaults independently to Oldest first (21 -> 20)
        composeTestRule.onNodeWithTag("album_detail_back_button").performClick()
        composeTestRule.waitForIdle()
        assertVerticalOrder(listOf("media_tile_21", "media_tile_20"))
        composeTestRule.onNodeWithTag("album_sort_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("album_sort_option_oldest_first")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        // Dismiss / switch back to Album 1 -> Album 1 preserves Smallest first (10 -> 11 -> 12)
        composeTestRule.onNodeWithTag("album_sort_option_oldest_first").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("album_detail_back_button").performClick()
        composeTestRule.waitForIdle()
        assertVerticalOrder(listOf("media_tile_10", "media_tile_11", "media_tile_12"))
    }

    private fun assertVerticalOrder(tags: List<String>) {
        val tops = tags.map { tag ->
            composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top
        }
        for (i in 0 until tops.lastIndex) {
            assertTrue(
                "Expected ${tags[i]} (top=${tops[i]}) above ${tags[i + 1]} (top=${tops[i + 1]})",
                tops[i] < tops[i + 1],
            )
        }
    }

    private fun sampleItem(
        id: Long,
        dateTaken: Long,
        dateModified: Long,
        size: Long,
    ): MediaItem = MediaItem(
        id = id,
        contentUri = Uri.parse("content://media/external/images/media/$id"),
        displayName = "IMG_$id.jpg",
        mimeType = "image/jpeg",
        dateTakenMillis = dateTaken,
        dateModifiedMillis = dateModified,
        sizeBytes = size,
        durationMillis = 0L,
        width = 1080,
        height = 1920,
        bucketId = 10L,
        bucketName = "Camera",
        relativePath = "DCIM/Camera/",
        isVideo = false,
        isTrashed = false,
    )

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

    private class SortingFakeMediaProvider : ContentProvider() {
        private data class Row(
            val id: Long,
            val name: String,
            val dateTakenMillis: Long,
            val dateModifiedSec: Long,
            val sizeBytes: Long,
            val bucketId: Long,
            val bucketName: String,
        )

        private val lock = Any()
        private val rows = mutableListOf<Row>()

        fun addMedia(
            id: Long,
            name: String,
            dateTaken: Long,
            dateModifiedSec: Long,
            sizeBytes: Long,
            bucketId: Long,
            bucketName: String,
        ) {
            synchronized(lock) {
                rows.add(
                    Row(
                        id = id,
                        name = name,
                        dateTakenMillis = dateTaken,
                        dateModifiedSec = dateModifiedSec,
                        sizeBytes = sizeBytes,
                        bucketId = bucketId,
                        bucketName = bucketName,
                    ),
                )
            }
        }

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
                // Intentionally emit newest modified first from MediaStore to verify app-side oldest-first sort
                rows.sortedByDescending { it.dateModifiedSec }.forEach { row ->
                    val values = cols.map<String, Any?> { col ->
                        when (col) {
                            MediaStore.MediaColumns._ID -> row.id
                            MediaStore.MediaColumns.DISPLAY_NAME -> row.name
                            MediaStore.MediaColumns.MIME_TYPE -> "image/jpeg"
                            MediaStore.MediaColumns.DATE_TAKEN -> row.dateTakenMillis
                            MediaStore.MediaColumns.DATE_MODIFIED -> row.dateModifiedSec
                            MediaStore.MediaColumns.SIZE -> row.sizeBytes
                            MediaStore.MediaColumns.WIDTH -> 1080
                            MediaStore.MediaColumns.HEIGHT -> 1920
                            MediaStore.MediaColumns.BUCKET_ID -> row.bucketId
                            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME -> row.bucketName
                            MediaStore.MediaColumns.RELATIVE_PATH -> "DCIM/${row.bucketName}/"
                            MediaStore.MediaColumns.IS_TRASHED -> 0
                            else -> 0
                        }
                    }.toTypedArray()
                    cursor.addRow(values)
                }
            }
            return cursor
        }

        override fun getType(uri: Uri): String = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }
}
