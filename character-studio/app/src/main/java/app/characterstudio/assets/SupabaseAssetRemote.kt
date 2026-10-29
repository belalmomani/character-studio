package app.characterstudio.assets

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class CategoryDto(
    val id: String,
    val slug: String,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("name_ar") val nameAr: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val active: Boolean = true,
)

@Serializable
private data class AssetRow(
    @SerialName("asset_id") val assetId: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("name_ar") val nameAr: String,
    @SerialName("art_style") val artStyle: String,
    val angle: String,
    @SerialName("access_level") val accessLevel: String,
    @SerialName("points_cost") val pointsCost: Int,
    val status: String,
    @SerialName("updated_at_us") val updatedAtUs: Long,
    @SerialName("version_id") val versionId: String,
    val version: Int,
    @SerialName("file_type") val fileType: String,
    @SerialName("file_public_id") val filePublicId: String,
    @SerialName("thumb_public_id") val thumbPublicId: String,
    val width: Int,
    val height: Int,
    @SerialName("file_bytes") val fileBytes: Int,
    val sha256: String,
    val anchors: JsonElement,
    val compatibility: JsonElement,
)

/**
 * مصدر Supabase عبر REST بالمفتاح العام (anon) فقط. القراءة العامة مسموحة لما هو منشور (انظر 0007).
 * الأصول المفتوحة بالنقاط تحتاج حسابًا، لذلك fetchUnlockedIds يرجع قائمة فارغة حتى مرحلة الحسابات.
 */
class SupabaseAssetRemote(
    baseUrl: String,
    private val anonKey: String,
    /** رمز المستخدم إن كان مسجَّلًا (لقراءة أصوله المفتوحة)، وإلا null. */
    private val tokenProvider: suspend () -> String? = { null },
) : AssetRemoteSource {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchCategories(): List<CategoryEntity> {
        val body = request("GET", "/rest/v1/asset_categories?select=id,slug,parent_id,name_ar,sort_order,active&active=eq.true", null)
        return json.decodeFromString<List<CategoryDto>>(body).map {
            CategoryEntity(it.id, it.slug, it.parentId, it.nameAr, it.sortOrder, it.active)
        }
    }

    override suspend fun fetchAssetsSince(sinceUs: Long, afterId: String?, appVersion: Int): List<AssetEntity> {
        val payload = buildJsonObject {
            put("p_since_us", sinceUs)
            put("p_after_id", if (afterId == null) JsonNull else JsonPrimitive(afterId))
            put("p_app_version", appVersion)
        }.toString()
        val body = request("POST", "/rest/v1/rpc/list_assets_since", payload)
        return json.decodeFromString<List<AssetRow>>(body).map {
            AssetEntity(
                assetId = it.assetId, categoryId = it.categoryId, nameAr = it.nameAr, artStyle = it.artStyle, angle = it.angle,
                accessLevel = it.accessLevel, pointsCost = it.pointsCost, status = it.status, updatedAtUs = it.updatedAtUs,
                versionId = it.versionId, version = it.version, fileType = it.fileType, filePublicId = it.filePublicId,
                thumbPublicId = it.thumbPublicId, width = it.width, height = it.height, fileBytes = it.fileBytes,
                sha256 = it.sha256, anchorsJson = it.anchors.toString(), compatibilityJson = it.compatibility.toString(),
            )
        }
    }

    @Serializable private data class UnlockRow(@SerialName("asset_id") val assetId: String)

    override suspend fun fetchUnlockedIds(): List<String> {
        val token = tokenProvider() ?: return emptyList()
        val body = request("GET", "/rest/v1/asset_unlocks?select=asset_id", null, token)
        return json.decodeFromString<List<UnlockRow>>(body).map { it.assetId }
    }

    private suspend fun request(method: String, path: String, body: String?, userToken: String? = null): String = withContext(Dispatchers.IO) {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("apikey", anonKey)
            conn.setRequestProperty("Authorization", "Bearer ${userToken ?: anonKey}")
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("http_$code")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
