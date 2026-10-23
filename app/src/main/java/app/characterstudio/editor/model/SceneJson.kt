package app.characterstudio.editor.model

import kotlinx.serialization.json.Json

class UnsupportedSceneVersion(val found: Int) : Exception("unsupported_scene_version_$found")

object SceneJson {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(scene: Scene): String = json.encodeToString(Scene.serializer(), scene)

    /** "{}" أو نص فارغ = مشروع جديد لم يُحرَّر بعد. */
    fun decode(text: String): Scene {
        if (text.isBlank() || text.trim() == "{}") return Scene()
        val scene = json.decodeFromString(Scene.serializer(), text)
        if (scene.version > SCENE_VERSION) throw UnsupportedSceneVersion(scene.version)
        return scene // عند رفع SCENE_VERSION لاحقًا تُضاف هنا خطوات الترحيل من الإصدارات الأقدم
    }
}
