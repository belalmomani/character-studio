package app.characterstudio.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** مشروع محلي. documentJson يحمل المشهد (الطبقات ونقاط التثبيت) ويُملأ في مرحلة المحرر. */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val artStyle: String,
    val formatVersion: Int,
    val documentJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class ArtStyle(val key: String) {
    Realistic2d("realistic_2d"),
    Cartoon("cartoon"),
    Anime("anime"),
    SimpleGame("simple_game");

    companion object {
        fun fromKey(key: String): ArtStyle = entries.firstOrNull { it.key == key } ?: Cartoon
    }
}

enum class ProjectSort { UpdatedDesc, NameAsc }
