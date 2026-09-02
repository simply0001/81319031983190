package com.pocketpass.app.ui.phone

import com.pocketpass.app.ui.screens.StepRewardsPanel
import com.pocketpass.app.ui.PocketAsset
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.pocketpass.app.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassExtensions
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.components.EntranceMotion
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.MotionLayer
import com.pocketpass.app.ui.components.PocketPanel
import com.pocketpass.app.ui.components.rememberGearRotation
import com.pocketpass.app.ui.screens.AccessibilityPanel
import com.pocketpass.app.ui.screens.AppUpdateStatusPanel
import com.pocketpass.app.ui.screens.CREDITS_PANEL_HEIGHT
import com.pocketpass.app.ui.screens.CreditsPanel
import com.pocketpass.app.ui.screens.DeletePanel
import com.pocketpass.app.ui.screens.ConnectedAppsPanel
import com.pocketpass.app.ui.screens.EditMiiPanel
import com.pocketpass.app.ui.screens.EditNamePanel
import com.pocketpass.app.ui.screens.LogoutPanel
import com.pocketpass.app.ui.screens.NearbyPanel
import com.pocketpass.app.ui.screens.NearbyToggle
import com.pocketpass.app.ui.screens.NotificationsPanel
import com.pocketpass.app.ui.screens.OVERLAY_POP_BASE_DELAY_MILLIS
import com.pocketpass.app.ui.screens.OVERLAY_POP_STAGGER_MILLIS
import com.pocketpass.app.ui.screens.SETTINGS_ROW_HEIGHT
import com.pocketpass.app.ui.screens.SETTINGS_TALL_HEIGHT
import com.pocketpass.app.ui.screens.SettingsHeading
import com.pocketpass.app.ui.screens.SocialPanel
import com.pocketpass.app.ui.screens.SOUND_PANEL_HEIGHT
import com.pocketpass.app.ui.screens.SoundPanel
import com.pocketpass.app.ui.screens.THEME_PANEL_HEIGHT
import com.pocketpass.app.ui.screens.ThemePanel
import com.pocketpass.app.ui.screens.VersionPanel
import com.pocketpass.app.ui.screens.greyPanelBrush
import com.pocketpass.app.ui.theme.pocketPalette
import com.pocketpass.app.update.AppUpdatePhase
import com.pocketpass.app.update.releaseNoteLines

@Composable
fun PhoneSettingsTab(
    metrics: DesignMetrics,
    panes: WidePanes?,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    if (panes == null) {
        PhoneSettingsList(metrics, state, dispatch, titled = true)
    } else {
        PhonePanes(
            metrics = metrics,
            panes = panes,
            stage = { PhoneStageScroll(metrics) { PhoneSettingsGear(metrics) } },
            deck = {
                AnimatedContent(
                    targetState = state.routes.lastOrNull()?.takeUnless { it is PocketPassRoute.Root },
                    transitionSpec = {
                        val pushing = targetState != null
                        (fadeIn(tween(200)) + slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { if (pushing) it / 5 else -it / 5 })
                            .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { if (pushing) -it / 5 else it / 5 })
                    },
                    label = "Settings deck",
                ) { route ->
                    if (route == null) {
                        PhoneSettingsList(metrics, state, dispatch, titled = false)
                    } else {
                        PhoneSettingsSubpage(metrics, route, state, dispatch)
                    }
                }
            },
        )
    }
}

