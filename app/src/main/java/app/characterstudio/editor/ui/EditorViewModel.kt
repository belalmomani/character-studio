package app.characterstudio.editor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.characterstudio.CharacterStudioApp
import app.characterstudio.assets.AssetEntity
import app.characterstudio.data.ProjectRepository
import app.characterstudio.editor.model.*
import java.util.UUID
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class LoadState { Loading, Ready, NotFound, Corrupt, NeedsUpdate }
enum class SaveStatus { Saved, Saving, Unsaved, Error }

@OptIn(FlowPreview::class)
class EditorViewModel(private val repo: ProjectRepository, private val projectId: String) : ViewModel() {
    private var history: EditHistory? = null
    private var sessionStart: Scene? = null
    private val saveLock = Mutex()
    private var lastSavedJson: String? = null

    private val _load = MutableStateFlow(LoadState.Loading)
    val loadState: StateFlow<LoadState> = _load
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name
    private val _scene = MutableStateFlow<Scene?>(null)
    val scene: StateFlow<Scene?> = _scene
    private val _selected = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selected
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo
    private val _status = MutableStateFlow(SaveStatus.Saved)
    val saveStatus: StateFlow<SaveStatus> = _status
    private val _anchorMode = MutableStateFlow(false)
    val anchorMode: StateFlow<Boolean> = _anchorMode
    private val _view = MutableStateFlow(Viewport())
    val view: StateFlow<Viewport> = _view

    init {
        viewModelScope.launch {
            val p = repo.get(projectId)
            if (p == null) { _load.value = LoadState.NotFound; return@launch }
            val scene = try {
                SceneJson.decode(p.documentJson)
            } catch (e: UnsupportedSceneVersion) {
                _load.value = LoadState.NeedsUpdate; return@launch
            } catch (e: Exception) {
                _load.value = LoadState.Corrupt; return@launch
            }
            history = EditHistory(scene)
            sessionStart = scene
            lastSavedJson = p.documentJson
            _name.value = p.name
            _scene.value = scene
            _load.value = LoadState.Ready
        }
        viewModelScope.launch {
            _scene.filterNotNull().drop(1)
                .onEach { _status.value = SaveStatus.Unsaved }
                .debounce(800)
                .collect { saveNow() }
        }
    }

    private fun sync() {
        val h = history ?: return
        _scene.value = h.present
        _canUndo.value = h.canUndo
        _canRedo.value = h.canRedo
        val sel = _selected.value
        if (sel != null && h.present.layers.none { it.id == sel }) _selected.value = null
    }

    private fun commit(op: (Scene) -> Scene) { history?.let { it.commit(op(it.present)); sync() } }
    private fun live(op: (Scene) -> Scene) { history?.let { it.preview(op(it.present)); sync() } }
    fun endLive() { history?.endGesture(); sync() }

    /** حفظ فوري. يرجع true عند النجاح أو عدم وجود تغييرات. */
    suspend fun saveNow(): Boolean = saveLock.withLock {
        val s = history?.present ?: return true
        val json = SceneJson.encode(s)
        if (json == lastSavedJson) { _status.value = SaveStatus.Saved; return true }
        _status.value = SaveStatus.Saving
        val ok = repo.saveDocument(projectId, json).isSuccess
        if (ok) lastSavedJson = json
        _status.value = if (ok) SaveStatus.Saved else SaveStatus.Error
        ok
    }

    fun saveInBackground() { viewModelScope.launch { saveNow() } }

    // ---- التحديد والعرض
    fun select(id: String?) { _selected.value = id }
    fun selectAt(x: Float, y: Float): String? {
        val id = history?.present?.let { SceneOps.hitTest(it, x, y) }
        _selected.value = id
        return id
    }
    fun anchorAt(x: Float, y: Float, radius: Float): String? =
        history?.present?.let { SceneOps.nearestAnchor(it, x, y, radius)?.id }
    fun transformView(cx: Float, cy: Float, vcx: Float, vcy: Float, zoom: Float, dx: Float, dy: Float) {
        _view.value = _view.value.zoomAbout(cx, cy, vcx, vcy, zoom).panBy(dx, dy)
    }
    fun panBy(dx: Float, dy: Float) { _view.value = _view.value.panBy(dx, dy) }
    fun resetView() { _view.value = Viewport() }
    fun setAnchorMode(on: Boolean) { _anchorMode.value = on }

    // ---- الطبقات
    fun addShape(kind: LayerKind, name: String) {
        val id = UUID.randomUUID().toString()
        commit { SceneOps.addLayer(it, id, name, kind) }
        _selected.value = id
    }
    fun addAssetLayer(asset: AssetEntity, file: String) {
        val id = UUID.randomUUID().toString()
        commit { SceneOps.addAssetLayer(it, id, asset.nameAr, asset.assetId, asset.version, file, asset.width, asset.height) }
        _selected.value = id
    }
    fun deleteLayer(id: String) = commit { SceneOps.deleteLayer(it, id) }
    fun duplicateLayer(id: String, name: String) {
        val newId = UUID.randomUUID().toString()
        commit { SceneOps.duplicateLayer(it, id, newId, name) }
        _selected.value = newId
    }
    fun rename(id: String, name: String) = commit { SceneOps.rename(it, id, name) }
    fun setVisible(id: String, v: Boolean) = commit { SceneOps.setVisible(it, id, v) }
    fun setLocked(id: String, v: Boolean) = commit { SceneOps.setLocked(it, id, v) }
    fun moveLayer(id: String, delta: Int) = commit { SceneOps.moveLayer(it, id, delta) }
    fun setColor(id: String, argb: Long) = commit { SceneOps.edit(it, id) { l -> l.copy(colorArgb = argb) } }
    fun resetLayer(id: String) = commit { SceneOps.resetLayer(it, id) }
    fun snapToAnchor(id: String) = commit { SceneOps.snapToNearestAnchor(it, id) }

    // ---- تعديلات حية (سحب/منزلقات) تُسجَّل حركة واحدة عند endLive
    fun liveTransform(id: String, f: (Transform) -> Transform) = live { SceneOps.setTransform(it, id, f) }
    fun liveOpacity(id: String, v: Float) = live { SceneOps.edit(it, id) { l -> l.copy(opacity = v.coerceIn(0f, 1f)) } }
    fun moveSelectedBy(dx: Float, dy: Float) {
        val id = _selected.value ?: return
        liveTransform(id) { it.copy(x = it.x + dx, y = it.y + dy) }
    }
    fun moveAnchorBy(anchorId: String, dx: Float, dy: Float) = live { SceneOps.moveAnchorBy(it, anchorId, dx, dy) }

    // ---- اللوحة والهيكل
    fun setShowSkeleton(v: Boolean) = commit { it.copy(showSkeleton = v) }
    fun setShowAnchors(v: Boolean) = commit { it.copy(showAnchors = v) }
    fun setBackground(argb: Long?) = commit { it.copy(backgroundArgb = argb) }
    fun resetAnchors() = commit { SceneOps.resetAnchors(it) }

    fun undo() { history?.undo(); sync() }
    fun redo() { history?.redo(); sync() }

    /** إعادة ضبط كل التغييرات إلى حالة بداية الجلسة (قابلة للتراجع). */
    fun resetSession() { sessionStart?.let { start -> commit { start } } }

    companion object {
        fun factory(projectId: String) = viewModelFactory {
            initializer {
                EditorViewModel((this[APPLICATION_KEY] as CharacterStudioApp).container.projects, projectId)
            }
        }
    }
}
