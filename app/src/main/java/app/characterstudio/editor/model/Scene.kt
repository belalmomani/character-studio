package app.characterstudio.editor.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val SCENE_VERSION = 1

@Serializable
enum class LayerKind {
    @SerialName("ellipse") Ellipse,
    @SerialName("rect") Rect,
}

/** x,y = مركز العنصر بوحدات اللوحة؛ rotation بالدرجات. */
@Serializable
data class Transform(val x: Float, val y: Float, val width: Float, val height: Float, val rotation: Float = 0f)

@Serializable
data class Layer(
    val id: String,
    val name: String,
    val kind: LayerKind,
    val assetId: String? = null,
    val assetVersion: Int? = null,
    val transform: Transform,
    val home: Transform,
    val opacity: Float = 1f,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val colorArgb: Long = 0xFF3F5BA9,
    val attachedAnchorId: String? = null,
)

@Serializable
data class Anchor(val id: String, val label: String, val x: Float, val y: Float)

/** ترتيب القائمة = ترتيب الرسم: الفهرس 0 في الخلف. */
@Serializable
data class Scene(
    val version: Int = SCENE_VERSION,
    val canvasWidth: Int = 1024,
    val canvasHeight: Int = 1024,
    val layers: List<Layer> = emptyList(),
    val anchors: List<Anchor> = DefaultSkeleton.anchors(),
    val showSkeleton: Boolean = true,
    val showAnchors: Boolean = true,
    val backgroundArgb: Long? = null,
)
