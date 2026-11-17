package com.example

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import com.example.data.UndoRedoUiState
import com.example.model.MediaItem
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.TrashBinScreen
import com.example.ui.theme.SweepTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class ViewerAndTrashUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleItems(count: Int = 4): List<MediaItem> = (1..count).map { index ->
        MediaItem(
            id = index.toLong(),
            contentUri = Uri.parse("content://media/external/images/media/$index"),
            displayName = "IMG_000$index.jpg",
            mimeType = "image/jpeg",
            dateTakenMillis = 1_700_000_000_000L + index * 1000L,
            dateModifiedMillis = 1_700_000_000_000L + index * 1000L,
            sizeBytes = 2048L * index,
            durationMillis = 0L,
            width = 1080,
            height = 1920,
            bucketId = 10L,
            bucketName = "Camera",
            relativePath = "DCIM/Camera/",
            isVideo = false,
            isTrashed = true,
        )
    }

    @Test
    fun `empty_trash_uses_single_confirmation_modal_and_executes_permanent_delete`() {
        val sample = sampleItems(2)
        val deletedBatches = mutableListOf<List<Uri>>()

        composeTestRule.setContent {
            SweepTheme {
                var trashed by remember { mutableStateOf(sample) }
                TrashBinScreen(
                    trashedItems = trashed,
                    selectedUris = emptySet(),
                    gridColumns = 3,
                    onBack = {},
                    onOpenTrashPreview = { _, _ -> },
                    onToggleSelection = {},
                    onSelectAll = {},
                    onClearSelection = {},
                    onRestoreUris = {},
                    onPermanentDeleteUris = { uris ->
                        deletedBatches.add(uris)
                        trashed = emptyList()
                    },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("empty_trash_button").performClick()
        composeTestRule.waitForIdle()

        // First (and only) confirmation modal appears
        composeTestRule.onNodeWithTag("confirm_empty_trash_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_empty_trash_button").performClick()
        composeTestRule.waitForIdle()

        // Immediately deletes without any intermediate or second modal
        assertEquals(1, deletedBatches.size)
        assertEquals(sample.map { it.contentUri }, deletedBatches.first())
        composeTestRule.onNodeWithTag("confirm_empty_trash_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("confirm_permanent_delete_button").assertDoesNotExist()
    }

    @Test
    fun `swipe_up_exit_travels_unclipped_over_fixed_top_header`() {
        val trashed = mutableListOf<MediaItem>()

        composeTestRule.setContent {
            SweepTheme {
                var items by remember { mutableStateOf(sampleItems(3)) }
                MediaViewerScreen(
                    items = items,
                    initialMediaId = 1L,
                    isTrashPreview = false,
                    trashCount = trashed.size,
                    undoRedoState = UndoRedoUiState(canUndo = trashed.isNotEmpty()),
                    onBack = {},
                    onOpenTrash = {},
                    onTrashItem = { item ->
                        trashed.add(item)
                        items = items.filterNot { it.id == item.id }
                    },
                    onRestoreItem = {},
                    onPermanentDeleteItem = {},
                    onUndo = {},
                    onRedo = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        val topHeaderInitial =
            composeTestRule.onNodeWithTag("viewer_top_header").fetchSemanticsNode().boundsInRoot
        val viewportInitial =
            composeTestRule.onNodeWithTag("viewer_media_viewport").fetchSemanticsNode().boundsInRoot

        val trashIconCenterInitial = composeTestRule
            .onNodeWithTag("viewer_trash_icon", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .center

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            swipeUp(
                startY = centerY,
                endY = centerY - 260f,
                durationMillis = 35L,
            )
        }
        composeTestRule.mainClock.advanceTimeBy(16L)

        assertEquals(listOf(1L), trashed.map { it.id })
        composeTestRule.onNodeWithTag("viewer_exiting_media_card_1").assertExists()

        var observedOverHeader = false
        var prevTopCenter: Offset? = null
        var firstDistanceToTrash = Float.MAX_VALUE
        var minDistanceToTrash = Float.MAX_VALUE
        var maxStepJump = 0f

        repeat(28) {
            val exitingNodes = composeTestRule
                .onAllNodes(androidx.compose.ui.test.hasTestTag("viewer_exiting_media_card_1"))
                .fetchSemanticsNodes()
            if (exitingNodes.isNotEmpty()) {
                val bounds = exitingNodes.first().boundsInRoot
                val topCenter = Offset((bounds.left + bounds.right) * 0.5f, bounds.top)
                if (bounds.top < viewportInitial.top && bounds.top < topHeaderInitial.bottom) {
                    observedOverHeader = true
                }
                val dist = kotlin.math.hypot(
                    (topCenter.x - trashIconCenterInitial.x).toDouble(),
                    (topCenter.y - trashIconCenterInitial.y).toDouble(),
                ).toFloat()
                if (firstDistanceToTrash == Float.MAX_VALUE) {
                    firstDistanceToTrash = dist
                }
                if (dist < minDistanceToTrash) {
                    minDistanceToTrash = dist
                }
                prevTopCenter?.let { prev ->
                    val step = kotlin.math.hypot(
                        (topCenter.x - prev.x).toDouble(),
                        (topCenter.y - prev.y).toDouble(),
                    ).toFloat()
                    if (step > maxStepJump) maxStepJump = step
                }
                prevTopCenter = topCenter
            }
            val headerNow =
                composeTestRule.onNodeWithTag("viewer_top_header").fetchSemanticsNode().boundsInRoot
            assertEquals(topHeaderInitial.top, headerNow.top, 0.5f)
            assertEquals(topHeaderInitial.bottom, headerNow.bottom, 0.5f)
            composeTestRule.mainClock.advanceTimeBy(16L)
        }

        assertTrue("Exiting card must pass unclipped over viewer_top_header", observedOverHeader)
        assertTrue(
            "Top-center must converge toward Trash Bin icon (start=$firstDistanceToTrash, min=$minDistanceToTrash)",
            minDistanceToTrash < firstDistanceToTrash * 0.25f && minDistanceToTrash < 48f,
        )
        assertTrue("Trajectory must be smooth without abrupt jumps (maxStep=$maxStepJump)", maxStepJump < 220f)

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("viewer_exiting_card_1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("viewer_trash_badge", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `trash_badge_displays_count_and_does_not_appear_for_cancelled_swipes`() {
        val trashed = mutableListOf<MediaItem>()

        composeTestRule.setContent {
            SweepTheme {
                var items by remember { mutableStateOf(sampleItems(3)) }
                MediaViewerScreen(
                    items = items,
                    initialMediaId = 1L,
                    isTrashPreview = false,
                    trashCount = trashed.size,
                    undoRedoState = UndoRedoUiState(canUndo = trashed.isNotEmpty()),
                    onBack = {},
                    onOpenTrash = {},
                    onTrashItem = { item ->
                        trashed.add(item)
                        items = items.filterNot { it.id == item.id }
                    },
                    onRestoreItem = {},
                    onPermanentDeleteItem = {},
                    onUndo = {},
                    onRedo = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("viewer_trash_icon", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("viewer_trash_badge", useUnmergedTree = true).assertDoesNotExist()

        // Cancelled / short upward drag must NOT commit trash or show badge
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            down(center)
            moveBy(Offset(0f, -120f), delayMillis = 250L)
            up()
        }
        composeTestRule.waitForIdle()
        assertTrue(trashed.isEmpty())
        composeTestRule.onNodeWithTag("viewer_trash_badge").assertDoesNotExist()

        // Committed swipe-up trash shows badge with "1"
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            swipeUp(startY = centerY, endY = top + 40f, durationMillis = 100L)
        }
        composeTestRule.waitForIdle()
        assertEquals(1, trashed.size)
        composeTestRule.onNodeWithTag("viewer_trash_badge", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("1", useUnmergedTree = true).assertExists()

        // Second committed trash increments badge to "2"
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            swipeUp(startY = centerY, endY = top + 40f, durationMillis = 100L)
        }
        composeTestRule.waitForIdle()
        assertEquals(2, trashed.size)
        composeTestRule.onNodeWithText("2", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `undo_visually_reverses_swipe_up_and_works_after_navigation_and_consecutively`() {
        val original = sampleItems(4)
        val trashHistory = mutableStateListOf<Pair<Int, MediaItem>>()
        val focusFlow = MutableSharedFlow<Long>(extraBufferCapacity = 4)

        composeTestRule.setContent {
            SweepTheme {
                var items by remember { mutableStateOf(original) }
                MediaViewerScreen(
                    items = items,
                    initialMediaId = 1L,
                    isTrashPreview = false,
                    trashCount = trashHistory.size,
                    undoRedoState = UndoRedoUiState(canUndo = trashHistory.isNotEmpty()),
                    focusMediaIdFlow = focusFlow,
                    onBack = {},
                    onOpenTrash = {},
                    onTrashItem = { item ->
                        val idx = items.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                        trashHistory.add(idx to item)
                        items = items.filterNot { it.id == item.id }
                    },
                    onRestoreItem = {},
                    onPermanentDeleteItem = {},
                    onUndo = {
                        val last = trashHistory.removeAt(trashHistory.lastIndex)
                        val mutable = items.toMutableList()
                        mutable.add(last.first.coerceIn(0, mutable.size), last.second)
                        items = mutable
                        focusFlow.tryEmit(last.second.id)
                    },
                    onRedo = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        val topHeaderBounds =
            composeTestRule.onNodeWithTag("viewer_top_header").fetchSemanticsNode().boundsInRoot

        // 1) Trash item 1, then navigate horizontally to item 3 ("2 / 3")
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            swipeUp(startY = centerY, endY = top + 40f, durationMillis = 100L)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1 / 3").assertIsDisplayed()

        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("2 / 3").assertIsDisplayed()

        // 2) Undo after navigating horizontally -> returns to item 1 and animates down from top header
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("viewer_undo_button").performClick()
        repeat(4) {
            composeTestRule.mainClock.advanceTimeBy(16L)
            if (composeTestRule
                    .onAllNodes(androidx.compose.ui.test.hasTestTag("viewer_undo_returning_card_1"))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            ) {
                return@repeat
            }
        }

        composeTestRule.onNodeWithTag("viewer_undo_returning_card_1").assertExists()
        val firstFrameTop = composeTestRule
            .onNodeWithTag("viewer_undo_returning_card_1")
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue(
            "Expected Undo return card to start above/over header: firstTop=$firstFrameTop, headerBottom=${topHeaderBounds.bottom}",
            firstFrameTop < topHeaderBounds.bottom,
        )

        var lastObservedTop = firstFrameTop
        var movedDownward = false
        repeat(15) {
            composeTestRule.mainClock.advanceTimeBy(16L)
            val nodes = composeTestRule
                .onAllNodes(androidx.compose.ui.test.hasTestTag("viewer_undo_returning_card_1"))
                .fetchSemanticsNodes()
            if (nodes.isNotEmpty()) {
                val currentTop = nodes.first().boundsInRoot.top
                if (currentTop > lastObservedTop + 1f) {
                    movedDownward = true
                }
                lastObservedTop = currentTop
            }
        }
        assertTrue("Expected Undo return card to animate downward toward viewer", movedDownward)

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("viewer_undo_returning_card_1").assertDoesNotExist()
        composeTestRule.onNodeWithText("1 / 4").assertIsDisplayed()
        composeTestRule.onNodeWithTag("viewer_trash_badge").assertDoesNotExist()

        // 3) Trash two items in a row, then trigger two consecutive Undos
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            swipeUp(startY = centerY, endY = top + 40f, durationMillis = 100L)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            swipeUp(startY = centerY, endY = top + 40f, durationMillis = 100L)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1 / 2").assertIsDisplayed()

        composeTestRule.onNodeWithTag("viewer_undo_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1 / 3").assertIsDisplayed()

        composeTestRule.onNodeWithTag("viewer_undo_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1 / 4").assertIsDisplayed()
        assertTrue(trashHistory.isEmpty())
    }
}
