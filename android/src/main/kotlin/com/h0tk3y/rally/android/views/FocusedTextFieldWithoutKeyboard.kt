package com.h0tk3y.rally.android.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontVariation.width
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.h0tk3y.rally.android.scenes.DataKind

@Composable
fun FocusedTextFieldWithoutKeyboard(
    text: String,
    kind: DataKind,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current,
    selectionPosition: Int = text.length,
    enabled: Boolean = true,
    focused: Boolean = false,
    onTextChange: (String) -> Unit = {},
    onFocused: () -> Unit = {},
    onPositionChange: (Int) -> Unit = {},
) {
    CompositionLocalProvider(
        LocalTextInputService provides null
    ) {
        val focusRequester = remember { FocusRequester() }

        if (focused) {
            LaunchedEffect(key1 = Unit) { focusRequester.requestFocus() }
        }

        val textFieldValue = TextFieldValue(
            text,
            selection = if (focused) TextRange(selectionPosition, selectionPosition) else TextRange.Zero
        )

        Box(modifier) {
            Spacer(Modifier.width(if (kind == DataKind.SyntheticCount) 40.dp else 50.dp))
            BasicTextField(
                enabled = enabled,
                textStyle = textStyle.copy(color = MaterialTheme.colors.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colors.onBackground),
                value = textFieldValue,
                readOnly = !focused,
                onValueChange = { value: TextFieldValue ->
                    onTextChange(value.text)
                    if (value.selection.collapsed) {
                        onPositionChange(value.selection.start)
                    }
                },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) onFocused() }
                    .width(IntrinsicSize.Min),
                singleLine = true,
            )
        }
    }
}