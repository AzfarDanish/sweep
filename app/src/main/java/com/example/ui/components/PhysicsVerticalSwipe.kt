package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class VerticalSwipePhase {
    Idle,
    Dragging,
    Expanded,
    Dismissed,
}

enum class VerticalSwipeDirection {
    /** Resting at 0f; dragging/flicking upward (negative Y) animates to Dismissed. */
    UpToDismiss,

    /** Resting at 0f; dragging/flicking downward (positive Y) animates to Dismissed. */
    DownToDismiss,
}

enum class GestureAxisLock {
    Undecided,
    Vertical,
    Horizontal,
    Rejected,
}

@Immutable
data class PhysicsVerticalSwipeConfig(
    val direction: VerticalSwipeDirection = VerticalSwipeDirection.UpToDismiss,
    val dismissDistanceThresholdPx: Float,
    val minFlickDistancePx: Float,
    val flickVelocityThresholdPx: Float,
    val dismissTargetOffsetPx: Float,
    val overscrollLimitPx: Float = 0f,
    val overscrollResistance: Float = 0.2f,
    val maxHorizontalDriftPx: Float = 0f,
    val horizontalDriftDamping: Float = 0.65f,
    val gestureActivationThresholdPx: Float = 10f,
    val verticalDirectionRatio: Float = 1.3f,
    val horizontalDirectionRatio: Float = 1.3f,
    val dismissStiffness: Float = Spring.StiffnessMedium,
    val dismissDampingRatio: Float = Spring.DampingRatioNoBouncy,
    val settleStiffness: Float = Spring.StiffnessMediumLow,
    val settleDampingRatio: Float = Spring.DampingRatioNoBouncy,
)

@Composable
fun rememberPhysicsVerticalSwipeConfig(
    direction: VerticalSwipeDirection,
    containerHeightPx: Float,
    maxDismissThresholdDp: Dp = 120.dp,
    minFlickDistanceDp: Dp = 48.dp,
    flickVelocityThresholdDpPerSec: Dp = 800.dp,
    overscrollLimitDp: Dp = 18.dp,
    maxHorizontalDriftDp: Dp = 0.dp,
    exitTravelMultiplier: Float = 0.92f,
    gestureActivationThresholdDp: Dp = 4.dp,
    verticalDirectionRatio: Float = 1.3f,
    horizontalDirectionRatio: Float = 1.3f,
): PhysicsVerticalSwipeConfig {
    val density = LocalDensity.current
    val maxDismissPx = with(density) { maxDismissThresholdDp.toPx() }
    val minDismissFloorPx = with(density) { 64.dp.toPx() }
    val minFlickPx = with(density) { minFlickDistanceDp.toPx() }
    val flickVelocityPx = with(density) { flickVelocityThresholdDpPerSec.toPx() }
    val overscrollPx = with(density) { overscrollLimitDp.toPx() }
    val maxDriftPx = with(density) { maxHorizontalDriftDp.toPx() }
    val activationPx = with(density) { gestureActivationThresholdDp.toPx() }.coerceAtLeast(10f)

    return remember(
        direction,
        containerHeightPx,
        maxDismissPx,
        minFlickPx,
        flickVelocityPx,
        overscrollPx,
        maxDriftPx,
        exitTravelMultiplier,
        activationPx,
        verticalDirectionRatio,
        horizontalDirectionRatio,
    ) {
        val safeHeight = containerHeightPx.coerceAtLeast(minDismissFloorPx * 2f)
        val dismissThreshold = minOf(maxDismissPx, (safeHeight * 0.24f).coerceAtLeast(minDismissFloorPx))
        val effectiveMinFlick = minOf(minFlickPx, dismissThreshold * 0.5f)
        val exitTarget = (safeHeight * exitTravelMultiplier).coerceAtLeast(dismissThreshold * 3f)
        PhysicsVerticalSwipeConfig(
            direction = direction,
            dismissDistanceThresholdPx = dismissThreshold,
            minFlickDistancePx = effectiveMinFlick,
            flickVelocityThresholdPx = flickVelocityPx,
            dismissTargetOffsetPx = exitTarget,
            overscrollLimitPx = overscrollPx,
            maxHorizontalDriftPx = maxDriftPx,
            gestureActivationThresholdPx = activationPx,
            verticalDirectionRatio = verticalDirectionRatio,
            horizontalDirectionRatio = horizontalDirectionRatio,
        )
    }
}

