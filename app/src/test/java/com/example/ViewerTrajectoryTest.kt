package com.example

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.data.UndoRedoUiState
import com.example.model.MediaItem
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.computeTrashBinTrajectoryTransform
import com.example.ui.screens.resolveViewerCardSizePx
import com.example.ui.theme.SweepTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.hypot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xxhdpi")
class ViewerTrajectoryTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `trajectory_math_guides_top_center_smoothly_to_trash_bin_across_viewports_and_aspect_ratios`() {
        val viewports = listOf(
            1080f to 1920f, // compact phone
            2560f to 1400f, // tablet landscape
        )
        val mediaSizes = listOf(
            1080 to 1920, // portrait
            1920 to 1080, // landscape
            1080 to 1080, // square
            480 to 2400, // very tall
            2400 to 400, // very short
        )
        val releaseStates = listOf(
            -70f to -3800f, // fast low-displacement flick
            -360f to -900f, // slow high-displacement drag
            -950f to -2400f, // already high into header (rawDy < 0 case)
        )

        for ((vw, vh) in viewports) {
            val trashCenter = Offset(x = vw - 72f, y = -84f)
            val threshold = vh * 0.14f
            for ((mw, mh) in mediaSizes) {
                val ratio = mw.toFloat() / mh.toFloat()
                val (_, cardH) = resolveViewerCardSizePx(vw, vh, ratio)
                assertTrue(cardH in 1f..vh)

                for ((startY, velY) in releaseStates) {
                    val startTransform = computeTrashBinTrajectoryTransform(
                        progress = 0f,
                        viewportWidthPx = vw,
                        viewportHeightPx = vh,
                        cardHeightPx = cardH,
                        trashBinCenterInViewport = trashCenter,
                        startOffsetY = startY,
                        dismissDistanceThresholdPx = threshold,
                        startVelocityY = velY,
                    )
                    assertEquals(0f, startTransform.translationX, 0.01f)
                    assertEquals(startY, startTransform.translationY, 0.05f)
                    assertEquals(1f, startTransform.alpha, 0.001f)

                    val endTransform = computeTrashBinTrajectoryTransform(
                        progress = 1f,
                        viewportWidthPx = vw,
                        viewportHeightPx = vh,
                        cardHeightPx = cardH,
                        trashBinCenterInViewport = trashCenter,
                        startOffsetY = startY,
                        dismissDistanceThresholdPx = threshold,
                        startVelocityY = velY,
                    )
                    assertEquals(trashCenter.x, endTransform.topCenterX, 0.05f)
                    assertEquals(trashCenter.y, endTransform.topCenterY, 0.05f)
                    assertEquals(0f, endTransform.rotationZ, 0.01f)
                    assertEquals(0f, endTransform.alpha, 0.001f)

                    // Initial launch tangent near u=0 is vertical upward (no abrupt sideways snap)
                    val early = computeTrashBinTrajectoryTransform(
                        progress = 0.02f,
                        viewportWidthPx = vw,
                        viewportHeightPx = vh,
                        cardHeightPx = cardH,
                        trashBinCenterInViewport = trashCenter,
                        startOffsetY = startY,
                        dismissDistanceThresholdPx = threshold,
                        startVelocityY = velY,
                    )
                    val initialDx = abs(early.topCenterX - startTransform.topCenterX)
                    val initialDy = startTransform.topCenterY - early.topCenterY
                    assertTrue("Initial movement should be upward ($initialDy)", initialDy > 0f)
                    assertTrue(
                        "Initial lateral delta ($initialDx) must be tiny vs vertical ($initialDy)",
                        initialDx <= initialDy * 0.2f + 1f,
                    )

                    // Uniform progression has no discontinuous jumps or excessive rotation
                    var prev = startTransform
                    for (step in 1..50) {
                        val u = step / 50f
                        val cur = computeTrashBinTrajectoryTransform(
                            progress = u,
                            viewportWidthPx = vw,
                            viewportHeightPx = vh,
                            cardHeightPx = cardH,
                            trashBinCenterInViewport = trashCenter,
                            startOffsetY = startY,
                            dismissDistanceThresholdPx = threshold,
                            startVelocityY = velY,
                        )
                        val delta = hypot(
                            (cur.topCenterX - prev.topCenterX).toDouble(),
                            (cur.topCenterY - prev.topCenterY).toDouble(),
                        ).toFloat()
                        assertTrue("Step jump too large: $delta", delta < hypot(vw.toDouble(), vh.toDouble()).toFloat() * 0.1f)
                        assertTrue("Rotation exceeded subtle limit: ${cur.rotationZ}", cur.rotationZ in 0f..3.25f)
                        prev = cur
                    }
                }
            }
        }
    }

    @Test
    fun `slow_and_fast_upward_swipes_converge_to_trash_bin_across_all_aspect_ratios_and_undo_reverses`() {
        val aspectItems = listOf(
            mediaItem(id = 101L, width = 1080, height = 1920), // portrait
            mediaItem(id = 102L, width = 1920, height = 1080), // landscape
            mediaItem(id = 103L, width = 1080, height = 1080), // square
            mediaItem(id = 104L, width = 480, height = 2400), // very tall
            mediaItem(id = 105L, width = 2400, height = 400), // very short
        )
        val trashHistory = mutableStateListOf<Pair<Int, MediaItem>>()
        val focusFlow = MutableSharedFlow<Long>(extraBufferCapacity = 4)

        composeTestRule.setContent {
            SweepTheme {
                var items by remember { mutableStateOf(aspectItems) }
                MediaViewerScreen(
                    items = items,
                    initialMediaId = aspectItems.first().id,
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
        val trashCenter = composeTestRule
            .onNodeWithTag("viewer_trash_icon", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .center

        aspectItems.forEachIndexed { index, item ->
            val useFastFlick = index % 2 == 0
            composeTestRule.mainClock.autoAdvance = false

            if (useFastFlick) {
                composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
                    swipeUp(startY = centerY, endY = centerY - 260f, durationMillis = 35L)
                }
            } else {
                // Slow sustained upward drag past dismiss distance threshold
                composeTestRule.onNodeWithTag("viewer_media_card").performTouchInput {
                    down(center)
                    repeat(8) {
                        moveBy(Offset(0f, -65f), delayMillis = 60L)
                    }
                    up()
                }
            }
            composeTestRule.mainClock.advanceTimeBy(16L)

            val overlayTag = "viewer_exiting_media_card_${item.id}"
            composeTestRule.onNodeWithTag(overlayTag).assertExists()

            var startDist = Float.MAX_VALUE
            var minDist = Float.MAX_VALUE
            var midFlightObserved = false
            repeat(38) { frame ->
                val nodes = composeTestRule.onAllNodes(hasTestTag(overlayTag)).fetchSemanticsNodes()
                if (nodes.isNotEmpty()) {
                    if (frame >= 10) midFlightObserved = true
                    val b = nodes.first().boundsInRoot
                    val topCenter = Offset((b.left + b.right) * 0.5f, b.top)
                    val dist = hypot(
                        (topCenter.x - trashCenter.x).toDouble(),
                        (topCenter.y - trashCenter.y).toDouble(),
                    ).toFloat()
                    if (startDist == Float.MAX_VALUE) startDist = dist
                    if (dist < minDist) minDist = dist
                }
                composeTestRule.mainClock.advanceTimeBy(16L)
            }

            assertTrue(
                "Exiting card ${item.id} should animate cleanly past 160ms rather than snapping",
                midFlightObserved,
            )
            assertTrue(
                "Item ${item.id} (${item.width}x${item.height}, fast=$useFastFlick) must converge toward trash center (start=$startDist, min=$minDist)",
                minDist < startDist * 0.25f && minDist < 54f,
            )

            composeTestRule.mainClock.autoAdvance = true
            composeTestRule.waitForIdle()
            assertEquals(index + 1, trashHistory.size)
        }

        // Undo the last trashed item (very short 2400x400) and verify reverse animation returns from Trash Bin
        val lastItem = aspectItems.last()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag("viewer_undo_button").performClick()
        repeat(4) {
            composeTestRule.mainClock.advanceTimeBy(16L)
            if (composeTestRule.onAllNodes(hasTestTag("viewer_undo_returning_media_card_${lastItem.id}"))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            ) {
                return@repeat
            }
        }
        val undoTag = "viewer_undo_returning_media_card_${lastItem.id}"
        composeTestRule.onNodeWithTag(undoTag).assertExists()

        val initialUndoBounds = composeTestRule.onNodeWithTag(undoTag).fetchSemanticsNode().boundsInRoot
        val initialUndoTopCenter = Offset(
            (initialUndoBounds.left + initialUndoBounds.right) * 0.5f,
            initialUndoBounds.top,
        )
        val initialDistFromTrash = hypot(
            (initialUndoTopCenter.x - trashCenter.x).toDouble(),
            (initialUndoTopCenter.y - trashCenter.y).toDouble(),
        ).toFloat()

        var maxDistFromTrash = initialDistFromTrash
        repeat(16) {
            composeTestRule.mainClock.advanceTimeBy(16L)
            val nodes = composeTestRule.onAllNodes(hasTestTag(undoTag)).fetchSemanticsNodes()
            if (nodes.isNotEmpty()) {
                val b = nodes.first().boundsInRoot
                val tc = Offset((b.left + b.right) * 0.5f, b.top)
                val dist = hypot((tc.x - trashCenter.x).toDouble(), (tc.y - trashCenter.y).toDouble()).toFloat()
                if (dist > maxDistFromTrash) maxDistFromTrash = dist
            }
        }
        assertTrue(
            "Undo card must start near trash ($initialDistFromTrash) and reverse back down ($maxDistFromTrash)",
            maxDistFromTrash > initialDistFromTrash + 100f,
        )

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        assertEquals(aspectItems.size - 1, trashHistory.size)
    }

    private fun mediaItem(id: Long, width: Int, height: Int): MediaItem = MediaItem(
        id = id,
        contentUri = Uri.parse("content://media/external/images/media/$id"),
        displayName = "IMG_$id.jpg",
        mimeType = "image/jpeg",
        dateTakenMillis = 1_700_000_000_000L + id * 1000L,
        dateModifiedMillis = 1_700_000_000_000L + id * 1000L,
        sizeBytes = 4096L,
        durationMillis = 0L,
        width = width,
        height = height,
        bucketId = 10L,
        bucketName = "Camera",
        relativePath = "DCIM/Camera/",
        isVideo = false,
        isTrashed = false,
    )
}