@Composable
private fun PhoneSettingsList(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    titled: Boolean,
) {
    val insets = LocalPhoneInsets.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("settings_scroll")
            .padding(top = metrics.dp(insets.top + 40f), bottom = metrics.dp(60f)),
    ) {
        if (titled) {
            PhoneSectionHeader(metrics, "Settings", pocketPalette.textPrimary)
            Spacer(Modifier.height(metrics.dp(40f)))
        }
        var order = 0
        val slot: @Composable (Float, @Composable () -> Unit) -> Unit = { height, content ->
            MotionLayer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.dp(height)),
                entrance = EntranceMotion.OverlayPop,
                delayMillis = OVERLAY_POP_BASE_DELAY_MILLIS + order * OVERLAY_POP_STAGGER_MILLIS,
                transformOrigin = TransformOrigin(0.5f, 0.5f),
            ) { content() }
            order += 1
            Spacer(Modifier.height(metrics.dp(50f)))
        }
        slot(SETTINGS_ROW_HEIGHT) {
            NearbyPanel(metrics, 0f, state.nearbyEnabled) { dispatch(PocketPassEvent.SetNearby(!state.nearbyEnabled)) }
        }
        if (state.stepRewards.supported) {
            slot(SETTINGS_ROW_HEIGHT) {
                StepRewardsPanel(metrics, 0f, state, dispatch)
            }
        }
        slot(SOUND_PANEL_HEIGHT) {
            SoundPanel(
                metrics = metrics,
                y = 0f,
                musicLevel = state.soundLevel,
                sfxLevel = state.sfxLevel,
                onMusicLevelChange = { dispatch(PocketPassEvent.SetSoundLevel(it)) },
                onSfxLevelChange = { dispatch(PocketPassEvent.SetSfxLevel(it)) },
            )
        }
        slot(SETTINGS_ROW_HEIGHT) {
            NotificationsPanel(metrics, 0f) { dispatch(PocketPassEvent.OpenNotificationSettings) }
        }
        slot(THEME_PANEL_HEIGHT) {
            ThemePanel(
                metrics = metrics,
                y = 0f,
                selected = state.themeMode,
                expanded = state.themePickerExpanded,
                onExpand = { dispatch(PocketPassEvent.OpenThemePicker) },
            ) { dispatch(PocketPassEvent.SetThemeMode(it)) }
        }
        slot(SETTINGS_ROW_HEIGHT) {
            SocialPanel(metrics, 0f) { dispatch(PocketPassEvent.OpenSocial) }
        }
        slot(SETTINGS_ROW_HEIGHT) {
            AccessibilityPanel(metrics, 0f) { dispatch(PocketPassEvent.OpenAccessibility) }
        }
        slot(SETTINGS_ROW_HEIGHT) {
            VersionPanel(metrics, 0f, state.appUpdate) { dispatch(PocketPassEvent.OpenAppUpdate) }
        }
        slot(SETTINGS_ROW_HEIGHT) {
            LogoutPanel(metrics, 0f) { dispatch(PocketPassEvent.SignOut) }
        }
        slot(SETTINGS_TALL_HEIGHT) {
            DeletePanel(metrics, 0f) { dispatch(PocketPassEvent.OpenDeleteAccount) }
        }
        slot(CREDITS_PANEL_HEIGHT) {
            CreditsPanel(metrics, 0f)
        }
    }
}

