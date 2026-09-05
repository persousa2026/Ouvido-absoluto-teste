package com.ouvidoabsoluto.ai.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tonality_analyses")
data class TonalityAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tonic: String,
    val mode: String,
    val confidence: Double,
    val createdAt: Long,
    val voiceTimeMs: Long
)

@Dao
interface TonalityAnalysisDao {
    @Insert suspend fun insert(entity: TonalityAnalysisEntity): Long
    @Query("SELECT * FROM tonality_analyses ORDER BY createdAt DESC") fun observeAll(): Flow<List<TonalityAnalysisEntity>>
    @Query("DELETE FROM tonality_analyses WHERE id = :id") suspend fun delete(id: Long)
}

@Database(entities = [TonalityAnalysisEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() { abstract fun tonalityAnalysisDao(): TonalityAnalysisDao }

class TonalityRepository(private val dao: TonalityAnalysisDao) {
    fun history() = dao.observeAll()
    suspend fun save(tonic: String, mode: String, confidence: Double, voiceTimeMs: Long) =
        dao.insert(TonalityAnalysisEntity(tonic = tonic, mode = mode, confidence = confidence, createdAt = System.currentTimeMillis(), voiceTimeMs = voiceTimeMs))
}
