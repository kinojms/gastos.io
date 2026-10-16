package io.gastos.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {
    @Query("SELECT * FROM labels ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<LabelEntity>>

    @Query("SELECT * FROM labels ORDER BY position ASC, id ASC")
    suspend fun all(): List<LabelEntity>

    @Query("SELECT * FROM labels WHERE id = :id")
    suspend fun byId(id: Long): LabelEntity?

    @Query("SELECT * FROM labels WHERE isOther = 1 LIMIT 1")
    suspend fun other(): LabelEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM labels")
    suspend fun maxPosition(): Int

    @Insert
    suspend fun insert(label: LabelEntity): Long

    @Query("UPDATE labels SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE labels SET colorArgb = :color WHERE id = :id")
    suspend fun recolor(id: Long, color: Int)

    @Query("UPDATE labels SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun delete(id: Long)
}
