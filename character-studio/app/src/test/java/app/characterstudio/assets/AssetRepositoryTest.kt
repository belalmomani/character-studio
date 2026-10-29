package app.characterstudio.assets

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun asset(
    id: String, name: String = id, cat: String = "c1", style: String = "cartoon",
    status: String = "published", us: Long = 1_000L, access: String = "free",
) = AssetEntity(
    assetId = id, categoryId = cat, nameAr = name, artStyle = style, angle = "front", accessLevel = access,
    pointsCost = if (access == "paid") 10 else 0, status = status, updatedAtUs = us, versionId = "v-$id", version = 1,
    fileType = "png", filePublicId = "f/$id", thumbPublicId = "t/$id", width = 512, height = 512, fileBytes = 1000,
    sha256 = "0".repeat(64), anchorsJson = "[]", compatibilityJson = "{}",
)

private fun cat(id: String, parent: String? = null, active: Boolean = true, order: Int = 0) =
    CategoryEntity(id, id, parent, "فئة $id", order, active)

private class FakeAssetDao : AssetDao {
    val assets = MutableStateFlow<List<AssetEntity>>(emptyList())
    val cats = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val unlocked = MutableStateFlow<List<String>>(emptyList())
    override fun observeAll() = assets
    override suspend fun get(id: String) = assets.value.firstOrNull { it.assetId == id }
    override suspend fun latest() = assets.value.maxWithOrNull(compareBy<AssetEntity>({ it.updatedAtUs }, { it.assetId }))
    override suspend fun upsertAssets(items: List<AssetEntity>) {
        val ids = items.map { it.assetId }.toSet()
        assets.value = assets.value.filterNot { it.assetId in ids } + items
    }
    override fun observeCategories() = cats
    override suspend fun upsertCategories(items: List<CategoryEntity>) {
        val ids = items.map { it.id }.toSet()
        cats.value = cats.value.filterNot { it.id in ids } + items
    }
    override fun observeUnlockedIds() = unlocked
    override suspend fun upsertUnlocked(items: List<AssetUnlockEntity>) {
        unlocked.value = (unlocked.value + items.map { it.assetId }).distinct()
    }
    override suspend fun deleteUnlockedNotIn(keep: List<String>) { unlocked.value = unlocked.value.filter { it in keep } }
}

/** يحاكي سلوك list_assets_since على الخادم: ترقيم مفتاحي بـ (updatedAtUs, assetId) وحد 500. */
private class FakeRemote(var data: List<AssetEntity>, var categories: List<CategoryEntity> = emptyList(),
                         var unlockedIds: List<String> = emptyList()) : AssetRemoteSource {
    var calls = 0
    var stuck = false
    override suspend fun fetchCategories() = categories
    override suspend fun fetchAssetsSince(sinceUs: Long, afterId: String?, appVersion: Int): List<AssetEntity> {
        calls++
        val sorted = data.sortedWith(compareBy<AssetEntity>({ it.updatedAtUs }, { it.assetId }))
        if (stuck) return sorted.take(ASSET_PAGE_SIZE)
        return sorted.filter { it.updatedAtUs > sinceUs || (it.updatedAtUs == sinceUs && it.assetId > (afterId ?: "")) }
            .take(ASSET_PAGE_SIZE)
    }
    override suspend fun fetchUnlockedIds() = unlockedIds
}

class AssetRepositoryTest {
    private val dao = FakeAssetDao()

    @Test fun syncPagesThroughRowsSharingTheSameTimestamp() = runTest {
        val remote = FakeRemote((1..1200).map { asset("a%04d".format(it), us = 5_000L) })
        val r = AssetRepository(dao, remote).sync().getOrThrow()
        assertEquals(1200, r.assetsReceived)
        assertEquals(1200, dao.assets.value.size)
        assertEquals(1200, dao.assets.value.map { it.assetId }.toSet().size)
        assertEquals(3, remote.calls)
    }

