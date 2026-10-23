package app.characterstudio.data

import androidx.room.Database
import androidx.room.RoomDatabase

// لا نستخدم fallbackToDestructiveMigration أبدًا: حذف مشاريع المستخدم عند التحديث ممنوع.
// كل تغيير في المخطط يتطلب رفع version وكتابة Migration.
@Database(entities = [ProjectEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
