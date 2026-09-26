package com.tappony.android.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tappony.core.HistoryRow
import kotlinx.coroutines.flow.Flow

/**
 * One scan in local history: identity and outcome always, bodies only when the
 * profile opted in to keeping them. Never leaves the device except as the CSV
 * export, which excludes bodies (PROFILE_SCHEMA.md section 12).
 */
@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timeMs: Long,
    val profileId: String,
    val profileName: String,
    val uid: String,
    val chip: String,
    val tagType: String,
    val outcome: String,
    val status: Int?,
    val latencyMs: Long?,
    val error: String,
    val message: String?,
    val request: String,
    val requestBody: String?,
    val responseBody: String?,
) {
    fun toRow() = HistoryRow(timeMs, profileName, uid, chip, tagType, outcome, status, latencyMs, error)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timeMs DESC, id DESC")
    fun observe(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history ORDER BY timeMs ASC, id ASC")
    suspend fun allOldestFirst(): List<HistoryEntry>

    @Insert
    suspend fun insert(e: HistoryEntry): Long

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE timeMs < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY timeMs DESC, id DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)
}

/**
 * A scan waiting for a connection (PROFILE_SCHEMA.md section 13). Holds the
 * scan's variables as JSON, never the rendered request, so no secret is stored.
 */
@Entity(tableName = "queue")
data class QueuedScan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scanTimeMs: Long,
    val profileId: String,
    val profileName: String,
    val variablesJson: String,
    val attempts: Int,
    val lastError: String,
)

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue ORDER BY scanTimeMs ASC, id ASC LIMIT 1")
    suspend fun oldest(): QueuedScan?

    @Query("SELECT COUNT(*) FROM queue")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(q: QueuedScan): Long

    @Query("DELETE FROM queue WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE queue SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun failedAttempt(id: Long, error: String)
}

@Database(entities = [HistoryEntry::class, QueuedScan::class], version = 2, exportSchema = false)
abstract class HistoryDb : RoomDatabase() {
    abstract fun dao(): HistoryDao
    abstract fun queue(): QueueDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `queue` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`scanTimeMs` INTEGER NOT NULL, " +
                        "`profileId` TEXT NOT NULL, " +
                        "`profileName` TEXT NOT NULL, " +
                        "`variablesJson` TEXT NOT NULL, " +
                        "`attempts` INTEGER NOT NULL, " +
                        "`lastError` TEXT NOT NULL)",
                )
            }
        }

        @Volatile
        private var instance: HistoryDb? = null

        /** One instance per process; the Worker and the UI share it. */
        fun get(context: Context): HistoryDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, HistoryDb::class.java, "history.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}

/** History with the retention rule applied on every write: at most [MAX_ENTRIES], none older than the setting. */
class HistoryStore(context: Context, private val settings: AppSettings) {
    private val dao = HistoryDb.get(context).dao()

    val entries: Flow<List<HistoryEntry>> = dao.observe()

    suspend fun record(e: HistoryEntry) {
        dao.insert(e)
        prune()
    }

    suspend fun prune() {
        val days = settings.historyDays.value
        if (days > 0) dao.deleteOlderThan(System.currentTimeMillis() - days * DAY_MS)
        dao.trimTo(MAX_ENTRIES)
    }

    suspend fun clear() = dao.clear()

    suspend fun exportRows(): List<HistoryRow> = dao.allOldestFirst().map { it.toRow() }

    companion object {
        const val MAX_ENTRIES = 1000
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val KEPT_BODY_CHARS = 4096
    }
}
