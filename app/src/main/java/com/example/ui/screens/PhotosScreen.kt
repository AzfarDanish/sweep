package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.MediaFilter
import com.example.model.MediaItem
import com.example.model.PermissionAccessState
import com.example.ui.components.HeaderTrashButton
import com.example.ui.components.MediaThumbnailTile
import com.example.ui.components.PartialAccessBanner
import com.example.ui.components.PermissionGateView
import com.example.ui.components.SelectionTopBar
import com.example.ui.components.dragSelectGrid
import com.example.ui.components.rememberSweepImageLoader
import com.example.ui.state.MediaLibraryUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    uiState: MediaLibraryUiState,
    gridColumns: Int,
    onFilterChange: (MediaFilter) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenViewer: (List<MediaItem>, MediaItem) -> Unit,
    onToggleSelection: (Uri) -> Unit,
    onSelectAll: (List<Uri>) -> Unit,
    onClearSelection: () -> Unit,
    onTrashSelected: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectUris: (List<Uri>) -> Unit = { uris ->
        uris.forEach { uri ->
            if (uri !in uiState.selectedUris) onToggleSelection(uri)
        }
    },
) {
    val isSelectionMode = uiState.selectedUris.isNotEmpty()
    val imageLoader = rememberSweepImageLoader()
    val gridState = rememberLazyGridState()
    val uriById = remember(uiState.filteredMedia) {
        HashMap<Long, Uri>(uiState.filteredMedia.size).apply {
            uiState.filteredMedia.forEach { put(it.id, it.contentUri) }
        }
    }

    BackHandler(enabled = isSelectionMode || uiState.isSearchActive) {
        when {
            isSelectionMode -> onClearSelection()
            uiState.isSearchActive -> onToggleSearch(false)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                if (isSelectionMode) {
                    SelectionTopBar(
                        selectedCount = uiState.selectedUris.size,
                        onClearSelection = onClearSelection,
                        onSelectAll = {
                            onSelectAll(uiState.filteredMedia.map { it.contentUri })
                        },
                        onPrimaryAction = onTrashSelected,
                    )
                } else {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                        title = {
                            if (uiState.isSearchActive) {
                                BasicTextField(
                                    value = uiState.searchQuery,
                                    onValueChange = onSearchQueryChange,
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onBackground,
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    decorationBox = { innerTextField ->
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            if (uiState.searchQuery.isEmpty()) {
                                                Text(
                                                    text = stringResource(R.string.search_hint),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            innerTextField()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("photos_search_input"),
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.nav_photos),
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                            }
                        },
                        actions = {
                            if (uiState.isSearchActive) {
                                IconButton(
                                    onClick = { onToggleSearch(false) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("close_search_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Close search",
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { onToggleSearch(true) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("photos_search_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = "Search media",
                                    )
                                }
                                HeaderTrashButton(
                                    trashCount = uiState.trashedMedia.size,
                                    onClick = onOpenTrash,
                                    buttonTestTag = "open_trash_button",
                                    badgeTestTag = "photos_trash_badge",
                                )
                            }
                        },
                    )
                }

                if (uiState.permissionState == PermissionAccessState.FULL ||
                    uiState.permissionState == PermissionAccessState.PARTIAL
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        MediaFilter.entries.forEach { filter ->
                            val selected = uiState.filter == filter
                            val label = when (filter) {
                                MediaFilter.ALL -> stringResource(R.string.filter_all)
                                MediaFilter.PHOTOS -> stringResource(R.string.filter_photos)
                                MediaFilter.VIDEOS -> stringResource(R.string.filter_videos)
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { onFilterChange(filter) },
                                label = { Text(text = label, style = MaterialTheme.typography.labelLarge) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                border = null,
                                modifier = Modifier.testTag("filter_${filter.name.lowercase()}"),
                            )
                        }
                    }
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.permissionState == PermissionAccessState.DENIED ||
                    uiState.permissionState == PermissionAccessState.UNKNOWN -> {
                    PermissionGateView(
                        onGrantClick = onRequestPermission,
                        onOpenSettingsClick = onOpenSettings,
                    )
                }

                uiState.isLoading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    }
                }

                uiState.filteredMedia.isEmpty() -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    ) {
                        if (uiState.permissionState == PermissionAccessState.PARTIAL) {
                            PartialAccessBanner(onManageAccess = onRequestPermission)
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        Text(
                            text = stringResource(R.string.empty_photos),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(gridColumns),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .dragSelectGrid(
                                gridState = gridState,
                                isSelectionMode = isSelectionMode,
                                resolveUriForKey = { key -> (key as? Long)?.let(uriById::get) },
                                onSelectUris = onSelectUris,
                            )
                            .testTag("photos_grid"),
                    ) {
                        if (uiState.permissionState == PermissionAccessState.PARTIAL) {
                            item(
                                key = "partial_banner",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                PartialAccessBanner(onManageAccess = onRequestPermission)
                            }
                        }

                        uiState.dateGroups.forEach { group ->
                            item(
                                key = "header_${group.key}",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                                ) {
                                    Text(
                                        text = group.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Text(
                                        text = "${group.items.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            items(
                                items = group.items,
                                key = { it.id },
                            ) { item ->
                                val isSelected = item.contentUri in uiState.selectedUris
                                MediaThumbnailTile(
                                    item = item,
                                    imageLoader = imageLoader,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (isSelectionMode) {
                                            onToggleSelection(item.contentUri)
                                        } else {
                                            onOpenViewer(uiState.filteredMedia, item)
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
        }
    }
}
