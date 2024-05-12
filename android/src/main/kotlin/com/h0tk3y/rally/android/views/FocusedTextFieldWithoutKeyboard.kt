package com.h0tk3y.rally.android.views

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.h0tk3y.rally.android.scenes.DataKind
import com.h0tk3y.rally.android.theme.LocalCustomTypography

@Composable
fun FocusedTextFieldWithoutKeyboard(
    text: String,
    kind: DataKind,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalCustomTypography.current.dataTextStyle,
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
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current

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
                    .widthIn(if (kind == DataKind.Distance) 45.dp else Dp.Unspecified)
                    .width(with(density) { measurer.measure(text, textStyle, density = density, constraints = Constraints()).size.width.toDp() + 4.dp })
                    .disabledHorizontalPointerInputScroll(),
                singleLine = true,
                decorationBox = { fn ->
                    Box(propagateMinConstraints = true) { fn() }
                }
            )
        }
    }
}

private fun measure(
    density: Density,
    measurer: TextMeasurer,
    style: TextStyle,
    text: String
) = with(density) {
    measurer.measure(text, style = style, constraints = Constraints(), density = density).size.width.toDp() * 2
}.also { Log.i("measurer", "measured: $it") }

private fun DataKind.width() = when (this) {
    DataKind.SyntheticCount -> 20.dp
    DataKind.AstroTime -> 70.dp
    else -> 40.dp
}


private val HorizontalScrollConsumer = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource) = available.copy(y = 0f)
    override suspend fun onPreFling(available: Velocity) = available.copy(y = 0f)
}

fun Modifier.disabledHorizontalPointerInputScroll(disabled: Boolean = true) =
    if (disabled) this.nestedScroll(HorizontalScrollConsumer) else this
