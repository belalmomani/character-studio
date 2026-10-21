package app.characterstudio.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.characterstudio.CharacterStudioApp
import app.characterstudio.data.ProjectRepository
import app.characterstudio.editor.model.Scene
import app.characterstudio.editor.model.SceneJson
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExportLoad { Loading, Ready, Failed }
sealed interface ExportResult {
    data object Idle : ExportResult
    data object Working : ExportResult
    data class Saved(val name: String) : ExportResult
    data object Failed : ExportResult
    data object OutOfMemory : ExportResult
}

class ExportViewModel(private val repo: ProjectRepository, private val projectId: String) : ViewModel() {
    private val maxHeap = Runtime.getRuntime().maxMemory()
    val supportedSizes: List<Int> = ExportRules.supportedSizes(maxHeap)

    private var scene: Scene? = null
    private val _load = MutableStateFlow(ExportLoad.Loading)
    val load: StateFlow<ExportLoad> = _load
    private val _name = MutableStateFlow("")
    val projectName: StateFlow<String> = _name
    private val _options = MutableStateFlow(ExportOptions())
    val options: StateFlow<ExportOptions> = _options
    private val _preview = MutableStateFlow<android.graphics.Bitmap?>(null)
    val preview: StateFlow<android.graphics.Bitmap?> = _preview
    private val _result = MutableStateFlow<ExportResult>(ExportResult.Idle)
    val result: StateFlow<ExportResult> = _result
    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            val p = repo.get(projectId)
            val s = p?.let { runCatching { SceneJson.decode(it.documentJson) }.getOrNull() }
            if (p == null || s == null) { _load.value = ExportLoad.Failed; return@launch }
            scene = s
            _name.value = p.name
            _load.value = ExportLoad.Ready
            refreshPreview()
        }
    }

    fun update(f: (ExportOptions) -> ExportOptions) {
        _options.value = ExportRules.sanitized(f(_options.value), maxHeap)
        refreshPreview()
    }

    /** المعاينة بدقة 512 بنفس خيارات التصدير (الصيغة والخلفية والهيكل). */
    private fun refreshPreview() {
        val s = scene ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val bmp = withContext(Dispatchers.Default) {
                runCatching { SceneExporter.render(s, _options.value.copy(size = 512)) }.getOrNull()
            }
            _preview.value = bmp
        }
    }

    fun suggestedName(): String = ExportRules.fileName(_name.value, _options.value)

    /** يحفظ في Uri اختاره المستخدم عبر منتقي الملفات (لا يحتاج أذونات تخزين). */
    fun saveTo(context: Context, uri: Uri) {
        val s = scene ?: return
        val o = _options.value
        _result.value = ExportResult.Working
        viewModelScope.launch {
            _result.value = withContext(Dispatchers.IO) {
                var bmp: android.graphics.Bitmap? = null
                try {
                    bmp = SceneExporter.render(s, o)
                    val ok = context.contentResolver.openOutputStream(uri)?.use { SceneExporter.encode(bmp, o, it) } ?: false
                    if (ok) ExportResult.Saved(uri.lastPathSegment ?: "") else ExportResult.Failed
                } catch (e: OutOfMemoryError) {
                    ExportResult.OutOfMemory
                } catch (e: Exception) {
                    ExportResult.Failed
                } finally {
                    bmp?.recycle()
                }
            }
        }
    }

    /** يكتب الصورة في ذاكرة التطبيق المؤقتة ويفتح Android Sharesheet. */
    fun share(context: Context, startChooser: (Intent) -> Unit) {
        val s = scene ?: return
        val o = _options.value
        _result.value = ExportResult.Working
        viewModelScope.launch {
            val intent = withContext(Dispatchers.IO) {
                var bmp: android.graphics.Bitmap? = null
                try {
                    val dir = File(context.cacheDir, "exports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                    val file = File(dir, suggestedName())
                    bmp = SceneExporter.render(s, o)
                    val ok = file.outputStream().use { SceneExporter.encode(bmp, o, it) }
                    if (!ok) { _result.value = ExportResult.Failed; null } else {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        Intent(Intent.ACTION_SEND).apply {
                            type = o.format.mime
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                } catch (e: OutOfMemoryError) {
                    _result.value = ExportResult.OutOfMemory; null
                } catch (e: Exception) {
                    _result.value = ExportResult.Failed; null
                } finally {
                    bmp?.recycle()
                }
            }
            if (intent != null) { _result.value = ExportResult.Idle; startChooser(Intent.createChooser(intent, null)) }
        }
    }

    fun clearResult() { _result.value = ExportResult.Idle }

    companion object {
        fun factory(projectId: String) = viewModelFactory {
            initializer {
                ExportViewModel((this[APPLICATION_KEY] as CharacterStudioApp).container.projects, projectId)
            }
        }
    }
}
