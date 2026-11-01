package app.characterstudio.sync

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

/** حالات المزامنة لكل (حساب، مشروع). قاعدة مستقلة حتى لا نمسّ قاعدة المشاريع ولا نحتاج ترحيلًا لها. */
@Entity(tableName = "sync_state", primaryKeys = ["userId", "projectId"])
data class SyncStateEntity(val userId: String, val projectId: String, val cloudRevision: Int, val syncedUpdatedAt: Long)

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE userId = :userId")
    suspend fun all(userId: String): List<SyncStateEntity>

    @Upsert
    suspend fun put(s: SyncStateEntity)

    @Query("DELETE FROM sync_state WHERE userId = :userId AND projectId = :projectId")
    suspend fun remove(userId: String, projectId: String)
}

// فقدان هذه القاعدة آمن: تُعامل المشاريع كغير مزامَنة، فنحتفظ بالنسختين عند التعارض بدل الكتابة فوق أي منهما.
@Database(entities = [SyncStateEntity::class], version = 1, exportSchema = true)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun dao(): SyncStateDao
}

class RoomSyncStateStore(private val dao: SyncStateDao, private val userId: String) : SyncStateStore {
    override suspend fun all() = dao.all(userId).map { SyncState(it.projectId, it.cloudRevision, it.syncedUpdatedAt) }
    override suspend fun put(s: SyncState) = dao.put(SyncStateEntity(userId, s.projectId, s.cloudRevision, s.syncedUpdatedAt))
    override suspend fun remove(projectId: String) = dao.remove(userId, projectId)
}
