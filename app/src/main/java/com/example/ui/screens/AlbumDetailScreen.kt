package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AlbumCollection
import com.example.model.MediaItem
import com.example.ui.components.MediaThumbnailTile
import com.example.ui.components.SelectionTopBar
import com.example.ui.components.rememberSweepImageLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    album: AlbumCollection?,
    selectedUris: Set<Uri>,
    gridColumns: Int,
    onBack: () -> Unit,
    onOpenViewer: (List<MediaItem>, MediaItem) -> Unit,
    onToggleSelection: (Uri) -> Unit,
    onSelectAll: (List<Uri>) -> Unit,
    onClearSelection: () -> Unit,
    onTrashSelected: () -> Unit,
) {
    val isSelectionMode = selectedUris.isNotEmpty()
    val imageLoader = rememberSweepImageLoader()
    val items = album?.items.orEmpty()

    BackHandler(enabled = isSelectionMode) {
        onClearSelection()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                if (isSelectionMode) {
                    SelectionTopBar(
                        selectedCount = selectedUris.size,
                        onClearSelection = onClearSelection,
                        onSelectAll = { onSelectAll(items.map { it.contentUri }) },
                        onPrimaryAction = onTrashSelected,
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
                                    .testTag("album_detail_back_button"),
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
                                    text = album?.name ?: "Album",
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (album != null) {
                                    Text(
                                        text = "${album.count} items · ${album.subtitle}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
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
        if (items.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Text(
                    text = "No items in this album",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("album_detail_grid"),
            ) {
                items(
                    items = items,
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
                                onOpenViewer(items, item)
                            }
                        },
                        onLongClick = { onToggleSelection(item.contentUri) },
                    )
                }
            }
        }
    }
}
