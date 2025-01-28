package com.h0tk3y.rally.android.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TextFieldDefaults.indicatorLine
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Done
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.h0tk3y.rally.BuildConfig
import com.h0tk3y.rally.DistanceKm
import com.h0tk3y.rally.PositionLine
import com.h0tk3y.rally.PositionLineModifier
import com.h0tk3y.rally.RaceService
import com.h0tk3y.rally.SpeedKmh
import com.h0tk3y.rally.TimeDayHrMinSec
import com.h0tk3y.rally.TimeHr
import com.h0tk3y.rally.android.scenes.SectionViewModel
import com.h0tk3y.rally.android.scenes.SectionViewModel.RaceUiState.RaceNotStarted
import com.h0tk3y.rally.android.theme.LocalCustomColorsPalette
import com.h0tk3y.rally.model.RaceModel
import com.h0tk3y.rally.modifier
import com.h0tk3y.rally.strRound1
import com.h0tk3y.rally.strRound2Exact
import com.h0tk3y.rally.strRound3
import com.h0tk3y.rally.strRound3Exact
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

@Composable
fun RaceView(
    race: SectionViewModel.RaceUiState,
    btState: RaceService.BtPublicState,
    macAddressInPreferences: String?,
    updateMacAddress: (String?) -> Unit,
    selectedPosition: PositionLine?,
    onStartRace: (option: StartRaceOption) -> Unit,
    onFinishRace: () -> Unit,
    onUndoFinishRace: () -> Unit,
    onStopRace: () -> Unit,
    onResetRace: () -> Unit,
    onSetGoingForward: (Boolean) -> Unit,
    onSetDebugSpeed: (Int) -> Unit,
    navigateToSection: (Long) -> Unit,
    modifier: Modifier,
    applyLimit: (SpeedKmh?) -> Unit
) {
    val time by produceState(Clock.System.now()) {
        while (true) {
            val time = Clock.System.now()
            val currentTime = time.toLocalDateTime(TimeZone.currentSystemDefault())
            val millisToNextWholeSecond = 1000 - (currentTime.time.toMillisecondOfDay() - currentTime.time.toSecondOfDay() * 1000)
            value = time
            val delayTime = millisToNextWholeSecond.toLong()
            delay(delayTime)
        }
    }

    Surface(modifier.padding(16.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            BtStatus(btState, macAddressInPreferences, updateMacAddress)
            RaceStatus(race, time, onSetGoingForward)
            RaceControls(race, selectedPosition, onStartRace, onFinishRace, onUndoFinishRace, onStopRace, onResetRace, onSetDebugSpeed, navigateToSection, applyLimit)
        }
    }
}

@Composable
private fun RaceViewElement(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val elementModifier = modifier.padding(4.dp)
    Box(elementModifier) {
        content()
    }
}

@Composable
private fun BtStatus(btPublicState: RaceService.BtPublicState, macAddressInPreferences: String?, updateMacAddress: (String?) -> Unit) {
    var isEditingMacAddress by remember { mutableStateOf(false) }

    if (isEditingMacAddress) {
        var text by rememberSaveable(macAddressInPreferences) { mutableStateOf(macAddressInPreferences) }
        RaceViewElement {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    text.orEmpty(),
                    onValueChange = {
                        text = it
                    },
                    label = { Text("Bluetooth MAC address") }
                )
                IconButton(onClick = {
                    updateMacAddress(text)
                    isEditingMacAddress = false
                }) {
                    Icon(Icons.Rounded.Done, "Apply")
                }
            }
        }
    } else {
        RaceViewElement(Modifier.clickable(onClick = {
            isEditingMacAddress = true
        })) {
            val text = when (btPublicState) {
                RaceService.BtPublicState.Connecting -> "connecting..."
                RaceService.BtPublicState.NoPermissions -> "lacking permissions"
                RaceService.BtPublicState.NoTargetMacAddress -> "click here to set the MAC"
                RaceService.BtPublicState.NotInitialized -> "not initialized"
                RaceService.BtPublicState.Reconnecting -> "reconnecting..."
                RaceService.BtPublicState.Working -> "OK"
            }
            val color = when (btPublicState) {
                RaceService.BtPublicState.Working -> Color.Unspecified
                RaceService.BtPublicState.Connecting,
                RaceService.BtPublicState.NoPermissions,
                RaceService.BtPublicState.NoTargetMacAddress,
                RaceService.BtPublicState.NotInitialized,
                RaceService.BtPublicState.Reconnecting -> LocalCustomColorsPalette.current.warning
            }
            Text("OBD: $text", color = color)
        }
    }
}


