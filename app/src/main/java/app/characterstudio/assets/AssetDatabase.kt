package app.characterstudio.assets

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * قاعدة منفصلة عن المشاريع عمدًا: هذه ذاكرة مؤقتة قابلة لإعادة المزامنة من الخادم،
 * فيجوز مسحها عند تغيّر المخطط. قاعدة المشاريع لا تُمسح أبدًا.
 */
@Database(
    entities = [AssetEntity::class, CategoryEntity::class, AssetUnlockEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AssetDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
}
