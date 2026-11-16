package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
    private val offsetYState: MutableFloatState = mutableFloatStateOf(initialOffsetY)
    private val offsetXState: MutableFloatState = mutableFloatStateOf(0f)

    /**
     * Backed by snapshot state, but written directly from the pointer-event coroutine and read
     * from [androidx.compose.ui.graphics.graphicsLayer] blocks. This keeps dragging synchronous
     * with input and limits gesture-driven invalidation to the draw phase (no recomposition or
     * layout). [FloatState] also avoids boxing on the animation hot path.
     */
    val offsetY: FloatState = offsetYState
    val offsetX: FloatState = offsetXState

    var phase by mutableStateOf(initialPhase)
        private set

    var axisLock by mutableStateOf(GestureAxisLock.Undecided)
        private set

    val currentOffsetY: Float
        get() = offsetYState.floatValue

    val currentOffsetX: Float
        get() = offsetXState.floatValue

    val isRunning: Boolean
        get() = activeAnimationJob?.isActive == true

    private var gestureGeneration: Long = 0L
    private var dispatchedDismissGeneration: Long = -1L
    private var activeAnimationJob: Job? = null

    internal fun updateAxisLock(lock: GestureAxisLock) {
        axisLock = lock
    }

    @Suppress("UNUSED_PARAMETER")
    fun beginOrResumeDrag(scope: CoroutineScope) {
        // Cancelling is enough to stop the spring on its next frame. The generation token makes
        // sure a frame already in flight can never overwrite the new drag position.
        activeAnimationJob?.cancel()
        activeAnimationJob = null
        gestureGeneration++
        axisLock = GestureAxisLock.Vertical
        phase = VerticalSwipePhase.Dragging
    }

    /**
     * Applies a drag delta synchronously. Do not launch a coroutine here: move events can arrive on
     * every display frame and allocating/dispatching a job per event adds input latency and GC
     * churn while making the card lag behind the finger.
     */
    @Suppress("UNUSED_PARAMETER")
    fun dragBy(
        scope: CoroutineScope,
        deltaY: Float,
        config: PhysicsVerticalSwipeConfig,
        deltaX: Float = 0f,
    ) {
        offsetYState.floatValue = computeNextOffset(currentOffsetY, deltaY, config)

        val nextX = if (config.maxHorizontalDriftPx > 0f) {
            (currentOffsetX + deltaX * config.horizontalDriftDamping)
                .coerceIn(-config.maxHorizontalDriftPx, config.maxHorizontalDriftPx)
        } else {
            0f
        }
        if (offsetXState.floatValue != nextX) {
            offsetXState.floatValue = nextX
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
        val dismiss = shouldDismissOnRelease(currentOffsetY, velocityY, config)
        activeAnimationJob?.cancel()
        val token = ++gestureGeneration
        activeAnimationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (dismiss) {
                val targetY = if (direction == VerticalSwipeDirection.UpToDismiss) {
                    -config.dismissTargetOffsetPx
                } else {
                    config.dismissTargetOffsetPx
                }
                val dismissSpec = spring<Float>(
                    dampingRatio = config.dismissDampingRatio,
                    stiffness = config.dismissStiffness,
                )
                if (config.maxHorizontalDriftPx > 0f) {
                    val targetX = (currentOffsetX + velocityX * 0.08f)
                        .coerceIn(-config.maxHorizontalDriftPx * 1.4f, config.maxHorizontalDriftPx * 1.4f)
                    launch {
                        animateAxisToTarget(
                            state = offsetXState,
                            targetValue = targetX,
                            initialVelocity = velocityX * 0.5f,
                            animationSpec = dismissSpec,
                            generation = token,
                        )
                    }
                }
                animateAxisToTarget(
                    state = offsetYState,
                    targetValue = targetY,
                    initialVelocity = velocityY,
                    animationSpec = dismissSpec,
                    generation = token,
                )
                if (gestureGeneration == token) {
                    phase = VerticalSwipePhase.Dismissed
                    if (dispatchedDismissGeneration != token) {
                        dispatchedDismissGeneration = token
                        onDismissed()
                    }
                }
            } else {
                val settleSpec = spring<Float>(
                    dampingRatio = config.settleDampingRatio,
                    stiffness = config.settleStiffness,
                )
                if (offsetXState.floatValue != 0f || velocityX != 0f) {
                    launch {
                        animateAxisToTarget(
                            state = offsetXState,
                            targetValue = 0f,
                            initialVelocity = velocityX * 0.5f,
                            animationSpec = settleSpec,
                            generation = token,
                        )
                    }
                }
                animateAxisToTarget(
                    state = offsetYState,
                    targetValue = 0f,
                    initialVelocity = velocityY,
                    animationSpec = settleSpec,
                    generation = token,
                )
                if (gestureGeneration == token) {
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
        activeAnimationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val settleSpec = spring<Float>(
                dampingRatio = config.settleDampingRatio,
                stiffness = config.settleStiffness,
            )
            if (offsetXState.floatValue != 0f) {
                launch {
                    animateAxisToTarget(
                        state = offsetXState,
                        targetValue = 0f,
                        initialVelocity = 0f,
                        animationSpec = settleSpec,
                        generation = token,
                    )
                }
            }
            animateAxisToTarget(
                state = offsetYState,
                targetValue = 0f,
                initialVelocity = initialVelocity,
                animationSpec = settleSpec,
                generation = token,
            )
            if (gestureGeneration == token) {
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
        if (phase == VerticalSwipePhase.Dismissed && !isRunning) {
            return
        }
        activeAnimationJob?.cancel()
        val token = ++gestureGeneration
        val target = if (direction == VerticalSwipeDirection.UpToDismiss) {
            -config.dismissTargetOffsetPx
        } else {
            config.dismissTargetOffsetPx
        }
        activeAnimationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val dismissSpec = spring<Float>(
                dampingRatio = config.dismissDampingRatio,
                stiffness = config.dismissStiffness,
            )
            animateAxisToTarget(
                state = offsetYState,
                targetValue = target,
                initialVelocity = initialVelocity,
                animationSpec = dismissSpec,
                generation = token,
            )
            if (gestureGeneration == token) {
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
        offsetYState.floatValue = 0f
        offsetXState.floatValue = 0f
        axisLock = GestureAxisLock.Undecided
        phase = restPhase
    }

    suspend fun snapToOffset(offset: Float, newPhase: VerticalSwipePhase) {
        activeAnimationJob?.cancel()
        activeAnimationJob = null
        gestureGeneration++
        dispatchedDismissGeneration = -1L
        offsetYState.floatValue = offset
        offsetXState.floatValue = 0f
        axisLock = GestureAxisLock.Undecided
        phase = newPhase
    }

    /**
     * Runs a spring on the Compose frame clock and writes straight into the snapshot-backed
     * offset. No recomposition or layout pass is scheduled per frame, only a graphics-layer
     * invalidation. The generation check makes cancellation instant even if a frame is in flight.
     */
    private suspend fun animateAxisToTarget(
        state: MutableFloatState,
        targetValue: Float,
        initialVelocity: Float,
        animationSpec: SpringSpec<Float>,
        generation: Long,
    ) {
        val animation = TargetBasedAnimation(
            animationSpec = animationSpec,
            typeConverter = Float.VectorConverter,
            initialValue = state.floatValue,
            targetValue = targetValue,
            initialVelocity = initialVelocity,
        )
        var startTimeNanos = Long.MIN_VALUE
        while (true) {
            val frameTimeNanos = withFrameNanos { it }
            if (generation != gestureGeneration) return
            if (startTimeNanos == Long.MIN_VALUE) {
                startTimeNanos = frameTimeNanos
            }
            val playTimeNanos = frameTimeNanos - startTimeNanos
            if (animation.isFinishedFromNanos(playTimeNanos)) {
                state.floatValue = targetValue
                return
            }
            state.floatValue = animation.getValueFromNanos(playTimeNanos)
        }
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

                    var pressedPointerCount = 0
                    for (index in event.changes.indices) {
                        if (event.changes[index].pressed) {
                            pressedPointerCount++
                        }
                    }
                    if (pressedPointerCount > 1) {
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

                    var changeIndex = -1
                    for (index in event.changes.indices) {
                        if (event.changes[index].id == down.id) {
                            changeIndex = index
                            break
                        }
                    }
                    if (changeIndex == -1 && event.changes.isNotEmpty()) {
                        changeIndex = 0
                    }
                    if (changeIndex == -1) {
                        break
                    }
                    val change = event.changes[changeIndex]

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
