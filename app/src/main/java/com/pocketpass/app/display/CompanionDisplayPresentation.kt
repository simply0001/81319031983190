package com.pocketpass.app.display

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.navigationevent.setViewTreeNavigationEventDispatcherOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pocketpass.app.input.MiiEditorJoystickHandler
import com.pocketpass.app.input.MiiEditorRightStickHandler
import com.pocketpass.app.input.handleActivitiesGamepadKeyEvent
import com.pocketpass.app.input.handleBackGamepadKeyEvent
import com.pocketpass.app.input.handleMiiEditorGamepadKeyEvent
import com.pocketpass.app.input.handleNavigationGamepadKeyEvent
import com.pocketpass.app.mii.renderer.MiiRenderController
import com.pocketpass.app.state.PocketPassViewModel
import com.pocketpass.app.ui.BottomDisplayApp
import com.pocketpass.app.ui.TopDisplayApp

class CompanionDisplayPresentation(
    private val hostActivity: ComponentActivity,
    display: Display,
    private val viewModel: PocketPassViewModel,
) : Presentation(hostActivity, display) {

    private val miiEditorJoystickHandler by lazy {
        MiiEditorJoystickHandler(MiiRenderController.get(hostActivity)::postOrbit)
    }
    private val miiEditorRightStickHandler by lazy {
        MiiEditorRightStickHandler(
            state = { viewModel.state.value },
            sendKey = { dispatchKeyEvent(it) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setCancelable(false)

        window?.let { presentationWindow ->
            WindowCompat.setDecorFitsSystemWindows(presentationWindow, false)
            presentationWindow.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            presentationWindow.preferSixtyHertz(display)
            presentationWindow.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            presentationWindow.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            WindowInsetsControllerCompat(
                presentationWindow,
                presentationWindow.decorView,
            ).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(hostActivity)
            setViewTreeSavedStateRegistryOwner(hostActivity)
            setViewTreeViewModelStoreOwner(hostActivity)
            setViewTreeNavigationEventDispatcherOwner(hostActivity)
            setContent {
                if (DisplayRoles.defaultDisplayIsBottomPanel) {
                    TopDisplayApp(viewModel = viewModel)
                } else {
                    BottomDisplayApp(viewModel = viewModel)
                }
            }
        }

        setContentView(composeView)
    }

    override fun onStop() {
        miiEditorRightStickHandler.release()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) miiEditorRightStickHandler.release()
    }

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
        if (orbited || navigated) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) viewModel.controllerFocus.hide()
        return super.dispatchTouchEvent(event)
    }
}
