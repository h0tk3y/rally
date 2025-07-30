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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.h0tk3y.rally.android.theme.LocalCustomTypography

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
    isError: (String) -> Boolean = { it.toDoubleOrNull() == null }
) {
    val source = remember { MutableInteractionSource() }

    BasicTextField(
        value = text.value,
        onValueChange = { it: TextFieldValue ->
            text.value = it.copy(text = it.text.filter { char ->
                char.isDigit() || char == '.' || isAllowMinus && char == '-' || isAllowColon && char == ':'
            })
            onChange(it)
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
            .onFocusChanged { if (it.isFocused) text.value = TextFieldValue(text.value.text, TextRange(0, text.value.text.length)) }
            .height(45.dp)
            .width(70.dp),
        interactionSource = source,
        visualTransformation = if (text.value.text.isEmpty())
            VisualTransformation.None
        else if (suffix != null) SuffixTransformation(suffix) else VisualTransformation.None,
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
                visualTransformation = VisualTransformation.None,
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
