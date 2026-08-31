package com.pocketpass.app.ui.mii

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.pocketpass.app.mii.MiiEditorController

/**
 * The live Mii render surface is platform machinery (a WebView on Android), so each
 * platform's entry point injects it here. Screens render nothing when it is absent.
 */
val LocalMiiRenderSurface = staticCompositionLocalOf<
    (@Composable (
        controller: MiiEditorController,
        initialCanonicalBase64: String?,
        modifier: Modifier,
    ) -> Unit)?,
> { null }
