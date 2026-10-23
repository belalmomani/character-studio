package app.characterstudio.data

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

const val PROJECT_FORMAT_VERSION = 1
const val MAX_NAME_LENGTH = 60

class ProjectRepository(
    private val dao: ProjectDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    fun observe(query: String, sort: ProjectSort): Flow<List<ProjectEntity>> =
        dao.observeAll().map { list ->
            val q = query.trim()
            val filtered = if (q.isEmpty()) list else list.filter { it.name.contains(q, ignoreCase = true) }
            when (sort) {
                ProjectSort.UpdatedDesc -> filtered.sortedByDescending { it.updatedAt }
                ProjectSort.NameAsc -> filtered.sortedBy { it.name.lowercase() }
            }
        }

    suspend fun create(name: String, style: ArtStyle): Result<ProjectEntity> = runCatching {
        val now = clock()
        val project = ProjectEntity(
            id = idGenerator(),
            name = validateName(name),
            artStyle = style.key,
            formatVersion = PROJECT_FORMAT_VERSION,
            documentJson = "{}",
            createdAt = now,
            updatedAt = now,
        )
        dao.insert(project)
        project
    }

    suspend fun rename(id: String, newName: String): Result<Unit> = runCatching {
        val valid = validateName(newName)
        val current = dao.getById(id) ?: error("project_not_found")
        dao.update(current.copy(name = valid, updatedAt = clock()))
    }

    suspend fun duplicate(id: String, suffix: String): Result<ProjectEntity> = runCatching {
        val current = dao.getById(id) ?: error("project_not_found")
        val now = clock()
        val copy = current.copy(
            id = idGenerator(),
            name = "${current.name} $suffix".take(MAX_NAME_LENGTH),
            createdAt = now,
            updatedAt = now,
        )
        dao.insert(copy)
        copy
    }

    suspend fun get(id: String): ProjectEntity? = dao.getById(id)

    /** يحفظ مستند المشهد؛ يرفع updatedAt ويثبّت formatVersion الحالي. */
    suspend fun saveDocument(id: String, documentJson: String): Result<Unit> = runCatching {
        val current = dao.getById(id) ?: error("project_not_found")
        dao.update(current.copy(documentJson = documentJson, formatVersion = PROJECT_FORMAT_VERSION, updatedAt = clock()))
    }

    suspend fun delete(id: String): Result<Unit> = runCatching { dao.deleteById(id) }

    private fun validateName(raw: String): String {
        val name = raw.trim()
        require(name.isNotEmpty() && name.length <= MAX_NAME_LENGTH) { "invalid_name" }
        return name
    }
}
