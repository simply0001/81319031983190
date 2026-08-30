package com.pocketpass.spike

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

const val DEFAULT_MII_BASE64 =
    "BAXGigDvV8wSNID/cJl869TJwxYAAAAAAAAAAAAAAAAAAAAAAAAAAE0AaQBpAAAAAAAAAAAAAAAAAAAA" +
        "CAAAAAAAQAMDAQYEBgIKCAQEAgIMAAAAAP8AAAAACAQACgEAIf///0AABAACFAMTBBcNBAAKBAEJ//8A/wAAAP//"

fun ProbeViewController(): UIViewController = ComposeUIViewController {
    val log = remember { mutableStateListOf<String>() }
    ProbeApp(
        rendererSlot = { modifier ->
            MiiRendererSurface(
                canonicalBase64 = DEFAULT_MII_BASE64,
                modifier = modifier,
                onMessage = { message ->
                    if (log.size > 32) log.removeAt(0)
                    log.add(message)
                },
            )
        },
        rendererLog = log,
    )
}
