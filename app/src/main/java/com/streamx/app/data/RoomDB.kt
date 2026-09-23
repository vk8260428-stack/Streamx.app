package com.streamx.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Room Entity to track user watch progress and episode completion status.
 */
@Entity(tableName = "watched_episodes")
data class WatchedEpisodeEntity(
    @PrimaryKey
    val episodeId: String,
    val seriesId: String,
    val seriesTitle: String,
    val episodeTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val thumbnailUrl: String,
    val lastPlaybackPositionMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
    val lastWatchedTimestamp: Long = System.currentTimeMillis()
)

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watched_episodes ORDER BY lastWatchedTimestamp DESC")
    fun getAllWatchHistory(): Flow<List<WatchedEpisodeEntity>>

    @Query("SELECT * FROM watched_episodes WHERE seriesId = :seriesId")
    fun getWatchHistoryForSeries(seriesId: String): Flow<List<WatchedEpisodeEntity>>

    @Query("SELECT * FROM watched_episodes WHERE episodeId = :episodeId LIMIT 1")
    suspend fun getWatchedEpisode(episodeId: String): WatchedEpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWatchProgress(episode: WatchedEpisodeEntity)

    @Query("UPDATE watched_episodes SET isCompleted = 1, lastPlaybackPositionMs = durationMs WHERE episodeId = :episodeId")
    suspend fun markAsCompleted(episodeId: String)

    @Query("DELETE FROM watched_episodes WHERE episodeId = :episodeId")
    suspend fun removeEpisode(episodeId: String)

    @Query("DELETE FROM watched_episodes")
    suspend fun clearAllHistory()
}

@Database(entities = [WatchedEpisodeEntity::class], version = 1, exportSchema = false)
abstract class StreamXDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: StreamXDatabase? = null

        fun getDatabase(context: Context): StreamXDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StreamXDatabase::class.java,
                    "streamx_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
