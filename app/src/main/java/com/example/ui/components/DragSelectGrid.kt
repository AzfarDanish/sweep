package com.example.ui.components

import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AlbumSortOption
import com.example.ui.theme.SweepBadgeRed
import com.example.ui.theme.SweepBadgeWhite
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Enables continuous drag-to-select across a [LazyGridState] grid without interfering with
 * normal vertical scrolling or tap-to-toggle:
 * - When [isSelectionMode] is active, a horizontal/diagonal slide engages drag-selection
 *   immediately and continues selecting across rows for the remainder of the gesture.
 * - In any mode, holding past the long-press timeout engages drag-selection in any direction.
 * - Previously selected items are never deselected when dragged over again.
 */
fun Modifier.dragSelectGrid(
    gridState: LazyGridState,
    isSelectionMode: Boolean,
    resolveUriForKey: (Any) -> Uri?,
    onSelectUris: (List<Uri>) -> Unit,
): Modifier = composed {
    val latestSelectionMode by rememberUpdatedState(isSelectionMode)
    val latestResolveUri by rememberUpdatedState(resolveUriForKey)
    val latestOnSelectUris by rememberUpdatedState(onSelectUris)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val edgeZonePx = with(density) { 48.dp.toPx() }
    val maxAutoScrollPxPerFrame = with(density) { 14.dp.toPx() }

    pointerInput(gridState) {
        val touchSlop = viewConfiguration.touchSlop
        val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis

        fun hitKeyAt(pos: Offset): Any? {
            val visible = gridState.layoutInfo.visibleItemsInfo
            for (i in visible.indices) {
                val info = visible[i]
                val left = info.offset.x.toFloat()
                val top = info.offset.y.toFloat()
                if (pos.x >= left &&
                    pos.x <= left + info.size.width &&
                    pos.y >= top &&
                    pos.y <= top + info.size.height
                ) {
                    return info.key
                }
            }
            return null
        }

        fun collectSegment(
            from: Offset,
            to: Offset,
            visitedKeys: MutableSet<Any>,
        ) {
            val dx = to.x - from.x
            val dy = to.y - from.y
            val dist = hypot(dx, dy)
            val steps = (dist / 24f).toInt().coerceIn(1, 24)
            var batch: MutableList<Uri>? = null
            for (step in 0..steps) {
                val t = step / steps.toFloat()
                val sample = Offset(from.x + dx * t, from.y + dy * t)
                val key = hitKeyAt(sample) ?: continue
                if (visitedKeys.add(key)) {
                    val uri = latestResolveUri(key)
                    if (uri != null) {
                        if (batch == null) batch = ArrayList(4)
                        batch.add(uri)
                    }
                }
            }
            if (!batch.isNullOrEmpty()) {
                latestOnSelectUris(batch)
            }
        }

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val startPos = down.position
            var lastPos = startPos
            val downTimeMs = down.uptimeMillis
            var engaged = false
            var rejected = false
            val visitedKeys = HashSet<Any>()
            var autoScrollJob: Job? = null

            fun ensureAutoScroll() {
                if (autoScrollJob?.isActive == true) return
                autoScrollJob = scope.launch {
                    while (engaged) {
                        withFrameNanos { }
                        if (!engaged) break
                        val vh = gridState.layoutInfo.viewportSize.height.toFloat()
                        if (vh <= 0f) continue
                        val scrollDelta = when {
                            lastPos.y > vh - edgeZonePx -> {
                                val factor = ((lastPos.y - (vh - edgeZonePx)) / edgeZonePx)
                                    .coerceIn(0.15f, 1f)
                                maxAutoScrollPxPerFrame * factor
                            }
                            lastPos.y < edgeZonePx -> {
                                val factor = ((edgeZonePx - lastPos.y) / edgeZonePx)
                                    .coerceIn(0.15f, 1f)
                                -maxAutoScrollPxPerFrame * factor
                            }
                            else -> 0f
                        }
                        if (abs(scrollDelta) > 0.5f) {
                            gridState.scrollBy(scrollDelta)
                            collectSegment(lastPos, lastPos, visitedKeys)
                        }
                    }
                }
            }

            try {
                while (true) {
                    val event = if (!engaged && !rejected) {
                        val elapsed = (SystemClock.uptimeMillis() - downTimeMs).coerceAtLeast(0L)
                        val remaining = (longPressTimeoutMs - elapsed).coerceAtLeast(1L)
                        withTimeoutOrNull(remaining) {
                            awaitPointerEvent(pass = PointerEventPass.Initial)
                        }
                    } else {
                        awaitPointerEvent(pass = PointerEventPass.Initial)
                    }

                    if (event == null) {
                        // Long-press timeout elapsed without moving past slop -> engage drag select.
                        if (hitKeyAt(lastPos) != null) {
                            engaged = true
                            collectSegment(startPos, lastPos, visitedKeys)
                            ensureAutoScroll()
                        } else {
                            rejected = true
                            break
                        }
                        continue
                    }

                    var pressedCount = 0
                    var trackedChange = event.changes.firstOrNull()
                    for (i in event.changes.indices) {
                        val c = event.changes[i]
                        if (c.pressed) pressedCount++
                        if (c.id == down.id) trackedChange = c
                    }
                    val change = trackedChange ?: break

                    if (!change.pressed || pressedCount > 1) {
                        if (engaged) {
                            change.consume()
                        }
                        break
                    }

                    val prevPos = lastPos
                    lastPos = change.position

                    if (rejected) {
                        break
                    }

                    if (!engaged) {
                        val totalDx = abs(lastPos.x - startPos.x)
                        val totalDy = abs(lastPos.y - startPos.y)
                        if (latestSelectionMode) {
                            when {
                                totalDx >= touchSlop * 0.65f && totalDx >= totalDy * 0.75f -> {
                                    engaged = true
                                    change.consume()
                                    collectSegment(startPos, lastPos, visitedKeys)
                                    ensureAutoScroll()
                                }
                                totalDy > touchSlop && totalDy > totalDx * 1.15f -> {
                                    rejected = true
                                    break
                                }
                            }
                        } else {
                            if (totalDx > touchSlop || totalDy > touchSlop) {
                                rejected = true
                                break
                            }
                        }
                    } else {
                        change.consume()
                        collectSegment(prevPos, lastPos, visitedKeys)
                    }
                }
            } finally {
                engaged = false
                autoScrollJob?.cancel()
            }
        }
    }
}

