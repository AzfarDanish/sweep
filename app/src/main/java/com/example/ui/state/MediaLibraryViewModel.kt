package com.example.ui.state

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MediaStoreRepository
import com.example.data.UndoRedoManager
import com.example.data.UndoRedoUiState
import com.example.model.AlbumCollection
import com.example.model.AlbumKind
import com.example.model.MediaDateGroup
import com.example.model.MediaFilter
import com.example.model.MediaItem
import com.example.model.PermissionAccessState
import com.example.model.TimelineMonthGroup
import com.example.model.TimelineYearGroup
import com.example.model.TrashAction
import com.example.util.Formatters
import com.example.util.PermissionHelper
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class PendingOperationType {
    TRASH,
    RESTORE,
    PERMANENT_DELETE,
    UNDO,
    REDO,
}

data class PendingIntentOperation(
    val intentSender: IntentSender,
    val type: PendingOperationType,
    val uris: List<Uri>,
    val viewerRemoval: Boolean = false,
)

data class SnackbarNotification(
    val message: String,
    val actionLabel: String? = null,
    val isUndoAction: Boolean = false,
    val isRedoAction: Boolean = false,
)

data class MediaLibraryUiState(
    val permissionState: PermissionAccessState = PermissionAccessState.UNKNOWN,
    val isLoading: Boolean = false,
    val allMedia: List<MediaItem> = emptyList(),
    val filteredMedia: List<MediaItem> = emptyList(),
    val dateGroups: List<MediaDateGroup> = emptyList(),
    val timelineYears: List<TimelineYearGroup> = emptyList(),
    val selectedYear: Int? = null,
    val albums: List<AlbumCollection> = emptyList(),
    val trashedMedia: List<MediaItem> = emptyList(),
    val filter: MediaFilter = MediaFilter.ALL,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val selectedUris: Set<Uri> = emptySet(),
    val undoRedoState: UndoRedoUiState = UndoRedoUiState(),
)

