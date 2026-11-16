package com.example.ui.screens

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.Disposable as CoilDisposable
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Precision
import com.example.R
import com.example.data.UndoRedoUiState
import com.example.model.MediaItem
import com.example.ui.components.MediaInfoSheet
import com.example.ui.components.PermanentDeleteDialog
import com.example.ui.components.PhysicsVerticalSwipeConfig
import com.example.ui.components.PhysicsVerticalSwipeController
import com.example.ui.components.VerticalSwipeDirection
import com.example.ui.components.VerticalSwipePhase
import com.example.ui.components.physicsVerticalSwipe
import com.example.ui.components.rememberPhysicsVerticalSwipeConfig
import com.example.ui.components.rememberSweepImageLoader
import com.example.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
) {
    val startIndex = remember(initialMediaId) {
        items.indexOfFirst { it.id == initialMediaId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
        pageCount = { items.size },
    )

    val context = LocalContext.current
    val localDensity = LocalDensity.current
    val imageLoader = rememberSweepImageLoader()

    var showControls by remember { mutableStateOf(true) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var isCurrentZoomed by remember { mutableStateOf(false) }
    var pendingDeleteItem by remember { mutableStateOf<MediaItem?>(null) }
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
    var previousItemIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val exitingCards = remember { mutableStateListOf<ExitingCardState>() }
    var nextExitInstanceId by remember { mutableLongStateOf(0L) }

    val incomingRevealProgress = remember { mutableFloatStateOf(1f) }
    var incomingExpansionJob by remember { mutableStateOf<Job?>(null) }
    var incomingGeneration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(items) {
        val currentIds = items.mapTo(HashSet(items.size)) { it.id }
        for (id in currentIds) {
            if (id !in previousItemIds) {
                committedTrashIds.remove(id)
            }
        }
        previousItemIds = currentIds
        if (items.isNotEmpty() && pagerState.currentPage >= items.size) {
            pagerState.scrollToPage(items.lastIndex)
        }
    }

    LaunchedEffect(effectivePage) {
        isCurrentZoomed = false
    }

    LaunchedEffect(currentItem?.id, currentItem?.isVideo) {
        if (currentItem?.isVideo != true) {
            activeExoPlayer = null
        }
    }

    // Memory-conscious lookahead preloading for next, next + 1, and previous items.
    val prefetchHandles = remember { LinkedHashMap<Long, CoilDisposable>() }
    LaunchedEffect(effectivePage, items, imageLoader) {
        val lookahead = buildList {
            items.getOrNull(effectivePage + 1)?.let(::add)
            items.getOrNull(effectivePage + 2)?.let(::add)
            items.getOrNull(effectivePage - 1)?.let(::add)
        }
        val desiredIds = lookahead.mapTo(HashSet(lookahead.size)) { it.id }
        val iterator = prefetchHandles.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in desiredIds) {
                entry.value.dispose()
                iterator.remove()
            }
        }
        for (target in lookahead) {
            if (target.id !in prefetchHandles) {
                prefetchHandles[target.id] = imageLoader.enqueue(
                    buildViewerImageRequest(context, target),
                )
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            prefetchHandles.values.forEach { it.dispose() }
            prefetchHandles.clear()
        }
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

    var viewportHeightPx by remember { mutableFloatStateOf(1600f) }
    val underlyingRestingOffsetPx = with(localDensity) { 24.dp.toPx() }
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

    LaunchedEffect(focusMediaIdFlow) {
        focusMediaIdFlow?.collect { targetId ->
            committedTrashIds.remove(targetId)
            val targetList = withTimeoutOrNull(600L) {
                snapshotFlow { latestItems }.firstOrNull { list ->
                    list.any { it.id == targetId }
                }
            } ?: latestItems
            val idx = targetList.indexOfFirst { it.id == targetId }
            if (idx >= 0) {
                trashSwipeController.snapToRest(VerticalSwipePhase.Idle)
                pagerState.animateScrollToPage(idx)
            }
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
        if (targetItem != null && !isTrashPreview && committedTrashIds.add(targetItem.id)) {
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
                instanceId = ++nextExitInstanceId,
                item = targetItem,
                offsetYState = mutableFloatStateOf(releasedOffsetY),
                targetOffsetY = -trashConfig.dismissTargetOffsetPx,
                initialVelocityY = releasedVelocityY,
                dismissDistanceThresholdPx = trashConfig.dismissDistanceThresholdPx,
            )
            exitingCards.add(exitCard)
            if (exitingCards.size > 4) {
                exitingCards.removeAt(0)
            }

            incomingExpansionJob?.cancel()
            val gen = ++incomingGeneration
            if (revealAtHandoff < 0.999f) {
                incomingRevealProgress.floatValue = revealAtHandoff
                incomingExpansionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    val anim = TargetBasedAnimation(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                        typeConverter = Float.VectorConverter,
                        initialValue = revealAtHandoff,
                        targetValue = 1f,
                    )
                    var startNanos = Long.MIN_VALUE
                    while (true) {
                        val frameNanos = withFrameNanos { it }
                        if (gen != incomingGeneration) return@launch
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

            latestOnTrashItem(targetItem)
        }
    }

    val triggerExitTrash: (MediaItem) -> Unit = { targetItem ->
        if (targetItem.id == latestCurrentItem?.id) {
            trashSwipeController.snapToRestSync(VerticalSwipePhase.Idle)
            commitTrashTransition(0f, -trashConfig.flickVelocityThresholdPx * 1.4f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("media_viewer_screen"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Header strictly outside and above the media viewport
            ViewerTopHeader(
                currentItem = currentItem,
                currentIndex = effectivePage,
                totalCount = items.size,
                trashCount = trashCount,
                isTrashPreview = isTrashPreview,
                undoRedoState = undoRedoState,
                controlsVisible = showControls || currentItem == null,
                onBack = onBack,
                onShowInfo = { showInfoSheet = true },
                onOpenTrash = onOpenTrash,
                onMoveToTrash = { currentItem?.let(triggerExitTrash) },
                onRestore = { currentItem?.let(onRestoreItem) },
                onPermanentDelete = { currentItem?.let { pendingDeleteItem = it } },
                onUndo = {
                    committedTrashIds.clear()
                    onUndo()
                },
                onRedo = onRedo,
            )

            // Calculated available content viewport strictly between top header and bottom controls
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .clipToBounds()
                    .onSizeChanged { size ->
                        if (size.height > 0) viewportHeightPx = size.height.toFloat()
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
                            if (!isTrashPreview && undoRedoState.canUndo) {
                                Button(
                                    onClick = {
                                        committedTrashIds.clear()
                                        onUndo()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black,
                                    ),
                                ) {
                                    Text(stringResource(R.string.undo))
                                }
                            }
                        }
                    }
                } else {
                    if (nextItem != null) {
                        StackedUnderlyingCard(
                            item = nextItem,
                            imageLoader = imageLoader,
                            offsetY = trashSwipeController.offsetY,
                            dismissDistanceThresholdPx = trashConfig.dismissDistanceThresholdPx,
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
                                enabled = !isTrashPreview && !isCurrentZoomed,
                                key = isTrashPreview,
                                restPhase = VerticalSwipePhase.Idle,
                                canEngage = {
                                    !pagerState.isScrollInProgress &&
                                        !isCurrentZoomed &&
                                        !isTrashPreview &&
                                        latestCurrentItem != null
                                },
                                pointerPass = PointerEventPass.Initial,
                                onDismissed = {},
                                onDismissCommit = commitTrashTransition,
                            )
                            .graphicsLayer {
                                val currentOffsetY = trashSwipeController.offsetY.floatValue
                                val dragTransform = computeStackedActiveCardTransform(
                                    offsetY = currentOffsetY,
                                    dismissDistanceThresholdPx = trashConfig.dismissDistanceThresholdPx,
                                    dismissTargetOffsetPx = trashConfig.dismissTargetOffsetPx,
                                )
                                val expandProgress = incomingRevealProgress.floatValue.coerceIn(0f, 1f)
                                val expandScale = 0.92f + (0.08f * expandProgress)
                                val expandOffsetY = underlyingRestingOffsetPx * (1f - expandProgress)

                                translationY = dragTransform.translationY + expandOffsetY
                                scaleX = dragTransform.scale * expandScale
                                scaleY = dragTransform.scale * expandScale
                                rotationZ = dragTransform.rotationZ
                                cameraDistance = 16f * density
                            }
                            .background(Color.Black)
                            .testTag("viewer_media_card"),
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
                            if (item.isVideo) {
                                VideoPlayerPage(
                                    item = item,
                                    imageLoader = imageLoader,
                                    isActivePage = isCurrentPage && pagerState.settledPage == page,
                                    isCurrentPage = isCurrentPage,
                                    onPlayerActive = { player -> activeExoPlayer = player },
                                    onPlayerInactive = { player ->
                                        if (activeExoPlayer === player) {
                                            activeExoPlayer = null
                                        }
                                    },
                                    onToggleControls = { showControls = !showControls },
                                    onLongPress = { showInfoSheet = true },
                                )
                            } else {
                                ZoomablePhotoPage(
                                    item = item,
                                    imageLoader = imageLoader,
                                    onZoomChanged = { zoomed ->
                                        if (page == effectivePage) {
                                            isCurrentZoomed = zoomed
                                        }
                                    },
                                    onTap = { showControls = !showControls },
                                    onLongPress = { showInfoSheet = true },
                                )
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

                    // Independent outgoing flight overlays so the incoming card is immediately interactive.
                    for (idx in exitingCards.indices) {
                        val exitCard = exitingCards[idx]
                        key(exitCard.instanceId) {
                            ExitingCardOverlay(
                                exitCard = exitCard,
                                imageLoader = imageLoader,
                                dismissStiffness = trashConfig.dismissStiffness,
                                dismissDampingRatio = trashConfig.dismissDampingRatio,
                                onFinished = { exitingCards.remove(exitCard) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(2f + idx * 0.01f),
                            )
                        }
                    }
                }
            }

            // Bottom Interface Bar strictly outside and below the media viewport
            ViewerBottomBar(
                currentItem = currentItem,
                isTrashPreview = isTrashPreview,
                activeExoPlayer = activeExoPlayer,
                showControls = showControls,
                trashSwipeController = trashSwipeController,
                trashSwipeConfig = trashConfig,
                scope = scope,
                canSwipeUpToTrash = !isTrashPreview &&
                    !isCurrentZoomed &&
                    !pagerState.isScrollInProgress &&
                    currentItem != null,
                onSwipeUpCommit = commitTrashTransition,
                onRestore = { currentItem?.let(onRestoreItem) },
                onPermanentDelete = { currentItem?.let { pendingDeleteItem = it } },
            )
        }
    }
}

@Composable
private fun ViewerTopHeader(
    currentItem: MediaItem?,
    currentIndex: Int,
    totalCount: Int,
    trashCount: Int,
    isTrashPreview: Boolean,
    undoRedoState: UndoRedoUiState,
    controlsVisible: Boolean,
    onBack: () -> Unit,
    onShowInfo: () -> Unit,
    onOpenTrash: () -> Unit,
    onMoveToTrash: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    Surface(
        color = Color.Black,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(1f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                )
                .heightIn(min = 56.dp),
        ) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("viewer_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
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

                    if (currentItem != null) {
                        IconButton(
                            onClick = onShowInfo,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("viewer_info_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "Media information",
                                tint = Color.White,
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenTrash,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("viewer_trash_button"),
                    ) {
                        if (trashCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = Color.White,
                                        contentColor = Color.Black,
                                        modifier = Modifier.testTag("viewer_trash_badge"),
                                    ) {
                                        Text(
                                            text = if (trashCount > 99) "99+" else trashCount.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = stringResource(R.string.trash_bin_count_cd, trashCount),
                                    tint = Color.White,
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.trash_bin_empty_cd),
                                tint = Color.White.copy(alpha = 0.72f),
                            )
                        }
                    }

                    if (currentItem != null) {
                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("viewer_more_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "More actions",
                                    tint = Color.White,
                                )
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                            ) {
                                if (isTrashPreview) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.restore)) },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.RestoreFromTrash, contentDescription = null)
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            onRestore()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.delete_permanently_action)) },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            onPermanentDelete()
                                        },
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Move to trash") },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            onMoveToTrash()
                                        },
                                    )
                                    if (undoRedoState.canUndo) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.undo)) },
                                            leadingIcon = {
                                                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = null)
                                            },
                                            onClick = {
                                                showMoreMenu = false
                                                onUndo()
                                            },
                                        )
                                    }
                                    if (undoRedoState.canRedo) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.redo)) },
                                            leadingIcon = {
                                                Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = null)
                                            },
                                            onClick = {
                                                showMoreMenu = false
                                                onRedo()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerBottomBar(
    currentItem: MediaItem?,
    isTrashPreview: Boolean,
    activeExoPlayer: ExoPlayer?,
    showControls: Boolean,
    trashSwipeController: PhysicsVerticalSwipeController,
    trashSwipeConfig: PhysicsVerticalSwipeConfig,
    scope: CoroutineScope,
    canSwipeUpToTrash: Boolean,
    onSwipeUpCommit: (Float, Float) -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
) {
    val controlsVisible = showControls &&
        trashSwipeController.phase != VerticalSwipePhase.Dragging &&
        trashSwipeController.phase != VerticalSwipePhase.Dismissed
    Surface(
        color = Color.Black,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(1f)
            .physicsVerticalSwipe(
                controller = trashSwipeController,
                config = trashSwipeConfig,
                scope = scope,
                enabled = currentItem != null && !isTrashPreview,
                key = isTrashPreview,
                restPhase = VerticalSwipePhase.Idle,
                canEngage = { canSwipeUpToTrash },
                onDismissed = {},
                onDismissCommit = onSwipeUpCommit,
            )
            .testTag("viewer_bottom_bar"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                    ),
                ),
        ) {
            if (currentItem == null) {
                return@Box
            }

            val minReservedHeight = when {
                isTrashPreview && currentItem.isVideo -> 164.dp
                currentItem.isVideo -> 104.dp
                isTrashPreview -> 68.dp
                // Only the drag handle remains for photos; the swipe hint used to reserve 52.dp.
                else -> 12.dp
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minReservedHeight),
            ) {
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (!isTrashPreview) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp, bottom = 2.dp)
                                    .width(32.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.28f))
                                    .testTag("viewer_bottom_drag_handle"),
                            )
                        }
                        if (currentItem.isVideo) {
                            VideoControlsBar(
                                exoPlayer = activeExoPlayer,
                                fallbackDurationMs = currentItem.durationMillis,
                            )
                        }
                        if (isTrashPreview) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                OutlinedButton(
                                    onClick = onRestore,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    modifier = Modifier.testTag("viewer_restore_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.RestoreFromTrash,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.restore))
                                }
                                OutlinedButton(
                                    onClick = onPermanentDelete,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                                    modifier = Modifier.testTag("viewer_permanent_delete_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteForever,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.delete_permanently_action))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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
        .size(maxDimensionPx)
        .precision(Precision.INEXACT)
        .crossfade(false)
        .build()
}

private data class ExitingCardState(
    val instanceId: Long,
    val item: MediaItem,
    val offsetYState: MutableFloatState,
    val targetOffsetY: Float,
    val initialVelocityY: Float,
    val dismissDistanceThresholdPx: Float,
)

@Composable
private fun ExitingCardOverlay(
    exitCard: ExitingCardState,
    imageLoader: ImageLoader,
    dismissStiffness: Float,
    dismissDampingRatio: Float,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    LaunchedEffect(exitCard.instanceId) {
        val anim = TargetBasedAnimation(
            animationSpec = spring(
                dampingRatio = dismissDampingRatio,
                stiffness = dismissStiffness,
            ),
            typeConverter = Float.VectorConverter,
            initialValue = exitCard.offsetYState.floatValue,
            targetValue = exitCard.targetOffsetY,
            initialVelocity = exitCard.initialVelocityY,
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
                    exitCard.offsetYState.floatValue = exitCard.targetOffsetY
                    break
                }
                exitCard.offsetYState.floatValue = anim.getValueFromNanos(playNanos)
            }
        } finally {
            onFinished()
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                val transform = computeStackedActiveCardTransform(
                    offsetY = exitCard.offsetYState.floatValue,
                    dismissDistanceThresholdPx = exitCard.dismissDistanceThresholdPx,
                    dismissTargetOffsetPx = -exitCard.targetOffsetY,
                )
                translationY = transform.translationY
                scaleX = transform.scale
                scaleY = transform.scale
                rotationZ = transform.rotationZ
                cameraDistance = 16f * density.density
            }
            .background(Color.Black)
            .testTag("viewer_exiting_card_${exitCard.item.id}"),
    ) {
        if (exitCard.item.isVideo) {
            val request = remember(exitCard.item.id, exitCard.item.contentUri) {
                buildViewerImageRequest(context, exitCard.item)
            }
            AsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = exitCard.item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LayeredPhotoContent(
                item = exitCard.item,
                imageLoader = imageLoader,
            )
        }
    }
}

