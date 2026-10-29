package app.characterstudio.auth

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

private class FakeAccountApi : AccountApi {
    var lastToken: String? = null; var lastUnlock: String? = null
    var unlockResult: () -> Int = { 40 }
    override suspend fun balance(token: String): Int { lastToken = token; return 70 }
    override suspend fun transactions(token: String, limit: Int) = emptyList<PointTx>()
    override suspend fun unlockedIds(token: String) = listOf("a1")
    override suspend fun unlockAsset(token: String, assetId: String): Int { lastToken = token; lastUnlock = assetId; return unlockResult() }
}

private class OnlineStore(var s: Session?) : SessionStore {
    override fun load() = s
    override fun save(session: Session) { s = session }
    override fun clear() { s = null }
}

class AccountRepositoryTest {
    private val api = FakeAccountApi()
    private fun signedIn() = AccountRepository(
        AuthRepository(null, OnlineStore(Session("TOK", "R", 9_999_999_999, "u", "e"))) { 1L }, api)
    private fun signedOut() = AccountRepository(AuthRepository(null, OnlineStore(null)) { 1L }, api)
    private fun err(r: Result<*>) = (r.exceptionOrNull() as AccountException).error

    @Test fun signedOutCannotCallAnything() = runTest {
        val a = signedOut()
        assertEquals(AccountError.NotSignedIn, err(a.balance()))
        assertEquals(AccountError.NotSignedIn, err(a.unlock("x")))
        assertNull(api.lastUnlock)
    }

    @Test fun notConfiguredIsReported() = runTest {
        val a = AccountRepository(AuthRepository(null, OnlineStore(null)) { 1L }, null)
        assertEquals(AccountError.NotConfigured, err(a.balance()))
    }

    @Test fun usesCurrentTokenAndReturnsServerBalance() = runTest {
        val a = signedIn()
        assertEquals(70, a.balance().getOrThrow()); assertEquals("TOK", api.lastToken)
        assertEquals(40, a.unlock("asset-9").getOrThrow()); assertEquals("asset-9", api.lastUnlock)
        assertEquals(listOf("a1"), a.unlockedIds().getOrThrow())
    }

    @Test fun serverErrorsKeepTheirMeaning() = runTest {
        api.unlockResult = { throw AccountException(AccountError.InsufficientPoints) }
        assertEquals(AccountError.InsufficientPoints, err(signedIn().unlock("x")))
    }

    @Test fun ioFailureBecomesNetworkError() = runTest {
        api.unlockResult = { throw IOException("offline") }
        assertEquals(AccountError.Network, err(signedIn().unlock("x")))
    }

    @Test fun serverBodiesMapToErrors() {
        assertEquals(AccountError.InsufficientPoints, AccountErrors.fromServer(400, """{"code":"P0001","message":"insufficient_points"}"""))
        assertEquals(AccountError.NotPurchasable, AccountErrors.fromServer(400, """{"message":"asset_not_purchasable"}"""))
        assertEquals(AccountError.NotSignedIn, AccountErrors.fromServer(401, "{}"))
        assertEquals(AccountError.NotSignedIn, AccountErrors.fromServer(400, """{"message":"not_authenticated"}"""))
        assertEquals(AccountError.Unknown, AccountErrors.fromServer(500, "x"))
    }
}
