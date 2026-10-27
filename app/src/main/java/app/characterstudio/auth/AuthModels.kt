package app.characterstudio.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtSec: Long,
    val userId: String,
    val email: String,
) {
    /** نجدد قبل الانتهاء بدقيقة لتفادي إرسال رمز منتهٍ. */
    fun needsRefresh(nowSec: Long, marginSec: Long = 60): Boolean = nowSec >= expiresAtSec - marginSec
}

sealed interface AuthState {
    data object SignedOut : AuthState
    data class SignedIn(val userId: String, val email: String) : AuthState
}

enum class AuthError { InvalidCredentials, EmailNotConfirmed, AlreadyRegistered, WeakPassword, RateLimited, Network, NotConfigured, Unknown }

class AuthException(val error: AuthError, message: String? = null) : Exception(message ?: error.name)

sealed interface SignUpOutcome {
    data class SignedIn(val session: Session) : SignUpOutcome
    /** الحساب أُنشئ لكن يحتاج تأكيد البريد قبل الدخول. */
    data object ConfirmEmail : SignUpOutcome
}

@Serializable
private data class UserDto(val id: String, val email: String? = null)

@Serializable
private data class TokenDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    val user: UserDto? = null,
)

object AuthParsing {
    private val json = Json { ignoreUnknownKeys = true }

    /** يحوّل استجابة GoTrue إلى جلسة، أو null إن لم تحتوِ رموزًا (مثل إنشاء حساب بانتظار تأكيد البريد). */
    fun session(body: String, nowSec: Long): Session? {
        val d = runCatching { json.decodeFromString<TokenDto>(body) }.getOrNull() ?: return null
        val access = d.accessToken ?: return null
        val refresh = d.refreshToken ?: return null
        val user = d.user ?: return null
        val exp = d.expiresAt ?: (nowSec + (d.expiresIn ?: 3600))
        return Session(access, refresh, exp, user.id, user.email.orEmpty())
    }

    fun error(httpCode: Int, body: String): AuthError {
        if (httpCode == 429) return AuthError.RateLimited
        val obj: JsonObject? = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        fun s(k: String) = (obj?.get(k) as? JsonPrimitive)?.contentOrNull.orEmpty()
        val text = (s("error_code") + " " + s("msg") + " " + s("message") + " " + s("error_description") + " " + s("error")).lowercase()
        return when {
            "invalid_credentials" in text || "invalid login credentials" in text -> AuthError.InvalidCredentials
            "email_not_confirmed" in text || "email not confirmed" in text -> AuthError.EmailNotConfirmed
            "user_already_exists" in text || "already registered" in text || "already been registered" in text -> AuthError.AlreadyRegistered
            "weak_password" in text || "password should be" in text -> AuthError.WeakPassword
            "over_request_rate_limit" in text || "over_email_send_rate_limit" in text || "rate limit" in text -> AuthError.RateLimited
            else -> AuthError.Unknown
        }
    }
}

object AuthValidation {
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
    const val MIN_PASSWORD = 8
    fun emailOk(e: String) = e.trim().length <= 254 && EMAIL.matches(e.trim())
    fun passwordOk(p: String) = p.length in MIN_PASSWORD..72
}