@Composable
private fun StackedUnderlyingCard(
    item: MediaItem,
    imageLoader: ImageLoader,
    offsetY: FloatState,
    dismissDistanceThresholdPx: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val restingOffsetPx = with(LocalDensity.current) { 24.dp.toPx() }
    val revealDistancePx = (dismissDistanceThresholdPx * 1.45f).coerceAtLeast(1f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                val revealProgress = (-offsetY.floatValue / revealDistancePx).coerceIn(0f, 1f)
                val scale = 0.92f + (0.08f * revealProgress)
                scaleX = scale
                scaleY = scale
                translationY = restingOffsetPx * (1f - revealProgress)
            }
            .background(Color.Black)
            .testTag("viewer_underlying_card"),
    ) {
        if (item.isVideo) {
            val request = remember(item.id, item.contentUri) {
                buildViewerImageRequest(context, item)
            }
            AsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LayeredPhotoContent(
                item = item,
                imageLoader = imageLoader,
                backdropTestTag = "viewer_underlying_backdrop_${item.id}",
                foregroundTestTag = "viewer_underlying_foreground_${item.id}",
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val revealProgress = (-offsetY.floatValue / revealDistancePx).coerceIn(0f, 1f)
                    alpha = 0.45f * (1f - revealProgress)
                }
                .background(Color.Black)
                .testTag("viewer_underlying_dim"),
        )
    }
}

