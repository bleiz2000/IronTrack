package com.irontrack.app.health

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.irontrack.app.data.local.HealthSnapshotEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class HealthStatus(
    val available: Boolean,
    val providerUpdateRequired: Boolean,
    val connected: Boolean,
    val grantedCount: Int = 0,
    val requiredCount: Int = 0,
    val backgroundGranted: Boolean = false,
)

/**
 * Reads Mi Fitness / wearable data through Google Health Connect.
 * Mi Fitness is not called directly: the user enables its Health Connect sync.
 */
class HealthRepository(private val context: Context) {
    companion object {
        val corePermissions: Set<String> = setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        )
        /**
         * Separate Health Connect grant that lets the app read while it is not in
         * the foreground. The user approves it in the same dialog as the rest.
         */
        const val BACKGROUND_PERMISSION = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

        val optionalPermissions: Set<String> = setOf(
            HealthPermission.getReadPermission(Vo2MaxRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        )
        val allPermissions: Set<String> = corePermissions + optionalPermissions
    }

    fun hasBackgroundPermission(): Boolean =
        context.checkSelfPermission(BACKGROUND_PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun clientOrNull(): HealthConnectClient? =
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
        } else null

    suspend fun status(): HealthStatus {
        val background = hasBackgroundPermission()
        val sdkStatus = HealthConnectClient.getSdkStatus(context)
        if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
            return HealthStatus(
                available = false,
                providerUpdateRequired = sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED,
                connected = false,
                grantedCount = 0,
                requiredCount = corePermissions.size,
                backgroundGranted = background,
            )
        }
        val client = clientOrNull()
            ?: return HealthStatus(false, false, false, 0, corePermissions.size, background)
        val granted = runCatching { client.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())
        return HealthStatus(
            available = true,
            providerUpdateRequired = false,
            connected = granted.containsAll(corePermissions),
            grantedCount = granted.count { it in corePermissions },
            requiredCount = corePermissions.size,
            backgroundGranted = background,
        )
    }

    suspend fun read(): HealthSnapshotEntity = withContext(Dispatchers.IO) {
        val client = clientOrNull() ?: error("Health Connect недоступен на этом телефоне")

        val now = Instant.now()
        val since24 = now.minus(24, ChronoUnit.HOURS)
        val since48 = now.minus(48, ChronoUnit.HOURS)
        val since7Days = now.minus(7, ChronoUnit.DAYS)
        val since30Days = now.minus(30, ChronoUnit.DAYS)

        // Some ROMs report an empty granted-permission set even when the data is
        // readable, so every read is attempted and each failure is collected.
        // Only a total failure is reported as a permissions problem.
        val failures = mutableListOf<String>()

        val sleepSessions = runCatching {
            client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(since48, now)),
            ).records
        }.getOrElse {
            failures += "сон: ${it.shortReason()}"
            emptyList()
        }
        val latestSleep = sleepSessions.filter { it.endTime >= since24 }
            .maxByOrNull { ChronoUnit.MINUTES.between(it.startTime, it.endTime) }
        val sleepMinutes = latestSleep?.let {
            ChronoUnit.MINUTES.between(it.startTime, it.endTime).toInt().coerceAtLeast(0)
        }
        val stageMinutes = latestSleep?.stages
            ?.groupBy { it.stage }
            ?.mapValues { (_, stages) ->
                stages.sumOf { ChronoUnit.MINUTES.between(it.startTime, it.endTime).toInt().coerceAtLeast(0) }
            }
            .orEmpty()
        val deepSleep = stageMinutes[SleepSessionRecord.STAGE_TYPE_DEEP]
        val remSleep = stageMinutes[SleepSessionRecord.STAGE_TYPE_REM]

        val restingRecords = runCatching {
            client.readRecords(
                ReadRecordsRequest(RestingHeartRateRecord::class, TimeRangeFilter.between(since7Days, now)),
            ).records
        }.getOrElse {
            failures += "пульс покоя: ${it.shortReason()}"
            emptyList()
        }.sortedBy { it.time }
        val latestRestingHr = restingRecords.lastOrNull()?.beatsPerMinute?.toInt()
        val baselineRestingHr = restingRecords.dropLast(1)
            .takeIf { it.isNotEmpty() }
            ?.map { it.beatsPerMinute }
            ?.average()
            ?.toInt()

        val heartSamples = runCatching {
            client.readRecords(
                ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(since24, now)),
            ).records
        }.getOrElse {
            failures += "пульс: ${it.shortReason()}"
            emptyList()
        }.flatMap { it.samples }
        val averageHr = heartSamples.takeIf { it.isNotEmpty() }
            ?.map { it.beatsPerMinute }
            ?.average()
            ?.toInt()

        val stepRecords = runCatching {
            client.readRecords(
                ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.between(since24, now)),
            ).records
        }.getOrElse {
            failures += "шаги: ${it.shortReason()}"
            emptyList()
        }
        val steps = if (stepRecords.isEmpty()) null else stepRecords.sumOf { it.count }.toInt()

        val calorieRecords = runCatching {
            client.readRecords(
                ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, TimeRangeFilter.between(since24, now)),
            ).records
        }.getOrElse {
            failures += "калории: ${it.shortReason()}"
            emptyList()
        }
        val calories = if (calorieRecords.isEmpty()) null
        else calorieRecords.sumOf { it.energy.inKilocalories }.toInt()

        val vo2max = runCatching {
            client.readRecords(
                ReadRecordsRequest(Vo2MaxRecord::class, TimeRangeFilter.between(since30Days, now)),
            ).records.maxByOrNull { it.time }?.vo2MillilitersPerMinuteKilogram
        }.getOrNull()

        val hrv = runCatching {
            client.readRecords(
                ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, TimeRangeFilter.between(since48, now)),
            ).records.maxByOrNull { it.time }?.heartRateVariabilityMillis
        }.getOrNull()

        if (failures.size == 5 && sleepSessions.isEmpty() && restingRecords.isEmpty() &&
            heartSamples.isEmpty() && stepRecords.isEmpty() && calorieRecords.isEmpty()
        ) {
            error("Health Connect не отдал данные (${failures.first()})")
        }

        HealthSnapshotEntity(
            dateEpochDay = LocalDate.now().toEpochDay(),
            sleepMinutes = sleepMinutes,
            deepSleepMinutes = deepSleep,
            remSleepMinutes = remSleep,
            sleepStartMillis = latestSleep?.startTime?.toEpochMilli(),
            sleepEndMillis = latestSleep?.endTime?.toEpochMilli(),
            restingHr = latestRestingHr,
            baselineRestingHr = baselineRestingHr,
            avgHr = averageHr,
            steps = steps,
            activeCalories = calories,
            vo2max = vo2max,
            hrvRmssd = hrv,
            updatedAt = System.currentTimeMillis(),
        )
    }
}

private fun Throwable.shortReason(): String =
    (message ?: javaClass.simpleName).lineSequence().firstOrNull()?.take(300) ?: javaClass.simpleName
