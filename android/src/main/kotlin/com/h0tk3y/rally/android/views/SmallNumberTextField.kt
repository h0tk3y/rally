package com.h0tk3y.rally.android.views

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults.TextFieldDecorationBox
import androidx.compose.material.TextFieldDefaults.indicatorLine
import androidx.compose.material.TextFieldDefaults.textFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.h0tk3y.rally.android.theme.LocalCustomTypography
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun SmallNumberTextField(
    modifier: Modifier,
    text: MutableState<TextFieldValue>,
    onChange: (TextFieldValue) -> Unit = { },
    placeholderString: String?,
    suffix: String?,
    addSuffixToPlaceholder: Boolean = false,
    focusRequester: FocusRequester = FocusRequester(),
    isAllowMinus: Boolean = false,
    isAllowColon: Boolean = false,
    selectAllOnFocus: Boolean = false,
    isError: (String) -> Boolean = { it.toDoubleOrNull() == null }
) {
    val source = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()
    var wasFocused by remember { mutableStateOf(false) }

    val visualTransformation = if (text.value.text.isEmpty())
        VisualTransformation.None
    else if (suffix != null) SuffixTransformation(suffix) else VisualTransformation.None

    BasicTextField(
        value = text.value,
        onValueChange = { it: TextFieldValue ->
            val filteredText = it.text.filter { char ->
                char.isDigit() || char == '.' || isAllowMinus && char == '-' || isAllowColon && char == ':'
            }
            if (selectAllOnFocus && filteredText == text.value.text) {
                val fullSelection = TextRange(0, filteredText.length)
                if (text.value.selection == fullSelection && it.selection != fullSelection) {
                    text.value = it.copy(text = filteredText)
                    coroutineScope.launch {
                        text.value = it.copy(text = filteredText, selection = fullSelection)
                        onChange(text.value)
                    }
                } else {
                    text.value = it.copy(text = filteredText, selection = fullSelection)
                    onChange(text.value)
                }
            } else {
                text.value = it.copy(text = filteredText)
                onChange(text.value)
            }
        },
        textStyle = androidx.compose.ui.text.TextStyle(brush = SolidColor(MaterialTheme.colors.onSurface))
            .plus(LocalCustomTypography.current.raceEditableValue),
        cursorBrush = SolidColor(MaterialTheme.colors.onSurface),
        modifier = modifier
            .indicatorLine(
                enabled = true,
                isError = text.value.text.let { isError(it) },
                interactionSource = source,
                colors = textFieldColors(backgroundColor = Color.Transparent),
                focusedIndicatorLineThickness = 2.dp,
                unfocusedIndicatorLineThickness = 2.dp
            )
            .focusRequester(focusRequester)
            .pointerInput(selectAllOnFocus) {
                if (!selectAllOnFocus) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            focusRequester.requestFocus()
                            val currentText = text.value.text
                            val fullSelection = TextRange(0, currentText.length)
                            if (text.value.selection == fullSelection) {
                                text.value = text.value.copy(selection = TextRange.Zero)
                            }
                            coroutineScope.launch {
                                text.value = text.value.copy(selection = fullSelection)
                                onChange(text.value)
                            }
                        }
                    }
                }
            }
            .onFocusChanged {
                if (it.isFocused && !wasFocused) {
                    wasFocused = true
                    if (selectAllOnFocus) {
                        val currentText = text.value.text
                        text.value = text.value.copy(selection = TextRange(0, currentText.length))
                    }
                } else if (!it.isFocused) {
                    wasFocused = false
                }
            }
            .height(45.dp)
            .width(70.dp),
        interactionSource = source,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = true,
        singleLine = true,
        decorationBox = {
            TextFieldDecorationBox(
                value = text.value.text,
                isError = text.value.text.let(isError),
                innerTextField = it,
                singleLine = true,
                enabled = true,
                visualTransformation = visualTransformation,
                placeholder = {
                    if (placeholderString != null) {
                        Text(
                            text = placeholderString + if (addSuffixToPlaceholder) suffix.orEmpty() else "",
                            style = LocalCustomTypography.current.raceEditableValue,
                        )
                    }
                },
                colors = textFieldColors(backgroundColor = Color.Transparent),
                interactionSource = source,
                contentPadding = PaddingValues(4.dp, 0.dp)
            )
        }
    )
}

private class SuffixTransformation(val suffix: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {

        val result = text + AnnotatedString(suffix)

        val textWithSuffixMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return offset
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (text.isEmpty()) return 0
                if (offset >= text.length) return text.length
                return offset
            }
        }

        return TransformedText(result, textWithSuffixMapping)
    }
}
