package com.humanstudio3d.data

import android.content.Context
import com.humanstudio3d.domain.ServerUrl

/**
 * The user's own conversion server. Nothing is bundled in the APK: the address and the optional
 * token are typed by the user and stored only on this device (the token encrypted with Keystore).
 */
class ServerSettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("server_settings", Context.MODE_PRIVATE)

    /** Normalized HTTPS base URL, or null when no valid server is configured. */
    val baseUrl: String?
        get() = prefs.getString(KEY_URL, null)?.let { ServerUrl.normalize(it) }

    val token: String?
        get() = prefs.getString(KEY_TOKEN, null)?.let { SecretBox.decrypt(it) }

    val hasToken: Boolean get() = token != null

    fun setBaseUrl(normalized: String?) {
        prefs.edit().apply {
            if (normalized.isNullOrBlank()) remove(KEY_URL) else putString(KEY_URL, normalized)
        }.apply()
    }

    fun setToken(value: String?) {
        prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_TOKEN) else putString(KEY_TOKEN, SecretBox.encrypt(value))
        }.apply()
    }

    private companion object {
        const val KEY_URL = "base_url"
        const val KEY_TOKEN = "token_encrypted"
    }
}
