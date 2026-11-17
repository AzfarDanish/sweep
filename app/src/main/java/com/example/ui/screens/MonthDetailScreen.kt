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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.AlbumSortOption
import com.example.model.MediaItem
import com.example.model.sortAlbumMedia
import com.example.ui.components.MediaThumbnailTile
import com.example.ui.components.SelectionTopBar
import com.example.ui.components.SortMenuButton
import com.example.ui.components.dragSelectGrid
import com.example.ui.components.rememberSweepImageLoader
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthDetailScreen(
    year: Int,
    month: Int,
    items: List<MediaItem>,
    selectedUris: Set<Uri>,
    gridColumns: Int,
    selectedSort: AlbumSortOption = AlbumSortOption.NEWEST_FIRST,
    onSortChange: (AlbumSortOption) -> Unit = {},
    onBack: () -> Unit,
    onOpenViewer: (List<MediaItem>, MediaItem) -> Unit,
    onToggleSelection: (Uri) -> Unit,
    onSelectAll: (List<Uri>) -> Unit,
    onClearSelection: () -> Unit,
    onTrashSelected: () -> Unit,
    onSelectUris: (List<Uri>) -> Unit = { uris ->
        uris.forEach { uri ->
            if (uri !in selectedUris) onToggleSelection(uri)
        }
    },
) {
    val isSelectionMode = selectedUris.isNotEmpty()
    val imageLoader = rememberSweepImageLoader()
    val sortedItems = remember(items, selectedSort) {
        sortAlbumMedia(items, selectedSort)
    }
    val gridState = rememberLazyGridState()
    val uriById = remember(sortedItems) {
        HashMap<Long, Uri>(sortedItems.size).apply {
            sortedItems.forEach { put(it.id, it.contentUri) }
        }
    }
    val title = "${Formatters.monthDisplayName(month)} $year"

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
                        onSelectAll = { onSelectAll(sortedItems.map { it.contentUri }) },
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
                                    .testTag("month_detail_back_button"),
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
                                    text = title,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    text = "${sortedItems.size} items",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        actions = {
                            SortMenuButton(
                                selectedSort = selectedSort,
                                onSortChange = onSortChange,
                                buttonTestTag = "month_sort_button",
                                menuTestTag = "month_sort_menu",
                                optionTagPrefix = "month_sort_option",
                            )
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
        if (sortedItems.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Text(
                    text = "No media remaining in $title",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    .testTag("month_detail_grid"),
            ) {
                items(
                    items = sortedItems,
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
                                onOpenViewer(sortedItems, item)
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
