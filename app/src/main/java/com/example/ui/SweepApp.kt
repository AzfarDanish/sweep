package com.example.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.model.AlbumSortOption
import com.example.ui.navigation.AlbumDetailRoute
import com.example.ui.navigation.AlbumsRoute
import com.example.ui.navigation.FilesRoute
import com.example.ui.navigation.MonthDetailRoute
import com.example.ui.navigation.PhotosRoute
import com.example.ui.navigation.TimelineRoute
import com.example.ui.navigation.TrashRoute
import com.example.ui.navigation.ViewerRoute
import com.example.ui.screens.AlbumDetailScreen
import com.example.ui.screens.AlbumsScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.MonthDetailScreen
import com.example.ui.screens.PhotosScreen
import com.example.ui.screens.TimelineScreen
import com.example.ui.screens.TrashBinScreen
import com.example.ui.state.FilesViewModel
import com.example.ui.state.MediaLibraryViewModel
import com.example.util.PermissionHelper

private data class TopLevelDestination(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

@Composable
fun SweepApp(
    widthSizeClass: WindowWidthSizeClass,
    mediaViewModel: MediaLibraryViewModel = viewModel(),
    filesViewModel: FilesViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val mediaUiState by mediaViewModel.uiState.collectAsStateWithLifecycle()
    val viewerItems by mediaViewModel.viewerItems.collectAsStateWithLifecycle()
    val filesUiState by filesViewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        mediaViewModel.refreshPermissionsAndMedia()
        filesViewModel.refreshCurrent()
    }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val confirmed = result.resultCode == Activity.RESULT_OK
        mediaViewModel.onIntentOperationResult(confirmed)
        if (confirmed) {
            filesViewModel.refreshCurrent()
        }
    }

    LaunchedEffect(mediaViewModel) {
        mediaViewModel.pendingOperation.collect { op ->
            intentSenderLauncher.launch(
                IntentSenderRequest.Builder(op.intentSender).build(),
            )
        }
    }

    LaunchedEffect(mediaViewModel) {
        mediaViewModel.snackbarEvents.collect { notification ->
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = notification.message,
                actionLabel = notification.actionLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                when {
                    notification.isUndoAction -> mediaViewModel.triggerUndo()
                    notification.isRedoAction -> mediaViewModel.triggerRedo()
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mediaViewModel.refreshPermissionsAndMedia()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val openAppSettings: () -> Unit = {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        context.startActivity(intent)
    }

    val requestMediaAccess: () -> Unit = {
        permissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
    }

    val destinations = remember {
        listOf(
            TopLevelDestination(
                route = PhotosRoute,
                labelRes = R.string.nav_photos,
                selectedIcon = Icons.Filled.PhotoLibrary,
                unselectedIcon = Icons.Outlined.PhotoLibrary,
                testTag = "nav_tab_photos",
            ),
            TopLevelDestination(
                route = TimelineRoute,
                labelRes = R.string.nav_timeline,
                selectedIcon = Icons.Filled.CalendarMonth,
                unselectedIcon = Icons.Outlined.CalendarMonth,
                testTag = "nav_tab_timeline",
            ),
            TopLevelDestination(
                route = AlbumsRoute,
                labelRes = R.string.nav_albums,
                selectedIcon = Icons.Filled.PhotoAlbum,
                unselectedIcon = Icons.Outlined.PhotoAlbum,
                testTag = "nav_tab_albums",
            ),
            TopLevelDestination(
                route = FilesRoute,
                labelRes = R.string.nav_files,
                selectedIcon = Icons.Filled.Folder,
                unselectedIcon = Icons.Outlined.Folder,
                testTag = "nav_tab_files",
            ),
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isTopLevel = currentDestination == null || destinations.any { dest ->
        currentDestination.hasRoute(dest.route::class)
    }
    val isFilesVisible = currentDestination?.hasRoute(FilesRoute::class) == true

    // File listings only need refreshing when they are actually on screen. Previously every
    // swipe-to-trash triggered an unrelated SAF/media scan while the viewer was open.
    LaunchedEffect(mediaUiState.trashedMedia, isFilesVisible) {
        if (isFilesVisible) {
            filesViewModel.refreshCurrent()
        }
    }

    val useNavigationRail = widthSizeClass != WindowWidthSizeClass.Compact

    val gridColumns = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 3
        WindowWidthSizeClass.Medium -> 4
        else -> 5
    }
    val albumColumns = when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 2
        WindowWidthSizeClass.Medium -> 3
        else -> 4
    }

    val navigateToTopLevel: (Any) -> Unit = { targetRoute ->
        mediaViewModel.clearSelection()
        filesViewModel.clearSelection()
        navController.navigate(targetRoute) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = if (!isTopLevel) Modifier.navigationBarsPadding() else Modifier,
            ) { data ->
                Snackbar(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    action = {
                        data.visuals.actionLabel?.let { label ->
                            TextButton(
                                onClick = { data.performAction() },
                                modifier = Modifier.testTag("snackbar_action_button"),
                            ) {
                                Text(
                                    text = label,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    },
                ) {
                    Text(text = data.visuals.message)
                }
            }
        },
        bottomBar = {
            if (isTopLevel && !useNavigationRail) {
                Box {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp,
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentDestination?.hasRoute(dest.route::class) ?: (dest.route == PhotosRoute)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes),
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onBackground,
                                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (isTopLevel && useNavigationRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    destinations.forEach { dest ->
                        val selected = currentDestination?.hasRoute(dest.route::class) ?: (dest.route == PhotosRoute)
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateToTopLevel(dest.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = stringResource(dest.labelRes),
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(dest.labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onBackground,
                                selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            modifier = Modifier.testTag(dest.testTag),
                        )
                    }
                }
                VerticalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }

            NavHost(
                navController = navController,
                startDestination = PhotosRoute,
                modifier = Modifier.weight(1f),
            ) {
                composable<PhotosRoute> {
                    PhotosScreen(
                        uiState = mediaUiState,
                        gridColumns = gridColumns,
                        onFilterChange = mediaViewModel::setFilter,
                        onToggleSearch = mediaViewModel::toggleSearch,
                        onSearchQueryChange = mediaViewModel::setSearchQuery,
                        onOpenTrash = {
                            mediaViewModel.clearSelection()
                            navController.navigate(TrashRoute)
                        },
                        onOpenViewer = { list, target ->
                            mediaViewModel.openViewerSession(list)
                            navController.navigate(ViewerRoute(initialMediaId = target.id))
                        },
                        onToggleSelection = mediaViewModel::toggleSelection,
                        onSelectAll = mediaViewModel::selectAll,
                        onClearSelection = mediaViewModel::clearSelection,
                        onTrashSelected = {
                            mediaViewModel.requestTrash(mediaUiState.selectedUris.toList())
                        },
                        onRequestPermission = requestMediaAccess,
                        onOpenSettings = openAppSettings,
                        onSelectUris = mediaViewModel::selectUris,
                    )
                }

                composable<TimelineRoute> {
                    TimelineScreen(
                        uiState = mediaUiState,
                        onSelectYear = mediaViewModel::selectYear,
                        onOpenMonth = { year, month ->
                            mediaViewModel.clearSelection()
                            navController.navigate(MonthDetailRoute(year = year, month = month))
                        },
                        onRequestPermission = requestMediaAccess,
                        onOpenSettings = openAppSettings,
                        onOpenTrash = {
                            mediaViewModel.clearSelection()
                            navController.navigate(TrashRoute)
                        },
                    )
                }

                composable<MonthDetailRoute> { entry ->
                    val route = entry.toRoute<MonthDetailRoute>()
                    val monthKey = "${route.year}_${route.month}"
                    val selectedSort = mediaUiState.monthSortOptions[monthKey]
                        ?: AlbumSortOption.NEWEST_FIRST
                    val monthItems = remember(
                        mediaUiState.allMedia,
                        route.year,
                        route.month,
                        selectedSort,
                    ) {
                        mediaViewModel.getMonthItems(route.year, route.month)
                    }
                    MonthDetailScreen(
                        year = route.year,
                        month = route.month,
                        items = monthItems,
                        selectedUris = mediaUiState.selectedUris,
                        gridColumns = gridColumns,
                        selectedSort = selectedSort,
                        onSortChange = { option ->
                            mediaViewModel.setMonthSortOption(route.year, route.month, option)
                        },
                        onBack = {
                            mediaViewModel.clearSelection()
                            navController.popBackStack()
                        },
                        onOpenViewer = { list, target ->
                            mediaViewModel.openViewerSession(list)
                            navController.navigate(ViewerRoute(initialMediaId = target.id))
                        },
                        onToggleSelection = mediaViewModel::toggleSelection,
                        onSelectAll = mediaViewModel::selectAll,
                        onClearSelection = mediaViewModel::clearSelection,
                        onTrashSelected = {
                            mediaViewModel.requestTrash(mediaUiState.selectedUris.toList())
                        },
                        onSelectUris = mediaViewModel::selectUris,
                    )
                }

                composable<AlbumsRoute> {
                    AlbumsScreen(
                        uiState = mediaUiState,
                        albumColumns = albumColumns,
                        onOpenAlbum = { albumId ->
                            mediaViewModel.clearSelection()
                            navController.navigate(AlbumDetailRoute(albumId = albumId))
                        },
                        onRequestPermission = requestMediaAccess,
                        onOpenSettings = openAppSettings,
                        onOpenTrash = {
                            mediaViewModel.clearSelection()
                            navController.navigate(TrashRoute)
                        },
                    )
                }

                composable<AlbumDetailRoute> { entry ->
                    val route = entry.toRoute<AlbumDetailRoute>()
                    val album = remember(mediaUiState.albums, route.albumId) {
                        mediaUiState.albums.firstOrNull { it.id == route.albumId }
                    }
                    val selectedSort = mediaUiState.albumSortOptions[route.albumId]
                        ?: AlbumSortOption.NEWEST_FIRST
                    AlbumDetailScreen(
                        album = album,
                        selectedUris = mediaUiState.selectedUris,
                        gridColumns = gridColumns,
                        selectedSort = selectedSort,
                        onSortChange = { option ->
                            mediaViewModel.setAlbumSortOption(route.albumId, option)
                        },
                        onBack = {
                            mediaViewModel.clearSelection()
                            navController.popBackStack()
                        },
                        onOpenViewer = { list, target ->
                            mediaViewModel.openViewerSession(list)
                            navController.navigate(ViewerRoute(initialMediaId = target.id))
                        },
                        onToggleSelection = mediaViewModel::toggleSelection,
                        onSelectAll = mediaViewModel::selectAll,
                        onClearSelection = mediaViewModel::clearSelection,
                        onTrashSelected = {
                            mediaViewModel.requestTrash(mediaUiState.selectedUris.toList())
                        },
                        onSelectUris = mediaViewModel::selectUris,
                    )
                }

                composable<FilesRoute> {
                    FilesScreen(
                        uiState = filesUiState,
                        onOpenCategory = filesViewModel::openCategory,
                        onAddSafFolder = filesViewModel::addSafFolder,
                        onRemoveSafFolder = filesViewModel::removeSafFolder,
                        onOpenSafRoot = filesViewModel::openSafRoot,
                        onOpenSubdirectory = filesViewModel::openSubdirectory,
                        onNavigateUp = filesViewModel::navigateUp,
                        onSortChange = filesViewModel::setSort,
                        onToggleSelection = filesViewModel::toggleSelection,
                        onSelectAll = filesViewModel::selectAllVisible,
                        onClearSelection = filesViewModel::clearSelection,
                        onOpenMediaViewer = { list, target ->
                            mediaViewModel.openViewerSession(list)
                            navController.navigate(ViewerRoute(initialMediaId = target.id))
                        },
                        onTrashMediaStoreUris = { uris ->
                            filesViewModel.clearSelection()
                            mediaViewModel.requestTrash(uris)
                        },
                        onConsumeStatusMessage = filesViewModel::consumeStatusMessage,
                    )
                }

                composable<TrashRoute> {
                    TrashBinScreen(
                        trashedItems = mediaUiState.trashedMedia,
                        selectedUris = mediaUiState.selectedUris,
                        gridColumns = gridColumns,
                        onBack = {
                            mediaViewModel.clearSelection()
                            navController.popBackStack()
                        },
                        onOpenTrashPreview = { list, target ->
                            mediaViewModel.openViewerSession(list)
                            navController.navigate(
                                ViewerRoute(
                                    initialMediaId = target.id,
                                    isTrashPreview = true,
                                ),
                            )
                        },
                        onToggleSelection = mediaViewModel::toggleSelection,
                        onSelectAll = mediaViewModel::selectAll,
                        onClearSelection = mediaViewModel::clearSelection,
                        onRestoreUris = { uris ->
                            mediaViewModel.requestRestore(uris)
                        },
                        onPermanentDeleteUris = { uris ->
                            mediaViewModel.requestPermanentDelete(uris)
                        },
                        onSelectUris = mediaViewModel::selectUris,
                    )
                }

                composable<ViewerRoute> { entry ->
                    val route = entry.toRoute<ViewerRoute>()

                    // Trash actions are applied to the viewer list immediately and reconciled in
                    // the background; refresh the gallery once when the viewer leaves.
                    DisposableEffect(Unit) {
                        onDispose { mediaViewModel.refreshAfterViewerSession() }
                    }

                    MediaViewerScreen(
                        items = viewerItems,
                        initialMediaId = route.initialMediaId,
                        isTrashPreview = route.isTrashPreview,
                        trashCount = mediaUiState.trashedMedia.size,
                        undoRedoState = mediaUiState.undoRedoState,
                        focusMediaIdFlow = mediaViewModel.viewerFocusMediaId,
                        onBack = { navController.popBackStack() },
                        onOpenTrash = {
                            mediaViewModel.clearSelection()
                            if (route.isTrashPreview) {
                                navController.popBackStack()
                            } else {
                                navController.navigate(TrashRoute) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onTrashItem = { item ->
                            mediaViewModel.requestTrash(listOf(item.contentUri), fromViewer = true)
                        },
                        onRestoreItem = { item ->
                            mediaViewModel.requestRestore(listOf(item.contentUri), fromViewer = true)
                        },
                        onPermanentDeleteItem = { item ->
                            mediaViewModel.requestPermanentDelete(
                                listOf(item.contentUri),
                                fromViewer = true,
                            )
                        },
                        onUndo = mediaViewModel::triggerUndo,
                        onRedo = mediaViewModel::triggerRedo,
                        albums = mediaUiState.albums,
                        onMoveToAlbum = mediaViewModel::requestMoveToAlbum,
                    )
                }
            }
        }
    }
}