@Stable
class PhysicsVerticalSwipeController(
    val direction: VerticalSwipeDirection = VerticalSwipeDirection.UpToDismiss,
    initialOffsetY: Float = 0f,
    initialPhase: VerticalSwipePhase = VerticalSwipePhase.Idle,
) {
    val offsetY: Animatable<Float, AnimationVector1D> = Animatable(initialOffsetY)
    val offsetX: Animatable<Float, AnimationVector1D> = Animatable(0f)

    var phase by mutableStateOf(initialPhase)
        private set

    var axisLock by mutableStateOf(GestureAxisLock.Undecided)
        private set

    val currentOffsetY: Float
        get() = offsetY.value

    val currentOffsetX: Float
        get() = offsetX.value

    val isRunning: Boolean
        get() = offsetY.isRunning || offsetX.isRunning

    private var gestureGeneration: Long = 0L
    private var dispatchedDismissGeneration: Long = -1L
    private var activeAnimationJob: Job? = null

    internal fun updateAxisLock(lock: GestureAxisLock) {
        axisLock = lock
    }

    fun beginOrResumeDrag(scope: CoroutineScope) {
        activeAnimationJob?.cancel()
        activeAnimationJob = null
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offsetY.stop()
            offsetX.stop()
        }
        gestureGeneration++
        axisLock = GestureAxisLock.Vertical
        phase = VerticalSwipePhase.Dragging
    }

    fun dragBy(
        scope: CoroutineScope,
        deltaY: Float,
        config: PhysicsVerticalSwipeConfig,
        deltaX: Float = 0f,
    ) {
        val currentY = offsetY.value
        val nextY = computeNextOffset(currentY, deltaY, config)
        val nextX = if (config.maxHorizontalDriftPx > 0f) {
            (offsetX.value + deltaX * config.horizontalDriftDamping)
                .coerceIn(-config.maxHorizontalDriftPx, config.maxHorizontalDriftPx)
        } else {
            0f
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offsetY.snapTo(nextY)
            if (config.maxHorizontalDriftPx > 0f || offsetX.value != 0f) {
                offsetX.snapTo(nextX)
            }
        }
    }

    internal fun computeNextOffset(
        current: Float,
        deltaY: Float,
        config: PhysicsVerticalSwipeConfig,
    ): Float {
        return when (direction) {
            VerticalSwipeDirection.UpToDismiss -> {
                if (current < 0f || deltaY < 0f) {
                    val raw = current + deltaY
                    if (raw <= 0f) {
                        raw.coerceAtLeast(-config.dismissTargetOffsetPx)
                    } else {
                        (raw * config.overscrollResistance).coerceIn(0f, config.overscrollLimitPx)
                    }
                } else {
                    (current + deltaY * config.overscrollResistance)
                        .coerceIn(0f, config.overscrollLimitPx)
                }
            }

            VerticalSwipeDirection.DownToDismiss -> {
                if (current > 0f || deltaY > 0f) {
                    val raw = current + deltaY
                    if (raw >= 0f) {
                        raw.coerceAtMost(config.dismissTargetOffsetPx)
                    } else {
                        (raw * config.overscrollResistance).coerceIn(-config.overscrollLimitPx, 0f)
                    }
                } else {
                    (current + deltaY * config.overscrollResistance)
                        .coerceIn(-config.overscrollLimitPx, 0f)
                }
            }
        }
    }

    internal fun shouldDismissOnRelease(
        offset: Float,
        velocityY: Float,
        config: PhysicsVerticalSwipeConfig,
    ): Boolean {
        return when (direction) {
            VerticalSwipeDirection.UpToDismiss -> {
                if (velocityY > config.flickVelocityThresholdPx) {
                    false
                } else {
                    offset <= -config.dismissDistanceThresholdPx ||
                        (offset <= -config.minFlickDistancePx && velocityY <= -config.flickVelocityThresholdPx)
                }
            }

            VerticalSwipeDirection.DownToDismiss -> {
                if (velocityY < -config.flickVelocityThresholdPx) {
                    false
                } else {
                    offset >= config.dismissDistanceThresholdPx ||
                        (offset >= config.minFlickDistancePx && velocityY >= config.flickVelocityThresholdPx)
                }
            }
        }
    }

    fun releaseWithVelocity(
        scope: CoroutineScope,
        velocityY: Float,
        config: PhysicsVerticalSwipeConfig,
        velocityX: Float = 0f,
        restPhase: VerticalSwipePhase = VerticalSwipePhase.Idle,
        onDismissed: () -> Unit,
        onSettledRestored: (() -> Unit)? = null,
    ) {
        val token = gestureGeneration
        val dismiss = shouldDismissOnRelease(offsetY.value, velocityY, config)
        activeAnimationJob?.cancel()
        activeAnimationJob = scope.launch {
            if (dismiss) {
                val targetY = if (direction == VerticalSwipeDirection.UpToDismiss) {
                    -config.dismissTargetOffsetPx
                } else {
                    config.dismissTargetOffsetPx
                }
                if (config.maxHorizontalDriftPx > 0f) {
                    val targetX = (offsetX.value + velocityX * 0.08f)
                        .coerceIn(-config.maxHorizontalDriftPx * 1.4f, config.maxHorizontalDriftPx * 1.4f)
                    launch {
                        offsetX.animateTo(
                            targetValue = targetX,
                            initialVelocity = velocityX * 0.5f,
                            animationSpec = spring(
                                dampingRatio = config.dismissDampingRatio,
                                stiffness = config.dismissStiffness,
                            ),
                        )
                    }
                }
                val result = offsetY.animateTo(
                    targetValue = targetY,
                    initialVelocity = velocityY,
                    animationSpec = spring(
                        dampingRatio = config.dismissDampingRatio,
                        stiffness = config.dismissStiffness,
                    ),
                )
                if (result.endReason == AnimationEndReason.Finished && gestureGeneration == token) {
                    phase = VerticalSwipePhase.Dismissed
                    if (dispatchedDismissGeneration != token) {
                        dispatchedDismissGeneration = token
                        onDismissed()
                    }
                }
            } else {
                if (offsetX.value != 0f || velocityX != 0f) {
                    launch {
                        offsetX.animateTo(
                            targetValue = 0f,
                            initialVelocity = velocityX * 0.5f,
                            animationSpec = spring(
                                dampingRatio = config.settleDampingRatio,
                                stiffness = config.settleStiffness,
                            ),
                        )
                    }
                }
                val result = offsetY.animateTo(
                    targetValue = 0f,
                    initialVelocity = velocityY,
                    animationSpec = spring(
                        dampingRatio = config.settleDampingRatio,
                        stiffness = config.settleStiffness,
                    ),
                )
                if (result.endReason == AnimationEndReason.Finished && gestureGeneration == token) {
                    phase = restPhase
                    onSettledRestored?.invoke()
                }
            }
        }
    }

    fun animateToRest(
        scope: CoroutineScope,
        config: PhysicsVerticalSwipeConfig,
        restPhase: VerticalSwipePhase = VerticalSwipePhase.Idle,
        initialVelocity: Float = 0f,
        onSettledRestored: (() -> Unit)? = null,
    ) {
        activeAnimationJob?.cancel()
        val token = ++gestureGeneration
        activeAnimationJob = scope.launch {
            if (offsetX.value != 0f) {
                launch {
                    offsetX.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = config.settleDampingRatio,
                            stiffness = config.settleStiffness,
                        ),
                    )
                }
            }
            val result = offsetY.animateTo(
                targetValue = 0f,
                initialVelocity = initialVelocity,
                animationSpec = spring(
                    dampingRatio = config.settleDampingRatio,
                    stiffness = config.settleStiffness,
                ),
            )
            if (result.endReason == AnimationEndReason.Finished && gestureGeneration == token) {
                phase = restPhase
                onSettledRestored?.invoke()
            }
        }
    }

    fun animateToDismissed(
        scope: CoroutineScope,
        config: PhysicsVerticalSwipeConfig,
        initialVelocity: Float = 0f,
        onDismissed: () -> Unit,
    ) {
        if (phase == VerticalSwipePhase.Dismissed && !offsetY.isRunning) {
            return
        }
        activeAnimationJob?.cancel()
        val token = ++gestureGeneration
        val target = if (direction == VerticalSwipeDirection.UpToDismiss) {
            -config.dismissTargetOffsetPx
        } else {
            config.dismissTargetOffsetPx
        }
        activeAnimationJob = scope.launch {
            val result = offsetY.animateTo(
                targetValue = target,
                initialVelocity = initialVelocity,
                animationSpec = spring(
                    dampingRatio = config.dismissDampingRatio,
                    stiffness = config.dismissStiffness,
                ),
            )
            if (result.endReason == AnimationEndReason.Finished && gestureGeneration == token) {
                phase = VerticalSwipePhase.Dismissed
                if (dispatchedDismissGeneration != token) {
                    dispatchedDismissGeneration = token
                    onDismissed()
                }
            }
        }
    }

    suspend fun snapToRest(restPhase: VerticalSwipePhase = VerticalSwipePhase.Idle) {
        activeAnimationJob?.cancel()
        activeAnimationJob = null
        gestureGeneration++
        dispatchedDismissGeneration = -1L
        offsetY.snapTo(0f)
        offsetX.snapTo(0f)
        axisLock = GestureAxisLock.Undecided
        phase = restPhase
    }

    suspend fun snapToOffset(offset: Float, newPhase: VerticalSwipePhase) {
        activeAnimationJob?.cancel()
        activeAnimationJob = null
        gestureGeneration++
        dispatchedDismissGeneration = -1L
        offsetY.snapTo(offset)
        offsetX.snapTo(0f)
        axisLock = GestureAxisLock.Undecided
        phase = newPhase
    }
}

