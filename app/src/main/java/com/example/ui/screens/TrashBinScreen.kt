package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.MediaItem
import com.example.ui.components.EmptyTrashDialog
import com.example.ui.components.MediaThumbnailTile
import com.example.ui.components.PermanentDeleteDialog
import com.example.ui.components.SelectionTopBar
import com.example.ui.components.dragSelectGrid
import com.example.ui.components.rememberSweepImageLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashBinScreen(
    trashedItems: List<MediaItem>,
    selectedUris: Set<Uri>,
    gridColumns: Int,
    onBack: () -> Unit,
    onOpenTrashPreview: (List<MediaItem>, MediaItem) -> Unit,
    onToggleSelection: (Uri) -> Unit,
    onSelectAll: (List<Uri>) -> Unit,
    onClearSelection: () -> Unit,
    onRestoreUris: (List<Uri>) -> Unit,
    onPermanentDeleteUris: (List<Uri>) -> Unit,
    onSelectUris: (List<Uri>) -> Unit = { uris ->
        uris.forEach { uri ->
            if (uri !in selectedUris) onToggleSelection(uri)
        }
    },
) {
    var manualSelectionMode by remember { mutableStateOf(false) }
    val isSelectionMode = selectedUris.isNotEmpty() || (manualSelectionMode && trashedItems.isNotEmpty())
    val imageLoader = rememberSweepImageLoader()
    val gridState = rememberLazyGridState()
    val uriById = remember(trashedItems) {
        HashMap<Long, Uri>(trashedItems.size).apply {
            trashedItems.forEach { put(it.id, it.contentUri) }
        }
    }
    var pendingDeleteUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(trashedItems.isEmpty()) {
        if (trashedItems.isEmpty()) {
            manualSelectionMode = false
        }
    }

    BackHandler(enabled = isSelectionMode) {
        manualSelectionMode = false
        onClearSelection()
    }

    if (pendingDeleteUris.isNotEmpty()) {
        PermanentDeleteDialog(
            itemCount = pendingDeleteUris.size,
            onDismiss = { pendingDeleteUris = emptyList() },
            onConfirm = {
                val uris = pendingDeleteUris
                pendingDeleteUris = emptyList()
                manualSelectionMode = false
                onPermanentDeleteUris(uris)
            },
        )
    }

    if (showEmptyTrashDialog && trashedItems.isNotEmpty()) {
        EmptyTrashDialog(
            itemCount = trashedItems.size,
            onDismiss = { showEmptyTrashDialog = false },
            onConfirm = {
                showEmptyTrashDialog = false
                manualSelectionMode = false
                onPermanentDeleteUris(trashedItems.map { it.contentUri })
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                if (isSelectionMode) {
                    SelectionTopBar(
                        selectedCount = selectedUris.size,
                        onClearSelection = {
                            manualSelectionMode = false
                            onClearSelection()
                        },
                        onSelectAll = { onSelectAll(trashedItems.map { it.contentUri }) },
                        onPrimaryAction = {
                            manualSelectionMode = false
                            onRestoreUris(selectedUris.toList())
                        },
                        primaryActionIcon = {
                            Icon(
                                imageVector = Icons.Outlined.RestoreFromTrash,
                                contentDescription = stringResource(R.string.restore),
                            )
                        },
                        primaryActionTestTag = "trash_restore_selected_button",
                        extraActions = {
                            IconButton(
                                onClick = { pendingDeleteUris = selectedUris.toList() },
                                enabled = selectedUris.isNotEmpty(),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("trash_delete_selected_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteForever,
                                    contentDescription = stringResource(R.string.delete_permanently_action),
                                    tint = if (selectedUris.isNotEmpty()) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    },
                                )
                            }
                        },
                    )
                } else {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                        navigationIcon = {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("trash_back_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Back",
                                )
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = stringResource(R.string.trash_bin),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                if (trashedItems.isNotEmpty()) {
                                    Text(
                                        text = "${trashedItems.size} items",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        actions = {
                            if (trashedItems.isNotEmpty()) {
                                IconButton(
                                    onClick = { manualSelectionMode = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("trash_select_mode_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Checklist,
                                        contentDescription = "Select items",
                                    )
                                }
                                TextButton(
                                    onClick = { showEmptyTrashDialog = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("empty_trash_button"),
                                ) {
                                    Text(
                                        text = stringResource(R.string.empty_trash_action),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                    )
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        },
    ) { innerPadding ->
        if (trashedItems.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.empty_trash),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.empty_trash_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .dragSelectGrid(
                        gridState = gridState,
                        isSelectionMode = isSelectionMode,
                        resolveUriForKey = { key -> (key as? Long)?.let(uriById::get) },
                        onSelectUris = onSelectUris,
                    )
                    .testTag("trash_grid"),
            ) {
                items(
                    items = trashedItems,
                    key = { it.id },
                ) { item ->
                    val isSelected = item.contentUri in selectedUris
                    MediaThumbnailTile(
                        item = item,
                        imageLoader = imageLoader,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelectionMode) {
                                onToggleSelection(item.contentUri)
                            } else {
                                onOpenTrashPreview(trashedItems, item)
                            }
                        },
                        onLongClick = {
                            if (!isSelected) {
                                onSelectUris(listOf(item.contentUri))
                            }
                        },
                    )
                }
            }
        }
    }
}
