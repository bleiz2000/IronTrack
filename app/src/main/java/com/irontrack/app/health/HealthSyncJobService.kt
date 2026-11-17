package com.irontrack.app.health

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.irontrack.app.data.local.WorkoutDatabase
import com.irontrack.app.data.local.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Reads Health Connect in the background so the readiness card is fresh when the
 * user opens the app. Android runs periodic jobs at best-effort: with a one hour
 * period the system may shift the run by a few minutes to save battery.
 */
class HealthSyncJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val context = applicationContext
            runCatching {
                val database = WorkoutDatabase.create(context)
                val repository = WorkoutRepository(database)
                val health = HealthRepository(context)
                if (!health.hasBackgroundPermission()) {
                    HealthSyncScheduler.cancel(context)
                    return@runCatching
                }
                val snapshot = health.read()
                repository.upsertHealth(snapshot)
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}

object HealthSyncScheduler {
    private const val JOB_ID = 4107
    private const val PERIOD_MILLIS = 60L * 60L * 1000L

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val component = ComponentName(context, HealthSyncJobService::class.java)
        val job = JobInfo.Builder(JOB_ID, component)
            .setPeriodic(PERIOD_MILLIS)
            .setPersisted(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
            .build()
        runCatching { scheduler.schedule(job) }
    }

    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
    }

    fun isScheduled(context: Context): Boolean {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return false
        return runCatching {
            scheduler.allPendingJobs.any { it.id == JOB_ID && it.isPeriodic }
        }.getOrDefault(false)
    }
}

/** Restores the hourly job after a reboot; persisted jobs may be dropped on some ROMs. */
class HealthSyncBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (HealthRepository(context).hasBackgroundPermission()) {
            HealthSyncScheduler.schedule(context)
        }
    }
}