fun Modifier.physicsVerticalSwipe(
    controller: PhysicsVerticalSwipeController,
    config: PhysicsVerticalSwipeConfig,
    scope: CoroutineScope,
    enabled: Boolean = true,
    key: Any? = Unit,
    restPhase: VerticalSwipePhase = VerticalSwipePhase.Idle,
    canEngage: () -> Boolean = { true },
    allowOverscrollEngageFromRest: Boolean = false,
    pointerPass: PointerEventPass = PointerEventPass.Main,
    onDismissed: () -> Unit,
    onSettledRestored: (() -> Unit)? = null,
): Modifier = composed {
    val latestConfig by rememberUpdatedState(config)
    val latestCanEngage by rememberUpdatedState(canEngage)
    val latestOnDismissed by rememberUpdatedState(onDismissed)
    val latestOnSettledRestored by rememberUpdatedState(onSettledRestored)

    if (!enabled) {
        return@composed this
    }

    pointerInput(controller, key, enabled, pointerPass) {
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = pointerPass)
            if (!latestCanEngage()) {
                return@awaitEachGesture
            }
            if (controller.phase == VerticalSwipePhase.Dismissed && !controller.isRunning) {
                return@awaitEachGesture
            }

            val startX = down.position.x
            val startY = down.position.y
            val caughtInFlight = controller.isRunning && abs(controller.currentOffsetY) > 2f
            var engaged = caughtInFlight
            var axisLock = if (caughtInFlight) {
                GestureAxisLock.Vertical
            } else {
                GestureAxisLock.Undecided
            }
            controller.updateAxisLock(axisLock)
            var settledOnExit = false

            if (caughtInFlight) {
                controller.beginOrResumeDrag(scope)
            }

            val velocityTracker = VelocityTracker().apply {
                addPosition(down.uptimeMillis, down.position)
            }

            try {
                while (true) {
                    val event = awaitPointerEvent(pass = pointerPass)
                    if (!latestCanEngage()) {
                        if (engaged) {
                            settledOnExit = true
                            controller.animateToRest(
                                scope = scope,
                                config = latestConfig,
                                restPhase = restPhase,
                                onSettledRestored = latestOnSettledRestored,
                            )
                        }
                        break
                    }

                    val pressedChanges = event.changes.filter { it.pressed }
                    if (pressedChanges.size > 1) {
                        if (engaged) {
                            settledOnExit = true
                            controller.animateToRest(
                                scope = scope,
                                config = latestConfig,
                                restPhase = restPhase,
                                onSettledRestored = latestOnSettledRestored,
                            )
                        }
                        break
                    }

                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: event.changes.firstOrNull()
                        ?: break

                    if (!change.pressed) {
                        if (engaged) {
                            change.consume()
                            settledOnExit = true
                            val velocity = velocityTracker.calculateVelocity()
                            controller.releaseWithVelocity(
                                scope = scope,
                                velocityY = velocity.y,
                                velocityX = 0f,
                                config = latestConfig,
                                restPhase = restPhase,
                                onDismissed = latestOnDismissed,
                                onSettledRestored = latestOnSettledRestored,
                            )
                        }
                        break
                    }

                    if (change.isConsumed && !engaged) {
                        break
                    }

                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    val delta = change.position - change.previousPosition
                    val totalDx = change.position.x - startX
                    val totalDy = change.position.y - startY
                    val absDx = abs(totalDx)
                    val absDy = abs(totalDy)

                    if (!engaged) {
                        val cfg = latestConfig
                        val activationThreshold = cfg.gestureActivationThresholdPx.coerceAtLeast(1f)
                        val ambiguityCeiling = maxOf(activationThreshold * 4f, touchSlop * 2f)

                        if (absDx < activationThreshold && absDy < activationThreshold) {
                            continue
                        }

                        val verticalDominant = absDy >= activationThreshold &&
                            absDy >= absDx * cfg.verticalDirectionRatio
                        val horizontalDominant = absDx >= activationThreshold &&
                            absDx >= absDy * cfg.horizontalDirectionRatio

                        if (horizontalDominant) {
                            axisLock = GestureAxisLock.Horizontal
                            controller.updateAxisLock(axisLock)
                            break
                        }

                        if (verticalDominant) {
                            val directionalMatch = when {
                                allowOverscrollEngageFromRest -> true
                                controller.direction == VerticalSwipeDirection.UpToDismiss -> totalDy < 0f
                                else -> totalDy > 0f
                            }
                            if (directionalMatch) {
                                axisLock = GestureAxisLock.Vertical
                                controller.updateAxisLock(axisLock)
                                engaged = true
                                controller.beginOrResumeDrag(scope)
                                change.consume()
                                val initialDeltaY = if (caughtInFlight) delta.y else totalDy
                                controller.dragBy(
                                    scope = scope,
                                    deltaY = initialDeltaY,
                                    config = cfg,
                                    deltaX = 0f,
                                )
                            } else {
                                axisLock = GestureAxisLock.Rejected
                                controller.updateAxisLock(axisLock)
                                break
                            }
                        } else if (maxOf(absDx, absDy) >= ambiguityCeiling) {
                            axisLock = GestureAxisLock.Rejected
                            controller.updateAxisLock(axisLock)
                            break
                        }
                    } else {
                        change.consume()
                        controller.dragBy(
                            scope = scope,
                            deltaY = delta.y,
                            config = latestConfig,
                            deltaX = 0f,
                        )
                    }
                }
            } catch (e: CancellationException) {
                if (engaged && !settledOnExit) {
                    settledOnExit = true
                    controller.animateToRest(
                        scope = scope,
                        config = latestConfig,
                        restPhase = restPhase,
                        onSettledRestored = latestOnSettledRestored,
                    )
                }
                throw e
            } finally {
                if (engaged && !settledOnExit) {
                    controller.animateToRest(
                        scope = scope,
                        config = latestConfig,
                        restPhase = restPhase,
                        onSettledRestored = latestOnSettledRestored,
                    )
                }
            }
        }
    }
}
