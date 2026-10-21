package com.humanstudio3d.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.humanstudio3d.data.ImageImportException
import com.humanstudio3d.data.ImageRepository
import com.humanstudio3d.data.ExportException
import com.humanstudio3d.data.HttpImageTo3DService
import com.humanstudio3d.data.ServerSettings
import com.humanstudio3d.data.ModelExporter
import com.humanstudio3d.data.ModelImportException
import com.humanstudio3d.data.ModelRepository
import com.humanstudio3d.domain.FailureReason
import com.humanstudio3d.domain.ExportErrorKind
import com.humanstudio3d.domain.GlbReport
import com.humanstudio3d.domain.GlbValidator
import com.humanstudio3d.domain.ModelErrorKind
import com.humanstudio3d.domain.ImageErrorKind
import com.humanstudio3d.domain.ImageTo3DResult
import com.humanstudio3d.domain.ImageTo3DService
import com.humanstudio3d.domain.ServerUrl
import com.humanstudio3d.domain.ProcessingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ImageUiState(
    val preview: Bitmap? = null,
    val busy: Boolean = false,
    val error: ImageErrorKind? = null,
) {
    val hasImage: Boolean get() = preview != null
}

data class ModelUiState(
    val file: File? = null,
    val report: GlbReport? = null,
    val busy: Boolean = false,
    val error: ModelErrorKind? = null,
)

sealed interface ExportState {
    data object Idle : ExportState
    data object Saving : ExportState
    data class Success(val bytes: Long) : ExportState
    data class Failed(val kind: ExportErrorKind) : ExportState
}

enum class UiEvent { ImageReady, ModelReady }

class CharacterViewModel(app: Application) : AndroidViewModel(app) {

    private val images = ImageRepository(app)
    private val models = ModelRepository(app)
    private val exporter = ModelExporter(app)

    // Talks to the user's own server (address typed in Settings). Not configured until then.
    private val serverSettings = ServerSettings(app)
    private val imageTo3D: ImageTo3DService = HttpImageTo3DService(app, serverSettings)

    // The user must agree before a photo leaves the device; reset whenever the photo changes.
    private var uploadConsent = false

    private val _image = MutableStateFlow(ImageUiState())
    val image: StateFlow<ImageUiState> = _image.asStateFlow()

    private val _processing = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processing: StateFlow<ProcessingState> = _processing.asStateFlow()

    private val _model = MutableStateFlow(ModelUiState())
    val model: StateFlow<ModelUiState> = _model.asStateFlow()

    private val _export = MutableStateFlow<ExportState>(ExportState.Idle)
    val export: StateFlow<ExportState> = _export.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var pendingCapture: File? = null
    private var processingJob: Job? = null

    init {
        // A new session starts clean, which also covers leftovers after a crash.
        images.clearAll()
        models.clear()
    }

    fun prepareCaptureUri(): Uri {
        val file = images.newCaptureFile()
        pendingCapture = file
        return images.uriFor(file)
    }

    fun onCaptureResult(success: Boolean) {
        val file = pendingCapture
        pendingCapture = null
        if (file == null) return
        if (!success) {
            file.delete()
            return
        }
        importUri(images.uriFor(file)) { file.delete() }
    }

    fun onImagePicked(uri: Uri?) {
        if (uri != null) importUri(uri)
    }

