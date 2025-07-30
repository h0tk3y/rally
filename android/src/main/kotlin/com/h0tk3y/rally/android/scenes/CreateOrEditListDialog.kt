package com.h0tk3y.rally.android.scenes

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.h0tk3y.rally.DefaultModifierValidator
import com.h0tk3y.rally.DistanceKm
import com.h0tk3y.rally.InputRoadmapParser
import com.h0tk3y.rally.SpeedKmh
import com.h0tk3y.rally.SpeedKmh.Companion.averageAt
import com.h0tk3y.rally.TimeDayHrMinSec
import com.h0tk3y.rally.TimeHr
import com.h0tk3y.rally.android.theme.Typography
import com.h0tk3y.rally.android.views.LeftAlignedRow
import com.h0tk3y.rally.android.views.SmallNumberTextField
import com.h0tk3y.rally.db.Section
import com.h0tk3y.rally.strRound1
import com.h0tk3y.rally.strRound3

enum class DialogKind {
    CREATE, IMPORT, RENAME, DUPLICATE
}

@ExperimentalComposeUiApi
@Composable
fun CreateOrRenameSectionDialog(
    kind: DialogKind,
    existing: Section?,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> ItemSaveResult<Section>
) {
    var itemName by rememberSaveable { mutableStateOf(existing?.name ?: "") }

    var textFieldValueState by remember {
        mutableStateOf(
            TextFieldValue(
                text = itemName,
                selection = TextRange(itemName.length)
            )
        )
    }
    var importText by rememberSaveable { mutableStateOf("") }
    var isValidImportText by rememberSaveable { mutableStateOf(true) }

    var isErrorEmptyName by rememberSaveable { mutableStateOf(false) }
    var itemAlreadyExists by rememberSaveable { mutableStateOf(false) }

    fun trySave(content: String? = null) {
        if (itemName.isBlank()) {
            isErrorEmptyName = true
        } else {
            val result = onSave(itemName, content)
            itemAlreadyExists = result == ItemSaveResult.AlreadyExists
        }
    }

    val title = when (kind) {
        DialogKind.CREATE -> "Create section"
        DialogKind.DUPLICATE -> "Duplicate section"
        DialogKind.RENAME -> "Rename section"
        DialogKind.IMPORT -> "Import section"
    }

    DialogOnTop(onDismiss, title = title) {
        val focusRequester = remember { FocusRequester() }

        Column(Modifier.padding(8.dp)) {
            val textModifier = Modifier.fillMaxWidth()
            Column(textModifier) {
                OutlinedTextField(
                    value = textFieldValueState,
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .fillMaxWidth(),
                    singleLine = true,
                    onValueChange = {
                        itemAlreadyExists = false
                        isErrorEmptyName = false
                        itemName = it.text
                        textFieldValueState = it
                    },
                    isError = itemAlreadyExists || isErrorEmptyName,
                    label = { Text("New section name") },
                )

                if (itemAlreadyExists) {
                    Box(Modifier.height(24.dp)) {
                        Text(text = "There is already a section with this name", color = MaterialTheme.colors.error)
                    }
                }

                if (kind == DialogKind.IMPORT) {
                    OutlinedTextField(
                        value = importText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        singleLine = false,
                        onValueChange = {
                            importText = it
                            isValidImportText = importText.isNotEmpty() && try {
                                InputRoadmapParser(DefaultModifierValidator()).parseRoadmap(it.reader())
                                true
                            } catch (e: Exception) {
                                false
                            }
                        },
                        isError = importText.isNotEmpty() && !isValidImportText,
                        label = { Text("Section content") }
                    )

                    if (importText.isNotEmpty() && !isValidImportText) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Invalid input", color = MaterialTheme.colors.error)
                    }
                }

                if (kind == DialogKind.CREATE) {
                    EditableStageData { distance, speed ->
                        importText = importTextFromData(distance, speed)
                    }
                }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
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
                    onClick = {
                        trySave(importText.takeIf { it.isNotEmpty() })
                    },
                    enabled = itemName.isNotBlank() && !itemAlreadyExists && isValidImportText
                ) {
                    Text("Save")
                }
            }

        }
    }
}

private fun importTextFromData(distanceKm: DistanceKm, speedKmh: SpeedKmh) =
    """
        0.0 setavg ${speedKmh.valueKmh}
        ${distanceKm.valueKm}
    """.trimIndent()

