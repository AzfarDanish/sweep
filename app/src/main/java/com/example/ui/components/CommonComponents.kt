package com.example.ui.components

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.R
import com.example.model.MediaItem
import com.example.util.Formatters

object SweepImageLoaderFactory {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: ImageLoader.Builder(context.applicationContext)
                .components {
                    add(VideoFrameDecoder.Factory())
                }
                .memoryCache {
                    MemoryCache.Builder(context.applicationContext)
                        .maxSizePercent(0.20)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.applicationContext.cacheDir.resolve("media_thumbs"))
                        .maxSizeBytes(64L * 1024L * 1024L)
                        .build()
                }
                .crossfade(120)
                .build()
                .also { instance = it }
        }
    }
}

@Composable
fun rememberSweepImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) { SweepImageLoaderFactory.get(context) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumbnailTile(
    item: MediaItem,
    imageLoader: ImageLoader,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request = remember(item.contentUri, item.isVideo) {
        ImageRequest.Builder(context)
            .data(item.contentUri)
            .size(360)
            .apply {
                if (item.isVideo) {
                    videoFrameMillis(500L)
                }
            }
            .build()
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(2.dp),
                    )
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .testTag("media_tile_${item.id}"),
    ) {
        AsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (isSelected) Modifier.padding(4.dp) else Modifier),
        )

        if (item.isVideo) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.68f),
                        shape = RoundedCornerShape(4.dp),
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = Formatters.formatDuration(item.durationMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }

        if (isSelectionMode) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.45f),
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                        shape = CircleShape,
                    ),
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onPrimaryAction: () -> Unit,
    primaryActionIcon: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = "Move to trash",
        )
    },
    primaryActionTestTag: String = "selection_trash_button",
    extraActions: @Composable () -> Unit = {},
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        navigationIcon = {
            IconButton(
                onClick = onClearSelection,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("clear_selection_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Clear selection",
                )
            }
        },
        title = {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        actions = {
            IconButton(
                onClick = onSelectAll,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("select_all_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.SelectAll,
                    contentDescription = "Select all",
                )
            }
            extraActions()
            IconButton(
                onClick = onPrimaryAction,
                enabled = selectedCount > 0,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag(primaryActionTestTag),
            ) {
                primaryActionIcon()
            }
        },
    )
}

@Composable
fun PermissionGateView(
    onGrantClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.permission_rationale),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onGrantClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("grant_media_access_button"),
            ) {
                Text(text = stringResource(R.string.permission_grant_button))
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenSettingsClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_app_settings_button"),
            ) {
                Text(text = stringResource(R.string.permission_open_settings))
            }
        }
    }
}

@Composable
fun PartialAccessBanner(
    onManageAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.partial_access_banner),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onManageAccess,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("manage_partial_access_button"),
            ) {
                Text(text = stringResource(R.string.partial_access_manage))
            }
        }
    }
}

@Composable
fun PermanentDeleteDialog(
    itemCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val message = if (itemCount <= 1) {
        stringResource(R.string.delete_permanently_message)
    } else {
        stringResource(R.string.delete_permanently_many_message, itemCount)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.delete_permanently_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_permanent_delete_button"),
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_permanent_delete_button"),
            ) {
                Text(
                    text = stringResource(R.string.delete_permanently_action),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

@Composable
fun EmptyTrashDialog(
    itemCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.empty_trash_dialog_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.empty_trash_dialog_message, itemCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_empty_trash_button"),
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_empty_trash_button"),
            ) {
                Text(
                    text = stringResource(R.string.empty_trash_action),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

@Composable
fun MediaInfoSheet(
    item: MediaItem,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val initialTravelPx = with(density) { 420.dp.toPx() }
    var sheetHeightPx by remember { mutableFloatStateOf(initialTravelPx) }

    val sheetConfig = rememberPhysicsVerticalSwipeConfig(
        direction = VerticalSwipeDirection.DownToDismiss,
        containerHeightPx = sheetHeightPx,
        maxDismissThresholdDp = 110.dp,
        minFlickDistanceDp = 40.dp,
        flickVelocityThresholdDpPerSec = 750.dp,
        overscrollLimitDp = 18.dp,
    )

    val controller = remember {
        PhysicsVerticalSwipeController(
            direction = VerticalSwipeDirection.DownToDismiss,
            initialOffsetY = initialTravelPx,
            initialPhase = VerticalSwipePhase.Idle,
        )
    }

    LaunchedEffect(Unit) {
        controller.animateToRest(
            scope = scope,
            config = sheetConfig,
            restPhase = VerticalSwipePhase.Expanded,
        )
    }

    val dismissWithSpring: () -> Unit = {
        controller.animateToDismissed(
            scope = scope,
            config = sheetConfig,
            onDismissed = onDismiss,
        )
    }

    BackHandler {
        dismissWithSpring()
    }

    val noIndicationInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(10f)
            .testTag("media_info_sheet_overlay"),
    ) {
        // Scrim with progress-driven alpha in graphicsLayer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = (1f - (controller.offsetY.value / sheetConfig.dismissTargetOffsetPx))
                        .coerceIn(0f, 1f)
                    alpha = progress * 0.52f
                }
                .background(Color.Black)
                .clickable(
                    interactionSource = noIndicationInteraction,
                    indication = null,
                    onClick = dismissWithSpring,
                )
                .testTag("media_info_scrim"),
        )

        Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = 2.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .onSizeChanged { size ->
                    if (size.height > 0) {
                        sheetHeightPx = size.height.toFloat()
                    }
                }
                .physicsVerticalSwipe(
                    controller = controller,
                    config = sheetConfig,
                    scope = scope,
                    key = item.id,
                    restPhase = VerticalSwipePhase.Expanded,
                    allowOverscrollEngageFromRest = true,
                    onDismissed = onDismiss,
                )
                .graphicsLayer {
                    translationY = controller.offsetY.value
                }
                .pointerInput(Unit) {
                    detectTapGestures { /* absorb taps so scrim behind sheet is not triggered */ }
                }
                .testTag("media_info_sheet"),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                        ),
                    )
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 24.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .testTag("media_info_drag_handle"),
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                    )
                }

                Text(
                    text = "Media information",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(16.dp))
                MetadataRow(label = "Name", value = item.displayName)
                MetadataRow(
                    label = "Date",
                    value = Formatters.formatFullDateTime(item.effectiveTimestampMillis),
                )
                if (item.width > 0 && item.height > 0) {
                    MetadataRow(label = "Dimensions", value = "${item.width} × ${item.height}")
                }
                if (item.isVideo) {
                    MetadataRow(
                        label = "Duration",
                        value = Formatters.formatDuration(item.durationMillis),
                    )
                }
                MetadataRow(label = "Size", value = Formatters.formatFileSize(item.sizeBytes))
                MetadataRow(label = "Type", value = item.mimeType)
                MetadataRow(
                    label = "Location",
                    value = item.relativePath.ifBlank { item.bucketName },
                )
            }
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(100.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}
