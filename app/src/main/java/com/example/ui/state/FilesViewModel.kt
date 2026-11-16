package com.example.ui.state

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SafFilesRepository
import com.example.data.SafFolderRoot
import com.example.model.FileCategory
import com.example.model.FileEntry
import com.example.model.FileSortOption
import com.example.model.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface FilesLocation {
    data object Root : FilesLocation
    data class Category(val category: FileCategory) : FilesLocation
    data class SafDirectory(val stack: List<SafFolderNode>) : FilesLocation
}

data class SafFolderNode(
    val uri: Uri,
    val name: String,
)

data class FilesUiState(
    val location: FilesLocation = FilesLocation.Root,
    val isLoading: Boolean = false,
    val categoryCounts: Map<FileCategory, Int> = emptyMap(),
    val authorizedFolders: List<SafFolderRoot> = emptyList(),
    val entries: List<FileEntry> = emptyList(),
    val sortOption: FileSortOption = FileSortOption.DATE,
    val sortOrder: SortOrder = SortOrder.DESCENDING,
    val selectedIds: Set<String> = emptySet(),
    val statusMessage: String? = null,
)

class FilesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SafFilesRepository(application)

    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    private var rawEntries: List<FileEntry> = emptyList()

    init {
        loadRoot()
    }

    fun loadRoot() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    location = FilesLocation.Root,
                    isLoading = true,
                    selectedIds = emptySet(),
                    entries = emptyList(),
                )
            }
            val counts = repository.loadCategoryCounts()
            val folders = repository.getAuthorizedFolders()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    categoryCounts = counts,
                    authorizedFolders = folders,
                )
            }
        }
    }

    fun openCategory(category: FileCategory) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    location = FilesLocation.Category(category),
                    isLoading = true,
                    selectedIds = emptySet(),
                )
            }
            rawEntries = repository.loadCategoryFiles(category)
            publishSortedEntries()
        }
    }

    fun addSafFolder(treeUri: Uri) {
        repository.persistFolderPermission(treeUri)
        val folders = repository.getAuthorizedFolders()
        _uiState.update { it.copy(authorizedFolders = folders) }
        val added = folders.firstOrNull { it.uri == treeUri }
        if (added != null) {
            openSafRoot(added)
        }
    }

    fun removeSafFolder(treeUri: Uri) {
        repository.releaseFolderPermission(treeUri)
        loadRoot()
    }

    fun openSafRoot(folder: SafFolderRoot) {
        val node = SafFolderNode(uri = folder.uri, name = folder.name)
        openSafStack(listOf(node))
    }

    fun openSubdirectory(entry: FileEntry) {
        val currentLoc = _uiState.value.location
        if (currentLoc !is FilesLocation.SafDirectory || !entry.isDirectory) return
        val nextStack = currentLoc.stack + SafFolderNode(uri = entry.uri, name = entry.name)
        openSafStack(nextStack)
    }

    private fun openSafStack(stack: List<SafFolderNode>) {
        val target = stack.lastOrNull() ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    location = FilesLocation.SafDirectory(stack),
                    isLoading = true,
                    selectedIds = emptySet(),
                )
            }
            rawEntries = repository.listSafDirectory(target.uri)
            publishSortedEntries()
        }
    }

    fun navigateUp(): Boolean {
        return when (val loc = _uiState.value.location) {
            is FilesLocation.Root -> false
            is FilesLocation.Category -> {
                loadRoot()
                true
            }
            is FilesLocation.SafDirectory -> {
                if (loc.stack.size > 1) {
                    openSafStack(loc.stack.dropLast(1))
                } else {
                    loadRoot()
                }
                true
            }
        }
    }

    fun refreshCurrent() {
        when (val loc = _uiState.value.location) {
            is FilesLocation.Root -> loadRoot()
            is FilesLocation.Category -> openCategory(loc.category)
            is FilesLocation.SafDirectory -> openSafStack(loc.stack)
        }
    }

    fun setSort(option: FileSortOption, order: SortOrder) {
        _uiState.update { it.copy(sortOption = option, sortOrder = order) }
        publishSortedEntries()
    }

    fun toggleSelection(entryId: String) {
        _uiState.update { state ->
            val updated = if (entryId in state.selectedIds) {
                state.selectedIds - entryId
            } else {
                state.selectedIds + entryId
            }
            state.copy(selectedIds = updated)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun selectAllVisible() {
        _uiState.update { state ->
            state.copy(selectedIds = state.entries.filter { !it.isDirectory }.map { it.id }.toSet())
        }
    }

    fun selectedEntries(): List<FileEntry> {
        val ids = _uiState.value.selectedIds
        return _uiState.value.entries.filter { it.id in ids }
    }

    fun consumeStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun publishSortedEntries() {
        val state = _uiState.value
        val sorted = sortEntries(rawEntries, state.sortOption, state.sortOrder)
        _uiState.update {
            it.copy(
                isLoading = false,
                entries = sorted,
            )
        }
    }

    private fun sortEntries(
        list: List<FileEntry>,
        option: FileSortOption,
        order: SortOrder,
    ): List<FileEntry> {
        val comparator = when (option) {
            FileSortOption.NAME -> compareBy<FileEntry> { it.name.lowercase() }
            FileSortOption.DATE -> compareBy<FileEntry> { it.lastModifiedMillis }
            FileSortOption.SIZE -> compareBy<FileEntry> { it.sizeBytes }
        }
        val ordered = if (order == SortOrder.ASCENDING) comparator else comparator.reversed()
        return list.sortedWith(compareByDescending<FileEntry> { it.isDirectory }.then(ordered))
    }
}
