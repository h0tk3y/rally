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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.h0tk3y.rally.DefaultModifierValidator
import com.h0tk3y.rally.DistanceKm
import com.h0tk3y.rally.InputRoadmapParser
import com.h0tk3y.rally.R
import com.h0tk3y.rally.SpeedKmh
import com.h0tk3y.rally.SpeedKmh.Companion.averageAt
import com.h0tk3y.rally.TimeDayHrMinSec
import com.h0tk3y.rally.TimeHr
import com.h0tk3y.rally.android.views.LeftAlignedRow
import com.h0tk3y.rally.android.views.SmallNumberTextField
import com.h0tk3y.rally.db.Section
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
        DialogKind.CREATE -> stringResource(R.string.createSection)
        DialogKind.DUPLICATE -> stringResource(R.string.duplicateSection)
        DialogKind.RENAME -> stringResource(R.string.renameSection)
        DialogKind.IMPORT -> stringResource(R.string.importSection)
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
                    label = { Text(stringResource(R.string.newSectionName)) },
                )

                if (itemAlreadyExists) {
                    Box(Modifier.height(24.dp)) {
                        Text(text = stringResource(R.string.sectionNameNotUnique), color = MaterialTheme.colors.error)
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
                        label = { Text(stringResource(R.string.sectionContent)) }
                    )

                    if (importText.isNotEmpty() && !isValidImportText) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.invalidSectionContentInput), color = MaterialTheme.colors.error)
                    }
                }

                if (kind == DialogKind.CREATE) {
                    EditableStageData { distance, speed, start ->
                        importText = importTextFromData(distance, speed, start)
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
                    Text(stringResource(android.R.string.cancel))
                }
                Button(
                    onClick = {
                        trySave(importText.takeIf { it.isNotEmpty() })
                    },
                    enabled = itemName.isNotBlank() && !itemAlreadyExists && isValidImportText
                ) {
                    Text(stringResource(R.string.saveButton))
                }
            }

        }
    }
}

private fun importTextFromData(distanceKm: DistanceKm, speedKmh: SpeedKmh, start: TimeDayHrMinSec?) =
    """
        0.0 setavg ${speedKmh.valueKmh}${if (start != null) " atime ${start.timeStrNoDayOverflow()}" else "" }
        ${distanceKm.valueKm}
    """.trimIndent()

@Composable
private fun ColumnScope.EditableStageData(
    updateData: (DistanceKm, SpeedKmh, TimeDayHrMinSec?) -> Unit
) {
    val defaultDistanceText = "60.0"
    val defaultTimeText = "01:00"
    val defaultSpeedText = "60"

    val distanceText = rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val timeText = rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val speedText = rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var preferTimeOverSpeed by rememberSaveable { mutableStateOf(true) }
    val startTimeText = rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }

    fun distanceValue(): DistanceKm =
        distanceText.value.text.ifEmpty { defaultDistanceText }.toDoubleOrNull()?.let(::DistanceKm) ?: DistanceKm(0.0)

    fun timeValue(): TimeDayHrMinSec =
        parseTimeInput(timeText.value.text.ifEmpty { defaultTimeText }) ?: parseTimeInput(defaultTimeText)!!

    fun speedValue(): SpeedKmh =
        speedText.value.text.ifEmpty { defaultSpeedText }.toDoubleOrNull()?.let(::SpeedKmh) ?: SpeedKmh(defaultSpeedText.toDouble())

    fun startTimeValue(): TimeDayHrMinSec? =
        parseTimeInput(startTimeText.value.text) 

    fun updateValues(distance: DistanceKm?, time: TimeDayHrMinSec?, speedKmh: SpeedKmh?) {
        val distanceToUse = distance ?: DistanceKm(defaultDistanceText.toDouble())
        if (preferTimeOverSpeed && time != null) {
            if ((distanceToUse.valueKm / time.toHr().timeHours).isFinite()) {
                val averageAt = averageAt(distanceToUse, time.toHr()).run {
                    copy(valueKmh = valueKmh.strRound3().toDouble()) // avoid redundant precision
                }
                speedText.value = TextFieldValue(averageAt.valueKmh.strRound3())
                updateData(distanceToUse, averageAt, startTimeValue())
            }
        } else if (!preferTimeOverSpeed && speedKmh != null) {
            if ((distanceToUse.valueKm / speedKmh.valueKmh).isFinite()) {
                timeText.value = TextFieldValue(TimeHr.byMoving(distanceToUse, speedKmh).toTimeDayHrMinSec().timeStrNoDayOverflow())
                updateData(distanceToUse, speedKmh, startTimeValue())
            }
        } else {
            updateData(distanceToUse, SpeedKmh(defaultSpeedText.toDouble()), startTimeValue())
        }
    }

    fun updateFromCurrentValues() =
        updateValues(distanceValue(), timeValue(), speedValue())

    LaunchedEffect(Unit) {
        updateData(distanceValue(), speedValue(), startTimeValue())
    }

    val maxLeftWidth = remember { mutableIntStateOf(0) }

    LeftAlignedRow(
        maxLeftWidth,
        leftContent = {
            Text(stringResource(R.string.distance))
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
                stringResource(R.string.kmUnit),
                addSuffixToPlaceholder = true,
                isError = { distanceText.value.text.ifEmpty { "0.0" }.toDoubleOrNull() == null }
            )

        })

    LeftAlignedRow(
        maxLeftWidth,
        leftContent = {
            Text(stringResource(R.string.timeHhMmSs), fontWeight = if (preferTimeOverSpeed) FontWeight.Bold else FontWeight.Normal)
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
        Text(stringResource(R.string.averageSpeedHint), fontWeight = if (!preferTimeOverSpeed) FontWeight.Bold else FontWeight.Normal)
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
            stringResource(R.string.perHourSuffix),
            addSuffixToPlaceholder = true,
            isError = { speedText.value.text.ifEmpty { "0.0" }.toDoubleOrNull() == null }
        )
    })
    
    Spacer(Modifier.height(16.dp))

    LeftAlignedRow(maxLeftWidth, {
        Text(stringResource(R.string.startAtHhMmSs))
    }, {
        SmallNumberTextField(
            Modifier.weight(1.0f),
            startTimeText,
            onChange = {
                startTimeText.value = it
                updateFromCurrentValues()
            },
            placeholderString = "",
            "",
            addSuffixToPlaceholder = false,
            isError = { it.isNotEmpty() && parseTimeInput(it) == null }
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