@OptIn(FlowPreview::class)
class MediaLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaStoreRepository(application)
    private val undoRedoManager = UndoRedoManager()

    private val _permissionState = MutableStateFlow(PermissionHelper.evaluateAccessState(application))
    private val _isLoading = MutableStateFlow(false)
    private val _allMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _trashedMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _filter = MutableStateFlow(MediaFilter.ALL)
    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)
    private val _selectedUris = MutableStateFlow<Set<Uri>>(emptySet())
    private val _selectedYear = MutableStateFlow<Int?>(null)

    private val _viewerItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val viewerItems: StateFlow<List<MediaItem>> = _viewerItems.asStateFlow()

    private val _viewerFocusMediaId = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    val viewerFocusMediaId: SharedFlow<Long> = _viewerFocusMediaId.asSharedFlow()

    private val _pendingOperation = MutableSharedFlow<PendingIntentOperation>(extraBufferCapacity = 4)
    val pendingOperation: SharedFlow<PendingIntentOperation> = _pendingOperation.asSharedFlow()

    private val _snackbarEvents = MutableSharedFlow<SnackbarNotification>(extraBufferCapacity = 4)
    val snackbarEvents: SharedFlow<SnackbarNotification> = _snackbarEvents.asSharedFlow()

    private var activePendingOp: PendingIntentOperation? = null
    private var mediaScanJob: Job? = null
    private val viewerRemovedHistory = LinkedHashMap<Uri, Pair<Int, MediaItem>>()
    private val pendingViewerTrashUris = HashSet<Uri>()
    private val trashMutex = Mutex()

    private val baseFlow = combine(
        _permissionState,
        _isLoading,
        _allMedia,
        _trashedMedia,
        _filter,
    ) { permission, loading, all, trashed, filter ->
        BaseState(permission, loading, all, trashed, filter)
    }

    private val queryFlow = combine(
        _searchQuery,
        _isSearchActive,
        _selectedUris,
        _selectedYear,
        undoRedoManager.state,
    ) { query, searchActive, selected, year, undoState ->
        QueryState(query, searchActive, selected, year, undoState)
    }

    val uiState: StateFlow<MediaLibraryUiState> = combine(baseFlow, queryFlow) { base, q ->
        val filtered = applyFilters(base.allMedia, base.filter, q.searchQuery)
        val dateGroups = buildDateGroups(filtered)
        val years = buildTimelineYears(base.allMedia)
        val effectiveYear = q.selectedYear?.takeIf { target -> years.any { it.year == target } }
            ?: years.firstOrNull()?.year
        val albums = buildAlbums(base.allMedia)

        MediaLibraryUiState(
            permissionState = base.permissionState,
            isLoading = base.isLoading,
            allMedia = base.allMedia,
            filteredMedia = filtered,
            dateGroups = dateGroups,
            timelineYears = years,
            selectedYear = effectiveYear,
            albums = albums,
            trashedMedia = base.trashedMedia,
            filter = base.filter,
            searchQuery = q.searchQuery,
            isSearchActive = q.isSearchActive,
            selectedUris = q.selectedUris,
            undoRedoState = q.undoState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MediaLibraryUiState(),
    )

    init {
        refreshPermissionsAndMedia()
        viewModelScope.launch {
            repository.observeMediaChanges()
                .debounce(350L)
                .collect {
                    if (_permissionState.value == PermissionAccessState.FULL ||
                        _permissionState.value == PermissionAccessState.PARTIAL
                    ) {
                        loadMediaInternal(showSpinner = false)
                    }
                }
        }
    }

    fun refreshPermissionsAndMedia() {
        val state = PermissionHelper.evaluateAccessState(getApplication())
        _permissionState.value = state
        if (state == PermissionAccessState.FULL || state == PermissionAccessState.PARTIAL) {
            loadMediaInternal(showSpinner = _allMedia.value.isEmpty())
        } else {
            _allMedia.value = emptyList()
            _trashedMedia.value = emptyList()
        }
    }

    fun refreshMedia() {
        val state = PermissionHelper.evaluateAccessState(getApplication())
        _permissionState.value = state
        if (state == PermissionAccessState.FULL || state == PermissionAccessState.PARTIAL) {
            loadMediaInternal(showSpinner = false)
        }
    }

    private fun loadMediaInternal(showSpinner: Boolean) {
        mediaScanJob?.cancel()
        mediaScanJob = viewModelScope.launch {
            if (showSpinner) _isLoading.value = true
            val scan = repository.scanAllMedia()
            _allMedia.value = scan.activeMedia
            _trashedMedia.value = scan.trashedMedia
            _isLoading.value = false
        }
    }

    fun setFilter(filter: MediaFilter) {
        _filter.value = filter
    }

    fun toggleSearch(active: Boolean) {
        _isSearchActive.value = active
        if (!active) _searchQuery.value = ""
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectYear(year: Int) {
        _selectedYear.value = year
    }

    fun toggleSelection(uri: Uri) {
        _selectedUris.update { current ->
            if (uri in current) current - uri else current + uri
        }
    }

    fun selectAll(uris: List<Uri>) {
        _selectedUris.value = uris.toSet()
    }

    fun clearSelection() {
        _selectedUris.value = emptySet()
    }

    fun openViewerSession(items: List<MediaItem>) {
        viewerRemovedHistory.clear()
        _viewerItems.value = items
    }

    fun getMonthItems(year: Int, month: Int): List<MediaItem> {
        return _allMedia.value.filter { item ->
            val (y, m) = Formatters.yearMonthPair(item.effectiveTimestampMillis)
            y == year && m == month
        }
    }

    fun getAlbumById(albumId: String): AlbumCollection? {
        return buildAlbums(_allMedia.value).firstOrNull { it.id == albumId }
    }

    fun requestTrash(uris: List<Uri>, fromViewer: Boolean = false) {
        if (uris.isEmpty()) return
        val targetUris = if (fromViewer) {
            uris.filter { pendingViewerTrashUris.add(it) }
        } else {
            uris.distinct()
        }
        if (targetUris.isEmpty()) return

        if (fromViewer) {
            val currentViewerList = _viewerItems.value
            targetUris.forEach { uri ->
                val idx = currentViewerList.indexOfFirst { it.contentUri == uri }
                if (idx >= 0) {
                    viewerRemovedHistory[uri] = idx to currentViewerList[idx]
                }
            }
            val targetSet = targetUris.toSet()
            _viewerItems.update { list -> list.filterNot { it.contentUri in targetSet } }
            _selectedUris.value = emptySet()
        }

        viewModelScope.launch {
            trashMutex.withLock {
                try {
                    val moved = repository.moveToTrashImmediate(targetUris)
                    if (moved.isEmpty()) {
                        loadMediaInternal(showSpinner = false)
                        return@launch
                    }

                    undoRedoManager.recordNewAction(TrashAction.MoveToTrash(moved))
                    if (!fromViewer) {
                        _selectedUris.value = emptySet()
                    }
                    loadMediaInternal(showSpinner = false)
                } finally {
                    if (fromViewer) {
                        pendingViewerTrashUris.removeAll(targetUris.toSet())
                    }
                }
            }
        }
    }

    fun requestRestore(uris: List<Uri>, fromViewer: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val targetSet = uris.toSet()
            val matchingItems = _trashedMedia.value.filter { it.contentUri in targetSet }
            if (matchingItems.isEmpty()) {
                _snackbarEvents.tryEmit(SnackbarNotification("Item no longer exists in trash"))
                loadMediaInternal(showSpinner = false)
                return@launch
            }

            val (restoredSilently, needsOsAuth) = repository.restoreImmediateOrNeedsOsAuth(matchingItems)

            if (restoredSilently.isNotEmpty()) {
                undoRedoManager.recordNewAction(TrashAction.RestoreFromTrash(restoredSilently))
                if (fromViewer) {
                    _viewerItems.update { list -> list.filterNot { it.contentUri in restoredSilently } }
                }
                _selectedUris.value = emptySet()
                loadMediaInternal(showSpinner = false)
                _snackbarEvents.tryEmit(
                    SnackbarNotification(
                        message = if (restoredSilently.size == 1) {
                            "Restored from trash"
                        } else {
                            "Restored ${restoredSilently.size} items"
                        },
                        actionLabel = "Undo",
                        isUndoAction = true,
                    ),
                )
            }

            if (needsOsAuth.isNotEmpty()) {
                val sender = repository.buildTrashIntentSender(needsOsAuth, trash = false)
                if (sender != null) {
                    val op = PendingIntentOperation(
                        intentSender = sender,
                        type = PendingOperationType.RESTORE,
                        uris = needsOsAuth,
                        viewerRemoval = fromViewer,
                    )
                    activePendingOp = op
                    _pendingOperation.emit(op)
                }
            }
        }
    }

    fun requestPermanentDelete(uris: List<Uri>, fromViewer: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val valid = repository.filterExistingUris(uris)
            if (valid.isEmpty()) {
                repository.removeFromAppTrashStore(uris)
                if (fromViewer) {
                    _viewerItems.update { list -> list.filterNot { it.contentUri in uris } }
                }
                _selectedUris.value = emptySet()
                loadMediaInternal(showSpinner = false)
                _snackbarEvents.tryEmit(SnackbarNotification("File no longer exists on device"))
                return@launch
            }
            val sender = repository.buildDeleteIntentSender(valid)
            if (sender != null) {
                val op = PendingIntentOperation(
                    intentSender = sender,
                    type = PendingOperationType.PERMANENT_DELETE,
                    uris = valid,
                    viewerRemoval = fromViewer,
                )
                activePendingOp = op
                _pendingOperation.emit(op)
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val deleted = repository.deleteDirectlyLegacy(valid)
                if (deleted) {
                    repository.removeFromAppTrashStore(valid)
                    if (fromViewer) {
                        _viewerItems.update { list -> list.filterNot { it.contentUri in valid } }
                    }
                    _selectedUris.value = emptySet()
                    loadMediaInternal(showSpinner = false)
                    _snackbarEvents.tryEmit(SnackbarNotification("Deleted permanently"))
                } else {
                    _snackbarEvents.tryEmit(SnackbarNotification("Could not delete selected items"))
                }
            }
        }
    }

    fun triggerUndo() {
        val action = undoRedoManager.peekUndo() ?: return
        viewModelScope.launch {
            val valid = repository.filterExistingUris(action.uris)
            if (valid.isEmpty()) {
                undoRedoManager.discardTopUndo()
                _snackbarEvents.tryEmit(SnackbarNotification("File is no longer available to undo"))
                return@launch
            }

            when (action) {
                is TrashAction.MoveToTrash -> {
                    val trashedLookup = _trashedMedia.value.associateBy { it.contentUri }
                    val itemsToRestore = valid.map { uri ->
                        trashedLookup[uri] ?: viewerRemovedHistory[uri]?.second?.copy(isTrashed = true)
                    }.filterNotNull()

                    val (restoredSilently, needsOsAuth) =
                        repository.restoreImmediateOrNeedsOsAuth(itemsToRestore)

                    if (restoredSilently.isNotEmpty()) {
                        undoRedoManager.commitUndoExecuted()
                        restoreItemsIntoViewerIfPresent(restoredSilently)
                        _selectedUris.value = emptySet()
                        loadMediaInternal(showSpinner = false)
                        _snackbarEvents.tryEmit(
                            SnackbarNotification(
                                message = "Restored to gallery",
                                actionLabel = "Redo",
                                isRedoAction = true,
                            ),
                        )
                    }

                    if (needsOsAuth.isNotEmpty()) {
                        val sender = repository.buildTrashIntentSender(needsOsAuth, trash = false)
                        if (sender != null) {
                            val op = PendingIntentOperation(
                                intentSender = sender,
                                type = PendingOperationType.UNDO,
                                uris = needsOsAuth,
                            )
                            activePendingOp = op
                            _pendingOperation.emit(op)
                        }
                    }
                }

                is TrashAction.RestoreFromTrash -> {
                    val moved = repository.moveToTrashImmediate(valid)
                    if (moved.isNotEmpty()) {
                        undoRedoManager.commitUndoExecuted()
                        _selectedUris.value = emptySet()
                        loadMediaInternal(showSpinner = false)
                        _snackbarEvents.tryEmit(
                            SnackbarNotification(
                                message = "Moved back to trash",
                                actionLabel = "Redo",
                                isRedoAction = true,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun triggerRedo() {
        val action = undoRedoManager.peekRedo() ?: return
        viewModelScope.launch {
            val valid = repository.filterExistingUris(action.uris)
            if (valid.isEmpty()) {
                undoRedoManager.discardTopRedo()
                _snackbarEvents.tryEmit(SnackbarNotification("File is no longer available to redo"))
                return@launch
            }

            when (action) {
                is TrashAction.MoveToTrash -> {
                    val moved = repository.moveToTrashImmediate(valid)
                    if (moved.isNotEmpty()) {
                        undoRedoManager.commitRedoExecuted()
                        _viewerItems.update { list -> list.filterNot { it.contentUri in moved } }
                        _selectedUris.value = emptySet()
                        loadMediaInternal(showSpinner = false)
                    }
                }

                is TrashAction.RestoreFromTrash -> {
                    val trashedLookup = _trashedMedia.value.associateBy { it.contentUri }
                    val itemsToRestore = valid.mapNotNull { trashedLookup[it] }
                    val (restoredSilently, needsOsAuth) =
                        repository.restoreImmediateOrNeedsOsAuth(itemsToRestore)

                    if (restoredSilently.isNotEmpty()) {
                        undoRedoManager.commitRedoExecuted()
                        _selectedUris.value = emptySet()
                        loadMediaInternal(showSpinner = false)
                        _snackbarEvents.tryEmit(
                            SnackbarNotification(
                                message = "Restored from trash",
                                actionLabel = "Undo",
                                isUndoAction = true,
                            ),
                        )
                    }

                    if (needsOsAuth.isNotEmpty()) {
                        val sender = repository.buildTrashIntentSender(needsOsAuth, trash = false)
                        if (sender != null) {
                            val op = PendingIntentOperation(
                                intentSender = sender,
                                type = PendingOperationType.REDO,
                                uris = needsOsAuth,
                            )
                            activePendingOp = op
                            _pendingOperation.emit(op)
                        }
                    }
                }
            }
        }
    }

    private fun restoreItemsIntoViewerIfPresent(restoredUris: List<Uri>) {
        val entriesToRestore = restoredUris
            .mapNotNull { uri -> viewerRemovedHistory.remove(uri) }
            .sortedBy { it.first }
        if (entriesToRestore.isEmpty()) return

        _viewerItems.update { current ->
            val mutable = current.toMutableList()
            for ((originalIdx, item) in entriesToRestore) {
                if (mutable.none { it.contentUri == item.contentUri }) {
                    val insertIndex = originalIdx.coerceIn(0, mutable.size)
                    mutable.add(insertIndex, item.copy(isTrashed = false, isOsTrashed = false))
                }
            }
            mutable
        }
        val firstRestored = entriesToRestore.first().second
        _viewerFocusMediaId.tryEmit(firstRestored.id)
    }

    fun onIntentOperationResult(confirmed: Boolean) {
        val op = activePendingOp ?: return
        activePendingOp = null

        if (!confirmed) {
            return
        }

        when (op.type) {
            PendingOperationType.TRASH -> {
                undoRedoManager.recordNewAction(TrashAction.MoveToTrash(op.uris))
                if (op.viewerRemoval) {
                    _viewerItems.update { list -> list.filterNot { it.contentUri in op.uris } }
                }
                _selectedUris.value = emptySet()
                loadMediaInternal(showSpinner = false)
            }

            PendingOperationType.RESTORE -> {
                undoRedoManager.recordNewAction(TrashAction.RestoreFromTrash(op.uris))
                if (op.viewerRemoval) {
                    _viewerItems.update { list -> list.filterNot { it.contentUri in op.uris } }
                }
                _selectedUris.value = emptySet()
                loadMediaInternal(showSpinner = false)
                _snackbarEvents.tryEmit(
                    SnackbarNotification(
                        message = if (op.uris.size == 1) "Restored from trash" else "Restored ${op.uris.size} items",
                        actionLabel = "Undo",
                        isUndoAction = true,
                    ),
                )
            }

            PendingOperationType.PERMANENT_DELETE -> {
                repository.removeFromAppTrashStore(op.uris)
                op.uris.forEach { viewerRemovedHistory.remove(it) }
                if (op.viewerRemoval) {
                    _viewerItems.update { list -> list.filterNot { it.contentUri in op.uris } }
                }
                _selectedUris.value = emptySet()
                loadMediaInternal(showSpinner = false)
                _snackbarEvents.tryEmit(
                    SnackbarNotification(
                        message = if (op.uris.size == 1) "Deleted permanently" else "Deleted ${op.uris.size} items permanently",
                    ),
                )
            }

            PendingOperationType.UNDO -> {
                undoRedoManager.commitUndoExecuted()
                restoreItemsIntoViewerIfPresent(op.uris)
                _selectedUris.value = emptySet()
                loadMediaInternal(showSpinner = false)
                _snackbarEvents.tryEmit(
                    SnackbarNotification(
                        message = "Action undone",
                        actionLabel = "Redo",
                        isRedoAction = true,
                    ),
                )
            }

            PendingOperationType.REDO -> {
                val redone = undoRedoManager.peekRedo()
                undoRedoManager.commitRedoExecuted()
                if (redone is TrashAction.MoveToTrash) {
                    _viewerItems.update { list -> list.filterNot { it.contentUri in op.uris } }
                }
                _selectedUris.value = emptySet()
                loadMediaInternal(showSpinner = false)
                _snackbarEvents.tryEmit(
                    SnackbarNotification(
                        message = "Action redone",
                        actionLabel = "Undo",
                        isUndoAction = true,
                    ),
                )
            }
        }
    }

    private fun applyFilters(
        items: List<MediaItem>,
        filter: MediaFilter,
        query: String,
    ): List<MediaItem> {
        val byType = when (filter) {
            MediaFilter.ALL -> items
            MediaFilter.PHOTOS -> items.filter { !it.isVideo }
            MediaFilter.VIDEOS -> items.filter { it.isVideo }
        }
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return byType
        val qLower = trimmed.lowercase()
        return byType.filter { item ->
            item.displayName.lowercase().contains(qLower) ||
                item.bucketName.lowercase().contains(qLower) ||
                item.relativePath.lowercase().contains(qLower) ||
                Formatters.formatShortDate(item.effectiveTimestampMillis).lowercase().contains(qLower)
        }
    }

    private fun buildDateGroups(items: List<MediaItem>): List<MediaDateGroup> {
        if (items.isEmpty()) return emptyList()
        return items.groupBy { Formatters.formatDateKey(it.effectiveTimestampMillis) }
            .map { (key, groupItems) ->
                val sampleTs = groupItems.first().effectiveTimestampMillis
                MediaDateGroup(
                    key = key,
                    label = Formatters.formatDateGroupLabel(sampleTs),
                    items = groupItems,
                )
            }
    }

    private fun buildTimelineYears(items: List<MediaItem>): List<TimelineYearGroup> {
        if (items.isEmpty()) return emptyList()
        return items
            .groupBy { Formatters.yearMonthPair(it.effectiveTimestampMillis).first }
            .toSortedMap(compareByDescending { it })
            .map { (year, yearItems) ->
                val months = yearItems
                    .groupBy { Formatters.yearMonthPair(it.effectiveTimestampMillis).second }
                    .toSortedMap(compareByDescending { it })
                    .map { (month, monthItems) ->
                        TimelineMonthGroup(
                            year = year,
                            month = month,
                            monthName = Formatters.monthDisplayName(month),
                            items = monthItems,
                        )
                    }
                TimelineYearGroup(
                    year = year,
                    totalCount = yearItems.size,
                    months = months,
                )
            }
    }

    private fun buildAlbums(items: List<MediaItem>): List<AlbumCollection> {
        if (items.isEmpty()) return emptyList()
        val collections = mutableListOf<AlbumCollection>()

        val photos = items.filter { !it.isVideo }
        if (photos.isNotEmpty()) {
            collections.add(
                AlbumCollection(
                    id = "logical_photos",
                    name = "All Photos",
                    kind = AlbumKind.LOGICAL,
                    coverUri = photos.first().contentUri,
                    coverIsVideo = false,
                    count = photos.size,
                    subtitle = "Logical collection",
                    items = photos,
                ),
            )
        }

        val videos = items.filter { it.isVideo }
        if (videos.isNotEmpty()) {
            collections.add(
                AlbumCollection(
                    id = "logical_videos",
                    name = "All Videos",
                    kind = AlbumKind.LOGICAL,
                    coverUri = videos.first().contentUri,
                    coverIsVideo = true,
                    count = videos.size,
                    subtitle = "Logical collection",
                    items = videos,
                ),
            )
        }

        val folderAlbums = items
            .groupBy { it.bucketId }
            .values
            .filter { it.isNotEmpty() }
            .map { bucketItems ->
                val first = bucketItems.first()
                AlbumCollection(
                    id = "bucket_${first.bucketId}",
                    name = first.bucketName,
                    kind = AlbumKind.FOLDER,
                    coverUri = first.contentUri,
                    coverIsVideo = first.isVideo,
                    count = bucketItems.size,
                    subtitle = first.relativePath.trimEnd('/').ifBlank { "Device folder" },
                    items = bucketItems,
                )
            }
            .sortedByDescending { it.count }

        return collections + folderAlbums
    }

    private data class BaseState(
        val permissionState: PermissionAccessState,
        val isLoading: Boolean,
        val allMedia: List<MediaItem>,
        val trashedMedia: List<MediaItem>,
        val filter: MediaFilter,
    )

    private data class QueryState(
        val searchQuery: String,
        val isSearchActive: Boolean,
        val selectedUris: Set<Uri>,
        val selectedYear: Int?,
        val undoState: UndoRedoUiState,
    )
}
