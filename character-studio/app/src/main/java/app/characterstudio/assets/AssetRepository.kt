package app.characterstudio.assets

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class AssetFilter(val query: String = "", val categoryId: String? = null, val artStyle: String? = null)

data class SyncReport(val assetsReceived: Int)

class AssetRepository(
    private val dao: AssetDao,
    private val remote: AssetRemoteSource? = null,
    private val appVersion: Int = 1,
) {
    /** المنشور فقط يظهر في التصفح. المخفي/المؤرشف يبقى محفوظًا محليًا لأن مشاريع قديمة قد تعتمد عليه. */
    fun browse(filter: AssetFilter): Flow<List<AssetEntity>> =
        combine(dao.observeAll(), dao.observeCategories()) { assets, cats ->
            val allowed = filter.categoryId?.let { descendantsOf(it, cats) }
            val q = filter.query.trim()
            assets.asSequence()
                .filter { it.status == AssetStatus.PUBLISHED }
                .filter { allowed == null || it.categoryId in allowed }
                .filter { filter.artStyle == null || it.artStyle == filter.artStyle }
                .filter { q.isEmpty() || it.nameAr.contains(q, ignoreCase = true) }
                .sortedBy { it.nameAr }
                .toList()
        }

    fun categories(): Flow<List<CategoryEntity>> =
        dao.observeCategories().map { cats ->
            cats.filter { it.active }.sortedWith(compareBy<CategoryEntity>({ it.sortOrder }, { it.nameAr }))
        }

    fun unlockedIds(): Flow<Set<String>> = dao.observeUnlockedIds().map { it.toSet() }

    suspend fun get(id: String): AssetEntity? = dao.get(id)

    /** يُستدعى فقط بعد تأكيد الخادم لفتح الأصل. */
    suspend fun markUnlocked(assetId: String) = dao.upsertUnlocked(listOf(AssetUnlockEntity(assetId)))

    /** هل يستطيع المستخدم استخدام الأصل الآن؟ (التحقق النهائي يبقى على الخادم). */
    fun canUse(asset: AssetEntity, unlocked: Set<String>): Boolean =
        asset.accessLevel == AssetAccess.FREE || asset.assetId in unlocked

    suspend fun sync(): Result<SyncReport> = runCatching {
        val source = remote ?: error("remote_not_configured")
        dao.upsertCategories(source.fetchCategories())
        var total = 0
        while (true) {
            val cursor = dao.latest()
            val page = source.fetchAssetsSince(cursor?.updatedAtUs ?: 0L, cursor?.assetId, appVersion)
            if (page.isEmpty()) break
            dao.upsertAssets(page)
            total += page.size
            val after = dao.latest()
            // حماية من حلقة لا نهائية إذا أعاد الخادم الصفحة نفسها ولم يتقدم المؤشر
            if (after?.updatedAtUs == cursor?.updatedAtUs && after?.assetId == cursor?.assetId) break
            if (page.size < ASSET_PAGE_SIZE) break
        }
        val unlocked = source.fetchUnlockedIds()
        dao.upsertUnlocked(unlocked.map { AssetUnlockEntity(it) })
        dao.deleteUnlockedNotIn(unlocked)
        SyncReport(total)
    }

    private fun descendantsOf(root: String, cats: List<CategoryEntity>): Set<String> {
        val result = mutableSetOf(root)
        var grew = true
        while (grew) {
            grew = false
            for (c in cats) if (c.parentId != null && c.parentId in result && result.add(c.id)) grew = true
        }
        return result
    }
}
