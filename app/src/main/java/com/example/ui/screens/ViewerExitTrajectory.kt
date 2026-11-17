package com.example.ui.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.lerp
import kotlin.math.cos
import kotlin.math.sin

internal data class TrashBinTrajectoryTransform(
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val rotationZ: Float,
    val alpha: Float,
    val topCenterX: Float,
    val topCenterY: Float,
)

/**
 * Computes the resting media preview card size inside a viewport of [viewportWidthPx] x
 * [viewportHeightPx], matching [AdaptiveMediaFrame] aspect-ratio sizing.
 */
internal fun resolveViewerCardSizePx(
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    aspectRatio: Float?,
): Pair<Float, Float> {
    if (viewportWidthPx <= 0f || viewportHeightPx <= 0f) return 0f to 0f
    val safeRatio = aspectRatio?.takeIf { it.isFinite() && it > 0f }
        ?: return viewportWidthPx to viewportHeightPx
    val containerRatio = viewportWidthPx / viewportHeightPx
    return if (containerRatio > safeRatio) {
        (viewportHeightPx * safeRatio) to viewportHeightPx
    } else {
        viewportWidthPx to (viewportWidthPx / safeRatio)
    }
}

/**
 * Computes the continuous per-frame transform guiding the media preview's top-center point from
 * its release position [startOffsetY] smoothly into [trashBinCenterInViewport] as [progress] moves
 * from `0f` to `1f` (and played backward `1f -> 0f` for Undo).
 */
internal fun computeTrashBinTrajectoryTransform(
    progress: Float,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    cardHeightPx: Float,
    trashBinCenterInViewport: Offset,
    startOffsetY: Float,
    dismissDistanceThresholdPx: Float,
    startVelocityY: Float = 0f,
    endScale: Float = 0.24f,
): TrashBinTrajectoryTransform {
    val u = progress.coerceIn(0f, 1f)
    val safeThreshold = dismissDistanceThresholdPx.coerceAtLeast(1f)
    val startLiftProgress = (-startOffsetY / safeThreshold).coerceIn(0f, 1f)
    val startScale = 1f - (startLiftProgress * 0.08f)
    val startRotationDeg = (startLiftProgress * 1.8f).coerceIn(0f, 4f)

    val startTheta = Math.toRadians(startRotationDeg.toDouble())
    val startHalfH = (cardHeightPx * startScale) * 0.5f
    val p0 = Offset(
        x = viewportWidthPx * 0.5f + startHalfH * sin(startTheta).toFloat(),
        y = viewportHeightPx * 0.5f + startOffsetY - startHalfH * cos(startTheta).toFloat(),
    )
    val p3 = trashBinCenterInViewport

    val rawDy = p0.y - p3.y
    val p1: Offset
    val p2: Offset
    if (rawDy >= 0f) {
        val minVerticalLead = minOf(160f, viewportHeightPx * 0.25f).coerceAtLeast(48f)
        val verticalLead = maxOf(rawDy * 0.65f, minVerticalLead)
        p1 = Offset(x = p0.x, y = p0.y - verticalLead)
        p2 = Offset(
            x = lerp(p0.x, p3.x, 0.78f),
            y = p3.y + rawDy * 0.08f,
        )
    } else {
        val upCarry = ((-startVelocityY / 4200f) * 72f).coerceIn(18f, 72f)
        p1 = Offset(x = p0.x, y = p0.y - upCarry)
        p2 = Offset(
            x = lerp(p0.x, p3.x, 0.78f),
            y = minOf(p0.y, p3.y) - upCarry * 0.25f,
        )
    }

    val inv = 1f - u
    val b0 = inv * inv * inv
    val b1 = 3f * inv * inv * u
    val b2 = 3f * inv * u * u
    val b3 = u * u * u

    val topCenterX = b0 * p0.x + b1 * p1.x + b2 * p2.x + b3 * p3.x
    val topCenterY = b0 * p0.y + b1 * p1.y + b2 * p2.y + b3 * p3.y

    val scaleEase = u * u * (3f - 2f * u)
    val scale = lerp(startScale, endScale, scaleEase)
    val rotationDeg = (
        startRotationDeg * (1f - u) + 1.6f * sin(u * Math.PI).toFloat()
    ).coerceIn(0f, 3.2f)

    val alpha = if (u <= 0.78f) {
        1f
    } else {
        (1f - ((u - 0.78f) / 0.22f)).coerceIn(0f, 1f)
    }

    val theta = Math.toRadians(rotationDeg.toDouble())
    val scaledHalfH = (cardHeightPx * scale) * 0.5f
    val translationX = topCenterX - viewportWidthPx * 0.5f - scaledHalfH * sin(theta).toFloat()
    val translationY = topCenterY - viewportHeightPx * 0.5f + scaledHalfH * cos(theta).toFloat()

    return TrashBinTrajectoryTransform(
        translationX = translationX,
        translationY = translationY,
        scale = scale,
        rotationZ = rotationDeg,
        alpha = alpha,
        topCenterX = topCenterX,
        topCenterY = topCenterY,
    )
}
