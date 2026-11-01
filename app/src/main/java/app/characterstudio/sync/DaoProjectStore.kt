package app.characterstudio.sync

import app.characterstudio.data.ProjectDao
import app.characterstudio.data.ProjectEntity

class DaoProjectStore(private val dao: ProjectDao) : ProjectStore {
    override suspend fun all() = dao.getAll()
    override suspend fun get(id: String) = dao.getById(id)
    override suspend fun insert(p: ProjectEntity) = dao.insert(p)
    override suspend fun overwrite(p: ProjectEntity) = dao.update(p)
}