@Composable
internal fun PhoneSettingsGear(metrics: DesignMetrics) {
    val rotation by rememberGearRotation()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MotionLayer(entrance = EntranceMotion.SettingsTurn) {
            Box(Modifier.requiredSize(metrics.dp(535.119f), metrics.dp(584.074f))) {
                FigmaAsset(
                    resource = Assets.SettingsGearShadow,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = metrics.dp(8f).toPx()
                            translationY = metrics.dp(18f).toPx()
                            rotationZ = rotation
                            transformOrigin = TransformOrigin(268.88f / 535.119f, 280.232f / 584.074f)
                        }
                        .blur(metrics.dp(5f), BlurredEdgeTreatment.Unbounded)
                        .alpha(0.52f),
                )
                FigmaAsset(
                    resource = Assets.SettingsGear,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = rotation
                            transformOrigin = TransformOrigin(268.88f / 535.119f, 280.232f / 584.074f)
                        },
                )
            }
        }
        Spacer(Modifier.height(metrics.dp(30f)))
        MotionLayer(entrance = EntranceMotion.TextRise, delayMillis = 110) {
            Text(
                text = "Settings",
                color = pocketPalette.ink(Color(0xFF4E4E4E)),
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(128f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun PhoneRoutePage(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    extensions: PocketPassExtensions,
) {
    val current = state.routes.lastOrNull()
    val shown = remember { mutableStateOf(current) }
    if (current != null && current !is PocketPassRoute.Root) shown.value = current
    when (val route = shown.value) {
        is PocketPassRoute.MessageDetail -> Box(Modifier.fillMaxSize()) { PhoneThread(metrics, state, dispatch, extensions) }
        is PocketPassRoute.NewGroup -> Box(Modifier.fillMaxSize()) { PhoneNewGroupPage(metrics, state, dispatch) }
        null, is PocketPassRoute.Root -> Unit
        else -> PhoneSettingsSubpage(metrics, route, state, dispatch)
    }
}

@Composable
private fun PhoneSettingsSubpage(
    metrics: DesignMetrics,
    route: PocketPassRoute,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    when (route) {
        PocketPassRoute.Accessibility -> PhoneSubpage(
            metrics = metrics,
            title = "Accessibility",
            subtitle = "Tune PocketPass' visual effects.",
            backTag = "accessibility_back",
            onBack = { dispatch(PocketPassEvent.Back) },
        ) {
            PhoneToggleRow(
                metrics = metrics,
                icon = Assets.SettingsMoodEmojis,
                title = "Mood Emojis",
                subtitle = "Float up when you set a mood",
                enabled = state.moodEmojisEnabled,
                tag = "mood_emoji_toggle",
                order = 0,
            ) { dispatch(PocketPassEvent.SetMoodEmojisEnabled(!state.moodEmojisEnabled)) }
            if (state.encounterLedSupported) {
                PhoneToggleRow(
                    metrics = metrics,
                    icon = Assets.SettingsEncounterLed,
                    title = "Encounter Lights",
                    subtitle = "Pulse when you pass someone",
                    enabled = state.encounterLedEnabled,
                    tag = "encounter_led_toggle",
                    order = 1,
                ) { dispatch(PocketPassEvent.SetEncounterLedEnabled(!state.encounterLedEnabled)) }
            }
        }

        PocketPassRoute.Social -> PhoneSubpage(
            metrics = metrics,
            title = "Social",
            subtitle = "Your name, Piip and connected apps.",
            backTag = "social_back",
            onBack = { dispatch(PocketPassEvent.Back) },
        ) {
            PhoneSubpageRow(metrics, order = 0) {
                EditNamePanel(metrics, 0f) { dispatch(PocketPassEvent.OpenNameEditor) }
            }
            if (state.miiEditorEnabled) {
                PhoneSubpageRow(metrics, order = 1) {
                    EditMiiPanel(metrics, 0f) { dispatch(PocketPassEvent.OpenMiiSlots) }
                }
            }
            if (state.connectedApps.enabled) {
                PhoneSubpageRow(metrics, order = if (state.miiEditorEnabled) 2 else 1) {
                    ConnectedAppsPanel(metrics, 0f) { dispatch(PocketPassEvent.OpenConnectedApps) }
                }
            }
        }

        PocketPassRoute.NotificationSettings -> PhoneSubpage(
            metrics = metrics,
            title = "Notifications",
            subtitle = "Which events send alerts.",
            backTag = "notification_settings_back",
            onBack = { dispatch(PocketPassEvent.Back) },
        ) {
            PhoneToggleRow(
                metrics = metrics,
                icon = Assets.SettingsEncounterAlerts,
                title = "Encounter Alerts",
                subtitle = "Notify when you pass someone",
                enabled = state.encounterAlertsEnabled,
                tag = "encounter_alerts_toggle",
                order = 0,
            ) { dispatch(PocketPassEvent.SetEncounterAlertsEnabled(!state.encounterAlertsEnabled)) }
            PhoneToggleRow(
                metrics = metrics,
                icon = Assets.SettingsRepairAlerts,
                title = "Repair Alerts",
                subtitle = "Warn if Nearby stops working",
                enabled = state.nearbyRepairAlertsEnabled,
                tag = "repair_alerts_toggle",
                order = 1,
            ) { dispatch(PocketPassEvent.SetNearbyRepairAlertsEnabled(!state.nearbyRepairAlertsEnabled)) }
            PhoneToggleRow(
                metrics = metrics,
                icon = Assets.SettingsVersion,
                title = "Update Alerts",
                subtitle = "Tell me about new versions",
                enabled = state.updateAlertsEnabled,
                tag = "update_alerts_toggle",
                order = 2,
            ) { dispatch(PocketPassEvent.SetUpdateAlertsEnabled(!state.updateAlertsEnabled)) }
        }

        PocketPassRoute.AppUpdate -> {
            LaunchedEffect(Unit) {
                if (state.appUpdate.phase is AppUpdatePhase.Idle) dispatch(PocketPassEvent.CheckForAppUpdate)
            }
            PhoneSubpage(
                metrics = metrics,
                title = "App Update",
                subtitle = "Keep PocketPass fresh.",
                backTag = "app_update_back",
                onBack = { dispatch(PocketPassEvent.Back) },
            ) {
                MotionLayer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(metrics.dp(428f)),
                    entrance = EntranceMotion.OverlayPop,
                    delayMillis = OVERLAY_POP_BASE_DELAY_MILLIS,
                ) {
                    AppUpdateStatusPanel(metrics = metrics, appUpdate = state.appUpdate, y = 0f, dispatch = dispatch)
                }
                val notes = state.appUpdate.manifest?.notes
                if (state.appUpdate.updateAvailable && !notes.isNullOrBlank()) {
                    Spacer(Modifier.height(metrics.dp(50f)))
                    MotionLayer(
                        modifier = Modifier.fillMaxWidth(),
                        entrance = EntranceMotion.OverlayPop,
                        delayMillis = OVERLAY_POP_BASE_DELAY_MILLIS + OVERLAY_POP_STAGGER_MILLIS,
                    ) {
                        Column(
                            Modifier
                                .padding(horizontal = metrics.dp(PHONE_CONTENT_MARGIN))
                                .fillMaxWidth()
                                .phonePanel(metrics, radius = 110f)
                                .testTag("app_update_notes")
                                .padding(horizontal = metrics.dp(70f), vertical = metrics.dp(40f)),
                        ) {
                            Text(
                                text = "What's New",
                                color = pocketPalette.textPrimary,
                                fontFamily = Rubik,
                                fontWeight = FontWeight.Bold,
                                fontSize = metrics.sp(48f),
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(metrics.dp(16f)))
                            val lines = remember(notes) { releaseNoteLines(notes) }
                            lines.forEach { line ->
                                Text(
                                    text = line,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = metrics.dp(3f)),
                                    color = pocketPalette.textSecondary,
                                    fontFamily = Rubik,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = metrics.sp(36f),
                                )
                            }
                        }
                    }
                }
            }
        }

        else -> Unit
    }
}

@Composable
internal fun PhoneSubpage(
    metrics: DesignMetrics,
    title: String,
    subtitle: String?,
    backTag: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val insets = LocalPhoneInsets.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = metrics.dp(insets.top + 24f), bottom = metrics.dp(insets.bottom + 60f)),
    ) {
        PhonePageHeader(metrics, title, subtitle, backTag, onBack)
        Spacer(Modifier.height(metrics.dp(40f)))
        content()
    }
}

