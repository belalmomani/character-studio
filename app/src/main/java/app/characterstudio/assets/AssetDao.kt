package app.characterstudio.assets

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets")
    fun observeAll(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE assetId = :id")
    suspend fun get(id: String): AssetEntity?

    /** آخر صف حسب (updatedAtUs, assetId): يُستخدم مؤشرًا للمزامنة التالية. */
    @Query("SELECT * FROM assets ORDER BY updatedAtUs DESC, assetId DESC LIMIT 1")
    suspend fun latest(): AssetEntity?

    @Upsert
    suspend fun upsertAssets(items: List<AssetEntity>)

    @Query("SELECT * FROM asset_categories")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Upsert
    suspend fun upsertCategories(items: List<CategoryEntity>)

    @Query("SELECT assetId FROM asset_unlocks")
    fun observeUnlockedIds(): Flow<List<String>>

    @Upsert
    suspend fun upsertUnlocked(items: List<AssetUnlockEntity>)

    @Query("DELETE FROM asset_unlocks WHERE assetId NOT IN (:keep)")
    suspend fun deleteUnlockedNotIn(keep: List<String>)
}
