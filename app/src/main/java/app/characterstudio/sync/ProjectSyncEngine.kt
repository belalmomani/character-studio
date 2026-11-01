package app.characterstudio.sync

import app.characterstudio.data.ProjectEntity
import java.io.IOException
import java.util.UUID

/**
 * مزامنة ثنائية الاتجاه بلا فقدان بيانات:
 *  - تعديل محلي فقط  → رفع (بشرط أن تكون نسخة السحابة هي التي نعرفها).
 *  - تعديل سحابي فقط → تنزيل فوق المحلي.
 *  - تعديل على الجهتين → نحتفظ بنسخة السحابة كمشروع محلي مستقل، ونرفع المحلي (لا يضيع أي منهما).
 *  - حُذف محليًا بعد مزامنته → يُحذف من السحابة. حُذف من السحابة → نعيد رفع المحلي (لا نحذف محليًا تلقائيًا).
 * أخطاء الشبكة (IOException) توقف المزامنة وتُرمى؛ ما تم قبلها محفوظ ولا يتكرر.
 */
class ProjectSyncEngine(
    private val local: ProjectStore,
    private val states: SyncStateStore,
    private val cloud: CloudProjects,
    private val copySuffix: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGen: () -> String = { UUID.randomUUID().toString() },
    /** بعد تنزيل مشروع: لاستعادة ملفات الأصول إن أمكن. */
    private val onPulled: suspend (ProjectEntity) -> Unit = {},
) {
    suspend fun sync(): SyncCounts {
        var up = 0; var down = 0; var conflicts = 0; var deleted = 0; var failed = 0
        val metas = cloud.list().associateBy { it.id }
        val locals = local.all()
        val st = states.all().associateBy { it.projectId }
        val localIds = locals.map { it.id }.toSet()

        suspend fun push(p: ProjectEntity, expected: Int): Boolean {
            val meta = cloud.push(p, expected) ?: return false
            states.put(SyncState(p.id, meta.revision, p.updatedAt))
            return true
        }

        suspend fun pull(id: String): Boolean {
            val cp = cloud.fetch(id) ?: return false
            val existing = local.get(id)
            val entity = ProjectEntity(
                id = id, name = cp.meta.name, artStyle = cp.meta.artStyle, formatVersion = cp.meta.formatVersion,
                documentJson = cp.documentJson, createdAt = existing?.createdAt ?: cp.createdAtMs, updatedAt = cp.meta.updatedAtMs,
            )
            if (existing == null) local.insert(entity) else local.overwrite(entity)
            states.put(SyncState(id, cp.meta.revision, entity.updatedAt))
            onPulled(entity)
            return true
        }

        suspend fun resolveConflict(p: ProjectEntity, c: CloudMeta) {
            val cp = cloud.fetch(p.id)
            if (cp != null) {
                val now = clock()
                local.insert(ProjectEntity(
                    id = idGen(), name = "${cp.meta.name} $copySuffix".take(60), artStyle = cp.meta.artStyle,
                    formatVersion = cp.meta.formatVersion, documentJson = cp.documentJson, createdAt = now, updatedAt = now,
                ))
            }
            conflicts++
            // لو تحرّكت السحابة مرة أخرى أثناء ذلك يبقى المحلي سليمًا ويُعاد في المزامنة التالية
            if (push(p, cp?.meta?.revision ?: c.revision)) up++ else failed++
        }

        for (p in locals) {
            try {
                val c = metas[p.id]
                val s = st[p.id]
                when {
                    c == null -> if (push(p, 0)) up++ else failed++
                    s == null -> resolveConflict(p, c)
                    else -> {
                        val dirty = p.updatedAt != s.syncedUpdatedAt
                        val changed = c.revision != s.cloudRevision
                        when {
                            !dirty && !changed -> Unit
                            dirty && !changed -> if (push(p, s.cloudRevision)) up++ else resolveConflict(p, c)
                            !dirty && changed -> if (pull(c.id)) down++ else failed++
                            else -> resolveConflict(p, c)
                        }
                    }
                }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                failed++
            }
        }

        for (c in metas.values) {
            if (c.id in localIds) continue
            try {
                if (st.containsKey(c.id)) {   // كان مزامَنًا ثم حُذف محليًا
                    cloud.delete(c.id); states.remove(c.id); deleted++
                } else {
                    if (pull(c.id)) down++ else failed++
                }
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                failed++
            }
        }

        for (s in st.values) if (s.projectId !in localIds && s.projectId !in metas) states.remove(s.projectId)
        return SyncCounts(up, down, conflicts, deleted, failed)
    }
}