    @Test fun secondSyncFetchesOnlyNewerRows() = runTest {
        val remote = FakeRemote(listOf(asset("a", us = 1L), asset("b", us = 2L)))
        val repo = AssetRepository(dao, remote)
        assertEquals(2, repo.sync().getOrThrow().assetsReceived)
        assertEquals(0, repo.sync().getOrThrow().assetsReceived)
        remote.data = remote.data + asset("c", us = 3L) + asset("a", name = "محدّث", us = 4L)
        assertEquals(2, repo.sync().getOrThrow().assetsReceived)
        assertEquals("محدّث", dao.get("a")!!.nameAr)
        assertEquals(3, dao.assets.value.size)
    }

    @Test fun syncStopsIfServerKeepsReturningSamePage() = runTest {
        val remote = FakeRemote((1..ASSET_PAGE_SIZE).map { asset("s%04d".format(it), us = 7L) })
        remote.stuck = true
        val result = AssetRepository(dao, remote).sync()
        assertTrue(result.isSuccess)
        assertTrue(remote.calls <= 3)
    }

    @Test fun syncWithoutRemoteFailsClearly() = runTest {
        assertTrue(AssetRepository(dao).sync().isFailure)
    }

    @Test fun browseShowsOnlyPublishedButKeepsHiddenAndArchivedStored() = runTest {
        dao.assets.value = listOf(
            asset("p"), asset("h", status = "hidden"), asset("z", status = "archived"), asset("d", status = "draft"),
        )
        val repo = AssetRepository(dao)
        assertEquals(listOf("p"), repo.browse(AssetFilter()).first().map { it.assetId })
        assertEquals("archived", repo.get("z")!!.status) // مشروع قديم ما زال يستطيع الوصول إليه
    }

    @Test fun browseFiltersByQueryStyleAndCategoryTree() = runTest {
        dao.cats.value = listOf(cat("hair"), cat("long_hair", parent = "hair"), cat("shoes"))
        dao.assets.value = listOf(
            asset("1", name = "شعر طويل", cat = "long_hair", style = "anime"),
            asset("2", name = "شعر قصير", cat = "hair", style = "cartoon"),
            asset("3", name = "حذاء", cat = "shoes", style = "cartoon"),
        )
        val repo = AssetRepository(dao)
        assertEquals(setOf("1", "2"), repo.browse(AssetFilter(categoryId = "hair")).first().map { it.assetId }.toSet())
        assertEquals(listOf("1"), repo.browse(AssetFilter(categoryId = "hair", artStyle = "anime")).first().map { it.assetId })
        assertEquals(listOf("3"), repo.browse(AssetFilter(query = " حذاء ")).first().map { it.assetId })
        assertEquals(3, repo.browse(AssetFilter()).first().size)
    }

    @Test fun categoriesHideInactiveAndSort() = runTest {
        dao.cats.value = listOf(cat("b", order = 2), cat("a", order = 1), cat("x", active = false))
        assertEquals(listOf("a", "b"), AssetRepository(dao).categories().first().map { it.id })
    }

    @Test fun unlockedSetIsReplacedBySync() = runTest {
        val remote = FakeRemote(emptyList(), unlockedIds = listOf("p1", "p2"))
        val repo = AssetRepository(dao, remote)
        repo.sync().getOrThrow()
        assertEquals(setOf("p1", "p2"), repo.unlockedIds().first())
        remote.unlockedIds = listOf("p2")
        repo.sync().getOrThrow()
        assertEquals(setOf("p2"), repo.unlockedIds().first())
    }

    @Test fun canUseFreeOrUnlockedPaidOnly() {
        val repo = AssetRepository(dao)
        val paid = asset("p", access = "paid")
        assertTrue(repo.canUse(asset("f"), emptySet()))
        assertFalse(repo.canUse(paid, emptySet()))
        assertTrue(repo.canUse(paid, setOf("p")))
    }
}
