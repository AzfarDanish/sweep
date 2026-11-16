package com.example

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.example.data.UndoRedoUiState
import com.example.model.MediaItem
import com.example.ui.components.GestureAxisLock
import com.example.ui.components.MediaInfoSheet
import com.example.ui.components.PhysicsVerticalSwipeController
import com.example.ui.components.VerticalSwipeDirection
import com.example.ui.components.VerticalSwipePhase
import com.example.ui.components.physicsVerticalSwipe
import com.example.ui.components.rememberPhysicsVerticalSwipeConfig
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.computeStackedActiveCardTransform
import com.example.ui.screens.computeStackedUnderlyingCardTransform
import com.example.ui.theme.SweepTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class PhysicsVerticalSwipeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleItems(count: Int = 3): List<MediaItem> = (1..count).map { index ->
        MediaItem(
            id = index.toLong(),
            contentUri = Uri.parse("content://media/external/images/media/$index"),
            displayName = "IMG_000$index.jpg",
            mimeType = "image/jpeg",
            dateTakenMillis = 1_700_000_000_000L + index * 1000L,
            dateModifiedMillis = 1_700_000_000_000L + index * 1000L,
            sizeBytes = 1024L * index,
            durationMillis = 0L,
            width = 1920,
            height = 1080,
            bucketId = 10L,
            bucketName = "Camera",
            relativePath = "DCIM/Camera/",
            isVideo = false,
            isTrashed = false,
        )
    }

    @Test
    fun `1_slow_upward_drag_past_threshold_dismisses_and_triggers_trash_once`() {
        val trashed = mutableListOf<MediaItem>()
        composeTestRule.setContent {
            SweepTheme {
                var items by remember { mutableStateOf(sampleItems()) }
                MediaViewerScreen(
                    items = items,
                    initialMediaId = 1L,
                    isTrashPreview = false,
                    trashCount = trashed.size,
                    undoRedoState = UndoRedoUiState(),
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
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            down(center)
            // Slow upward drag exceeding 120.dp threshold (~600px)
            moveBy(Offset(0f, -500f), delayMillis = 400L)
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(1, trashed.size)
        assertEquals(1L, trashed.first().id)
        // Next item is now visible
        composeTestRule.onNodeWithText("1 / 2").assertIsDisplayed()
    }

    @Test
    fun `1b_slow_upward_drag_from_bottom_bar_handle_triggers_trash_once`() {
        val trashed = mutableListOf<MediaItem>()
        composeTestRule.setContent {
            SweepTheme {
                var items by remember { mutableStateOf(sampleItems()) }
                MediaViewerScreen(
                    items = items,
                    initialMediaId = 1L,
                    isTrashPreview = false,
                    trashCount = trashed.size,
                    undoRedoState = UndoRedoUiState(),
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
        composeTestRule.onNodeWithTag("viewer_bottom_bar").performTouchInput {
            down(center)
            moveBy(Offset(0f, -520f), delayMillis = 350L)
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(1, trashed.size)
        assertEquals(1L, trashed.first().id)
    }

    @Test
    fun `2_fast_upward_swipe_with_short_distance_triggers_dismissal_once`() {
        val trashed = mutableListOf<MediaItem>()
        composeTestRule.setContent {
            SweepTheme {
                MediaViewerScreen(
                    items = sampleItems(),
                    initialMediaId = 1L,
                    isTrashPreview = false,
                    trashCount = 0,
                    undoRedoState = UndoRedoUiState(),
                    onBack = {},
                    onOpenTrash = {},
                    onTrashItem = { item -> trashed.add(item) },
                    onRestoreItem = {},
                    onPermanentDeleteItem = {},
                    onUndo = {},
                    onRedo = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            // Distance ~70dp (~210px, well below 120dp threshold), but very high upward velocity
            swipeUp(
                startY = centerY,
                endY = centerY - 220f,
                durationMillis = 35L,
            )
        }
        composeTestRule.waitForIdle()

        assertEquals(1, trashed.size)
        assertEquals(1L, trashed.first().id)
    }

    @Test
    fun `3_short_slow_upward_swipe_returns_smoothly_to_idle_without_trashing`() {
        var dismissCount = 0
        lateinit var controller: PhysicsVerticalSwipeController

        composeTestRule.setContent {
            SweepTheme {
                val scope = rememberCoroutineScope()
                val config = rememberPhysicsVerticalSwipeConfig(
                    direction = VerticalSwipeDirection.UpToDismiss,
                    containerHeightPx = 1800f,
                )
                controller = remember { PhysicsVerticalSwipeController(VerticalSwipeDirection.UpToDismiss) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .physicsVerticalSwipe(
                            controller = controller,
                            config = config,
                            scope = scope,
                            onDismissed = { dismissCount++ },
                        )
                        .graphicsLayer { translationY = controller.offsetY.value }
                        .testTag("swipe_card"),
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("swipe_card").performTouchInput {
            down(center)
            // Short slow drag (~140px < 120dp = 360px threshold at xxhdpi)
            moveBy(Offset(0f, -140f), delayMillis = 450L)
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(0, dismissCount)
        assertEquals(VerticalSwipePhase.Idle, controller.phase)
        assertEquals(0f, controller.currentOffsetY, 0.5f)
    }

    @Test
    fun `4_long_upward_swipe_settles_in_dismissed_and_fires_callback_once`() {
        var dismissCount = 0
        lateinit var controller: PhysicsVerticalSwipeController

        composeTestRule.setContent {
            SweepTheme {
                val scope = rememberCoroutineScope()
                val config = rememberPhysicsVerticalSwipeConfig(
                    direction = VerticalSwipeDirection.UpToDismiss,
                    containerHeightPx = 1800f,
                )
                controller = remember { PhysicsVerticalSwipeController(VerticalSwipeDirection.UpToDismiss) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .graphicsLayer { translationY = controller.offsetY.value }
                        .physicsVerticalSwipe(
                            controller = controller,
                            config = config,
                            scope = scope,
                            onDismissed = { dismissCount++ },
                        )
                        .testTag("swipe_card"),
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("swipe_card").performTouchInput {
            swipeUp(startY = bottom - 50f, endY = top + 50f, durationMillis = 250L)
        }
        composeTestRule.waitForIdle()

        assertEquals(1, dismissCount)
        assertEquals(VerticalSwipePhase.Dismissed, controller.phase)
    }

    @Test
    fun `5_downward_reversal_after_crossing_threshold_returns_to_idle_and_restores_controls`() {
        val trashed = mutableListOf<MediaItem>()
        composeTestRule.setContent {
            SweepTheme {
                MediaViewerScreen(
                    items = sampleItems(),
                    initialMediaId = 1L,
                    isTrashPreview = false,
                    trashCount = 0,
                    undoRedoState = UndoRedoUiState(),
                    onBack = {},
                    onOpenTrash = {},
                    onTrashItem = { trashed.add(it) },
                    onRestoreItem = {},
                    onPermanentDeleteItem = {},
                    onUndo = {},
                    onRedo = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            down(center)
            // Drag upward past threshold, then reverse downward back to origin
            moveBy(Offset(0f, -450f), delayMillis = 200L)
            moveBy(Offset(0f, 420f), delayMillis = 220L)
            up()
        }
        composeTestRule.waitForIdle()

        assertTrue(trashed.isEmpty())
        composeTestRule.onNodeWithTag("viewer_bottom_bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 / 3").assertIsDisplayed()
    }

    @Test
    fun `5b_media_info_sheet_supports_downward_dismissal_and_upward_reversal`() {
        var dismissCount = 0
        val item = sampleItems().first()
        composeTestRule.setContent {
            SweepTheme {
                MediaInfoSheet(
                    item = item,
                    onDismiss = { dismissCount++ },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("media_info_sheet").assertIsDisplayed()

        // Drag down slightly and reverse back up -> stays open
        composeTestRule.onNodeWithTag("media_info_drag_handle").performTouchInput {
            down(center)
            moveBy(Offset(0f, 220f), delayMillis = 150L)
            moveBy(Offset(0f, -200f), delayMillis = 150L)
            up()
        }
        composeTestRule.waitForIdle()
        assertEquals(0, dismissCount)
        composeTestRule.onNodeWithTag("media_info_sheet").assertIsDisplayed()

        // Drag down decisively -> dismisses once
        composeTestRule.onNodeWithTag("media_info_sheet").performTouchInput {
            down(center)
            moveBy(Offset(0f, 520f), delayMillis = 180L)
            up()
        }
        composeTestRule.waitForIdle()
        assertEquals(1, dismissCount)
    }

    @Test
    fun `6_interrupted_drag_by_multitouch_settles_cleanly_to_idle`() {
        var dismissCount = 0
        lateinit var controller: PhysicsVerticalSwipeController

        composeTestRule.setContent {
            SweepTheme {
                val scope = rememberCoroutineScope()
                val config = rememberPhysicsVerticalSwipeConfig(
                    direction = VerticalSwipeDirection.UpToDismiss,
                    containerHeightPx = 1800f,
                )
                controller = remember { PhysicsVerticalSwipeController(VerticalSwipeDirection.UpToDismiss) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .graphicsLayer { translationY = controller.offsetY.value }
                        .physicsVerticalSwipe(
                            controller = controller,
                            config = config,
                            scope = scope,
                            onDismissed = { dismissCount++ },
                        )
                        .testTag("swipe_card"),
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("swipe_card").performTouchInput {
            down(pointerId = 0, position = center)
            moveBy(pointerId = 0, delta = Offset(0f, -260f), delayMillis = 120L)
            // Second finger touches down mid-drag -> interrupts single-finger drag
            down(pointerId = 1, position = center + Offset(40f, 40f))
            up(pointerId = 0)
            up(pointerId = 1)
        }
        composeTestRule.waitForIdle()

        assertEquals(0, dismissCount)
        assertEquals(VerticalSwipePhase.Idle, controller.phase)
        assertEquals(0f, controller.currentOffsetY, 0.5f)
    }

    @Test
    fun `7_interrupted_spring_animation_can_be_caught_and_settled_cleanly`() {
        var dismissCount = 0
        lateinit var controller: PhysicsVerticalSwipeController

        composeTestRule.setContent {
            SweepTheme {
                val scope = rememberCoroutineScope()
                val config = rememberPhysicsVerticalSwipeConfig(
                    direction = VerticalSwipeDirection.UpToDismiss,
                    containerHeightPx = 1800f,
                )
                controller = remember { PhysicsVerticalSwipeController(VerticalSwipeDirection.UpToDismiss) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .graphicsLayer { translationY = controller.offsetY.value }
                        .physicsVerticalSwipe(
                            controller = controller,
                            config = config,
                            scope = scope,
                            onDismissed = { dismissCount++ },
                        )
                        .testTag("swipe_card"),
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.autoAdvance = false

        // Start a short upward drag and release so a return spring begins
        composeTestRule.onNodeWithTag("swipe_card").performTouchInput {
            down(center)
            moveBy(Offset(0f, -240f), delayMillis = 200L)
            up()
        }
        composeTestRule.mainClock.advanceTimeBy(32L)
        assertTrue(controller.currentOffsetY < -10f)

        // Catch the card mid-spring and pull it past dismissal threshold
        composeTestRule.onNodeWithTag("swipe_card").performTouchInput {
            down(center)
            moveBy(Offset(0f, -450f), delayMillis = 150L)
            up()
        }

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        assertEquals(1, dismissCount)
        assertEquals(VerticalSwipePhase.Dismissed, controller.phase)
    }

    @Test
    fun `8_horizontal_swipe_navigates_pager_without_triggering_vertical_dismissal`() {
        val trashed = mutableListOf<MediaItem>()
        composeTestRule.setContent {
            SweepTheme {
                MediaViewerScreen(
                    items = sampleItems(3),
                    initialMediaId = 1L,
                    isTrashPreview = false,
                    trashCount = 0,
                    undoRedoState = UndoRedoUiState(),
                    onBack = {},
                    onOpenTrash = {},
                    onTrashItem = { trashed.add(it) },
                    onRestoreItem = {},
                    onPermanentDeleteItem = {},
                    onUndo = {},
                    onRedo = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1 / 3").assertIsDisplayed()

        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()

        assertTrue(trashed.isEmpty())
        composeTestRule.onNodeWithText("2 / 3").assertIsDisplayed()
    }

    @Test
    fun `9_repeated_gestures_and_sequential_trashes_work_reliably_without_duplicate_callbacks`() {
        val trashed = mutableListOf<MediaItem>()
        composeTestRule.setContent {
            SweepTheme {
                var items by remember { mutableStateOf(sampleItems(3)) }
                MediaViewerScreen(
                    items = items,
                    initialMediaId = 1L,
                    isTrashPreview = false,
                    trashCount = trashed.size,
                    undoRedoState = UndoRedoUiState(),
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

        // Two short bounce-back swipes first
        repeat(2) {
            composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
                down(center)
                moveBy(Offset(0f, -120f), delayMillis = 250L)
                up()
            }
            composeTestRule.waitForIdle()
        }
        assertEquals(0, trashed.size)

        // Trash item 1
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            swipeUp(startY = centerY, endY = top + 40f, durationMillis = 120L)
        }
        composeTestRule.waitForIdle()
        assertEquals(listOf(1L), trashed.map { it.id })

        // Trash item 2
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            swipeUp(startY = centerY, endY = top + 40f, durationMillis = 120L)
        }
        composeTestRule.waitForIdle()
        assertEquals(listOf(1L, 2L), trashed.map { it.id })
        composeTestRule.onNodeWithText("1 / 1").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w320dp-h560dp-mdpi")
    fun `10a_compact_screen_size_scales_thresholds_and_settles_cleanly`() {
        val trashed = mutableListOf<MediaItem>()
        composeTestRule.setContent {
            SweepTheme {
                MediaViewerScreen(
                    items = sampleItems(2),
                    initialMediaId = 1L,
                    isTrashPreview = false,
                    trashCount = 0,
                    undoRedoState = UndoRedoUiState(),
                    onBack = {},
                    onOpenTrash = {},
                    onTrashItem = { trashed.add(it) },
                    onRestoreItem = {},
                    onPermanentDeleteItem = {},
                    onUndo = {},
                    onRedo = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        // Short slow drag stays idle
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            down(center)
            moveBy(Offset(0f, -35f), delayMillis = 250L)
            up()
        }
        composeTestRule.waitForIdle()
        assertEquals(0, trashed.size)

        // Decisive upward swipe dismisses once
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            swipeUp(startY = centerY, endY = top + 20f, durationMillis = 100L)
        }
        composeTestRule.waitForIdle()
        assertEquals(1, trashed.size)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w840dp-h1100dp-xhdpi")
    fun `10b_tablet_screen_size_scales_thresholds_and_settles_cleanly`() {
        val trashed = mutableListOf<MediaItem>()
        composeTestRule.setContent {
            SweepTheme {
                MediaViewerScreen(
                    items = sampleItems(2),
                    initialMediaId = 1L,
                    isTrashPreview = false,
                    trashCount = 0,
                    undoRedoState = UndoRedoUiState(),
                    onBack = {},
                    onOpenTrash = {},
                    onTrashItem = { trashed.add(it) },
                    onRestoreItem = {},
                    onPermanentDeleteItem = {},
                    onUndo = {},
                    onRedo = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            swipeUp(startY = centerY, endY = top + 80f, durationMillis = 120L)
        }
        composeTestRule.waitForIdle()
        assertEquals(1, trashed.size)
    }

    @Test
    fun `11_stacked_card_transforms_keep_horizontal_locked_to_zero_and_interpolate_underlying_reveal`() {
        val thresholdPx = 360f
        val targetOffsetPx = 1800f
        val restingOffsetPx = 72f

        val atRestActive = computeStackedActiveCardTransform(
            offsetY = 0f,
            dismissDistanceThresholdPx = thresholdPx,
            dismissTargetOffsetPx = targetOffsetPx,
        )
        assertEquals(0f, atRestActive.translationX, 0.001f)
        assertEquals(0f, atRestActive.translationY, 0.001f)
        assertEquals(1f, atRestActive.scale, 0.001f)
        assertEquals(0f, atRestActive.rotationZ, 0.001f)

        val atRestUnderlying = computeStackedUnderlyingCardTransform(
            offsetY = 0f,
            dismissDistanceThresholdPx = thresholdPx,
            restingOffsetPx = restingOffsetPx,
        )
        assertEquals(0f, atRestUnderlying.revealProgress, 0.001f)
        assertEquals(0.92f, atRestUnderlying.scale, 0.001f)
        assertEquals(restingOffsetPx, atRestUnderlying.translationY, 0.001f)
        assertEquals(0.45f, atRestUnderlying.dimAlpha, 0.001f)

        val midDragActive = computeStackedActiveCardTransform(
            offsetY = -thresholdPx,
            dismissDistanceThresholdPx = thresholdPx,
            dismissTargetOffsetPx = targetOffsetPx,
        )
        assertEquals(0f, midDragActive.translationX, 0.001f)
        assertEquals(-thresholdPx, midDragActive.translationY, 0.001f)
        assertTrue(midDragActive.scale in 0.85f..0.95f)
        assertTrue(midDragActive.rotationZ in 0f..4f)
        assertTrue(midDragActive.rotationZ > 0f)

        val midDragUnderlying = computeStackedUnderlyingCardTransform(
            offsetY = -thresholdPx * 0.725f,
            dismissDistanceThresholdPx = thresholdPx,
            restingOffsetPx = restingOffsetPx,
        )
        assertEquals(0.5f, midDragUnderlying.revealProgress, 0.01f)
        assertEquals(0.96f, midDragUnderlying.scale, 0.01f)
        assertEquals(restingOffsetPx * 0.5f, midDragUnderlying.translationY, 0.5f)
        assertEquals(0.225f, midDragUnderlying.dimAlpha, 0.01f)

        val fullExitUnderlying = computeStackedUnderlyingCardTransform(
            offsetY = -targetOffsetPx,
            dismissDistanceThresholdPx = thresholdPx,
            restingOffsetPx = restingOffsetPx,
        )
        assertEquals(1f, fullExitUnderlying.revealProgress, 0.001f)
        assertEquals(1f, fullExitUnderlying.scale, 0.001f)
        assertEquals(0f, fullExitUnderlying.translationY, 0.001f)
        assertEquals(0f, fullExitUnderlying.dimAlpha, 0.001f)
    }

    @Test
    fun `12_underlying_card_is_prestaged_for_next_item_and_omitted_on_last_item_or_trash_preview`() {
        composeTestRule.setContent {
            SweepTheme {
                var items by remember { mutableStateOf(sampleItems(2)) }
                var previewTrash by remember { mutableStateOf(false) }
                MediaViewerScreen(
                    items = items,
                    initialMediaId = 1L,
                    isTrashPreview = previewTrash,
                    trashCount = 0,
                    undoRedoState = UndoRedoUiState(),
                    onBack = {},
                    onOpenTrash = { previewTrash = !previewTrash },
                    onTrashItem = { item -> items = items.filterNot { it.id == item.id } },
                    onRestoreItem = {},
                    onPermanentDeleteItem = {},
                    onUndo = {},
                    onRedo = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        // When 2 items exist and we are on item 1, underlying card (item 2) is staged
        composeTestRule.onNodeWithTag("viewer_underlying_card").assertExists()
        composeTestRule.onNodeWithTag("viewer_underlying_dim").assertExists()

        // Toggle trash preview via test button? Instead test trash preview directly via swipe to last item
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            swipeUp(startY = centerY, endY = top + 40f, durationMillis = 110L)
        }
        composeTestRule.waitForIdle()

        // Now only 1 item remains -> no next item underneath
        composeTestRule.onNodeWithText("1 / 1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("viewer_underlying_card").assertDoesNotExist()
    }

    @Test
    fun `13_underlying_card_progressively_reveals_during_upward_drag_and_restores_on_downward_reversal`() {
        val trashed = mutableListOf<MediaItem>()
        composeTestRule.setContent {
            SweepTheme {
                MediaViewerScreen(
                    items = sampleItems(3),
                    initialMediaId = 1L,
                    isTrashPreview = false,
                    trashCount = 0,
                    undoRedoState = UndoRedoUiState(),
                    onBack = {},
                    onOpenTrash = {},
                    onTrashItem = { trashed.add(it) },
                    onRestoreItem = {},
                    onPermanentDeleteItem = {},
                    onUndo = {},
                    onRedo = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        val activeRestingBounds = composeTestRule.onNodeWithTag("viewer_media_card").getUnclippedBoundsInRoot()
        val underlyingRestingBounds = composeTestRule.onNodeWithTag("viewer_underlying_card").getUnclippedBoundsInRoot()

        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            down(center)
            // Realistic multi-frame upward drag with subtle horizontal drift
            repeat(10) {
                moveBy(Offset(9f, -42f), delayMillis = 16L)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("viewer_trash_pill").assertIsDisplayed()
        val draggedActiveRightBounds = composeTestRule.onNodeWithTag("viewer_media_card").getUnclippedBoundsInRoot()
        val draggedUnderlyingBounds = composeTestRule.onNodeWithTag("viewer_underlying_card").getUnclippedBoundsInRoot()
        assertTrue(
            "Expected active top to move up: dragged=$draggedActiveRightBounds, resting=$activeRestingBounds",
            draggedActiveRightBounds.top < activeRestingBounds.top,
        )
        assertTrue(
            "Expected underlying top to rise: dragged=$draggedUnderlyingBounds, resting=$underlyingRestingBounds",
            draggedUnderlyingBounds.top < underlyingRestingBounds.top,
        )

        // Reverse finger downward back past origin and release -> restores cleanly
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            repeat(10) {
                moveBy(Offset(-9f, 40f), delayMillis = 16L)
            }
            up()
        }
        composeTestRule.waitForIdle()

        assertTrue(trashed.isEmpty())
        val restoredUnderlyingBounds = composeTestRule.onNodeWithTag("viewer_underlying_card").getUnclippedBoundsInRoot()
        assertEquals(underlyingRestingBounds.top.value, restoredUnderlyingBounds.top.value, 1.5f)
        composeTestRule.onNodeWithText("1 / 3").assertIsDisplayed()

        // Drag upward with opposite (left) horizontal deviation -> identical vertical-only active X/Y bounds
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            down(center)
            repeat(10) {
                moveBy(Offset(-9f, -42f), delayMillis = 16L)
            }
        }
        composeTestRule.waitForIdle()
        val draggedActiveLeftBounds = composeTestRule.onNodeWithTag("viewer_media_card").getUnclippedBoundsInRoot()
        assertEquals(draggedActiveRightBounds.left.value, draggedActiveLeftBounds.left.value, 0.5f)
        assertEquals(draggedActiveRightBounds.top.value, draggedActiveLeftBounds.top.value, 0.5f)

        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            repeat(10) {
                moveBy(Offset(9f, 40f), delayMillis = 16L)
            }
            up()
        }
        composeTestRule.waitForIdle()
        assertTrue(trashed.isEmpty())
    }

    @Test
    fun `14_trash_preview_does_not_stage_underlying_card_or_allow_vertical_dismissal`() {
        val trashed = mutableListOf<MediaItem>()
        composeTestRule.setContent {
            SweepTheme {
                MediaViewerScreen(
                    items = sampleItems(3),
                    initialMediaId = 1L,
                    isTrashPreview = true,
                    trashCount = 3,
                    undoRedoState = UndoRedoUiState(),
                    onBack = {},
                    onOpenTrash = {},
                    onTrashItem = { trashed.add(it) },
                    onRestoreItem = {},
                    onPermanentDeleteItem = {},
                    onUndo = {},
                    onRedo = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("viewer_underlying_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            swipeUp(startY = centerY, endY = top + 40f, durationMillis = 110L)
        }
        composeTestRule.waitForIdle()
        assertTrue(trashed.isEmpty())
    }

    @Test
    fun `15_upward_swipes_with_slight_left_and_right_deviation_lock_vertical_and_trash`() {
        val trashed = mutableListOf<MediaItem>()
        composeTestRule.setContent {
            SweepTheme {
                var items by remember { mutableStateOf(sampleItems(3)) }
                MediaViewerScreen(
                    items = items,
                    initialMediaId = 1L,
                    isTrashPreview = false,
                    trashCount = trashed.size,
                    undoRedoState = UndoRedoUiState(),
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

        // Slight left deviation (8px left per 30px up -> ~15 deg, ratio 3.75 > 1.3)
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            down(center)
            repeat(15) {
                moveBy(Offset(-8f, -30f), delayMillis = 16L)
            }
            up()
        }
        composeTestRule.waitForIdle()
        assertEquals(listOf(1L), trashed.map { it.id })
        composeTestRule.onNodeWithText("1 / 2").assertIsDisplayed()

        // Slight right deviation (8px right per 30px up -> ~15 deg, ratio 3.75 > 1.3)
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            down(center)
            repeat(15) {
                moveBy(Offset(8f, -30f), delayMillis = 16L)
            }
            up()
        }
        composeTestRule.waitForIdle()
        assertEquals(listOf(1L, 2L), trashed.map { it.id })
        composeTestRule.onNodeWithText("1 / 1").assertIsDisplayed()
    }

    @Test
    fun `16_vertical_axis_lock_ignores_subsequent_large_horizontal_wobble`() {
        var dismissCount = 0
        lateinit var controller: PhysicsVerticalSwipeController

        composeTestRule.setContent {
            SweepTheme {
                val scope = rememberCoroutineScope()
                val config = rememberPhysicsVerticalSwipeConfig(
                    direction = VerticalSwipeDirection.UpToDismiss,
                    containerHeightPx = 1800f,
                )
                controller = remember { PhysicsVerticalSwipeController(VerticalSwipeDirection.UpToDismiss) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .physicsVerticalSwipe(
                            controller = controller,
                            config = config,
                            scope = scope,
                            onDismissed = { dismissCount++ },
                        )
                        .graphicsLayer {
                            translationX = controller.currentOffsetX
                            translationY = controller.currentOffsetY
                        }
                        .testTag("swipe_card"),
                )
            }
        }

        composeTestRule.waitForIdle()
        val restingBounds = composeTestRule.onNodeWithTag("swipe_card").getUnclippedBoundsInRoot()

        composeTestRule.onNodeWithTag("swipe_card").performTouchInput {
            down(center)
            // Initial upward stroke locks Vertical axis
            moveBy(Offset(2f, -30f), delayMillis = 16L)
            // Large incidental horizontal wobble while continuing upward
            repeat(10) {
                moveBy(Offset(25f, -45f), delayMillis = 16L)
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(GestureAxisLock.Vertical, controller.axisLock)
        assertEquals(0f, controller.currentOffsetX, 0.01f)
        assertTrue(controller.currentOffsetY < -360f)
        val draggedBounds = composeTestRule.onNodeWithTag("swipe_card").getUnclippedBoundsInRoot()
        assertEquals(restingBounds.left.value, draggedBounds.left.value, 0.5f)
        assertTrue(draggedBounds.top < restingBounds.top)

        composeTestRule.onNodeWithTag("swipe_card").performTouchInput { up() }
        composeTestRule.waitForIdle()
        assertEquals(1, dismissCount)
    }

    @Test
    fun `17_ambiguous_diagonal_downward_horizontal_and_tiny_gestures_never_delete`() {
        val trashed = mutableListOf<MediaItem>()
        composeTestRule.setContent {
            SweepTheme {
                MediaViewerScreen(
                    items = sampleItems(3),
                    initialMediaId = 1L,
                    isTrashPreview = false,
                    trashCount = 0,
                    undoRedoState = UndoRedoUiState(),
                    onBack = {},
                    onOpenTrash = {},
                    onTrashItem = { trashed.add(it) },
                    onRestoreItem = {},
                    onPermanentDeleteItem = {},
                    onUndo = {},
                    onRedo = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        // 1) Short incomplete upward swipe (above activation 10px, below dismiss threshold) -> restores cleanly
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            down(center)
            repeat(4) {
                moveBy(Offset(2f, -20f), delayMillis = 40L)
            }
            up()
        }
        composeTestRule.waitForIdle()
        assertTrue(trashed.isEmpty())

        // 2) Downward swipe -> never deletes
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            down(center)
            repeat(10) {
                moveBy(Offset(0f, 45f), delayMillis = 16L)
            }
            up()
        }
        composeTestRule.waitForIdle()
        assertTrue(trashed.isEmpty())

        // 3) 45-degree ambiguous diagonal upward swipe (dx == -dy) -> never deletes
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            down(center)
            repeat(12) {
                moveBy(Offset(24f, -24f), delayMillis = 16L)
            }
            up()
        }
        composeTestRule.waitForIdle()
        assertTrue(trashed.isEmpty())

        // 4) Horizontal left & right swipes -> page navigation only, never deletes
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("2 / 3").assertIsDisplayed()
        assertTrue(trashed.isEmpty())

        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput { swipeRight(startX = left + 40f, endX = right - 40f) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1 / 3").assertIsDisplayed()
        assertTrue(trashed.isEmpty())

        // 5) Very short movement below activation threshold (8px up) -> never deletes
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            down(center)
            moveBy(Offset(2f, -8f), delayMillis = 50L)
            up()
        }
        composeTestRule.waitForIdle()
        assertTrue(trashed.isEmpty())
    }

    @Test
    fun `18_sub_activation_threshold_does_not_engage_or_move_controller`() {
        var dismissCount = 0
        lateinit var controller: PhysicsVerticalSwipeController

        composeTestRule.setContent {
            SweepTheme {
                val scope = rememberCoroutineScope()
                val config = rememberPhysicsVerticalSwipeConfig(
                    direction = VerticalSwipeDirection.UpToDismiss,
                    containerHeightPx = 1800f,
                )
                controller = remember { PhysicsVerticalSwipeController(VerticalSwipeDirection.UpToDismiss) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .physicsVerticalSwipe(
                            controller = controller,
                            config = config,
                            scope = scope,
                            onDismissed = { dismissCount++ },
                        )
                        .graphicsLayer { translationY = controller.currentOffsetY }
                        .testTag("swipe_card"),
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("swipe_card").performTouchInput {
            down(center)
            moveBy(Offset(3f, -8f), delayMillis = 40L)
        }
        composeTestRule.waitForIdle()

        assertEquals(GestureAxisLock.Undecided, controller.axisLock)
        assertEquals(VerticalSwipePhase.Idle, controller.phase)
        assertEquals(0f, controller.currentOffsetY, 0.01f)

        composeTestRule.onNodeWithTag("swipe_card").performTouchInput { up() }
        composeTestRule.waitForIdle()
        assertEquals(0, dismissCount)
    }

    @Test
    fun `19_fast_diagonal_wobble_upward_still_locks_vertical_ahead_of_pager`() {
        val trashed = mutableListOf<MediaItem>()
        composeTestRule.setContent {
            SweepTheme {
                var items by remember { mutableStateOf(sampleItems(3)) }
                MediaViewerScreen(
                    items = items,
                    initialMediaId = 1L,
                    isTrashPreview = false,
                    trashCount = trashed.size,
                    undoRedoState = UndoRedoUiState(),
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
        // Single-jump diagonal upward drag (90px right, 420px up -> ratio 4.67 >= 1.3)
        // Must beat HorizontalPager's touchSlop via Initial pass axis locking and trash item 1.
        composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
            down(center)
            moveBy(Offset(90f, -420f), delayMillis = 80L)
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(listOf(1L), trashed.map { it.id })
        composeTestRule.onNodeWithText("1 / 2").assertIsDisplayed()
    }
}
