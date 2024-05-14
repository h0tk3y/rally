package com.h0tk3y.rally.android.scenes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@ExperimentalComposeUiApi
@Composable
fun CalibrationFactorDialog(
    onDismiss: () -> Unit,
    originalValue: Double,
    onSave: (Double) -> Unit
) {
    var valueString by rememberSaveable { mutableStateOf(originalValue.toString()) }
    var textFieldValueState by remember {
        mutableStateOf(
            TextFieldValue(
                text = valueString,
                selection = TextRange(valueString.length)
            )
        )
    }
    var isErrorInvalidValue by rememberSaveable { mutableStateOf(false) }

    fun trySave() {
        val factor = validateCalibrationFactor(valueString)
        if (factor == null) {
            isErrorInvalidValue = true
        } else {
            onSave(factor)
        }
    }

    DialogOnTop(onDismiss, title = "ODO Calibration") {
        val focusRequester = remember { FocusRequester() }

        Column(Modifier.padding(8.dp)) {
            val textModifier = Modifier.fillMaxWidth()
            Column(textModifier) {
                Text("The calibration factor is the distance measured by the odometer divided by the roadmap distance.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = textFieldValueState,
                    modifier = Modifier.focusRequester(focusRequester).fillMaxWidth(),
                    singleLine = true,
                    onValueChange = {
                        isErrorInvalidValue = validateCalibrationFactor(it.text) == null
                        valueString = it.text
                        textFieldValueState = it
                    },
                    isError = isErrorInvalidValue,
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                    label = { Text("Calibration factor") },
                )

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                if (isErrorInvalidValue) {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.height(48.dp)) {
                        Text(text = "Invalid number, should be between 0.1 and 10.0", color = MaterialTheme.colors.error)
                    }
                }
            }

            Row(
                modifier = textModifier,
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { trySave() },
                    enabled = valueString.isNotBlank() && !isErrorInvalidValue
                ) {
                    Text("Save")
                }
            }

        }
    }
}

private fun validateCalibrationFactor(string: String): Double? {
    val d = string.toDoubleOrNull() ?: return null
    return if (d in 0.01..10.0) d else null
}

sealed interface CalibrationDialogResult{
    object Ok : CalibrationDialogResult
    object InvalidValue : CalibrationDialogResult
}