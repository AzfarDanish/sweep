package com.example

import android.net.Uri
import com.example.data.UndoRedoManager
import com.example.model.TrashAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UndoRedoManagerTest {

    @Test
    fun `undo and redo cycle works and clears redo on new action`() {
        val manager = UndoRedoManager()
        val uri1 = Uri.parse("content://media/external/images/media/1")
        val uri2 = Uri.parse("content://media/external/images/media/2")

        assertFalse(manager.state.value.canUndo)
        assertFalse(manager.state.value.canRedo)

        manager.recordNewAction(TrashAction.MoveToTrash(listOf(uri1)))
        assertTrue(manager.state.value.canUndo)
        assertFalse(manager.state.value.canRedo)

        val undoAction = manager.peekUndo()
        assertEquals(listOf(uri1), undoAction?.uris)
        manager.commitUndoExecuted()

        assertFalse(manager.state.value.canUndo)
        assertTrue(manager.state.value.canRedo)

        val redoAction = manager.peekRedo()
        assertEquals(listOf(uri1), redoAction?.uris)
        manager.commitRedoExecuted()

        assertTrue(manager.state.value.canUndo)
        assertFalse(manager.state.value.canRedo)

        // Undo again, then perform a new action -> redo must clear
        manager.commitUndoExecuted()
        assertTrue(manager.state.value.canRedo)

        manager.recordNewAction(TrashAction.MoveToTrash(listOf(uri2)))
        assertTrue(manager.state.value.canUndo)
        assertFalse(manager.state.value.canRedo)
        assertNull(manager.peekRedo())
    }
}
