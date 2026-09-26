package com.tappony.android

import android.content.Context
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tappony.android.data.HistoryDb
import com.tappony.android.data.HistoryEntry
import com.tappony.android.data.QueuedScan
import com.tappony.core.HistoryCsv
import com.tappony.core.Json
import com.tappony.core.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Scans that got no response, waiting for a connection. PROFILE_SCHEMA.md
 * section 13. Only variables are stored; secrets are resolved at send time.
 * WorkManager flushes it when the network is back; opening the app and the
 * Send now button flush it too.
 */
class OfflineQueue(private val app: TapPonyApp) {

    enum class Flush { EMPTY, STOPPED }

    private val db = HistoryDb.get(app)
    private val dao = db.queue()
    private val mutex = Mutex()

    val count: Flow<Int> = dao.observeCount()

    suspend fun enqueue(profile: Profile, scanTimeMs: Long, variables: Map<String, String>, error: String) {
        dao.insert(
            QueuedScan(
                scanTimeMs = scanTimeMs,
                profileId = profile.id,
                profileName = profile.name,
                variablesJson = Json.write(variables),
                attempts = 1,
                lastError = error,
            ),
        )
        kick()
    }

    /** Ask WorkManager to flush once a network is available. Cheap to call when the queue is empty. */
    fun kick() {
        val request = OneTimeWorkRequestBuilder<QueueWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Sends queued scans oldest first; stops at the first one that still gets no response.
     * Re-reads the head each time, so a scan queued while this runs is picked up too.
     */
    suspend fun flush(): Flush = mutex.withLock {
        while (true) {
            val item = dao.oldest() ?: break
            val now = System.currentTimeMillis()
            if (now - item.scanTimeMs > MAX_AGE_MS) {
                finish(item, entry(item, HistoryCsv.NETWORK_ERROR, item.lastError))
                continue
            }
            val profile = app.profiles.get(item.profileId)
            if (profile == null) {
                finish(item, entry(item, HistoryCsv.NOT_SENT, "profileDeleted"))
                continue
            }
            val vars = decode(item.variablesJson)
            val outcome = app.engine.resend(profile, vars, item.scanTimeMs)
            if (outcome.isNoResponse) {
                dao.failedAttempt(item.id, outcome.result?.error ?: "")
                return@withLock Flush.STOPPED
            }
            finish(item, outcome.toHistory(profile.after.keepBodies))
        }
        Flush.EMPTY
    }

    /** Removes the item and writes its history entry together, so neither is lost or doubled. */
    private suspend fun finish(item: QueuedScan, e: HistoryEntry) {
        db.withTransaction {
            dao.delete(item.id)
            app.history.record(e)
        }
    }

    private fun entry(item: QueuedScan, outcome: String, error: String): HistoryEntry {
        val vars = decode(item.variablesJson)
        return HistoryEntry(
            timeMs = item.scanTimeMs,
            profileId = item.profileId,
            profileName = item.profileName,
            uid = vars["uid"] ?: "",
            chip = vars["chip"] ?: "",
            tagType = vars["tag_type"] ?: "",
            outcome = outcome,
            status = null,
            latencyMs = null,
            error = error,
            message = null,
            request = "",
            requestBody = null,
            responseBody = null,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun decode(json: String): Map<String, String> =
        try {
            (Json.parse(json) as Map<String, Any?>).mapValues { it.value as? String ?: "" }
        } catch (e: Exception) {
            emptyMap()
        }

    companion object {
        const val WORK_NAME = "tappony-offline-queue"
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}

class QueueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as TapPonyApp
        return when (app.queue.flush()) {
            OfflineQueue.Flush.EMPTY -> Result.success()
            OfflineQueue.Flush.STOPPED -> Result.retry()
        }
    }
}
