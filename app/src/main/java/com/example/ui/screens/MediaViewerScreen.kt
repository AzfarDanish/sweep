package com.example.ui.screens

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.request.Disposable as CoilDisposable
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Precision
import com.example.R
import com.example.data.UndoRedoUiState
import com.example.model.AlbumCollection
import com.example.model.AlbumKind
import com.example.model.MediaItem
import com.example.ui.components.HeaderTrashButton
import com.example.ui.components.MediaInfoSheet
import com.example.ui.components.PermanentDeleteDialog
import com.example.ui.components.PhysicsVerticalSwipeConfig
import com.example.ui.components.PhysicsVerticalSwipeController
import com.example.ui.components.VerticalSwipeDirection
import com.example.ui.components.VerticalSwipePhase
import com.example.ui.components.ViewerImageRequestParameter
import com.example.ui.components.physicsVerticalSwipe
import com.example.ui.components.rememberPhysicsVerticalSwipeConfig
import com.example.ui.components.rememberSweepImageLoader
import com.example.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val MaxExitingCards = 3
private const val ViewerPrefetchMaxHandles = 8
private const val ViewerTransitionStiffness = 260f
private const val ViewerTransitionVisibilityThreshold = 0.003f


private class IncrementingCounter {
    var value: Long = 0L
}

private class IncomingExpansionAnimator {
    var job: Job? = null
    var generation: Long = 0L
}

/**
 * Keeps a small warm window of upcoming media decoded without cancelling an in-flight preload at
 * the exact moment it becomes the active page. A bounded LRU releases anything no longer needed.
 */
private class ViewerPrefetchCache {
    private val handles = LinkedHashMap<Long, CoilDisposable>()

    fun sync(
        context: Context,
        imageLoader: ImageLoader,
        items: List<MediaItem>,
        page: Int,
    ) {
        if (items.isEmpty()) return

        val window = ArrayList<MediaItem>(5)
        for (offset in intArrayOf(0, 1, 2, 3, -1)) {
            val item = items.getOrNull(page + offset) ?: continue
            if (window.none { it.id == item.id }) {
                window.add(item)
            }
        }

        val desiredIds = HashSet<Long>(window.size)
        for (item in window) {
            desiredIds.add(item.id)
            val existing = handles.remove(item.id)
            if (existing != null) {
                // Reinsert to mark it as recently used.
                handles[item.id] = existing
            } else {
                handles[item.id] = imageLoader.enqueue(buildViewerImageRequest(context, item))
            }
        }

        // Keep a couple of recently used stale handles so a preload that is still decoding is not
        // cancelled during a page change. Only the oldest stale handles beyond the cap are freed.
        val iterator = handles.entries.iterator()
        while (handles.size > ViewerPrefetchMaxHandles && iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in desiredIds) {
                entry.value.dispose()
                iterator.remove()
            }
        }
        while (handles.size > ViewerPrefetchMaxHandles) {
            val oldest = handles.entries.firstOrNull() ?: break
            oldest.value.dispose()
            handles.remove(oldest.key)
        }
    }

    fun dispose() {
        handles.values.forEach { it.dispose() }
        handles.clear()
    }
}

