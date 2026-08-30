package com.pocketpass.app.update

import com.pocketpass.app.data.SettingsRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class AppUpdateStateHolder(
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val installedVersionCode: Int,
    private val enabled: Boolean,
    private val manifestUrl: String,
    private val downloadDirProvider: () -> File,
    private val manifestFetcher: UpdateManifestFetcher,
    private val apkDownloader: UpdateApkDownloader,
    private val installGate: () -> Boolean,
    private val installer: suspend (File, UpdateManifest) -> Unit,
    private val notifier: (UpdateManifest) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(
        AppUpdateUiState(
            enabled = enabled,
            installedVersionCode = installedVersionCode,
        ),
    )
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()

    private val _installPermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val installPermissionRequests: SharedFlow<Unit> = _installPermissionRequests.asSharedFlow()

    private var downloadJob: Job? = null
    private var foregroundJob: Job? = null
    private var lastCheckStartedAtMillis = 0L

    init {
        if (enabled) {
            scope.launch { cleanUpStaleDownloads() }
        }
    }

    fun checkOnLaunch() {
        if (!enabled) return
        lastCheckStartedAtMillis = now()
        scope.launch {
            val persistedFloor = settingsRepository.settings.first()
                .lastSeenMinSupportedVersionCode
            if (persistedFloor > installedVersionCode) {
                _state.update { it.copy(updateRequired = true) }
            }
            var delayMillis = 5_000L
            repeat(3) { attempt ->
                val manifest = runCatching {
                    json.decodeFromString<UpdateManifest>(manifestFetcher.fetch(manifestUrl))
                }.getOrNull()
                if (manifest != null) {
                    applyManifest(manifest, notify = true)
                    return@launch
                }
                if (attempt < 2) {
                    delay(delayMillis)
                    delayMillis = 30_000L
                }
            }
        }
    }

    fun setForeground(foreground: Boolean) {
        if (!enabled) return
        foregroundJob?.cancel()
        foregroundJob = null
        if (!foreground) return
        foregroundJob = scope.launch {
            if (now() - lastCheckStartedAtMillis >= FOREGROUND_RECHECK_MILLIS) {
                refresh()
            }
            while (true) {
                delay(PERIODIC_RECHECK_MILLIS)
                refresh()
            }
        }
    }

    fun onRemoteManifestChanged() {
        if (!enabled) return
        scope.launch { refresh() }
    }

    private suspend fun refresh() {
        lastCheckStartedAtMillis = now()
        val manifest = runCatching {
            json.decodeFromString<UpdateManifest>(manifestFetcher.fetch(manifestUrl))
        }.getOrNull() ?: return
        applyManifest(manifest, notify = true)
    }

    fun check() {
        if (!enabled) return
        val phase = _state.value.phase
        if (
            phase !is AppUpdatePhase.Idle &&
            phase !is AppUpdatePhase.UpToDate &&
            phase !is AppUpdatePhase.UpdateAvailable &&
            phase !is AppUpdatePhase.Failed
        ) {
            return
        }
        _state.update { it.copy(phase = AppUpdatePhase.Checking) }
        scope.launch {
            runCatching {
                json.decodeFromString<UpdateManifest>(manifestFetcher.fetch(manifestUrl))
            }.onSuccess { manifest ->
                applyManifest(manifest, notify = false)
            }.onFailure {
                _state.update {
                    it.copy(
                        phase = AppUpdatePhase.Failed(
                            message = "Couldn't reach the update server.",
                            stage = AppUpdateFailureStage.Check,
                        ),
                    )
                }
            }
        }
    }

    fun download() {
        if (!enabled) return
        val current = _state.value
        val phase = current.phase
        val allowed = phase is AppUpdatePhase.UpdateAvailable ||
            (phase is AppUpdatePhase.Failed && phase.stage != AppUpdateFailureStage.Check)
        if (!allowed) return
        val manifest = current.manifest ?: return
        if (!manifest.isNewerThan(installedVersionCode) || !manifest.isInstallable()) return
        if (downloadJob?.isActive == true) return
        _state.update { it.copy(phase = AppUpdatePhase.Downloading(0f)) }
        downloadJob = scope.launch {
            val target = targetFile(manifest)
            val part = File(target.parentFile, "${target.name}.part")
            runCatching {
                withContext(Dispatchers.IO) { target.parentFile?.mkdirs() }
                apkDownloader.download(
                    url = manifest.apkUrl.orEmpty(),
                    target = part,
                    expectedBytes = manifest.apkSizeBytes,
                ) { progress ->
                    _state.update { it.copy(phase = AppUpdatePhase.Downloading(progress)) }
                }
                withContext(Dispatchers.IO) {
                    val actual = sha256Of(part)
                    check(actual.equals(manifest.apkSha256, ignoreCase = true)) {
                        "The download didn't verify. Try again."
                    }
                    target.delete()
                    check(part.renameTo(target)) { "Couldn't finish the download." }
                }
            }.onSuccess {
                _state.update { it.copy(phase = AppUpdatePhase.ReadyToInstall) }
            }.onFailure { error ->
                withContext(Dispatchers.IO) {
                    part.delete()
                    target.delete()
                }
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.update {
                    it.copy(
                        phase = AppUpdatePhase.Failed(
                            message = error.message ?: "Download failed.",
                            stage = AppUpdateFailureStage.Download,
                        ),
                    )
                }
            }
        }
    }

    fun install() {
        if (!enabled) return
        val current = _state.value
        val phase = current.phase
        val allowed = phase is AppUpdatePhase.ReadyToInstall ||
            (phase is AppUpdatePhase.Failed && phase.stage == AppUpdateFailureStage.Install)
        if (!allowed) return
        val manifest = current.manifest ?: return
        if (!installGate()) {
            _installPermissionRequests.tryEmit(Unit)
            return
        }
        _state.update { it.copy(phase = AppUpdatePhase.Installing) }
        scope.launch {
            runCatching {
                installer(targetFile(manifest), manifest)
            }.onFailure { error ->
                onInstallFailed(error.message, aborted = false)
            }
        }
    }

    fun onInstallFailed(message: String?, aborted: Boolean) {
        _state.update {
            it.copy(
                phase = if (aborted) {
                    AppUpdatePhase.ReadyToInstall
                } else {
                    AppUpdatePhase.Failed(
                        message = message ?: "The update couldn't be installed.",
                        stage = AppUpdateFailureStage.Install,
                    )
                },
            )
        }
    }

    private suspend fun applyManifest(manifest: UpdateManifest, notify: Boolean) {
        val available = manifest.isNewerThan(installedVersionCode) && manifest.isInstallable()
        _state.update { current ->
            val phase = when {
                current.phase is AppUpdatePhase.Downloading ||
                    current.phase is AppUpdatePhase.ReadyToInstall ||
                    current.phase is AppUpdatePhase.Installing -> current.phase

                available -> AppUpdatePhase.UpdateAvailable
                else -> AppUpdatePhase.UpToDate
            }
            current.copy(
                phase = phase,
                manifest = manifest,
                updateRequired = manifest.forcesUpdateOf(installedVersionCode),
            )
        }
        settingsRepository.setLastSeenMinSupportedVersionCode(
            manifest.minSupportedVersionCode ?: 0,
        )
        if (notify && available) {
            val settings = settingsRepository.settings.first()
            if (
                settings.updateAlertsEnabled &&
                manifest.versionCode > settings.lastNotifiedUpdateVersionCode &&
                notifier(manifest)
            ) {
                settingsRepository.setLastNotifiedUpdateVersionCode(manifest.versionCode)
            }
        }
    }

    private fun targetFile(manifest: UpdateManifest): File =
        File(downloadDirProvider(), "PocketPass-${manifest.versionCode}.apk")

    private suspend fun cleanUpStaleDownloads() = withContext(Dispatchers.IO) {
        val files = downloadDirProvider().listFiles() ?: return@withContext
        files.forEach { file ->
            val versionCode = Regex("PocketPass-(\\d+)\\.apk")
                .matchEntire(file.name)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
            if (versionCode == null || versionCode <= installedVersionCode) {
                file.delete()
            }
        }
    }
}

private const val FOREGROUND_RECHECK_MILLIS = 10L * 60L * 1_000L
private const val PERIODIC_RECHECK_MILLIS = 15L * 60L * 1_000L
