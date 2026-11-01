package app.characterstudio.sync

import android.content.SharedPreferences
import app.characterstudio.auth.AuthRepository
import app.characterstudio.auth.AuthState
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SyncError { NotSignedIn, NotConfigured, Network, Unknown }
class SyncException(val error: SyncError) : Exception(error.name)

/** المزامنة اختيارية ومعطّلة افتراضيًا: لا يُرفع شيء قبل أن يفعّلها المستخدم صراحةً بعد تأكيد. */
class ProjectSyncService(
    private val auth: AuthRepository,
    private val prefs: SharedPreferences,
    private val engineFor: suspend (userId: String) -> ProjectSyncEngine?,
) {
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY, false))
    val enabled: StateFlow<Boolean> = _enabled
    private val mutex = Mutex()

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY, value).apply()
        _enabled.value = value
    }

    suspend fun syncNow(): Result<SyncCounts> = mutex.withLock {
        val user = auth.state.value as? AuthState.SignedIn ?: return@withLock Result.failure(SyncException(SyncError.NotSignedIn))
        if (auth.accessToken() == null) return@withLock Result.failure(SyncException(SyncError.NotSignedIn))
        val engine = engineFor(user.userId) ?: return@withLock Result.failure(SyncException(SyncError.NotConfigured))
        runCatching { engine.sync() }.recoverCatching { e ->
            throw when (e) {
                is SyncException -> e
                is IOException -> SyncException(SyncError.Network)
                else -> SyncException(SyncError.Unknown)
            }
        }
    }

    private companion object { const val KEY = "enabled" }
}
