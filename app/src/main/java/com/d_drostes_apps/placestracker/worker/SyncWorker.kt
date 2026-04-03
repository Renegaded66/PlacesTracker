package com.d_drostes_apps.placestracker.worker

import android.content.Context
import androidx.work.*
import com.d_drostes_apps.placestracker.data.SupabaseManager
import java.util.concurrent.TimeUnit

class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val supabaseManager = SupabaseManager(applicationContext)
        supabaseManager.syncAll()
        return Result.success()
    }

    companion object {
        private const val SYNC_WORK_NAME = "supabase_sync_work"

        fun startImmediateSync(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
        
        fun schedulePeriodicSync(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SYNC_WORK_NAME + "_periodic",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