@Composable
private fun RaceStatus(race: SectionViewModel.RaceUiState, time: Instant, onSetGoingForward: (Boolean) -> Unit) {
    val actualTime = maxOf(time, Clock.System.now())

    RaceViewElement {
        when (race) {
            SectionViewModel.RaceUiState.NoRaceServiceConnection -> Text("Not connected to race service")
            SectionViewModel.RaceUiState.NotInRaceMode -> Text("Not in race mode")
            is SectionViewModel.RaceUiState.RaceGoing -> {
                Column {
                    RaceTimeDistance(actualTime, race.raceModel, onSetGoingForward)
                    RaceSpeed(actualTime, race)
                }
            }

            is SectionViewModel.RaceUiState.RaceGoingAfterFinish -> {
                Column {
                    Text("Finished: ${raceTimeDistanceString(race.finishedAt, race.raceModelAtFinish)}")
                    RaceTimeDistance(actualTime, race.raceModel, onSetGoingForward)
                }
            }

            is SectionViewModel.RaceUiState.RaceGoingInAnotherSection -> Text("Race going in another section")
            RaceNotStarted -> Text("Race not started")
            is SectionViewModel.RaceUiState.RaceStopped -> Text("Stopped, ${raceTimeDistanceString(race.stoppedAt, race.raceModel)}")
        }
    }
}

@Composable
private fun RaceTimeDistance(time: Instant, race: RaceModel, onSetGoingForward: (Boolean) -> Unit) {
    Row {
        Text(timeString(time, race), fontSize = 45.sp)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(distanceString(race.currentDistance), fontSize = 45.sp)
        TextButton(
            onClick = { onSetGoingForward(!race.distanceGoingUp) },
            colors = ButtonDefaults.buttonColors(if (!race.distanceGoingUp) LocalCustomColorsPalette.current.dangerous else Color.Unspecified)
        ) {
            Icon(Icons.AutoMirrored.Default.Undo, "Switch direction")
        }
    }
}

@Composable
private fun RaceSpeed(time: Instant, race: SectionViewModel.RaceUiState.RaceGoing) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Absolute.SpaceBetween) {
        Text("v=${race.raceModel.instantSpeed.valueKmh.roundToInt()}/h", fontSize = 45.sp)
        Text("ṽ=${race.raceModel.averageSpeed(time).valueKmh.strRound1()}/h", fontSize = 45.sp)
    }
}

private fun raceTimeDistanceString(time: Instant, race: RaceModel): String {
    return "${timeString(time, race)} / ${distanceString(race.currentDistance)}"
}

private fun timeString(time: Instant, race: RaceModel): String {
    val elapsedOrRemainingHours = TimeHr((time.minus(race.startAtTime).inWholeMilliseconds.toDouble() / 1000 / 3600).absoluteValue).toTimeDayHrMinSec()
    val isElapsed = time - race.startAtTime > (-1).seconds
    val timeString = "${if (!isElapsed) "-" else ""}${elapsedOrRemainingHours.timeStrNoDayOverflow()}"
    return timeString
}

fun TimeHr.toTimeDayHrMinSec() = TimeDayHrMinSec(0, 0, 0, 0) + toMinSec()

sealed interface StartRaceOption {
    data object StartAtSelectedPositionNow : StartRaceOption
    data object StartAtSelectedPositionAtTime : StartRaceOption
    data object StartNowFromRaceState : StartRaceOption
    data object StartNowFromStoppedState : StartRaceOption

    val isNow get() = this == StartAtSelectedPositionNow || this == StartNowFromRaceState || this == StartNowFromStoppedState
}

