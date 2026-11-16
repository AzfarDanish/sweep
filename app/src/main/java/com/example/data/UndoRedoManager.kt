package com.example.data

import com.example.model.TrashAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UndoRedoUiState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

/**
 * Strictly session-scoped, in-memory undo/redo stack for trash and restore actions.
 * Automatically resets when the app process terminates.
 */
class UndoRedoManager {
    private val undoStack = ArrayDeque<TrashAction>()
    private val redoStack = ArrayDeque<TrashAction>()

    private val _state = MutableStateFlow(UndoRedoUiState())
    val state: StateFlow<UndoRedoUiState> = _state.asStateFlow()

    fun recordNewAction(action: TrashAction) {
        if (action.uris.isEmpty()) return
        undoStack.addLast(action)
        redoStack.clear()
        publishState()
    }

    fun peekUndo(): TrashAction? = undoStack.lastOrNull()

    fun peekRedo(): TrashAction? = redoStack.lastOrNull()

    fun commitUndoExecuted() {
        val action = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(action)
        publishState()
    }

    fun commitRedoExecuted() {
        val action = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(action)
        publishState()
    }

    fun discardTopUndo() {
        undoStack.removeLastOrNull()
        publishState()
    }

    fun discardTopRedo() {
        redoStack.removeLastOrNull()
        publishState()
    }

    private fun publishState() {
        _state.value = UndoRedoUiState(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
        )
    }
}
