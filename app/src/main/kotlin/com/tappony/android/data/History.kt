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

@Database(entities = [HistoryEntry::class], version = 1, exportSchema = false)
abstract class HistoryDb : RoomDatabase() {
    abstract fun dao(): HistoryDao

    companion object {
        fun open(context: Context): HistoryDb =
            Room.databaseBuilder(context, HistoryDb::class.java, "history.db").build()
    }
}

/** History with the retention rule applied on every write: at most [MAX_ENTRIES], none older than the setting. */
class HistoryStore(context: Context, private val settings: AppSettings) {
    private val dao = HistoryDb.open(context).dao()

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
