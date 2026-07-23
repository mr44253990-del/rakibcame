package com.example

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.ControllerMapperScreen
import com.example.ui.ControllerMapperViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ControllerMapperViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ControllerMapperScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val source = event.source
        val isGamepad = (source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
            (source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
        return if (isGamepad && viewModel.onControllerKeyEvent(event)) true else super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val source = ev.source
        val isJoystick = (source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
        return if (isJoystick && viewModel.onControllerMotionEvent(ev)) true else super.dispatchGenericMotionEvent(ev)
    }
}
