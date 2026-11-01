package app.characterstudio.sync

import app.characterstudio.data.ProjectEntity
import app.characterstudio.net.Http
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable
private data class ProjectRow(
    val id: String,
    val name: String,
    @SerialName("art_style") val artStyle: String,
    @SerialName("format_version") val formatVersion: Int,
    val revision: Int,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("created_at") val createdAt: String? = null,
    val document: JsonElement? = null,
)

/** مشاريع المستخدم في Supabase بحماية RLS (المالك فقط). الكتابة عبر الدالة sync_push_project بتحقق من رقم النسخة. */
class SupabaseCloudProjects(
    baseUrl: String,
    private val anonKey: String,
    private val tokenProvider: suspend () -> String?,
) : CloudProjects {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun call(method: String, path: String, body: String? = null): String {
        val token = tokenProvider() ?: throw IllegalStateException("not_signed_in")
        val r = Http.call(method, base + path,
            mapOf("apikey" to anonKey, "Authorization" to "Bearer $token", "Accept" to "application/json"), body)
        if (r.code in 500..599) throw IOException("server_${r.code}")
        if (!r.ok) throw IllegalStateException("http_${r.code}")
        return r.body
    }

    private fun ms(iso: String): Long = OffsetDateTime.parse(iso).toInstant().toEpochMilli()
    private fun meta(r: ProjectRow) = CloudMeta(r.id, r.name, r.artStyle, r.formatVersion, r.revision, ms(r.updatedAt))

    override suspend fun list(): List<CloudMeta> =
        json.decodeFromString<List<ProjectRow>>(
            call("GET", "/rest/v1/projects?select=id,name,art_style,format_version,revision,updated_at&order=updated_at.desc&limit=1000"),
        ).map(::meta)

    override suspend fun fetch(id: String): CloudProject? {
        val rows = json.decodeFromString<List<ProjectRow>>(
            call("GET", "/rest/v1/projects?select=id,name,art_style,format_version,revision,updated_at,created_at,document&id=eq.$id"),
        )
        val r = rows.firstOrNull() ?: return null
        return CloudProject(meta(r), (r.document ?: JsonObject(emptyMap())).toString(), r.createdAt?.let(::ms) ?: 0L)
    }

    override suspend fun push(p: ProjectEntity, expectedRevision: Int): CloudMeta? {
        val doc = runCatching { json.parseToJsonElement(p.documentJson) }.getOrElse { JsonObject(emptyMap()) }
        val body = buildJsonObject {
            put("p_id", p.id); put("p_name", p.name); put("p_art_style", p.artStyle); put("p_format_version", p.formatVersion)
            put("p_document", doc)
            put("p_created_at", Instant.ofEpochMilli(p.createdAt).toString())
            put("p_updated_at", Instant.ofEpochMilli(p.updatedAt).toString())
            put("p_expected_revision", expectedRevision)
        }.toString()
        val res = json.parseToJsonElement(call("POST", "/rest/v1/rpc/sync_push_project", body)).jsonObject
        if (res["ok"]?.jsonPrimitive?.boolean != true) return null
        val rev = res["revision"]?.jsonPrimitive?.int ?: return null
        return CloudMeta(p.id, p.name, p.artStyle, p.formatVersion, rev, p.updatedAt)
    }

    override suspend fun delete(id: String) { call("DELETE", "/rest/v1/projects?id=eq.$id") }
}