@Composable
private fun LayeredPhotoContent(
    item: MediaItem,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    foregroundScale: () -> Float = { 1f },
    foregroundOffset: () -> Offset = { Offset.Zero },
    backdropTestTag: String? = null,
    foregroundTestTag: String? = null,
) {
    val context = LocalContext.current
    val request = remember(item.id, item.contentUri, item.isVideo) {
        buildViewerImageRequest(context, item)
    }
    // Single painter shared by both background and foreground Image composables so Coil decodes once.
    val painter = rememberAsyncImagePainter(
        model = request,
        imageLoader = imageLoader,
        contentScale = ContentScale.Fit,
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .let { base ->
                    if (backdropTestTag != null) base.testTag(backdropTestTag) else base
                }
                .graphicsLayer {
                    scaleX = 1.08f
                    scaleY = 1.08f
                    alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.48f else 0.28f
                }
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(
                            radius = 28.dp,
                            edgeTreatment = BlurredEdgeTreatment.Rectangle,
                        )
                    } else {
                        Modifier
                    },
                ),
        )

        Image(
            painter = painter,
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .let { base ->
                    if (foregroundTestTag != null) base.testTag(foregroundTestTag) else base
                }
                .graphicsLayer {
                    val s = foregroundScale()
                    val o = foregroundOffset()
                    scaleX = s
                    scaleY = s
                    translationX = o.x
                    translationY = o.y
                },
        )
    }
}

