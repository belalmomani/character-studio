package app.characterstudio.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeDao : ProjectDao {
    val items = MutableStateFlow<List<ProjectEntity>>(emptyList())
    override fun observeAll() = items
    override suspend fun getById(id: String) = items.value.firstOrNull { it.id == id }
    override suspend fun insert(project: ProjectEntity) { items.value = items.value + project }
    override suspend fun update(project: ProjectEntity) {
        items.value = items.value.map { if (it.id == project.id) project else it }
    }
    override suspend fun deleteById(id: String) { items.value = items.value.filterNot { it.id == id } }
}

class ProjectRepositoryTest {
    private var time = 1_000L
    private var counter = 0
    private val dao = FakeDao()
    private val repo = ProjectRepository(dao, clock = { time++ }, idGenerator = { "id${counter++}" })

    @Test fun createTrimsNameAndStoresFormatVersion() = runTest {
        val p = repo.create("  بطل  ", ArtStyle.Anime).getOrThrow()
        assertEquals("بطل", p.name)
        assertEquals("anime", p.artStyle)
        assertEquals(PROJECT_FORMAT_VERSION, p.formatVersion)
        assertEquals(1, dao.items.value.size)
    }

    @Test fun createRejectsBlankAndTooLongNames() = runTest {
        assertTrue(repo.create("   ", ArtStyle.Cartoon).isFailure)
        assertTrue(repo.create("x".repeat(MAX_NAME_LENGTH + 1), ArtStyle.Cartoon).isFailure)
        assertTrue(dao.items.value.isEmpty())
    }

    @Test fun renameUpdatesNameAndTimestamp() = runTest {
        val p = repo.create("أ", ArtStyle.Cartoon).getOrThrow()
        repo.rename(p.id, "ب").getOrThrow()
        val updated = dao.getById(p.id)!!
        assertEquals("ب", updated.name)
        assertTrue(updated.updatedAt > p.updatedAt)
        assertEquals(p.createdAt, updated.createdAt)
    }

    @Test fun renameRejectsInvalidNameAndKeepsOldOne() = runTest {
        val p = repo.create("أ", ArtStyle.Cartoon).getOrThrow()
        assertTrue(repo.rename(p.id, " ").isFailure)
        assertEquals("أ", dao.getById(p.id)!!.name)
    }

    @Test fun duplicateCopiesDocumentWithNewId() = runTest {
        val p = repo.create("أ", ArtStyle.Cartoon).getOrThrow()
        val c = repo.duplicate(p.id, "(نسخة)").getOrThrow()
        assertTrue(c.id != p.id)
        assertEquals("أ (نسخة)", c.name)
        assertEquals(p.documentJson, c.documentJson)
        assertEquals(2, dao.items.value.size)
    }

    @Test fun deleteRemovesOnlyTarget() = runTest {
        val a = repo.create("أ", ArtStyle.Cartoon).getOrThrow()
        val b = repo.create("ب", ArtStyle.Cartoon).getOrThrow()
        repo.delete(a.id).getOrThrow()
        assertEquals(listOf(b.id), dao.items.value.map { it.id })
    }

    @Test fun observeFiltersByQueryAndSorts() = runTest {
        repo.create("Zed", ArtStyle.Cartoon)
        repo.create("alpha", ArtStyle.Cartoon)
        assertEquals(listOf("alpha"), repo.observe("ALP", ProjectSort.NameAsc).first().map { it.name })
        assertEquals(listOf("alpha", "Zed"), repo.observe("", ProjectSort.UpdatedDesc).first().map { it.name })
        assertEquals(listOf("alpha", "Zed"), repo.observe("", ProjectSort.NameAsc).first().map { it.name })
    }
}
