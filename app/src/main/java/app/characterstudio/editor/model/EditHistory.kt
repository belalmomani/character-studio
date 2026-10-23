package app.characterstudio.editor.model

/**
 * تراجع/إعادة بلقطات كاملة للمشهد. السحب والمنزلقات تستخدم preview ثم endGesture
 * لتُسجَّل حركة واحدة في السجل بدل مئات الخطوات.
 */
class EditHistory(initial: Scene, private val limit: Int = 100) {
    var present: Scene = initial
        private set
    private val undoStack = ArrayDeque<Scene>()
    private val redoStack = ArrayDeque<Scene>()
    private var gestureBase: Scene? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty() || (gestureBase != null && gestureBase != present)
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun commit(next: Scene) {
        endGesture()
        if (next == present) return
        push(present)
        present = next
    }

    fun preview(next: Scene) {
        if (gestureBase == null) gestureBase = present
        present = next
    }

    fun endGesture() {
        val base = gestureBase ?: return
        gestureBase = null
        if (base != present) push(base)
    }

    fun undo(): Boolean {
        endGesture()
        if (undoStack.isEmpty()) return false
        redoStack.addLast(present)
        present = undoStack.removeLast()
        return true
    }

    fun redo(): Boolean {
        endGesture()
        if (redoStack.isEmpty()) return false
        undoStack.addLast(present)
        present = redoStack.removeLast()
        return true
    }

    private fun push(scene: Scene) {
        undoStack.addLast(scene)
        if (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
    }
}