@Composable
private fun ZoomablePhotoPage(
    item: MediaItem,
    imageLoader: ImageLoader,
    onZoomChanged: (Boolean) -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(scale) {
        onZoomChanged(scale > 1.02f)
    }

    fun clampOffset(candidate: Offset, currentScale: Float): Offset {
        if (currentScale <= 1f || containerSize == IntSize.Zero) return Offset.Zero
        val maxX = ((currentScale - 1f) * containerSize.width) / 2f
        val maxY = ((currentScale - 1f) * containerSize.height) / 2f
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
        LayeredPhotoContent(
            item = item,
            imageLoader = imageLoader,
            foregroundScale = { scale },
            foregroundOffset = { offset },
            backdropTestTag = "viewer_photo_backdrop_${item.id}",
            foregroundTestTag = "viewer_photo_foreground_${item.id}",
        )
    }
}

@Composable
private fun VideoPlayerPage(
    item: MediaItem,
    imageLoader: ImageLoader,
    isActivePage: Boolean,
    isCurrentPage: Boolean,
    onPlayerActive: (ExoPlayer) -> Unit,
    onPlayerInactive: (ExoPlayer) -> Unit,
    onToggleControls: () -> Unit,
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
                onTap = onToggleControls,
                onLongPress = onLongPress,
            ),
    ) {
        if (!isCurrentPage) {
            val request = remember(item.id, item.contentUri) {
                buildViewerImageRequest(context, item)
            }
            AsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = item.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
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
