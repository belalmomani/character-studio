package app.characterstudio.auth

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AuthApi {
    suspend fun signIn(email: String, password: String): Session
    suspend fun signUp(email: String, password: String): SignUpOutcome
    suspend fun refresh(refreshToken: String): Session
    suspend fun signOut(accessToken: String)
    suspend fun recover(email: String)
}

interface SessionStore {
    fun load(): Session?
    fun save(session: Session)
    fun clear()
}

/** يدير الجلسة: تخزين آمن، تجديد الرمز تلقائيًا، وتسجيل الخروج المحلي حتى لو فشل الخادم. */
class AuthRepository(
    private val api: AuthApi?,
    private val store: SessionStore,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val mutex = Mutex()
    private var session: Session? = store.load()
    private val _state = MutableStateFlow<AuthState>(session.toState())
    val state: StateFlow<AuthState> = _state

    private fun Session?.toState(): AuthState = if (this == null) AuthState.SignedOut else AuthState.SignedIn(userId, email)
    private fun set(s: Session?) {
        session = s
        if (s == null) store.clear() else store.save(s)
        _state.value = s.toState()
    }
    private fun requireApi(): AuthApi = api ?: throw AuthException(AuthError.NotConfigured)

    suspend fun signIn(email: String, password: String): Result<Unit> = mutex.withLock {
        runCatching { set(requireApi().signIn(email.trim(), password)) }.mapFailure()
    }

    suspend fun signUp(email: String, password: String): Result<SignUpOutcome> = mutex.withLock {
        runCatching {
            val out = requireApi().signUp(email.trim(), password)
            if (out is SignUpOutcome.SignedIn) set(out.session)
            out
        }.mapFailure()
    }

    suspend fun recover(email: String): Result<Unit> = runCatching { requireApi().recover(email.trim()) }.mapFailure()

    suspend fun signOut() = mutex.withLock {
        val s = session
        set(null) // الخروج المحلي أولًا وبلا شرط
        if (s != null) runCatching { api?.signOut(s.accessToken) }
    }

    /** رمز صالح للاستدعاءات، يُجدَّد عند الحاجة. null = غير مسجَّل. */
    suspend fun accessToken(): String? = mutex.withLock {
        val s = session ?: return@withLock null
        if (!s.needsRefresh(clock())) return@withLock s.accessToken
        val refreshed = runCatching { requireApi().refresh(s.refreshToken) }
        refreshed.onSuccess { set(it); return@withLock it.accessToken }
        val e = refreshed.exceptionOrNull()
        // رفض صريح للرمز = انتهت الجلسة فعلًا؛ أما انقطاع الشبكة فنُبقي الجلسة ونرجع null مؤقتًا
        if (e is AuthException && e.error != AuthError.Network) set(null)
        null
    }

    private fun <T> Result<T>.mapFailure(): Result<T> = recoverCatching { e ->
        throw when (e) {
            is AuthException -> e
            is IOException -> AuthException(AuthError.Network)
            else -> AuthException(AuthError.Unknown, e.message)
        }
    }
}