    private fun importUri(uri: Uri, afterImport: () -> Unit = {}) {
        _image.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = images.importFromUri(uri)
            afterImport()
            result.onSuccess { bitmap ->
                uploadConsent = false
                _processing.value = ProcessingState.Idle
                _image.value = ImageUiState(preview = bitmap)
                _events.tryEmit(UiEvent.ImageReady)
            }.onFailure { e ->
                val kind = (e as? ImageImportException)?.kind ?: ImageErrorKind.IO
                _image.update { it.copy(busy = false, error = kind) }
            }
        }
    }

    fun rotate(degrees: Float) {
        if (_image.value.busy || !_image.value.hasImage) return
        _image.update { it.copy(busy = true) }
        viewModelScope.launch {
            images.rotateCurrent(degrees)
                .onSuccess { bitmap -> _image.value = ImageUiState(preview = bitmap) }
                .onFailure { _image.update { s -> s.copy(busy = false, error = ImageErrorKind.IO) } }
        }
    }

    fun clearImageError() {
        _image.update { it.copy(error = null) }
    }

    /** Cancel / replace: drops the photo and every temporary file. */
    fun discardImage() {
        uploadConsent = false
        processingJob?.cancel()
        images.clearAll()
        _image.value = ImageUiState()
        _processing.value = ProcessingState.Idle
    }

    /** Opens a GLB the user already has (copied to the cache, validated, then shown). */
    fun onGlbPicked(uri: Uri?) {
        if (uri == null) return
        _model.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            models.importGlb(uri)
                .onSuccess { report ->
                    _model.value = ModelUiState(file = models.current, report = report)
                    _events.tryEmit(UiEvent.ModelReady)
                }
                .onFailure { e ->
                    val kind = (e as? ModelImportException)?.kind ?: ModelErrorKind.IO
                    _model.update { it.copy(busy = false, error = kind) }
                }
        }
    }

    fun clearModelError() {
        _model.update { it.copy(error = null) }
    }

    fun discardModel() {
        models.clear()
        _model.value = ModelUiState()
        _export.value = ExportState.Idle
    }

    fun resetExport() {
        if (_export.value !is ExportState.Saving) _export.value = ExportState.Idle
    }

    /** Saves the opened GLB to [uri] chosen by the user; reports success only after verification. */
    fun exportTo(uri: Uri?) {
        if (uri == null) return
        val file = models.current ?: return
        if (_export.value is ExportState.Saving) return
        _export.value = ExportState.Saving
        viewModelScope.launch {
            exporter.export(file, uri)
                .onSuccess { bytes -> _export.value = ExportState.Success(bytes) }
                .onFailure { e ->
                    val kind = (e as? ExportException)?.kind ?: ExportErrorKind.IO
                    _export.value = ExportState.Failed(kind)
                }
        }
    }

    /** Host of the configured server for the consent dialog, or null when none is configured. */
    fun serverHost(): String? = serverSettings.baseUrl?.let { ServerUrl.host(it) }

    fun grantUploadConsent() {
        uploadConsent = true
    }

    fun resetProcessing() {
        _processing.value = ProcessingState.Idle
    }

    fun startProcessing() {
        if (_processing.value !is ProcessingState.Idle) return
        val file = images.current ?: return

        if (!imageTo3D.isConfigured) {
            // Nothing leaves the device when no provider is configured.
            _processing.value = ProcessingState.Failed(FailureReason.PROVIDER_NOT_CONFIGURED)
            return
        }
        if (!uploadConsent) {
            // Defensive: the review screen asks first, so this should not happen.
            _processing.value = ProcessingState.Cancelled
            return
        }

        processingJob = viewModelScope.launch {
            _processing.value = ProcessingState.ReadyToUpload
            try {
                when (val result = imageTo3D.generate(file) { _processing.value = it }) {
                    is ImageTo3DResult.Success -> {
                        _processing.value = ProcessingState.Validating
                        val report = withContext(Dispatchers.IO) { GlbValidator.validateFile(result.glb) }
                        val adopted = if (report.valid) models.adopt(result.glb) else null
                        if (adopted != null) {
                            _model.value = ModelUiState(file = adopted, report = report)
                            _processing.value = ProcessingState.Completed(adopted, report)
                            _events.tryEmit(UiEvent.ModelReady)
                        } else if (report.valid) {
                            result.glb.delete()
                            _processing.value = ProcessingState.Failed(FailureReason.UNKNOWN)
                        } else {
                            result.glb.delete()
                            _processing.value = ProcessingState.Failed(FailureReason.INVALID_MODEL)
                        }
                    }
                    is ImageTo3DResult.Failure ->
                        _processing.value = ProcessingState.Failed(result.reason)
                }
            } catch (e: CancellationException) {
                _processing.value = ProcessingState.Cancelled
                throw e
            }
        }
    }

    fun cancelProcessing() {
        processingJob?.cancel()
        if (_processing.value !is ProcessingState.Completed) {
            _processing.value = ProcessingState.Cancelled
        }
    }

    override fun onCleared() {
        processingJob?.cancel()
        images.clearAll()
        models.clear()
    }
}
