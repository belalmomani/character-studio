package app.characterstudio.auth

import java.io.IOException

data class PointTx(val delta: Int, val balanceAfter: Int, val source: String, val reason: String, val createdAt: String)

enum class AccountError { NotSignedIn, InsufficientPoints, NotPurchasable, Network, NotConfigured, Unknown }
class AccountException(val error: AccountError) : Exception(error.name)

interface AccountApi {
    suspend fun balance(token: String): Int
    suspend fun transactions(token: String, limit: Int = 50): List<PointTx>
    suspend fun unlockedIds(token: String): List<String>
    /** يرجع الرصيد الجديد. التكرار آمن (مفتاح ثابت لكل أصل، والخادم لا يخصم مرتين). */
    suspend fun unlockAsset(token: String, assetId: String): Int
}

object AccountErrors {
    /** يحوّل نص خطأ PostgREST/Postgres إلى خطأ مفهوم. */
    fun fromServer(httpCode: Int, body: String): AccountError = when {
        httpCode == 401 || httpCode == 403 -> AccountError.NotSignedIn
        "insufficient_points" in body -> AccountError.InsufficientPoints
        "asset_not_purchasable" in body -> AccountError.NotPurchasable
        "not_authenticated" in body -> AccountError.NotSignedIn
        else -> AccountError.Unknown
    }
}

/** الرصيد والسجل وفتح الأصول المدفوعة: كلها تمر بالخادم؛ لا شيء يُعدَّل محليًا. */
class AccountRepository(private val auth: AuthRepository, private val api: AccountApi?) {
    val state get() = auth.state

    private suspend fun <T> withToken(block: suspend (AccountApi, String) -> T): Result<T> {
        val a = api ?: return Result.failure(AccountException(AccountError.NotConfigured))
        val token = auth.accessToken() ?: return Result.failure(AccountException(AccountError.NotSignedIn))
        return runCatching { block(a, token) }.recoverCatching { e ->
            throw when (e) { is AccountException -> e; is IOException -> AccountException(AccountError.Network); else -> AccountException(AccountError.Unknown) }
        }
    }

    suspend fun balance(): Result<Int> = withToken { a, t -> a.balance(t) }
    suspend fun transactions(): Result<List<PointTx>> = withToken { a, t -> a.transactions(t) }
    suspend fun unlockedIds(): Result<List<String>> = withToken { a, t -> a.unlockedIds(t) }
    suspend fun unlock(assetId: String): Result<Int> = withToken { a, t -> a.unlockAsset(t, assetId) }
}
