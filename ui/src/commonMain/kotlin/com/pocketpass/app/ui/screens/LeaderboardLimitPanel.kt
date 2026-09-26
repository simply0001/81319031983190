package com.pocketpass.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.pocketpass.app.data.GlobalLeaderboardLimits
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.components.PocketPanel
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.theme.pocketPalette

@Composable
internal fun LeaderboardLimitPanel(metrics: DesignMetrics, y: Float, state: PocketPassUiState, dispatch: (PocketPassEvent) -> Unit) {
    PocketPanel(metrics, 50f, y, 1140f, 250f,
        borderColor = pocketPalette.borderGrey, borderWidth = 20.152f, radius = 90f,
        fillBrush = greyPanelBrush(), modifier = Modifier.selectableGroup()) {
        Text("Global Players Shown", fontFamily = Rubik, fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(64f), color = pocketPalette.textPrimary,
            modifier = Modifier.designBounds(metrics, 55f, 30f, 1030f, 80f))
        GlobalLeaderboardLimits.forEachIndexed { index, limit ->
            val selected = state.leaderboard.globalLimit == limit
            val choose = { dispatch(PocketPassEvent.SetGlobalLeaderboardLimit(limit)) }
            Box(Modifier.designBounds(metrics, 55f + index * 265f, 125f, 235f, 85f)
                .pocketFrame(if (selected) greenButtonBrush() else SolidColor(pocketPalette.surface),
                    metrics.dp(9f), if (selected) ThemeChoiceGreen else pocketPalette.borderGrey,
                    RoundedCornerShape(metrics.dp(42.5f)))
                .testTag("leaderboard_limit_$limit")
                .controllerTarget("leaderboard_limit_$limit", layer = 20, onActivate = choose)
                .selectable(selected, role = Role.RadioButton, onClick = choose),
                contentAlignment = Alignment.Center) {
                Text(limit.toString(), fontFamily = Rubik, fontWeight = FontWeight.Bold,
                    fontSize = metrics.sp(48f), color = if (selected) androidx.compose.ui.graphics.Color.White else pocketPalette.textPrimary)
            }
        }
    }
}
