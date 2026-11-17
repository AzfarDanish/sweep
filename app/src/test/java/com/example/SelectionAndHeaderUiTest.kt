package com.example

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.model.AlbumCollection
import com.example.model.AlbumKind
import com.example.model.AlbumSortOption
import com.example.model.MediaDateGroup
import com.example.model.MediaItem
import com.example.model.PermissionAccessState
import com.example.ui.screens.AlbumDetailScreen
import com.example.ui.screens.AlbumsScreen
import com.example.ui.screens.PhotosScreen
import com.example.ui.screens.TimelineScreen
import com.example.ui.state.MediaLibraryUiState
import com.example.ui.theme.SweepTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class SelectionAndHeaderUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleItems(count: Int): List<MediaItem> = (1..count).map { index ->
        val dayOffset = index * 1000L
        MediaItem(
            id = index.toLong(),
            contentUri = Uri.parse("content://media/external/images/media/$index"),
            displayName = "IMG_$index.jpg",
            mimeType = "image/jpeg",
            dateTakenMillis = 1_730_000_000_000L - dayOffset,
            dateModifiedMillis = 1_730_000_000_000L - dayOffset,
            sizeBytes = 4096L * index,
            durationMillis = 0L,
            width = 1080,
            height = 1920,
            bucketId = 10L,
            bucketName = "Camera",
            relativePath = "DCIM/Camera/",
            isVideo = false,
            isTrashed = false,
        )
    }

    @Test
    fun `drag_to_select_selects_multiple_items_across_rows_without_deselecting_or_breaking_scroll`() {
        val items = sampleItems(30)
        var selectedSnapshot: Set<Uri> = emptySet()

        composeTestRule.setContent {
            SweepTheme {
                var selected by remember { mutableStateOf(emptySet<Uri>()) }
                selectedSnapshot = selected
                val uiState = MediaLibraryUiState(
                    permissionState = PermissionAccessState.FULL,
                    allMedia = items,
                    filteredMedia = items,
                    dateGroups = listOf(MediaDateGroup(key = "today", label = "Today", items = items)),
                    selectedUris = selected,
                )
                PhotosScreen(
                    uiState = uiState,
                    gridColumns = 3,
                    onFilterChange = {},
                    onToggleSearch = {},
                    onSearchQueryChange = {},
                    onOpenTrash = {},
                    onOpenViewer = { _, _ -> },
                    onToggleSelection = { uri ->
                        selected = if (uri in selected) selected - uri else selected + uri
                    },
                    onSelectAll = { uris -> selected = uris.toSet() },
                    onClearSelection = { selected = emptySet() },
                    onTrashSelected = {},
                    onRequestPermission = {},
                    onOpenSettings = {},
                    onSelectUris = { uris ->
                        selected = selected + uris
                    },
                )
            }
        }

        composeTestRule.waitForIdle()

        val gridOrigin = composeTestRule.onNodeWithTag("photos_grid").fetchSemanticsNode().boundsInRoot.topLeft
        fun localCenter(tag: String): Offset =
            composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center - gridOrigin

        val tile1Local = localCenter("media_tile_1")
        val tile2Local = localCenter("media_tile_2")
        val tile3Local = localCenter("media_tile_3")

        // 1) Long-press tile 1 to enter selection mode via drag-select, then slide horizontally across tiles 2 & 3
        composeTestRule.onNodeWithTag("photos_grid").performTouchInput {
            down(tile1Local)
            advanceEventTime(700L)
            moveTo(tile2Local, delayMillis = 60L)
            moveTo(tile3Local, delayMillis = 60L)
            up()
        }
        composeTestRule.waitForIdle()
        assertTrue(
            "Expected tiles 1, 2, 3 selected, got ${selectedSnapshot.size}: $selectedSnapshot",
            setOf(items[0].contentUri, items[1].contentUri, items[2].contentUri).all { it in selectedSnapshot },
        )

        // Re-measure grid origin after selection top bar replaces main top bar
        val selectionGridOrigin =
            composeTestRule.onNodeWithTag("photos_grid").fetchSemanticsNode().boundsInRoot.topLeft
        fun selectionLocalCenter(tag: String): Offset =
            composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center - selectionGridOrigin

        val tile3InSelection = selectionLocalCenter("media_tile_3")
        val tile4InSelection = selectionLocalCenter("media_tile_4")
        val tile6InSelection = selectionLocalCenter("media_tile_6")
        val tile9InSelection = selectionLocalCenter("media_tile_9")

        // 2) Now in selection mode: drag immediately starting with horizontal slide across row 2 (tiles 4 -> 6)
        // and down to row 3 (tile 9), then back over tile 3 (already selected must NOT be deselected)
        composeTestRule.onNodeWithTag("photos_grid").performTouchInput {
            down(tile4InSelection)
            moveTo(tile6InSelection, delayMillis = 60L)
            moveTo(tile9InSelection, delayMillis = 60L)
            moveTo(tile3InSelection, delayMillis = 60L)
            up()
        }
        composeTestRule.waitForIdle()
        assertTrue(items[0].contentUri in selectedSnapshot)
        assertTrue(items[2].contentUri in selectedSnapshot)
        assertTrue(items[3].contentUri in selectedSnapshot)
        assertTrue(items[4].contentUri in selectedSnapshot)
        assertTrue(items[5].contentUri in selectedSnapshot)
        assertTrue(items[8].contentUri in selectedSnapshot)

        // 3) Individual tap on an already selected tile toggles/deselects it cleanly
        val countBeforeTap = selectedSnapshot.size
        composeTestRule.onNodeWithTag("media_tile_2").performClick()
        composeTestRule.waitForIdle()
        assertEquals(countBeforeTap - 1, selectedSnapshot.size)
        assertTrue(items[1].contentUri !in selectedSnapshot)

        // Tap again re-selects it
        composeTestRule.onNodeWithTag("media_tile_2").performClick()
        composeTestRule.waitForIdle()
        assertEquals(countBeforeTap, selectedSnapshot.size)
        assertTrue(items[1].contentUri in selectedSnapshot)

        // 4) Vertical swipe in selection mode scrolls the grid instead of falsely drag-selecting
        val topTileBeforeScroll =
            composeTestRule.onNodeWithTag("media_tile_1").fetchSemanticsNode().boundsInRoot.top
        composeTestRule.onNodeWithTag("photos_grid").performTouchInput {
            swipeUp(startY = bottom - 80f, endY = top + 80f, durationMillis = 120L)
        }
        composeTestRule.waitForIdle()
        val tile1Nodes = composeTestRule
            .onAllNodes(androidx.compose.ui.test.hasTestTag("media_tile_1"))
            .fetchSemanticsNodes()
        assertTrue(
            "Vertical swipe in selection mode should scroll grid",
            tile1Nodes.isEmpty() || tile1Nodes.first().boundsInRoot.top < topTileBeforeScroll - 40f,
        )
    }

    @Test
    fun `drag_to_select_works_on_album_detail_screen`() {
        val items = sampleItems(9)
        var selectedSnapshot: Set<Uri> = emptySet()
        val album = AlbumCollection(
            id = "bucket_10",
            name = "Camera",
            kind = AlbumKind.FOLDER,
            coverUri = items.first().contentUri,
            coverIsVideo = false,
            count = items.size,
            subtitle = "DCIM/Camera",
            items = items,
        )

        composeTestRule.setContent {
            SweepTheme {
                var selected by remember { mutableStateOf(setOf(items[0].contentUri)) }
                selectedSnapshot = selected
                AlbumDetailScreen(
                    album = album,
                    selectedUris = selected,
                    gridColumns = 3,
                    selectedSort = AlbumSortOption.NEWEST_FIRST,
                    onSortChange = {},
                    onBack = {},
                    onOpenViewer = { _, _ -> },
                    onToggleSelection = { uri ->
                        selected = if (uri in selected) selected - uri else selected + uri
                    },
                    onSelectAll = { uris -> selected = uris.toSet() },
                    onClearSelection = { selected = emptySet() },
                    onTrashSelected = {},
                    onSelectUris = { uris -> selected = selected + uris },
                )
            }
        }

        composeTestRule.waitForIdle()
        val gridOrigin =
            composeTestRule.onNodeWithTag("album_detail_grid").fetchSemanticsNode().boundsInRoot.topLeft
        val tile1Local =
            composeTestRule.onNodeWithTag("media_tile_1").fetchSemanticsNode().boundsInRoot.center - gridOrigin
        val tile3Local =
            composeTestRule.onNodeWithTag("media_tile_3").fetchSemanticsNode().boundsInRoot.center - gridOrigin

        composeTestRule.onNodeWithTag("album_detail_grid").performTouchInput {
            down(tile1Local)
            moveTo(tile3Local, delayMillis = 60L)
            up()
        }
        composeTestRule.waitForIdle()
        assertTrue(items[0].contentUri in selectedSnapshot)
        assertTrue(items[1].contentUri in selectedSnapshot)
        assertTrue(items[2].contentUri in selectedSnapshot)
    }

    @Test
    fun `photos_header_removes_three_dot_menu_and_exposes_trash_access_on_photos_timeline_and_albums`() {
        var photosTrashClicks = 0
        var timelineTrashClicks = 0
        var albumsTrashClicks = 0
        val trashedSample = sampleItems(5)

        composeTestRule.setContent {
            SweepTheme {
                var tab by remember { mutableIntStateOf(0) }
                val uiState = MediaLibraryUiState(
                    permissionState = PermissionAccessState.FULL,
                    allMedia = sampleItems(3),
                    filteredMedia = sampleItems(3),
                    trashedMedia = trashedSample,
                )
                when (tab) {
                    0 -> PhotosScreen(
                        uiState = uiState,
                        gridColumns = 3,
                        onFilterChange = {},
                        onToggleSearch = {},
                        onSearchQueryChange = {},
                        onOpenTrash = {
                            photosTrashClicks++
                            tab = 1
                        },
                        onOpenViewer = { _, _ -> },
                        onToggleSelection = {},
                        onSelectAll = {},
                        onClearSelection = {},
                        onTrashSelected = {},
                        onRequestPermission = {},
                        onOpenSettings = {},
                    )
                    1 -> TimelineScreen(
                        uiState = uiState,
                        onSelectYear = {},
                        onOpenMonth = { _, _ -> },
                        onRequestPermission = {},
                        onOpenSettings = {},
                        onOpenTrash = {
                            timelineTrashClicks++
                            tab = 2
                        },
                    )
                    else -> AlbumsScreen(
                        uiState = uiState,
                        albumColumns = 2,
                        onOpenAlbum = {},
                        onRequestPermission = {},
                        onOpenSettings = {},
                        onOpenTrash = { albumsTrashClicks++ },
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        // Photos header: three-dot menu completely removed, Trash button navigates
        composeTestRule.onNodeWithTag("photos_more_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("open_trash_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("photos_trash_badge", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_trash_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, photosTrashClicks)

        // Timeline header: Trash button + badge navigates
        composeTestRule.onNodeWithTag("timeline_trash_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("timeline_trash_badge", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("timeline_trash_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, timelineTrashClicks)

        // Albums header: Trash button + badge navigates
        composeTestRule.onNodeWithTag("albums_trash_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("albums_trash_badge", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("albums_trash_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, albumsTrashClicks)
    }

    @Test
    fun `trash_badge_supports_single_double_and_99_plus_counts_without_clipping`() {
        val countsToCheck = listOf(5, 24, 100, 250)
        var currentCount by mutableIntStateOf(5)

        composeTestRule.setContent {
            SweepTheme {
                val uiState = MediaLibraryUiState(
                    permissionState = PermissionAccessState.FULL,
                    allMedia = sampleItems(2),
                    filteredMedia = sampleItems(2),
                    trashedMedia = sampleItems(currentCount),
                )
                TimelineScreen(
                    uiState = uiState,
                    onSelectYear = {},
                    onOpenMonth = { _, _ -> },
                    onRequestPermission = {},
                    onOpenSettings = {},
                    onOpenTrash = {},
                )
            }
        }

        for (count in countsToCheck) {
            currentCount = count
            composeTestRule.waitForIdle()

            val expectedLabel = if (count >= 100) "99+" else count.toString()
            composeTestRule.onNodeWithText(expectedLabel, useUnmergedTree = true).assertIsDisplayed()

            val buttonBounds = composeTestRule
                .onNodeWithTag("timeline_trash_button")
                .fetchSemanticsNode()
                .boundsInRoot
            val badgeBounds = composeTestRule
                .onNodeWithTag("timeline_trash_badge", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot

            assertTrue(
                "Badge for $count ($badgeBounds) must stay unclipped inside button ($buttonBounds)",
                badgeBounds.right <= buttonBounds.right + 0.5f &&
                    badgeBounds.left >= buttonBounds.left - 0.5f &&
                    badgeBounds.top >= buttonBounds.top - 0.5f,
            )

            if (count < 10) {
                assertTrue(
                    "Single-digit badge should be circular (w=${badgeBounds.width}, h=${badgeBounds.height})",
                    abs(badgeBounds.width - badgeBounds.height) <= 2f,
                )
            } else {
                assertTrue(
                    "Multi-digit/99+ badge should expand horizontally as a pill (w=${badgeBounds.width}, h=${badgeBounds.height})",
                    badgeBounds.width > badgeBounds.height,
                )
            }
        }
    }
}
