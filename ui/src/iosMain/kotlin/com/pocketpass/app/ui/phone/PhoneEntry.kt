package com.pocketpass.app.ui.phone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import com.pocketpass.app.model.PocketPassExtensions
import com.pocketpass.app.state.IosAppContainer
import com.pocketpass.app.state.IosStatusFeed
import com.pocketpass.app.state.PocketPassStore
import com.pocketpass.app.ui.LocalAppVersionName
import com.pocketpass.app.ui.PocketPassTheme
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

private val container by lazy { IosAppContainer() }
private val store by lazy {
    PocketPassStore(
        container = container,
        statusFeed = IosStatusFeed(),
        scope = container.applicationScope,
    )
}

private fun bundleVersionName(): String =
    NSBundle.mainBundle.infoDictionary
        ?.get("CFBundleShortVersionString") as? String
        ?: ""

// The iOS application's root, called from the Swift AppDelegate.
fun PhoneAppViewController(): UIViewController = ComposeUIViewController {
    IosPhoneApp()
}

@Composable
private fun IosPhoneApp() {
    val state by store.state.collectAsState()
    CompositionLocalProvider(
        LocalAppVersionName provides bundleVersionName(),
    ) {
        PocketPassTheme(state.themeMode) {
            PhoneSurface { metrics ->
                PhoneRoot(
                    metrics = metrics,
                    state = state,
                    dispatch = store::dispatch,
                    miiEditorController = container.miiEditor,
                    extensions = PocketPassExtensions.None,
                )
            }
        }
    }
}
