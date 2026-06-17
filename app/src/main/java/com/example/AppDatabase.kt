package com.example

import androidx.room.*
import android.content.Context

@Entity(tableName = "download_history")
data class DownloadHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val mediaType: String, // "Video", "Audio", "Thumbnail"
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface DownloadHistoryDao {
    @Query("SELECT * FROM download_history ORDER BY timestamp DESC")
    suspend fun getAllHistory(): List<DownloadHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: DownloadHistory)

    @Delete
    suspend fun delete(history: DownloadHistory)
}

@Database(entities = [DownloadHistory::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadHistoryDao(): DownloadHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "videodownloader_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
