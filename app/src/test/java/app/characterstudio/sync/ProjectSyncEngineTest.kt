package app.characterstudio.sync

import app.characterstudio.data.ProjectEntity
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

private fun proj(id: String, name: String = id, updated: Long = 100, doc: String = "{}") =
    ProjectEntity(id, name, "cartoon", 1, doc, 50, updated)

private class MemLocal(vararg p: ProjectEntity) : ProjectStore {
    val items = p.associateBy { it.id }.toMutableMap()
    override suspend fun all() = items.values.toList()
    override suspend fun get(id: String) = items[id]
    override suspend fun insert(p: ProjectEntity) { check(p.id !in items); items[p.id] = p }
    override suspend fun overwrite(p: ProjectEntity) { items[p.id] = p }
}

private class MemStates(vararg s: SyncState) : SyncStateStore {
    val items = s.associateBy { it.projectId }.toMutableMap()
    override suspend fun all() = items.values.toList()
    override suspend fun put(s: SyncState) { items[s.projectId] = s }
    override suspend fun remove(projectId: String) { items.remove(projectId) }
}

private class MemCloud : CloudProjects {
    class Row(var name: String, var doc: String, var revision: Int, var updatedAt: Long)
    val rows = mutableMapOf<String, Row>()
    var pushCalls = 0; var failNetwork = false; var forceConflictOnce = false
    var pushedExpected = mutableListOf<Int>()
    private fun meta(id: String, r: Row) = CloudMeta(id, r.name, "cartoon", 1, r.revision, r.updatedAt)
    override suspend fun list(): List<CloudMeta> { if (failNetwork) throw IOException("offline"); return rows.map { meta(it.key, it.value) } }
    override suspend fun fetch(id: String) = rows[id]?.let { CloudProject(meta(id, it), it.doc, 10) }
    override suspend fun push(p: ProjectEntity, expectedRevision: Int): CloudMeta? {
        if (failNetwork) throw IOException("offline")
        pushCalls++; pushedExpected += expectedRevision
        if (forceConflictOnce) { forceConflictOnce = false; rows[p.id]?.let { it.revision++ }; return null }
        val r = rows[p.id]
        if (expectedRevision == 0) { check(r == null); rows[p.id] = Row(p.name, p.documentJson, 1, p.updatedAt); return meta(p.id, rows[p.id]!!) }
        if (r == null || r.revision != expectedRevision) return null
        r.name = p.name; r.doc = p.documentJson; r.revision++; r.updatedAt = p.updatedAt
        return meta(p.id, r)
    }
    override suspend fun delete(id: String) { rows.remove(id) }
}

class ProjectSyncEngineTest {
    private var n = 0
    private fun engine(l: MemLocal, s: MemStates, c: MemCloud, pulled: MutableList<String> = mutableListOf()) =
        ProjectSyncEngine(l, s, c, "(من السحابة)", clock = { 9_000 }, idGen = { "copy${n++}" }, onPulled = { pulled += it.id })

    @Test fun newLocalProjectIsUploadedAndRecorded() = runTest {
        val l = MemLocal(proj("a", updated = 123)); val s = MemStates(); val c = MemCloud()
        val r = engine(l, s, c).sync()
        assertEquals(1, r.uploaded); assertEquals(0, r.failed)
        assertEquals(1, c.rows["a"]!!.revision)
        assertEquals(SyncState("a", 1, 123), s.items["a"])
    }

    @Test fun nothingChangedMeansNoUploads() = runTest {
        val l = MemLocal(proj("a", updated = 100)); val c = MemCloud(); val s = MemStates()
        engine(l, s, c).sync(); c.pushCalls = 0
        val r = engine(l, s, c).sync()
        assertEquals(SyncCounts(), r); assertEquals(0, c.pushCalls)
    }

    @Test fun localEditIsPushedWithExpectedRevision() = runTest {
        val l = MemLocal(proj("a", updated = 100)); val c = MemCloud(); val s = MemStates()
        engine(l, s, c).sync()
        l.items["a"] = proj("a", name = "جديد", updated = 200)
        c.pushedExpected.clear()
        val r = engine(l, s, c).sync()
        assertEquals(1, r.uploaded); assertEquals(listOf(1), c.pushedExpected)
        assertEquals(2, c.rows["a"]!!.revision); assertEquals("جديد", c.rows["a"]!!.name)
        assertEquals(SyncState("a", 2, 200), s.items["a"])
    }

    @Test fun cloudChangeOverwritesUntouchedLocal() = runTest {
        val l = MemLocal(proj("a", updated = 100)); val c = MemCloud(); val s = MemStates()
        engine(l, s, c).sync()
        c.rows["a"]!!.apply { name = "من جهاز آخر"; doc = "{\"x\":1}"; revision = 2; updatedAt = 500 }
        val pulled = mutableListOf<String>()
        val r = engine(l, s, c, pulled).sync()
        assertEquals(1, r.downloaded); assertEquals(listOf("a"), pulled)
        assertEquals("من جهاز آخر", l.items["a"]!!.name); assertEquals("{\"x\":1}", l.items["a"]!!.documentJson)
        assertEquals(500L, l.items["a"]!!.updatedAt); assertEquals(SyncState("a", 2, 500), s.items["a"])
        assertEquals(50L, l.items["a"]!!.createdAt) // تاريخ الإنشاء المحلي يبقى
    }