/**
 * Shared header Trash Bin button with a dynamic, unclipped pill/circle badge that supports
 * multi-digit counts and "99+" without overflowing the header or screen boundary.
 */
@Composable
fun HeaderTrashButton(
    trashCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonTestTag: String = "open_trash_button",
    iconTestTag: String? = null,
    badgeTestTag: String = "trash_badge",
    iconTint: Color = LocalContentColor.current,
    iconScale: Float = 1f,
    badgeScale: Float = 1f,
    contentDescription: String = if (trashCount > 0) {
        stringResource(R.string.trash_bin_count_cd, trashCount)
    } else {
        stringResource(R.string.trash_bin_empty_cd)
    },
    onIconPositioned: ((LayoutCoordinates) -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .widthIn(min = 56.dp)
            .height(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 24.dp),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(end = 6.dp)
            .testTag(buttonTestTag),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(width = 50.dp, height = 44.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier
                    .align(Alignment.Center)
                    .then(
                        if (onIconPositioned != null) {
                            Modifier.onGloballyPositioned(onIconPositioned)
                        } else {
                            Modifier
                        },
                    )
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
                    .then(if (iconTestTag != null) Modifier.testTag(iconTestTag) else Modifier),
            )

            if (trashCount > 0) {
                TrashCountBadge(
                    count = trashCount,
                    scale = badgeScale,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                        .testTag(badgeTestTag),
                )
            }
        }
    }
}

@Composable
fun TrashCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val label = if (count > 99) "99+" else count.toString()
    val isSingleDigit = count in 1..9
    val shape = if (isSingleDigit) CircleShape else RoundedCornerShape(percent = 50)
    val minWidth = when {
        count >= 100 -> 30.dp
        count >= 10 -> 24.dp
        else -> 18.dp
    }
    val horizontalPadding = when {
        isSingleDigit -> 0.dp
        count >= 100 -> 6.dp
        else -> 5.dp
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.85f, 0.2f)
                scaleX = scale
                scaleY = scale
            }
            .defaultMinSize(minWidth = minWidth, minHeight = 18.dp)
            .height(18.dp)
            .clip(shape)
            .background(SweepBadgeRed)
            .padding(horizontal = horizontalPadding),
    ) {
        Text(
            text = label,
            color = SweepBadgeWhite,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}

@Composable
fun SortMenuButton(
    selectedSort: AlbumSortOption,
    onSortChange: (AlbumSortOption) -> Unit,
    buttonTestTag: String,
    menuTestTag: String,
    optionTagPrefix: String,
    modifier: Modifier = Modifier,
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { showSortMenu = true },
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag(buttonTestTag),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Sort,
                contentDescription = stringResource(R.string.sort_by),
            )
        }
        DropdownMenu(
            expanded = showSortMenu,
            onDismissRequest = { showSortMenu = false },
            modifier = Modifier.testTag(menuTestTag),
        ) {
            AlbumSortOption.entries.forEach { option ->
                val isSelected = option == selectedSort
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(option.sortLabelRes()),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        showSortMenu = false
                        onSortChange(option)
                    },
                    modifier = Modifier
                        .semantics { selected = isSelected }
                        .testTag("${optionTagPrefix}_${option.name.lowercase()}"),
                )
            }
        }
    }
}

private fun AlbumSortOption.sortLabelRes(): Int = when (this) {
    AlbumSortOption.NEWEST_FIRST -> R.string.sort_newest_first
    AlbumSortOption.OLDEST_FIRST -> R.string.sort_oldest_first
    AlbumSortOption.LARGEST_FIRST -> R.string.sort_largest_first
    AlbumSortOption.SMALLEST_FIRST -> R.string.sort_smallest_first
}
