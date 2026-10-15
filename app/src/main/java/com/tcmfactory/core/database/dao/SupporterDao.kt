package com.tcmfactory.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tcmfactory.core.database.entity.SupporterEntity
import kotlinx.coroutines.flow.Flow

/**
 * 支持者名单读写。
 *
 * 不需要复杂的查询能力 —— 名单只有几十条，界面一次性全取即可，
 * 因此这里没有分页、没有搜索，只有「读全部」和「整包替换」两个动作。
 */
@Dao
interface SupporterDao {

    @Query("SELECT * FROM supporters ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<SupporterEntity>>

    @Query("SELECT * FROM supporters ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<SupporterEntity>

    @Query("SELECT COUNT(*) FROM supporters")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<SupporterEntity>)

    @Query("DELETE FROM supporters WHERE packId = :packId")
    suspend fun deleteByPack(packId: String)

    @Query("DELETE FROM supporters")
    suspend fun clear()

    /**
     * 整包替换：先删同包的旧记录，再写入新记录。
     *
     * 用事务包住，避免"删完了、写失败"导致名单暂时变空。
     */
    @Transaction
    suspend fun replacePack(packId: String, items: List<SupporterEntity>) {
        deleteByPack(packId)
        if (items.isNotEmpty()) insertAll(items)
    }
}
