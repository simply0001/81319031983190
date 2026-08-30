package com.pocketpass.app.mii.renderer

import android.graphics.Color
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun rememberMiiRenderController(): MiiRenderController {
    val context = LocalContext.current
    return remember(context.applicationContext) {
        MiiRenderController.get(context.applicationContext)
    }
}

@Composable
fun MiiRenderSurface(
    controller: MiiRenderController,
    modifier: Modifier = Modifier,
    initialCanonicalBase64: String = MiiRenderController.DEFAULT_MII_BASE64,
) {
    val context = LocalContext.current
    val webView = remember(context, controller) {
        controller.createWebView(context)
    }

    DisposableEffect(webView, controller) {
        controller.attach(webView, initialCanonicalBase64)
        onDispose {
            controller.detach(webView)
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier,
        update = {
            it.setBackgroundColor(Color.TRANSPARENT)
            it.visibility = View.VISIBLE
        },
    )
}
