package app.characterstudio.editor

import app.characterstudio.editor.model.*
import org.junit.Assert.*
import org.junit.Test

class EditHistoryTest {
    private val s0 = Scene()
    private fun withLayer(s: Scene, id: String) = SceneOps.addLayer(s, id, id, LayerKind.Rect)

    @Test fun undoRedoRoundTrip() {
        val h = EditHistory(s0)
        val s1 = withLayer(s0, "a"); h.commit(s1)
        val s2 = withLayer(s1, "b"); h.commit(s2)
        assertTrue(h.undo()); assertEquals(s1, h.present)
        assertTrue(h.undo()); assertEquals(s0, h.present)
        assertFalse(h.undo()); assertFalse(h.canUndo)
        assertTrue(h.redo()); assertEquals(s1, h.present)
        assertTrue(h.redo()); assertEquals(s2, h.present)
        assertFalse(h.redo())
    }

    @Test fun newCommitClearsRedo() {
        val h = EditHistory(s0)
        h.commit(withLayer(s0, "a")); h.undo()
        assertTrue(h.canRedo)
        h.commit(withLayer(s0, "z"))
        assertFalse(h.canRedo)
    }

    @Test fun gestureIsOneUndoStep() {
        val h = EditHistory(withLayer(s0, "a"))
        val start = h.present
        repeat(20) { i -> h.preview(SceneOps.setTransform(h.present, "a") { it.copy(x = it.x + 1f) }) }
        h.endGesture()
        assertTrue(h.undo())
        assertEquals(start, h.present)
        assertFalse(h.canUndo)
    }

    @Test fun noOpCommitAndEmptyGestureAddNothing() {
        val h = EditHistory(s0)
        h.commit(s0)
        h.preview(s0); h.endGesture()
        assertFalse(h.canUndo)
    }

    @Test fun undoDuringGestureClosesItFirst() {
        val h = EditHistory(withLayer(s0, "a"))
        val start = h.present
        h.preview(SceneOps.setTransform(h.present, "a") { it.copy(x = 1f) })
        assertTrue(h.canUndo)
        assertTrue(h.undo())
        assertEquals(start, h.present)
    }

    @Test fun limitDropsOldest() {
        val h = EditHistory(s0, limit = 3)
        var s = s0
        repeat(5) { i -> s = withLayer(s, "l$i"); h.commit(s) }
        var n = 0
        while (h.undo()) n++
        assertEquals(3, n)
    }
}
