package app.characterstudio.editor.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object AssetAnchors {
    private val json = Json { ignoreUnknownKeys = true }

    /** يقرأ نقاط الأصل من JSON الخادم؛ يتجاهل المعطوب ويرجع قائمة فارغة بدل الانهيار. */
    fun parse(text: String): List<AssetAnchor> =
        runCatching { json.decodeFromString(ListSerializer(AssetAnchor.serializer()), text) }
            .getOrDefault(emptyList())
            .filter { it.id.isNotBlank() && it.x.isFinite() && it.y.isFinite() && it.x >= 0f && it.y >= 0f }
            .distinctBy { it.id }
}
