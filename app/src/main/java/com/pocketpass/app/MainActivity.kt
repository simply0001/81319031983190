package com.pocketpass.app

import com.pocketpass.app.steps.StepRewardsPermissionPolicy
import com.pocketpass.app.steps.AndroidStepPermissionRequest
import com.pocketpass.app.steps.HealthConnectStepReader
import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityOptions
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.health.connect.client.PermissionController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pocketpass.app.audio.BackgroundMusicPlayer
import com.pocketpass.app.audio.backgroundMusicTrack
import com.pocketpass.app.auth.AuthCallbackDecision
import com.pocketpass.app.auth.AuthCallbackPolicy
import com.pocketpass.app.auth.ConsentLinkDecision
import com.pocketpass.app.auth.ConsentLinkPolicy
import com.pocketpass.app.display.CompanionDisplayCoordinator
import com.pocketpass.app.display.DisplayRoles
import com.pocketpass.app.display.preferSixtyHertz
import com.pocketpass.app.input.MiiEditorJoystickHandler
import com.pocketpass.app.input.MiiEditorRightStickHandler
import com.pocketpass.app.input.BoardCanvasJoystickHandler
import com.pocketpass.app.input.handleActivitiesGamepadKeyEvent
import com.pocketpass.app.input.handleBackGamepadKeyEvent
import com.pocketpass.app.input.handleMiiEditorGamepadKeyEvent
import com.pocketpass.app.input.handleNavigationGamepadKeyEvent
import com.pocketpass.app.media.AndroidImageAttachmentPreparer
import com.pocketpass.app.media.ImageAttachmentPreparation
import com.pocketpass.app.mii.renderer.MiiRenderController
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.nearby.NearbyNotifications
import com.pocketpass.app.nearby.NearbyPermissionPolicy
import com.pocketpass.app.power.BatteryOptimizationExemption
import com.pocketpass.app.state.PocketPassViewModel
import com.pocketpass.app.ui.BottomDisplayApp
import com.pocketpass.app.ui.phone.PhoneApp
import com.pocketpass.app.ui.TopDisplayApp
import com.pocketpass.app.update.UpdateNotifications
import com.pocketpass.app.widget.CustomWidget
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: PocketPassViewModel by viewModels {
        PocketPassViewModel.Factory(application as PocketPassApplication)
    }
    private lateinit var displayCoordinator: CompanionDisplayCoordinator
    private val backgroundMusic by lazy { BackgroundMusicPlayer(applicationContext) }
    private val soundEffects
        get() = (application as PocketPassApplication).container.soundEffects
    private val miiEditorJoystickHandler by lazy {
        MiiEditorJoystickHandler(MiiRenderController.get(this)::postOrbit)
    }
    private val miiEditorRightStickHandler by lazy {
        MiiEditorRightStickHandler(
            state = { viewModel.state.value },
            sendKey = ::forwardKeyEvent,
        )
    }
    private val boardCanvasJoystickHandler by lazy {
        BoardCanvasJoystickHandler({ viewModel.state.value }, viewModel.controllerFocus)
    }
    private var nearbyPermissionStage = NearbyPermissionStage.Idle
    private val nearbyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        when (nearbyPermissionStage) {
            NearbyPermissionStage.Foreground -> requestLegacyBackgroundOrFinish()
            NearbyPermissionStage.Background -> finishNearbyPermissionRequest()
            NearbyPermissionStage.Idle -> Unit
        }
    }
    private val nearbySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        finishNearbyPermissionRequest()
    }
    private val unknownSourcesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (packageManager.canRequestPackageInstalls()) {
            viewModel.dispatch(PocketPassEvent.InstallAppUpdate)
        }
    }
    private val imageAttachmentLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let(::sendPickedImage)
    }
    private val imageAttachmentPreparer by lazy {
        AndroidImageAttachmentPreparer(applicationContext)
    }
    private var boardImageRequest: com.pocketpass.app.boards.BoardBrandingPicker.Request? = null
    private val boardImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val request = boardImageRequest
        boardImageRequest = null
        if(request != null) lifecycleScope.launch {
            if(uri == null) request.complete(null)
            else try {
                when(val prepared = imageAttachmentPreparer.prepare(uri)) {
                    is ImageAttachmentPreparation.Ready -> {
                        val file = java.io.File(prepared.attachment.path)
                        try { request.complete(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { file.readBytes() }) }
                        finally { file.delete() }
                    }
                    is ImageAttachmentPreparation.Failed -> request.fail(prepared.message)
                }
            } catch(_: Exception) { request.fail("This image could not be opened") }
        }
    }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { com.pocketpass.app.push.MessagePushRegistrationWorker.enqueue(applicationContext) }
    private val stepPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        (application as PocketPassApplication).container.stepSource.onSensorPermissionResult()
        val permission = StepRewardsPermissionPolicy.requiredPermission()
        if (!granted && !shouldShowRequestPermissionRationale(permission)) {
            stepSettingsLauncher.launch(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ),
            )
            return@registerForActivityResult
        }
        viewModel.onStepRewardsPermissionResult()
    }
    private val healthStepsPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        (application as PocketPassApplication).container.stepSource
            .onHealthPermissionResult(HealthConnectStepReader.READ_STEPS in granted)
        viewModel.onStepRewardsPermissionResult()
    }
    private val stepSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onStepRewardsPermissionResult()
    }
    private val batteryExemptionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enforceDefaultDisplay()

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        displayCoordinator = CompanionDisplayCoordinator(this, viewModel)
        configureWindow(window, immersive = displayCoordinator.companionAttached)
        if (savedInstanceState == null && !isFinishing) offerBatteryExemptionOnce()

        setContent {
            val companionAttached = displayCoordinator.companionAttached
            LaunchedEffect(companionAttached) { applyOrientationPolicy() }
            when {
                !companionAttached -> PhoneApp(viewModel = viewModel)
                DisplayRoles.defaultDisplayIsBottomPanel -> BottomDisplayApp(viewModel = viewModel)
                else -> TopDisplayApp(viewModel = viewModel)
            }
        }
        processAuthCallback(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (application as PocketPassApplication)
                    .container
                    .notificationPermissionRequests
                    .collect {
                        if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (application as PocketPassApplication)
                    .container
                    .nearby
                    .permissionRequests
                    .collect { beginNearbyPermissionRequest() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (application as PocketPassApplication)
                    .container
                    .stepSource
                    .permissionRequests
                    .collect { request -> beginStepPermissionRequest(request) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val container = (application as PocketPassApplication).container
                container.pendingInstallConfirmation.collect { confirm ->
                    if (confirm == null) return@collect
                    container.pendingInstallConfirmation.value = null
                    runCatching { startActivity(confirm) }.onFailure {
                        container.appUpdate.onInstallFailed(
                            "Couldn't open the install prompt.",
                            aborted = false,
                        )
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (application as PocketPassApplication)
                    .container
                    .appUpdate
                    .installPermissionRequests
                    .collect {
                        unknownSourcesLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:$packageName"),
                            ),
                        )
                    }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (application as PocketPassApplication)
                    .container
                    .messages
                    .imageAttachmentRequested
                    .collect {
                        imageAttachmentLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                com.pocketpass.app.boards.BoardBrandingPicker.requests.collect { request ->
                    boardImageRequest = request
                    boardImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (application as PocketPassApplication)
                    .container
                    .connectedApps
                    .consentRedirects
                    .collect(::openConsentRedirect)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    viewModel.state
                        .map { state ->
                            backgroundMusicTrack(state) to state.soundLevel
                        }
                        .distinctUntilChanged()
                        .collect { (track, volume) ->
                            backgroundMusic.update(track, volume)
                        }
                } finally {
                    backgroundMusic.update(track = null, volume = 0f)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                soundEffects.foreground = true
                try {
                    viewModel.state
                        .map { state -> state.sfxLevel }
                        .distinctUntilChanged()
                        .collect { level -> soundEffects.volume = level }
                } finally {
                    soundEffects.foreground = false
                }
            }
        }
    }

    override fun onDestroy() {
        backgroundMusic.release()
        super.onDestroy()
    }

    private fun sendPickedImage(uri: Uri) {
        lifecycleScope.launch {
            val messages = (application as PocketPassApplication).container.messages
            when (val outcome = imageAttachmentPreparer.prepare(uri)) {
                is ImageAttachmentPreparation.Ready -> messages.sendImageAttachment(
                    localPath = outcome.attachment.path,
                    mimeType = outcome.attachment.mimeType,
                )

                is ImageAttachmentPreparation.Failed ->
                    messages.reportImageAttachmentFailure(outcome.message)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processAuthCallback(intent)
        consumeAppUpdateIntent(intent)
        consumeWidgetAssignIntent(intent)
    }

    private fun consumeWidgetAssignIntent(intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            CustomWidget.EXTRA_ASSIGN_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        intent.removeExtra(CustomWidget.EXTRA_ASSIGN_APPWIDGET_ID)
        (application as PocketPassApplication).container.requestWidgetAssignment(appWidgetId)
    }

    private fun consumeAppUpdateIntent(intent: Intent) {
        if (!intent.getBooleanExtra(UpdateNotifications.EXTRA_OPEN_APP_UPDATE, false)) return
        intent.removeExtra(UpdateNotifications.EXTRA_OPEN_APP_UPDATE)
        (application as PocketPassApplication).container.requestAppUpdateScreen()
    }

    override fun onStart() {
        super.onStart()
        (application as PocketPassApplication)
            .container
            .setAppForeground(true)
        viewModel.onAppOpened(
            intent.getBooleanExtra(NearbyNotifications.EXTRA_OPEN_REPAIR, false),
        )
        intent.removeExtra(NearbyNotifications.EXTRA_OPEN_REPAIR)
        consumeAppUpdateIntent(intent)
        consumeWidgetAssignIntent(intent)
        if (::displayCoordinator.isInitialized) {
            displayCoordinator.start()
            applyOrientationPolicy()
        }
    }

    override fun onResume() {
        super.onResume()
        enforceDefaultDisplay()
    }

    private fun applyOrientationPolicy() {
        val companion = displayCoordinator.companionAttached
        val target = if (companion) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_USER
        }
        if (requestedOrientation != target) requestedOrientation = target
        configureWindow(window, immersive = companion)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enforceDefaultDisplay()
        if (::displayCoordinator.isInitialized) {
            displayCoordinator.start()
        }
    }

    override fun onStop() {
        (application as PocketPassApplication)
            .container
            .setAppForeground(false)
        if (::displayCoordinator.isInitialized) {
            displayCoordinator.stop()
        }
        miiEditorRightStickHandler.release()
        boardCanvasJoystickHandler.release()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            miiEditorRightStickHandler.release()
            boardCanvasJoystickHandler.release()
        }
    }

    @SuppressLint("RestrictedApi")
    private fun forwardKeyEvent(event: KeyEvent): Boolean = dispatchKeyEvent(event)

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (
            handleMiiEditorGamepadKeyEvent(
                event = event,
                state = viewModel.state.value,
                dispatch = viewModel::dispatch,
            )
        ) {
            return true
        }
        if (
            handleActivitiesGamepadKeyEvent(
                event = event,
                state = viewModel.state.value,
                dispatch = viewModel::dispatch,
            )
        ) {
            return true
        }
        if (
            handleBackGamepadKeyEvent(
                event = event,
                state = viewModel.state.value,
                dispatch = viewModel::dispatch,
                focus = viewModel.controllerFocus,
            )
        ) {
            return true
        }
        if (
            handleNavigationGamepadKeyEvent(
                event = event,
                state = viewModel.state.value,
                dispatch = viewModel::dispatch,
                focus = viewModel.controllerFocus,
            )
        ) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val state = viewModel.state.value
        val orbited = miiEditorJoystickHandler.handle(event, state)
        val navigated = miiEditorRightStickHandler.handle(event)
        val panned = boardCanvasJoystickHandler.handle(event)
        if (orbited || navigated || panned) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) viewModel.controllerFocus.hide()
        return super.dispatchTouchEvent(event)
    }

    private fun enforceDefaultDisplay() {
        val currentDisplayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        if (currentDisplayId == Display.DEFAULT_DISPLAY) {
            removeStrandedDuplicateTasks()
            return
        }

        val healIntent = Intent(intent).apply {
            setClass(this@MainActivity, MainActivity::class.java)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        val options = ActivityOptions.makeBasic()
            .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            .toBundle()

        runCatching { startActivity(healIntent, options) }
    }

    private fun removeStrandedDuplicateTasks() {
        val activityManager =
            getSystemService(ActivityManager::class.java) ?: return
        activityManager.appTasks.forEach { task ->
            val info = runCatching { task.taskInfo }.getOrNull() ?: return@forEach
            if (
                info.taskId != taskId &&
                info.baseIntent.component?.className == MainActivity::class.java.name
            ) {
                runCatching { task.finishAndRemoveTask() }
            }
        }
    }

    private fun processAuthCallback(callbackIntent: Intent) {
        if (callbackIntent.action == com.pocketpass.app.push.BoardNotifications.ACTION_OPEN) {
            val account = callbackIntent.getStringExtra(com.pocketpass.app.push.MessageNotifications.EXTRA_ACCOUNT)
            val board = callbackIntent.getStringExtra(com.pocketpass.app.push.BoardNotifications.EXTRA_BOARD)
            val thread = callbackIntent.getStringExtra(com.pocketpass.app.push.BoardNotifications.EXTRA_THREAD)
            callbackIntent.removeExtra(com.pocketpass.app.push.MessageNotifications.EXTRA_ACCOUNT)
            callbackIntent.removeExtra(com.pocketpass.app.push.BoardNotifications.EXTRA_BOARD)
            callbackIntent.removeExtra(com.pocketpass.app.push.BoardNotifications.EXTRA_THREAD)
            if (account != null && board != null) (application as PocketPassApplication).container.requestBoard(account, board, thread)
        }
        if (callbackIntent.action == com.pocketpass.app.push.MessageNotifications.ACTION_OPEN) {
            val account = callbackIntent.getStringExtra(com.pocketpass.app.push.MessageNotifications.EXTRA_ACCOUNT)
            val conversation = callbackIntent.getStringExtra(com.pocketpass.app.push.MessageNotifications.EXTRA_CONVERSATION)
            callbackIntent.removeExtra(com.pocketpass.app.push.MessageNotifications.EXTRA_ACCOUNT)
            callbackIntent.removeExtra(com.pocketpass.app.push.MessageNotifications.EXTRA_CONVERSATION)
            if (account != null && conversation != null) {
                (application as PocketPassApplication).container.requestMessageConversation(account, conversation)
            }
        }
        if (callbackIntent.getBooleanExtra(NearbyNotifications.EXTRA_OPEN_REPAIR, false)) {
            viewModel.onAppOpened(openNearbyRepair = true)
        }
        if (callbackIntent.getBooleanExtra(NearbyNotifications.EXTRA_OPEN_ENCOUNTER, false)) {
            viewModel.dispatch(
                PocketPassEvent.SelectDestination(PocketPassDestination.Home),
            )
            callbackIntent.removeExtra(NearbyNotifications.EXTRA_OPEN_ENCOUNTER)
        }
        val callback = when (
            val decision = AuthCallbackPolicy.evaluate(callbackIntent.dataString)
        ) {
            is AuthCallbackDecision.AuthorizationCode -> callbackIntent.dataString
            is AuthCallbackDecision.ProviderError -> callbackIntent.dataString
            is AuthCallbackDecision.Ignored -> null
        }
        callback?.let(viewModel::handleAuthCallback)
        when (val consent = ConsentLinkPolicy.evaluate(callbackIntent.dataString)) {
            is ConsentLinkDecision.Consent -> viewModel.handleConsentLink(consent.authorizationId)
            ConsentLinkDecision.Ignored -> Unit
        }
    }

    private fun openConsentRedirect(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }

    private fun beginStepPermissionRequest(request: AndroidStepPermissionRequest) {
        if (request == AndroidStepPermissionRequest.HealthConnect) {
            healthStepsPermissionLauncher.launch(setOf(HealthConnectStepReader.READ_STEPS))
            return
        }
        if (StepRewardsPermissionPolicy.isGranted(this)) {
            viewModel.onStepRewardsPermissionResult()
            return
        }
        stepPermissionLauncher.launch(StepRewardsPermissionPolicy.requiredPermission())
    }

    private fun beginNearbyPermissionRequest() {
        val missingForeground = NearbyPermissionPolicy.foregroundPermissions()
            .filter { permission ->
                ContextCompat.checkSelfPermission(this, permission) !=
                    PackageManager.PERMISSION_GRANTED
            }
        if (missingForeground.isNotEmpty()) {
            nearbyPermissionStage = NearbyPermissionStage.Foreground
            nearbyPermissionLauncher.launch(missingForeground.toTypedArray())
            return
        }
        requestLegacyBackgroundOrFinish()
    }

    private fun requestLegacyBackgroundOrFinish() {
        val backgroundPermission =
            NearbyPermissionPolicy.backgroundLocationPermission()
        if (
            backgroundPermission != null &&
            ContextCompat.checkSelfPermission(this, backgroundPermission) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            nearbyPermissionStage = NearbyPermissionStage.Background
            nearbySettingsLauncher.launch(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ),
            )
            return
        }
        when {
            !NearbyPermissionPolicy.isLegacyLocationEnabled(this) -> {
                nearbyPermissionStage = NearbyPermissionStage.Background
                nearbySettingsLauncher.launch(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                )
            }

            !NearbyPermissionPolicy.isBluetoothEnabled(this) -> {
                nearbyPermissionStage = NearbyPermissionStage.Background
                nearbySettingsLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }

            else -> finishNearbyPermissionRequest()
        }
    }

    private fun finishNearbyPermissionRequest() {
        nearbyPermissionStage = NearbyPermissionStage.Idle
        viewModel.onNearbyPermissionResult()
        offerBatteryExemptionOnce()
    }

    private fun offerBatteryExemptionOnce() {
        if (BatteryOptimizationExemption.wasRequested(this)) return
        val nearbyGranted = NearbyPermissionPolicy.foregroundPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (!nearbyGranted) return
        BatteryOptimizationExemption.markRequested(this)
        if (BatteryOptimizationExemption.isGranted(this)) return
        runCatching { batteryExemptionLauncher.launch(BatteryOptimizationExemption.requestIntent(this)) }
    }

    private fun configureWindow(targetWindow: android.view.Window, immersive: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(targetWindow, false)
        targetWindow.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        targetWindow.preferSixtyHertz(display)
        WindowInsetsControllerCompat(targetWindow, targetWindow.decorView).apply {
            if (immersive) {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                hide(WindowInsetsCompat.Type.statusBars())
                show(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    private enum class NearbyPermissionStage {
        Idle,
        Foreground,
        Background,
    }
}
