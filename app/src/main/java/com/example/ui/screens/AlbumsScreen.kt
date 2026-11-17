package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.model.AlbumCollection
import com.example.model.AlbumKind
import com.example.model.PermissionAccessState
import com.example.ui.components.HeaderTrashButton
import com.example.ui.components.PermissionGateView
import com.example.ui.components.rememberSweepImageLoader
import com.example.ui.state.MediaLibraryUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    uiState: MediaLibraryUiState,
    albumColumns: Int,
    onOpenAlbum: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenTrash: () -> Unit = {},
) {
    val imageLoader = rememberSweepImageLoader()
    val logicalCollections = uiState.albums.filter { it.kind == AlbumKind.LOGICAL }
    val deviceFolders = uiState.albums.filter { it.kind == AlbumKind.FOLDER }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                    title = {
                        Text(
                            text = stringResource(R.string.nav_albums),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    },
                    actions = {
                        HeaderTrashButton(
                            trashCount = uiState.trashedMedia.size,
                            onClick = onOpenTrash,
                            buttonTestTag = "albums_trash_button",
                            badgeTestTag = "albums_trash_badge",
                        )
                    },
                )
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

                uiState.albums.isEmpty() -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No media collections found",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(albumColumns),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("albums_grid"),
                    ) {
                        if (logicalCollections.isNotEmpty()) {
                            item(
                                key = "section_logical",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                Text(
                                    text = "Collections",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            items(
                                items = logicalCollections,
                                key = { it.id },
                            ) { album ->
                                AlbumTile(
                                    album = album,
                                    imageLoader = imageLoader,
                                    onClick = { onOpenAlbum(album.id) },
                                )
                            }
                        }

                        if (deviceFolders.isNotEmpty()) {
                            item(
                                key = "section_folders",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                Text(
                                    text = "Device Folders",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            items(
                                items = deviceFolders,
                                key = { it.id },
                            ) { album ->
                                AlbumTile(
                                    album = album,
                                    imageLoader = imageLoader,
                                    onClick = { onOpenAlbum(album.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumTile(
    album: AlbumCollection,
    imageLoader: coil.ImageLoader,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val request = ImageRequest.Builder(context)
        .data(album.coverUri)
        .size(420)
        .apply { if (album.coverIsVideo) videoFrameMillis(500L) }
        .build()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag("album_card_${album.id}"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (album.coverIsVideo) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(
                            Color.Black.copy(alpha = 0.65f),
                            RoundedCornerShape(4.dp),
                        )
                        .padding(3.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${album.count}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