@Composable
private fun RaceControls(
    race: SectionViewModel.RaceUiState,
    selectedPosition: PositionLine?,
    onStartRace: (option: StartRaceOption) -> Unit,
    onFinishRace: () -> Unit,
    onUndoFinishRace: () -> Unit,
    onStopRace: () -> Unit,
    onResetRace: () -> Unit,
    onSetDebugSpeed: (Int) -> Unit,
    navigateToSection: (Long) -> Unit,
    applyLimit: (SpeedKmh?) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    when (race) {
        RaceNotStarted -> {
            RaceViewElement {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Start${selectedPosition?.atKm?.valueKm?.let { " at position ${it.strRound3()}:" }.orEmpty()}")
                    Button(
                        enabled = selectedPosition != null,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStartRace(StartRaceOption.StartAtSelectedPositionNow)
                        }) {
                        Text("Now")
                    }
                    val astroTime = selectedPosition?.modifier<PositionLineModifier.AstroTime>()
                    if (astroTime != null) {
                        val timeOfDay = astroTime.timeOfDay
                        Button(onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStartRace(StartRaceOption.StartAtSelectedPositionAtTime)
                        }) {
                            Text("On ${timeOfDay.timeStrNoDayOverflow()}")
                        }
                    }
                }
            }
        }

        is SectionViewModel.RaceUiState.RaceGoing -> {
            RaceViewElement {
                NewSpeedLimits(applyLimit)
            }
            RaceViewElement {
                FinishRace(onFinishRace)
            }
        }

        is SectionViewModel.RaceUiState.RaceGoingInAnotherSection -> {
            RaceViewElement {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { navigateToSection(race.raceSectionId) }) {
                        Text("Go to race section")
                    }
                    StopRace(onStopRace)
                }
            }
        }

        SectionViewModel.RaceUiState.NoRaceServiceConnection,
        SectionViewModel.RaceUiState.NotInRaceMode -> Unit

        is SectionViewModel.RaceUiState.RaceStopped -> {
            RaceViewElement {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ onStartRace(StartRaceOption.StartNowFromStoppedState) }) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        Text("Undo stop")
                    }
                    Button(onResetRace, colors = ButtonDefaults.buttonColors(backgroundColor = LocalCustomColorsPalette.current.dangerous)) {
                        Text("Reset")
                    }
                }
            }
        }

        is SectionViewModel.RaceUiState.RaceGoingAfterFinish -> {
            RaceViewElement {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Next:")
                    Button(onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartRace(StartRaceOption.StartNowFromRaceState)
                    }) {
                        Text("Now at ${distanceString(race.raceModel.currentDistance)}")
                    }
                    if (selectedPosition != null) {
                        selectedPosition.modifier<PositionLineModifier.AstroTime>()?.let { astroTime ->
                            val timeOfDay = astroTime.timeOfDay
                            Button(onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStartRace(StartRaceOption.StartAtSelectedPositionAtTime)
                            }) {
                                Text("On ${timeOfDay.timeStrNoDayOverflow()} at ${selectedPosition.atKm.valueKm.strRound2Exact()}")
                            }
                        } ?: run {
                            Button(onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStartRace(StartRaceOption.StartAtSelectedPositionNow)
                            }) {
                                Text("Now at ${distanceString(selectedPosition.atKm)}")
                            }
                        }
                    }
                }
            }
            RaceViewElement {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UndoFinish(onUndoFinishRace)
                    StopRace(onStopRace)
                }
            }
        }
    }
    if (BuildConfig.DEBUG) {
        DebugSpeedSlider(onSetDebugSpeed)
    }
}

private fun distanceString(distance: DistanceKm) = distance.valueKm.strRound3Exact()

private val speedLimitValues: List<Int> = listOf(5, 10, 20, 40, 50, 60, 70, 80, 90, 100, 110)

