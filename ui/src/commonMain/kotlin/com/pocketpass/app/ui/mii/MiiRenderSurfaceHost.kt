package com.pocketpass.app.ui.mii

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.pocketpass.app.mii.MiiEditorController

val LocalMiiRenderSurface = staticCompositionLocalOf<
    (@Composable (
        controller: MiiEditorController,
        initialCanonicalBase64: String?,
        modifier: Modifier,
    ) -> Unit)?,
> { null }
