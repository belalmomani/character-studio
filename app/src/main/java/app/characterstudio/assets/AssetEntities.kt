package app.characterstudio.assets

import androidx.room.Entity
import androidx.room.PrimaryKey

/** نسخة محلية من بيانات الأصل (الحالية) كما وردت من الخادم. updatedAtUs = ميكروثانية منذ epoch. */
@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey val assetId: String,
    val categoryId: String,
    val nameAr: String,
    val artStyle: String,
    val angle: String,
    val accessLevel: String,
    val pointsCost: Int,
    val status: String,
    val updatedAtUs: Long,
    val versionId: String,
    val version: Int,
    val fileType: String,
    val filePublicId: String,
    val thumbPublicId: String,
    val width: Int,
    val height: Int,
    val fileBytes: Int,
    val sha256: String,
    val anchorsJson: String,
    val compatibilityJson: String,
)

@Entity(tableName = "asset_categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val slug: String,
    val parentId: String?,
    val nameAr: String,
    val sortOrder: Int,
    val active: Boolean,
)

@Entity(tableName = "asset_unlocks")
data class AssetUnlockEntity(@PrimaryKey val assetId: String)

object AssetStatus { const val PUBLISHED = "published" }
object AssetAccess { const val FREE = "free"; const val PAID = "paid" }