@Composable
private fun PhoneSubpageRow(
    metrics: DesignMetrics,
    order: Int,
    content: @Composable () -> Unit,
) {
    MotionLayer(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.dp(SETTINGS_ROW_HEIGHT)),
        entrance = EntranceMotion.OverlayPop,
        delayMillis = OVERLAY_POP_BASE_DELAY_MILLIS + order * OVERLAY_POP_STAGGER_MILLIS,
    ) { content() }
    Spacer(Modifier.height(metrics.dp(50f)))
}

@Composable
private fun PhoneToggleRow(
    metrics: DesignMetrics,
    icon: PocketAsset,
    title: String,
    subtitle: String,
    enabled: Boolean,
    tag: String,
    order: Int,
    onToggle: () -> Unit,
) {
    MotionLayer(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.dp(SETTINGS_ROW_HEIGHT)),
        entrance = EntranceMotion.OverlayPop,
        delayMillis = OVERLAY_POP_BASE_DELAY_MILLIS + order * OVERLAY_POP_STAGGER_MILLIS,
    ) {
        PocketPanel(
            metrics = metrics,
            x = 50f,
            y = 0f,
            width = 1140f,
            height = SETTINGS_ROW_HEIGHT,
            borderColor = pocketPalette.borderGrey,
            borderWidth = PHONE_PANEL_BORDER,
            radius = 110f,
            fillBrush = greyPanelBrush(),
            tag = tag,
            onClick = onToggle,
        ) {
            SettingsHeading(metrics = metrics, icon = icon, title = title, subtitle = subtitle)
            NearbyToggle(metrics = metrics, enabled = enabled)
        }
    }
    Spacer(Modifier.height(metrics.dp(50f)))
}
