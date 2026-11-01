package app.characterstudio.sync

import app.characterstudio.data.ProjectEntity

/** حالة آخر مزامنة ناجحة لمشروع: نسخة السحابة التي عرفناها، ووقت تعديله المحلي حينها. */
data class SyncState(val projectId: String, val cloudRevision: Int, val syncedUpdatedAt: Long)

data class CloudMeta(
    val id: String, val name: String, val artStyle: String, val formatVersion: Int, val revision: Int, val updatedAtMs: Long,
)
data class CloudProject(val meta: CloudMeta, val documentJson: String, val createdAtMs: Long)

data class SyncCounts(
    val uploaded: Int = 0, val downloaded: Int = 0, val conflicts: Int = 0, val deletedInCloud: Int = 0, val failed: Int = 0,
)

interface ProjectStore {
    suspend fun all(): List<ProjectEntity>
    suspend fun get(id: String): ProjectEntity?
    suspend fun insert(p: ProjectEntity)
    suspend fun overwrite(p: ProjectEntity)
}

/** حالات المزامنة للمستخدم الحالي فقط (التنفيذ يربطها بحساب واحد). */
interface SyncStateStore {
    suspend fun all(): List<SyncState>
    suspend fun put(s: SyncState)
    suspend fun remove(projectId: String)
}

interface CloudProjects {
    suspend fun list(): List<CloudMeta>
    suspend fun fetch(id: String): CloudProject?
    /** expectedRevision = 0 → إنشاء جديد. يرجع null عند تعارض النسخة (تغيّر المشروع في السحابة). */
    suspend fun push(p: ProjectEntity, expectedRevision: Int): CloudMeta?
    suspend fun delete(id: String)
}
