package com.pocketpass.app.steps

import org.junit.Assert.assertEquals
import org.junit.Test

class StepSourceSelectionTest {
    @Test fun healthConnectIsPreferredEvenWhenSensorAlreadyGranted() {
        assertEquals(
            AndroidStepPermissionRequest.HealthConnect,
            nextStepPermissionRequest(true, false, false, true, true),
        )
    }

    @Test fun declinedHealthConnectFallsBackToSensor() {
        assertEquals(
            AndroidStepPermissionRequest.ActivityRecognition,
            nextStepPermissionRequest(true, false, true, true, false),
        )
    }

    @Test fun unavailableHealthConnectFallsBackToSensor() {
        assertEquals(
            AndroidStepPermissionRequest.ActivityRecognition,
            nextStepPermissionRequest(false, false, false, true, false),
        )
    }

    @Test fun explicitRetryCanRequestHealthConnectAgain() {
        assertEquals(
            AndroidStepPermissionRequest.HealthConnect,
            nextStepPermissionRequest(true, false, true, false, false, forceHealthRetry = true),
        )
    }

    @Test fun noPromptWhenPreferredAccessAlreadyGranted() {
        assertEquals(null, nextStepPermissionRequest(true, true, false, true, false))
    }

    @Test fun sensorAndHealthCountsChooseTheLargerReadingWithoutAdding() {
        val health = StepSample("2026-09-26", 120, 5_000, 10)
        val sensor = StepSample("2026-09-26", 120, 4_000, 11)
        assertEquals(5_000, combineStepSamples(sensor, health).stepsToday)
        assertEquals(5_000, combineStepSamples(health, sensor).stepsToday)
    }

    @Test fun yesterdayIsNotMergedIntoToday() {
        val today = StepSample("2026-09-26", 120, 100, 10)
        val yesterday = StepSample("2026-09-25", 120, 10_000, 11)
        assertEquals(100, combineStepSamples(today, yesterday).stepsToday)
    }

    @Test fun permissionOnMissingSensorCannotPretendToProvideSteps() {
        assertEquals(
            StepPermission.NotDetermined,
            effectiveStepPermission(false, true, false, StepPermission.Granted),
        )
    }

    @Test fun eitherGrantedSourcePermitsTracking() {
        assertEquals(
            StepPermission.Granted,
            effectiveStepPermission(true, true, false, StepPermission.NotDetermined),
        )
        assertEquals(
            StepPermission.Granted,
            effectiveStepPermission(false, true, true, StepPermission.Granted),
        )
    }
}