@Composable
fun MediaViewerScreen(
    items: List<MediaItem>,
    initialMediaId: Long,
    isTrashPreview: Boolean,
    trashCount: Int,
    undoRedoState: UndoRedoUiState,
    focusMediaIdFlow: SharedFlow<Long>? = null,
    onBack: () -> Unit,
    onOpenTrash: () -> Unit,
    onTrashItem: (MediaItem) -> Unit,
    onRestoreItem: (MediaItem) -> Unit,
    onPermanentDeleteItem: (MediaItem) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    albums: List<AlbumCollection> = emptyList(),
    onMoveToAlbum: (MediaItem, AlbumCollection) -> Unit = { _, _ -> },
) {
    val startIndex = remember(initialMediaId) {
        items.indexOfFirst { it.id == initialMediaId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
        pageCount = { items.size },
    )

    val context = LocalContext.current
    val imageLoader = rememberSweepImageLoader()
    // Decoded intrinsic ratios are authoritative because MediaStore width/height can be transposed
    // for EXIF-rotated photos. The map also keeps an outgoing card correctly sized after its item
    // has already left the list.
    val resolvedRatios = remember { mutableStateMapOf<Long, Float>() }

    // The fixed headers never hide. Only the video transport fades in/out, inside the viewport.
    var showVideoTransport by remember { mutableStateOf(true) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var isCurrentZoomed by remember { mutableStateOf(false) }
    var pendingDeleteItem by remember { mutableStateOf<MediaItem?>(null) }
    var albumPickerItem by remember { mutableStateOf<MediaItem?>(null) }
    var activeExoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    val effectivePage = if (items.isEmpty()) {
        0
    } else {
        pagerState.currentPage.coerceIn(0, items.lastIndex)
    }
    val currentItem = items.getOrNull(effectivePage)
    val nextItem = if (!isTrashPreview && items.isNotEmpty()) {
        items.getOrNull(effectivePage + 1)
    } else {
        null
    }

    val committedTrashIds = remember { HashSet<Long>() }
    val trashedSessionIds = remember { HashSet<Long>() }
    val knownItemIds = remember { HashSet<Long>() }
    val exitingCards = remember { mutableStateListOf<ExitingCardState>() }
    val exitIdCounter = remember { IncrementingCounter() }
    val undoIdCounter = remember { IncrementingCounter() }

    var awaitingUndoRestore by remember { mutableStateOf(false) }
    var activeUndoReturn by remember { mutableStateOf<UndoReturningCardState?>(null) }

    val incomingRevealProgress = remember { mutableFloatStateOf(1f) }
    val incomingExpansion = remember { IncomingExpansionAnimator() }

    LaunchedEffect(awaitingUndoRestore) {
        if (awaitingUndoRestore) {
            delay(1_200L)
            awaitingUndoRestore = false
        }
    }

    LaunchedEffect(effectivePage) {
        isCurrentZoomed = false
    }

    LaunchedEffect(currentItem?.id, currentItem?.isVideo) {
        showVideoTransport = true
        if (currentItem?.isVideo != true) {
            activeExoPlayer = null
        }
    }

    // Memory-conscious lookahead preloading for the current, next, next + 1, and previous items.
    // The bounded LRU keeps an in-flight preload alive long enough to become the active page.
    val prefetchCache = remember { ViewerPrefetchCache() }
    LaunchedEffect(effectivePage, items, imageLoader) {
        prefetchCache.sync(
            context = context,
            imageLoader = imageLoader,
            items = items,
            page = effectivePage,
        )
    }
    DisposableEffect(Unit) {
        onDispose { prefetchCache.dispose() }
    }

    if (pendingDeleteItem != null) {
        PermanentDeleteDialog(
            itemCount = 1,
            onDismiss = { pendingDeleteItem = null },
            onConfirm = {
                val target = pendingDeleteItem
                pendingDeleteItem = null
                if (target != null) onPermanentDeleteItem(target)
            },
        )
    }

    if (showInfoSheet && currentItem != null) {
        MediaInfoSheet(
            item = currentItem,
            onDismiss = { showInfoSheet = false },
        )
    }

    albumPickerItem?.let { pickerItem ->
        MoveToAlbumDialog(
            item = pickerItem,
            albums = albums,
            onDismiss = { albumPickerItem = null },
            onSelect = { album ->
                albumPickerItem = null
                onMoveToAlbum(pickerItem, album)
            },
        )
    }

    val density = LocalDensity.current
    var viewportWidthPx by remember { mutableFloatStateOf(1080f) }
    var viewportHeightPx by remember { mutableFloatStateOf(1600f) }
    var overlayCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var viewportCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var trashIconCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var viewportOffsetInOverlay by remember { mutableStateOf(IntOffset.Zero) }
    var viewportSizeInOverlay by remember { mutableStateOf(IntSize.Zero) }
    var trashCenterInViewport by remember { mutableStateOf<Offset?>(null) }

    fun syncViewportGeometry() {
        val overlay = overlayCoords?.takeIf { it.isAttached } ?: return
        val viewport = viewportCoords?.takeIf { it.isAttached } ?: return
        val topLeft = overlay.localPositionOf(viewport, Offset.Zero)
        viewportOffsetInOverlay = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt())
        viewportSizeInOverlay = viewport.size

        val icon = trashIconCoords?.takeIf { it.isAttached }
        if (icon != null) {
            val iconCenter = Offset(icon.size.width * 0.5f, icon.size.height * 0.5f)
            val iconCenterInOverlay = overlay.localPositionOf(icon, iconCenter)
            trashCenterInViewport = Offset(
                x = iconCenterInOverlay.x - topLeft.x,
                y = iconCenterInOverlay.y - topLeft.y,
            )
        }
    }

    // Content-sized cards are centered, so any resting downward offset would let the staged next
    // card peek out below short/landscape media. Keep the stack anchored at center; the reveal is
    // driven purely by scale and the outgoing card's upward travel.
    val underlyingRestingOffsetPx = 0f
    val trashConfig = rememberPhysicsVerticalSwipeConfig(
        direction = VerticalSwipeDirection.UpToDismiss,
        containerHeightPx = viewportHeightPx,
        maxDismissThresholdDp = 120.dp,
        minFlickDistanceDp = 48.dp,
        flickVelocityThresholdDpPerSec = 800.dp,
        overscrollLimitDp = 12.dp,
        exitTravelMultiplier = 1.18f,
        gestureActivationThresholdDp = 4.dp,
        verticalDirectionRatio = 1.3f,
        horizontalDirectionRatio = 1.3f,
    )
    val fallbackTrashTargetInViewport = Offset(
        x = viewportWidthPx - with(density) { 28.dp.toPx() },
        y = -with(density) { 28.dp.toPx() },
    )
    val resolvedTrashTargetInViewport = trashCenterInViewport ?: fallbackTrashTargetInViewport

    val resolveCardHeightPx: (MediaItem) -> Float = { item ->
        val ratio = resolvedRatios[item.id] ?: item.viewerAspectRatio()
        resolveViewerCardSizePx(
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            aspectRatio = ratio,
        ).second.coerceAtLeast(1f)
    }

    // Keep one stable controller & pointer-input session across rapid card transitions.
    val trashSwipeController = remember {
        PhysicsVerticalSwipeController(
            direction = VerticalSwipeDirection.UpToDismiss,
            initialOffsetY = 0f,
            initialPhase = VerticalSwipePhase.Idle,
        )
    }
    val scope = rememberCoroutineScope()

    val latestItems by rememberUpdatedState(items)
    val latestCurrentItem by rememberUpdatedState(currentItem)
    val latestNextItem by rememberUpdatedState(nextItem)
    val latestOnTrashItem by rememberUpdatedState(onTrashItem)

    val startUndoReturnAnimation: (MediaItem, Int) -> Unit = { targetItem, targetIndex ->
        awaitingUndoRestore = false
        committedTrashIds.remove(targetItem.id)
        trashedSessionIds.remove(targetItem.id)

        incomingExpansion.job?.cancel()
        incomingExpansion.generation++
        incomingRevealProgress.floatValue = 1f
        trashSwipeController.snapToRestSync(VerticalSwipePhase.Idle)

        val inFlightExit = exitingCards.firstOrNull { it.item.id == targetItem.id }
        val startProgress = if (inFlightExit != null) {
            val captured = inFlightExit.progressState.floatValue
            exitingCards.remove(inFlightExit)
            captured.coerceIn(0.02f, 1f)
        } else {
            1f
        }
        val vw = inFlightExit?.viewportWidthPx ?: viewportWidthPx
        val vh = inFlightExit?.viewportHeightPx ?: viewportHeightPx
        val cardHeight = inFlightExit?.cardHeightPx ?: resolveCardHeightPx(targetItem)
        val trashTarget = inFlightExit?.trashBinCenterInViewport ?: resolvedTrashTargetInViewport

        activeUndoReturn = UndoReturningCardState(
            instanceId = ++undoIdCounter.value,
            item = targetItem,
            targetPage = targetIndex,
            progressState = mutableFloatStateOf(startProgress),
            startProgress = startProgress,
            revealProgressState = mutableFloatStateOf((startProgress * 1.45f).coerceIn(0f, 1f)),
            viewportWidthPx = vw,
            viewportHeightPx = vh,
            cardHeightPx = cardHeight,
            trashBinCenterInViewport = trashTarget,
            dismissDistanceThresholdPx = trashConfig.dismissDistanceThresholdPx,
        )
    }

    LaunchedEffect(items) {
        val currentIds = items.mapTo(HashSet(items.size)) { it.id }
        var reappearedItem: MediaItem? = null
        var reappearedIndex = -1
        for (index in items.indices) {
            val item = items[index]
            if (item.id !in knownItemIds) {
                committedTrashIds.remove(item.id)
                if (awaitingUndoRestore &&
                    item.id in trashedSessionIds &&
                    activeUndoReturn?.item?.id != item.id &&
                    reappearedItem == null
                ) {
                    reappearedItem = item
                    reappearedIndex = index
                }
            }
        }
        knownItemIds.clear()
        knownItemIds.addAll(currentIds)
        if (items.isNotEmpty() && pagerState.currentPage >= items.size) {
            pagerState.scrollToPage(items.lastIndex)
        }
        if (reappearedItem != null && reappearedIndex >= 0) {
            if (pagerState.currentPage != reappearedIndex) {
                pagerState.scrollToPage(reappearedIndex)
            }
            startUndoReturnAnimation(reappearedItem, reappearedIndex)
        }
    }

    LaunchedEffect(focusMediaIdFlow) {
        focusMediaIdFlow?.collect { targetId ->
            val targetList = withTimeoutOrNull(600L) {
                snapshotFlow { latestItems }.firstOrNull { list ->
                    list.any { it.id == targetId }
                }
            } ?: latestItems
            val idx = targetList.indexOfFirst { it.id == targetId }
            if (idx >= 0) {
                val targetItem = targetList[idx]
                if (activeUndoReturn?.item?.id != targetId) {
                    if (pagerState.currentPage != idx) {
                        pagerState.scrollToPage(idx)
                    }
                    startUndoReturnAnimation(targetItem, idx)
                }
            }
        }
    }

    LaunchedEffect(pagerState.currentPage, activeUndoReturn?.instanceId) {
        val returning = activeUndoReturn ?: return@LaunchedEffect
        if (pagerState.currentPage != returning.targetPage) {
            activeUndoReturn = null
        }
    }

    LaunchedEffect(currentItem?.id) {
        if (trashSwipeController.phase != VerticalSwipePhase.Dragging &&
            (trashSwipeController.currentOffsetY != 0f || trashSwipeController.phase != VerticalSwipePhase.Idle)
        ) {
            trashSwipeController.snapToRest(VerticalSwipePhase.Idle)
        }
    }

    val commitTrashTransition: (Float, Float) -> Unit = { releasedOffsetY, releasedVelocityY ->
        val targetItem = latestCurrentItem
        val isStillCurrent = targetItem != null &&
            latestItems.any { it.id == targetItem.id }
        if (targetItem != null && isStillCurrent && !isTrashPreview &&
            committedTrashIds.add(targetItem.id)
        ) {
            trashedSessionIds.add(targetItem.id)
            if (activeUndoReturn?.item?.id == targetItem.id) {
                activeUndoReturn = null
            }

            val hadUnderlying = latestNextItem != null
            val revealAtHandoff = if (hadUnderlying) {
                computeStackedUnderlyingCardTransform(
                    offsetY = releasedOffsetY,
                    dismissDistanceThresholdPx = trashConfig.dismissDistanceThresholdPx,
                    restingOffsetPx = underlyingRestingOffsetPx,
                ).revealProgress
            } else {
                1f
            }

            val exitCard = ExitingCardState(
                instanceId = ++exitIdCounter.value,
                item = targetItem,
                progressState = mutableFloatStateOf(0f),
                startOffsetY = releasedOffsetY,
                startVelocityY = releasedVelocityY,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                cardHeightPx = resolveCardHeightPx(targetItem),
                trashBinCenterInViewport = resolvedTrashTargetInViewport,
                dismissDistanceThresholdPx = trashConfig.dismissDistanceThresholdPx,
            )
            exitingCards.add(exitCard)
            Snapshot.sendApplyNotifications()
            while (exitingCards.size > MaxExitingCards) {
                exitingCards.removeAt(0)
            }

            incomingExpansion.job?.cancel()
            val gen = ++incomingExpansion.generation
            if (revealAtHandoff < 0.999f) {
                incomingRevealProgress.floatValue = revealAtHandoff
                incomingExpansion.job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    val anim = TargetBasedAnimation(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = ViewerTransitionStiffness,
                            visibilityThreshold = ViewerTransitionVisibilityThreshold,
                        ),
                        typeConverter = Float.VectorConverter,
                        initialValue = revealAtHandoff,
                        targetValue = 1f,
                    )
                    var startNanos = Long.MIN_VALUE
                    while (true) {
                        val frameNanos = withFrameNanos { it }
                        if (gen != incomingExpansion.generation) return@launch
                        if (startNanos == Long.MIN_VALUE) {
                            startNanos = frameNanos
                        }
                        val playNanos = frameNanos - startNanos
                        if (anim.isFinishedFromNanos(playNanos)) {
                            incomingRevealProgress.floatValue = 1f
                            return@launch
                        }
                        incomingRevealProgress.floatValue = anim.getValueFromNanos(playNanos)
                    }
                }
            } else {
                incomingRevealProgress.floatValue = 1f
            }

            // Commit to the existing Trash Bin workflow exactly once, immediately. The media list
            // and thumbnails update from the repository's optimistic state; the viewer never waits
            // for the exit spring or for image decoding.
            latestOnTrashItem(targetItem)
            Snapshot.sendApplyNotifications()
        }
    }

    val triggerExitTrash: (MediaItem) -> Unit = { targetItem ->
        if (targetItem.id == latestCurrentItem?.id) {
            trashSwipeController.snapToRestSync(VerticalSwipePhase.Idle)
            commitTrashTransition(0f, -trashConfig.flickVelocityThresholdPx * 1.4f)
        }
    }

    // Synchronous composition-time guard so a restored item never flashes at offsetY = 0 on the
    // single frame between list insertion and LaunchedEffect coroutine resumption.
    val pendingRestoredItemId = if (awaitingUndoRestore) {
        items.firstOrNull { it.id !in knownItemIds && it.id in trashedSessionIds }?.id
    } else {
        null
    }
    val hiddenForUndoItemId = activeUndoReturn?.item?.id ?: pendingRestoredItemId
    val fullRevealState = remember { mutableFloatStateOf(1f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("media_viewer_screen"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            ViewerTopHeader(
                currentItem = currentItem,
                currentIndex = effectivePage,
                totalCount = items.size,
                trashCount = trashCount,
                onBack = onBack,
                onOpenTrash = onOpenTrash,
                onTrashIconPositioned = { coords ->
                    trashIconCoords = coords
                    syncViewportGeometry()
                },
                modifier = Modifier.zIndex(0f),
            )

            // The media viewport owns flexible layout space between both fixed headers, without
            // clipToBounds(), so upward drags travel cleanly over the top header while the header
            // stays stationary. Zoomable/video pages and HorizontalPager still clip internally.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .zIndex(1f)
                    .onSizeChanged { size ->
                        if (size.width > 0) viewportWidthPx = size.width.toFloat()
                        if (size.height > 0) viewportHeightPx = size.height.toFloat()
                    }
                    .onGloballyPositioned { coords ->
                        viewportCoords = coords
                        syncViewportGeometry()
                    }
                    .testTag("viewer_media_viewport"),
            ) {
                if (items.isEmpty() || currentItem == null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    ) {
                        Text(
                            text = "No media remaining",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = onBack,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            ) {
                                Text("Done")
                            }
                        }
                    }
                } else {
                    if (nextItem != null) {
                        val undoActiveForCurrent =
                            hiddenForUndoItemId != null && currentItem.id == hiddenForUndoItemId
                        val underlyingRevealOverride = when {
                            activeUndoReturn != null && activeUndoReturn?.item?.id == currentItem.id ->
                                activeUndoReturn!!.revealProgressState
                            undoActiveForCurrent -> fullRevealState
                            else -> null
                        }
                        StackedUnderlyingCard(
                            item = nextItem,
                            imageLoader = imageLoader,
                            resolvedRatios = resolvedRatios,
                            offsetY = trashSwipeController.offsetY,
                            dismissDistanceThresholdPx = trashConfig.dismissDistanceThresholdPx,
                            revealProgressOverride = underlyingRevealOverride,
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(0f),
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(1f)
                            .physicsVerticalSwipe(
                                controller = trashSwipeController,
                                config = trashConfig,
                                scope = scope,
                                enabled = !isTrashPreview && !isCurrentZoomed && activeUndoReturn == null,
                                key = isTrashPreview,
                                restPhase = VerticalSwipePhase.Idle,
                                canEngage = {
                                    !pagerState.isScrollInProgress &&
                                        !isCurrentZoomed &&
                                        !isTrashPreview &&
                                        activeUndoReturn == null &&
                                        latestCurrentItem != null
                                },
                                pointerPass = PointerEventPass.Initial,
                                onDismissed = {},
                                onDismissCommit = commitTrashTransition,
                            )
                            .graphicsLayer {
                                // Compute transforms inline on the draw/animation hot path so no
                                // temporary objects are allocated every frame during a gesture.
                                val currentOffsetY = trashSwipeController.offsetY.floatValue
                                val dismissThreshold =
                                    trashConfig.dismissDistanceThresholdPx.coerceAtLeast(1f)
                                val dismissTarget =
                                    trashConfig.dismissTargetOffsetPx.coerceAtLeast(1f)
                                val liftProgress =
                                    (-currentOffsetY / dismissThreshold).coerceIn(0f, 1f)
                                val exitProgress =
                                    (-currentOffsetY / dismissTarget).coerceIn(0f, 1f)
                                val dragScale =
                                    1f - (liftProgress * 0.08f) - (exitProgress * 0.06f)
                                val expandProgress =
                                    incomingRevealProgress.floatValue.coerceIn(0f, 1f)
                                val expandScale = 0.92f + (0.08f * expandProgress)
                                val expandOffsetY =
                                    underlyingRestingOffsetPx * (1f - expandProgress)

                                translationY = currentOffsetY + expandOffsetY
                                scaleX = dragScale * expandScale
                                scaleY = dragScale * expandScale
                                rotationZ =
                                    (liftProgress * 1.8f + exitProgress * 0.7f)
                                        .coerceIn(0f, 4f)
                                cameraDistance = 16f * this.density
                            }
                            .testTag("viewer_media_gesture_surface"),
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            key = { page -> items[page].id },
                            beyondViewportPageCount = 1,
                            // Vertical drags are consumed on the Initial pass before the pager sees
                            // them, so it is not necessary to recompose the pager when a trash drag
                            // starts. Keeping this flag stable avoids a gesture-start frame hitch.
                            userScrollEnabled = !isCurrentZoomed,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            val item = items[page]
                            val isCurrentPage = page == effectivePage
                            val isHiddenForUndo = item.id == hiddenForUndoItemId
                            val cardTestTag = if (isCurrentPage) "viewer_media_card" else null
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = if (isHiddenForUndo) 0f else 1f
                                    },
                            ) {
                                if (item.isVideo) {
                                    VideoPlayerPage(
                                        item = item,
                                        imageLoader = imageLoader,
                                        resolvedRatios = resolvedRatios,
                                        cardTestTag = cardTestTag,
                                        isActivePage = isCurrentPage &&
                                            pagerState.settledPage == page &&
                                            !isHiddenForUndo,
                                        isCurrentPage = isCurrentPage,
                                        onPlayerActive = { player -> activeExoPlayer = player },
                                        onPlayerInactive = { player ->
                                            if (activeExoPlayer === player) {
                                                activeExoPlayer = null
                                            }
                                        },
                                        onToggleTransport = {
                                            showVideoTransport = !showVideoTransport
                                        },
                                        onLongPress = { showInfoSheet = true },
                                    )
                                } else {
                                    ZoomablePhotoPage(
                                        item = item,
                                        imageLoader = imageLoader,
                                        resolvedRatios = resolvedRatios,
                                        cardTestTag = cardTestTag,
                                        onZoomChanged = { zoomed ->
                                            if (page == effectivePage) {
                                                isCurrentZoomed = zoomed
                                            }
                                        },
                                        onTap = {},
                                        onLongPress = { showInfoSheet = true },
                                    )
                                }
                            }
                        }

                        // Matching dim fade-out during smooth incoming card expansion
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val expandProgress = incomingRevealProgress.floatValue.coerceIn(0f, 1f)
                                    alpha = 0.45f * (1f - expandProgress)
                                }
                                .background(Color.Black),
                        )
                    }
                }

                // Video transport is contained by the viewport and never reserves space from it.
                if (currentItem?.isVideo == true &&
                    showVideoTransport &&
                    hiddenForUndoItemId != currentItem.id &&
                    trashSwipeController.phase != VerticalSwipePhase.Dragging &&
                    trashSwipeController.phase != VerticalSwipePhase.Dismissed
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .zIndex(2f)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("viewer_video_transport"),
                    ) {
                        VideoControlsBar(
                            exoPlayer = activeExoPlayer,
                            fallbackDurationMs = currentItem.durationMillis,
                        )
                    }
                }
            }

            ViewerBottomHeader(
                currentItem = currentItem,
                isTrashPreview = isTrashPreview,
                undoRedoState = undoRedoState,
                canMoveToAlbum = albums.any { it.kind == AlbumKind.FOLDER },
                onNext = {
                    val targetPage = (effectivePage + 1).coerceAtMost(items.lastIndex)
                    if (targetPage > effectivePage) {
                        scope.launch { pagerState.animateScrollToPage(targetPage) }
                    }
                },
                onUndo = {
                    awaitingUndoRestore = true
                    onUndo()
                },
                onMoveToAlbum = { currentItem?.let { albumPickerItem = it } },
                onTrash = { currentItem?.let(triggerExitTrash) },
                onRestore = { currentItem?.let(onRestoreItem) },
                onPermanentDelete = { currentItem?.let { pendingDeleteItem = it } },
                modifier = Modifier.zIndex(2f),
            )
        }

        // Top-level unclipped animation overlay rendered above the fixed top header and status bar
        // inset so outgoing cards fly cleanly over the header and Undo return cards reverse down
        // from beyond the top edge of the screen.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
                .onGloballyPositioned { coords ->
                    overlayCoords = coords
                    syncViewportGeometry()
                },
        ) {
            if (viewportSizeInOverlay.width > 0 && viewportSizeInOverlay.height > 0) {
                Box(
                    modifier = Modifier
                        .offset { viewportOffsetInOverlay }
                        .size(
                            width = with(density) { viewportSizeInOverlay.width.toDp() },
                            height = with(density) { viewportSizeInOverlay.height.toDp() },
                        ),
                ) {
                    ExitingCardsLayer(
                        exitingCards = exitingCards,
                        imageLoader = imageLoader,
                        resolvedRatios = resolvedRatios,
                        dismissStiffness = trashConfig.dismissStiffness,
                        dismissDampingRatio = trashConfig.dismissDampingRatio,
                    )

                    activeUndoReturn?.let { returning ->
                        key(returning.instanceId) {
                            UndoReturningCardOverlay(
                                returningCard = returning,
                                imageLoader = imageLoader,
                                resolvedRatios = resolvedRatios,
                                settleStiffness = trashConfig.dismissStiffness,
                                settleDampingRatio = trashConfig.dismissDampingRatio,
                                onFinished = {
                                    if (activeUndoReturn?.instanceId == returning.instanceId) {
                                        activeUndoReturn = null
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(5f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerTopHeader(
    currentItem: MediaItem?,
    currentIndex: Int,
    totalCount: Int,
    trashCount: Int,
    onBack: () -> Unit,
    onOpenTrash: () -> Unit,
    onTrashIconPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconScale = remember { Animatable(1f) }
    val badgeScale = remember { Animatable(1f) }
    var previousTrashCount by remember { mutableIntStateOf(trashCount) }

    LaunchedEffect(trashCount) {
        val previous = previousTrashCount
        previousTrashCount = trashCount
        if (trashCount > previous) {
            coroutineScope {
                launch {
                    iconScale.animateTo(
                        targetValue = 1.22f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessHigh,
                        ),
                    )
                    iconScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.75f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                }
                launch {
                    badgeScale.snapTo(0.72f)
                    badgeScale.animateTo(
                        targetValue = 1.24f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessHigh,
                        ),
                    )
                    badgeScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.72f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                }
            }
        } else if (trashCount < previous) {
            iconScale.snapTo(1f)
            badgeScale.snapTo(1f)
        }
    }

    // The header owns dedicated layout space above the viewport. The centered block is aligned to
    // the header's own center (not to the space left between the buttons), so the date and the
    // position remain visually centered on screen regardless of badge width.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(Color.Black)
            .testTag("viewer_top_header"),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .minimumInteractiveComponentSize()
                .testTag("viewer_back_button"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 72.dp),
        ) {
            if (currentItem != null) {
                Text(
                    text = Formatters.formatFullDateTime(currentItem.effectiveTimestampMillis),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${currentIndex + 1} / $totalCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                )
            }
        }

        HeaderTrashButton(
            trashCount = trashCount,
            onClick = onOpenTrash,
            buttonTestTag = "viewer_trash_button",
            iconTestTag = "viewer_trash_icon",
            badgeTestTag = "viewer_trash_badge",
            iconTint = if (trashCount > 0) Color.White else Color.White.copy(alpha = 0.72f),
            iconScale = iconScale.value,
            badgeScale = badgeScale.value,
            onIconPositioned = onTrashIconPositioned,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun ViewerBottomHeader(
    currentItem: MediaItem?,
    isTrashPreview: Boolean,
    undoRedoState: UndoRedoUiState,
    canMoveToAlbum: Boolean,
    onNext: () -> Unit,
    onUndo: () -> Unit,
    onMoveToAlbum: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Black,
        modifier = modifier
            .fillMaxWidth()
            .testTag("viewer_bottom_bar"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 8.dp),
        ) {
            if (isTrashPreview) {
                ViewerActionButton(
                    icon = Icons.Outlined.RestoreFromTrash,
                    contentDescription = stringResource(R.string.restore),
                    enabled = currentItem != null,
                    onClick = onRestore,
                    testTag = "viewer_restore_button",
                    modifier = Modifier.weight(1f),
                )
                ViewerActionButton(
                    icon = Icons.Outlined.DeleteForever,
                    contentDescription = stringResource(R.string.delete_permanently_action),
                    enabled = currentItem != null,
                    onClick = onPermanentDelete,
                    tint = MaterialTheme.colorScheme.error,
                    testTag = "viewer_permanent_delete_button",
                    modifier = Modifier.weight(1f),
                )
            } else {
                ViewerActionButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = stringResource(R.string.next),
                    enabled = currentItem != null,
                    onClick = onNext,
                    testTag = "viewer_next_button",
                    modifier = Modifier.weight(1f),
                )
                ViewerActionButton(
                    icon = Icons.AutoMirrored.Outlined.Undo,
                    contentDescription = stringResource(R.string.undo),
                    enabled = undoRedoState.canUndo,
                    onClick = onUndo,
                    testTag = "viewer_undo_button",
                    modifier = Modifier.weight(1f),
                )
                ViewerActionButton(
                    icon = Icons.Outlined.PhotoAlbum,
                    contentDescription = stringResource(R.string.move_to_album),
                    enabled = currentItem != null && canMoveToAlbum,
                    onClick = onMoveToAlbum,
                    testTag = "viewer_move_to_album_button",
                    modifier = Modifier.weight(1f),
                )
                ViewerActionButton(
                    icon = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.trash_action),
                    enabled = currentItem != null,
                    onClick = onTrash,
                    testTag = "viewer_trash_action_button",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ViewerActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.32f),
        )
    }
}

@Composable
private fun MoveToAlbumDialog(
    item: MediaItem,
    albums: List<AlbumCollection>,
    onDismiss: () -> Unit,
    onSelect: (AlbumCollection) -> Unit,
) {
    val currentFolder = remember(item.relativePath) { normalizeAlbumPath(item.relativePath) }
    val destinations = remember(albums, currentFolder) {
        albums.asSequence()
            .filter { it.kind == AlbumKind.FOLDER }
            .filter { album ->
                val folder = album.items.firstOrNull()?.relativePath ?: return@filter false
                normalizeAlbumPath(folder) != currentFolder
            }
            .distinctBy { normalizeAlbumPath(it.items.firstOrNull()?.relativePath.orEmpty()) }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_album_title)) },
        text = {
            if (destinations.isEmpty()) {
                Text(stringResource(R.string.move_to_album_empty))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                ) {
                    items(destinations, key = { it.id }) { album ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelect(album) }
                                .padding(horizontal = 4.dp, vertical = 12.dp)
                                .testTag("move_to_album_option_${album.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoAlbum,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = album.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${album.count} items",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun normalizeAlbumPath(path: String): String = path.trim().trim('/')

@Composable
private fun VideoControlsBar(
    exoPlayer: ExoPlayer?,
    fallbackDurationMs: Long,
) {
    var isPlaying by remember(exoPlayer) { mutableStateOf(exoPlayer?.isPlaying == true) }
    var isMuted by remember(exoPlayer) { mutableStateOf(exoPlayer?.volume == 0f) }
    var durationMs by remember(exoPlayer, fallbackDurationMs) {
        mutableLongStateOf(
            (exoPlayer?.duration?.takeIf { it > 0L } ?: fallbackDurationMs).coerceAtLeast(0L),
        )
    }
    var positionMs by remember(exoPlayer) {
        mutableLongStateOf(exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L)
    }
    var isScrubbing by remember(exoPlayer) { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        if (exoPlayer == null) return@DisposableEffect onDispose {}
        isPlaying = exoPlayer.isPlaying
        isMuted = exoPlayer.volume == 0f
        val initialDuration = exoPlayer.duration
        if (initialDuration > 0L) durationMs = initialDuration
        positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val d = exoPlayer.duration
                if (d > 0L) durationMs = d
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(exoPlayer, isPlaying, isScrubbing) {
        while (exoPlayer != null) {
            if (!isScrubbing) {
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val d = exoPlayer.duration
                if (d > 0L) durationMs = d
            }
            delay(200L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        val safeDuration = durationMs.coerceAtLeast(1L)
        Slider(
            value = positionMs.coerceIn(0L, safeDuration).toFloat(),
            onValueChange = { newVal ->
                isScrubbing = true
                positionMs = newVal.toLong()
            },
            onValueChangeFinished = {
                exoPlayer?.seekTo(positionMs)
                isScrubbing = false
            },
            valueRange = 0f..safeDuration.toFloat(),
            enabled = exoPlayer != null,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("video_seek_slider"),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(
                onClick = {
                    val player = exoPlayer ?: return@IconButton
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                },
                enabled = exoPlayer != null,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("video_play_pause_button"),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                )
            }

            Text(
                text = "${Formatters.formatDuration(positionMs)} / ${Formatters.formatDuration(durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )

            IconButton(
                onClick = {
                    val player = exoPlayer ?: return@IconButton
                    val nextMuted = !isMuted
                    isMuted = nextMuted
                    player.volume = if (nextMuted) 0f else 1f
                },
                enabled = exoPlayer != null,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("video_mute_button"),
            ) {
                Icon(
                    imageVector = if (isMuted) {
                        Icons.AutoMirrored.Outlined.VolumeOff
                    } else {
                        Icons.AutoMirrored.Outlined.VolumeUp
                    },
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = Color.White,
                )
            }
        }
    }
}

internal data class StackedActiveCardTransform(
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val rotationZ: Float,
)

internal data class StackedUnderlyingCardTransform(
    val revealProgress: Float,
    val scale: Float,
    val translationY: Float,
    val dimAlpha: Float,
)

internal fun computeStackedActiveCardTransform(
    offsetY: Float,
    dismissDistanceThresholdPx: Float,
    dismissTargetOffsetPx: Float,
): StackedActiveCardTransform {
    val liftProgress = (-offsetY / dismissDistanceThresholdPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val exitProgress = (-offsetY / dismissTargetOffsetPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val scale = 1f - (liftProgress * 0.08f) - (exitProgress * 0.06f)
    val rotationZ = (liftProgress * 1.8f + exitProgress * 0.7f).coerceIn(0f, 4f)
    return StackedActiveCardTransform(
        translationX = 0f,
        translationY = offsetY,
        scale = scale,
        rotationZ = rotationZ,
    )
}

internal fun computeStackedUnderlyingCardTransform(
    offsetY: Float,
    dismissDistanceThresholdPx: Float,
    restingOffsetPx: Float,
): StackedUnderlyingCardTransform {
    val revealProgress = (-offsetY / (dismissDistanceThresholdPx.coerceAtLeast(1f) * 1.45f)).coerceIn(0f, 1f)
    return StackedUnderlyingCardTransform(
        revealProgress = revealProgress,
        scale = 0.92f + (0.08f * revealProgress),
        translationY = restingOffsetPx * (1f - revealProgress),
        dimAlpha = 0.45f * (1f - revealProgress),
    )
}

internal fun buildViewerImageRequest(
    context: Context,
    item: MediaItem,
    maxDimensionPx: Int = 2048,
): ImageRequest {
    return ImageRequest.Builder(context)
        .data(item.contentUri)
        .apply {
            if (item.isVideo) {
                videoFrameMillis(500L)
            }
        }
        .setParameter(
            ViewerImageRequestParameter,
            "viewer:${item.id}:${item.contentUri}:$maxDimensionPx",
            memoryCacheKey = null,
        )
        .size(maxDimensionPx)
        .precision(Precision.INEXACT)
        .crossfade(false)
        .build()
}

private data class ExitingCardState(
    val instanceId: Long,
    val item: MediaItem,
    val progressState: MutableFloatState,
    val startOffsetY: Float,
    val startVelocityY: Float,
    val viewportWidthPx: Float,
    val viewportHeightPx: Float,
    val cardHeightPx: Float,
    val trashBinCenterInViewport: Offset,
    val dismissDistanceThresholdPx: Float,
)

private data class UndoReturningCardState(
    val instanceId: Long,
    val item: MediaItem,
    val targetPage: Int,
    val progressState: MutableFloatState,
    val startProgress: Float,
    val revealProgressState: MutableFloatState,
    val viewportWidthPx: Float,
    val viewportHeightPx: Float,
    val cardHeightPx: Float,
    val trashBinCenterInViewport: Offset,
    val dismissDistanceThresholdPx: Float,
)

@Composable
private fun ExitingCardsLayer(
    exitingCards: SnapshotStateList<ExitingCardState>,
    imageLoader: ImageLoader,
    resolvedRatios: MutableMap<Long, Float>,
    dismissStiffness: Float,
    dismissDampingRatio: Float,
) {
    for (idx in exitingCards.indices) {
        val exitCard = exitingCards[idx]
        key(exitCard.instanceId) {
            ExitingCardOverlay(
                exitCard = exitCard,
                imageLoader = imageLoader,
                resolvedRatios = resolvedRatios,
                dismissStiffness = dismissStiffness,
                dismissDampingRatio = dismissDampingRatio,
                onFinished = { exitingCards.remove(exitCard) },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f + idx * 0.01f),
            )
        }
    }
}

@Composable
private fun ExitingCardOverlay(
    exitCard: ExitingCardState,
    imageLoader: ImageLoader,
    resolvedRatios: MutableMap<Long, Float>,
    dismissStiffness: Float,
    dismissDampingRatio: Float,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(exitCard.instanceId) {
        val verticalTravel = (
            (exitCard.viewportHeightPx * 0.5f + exitCard.startOffsetY - exitCard.cardHeightPx * 0.5f) -
                exitCard.trashBinCenterInViewport.y
            ).coerceAtLeast(180f)
        val distanceEstimate = hypot(
            (exitCard.trashBinCenterInViewport.x - exitCard.viewportWidthPx * 0.5f).toDouble(),
            verticalTravel.toDouble(),
        ).toFloat().coerceAtLeast(240f)
        val initialVelocityProgress =
            (-exitCard.startVelocityY / distanceEstimate).coerceIn(0.6f, 3.4f)

        val anim = TargetBasedAnimation(
            animationSpec = spring(
                dampingRatio = maxOf(dismissDampingRatio, Spring.DampingRatioNoBouncy),
                stiffness = ViewerTransitionStiffness,
                visibilityThreshold = ViewerTransitionVisibilityThreshold,
            ),
            typeConverter = Float.VectorConverter,
            initialValue = 0f,
            targetValue = 1f,
            initialVelocity = initialVelocityProgress,
        )
        var startNanos = Long.MIN_VALUE
        try {
            while (true) {
                val frameNanos = withFrameNanos { it }
                if (startNanos == Long.MIN_VALUE) {
                    startNanos = frameNanos
                }
                val playNanos = frameNanos - startNanos
                if (anim.isFinishedFromNanos(playNanos)) {
                    exitCard.progressState.floatValue = 1f
                    break
                }
                exitCard.progressState.floatValue = anim.getValueFromNanos(playNanos).coerceIn(0f, 1f)
            }
        } finally {
            onFinished()
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                val liveRatio = resolvedRatios[exitCard.item.id] ?: exitCard.item.viewerAspectRatio()
                val liveCardHeight = resolveViewerCardSizePx(
                    viewportWidthPx = exitCard.viewportWidthPx,
                    viewportHeightPx = exitCard.viewportHeightPx,
                    aspectRatio = liveRatio,
                ).second.takeIf { it > 0f } ?: exitCard.cardHeightPx

                val transform = computeTrashBinTrajectoryTransform(
                    progress = exitCard.progressState.floatValue,
                    viewportWidthPx = exitCard.viewportWidthPx,
                    viewportHeightPx = exitCard.viewportHeightPx,
                    cardHeightPx = liveCardHeight,
                    trashBinCenterInViewport = exitCard.trashBinCenterInViewport,
                    startOffsetY = exitCard.startOffsetY,
                    dismissDistanceThresholdPx = exitCard.dismissDistanceThresholdPx,
                    startVelocityY = exitCard.startVelocityY,
                )

                translationX = transform.translationX
                translationY = transform.translationY
                scaleX = transform.scale
                scaleY = transform.scale
                rotationZ = transform.rotationZ
                alpha = transform.alpha
                cameraDistance = 16f * this.density
            }
            .testTag("viewer_exiting_card_${exitCard.item.id}"),
    ) {
        AdaptivePhotoCard(
            item = exitCard.item,
            imageLoader = imageLoader,
            resolvedRatios = resolvedRatios,
            modifier = Modifier.fillMaxSize(),
            cardTestTag = "viewer_exiting_media_card_${exitCard.item.id}",
        )
    }
}

@Composable
private fun UndoReturningCardOverlay(
    returningCard: UndoReturningCardState,
    imageLoader: ImageLoader,
    resolvedRatios: MutableMap<Long, Float>,
    settleStiffness: Float,
    settleDampingRatio: Float,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(returningCard.instanceId) {
        val anim = TargetBasedAnimation(
            animationSpec = spring(
                dampingRatio = maxOf(settleDampingRatio, Spring.DampingRatioNoBouncy),
                stiffness = ViewerTransitionStiffness,
                visibilityThreshold = ViewerTransitionVisibilityThreshold,
            ),
            typeConverter = Float.VectorConverter,
            initialValue = returningCard.startProgress,
            targetValue = 0f,
            initialVelocity = -0.75f,
        )
        var startNanos = Long.MIN_VALUE
        try {
            while (true) {
                val frameNanos = withFrameNanos { it }
                if (startNanos == Long.MIN_VALUE) {
                    startNanos = frameNanos
                }
                val playNanos = frameNanos - startNanos
                if (anim.isFinishedFromNanos(playNanos)) {
                    returningCard.progressState.floatValue = 0f
                    returningCard.revealProgressState.floatValue = 0f
                    break
                }
                val p = anim.getValueFromNanos(playNanos).coerceIn(0f, 1f)
                returningCard.progressState.floatValue = p
                returningCard.revealProgressState.floatValue = (p * 1.45f).coerceIn(0f, 1f)
            }
        } finally {
            Snapshot.withMutableSnapshot {
                onFinished()
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                val liveRatio =
                    resolvedRatios[returningCard.item.id] ?: returningCard.item.viewerAspectRatio()
                val liveCardHeight = resolveViewerCardSizePx(
                    viewportWidthPx = returningCard.viewportWidthPx,
                    viewportHeightPx = returningCard.viewportHeightPx,
                    aspectRatio = liveRatio,
                ).second.takeIf { it > 0f } ?: returningCard.cardHeightPx

                val transform = computeTrashBinTrajectoryTransform(
                    progress = returningCard.progressState.floatValue,
                    viewportWidthPx = returningCard.viewportWidthPx,
                    viewportHeightPx = returningCard.viewportHeightPx,
                    cardHeightPx = liveCardHeight,
                    trashBinCenterInViewport = returningCard.trashBinCenterInViewport,
                    startOffsetY = 0f,
                    dismissDistanceThresholdPx = returningCard.dismissDistanceThresholdPx,
                )

                translationX = transform.translationX
                translationY = transform.translationY
                scaleX = transform.scale
                scaleY = transform.scale
                rotationZ = transform.rotationZ
                alpha = transform.alpha
                cameraDistance = 16f * this.density
            }
            .testTag("viewer_undo_returning_card_${returningCard.item.id}"),
    ) {
        AdaptivePhotoCard(
            item = returningCard.item,
            imageLoader = imageLoader,
            resolvedRatios = resolvedRatios,
            modifier = Modifier.fillMaxSize(),
            cardTestTag = "viewer_undo_returning_media_card_${returningCard.item.id}",
        )
    }
}

@Composable
private fun StackedUnderlyingCard(
    item: MediaItem,
    imageLoader: ImageLoader,
    resolvedRatios: MutableMap<Long, Float>,
    offsetY: FloatState,
    dismissDistanceThresholdPx: Float,
    revealProgressOverride: FloatState? = null,
    modifier: Modifier = Modifier,
) {
    val restingOffsetPx = 0f
    val revealDistancePx = (dismissDistanceThresholdPx * 1.45f).coerceAtLeast(1f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                val revealProgress = revealProgressOverride?.floatValue?.coerceIn(0f, 1f)
                    ?: (-offsetY.floatValue / revealDistancePx).coerceIn(0f, 1f)
                val scale = 0.92f + (0.08f * revealProgress)
                scaleX = scale
                scaleY = scale
                translationY = restingOffsetPx * (1f - revealProgress)
                // A staged card with a different aspect ratio can be larger than the active card.
                // Keep it fully hidden at rest and fade it in only while the active card is moving.
                alpha = revealProgress
            }
            .testTag("viewer_underlying_card"),
    ) {
        AdaptivePhotoCard(
            item = item,
            imageLoader = imageLoader,
            resolvedRatios = resolvedRatios,
            modifier = Modifier.fillMaxSize(),
            cardTestTag = "viewer_underlying_media_card_${item.id}",
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val revealProgress = revealProgressOverride?.floatValue?.coerceIn(0f, 1f)
                        ?: (-offsetY.floatValue / revealDistancePx).coerceIn(0f, 1f)
                    alpha = 0.45f * (1f - revealProgress)
                }
                .background(Color.Black)
                .testTag("viewer_underlying_dim"),
        )
    }
}

/**
 * Displays exactly one copy of the media inside a card whose size follows the media's intrinsic
 * aspect ratio. The card is centered in the available viewport and is never cropped, stretched, or
 * padded with a blurred backdrop.
 */
@Composable
private fun AdaptivePhotoCard(
    item: MediaItem,
    imageLoader: ImageLoader,
    resolvedRatios: MutableMap<Long, Float>,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    cardTestTag: String? = null,
    imageTestTag: String? = null,
    onCardSizeChanged: (IntSize) -> Unit = {},
) {
    val context = LocalContext.current
    val request = remember(item.id, item.contentUri, item.isVideo) {
        buildViewerImageRequest(context, item)
    }
    val painter = rememberAsyncImagePainter(
        model = request,
        imageLoader = imageLoader,
        contentScale = ContentScale.Fit,
    )
    // The decoded painter's intrinsic size is authoritative: Coil applies EXIF rotation before the
    // drawable is exposed, so a portrait photo stored as a rotated landscape buffer still reports
    // a portrait ratio here. MediaStore width/height is only the pre-decode fallback.
    val intrinsicSize = painter.intrinsicSize
    val decodedRatio = if (
        intrinsicSize.isSpecified &&
        intrinsicSize.width.isFinite() &&
        intrinsicSize.height.isFinite() &&
        intrinsicSize.width > 0f &&
        intrinsicSize.height > 0f
    ) {
        intrinsicSize.width / intrinsicSize.height
    } else {
        null
    }
    LaunchedEffect(item.id, decodedRatio) {
        if (decodedRatio != null) {
            resolvedRatios[item.id] = decodedRatio
        }
    }

    AdaptiveMediaFrame(
        ratio = decodedRatio ?: resolvedRatios[item.id] ?: item.viewerAspectRatio(),
        modifier = modifier,
        cardTestTag = cardTestTag,
        onCardSizeChanged = onCardSizeChanged,
    ) {
        Image(
            painter = painter,
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .then(if (imageTestTag != null) Modifier.testTag(imageTestTag) else Modifier)
                .then(imageModifier),
        )
    }
}

/** Centers a card sized from [ratio] inside the full-size viewport without reserving extra space. */
@Composable
private fun AdaptiveMediaFrame(
    ratio: Float?,
    modifier: Modifier = Modifier,
    cardTestTag: String? = null,
    onCardSizeChanged: (IntSize) -> Unit = {},
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        val safeRatio = ratio?.takeIf { it.isFinite() && it > 0f }
        val bounded = maxWidth.value.isFinite() &&
            maxHeight.value.isFinite() &&
            maxWidth.value > 0f &&
            maxHeight.value > 0f
        val cardModifier = if (safeRatio == null || !bounded) {
            Modifier.fillMaxSize()
        } else {
            val containerRatio = maxWidth.value / maxHeight.value
            if (containerRatio > safeRatio) {
                // Height-limited media (e.g. tall portraits): fill the height, keep natural width.
                Modifier
                    .width(maxHeight * safeRatio)
                    .height(maxHeight)
            } else {
                // Width-limited media (e.g. landscapes and squares): keep natural height.
                Modifier
                    .width(maxWidth)
                    .height(maxWidth / safeRatio)
            }
        }
        Box(
            modifier = cardModifier
                .then(if (cardTestTag != null) Modifier.testTag(cardTestTag) else Modifier)
                .onSizeChanged(onCardSizeChanged),
        ) {
            content()
        }
    }
}

private fun MediaItem.viewerAspectRatio(): Float? {
    if (width <= 0 || height <= 0) return null
    val ratio = width.toFloat() / height.toFloat()
    return ratio.takeIf { it.isFinite() && it > 0f }
}

@Composable
private fun ZoomablePhotoPage(
    item: MediaItem,
    imageLoader: ImageLoader,
    resolvedRatios: MutableMap<Long, Float>,
    cardTestTag: String?,
    onZoomChanged: (Boolean) -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var cardSize by remember(item.id) { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(scale) {
        onZoomChanged(scale > 1.02f)
    }

    fun clampOffset(candidate: Offset, currentScale: Float): Offset {
        if (currentScale <= 1f || containerSize == IntSize.Zero || cardSize == IntSize.Zero) {
            return Offset.Zero
        }
        // Keep the viewport filled in each axis; an axis whose scaled card is smaller than the
        // viewport stays centered instead of allowing the photo to be dragged off-screen.
        val maxX = ((cardSize.width * currentScale - containerSize.width) / 2f).coerceAtLeast(0f)
        val maxY = ((cardSize.height * currentScale - containerSize.height) / 2f).coerceAtLeast(0f)
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY),
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            .viewerTapGestures(
                key = item.id,
                onTap = onTap,
                onLongPress = onLongPress,
                onDoubleTap = {
                    if (scale > 1.05f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                },
            )
            .pointerInput(item.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        if (pointerCount >= 2) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroidSize = event.calculateCentroidSize(useCurrent = false)
                            if (zoomChange != 1f || (scale > 1.02f && centroidSize > 0f)) {
                                val nextScale = (scale * zoomChange).coerceIn(1f, 3.5f)
                                val nextOffset = if (nextScale <= 1.02f) {
                                    Offset.Zero
                                } else {
                                    clampOffset(offset + panChange, nextScale)
                                }
                                scale = nextScale
                                offset = nextOffset
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } else if (pointerCount == 1 && scale > 1.02f) {
                            val panChange = event.calculatePan()
                            if (panChange != Offset.Zero) {
                                offset = clampOffset(offset + panChange, scale)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        AdaptivePhotoCard(
            item = item,
            imageLoader = imageLoader,
            resolvedRatios = resolvedRatios,
            modifier = Modifier.fillMaxSize(),
            imageModifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
            cardTestTag = cardTestTag,
            imageTestTag = "viewer_photo_image_${item.id}",
            onCardSizeChanged = { cardSize = it },
        )
    }
}

@Composable
private fun VideoPlayerPage(
    item: MediaItem,
    imageLoader: ImageLoader,
    resolvedRatios: MutableMap<Long, Float>,
    cardTestTag: String?,
    isActivePage: Boolean,
    isCurrentPage: Boolean,
    onPlayerActive: (ExoPlayer) -> Unit,
    onPlayerInactive: (ExoPlayer) -> Unit,
    onToggleTransport: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .viewerTapGestures(
                key = item.id,
                onTap = onToggleTransport,
                onLongPress = onLongPress,
            ),
    ) {
        if (!isCurrentPage) {
            AdaptivePhotoCard(
                item = item,
                imageLoader = imageLoader,
                resolvedRatios = resolvedRatios,
                modifier = Modifier.fillMaxSize(),
                cardTestTag = cardTestTag,
                imageTestTag = "viewer_video_frame_${item.id}",
            )
        } else {
            AdaptiveMediaFrame(
                ratio = resolvedRatios[item.id] ?: item.viewerAspectRatio(),
                modifier = Modifier.fillMaxSize(),
                cardTestTag = cardTestTag,
            ) {
                val lifecycleOwner = LocalLifecycleOwner.current
                val exoPlayer = remember(item.contentUri) {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(androidx.media3.common.MediaItem.fromUri(item.contentUri))
                        repeatMode = Player.REPEAT_MODE_ONE
                        prepare()
                    }
                }

                DisposableEffect(exoPlayer) {
                    onDispose {
                        onPlayerInactive(exoPlayer)
                        exoPlayer.release()
                    }
                }

                DisposableEffect(isCurrentPage, exoPlayer) {
                    onPlayerActive(exoPlayer)
                    onDispose {
                        onPlayerInactive(exoPlayer)
                    }
                }

                DisposableEffect(lifecycleOwner, exoPlayer) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_PAUSE) {
                            exoPlayer.pause()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                // Autoplay with audio when settled on this video page
                LaunchedEffect(isActivePage, exoPlayer) {
                    if (isActivePage) {
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    } else {
                        exoPlayer.playWhenReady = false
                        exoPlayer.pause()
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        val view = LayoutInflater.from(ctx)
                            .inflate(R.layout.viewer_player_view, null, false) as PlayerView
                        view.apply {
                            useController = false
                            isClickable = false
                            isFocusable = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            player = exoPlayer
                        }
                    },
                    update = { playerView ->
                        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        if (playerView.player !== exoPlayer) {
                            playerView.player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun Modifier.viewerTapGestures(
    key: Any,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDoubleTap: (() -> Unit)? = null,
): Modifier = composed {
    var exceededTapSlop by remember(key) { mutableStateOf(false) }
    this
        .pointerInput(key) {
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                exceededTapSlop = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if ((change.position - down.position).getDistance() > touchSlop) {
                        exceededTapSlop = true
                    }
                    if (!change.pressed) break
                }
            }
        }
        .pointerInput(key) {
            detectTapGestures(
                onTap = { if (!exceededTapSlop) onTap() },
                onLongPress = { if (!exceededTapSlop) onLongPress() },
                onDoubleTap = onDoubleTap?.let { callback ->
                    { if (!exceededTapSlop) callback() }
                },
            )
        }
}
