package app.characterstudio.assets

/**
 * مصدر الأصول عن بُعد. التنفيذ الفعلي (Supabase عبر list_assets_since) يأتي مع مرحلة الحسابات،
 * لأن الاستدعاء يحتاج هوية مستخدم. لا يوجد تنفيذ وهمي داخل التطبيق.
 */
interface AssetRemoteSource {
    suspend fun fetchCategories(): List<CategoryEntity>

    /** صفحة (حتى PAGE_SIZE) من الأصول التي تلي المؤشر (sinceUs, afterId) بالترتيب (updatedAtUs, assetId). */
    suspend fun fetchAssetsSince(sinceUs: Long, afterId: String?, appVersion: Int): List<AssetEntity>

    suspend fun fetchUnlockedIds(): List<String>
}

const val ASSET_PAGE_SIZE = 500
