package com.pocketpass.app.update

import kotlinx.serialization.Serializable

@Serializable
data class UpdateManifest(
    val schemaVersion: Int = 1,
    val versionCode: Int,
    val versionName: String? = null,
    val apkUrl: String? = null,
    val apkSha256: String? = null,
    val apkSizeBytes: Long = 0,
    val minSupportedVersionCode: Int? = null,
    val notes: String? = null,
    val publishedAt: String? = null,
) {
    fun isNewerThan(installedVersionCode: Int): Boolean =
        versionCode > installedVersionCode

    fun forcesUpdateOf(installedVersionCode: Int): Boolean =
        (minSupportedVersionCode ?: 0) > installedVersionCode

    fun isInstallable(): Boolean =
        !apkUrl.isNullOrBlank() &&
            apkSha256 != null &&
            apkSha256.length == 64 &&
            apkSha256.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
}

enum class AppUpdateFailureStage {
    Check,
    Download,
    Install,
}

sealed interface AppUpdatePhase {
    data object Idle : AppUpdatePhase
    data object Checking : AppUpdatePhase
    data object UpToDate : AppUpdatePhase
    data object UpdateAvailable : AppUpdatePhase
    data class Downloading(val progress: Float) : AppUpdatePhase
    data object ReadyToInstall : AppUpdatePhase
    data object Installing : AppUpdatePhase
    data class Failed(
        val message: String,
        val stage: AppUpdateFailureStage,
    ) : AppUpdatePhase
}

data class AppUpdateUiState(
    val enabled: Boolean = true,
    val phase: AppUpdatePhase = AppUpdatePhase.Idle,
    val manifest: UpdateManifest? = null,
    val updateRequired: Boolean = false,
    val installedVersionCode: Int = 0,
) {
    val updateAvailable: Boolean
        get() = enabled &&
            manifest?.isNewerThan(installedVersionCode) == true &&
            manifest.isInstallable()
}

fun releaseNoteLines(notes: String): List<String> =
    notes.lines()
        .map { line ->
            line.trim()
                .replace(Regex("^#{1,6}\\s+"), "")
                .replace(Regex("^[-*+]\\s+"), "• ")
        }
        .filter { it.isNotEmpty() }