@Composable
private fun NewSpeedLimits(applyLimit: (SpeedKmh?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CustomSpeedLimitInput("=/h", { it }, applyLimit)

        Spacer(Modifier.size(2.dp))
        Divider(Modifier.height(45.dp).width(4.dp), thickness = 2.dp)
        Spacer(Modifier.size(2.dp))
        
        val percent = rememberSaveable { mutableStateOf("") }

        SmallNumberTextField(
            Modifier,
            text = percent,
            placeholderString = "100%",
            suffix = "%"
        )
        Text("×")
        val toDoubleOrNull = percent.value.toDoubleOrNull()?.div(100.0)
        CustomSpeedLimitInput("%/h", { it * (toDoubleOrNull ?: 1.0) }, applyLimit)

        speedLimitValues.forEach { limit ->
            SpeedLimitButton("$limit", limit * (toDoubleOrNull ?: 1.0), applyLimit)
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SmallNumberTextField(modifier: Modifier, text: MutableState<String>, placeholderString: String?, suffix: String?) {
    val source = remember { MutableInteractionSource() }

    BasicTextField(
        value = text.value,
        onValueChange = { text.value = it.filter { char -> char.isDigit() || char == '.' } },
        textStyle = TextStyle(fontSize = 20.sp),
        modifier = modifier
            .indicatorLine(
                enabled = true,
                isError = false,
                interactionSource = source,
                colors = TextFieldDefaults.textFieldColors(backgroundColor = Color.Transparent),
                focusedIndicatorLineThickness = 2.dp,
                unfocusedIndicatorLineThickness = 2.dp
            )
            .height(45.dp)
            .width(70.dp),
        interactionSource = source,
        visualTransformation = if (text.value.isEmpty())
            VisualTransformation.None
        else if (suffix != null) SuffixTransformation(suffix) else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = true,
        singleLine = true
    ) {
        TextFieldDefaults.TextFieldDecorationBox(
            value = text.value,
            innerTextField = it,
            singleLine = true,
            enabled = true,
            visualTransformation = VisualTransformation.None,
            placeholder = {
                if (placeholderString != null) {
                    Text(
                        text = placeholderString,
                        fontSize = 20.sp,
                    )
                }
            },
            interactionSource = source,
            contentPadding = PaddingValues(4.dp, 0.dp)
        )
    }
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
                if (offset >=  text.length) return text.length
                return offset
            }
        }

        return TransformedText(result, textWithSuffixMapping )
    }
}


@Composable
private fun CustomSpeedLimitInput(placeholder: String, mapSpeed: (Double) -> Double, applyLimit: (SpeedKmh?) -> Unit) {
    val exactNumber = rememberSaveable { mutableStateOf("") }
    SmallNumberTextField(Modifier, exactNumber, placeholder, "/h")
    val text = exactNumber.value
    SpeedLimitButton(
        text.toIntOrNull()?.toString() ?: text.toDoubleOrNull()?.strRound3() ?: "?",
        text.toIntOrNull()?.toDouble()?.let(mapSpeed) ?: text.toDoubleOrNull()?.strRound3()?.toDouble()?.let(mapSpeed),
        applyLimit
    )
}

@Composable
private fun SpeedLimitButton(label: String, limit: Double?, applyLimit: (SpeedKmh?) -> Unit) {
    TextButton(
        modifier = Modifier.size(50.dp).border(BorderStroke(4.dp, LocalCustomColorsPalette.current.speedLimit), CircleShape),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent),
        onClick = { applyLimit(limit?.let(::SpeedKmh)) })
    {
        Text(label, fontSize = 20.sp)
    }
}

@Composable
private fun FinishRace(onFinishRace: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    Button(
        colors = ButtonDefaults.buttonColors(LocalCustomColorsPalette.current.warning),
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onFinishRace()
        }
    ) {
        Text("Finish")
    }
}

@Composable
private fun UndoFinish(onUndoFinishRace: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    Button(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onUndoFinishRace()
        }
    ) {
        Text("Undo finish")
    }
}

@Composable
private fun StopRace(onStopRace: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    Button(
        colors = ButtonDefaults.buttonColors(LocalCustomColorsPalette.current.warning),
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onStopRace()
        }
    ) {
        Text("Stop race")
    }
}

@Composable
private fun DebugSpeedSlider(onSetDebugSpeed: (Int) -> Unit) {
    var speedSliderValue by remember { mutableIntStateOf(0) }
    val maxDebugSpeed = 100
    RaceViewElement {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(imageVector = Icons.Rounded.BugReport, "debug")
            Text("Simulate speed:")
            Slider(
                value = speedSliderValue.toFloat() / maxDebugSpeed,
                onValueChange = {
                    speedSliderValue = (it * maxDebugSpeed).roundToInt()
                    onSetDebugSpeed(speedSliderValue)
                },
                Modifier.width(160.dp)
            )
            Text("$speedSliderValue/h", Modifier.wrapContentWidth())
        }
    }
}