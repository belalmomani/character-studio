package app.characterstudio.auth

import org.junit.Assert.*
import org.junit.Test

class AuthParsingTest {
    private val tokenJson = """{"access_token":"AT","token_type":"bearer","expires_in":3600,"expires_at":1800000000,
        "refresh_token":"RT","user":{"id":"u-1","email":"a@b.co","role":"authenticated"}}"""

    @Test fun parsesSessionAndPrefersExpiresAt() {
        val s = AuthParsing.session(tokenJson, 1_000)!!
        assertEquals("AT", s.accessToken); assertEquals("RT", s.refreshToken)
        assertEquals("u-1", s.userId); assertEquals("a@b.co", s.email)
        assertEquals(1_800_000_000L, s.expiresAtSec)
    }

    @Test fun fallsBackToExpiresIn() {
        val s = AuthParsing.session("""{"access_token":"A","refresh_token":"R","expires_in":100,"user":{"id":"u"}}""", 500)!!
        assertEquals(600L, s.expiresAtSec)
    }

    @Test fun signupWithoutTokensIsNotASession() {
        assertNull(AuthParsing.session("""{"id":"u-1","email":"a@b.co","confirmation_sent_at":"2026-01-01"}""", 0))
        assertNull(AuthParsing.session("not json", 0))
        assertNull(AuthParsing.session("""{"access_token":"A","refresh_token":"R"}""", 0)) // بلا مستخدم
    }

    @Test fun needsRefreshWithMargin() {
        val s = Session("a", "r", 1_000, "u", "e")
        assertFalse(s.needsRefresh(900)); assertTrue(s.needsRefresh(940)); assertTrue(s.needsRefresh(2_000))
    }

    @Test fun mapsGoTrueErrors() {
        assertEquals(AuthError.InvalidCredentials, AuthParsing.error(400, """{"error_code":"invalid_credentials","msg":"Invalid login credentials"}"""))
        assertEquals(AuthError.InvalidCredentials, AuthParsing.error(400, """{"error":"invalid_grant","error_description":"Invalid login credentials"}"""))
        assertEquals(AuthError.EmailNotConfirmed, AuthParsing.error(400, """{"error_code":"email_not_confirmed","msg":"Email not confirmed"}"""))
        assertEquals(AuthError.AlreadyRegistered, AuthParsing.error(422, """{"error_code":"user_already_exists","msg":"User already registered"}"""))
        assertEquals(AuthError.WeakPassword, AuthParsing.error(422, """{"error_code":"weak_password","msg":"Password should be at least 6 characters"}"""))
        assertEquals(AuthError.RateLimited, AuthParsing.error(429, ""))
        assertEquals(AuthError.Unknown, AuthParsing.error(500, "boom"))
    }

    @Test fun validation() {
        assertTrue(AuthValidation.emailOk(" a@b.co ")); assertFalse(AuthValidation.emailOk("a@b")); assertFalse(AuthValidation.emailOk("a b@c.de"))
        assertTrue(AuthValidation.passwordOk("12345678")); assertFalse(AuthValidation.passwordOk("1234567")); assertFalse(AuthValidation.passwordOk("x".repeat(73)))
    }
}
