package app.characterstudio.auth

import app.characterstudio.net.Http
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable private data class WalletRow(val balance: Int)
@Serializable private data class UnlockRow(@SerialName("asset_id") val assetId: String)
@Serializable private data class TxRow(
    val delta: Int, @SerialName("balance_after") val balanceAfter: Int, val source: String, val reason: String,
    @SerialName("created_at") val createdAt: String,
)

class SupabaseAccountApi(baseUrl: String, private val anonKey: String) : AccountApi {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }
    private fun h(token: String) = mapOf("apikey" to anonKey, "Authorization" to "Bearer $token", "Accept" to "application/json")

    private suspend fun get(path: String, token: String): String {
        val r = Http.call("GET", base + path, h(token))
        if (!r.ok) throw AccountException(AccountErrors.fromServer(r.code, r.body))
        return r.body
    }

    override suspend fun balance(token: String): Int =
        json.decodeFromString<List<WalletRow>>(get("/rest/v1/user_wallets?select=balance", token)).firstOrNull()?.balance ?: 0

    override suspend fun transactions(token: String, limit: Int): List<PointTx> =
        json.decodeFromString<List<TxRow>>(
            get("/rest/v1/point_transactions?select=delta,balance_after,source,reason,created_at&order=created_at.desc&limit=${limit.coerceIn(1, 200)}", token),
        ).map { PointTx(it.delta, it.balanceAfter, it.source, it.reason, it.createdAt) }

    override suspend fun unlockedIds(token: String): List<String> =
        json.decodeFromString<List<UnlockRow>>(get("/rest/v1/asset_unlocks?select=asset_id", token)).map { it.assetId }

    override suspend fun unlockAsset(token: String, assetId: String): Int {
        val body = buildJsonObject { put("p_asset_id", assetId); put("p_key", "unlock-$assetId") }.toString()
        val r = Http.call("POST", "$base/rest/v1/rpc/unlock_asset", h(token), body)
        if (!r.ok) throw AccountException(AccountErrors.fromServer(r.code, r.body))
        return r.body.trim().toIntOrNull() ?: throw AccountException(AccountError.Unknown)
    }
}
