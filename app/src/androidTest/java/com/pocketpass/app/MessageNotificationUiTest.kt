package com.pocketpass.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.feature.AccountSetupUiState
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassReducer
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.BottomDisplayContent
import com.pocketpass.app.ui.controller.ControllerFocus
import com.pocketpass.app.ui.controller.LocalControllerFocus
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class MessageNotificationUiTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(PocketPassUiState())

    private fun show(supported: Boolean) {
        state = PocketPassUiState(
            routes = listOf(PocketPassRoute.Root(PocketPassDestination.Settings), PocketPassRoute.NotificationSettings),
            sessionState = SessionState.Authenticated(FixtureData.CurrentUserId),
            accountSetup = AccountSetupUiState(resolved = true),
            messagePushSupported = supported,
        )
        compose.setContent {
            CompositionLocalProvider(LocalControllerFocus provides ControllerFocus()) {
                BottomDisplayContent(state = state, dispatch = { state = PocketPassReducer.reduce(state, it) })
            }
        }
    }

    @Test fun messageToggleWorksAndFourthRowIsReachable() {
        show(true)
        compose.onNodeWithTag("message_alerts_toggle").assertIsDisplayed().performClick()
        compose.runOnIdle { assertFalse(state.messageAlertsEnabled) }
        compose.onNodeWithTag("update_alerts_toggle").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertFalse(state.updateAlertsEnabled) }
    }

    @Test fun unconfiguredBuildDoesNotOfferAnUnavailablePushToggle() {
        show(false)
        compose.onNodeWithTag("message_alerts_toggle").assertDoesNotExist()
        compose.onNodeWithTag("encounter_alerts_toggle").assertIsDisplayed()
    }
}
