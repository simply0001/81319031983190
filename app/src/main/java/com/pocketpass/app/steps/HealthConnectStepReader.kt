package com.pocketpass.app.steps

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.pocketpass.app.widget.startOfLocalDayEpochMillis
import java.time.Instant

/** Read-only Health Connect access. Raw records stay on the device. */
internal class HealthConnectStepReader(context: Context) {
    private val client = runCatching {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else null
    }.getOrNull()

    val available: Boolean get() = client != null

    suspend fun hasReadPermission(): Boolean = client?.permissionController
        ?.getGrantedPermissions()
        ?.contains(READ_STEPS) == true

    suspend fun sample(): StepSample? {
        val health = client ?: return null
        val now = System.currentTimeMillis()
        val dayStart = startOfLocalDayEpochMillis(now)
        val range = TimeRangeFilter.between(
            Instant.ofEpochMilli(dayStart),
            Instant.ofEpochMilli(now),
        )
        val records = mutableListOf<HealthStepInterval>()
        var pageToken: String? = null
        var pages = 0
        do {
            val page = health.readRecords(
                ReadRecordsRequest<StepsRecord>(
                    timeRangeFilter = range,
                    pageSize = PAGE_SIZE,
                    pageToken = pageToken,
                ),
            )
            records += page.records.map { record ->
                HealthStepInterval(
                    origin = record.metadata.dataOrigin.packageName,
                    startMillis = record.startTime.toEpochMilli(),
                    endMillis = record.endTime.toEpochMilli(),
                    count = record.count,
                    method = when (record.metadata.recordingMethod) {
                        Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED -> StepRecordingMethod.Automatic
                        Metadata.RECORDING_METHOD_ACTIVELY_RECORDED -> StepRecordingMethod.Active
                        Metadata.RECORDING_METHOD_MANUAL_ENTRY -> StepRecordingMethod.Manual
                        else -> StepRecordingMethod.Unknown
                    },
                    hasDevice = record.metadata.device != null,
                )
            }
            pageToken = page.pageToken
            pages++
        } while (pageToken != null && pages < MAX_PAGES)
        if (pageToken != null) {
            Log.w(TAG, "Too many step records today; using the device sensor instead")
            return null
        }
        return StepSample(
            localDay = localDayKey(now),
            utcOffsetMinutes = localUtcOffsetMinutes(now),
            stepsToday = eligibleHealthSteps(records, dayStart, now),
            sampledAtEpochMillis = now,
        )
    }

    companion object {
        val READ_STEPS: String = HealthPermission.getReadPermission(StepsRecord::class)
        private const val PAGE_SIZE = 1_000
        private const val MAX_PAGES = 5
        private const val TAG = "PocketPassSteps"
    }
}
