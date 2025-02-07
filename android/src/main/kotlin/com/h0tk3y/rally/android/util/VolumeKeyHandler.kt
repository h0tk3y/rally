package com.h0tk3y.rally.android.util

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent

@Composable
fun VolumeKeyHandler(onVolumeUp: () -> Unit, onVolumeDown: () -> Unit, content: @Composable () -> Unit) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                            onVolumeUp()
                            true
                        }
                        android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            onVolumeDown()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        content()
    }
}