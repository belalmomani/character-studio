package app.characterstudio.auth

import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

private class MemStore(var s: Session? = null) : SessionStore {
    override fun load() = s
    override fun save(session: Session) { s = session }
    override fun clear() { s = null }
}

private class FakeAuthApi : AuthApi {
    var signInResult: () -> Session = { throw AuthException(AuthError.InvalidCredentials) }
    var refreshResult: () -> Session = { throw IOException("down") }
    var signUpResult: () -> SignUpOutcome = { SignUpOutcome.ConfirmEmail }
    var signOutThrows = false
    var refreshCalls = 0; var signOutCalls = 0
    override suspend fun signIn(email: String, password: String) = signInResult()
    override suspend fun signUp(email: String, password: String) = signUpResult()
    override suspend fun refresh(refreshToken: String): Session { refreshCalls++; return refreshResult() }
    override suspend fun signOut(accessToken: String) { signOutCalls++; if (signOutThrows) throw IOException("x") }
    override suspend fun recover(email: String) {}
}

class AuthRepositoryTest {
    private var now = 1_000L
    private fun session(exp: Long = 10_000, access: String = "A1") = Session(access, "R1", exp, "u1", "a@b.co")
    private val api = FakeAuthApi()
    private val store = MemStore()
    private fun repo() = AuthRepository(api, store) { now }

    @Test fun startsSignedOutOrRestoresStoredSession() {
        assertTrue(repo().state.value is AuthState.SignedOut)
        store.s = session()
        assertEquals(AuthState.SignedIn("u1", "a@b.co"), repo().state.value)
    }

    @Test fun signInStoresSessionAndUpdatesState() = runTest {
        api.signInResult = { session() }
        val r = repo(); assertTrue(r.signIn(" a@b.co ", "pw123456").isSuccess)
        assertEquals(AuthState.SignedIn("u1", "a@b.co"), r.state.value); assertNotNull(store.s)
    }

    @Test fun badCredentialsFailWithoutSession() = runTest {
        val r = repo(); val res = r.signIn("a@b.co", "bad")
        assertEquals(AuthError.InvalidCredentials, (res.exceptionOrNull() as AuthException).error)
        assertTrue(r.state.value is AuthState.SignedOut); assertNull(store.s)
    }

    @Test fun signUpNeedingConfirmationDoesNotSignIn() = runTest {
        val r = repo(); assertEquals(SignUpOutcome.ConfirmEmail, r.signUp("a@b.co", "pw123456").getOrThrow())
        assertTrue(r.state.value is AuthState.SignedOut)
    }

    @Test fun signUpWithImmediateSessionSignsIn() = runTest {
        api.signUpResult = { SignUpOutcome.SignedIn(session()) }
        val r = repo(); r.signUp("a@b.co", "pw123456")
        assertTrue(r.state.value is AuthState.SignedIn)
    }

    @Test fun networkFailureIsMapped() = runTest {
        api.signInResult = { throw IOException("offline") }
        val res = repo().signIn("a@b.co", "pw123456")
        assertEquals(AuthError.Network, (res.exceptionOrNull() as AuthException).error)
    }

    @Test fun validTokenIsReturnedWithoutRefresh() = runTest {
        store.s = session(exp = 10_000)
        assertEquals("A1", repo().accessToken()); assertEquals(0, api.refreshCalls)
    }

    @Test fun nearExpiryTokenIsRefreshedAndStored() = runTest {
        store.s = session(exp = 1_030)
        api.refreshResult = { session(exp = 9_999, access = "A2") }
        val r = repo(); assertEquals("A2", r.accessToken()); assertEquals("A2", store.s!!.accessToken)
    }

    @Test fun concurrentCallsRefreshOnlyOnce() = runTest {
        store.s = session(exp = 1_010)
        api.refreshResult = { session(exp = 9_999, access = "A2") }
        val r = repo()
        val tokens = (1..5).map { async { r.accessToken() } }.awaitAll()
        assertEquals(List(5) { "A2" }, tokens); assertEquals(1, api.refreshCalls)
    }

    @Test fun rejectedRefreshSignsOut() = runTest {
        store.s = session(exp = 1_010)
        api.refreshResult = { throw AuthException(AuthError.InvalidCredentials) }
        val r = repo(); assertNull(r.accessToken())
        assertTrue(r.state.value is AuthState.SignedOut); assertNull(store.s)
    }

    @Test fun networkFailureOnRefreshKeepsSession() = runTest {
        store.s = session(exp = 1_010)
        val r = repo(); assertNull(r.accessToken())
        assertTrue(r.state.value is AuthState.SignedIn); assertNotNull(store.s)
    }

    @Test fun signOutClearsLocallyEvenIfServerFails() = runTest {
        store.s = session(); api.signOutThrows = true
        val r = repo(); r.signOut()
        assertTrue(r.state.value is AuthState.SignedOut); assertNull(store.s); assertEquals(1, api.signOutCalls)
    }

    @Test fun notConfiguredFailsClearly() = runTest {
        val r = AuthRepository(null, MemStore()) { now }
        assertEquals(AuthError.NotConfigured, (r.signIn("a@b.co", "pw123456").exceptionOrNull() as AuthException).error)
    }
}
