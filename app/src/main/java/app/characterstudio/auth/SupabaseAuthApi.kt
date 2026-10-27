package app.characterstudio.auth

import app.characterstudio.net.Http
import java.io.IOException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** GoTrue (Supabase Auth) عبر REST بالمفتاح العام فقط. */
class SupabaseAuthApi(baseUrl: String, private val anonKey: String) : AuthApi {
    private val base = baseUrl.trimEnd('/')
    private val now get() = System.currentTimeMillis() / 1000
    private fun headers(token: String? = null) =
        mapOf("apikey" to anonKey, "Authorization" to "Bearer ${token ?: anonKey}", "Accept" to "application/json")

    private suspend fun post(path: String, body: String, token: String? = null) =
        Http.call("POST", base + path, headers(token), body)

    override suspend fun signIn(email: String, password: String): Session {
        val r = post("/auth/v1/token?grant_type=password", buildJsonObject { put("email", email); put("password", password) }.toString())
        if (!r.ok) throw AuthException(AuthParsing.error(r.code, r.body))
        return AuthParsing.session(r.body, now) ?: throw AuthException(AuthError.Unknown, "no_session")
    }

    override suspend fun signUp(email: String, password: String): SignUpOutcome {
        val r = post("/auth/v1/signup", buildJsonObject { put("email", email); put("password", password) }.toString())
        if (!r.ok) throw AuthException(AuthParsing.error(r.code, r.body))
        val s = AuthParsing.session(r.body, now)
        return if (s != null) SignUpOutcome.SignedIn(s) else SignUpOutcome.ConfirmEmail
    }

    override suspend fun refresh(refreshToken: String): Session {
        val r = post("/auth/v1/token?grant_type=refresh_token", buildJsonObject { put("refresh_token", refreshToken) }.toString())
        if (r.code in 500..599) throw IOException("server_${r.code}")
        if (!r.ok) throw AuthException(AuthError.InvalidCredentials)
        return AuthParsing.session(r.body, now) ?: throw AuthException(AuthError.Unknown, "no_session")
    }

    override suspend fun signOut(accessToken: String) {
        post("/auth/v1/logout", "{}", accessToken)
    }

    override suspend fun recover(email: String) {
        val r = post("/auth/v1/recover", buildJsonObject { put("email", email) }.toString())
        if (!r.ok) throw AuthException(AuthParsing.error(r.code, r.body))
    }
}
