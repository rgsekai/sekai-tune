/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.scrobble

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import moe.rgsekai.sekaitune.scrobble.ScrobblerEntryPoint
import timber.log.Timber

class ScrobbleSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            ScrobblerEntryPoint::class.java,
        )
        val scrobbleManager = entryPoint.scrobbleManager()

        val providerId = inputData.getString(KEY_PROVIDER_ID)
        val provider = ScrobbleProvider.entries.find { it.id == providerId }

        Timber.tag("ScrobbleSyncWorker").d("Executing queued scrobble flush for provider=%s", provider?.title ?: "all")
        return try {
            if (provider != null) {
                scrobbleManager.flushQueue(provider)
            } else {
                ScrobbleProvider.entries.forEach { scrobbleManager.flushQueue(it) }
            }
            Result.success()
        } catch (e: Exception) {
            Timber.tag("ScrobbleSyncWorker").w(e, "Scrobble flush encountered retryable error")
            Result.retry()
        }
    }

    companion object {
        const val KEY_PROVIDER_ID = "provider_id"
    }
}
