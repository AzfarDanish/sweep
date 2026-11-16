package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.R
import com.example.model.FileCategory
import com.example.model.FileEntry
import com.example.model.FileSortOption
import com.example.model.MediaItem
import com.example.model.SortOrder
import com.example.ui.components.SelectionTopBar
import com.example.ui.components.rememberSweepImageLoader
import com.example.ui.state.FilesLocation
import com.example.ui.state.FilesUiState
import com.example.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    uiState: FilesUiState,
    onOpenCategory: (FileCategory) -> Unit,
    onAddSafFolder: (Uri) -> Unit,
    onRemoveSafFolder: (Uri) -> Unit,
    onOpenSafRoot: (com.example.data.SafFolderRoot) -> Unit,
    onOpenSubdirectory: (FileEntry) -> Unit,
    onNavigateUp: () -> Boolean,
    onSortChange: (FileSortOption, SortOrder) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onOpenMediaViewer: (List<MediaItem>, MediaItem) -> Unit,
    onTrashMediaStoreUris: (List<Uri>) -> Unit,
    onConsumeStatusMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageLoader = rememberSweepImageLoader()
    val isSelectionMode = uiState.selectedIds.isNotEmpty()
    val isInSubLocation = uiState.location !is FilesLocation.Root
    var showSortMenu by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            onAddSafFolder(uri)
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (msg != null) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            onConsumeStatusMessage()
        }
    }

    BackHandler(enabled = isSelectionMode || isInSubLocation) {
        if (isSelectionMode) {
            onClearSelection()
        } else {
            onNavigateUp()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                if (isSelectionMode) {
                    SelectionTopBar(
                        selectedCount = uiState.selectedIds.size,
                        onClearSelection = onClearSelection,
                        onSelectAll = onSelectAll,
                        onPrimaryAction = {
                            val selected = uiState.entries.filter { it.id in uiState.selectedIds }
                            val trashableMedia = selected.filter {
                                it.isMediaStoreBacked && (it.isImage || it.isVideo)
                            }
                            val nonTrashableCount = selected.size - trashableMedia.size
                            if (trashableMedia.isNotEmpty()) {
                                onTrashMediaStoreUris(trashableMedia.map { it.uri })
                            }
                            if (nonTrashableCount > 0) {
                                Toast.makeText(
                                    context,
                                    "Only device photos and videos can be moved to Trash Bin",
                                    Toast.LENGTH_SHORT,
                                ).show()
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
                            if (isInSubLocation) {
                                IconButton(
                                    onClick = { onNavigateUp() },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("files_back_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                        contentDescription = "Back",
                                    )
                                }
                            }
                        },
                        title = {
                            when (val loc = uiState.location) {
                                FilesLocation.Root -> Text(
                                    text = stringResource(R.string.nav_files),
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                                is FilesLocation.Category -> Text(
                                    text = loc.category.label,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                is FilesLocation.SafDirectory -> Column {
                                    Text(
                                        text = loc.stack.lastOrNull()?.name ?: "Folder",
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (loc.stack.size > 1) {
                                        Text(
                                            text = loc.stack.joinToString(" / ") { it.name },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            if (isInSubLocation) {
                                Box {
                                    IconButton(
                                        onClick = { showSortMenu = true },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("files_sort_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.Sort,
                                            contentDescription = stringResource(R.string.sort_by),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false },
                                    ) {
                                        FileSortOption.entries.forEach { option ->
                                            val isCurrent = uiState.sortOption == option
                                            val arrow = if (isCurrent) {
                                                if (uiState.sortOrder == SortOrder.ASCENDING) " ↑" else " ↓"
                                            } else ""
                                            DropdownMenuItem(
                                                text = { Text("${option.label}$arrow") },
                                                onClick = {
                                                    showSortMenu = false
                                                    val nextOrder = if (isCurrent) {
                                                        if (uiState.sortOrder == SortOrder.ASCENDING) {
                                                            SortOrder.DESCENDING
                                                        } else {
                                                            SortOrder.ASCENDING
                                                        }
                                                    } else {
                                                        if (option == FileSortOption.NAME) {
                                                            SortOrder.ASCENDING
                                                        } else {
                                                            SortOrder.DESCENDING
                                                        }
                                                    }
                                                    onSortChange(option, nextOrder)
                                                },
                                            )
                                        }
                                    }
                                }
                            } else {
                                IconButton(
                                    onClick = { folderPickerLauncher.launch(null) },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("add_saf_folder_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CreateNewFolder,
                                        contentDescription = stringResource(R.string.add_folder),
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
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

                uiState.location is FilesLocation.Root -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("files_root_list"),
                    ) {
                        item(key = "header_categories") {
                            Text(
                                text = "Categories",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }

                        items(
                            items = FileCategory.entries,
                            key = { it.name },
                        ) { category ->
                            val count = uiState.categoryCounts[category] ?: 0
                            CategoryRow(
                                category = category,
                                count = count,
                                onClick = { onOpenCategory(category) },
                            )
                        }

                        item(key = "header_folders") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, bottom = 4.dp),
                            ) {
                                Text(
                                    text = "Internal Storage Folders",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (uiState.authorizedFolders.isEmpty()) {
                            item(key = "add_folder_prompt") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(16.dp),
                                ) {
                                    Text(
                                        text = "Browse device folders",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Grant access to any folder on internal storage using Android's folder picker.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = { folderPickerLauncher.launch(null) },
                                        modifier = Modifier.testTag("files_grant_folder_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CreateNewFolder,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = stringResource(R.string.add_folder))
                                    }
                                }
                            }
                        } else {
                            items(
                                items = uiState.authorizedFolders,
                                key = { it.uri.toString() },
                            ) { folder ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { onOpenSafRoot(folder) }
                                        .minimumInteractiveComponentSize()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Folder,
                                        contentDescription = null,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = folder.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(
                                        onClick = { onRemoveSafFolder(folder.uri) },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "Remove folder access",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                uiState.entries.isEmpty() -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    ) {
                        Text(
                            text = "This folder is empty",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("files_entries_list"),
                    ) {
                        items(
                            items = uiState.entries,
                            key = { it.id },
                        ) { entry ->
                            val isSelected = entry.id in uiState.selectedIds
                            FileEntryRow(
                                entry = entry,
                                imageLoader = imageLoader,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        if (!entry.isDirectory) onToggleSelection(entry.id)
                                    } else if (entry.isDirectory) {
                                        onOpenSubdirectory(entry)
                                    } else if (entry.isImage || entry.isVideo) {
                                        val mediaEntries = uiState.entries
                                            .filter { it.isImage || it.isVideo }
                                            .map { it.toViewerMediaItem() }
                                        val target = entry.toViewerMediaItem()
                                        onOpenMediaViewer(mediaEntries, target)
                                    } else {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(entry.uri, entry.mimeType.ifBlank { "*/*" })
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching {
                                            context.startActivity(Intent.createChooser(intent, entry.name))
                                        }.onFailure {
                                            Toast.makeText(
                                                context,
                                                "No app installed to open ${entry.name}",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                },
                                onLongClick = {
                                    if (!entry.isDirectory) onToggleSelection(entry.id)
                                },
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(start = 72.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: FileCategory,
    count: Int,
    onClick: () -> Unit,
) {
    val icon = when (category) {
        FileCategory.IMAGES -> Icons.Outlined.Image
        FileCategory.VIDEOS -> Icons.Outlined.VideoFile
        FileCategory.AUDIO -> Icons.Outlined.AudioFile
        FileCategory.DOCUMENTS -> Icons.Outlined.Description
        FileCategory.DOWNLOADS -> Icons.Outlined.Download
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .testTag("files_category_${category.name.lowercase()}"),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = category.label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileEntryRow(
    entry: FileEntry,
    imageLoader: coil.ImageLoader,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.background,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .minimumInteractiveComponentSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("file_row_${entry.name}"),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (entry.isImage || entry.isVideo) {
                val request = ImageRequest.Builder(context)
                    .data(entry.uri)
                    .size(160)
                    .apply { if (entry.isVideo) videoFrameMillis(500L) }
                    .build()
                AsyncImage(
                    model = request,
                    imageLoader = imageLoader,
                    contentDescription = entry.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val icon = when {
                    entry.isDirectory -> Icons.Outlined.Folder
                    entry.mimeType.startsWith("audio/") -> Icons.Outlined.AudioFile
                    entry.mimeType.startsWith("text/") || entry.mimeType.contains("pdf") ->
                        Icons.Outlined.Description
                    else -> Icons.AutoMirrored.Outlined.InsertDriveFile
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            val subtitle = if (entry.isDirectory) {
                "Folder · ${Formatters.formatShortDate(entry.lastModifiedMillis)}"
            } else {
                "${Formatters.formatFileSize(entry.sizeBytes)} · ${Formatters.formatShortDate(entry.lastModifiedMillis)}"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isSelectionMode && !entry.isDirectory) {
            Icon(
                imageVector = if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun FileEntry.toViewerMediaItem(): MediaItem {
    val stableId = mediaStoreId ?: id.hashCode().toLong()
    return MediaItem(
        id = stableId,
        contentUri = uri,
        displayName = name,
        mimeType = mimeType,
        dateTakenMillis = lastModifiedMillis,
        dateModifiedMillis = lastModifiedMillis,
        sizeBytes = sizeBytes,
        durationMillis = durationMillis,
        width = 0,
        height = 0,
        bucketId = 0L,
        bucketName = relativePath.ifBlank { "Files" },
        relativePath = relativePath,
        isVideo = isVideo,
        isTrashed = false,
    )
}