    @Test fun editsOnBothSidesKeepBothVersions() = runTest {
        val l = MemLocal(proj("a", updated = 100)); val c = MemCloud(); val s = MemStates()
        engine(l, s, c).sync()
        l.items["a"] = proj("a", name = "محلي", updated = 300, doc = "{\"local\":1}")
        c.rows["a"]!!.apply { name = "سحابي"; doc = "{\"cloud\":1}"; revision = 2; updatedAt = 400 }
        val r = engine(l, s, c).sync()
        assertEquals(1, r.conflicts)
        // نسخة السحابة محفوظة محليًا كمشروع مستقل
        val copy = l.items["copy0"]!!
        assertEquals("سحابي (من السحابة)", copy.name); assertEquals("{\"cloud\":1}", copy.documentJson)
        // والمحلي بقي كما هو ورُفع فوق السحابة
        assertEquals("{\"local\":1}", l.items["a"]!!.documentJson)
        assertEquals("{\"local\":1}", c.rows["a"]!!.doc); assertEquals(3, c.rows["a"]!!.revision)
    }

    @Test fun pushRaceFallsBackToConflictHandlingWithoutLoss() = runTest {
        val l = MemLocal(proj("a", updated = 100)); val c = MemCloud(); val s = MemStates()
        engine(l, s, c).sync()
        l.items["a"] = proj("a", name = "محلي", updated = 300)
        c.forceConflictOnce = true   // السحابة تحرّكت بين القائمة والرفع
        val r = engine(l, s, c).sync()
        assertEquals(1, r.conflicts); assertEquals("محلي", l.items["a"]!!.name)
        assertEquals("محلي", c.rows["a"]!!.name)
    }

    @Test fun cloudOnlyProjectIsDownloaded() = runTest {
        val l = MemLocal(); val c = MemCloud(); val s = MemStates()
        c.rows["z"] = MemCloud.Row("من جهاز آخر", "{\"d\":1}", 4, 777)
        val r = engine(l, s, c).sync()
        assertEquals(1, r.downloaded)
        assertEquals("من جهاز آخر", l.items["z"]!!.name); assertEquals(SyncState("z", 4, 777), s.items["z"])
    }

    @Test fun projectDeletedLocallyAfterSyncIsDeletedInCloud() = runTest {
        val l = MemLocal(proj("a")); val c = MemCloud(); val s = MemStates()
        engine(l, s, c).sync()
        l.items.remove("a")
        val r = engine(l, s, c).sync()
        assertEquals(1, r.deletedInCloud); assertTrue(c.rows.isEmpty()); assertTrue(s.items.isEmpty())
    }

    @Test fun projectDeletedInCloudIsReuploadedNotDeletedLocally() = runTest {
        val l = MemLocal(proj("a", updated = 100)); val c = MemCloud(); val s = MemStates()
        engine(l, s, c).sync()
        c.rows.clear()
        val r = engine(l, s, c).sync()
        assertTrue(l.items.containsKey("a")); assertEquals(1, r.uploaded); assertTrue(c.rows.containsKey("a"))
    }

    @Test fun otherUsersStateDoesNotLeakIn() = runTest {
        // حالة مزامنة لحساب آخر لا تصل للمحرك (المخزن يعيد حالات الحساب الحالي فقط) → يُعامل كمشروع جديد
        val l = MemLocal(proj("a")); val c = MemCloud(); val s = MemStates()
        val r = engine(l, s, c).sync()
        assertEquals(1, r.uploaded)
    }

    @Test fun networkFailureAbortsAndKeepsEarlierProgress() = runTest {
        val l = MemLocal(proj("a"), proj("b")); val s = MemStates(); val c = MemCloud()
        engine(MemLocal(proj("a")), s, c).sync()   // a تمت مزامنته
        c.failNetwork = true
        val res = runCatching { engine(l, s, c).sync() }
        assertTrue(res.exceptionOrNull() is IOException)
        assertTrue(l.items.keys == setOf("a", "b"))   // لا شيء حُذف محليًا
        assertNotNull(s.items["a"])
    }

    @Test fun staleStateWithoutProjectIsCleaned() = runTest {
        val l = MemLocal(); val c = MemCloud(); val s = MemStates(SyncState("gone", 3, 1))
        engine(l, s, c).sync()
        assertTrue(s.items.isEmpty())
    }

    @Test fun noBaselineWithBothSidesPresentCreatesCopyNotOverwrite() = runTest {
        // مثلًا بعد مسح قاعدة حالات المزامنة: لا نخمّن، نحفظ النسختين
        val l = MemLocal(proj("a", name = "محلي", updated = 100)); val c = MemCloud(); val s = MemStates()
        c.rows["a"] = MemCloud.Row("سحابي", "{\"c\":1}", 2, 200)
        val r = engine(l, s, c).sync()
        assertEquals(1, r.conflicts); assertEquals("محلي", l.items["a"]!!.name)
        assertEquals("سحابي (من السحابة)", l.items["copy0"]!!.name)
    }
}