@Composable
private fun ColumnScope.EditableStageData(
    updateData: (DistanceKm, SpeedKmh) -> Unit
) {
    val defaultDistanceText = "60.0"
    val defaultTimeText = "01:00"
    val defaultSpeedText = "60"

    val distanceText = rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val timeText = rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val speedText = rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var preferTimeOverSpeed by rememberSaveable { mutableStateOf(true) }

    fun distanceValue(): DistanceKm =
        distanceText.value.text.ifEmpty { defaultDistanceText }.toDoubleOrNull()?.let(::DistanceKm) ?: DistanceKm(0.0)

    fun timeValue(): TimeDayHrMinSec =
        parseTimeInput(timeText.value.text.ifEmpty { defaultTimeText }) ?: parseTimeInput(defaultTimeText)!!

    fun speedValue(): SpeedKmh =
        speedText.value.text.ifEmpty { defaultSpeedText }.toDoubleOrNull()?.let(::SpeedKmh) ?: SpeedKmh(defaultSpeedText.toDouble())

    fun updateValues(distance: DistanceKm?, time: TimeDayHrMinSec?, speedKmh: SpeedKmh?) {
        val distanceToUse = distance ?: DistanceKm(defaultDistanceText.toDouble())
        if (preferTimeOverSpeed && time != null) {
            if ((distanceToUse.valueKm / time.toHr().timeHours).isFinite()) {
                val averageAt = averageAt(distanceToUse, time.toHr()).run {
                    copy(valueKmh = valueKmh.strRound3().toDouble()) // avoid redundant precision
                }
                speedText.value = TextFieldValue(averageAt.valueKmh.strRound3())
                updateData(distanceToUse, averageAt)
            }
        } else if (!preferTimeOverSpeed && speedKmh != null) {
            if ((distanceToUse.valueKm / speedKmh.valueKmh).isFinite()) {
                timeText.value = TextFieldValue(TimeHr.byMoving(distanceToUse, speedKmh).toTimeDayHrMinSec().timeStrNoDayOverflow())
                updateData(distanceToUse, speedKmh)
            }
        } else {
            updateData(distanceToUse, SpeedKmh(defaultSpeedText.toDouble()))
        }
    }

    fun updateFromCurrentValues() =
        updateValues(distanceValue(), timeValue(), speedValue())

    LaunchedEffect(Unit) {
        updateData(distanceValue(), speedValue())
    }

    val maxLeftWidth = remember { mutableIntStateOf(0) }

    LeftAlignedRow(
        maxLeftWidth,
        leftContent = {
            Text("Distance:")
        },
        rightContent = {
            SmallNumberTextField(
                Modifier.weight(1.0f),
                distanceText,
                onChange = {
                    distanceText.value = it
                    updateFromCurrentValues()
                },
                placeholderString = defaultDistanceText,
                "km",
                addSuffixToPlaceholder = true,
                isError = { distanceText.value.text.ifEmpty { "0.0" }.toDoubleOrNull() == null }
            )

        })

    LeftAlignedRow(
        maxLeftWidth,
        leftContent = {
            Text("Time HH:MM(:SS)", fontWeight = if (preferTimeOverSpeed) FontWeight.Bold else FontWeight.Normal)
        }, rightContent = {
            SmallNumberTextField(
                Modifier.weight(1.0f),
                timeText,
                onChange = {
                    timeText.value = it
                    preferTimeOverSpeed = true
                    updateFromCurrentValues()
                },
                placeholderString = defaultTimeText,
                suffix = null,
                isError = { parseTimeInput(it.ifEmpty { defaultTimeText }) == null }
            )
        })

    LeftAlignedRow(maxLeftWidth, {
        Text("Average speed:", fontWeight = if (!preferTimeOverSpeed) FontWeight.Bold else FontWeight.Normal)
    }, {
        SmallNumberTextField(
            Modifier.weight(1.0f),
            speedText,
            onChange = {
                speedText.value = it
                preferTimeOverSpeed = false
                updateFromCurrentValues()
            },
            placeholderString = defaultSpeedText,
            "/h",
            addSuffixToPlaceholder = true,
            isError = { speedText.value.text.ifEmpty { "0.0" }.toDoubleOrNull() == null }
        )
    })
}

sealed interface ItemSaveResult<out T> {
    data class Ok<T>(val createdItem: T) : ItemSaveResult<T>
    object AlreadyExists : ItemSaveResult<Nothing>
}

private fun parseTimeInput(string: String): TimeDayHrMinSec? {
    val parts = string.split(":", ".", "-")
    if (!parts.all { it.toIntOrNull() != null })
        return null
    return when (parts.size) {
        2 -> return TimeHr(parts[0].toInt() + parts[1].toInt() / 60.0).toTimeDayHrMinSec()
        3 -> return TimeHr(parts[0].toInt() + parts[1].toInt() / 60.0 + parts[2].toInt() / 3600.0).toTimeDayHrMinSec()
        else -> null
    }
}