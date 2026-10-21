package com.humanstudio3d.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.humanstudio3d.data.HealthResult
import com.humanstudio3d.data.ServerClient
import com.humanstudio3d.data.ServerSettings
import com.humanstudio3d.domain.ServerUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SettingsStatus {
    data object Idle : SettingsStatus
    data object Saved : SettingsStatus
    data object InvalidUrl : SettingsStatus
    data object Testing : SettingsStatus
    data class Tested(val result: HealthResult) : SettingsStatus
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = ServerSettings(app)
    private val client = ServerClient()

    val savedUrl: String = settings.baseUrl.orEmpty()

    private val _hasToken = MutableStateFlow(settings.hasToken)
    val hasToken: StateFlow<Boolean> = _hasToken.asStateFlow()

    private val _status = MutableStateFlow<SettingsStatus>(SettingsStatus.Idle)
    val status: StateFlow<SettingsStatus> = _status.asStateFlow()

    /** An empty address clears the server. A blank token field keeps the stored token. */
    fun save(urlInput: String, tokenInput: String) {
        if (urlInput.isBlank()) {
            settings.setBaseUrl(null)
        } else {
            val normalized = ServerUrl.normalize(urlInput)
            if (normalized == null) {
                _status.value = SettingsStatus.InvalidUrl
                return
            }
            settings.setBaseUrl(normalized)
        }
        if (tokenInput.isNotBlank()) {
            settings.setToken(tokenInput.trim())
            _hasToken.value = true
        }
        _status.value = SettingsStatus.Saved
    }

    fun clearToken() {
        settings.setToken(null)
        _hasToken.value = false
        _status.value = SettingsStatus.Idle
    }

    /** Checks the typed address (not necessarily saved yet) with GET /v1/health. */
    fun test(urlInput: String, tokenInput: String) {
        val normalized = ServerUrl.normalize(urlInput)
        if (normalized == null) {
            _status.value = SettingsStatus.InvalidUrl
            return
        }
        val token = tokenInput.trim().ifEmpty { settings.token }
        _status.value = SettingsStatus.Testing
        viewModelScope.launch {
            _status.value = SettingsStatus.Tested(client.health(normalized, token))
        }
    }
}
