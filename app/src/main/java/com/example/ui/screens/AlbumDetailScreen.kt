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
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AlbumCollection
import com.example.model.AlbumSortOption
import com.example.model.MediaItem
import com.example.model.sortAlbumMedia
import com.example.ui.components.MediaThumbnailTile
import com.example.ui.components.SelectionTopBar
import com.example.ui.components.rememberSweepImageLoader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    album: AlbumCollection?,
    selectedUris: Set<Uri>,
    gridColumns: Int,
    selectedSort: AlbumSortOption = AlbumSortOption.OLDEST_FIRST,
    onSortChange: (AlbumSortOption) -> Unit = {},
    onBack: () -> Unit,
    onOpenViewer: (List<MediaItem>, MediaItem) -> Unit,
    onToggleSelection: (Uri) -> Unit,
    onSelectAll: (List<Uri>) -> Unit,
    onClearSelection: () -> Unit,
    onTrashSelected: () -> Unit,
) {
    val isSelectionMode = selectedUris.isNotEmpty()
    val imageLoader = rememberSweepImageLoader()
    val items = remember(album?.items, selectedSort) {
        sortAlbumMedia(album?.items.orEmpty(), selectedSort)
    }
    var showSortMenu by remember { mutableStateOf(false) }

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
                        actions = {
                            Box {
                                IconButton(
                                    onClick = { showSortMenu = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("album_sort_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Sort,
                                        contentDescription = stringResource(R.string.sort_by),
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    modifier = Modifier.testTag("album_sort_menu"),
                                ) {
                                    AlbumSortOption.entries.forEach { option ->
                                        val isSelected = option == selectedSort
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = stringResource(option.labelRes()),
                                                    fontWeight = if (isSelected) {
                                                        FontWeight.SemiBold
                                                    } else {
                                                        FontWeight.Normal
                                                    },
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
                                                .testTag("album_sort_option_${option.name.lowercase()}"),
                                        )
                                    }
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

private fun AlbumSortOption.labelRes(): Int = when (this) {
    AlbumSortOption.OLDEST_FIRST -> R.string.sort_oldest_first
    AlbumSortOption.NEWEST_FIRST -> R.string.sort_newest_first
    AlbumSortOption.LARGEST_FIRST -> R.string.sort_largest_first
    AlbumSortOption.SMALLEST_FIRST -> R.string.sort_smallest_first
}
