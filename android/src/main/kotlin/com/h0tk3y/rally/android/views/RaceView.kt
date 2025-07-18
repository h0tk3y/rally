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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults.TextFieldDecorationBox
import androidx.compose.material.TextFieldDefaults.indicatorLine
import androidx.compose.material.TextFieldDefaults.textFieldColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ControlPoint
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.OutlinedFlag
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.h0tk3y.rally.BuildConfig
import com.h0tk3y.rally.DistanceKm
import com.h0tk3y.rally.PositionLine
import com.h0tk3y.rally.PositionLineModifier
import com.h0tk3y.rally.android.racecervice.RaceService
import com.h0tk3y.rally.SpeedKmh
import com.h0tk3y.rally.TimeDistanceLocalizer
import com.h0tk3y.rally.TimeHr
import com.h0tk3y.rally.TimeMinSec
import com.h0tk3y.rally.android.scenes.SectionViewModel
import com.h0tk3y.rally.android.scenes.SectionViewModel.RaceUiState.RaceNotStarted
import com.h0tk3y.rally.android.scenes.TimeAllowance
import com.h0tk3y.rally.android.theme.LocalCustomColorsPalette
import com.h0tk3y.rally.android.theme.LocalCustomTypography
import com.h0tk3y.rally.android.util.KeepScreenOn
import com.h0tk3y.rally.android.util.VolumeKeyHandler
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

@Composable
fun RaceView(
    race: SectionViewModel.RaceUiState,
    distanceLocalizer: TimeDistanceLocalizer?,
    sectionDistanceLocalizer: TimeDistanceLocalizer?,
    btState: RaceService.BtPublicState,
    macAddressInPreferences: String?,
    updateMacAddress: (String?) -> Unit,
    speedLimitPercent: String?,
    setSpeedLimitPercent: (String?) -> Unit,
    selectedPosition: PositionLine?,
    onStartRace: (option: StartOption) -> Unit,
    onFinishRace: () -> Unit,
    onUndoFinishRace: () -> Unit,
    onStopRace: () -> Unit,
    onUndoStopRace: () -> Unit,
    onResetRace: () -> Unit,
    distanceCorrection: (DistanceKm) -> Unit,
    onSetGoingForward: (Boolean) -> Unit,
    onSetDebugSpeed: (Int) -> Unit,
    navigateToSection: (Long) -> Unit,
    modifier: Modifier,
    addPositionMaybeWithSpeed: (SpeedKmh?) -> Unit,
    allowance: TimeAllowance?
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

    VolumeKeyHandler(onVolumeUp = { distanceCorrection(DistanceKm(0.01)) }, onVolumeDown = { distanceCorrection(DistanceKm(-0.01)) }) {
        Column {
            Box(modifier.padding(4.dp)) {
                InRaceEditorControls({ addPositionMaybeWithSpeed(null) })
            }
            Surface(modifier.padding(4.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BtStatus(btState, macAddressInPreferences, updateMacAddress)
                    RaceStatus(race, time, onSetGoingForward, distanceLocalizer, sectionDistanceLocalizer, distanceCorrection, allowance, selectedPosition)
                    RaceControls(
                        race,
                        selectedPosition,
                        onStartRace,
                        onFinishRace,
                        onUndoFinishRace,
                        onStopRace,
                        onUndoStopRace,
                        onResetRace,
                        navigateToSection,
                        addPositionMaybeWithSpeed,
                        speedLimitPercent,
                        setSpeedLimitPercent
                    )
                    if (BuildConfig.DEBUG) {
                        DebugSpeedSlider(onSetDebugSpeed)
                    }
                }
            }
        }
    }
}

@Composable
private fun InRaceEditorControls(
    onAddPositionAtCurrentDistance: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { 
        IconButton(onClick = { onAddPositionAtCurrentDistance() }) { 
            Icon(Icons.Rounded.ControlPoint, contentDescription = "Add passed position")
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
private fun RaceStatus(
    race: SectionViewModel.RaceUiState,
    time: Instant,
    onSetGoingForward: (Boolean) -> Unit,
    distanceLocalizer: TimeDistanceLocalizer?,
    sectionDistanceLocalizer: TimeDistanceLocalizer?,
    distanceCorrection: (DistanceKm) -> Unit,
    allowance: TimeAllowance?,
    selectedPosition: PositionLine?
) {
    val actualTime = maxOf(time, Clock.System.now())

    if (race is SectionViewModel.RaceUiState.Going || race is SectionViewModel.RaceUiState.RaceGoing) {
        KeepScreenOn()
    }

    RaceViewElement {
        when (race) {
            SectionViewModel.RaceUiState.NoRaceServiceConnection -> Text("Not connected to race service")
            is SectionViewModel.RaceUiState.RaceGoing -> {
                Column {
                    if (race.lastFinishAt != null && race.lastFinishModel != null) {
                        Text("Finished: ${raceTimeDistanceString(race.lastFinishAt, race.lastFinishModel)}")
                    }
                    RaceTimeDistance(
                        actualTime, race.raceModel, selectedPosition, isSectionTime = false, onSetGoingForward, distanceCorrection, distanceLocalizer, allowance
                    )
                    RaceSpeed(actualTime, race)
                }
            }

            is SectionViewModel.RaceUiState.Going -> {
                Column {
                    if (race.finishedAt != null && race.raceModelAtFinish != null) {
                        Text("Finished: ${raceTimeDistanceString(race.finishedAt, race.raceModelAtFinish)}")
                    }
                    RaceTimeDistance(
                        actualTime, race.raceModel, selectedPosition, isSectionTime = true, 
                        onSetGoingForward, distanceCorrection, sectionDistanceLocalizer, allowance
                    )
                    GoingSpeed(race)
                }
            }

            is SectionViewModel.RaceUiState.RaceGoingInAnotherSection -> Text("Race going in another section")
            RaceNotStarted -> Text("Race not started")
            is SectionViewModel.RaceUiState.Stopped -> Column {
                if (race.finishedAt != null && race.raceModelAtFinish != null) {
                    Text("Finished, ${raceTimeDistanceString(race.finishedAt, race.raceModelAtFinish)}")
                }
                Text("Stopped, ${raceTimeDistanceString(race.stoppedAt, race.raceModel)}")
            }
        }
    }
}

@Composable
private fun RaceTimeDistance(
    time: Instant,
    race: RaceModel,
    selectedPosition: PositionLine?,
    isSectionTime: Boolean,
    onSetGoingForward: (Boolean) -> Unit,
    distanceCorrection: (DistanceKm) -> Unit,
    distanceLocalizer: TimeDistanceLocalizer?,
    allowance: TimeAllowance?
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(timeString(time, race), style = LocalCustomTypography.current.raceIndicatorText)
        if (distanceLocalizer != null) {
            val expectedTime = distanceLocalizer.getExpectedTimeForDistance(race.currentDistance)
            if (expectedTime != null) {
                val delta = time - (race.startAtTime + expectedTime.timeHours.hours)
                val deltaSec = delta.inWholeMicroseconds.toDouble() / 1000_000
                val timeText = if (deltaSec.absoluteValue < 60.0) {
                    val sign = if (delta <= Duration.ZERO) "" else "+"
                    "$sign${deltaSec.strRound1()}"
                } else {
                    val sign = if (delta < Duration.ZERO) "-" else if (delta == Duration.ZERO) "" else "+"
                    "$sign${
                        TimeMinSec(
                            deltaSec.absoluteValue.roundToInt() / 60,
                            deltaSec.absoluteValue.roundToInt() % 60,
                            false
                        )
                    }"
                }
                val allowedTime = if (isSectionTime) {
                    val allowanceTime = run {
                        val expectedTimeForZero = distanceLocalizer.getExpectedTimeForDistance(DistanceKm.zero) ?: return@run null
                        val timeFromSectionStart = expectedTime - expectedTimeForZero
                        allowance(allowance, timeFromSectionStart)
                    }
                    allowanceTime
                } else null
                val isAheadOfAllowance = allowedTime != null && deltaSec < allowedTime * -60
                if (isSectionTime) Text("(S${allowedTime?.takeIf { it != 0 }?.toString()?.let { "+$it" }.orEmpty()})")
                Text(
                    timeText,
                    style = LocalCustomTypography.current.raceIndicatorText
                        .copy(color = if (isAheadOfAllowance) LocalCustomColorsPalette.current.dangerous else Color.Unspecified)
                )
            }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        val hapticFeedback = LocalHapticFeedback.current

        var isEditing by remember { mutableStateOf(false) }
        if (!isEditing) {
            Text(distanceString(race.currentDistance), style = LocalCustomTypography.current.raceIndicatorText, modifier = Modifier.clickable {
                isEditing = true
            })
            if (selectedPosition != null) {
                Text(deltaDistanceString(race.currentDistance - selectedPosition.atKm), style = LocalCustomTypography.current.raceSmallIndicatorText)
            }
            TextButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSetGoingForward(!race.distanceGoingUp)
                },
                colors = ButtonDefaults.buttonColors(if (!race.distanceGoingUp) LocalCustomColorsPalette.current.dangerous else Color.Unspecified)
            ) {
                Icon(Icons.AutoMirrored.Default.Undo, "Switch direction")
            }
            Spacer(Modifier.weight(1.0f))
            TextButton(onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                distanceCorrection(DistanceKm(-0.1))
            }) { Text("-0.1", color = MaterialTheme.colors.onSurface, style = LocalCustomTypography.current.raceControlButton) }
            TextButton(onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                distanceCorrection(DistanceKm(0.1))
            }) { Text("+0.1", color = MaterialTheme.colors.onSurface, style = LocalCustomTypography.current.raceControlButton) }
        } else {
            val text = rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(distanceString(race.currentDistance))) }
            val focusRequester = FocusRequester()
            SmallNumberTextField(Modifier, text, { }, "0.000", null, focusRequester = focusRequester)
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            IconButton(
                enabled = text.value.text.toDoubleOrNull() != null,
                onClick = {
                    distanceCorrection(DistanceKm(text.value.text.toDouble()) - race.currentDistance)
                    isEditing = false
                }) {
                Icon(Icons.Rounded.Done, "Apply")
            }

            IconButton(onClick = {
                isEditing = false
            }) {
                Icon(Icons.Rounded.Close, "Cancel")
            }
        }
    }
}

@Composable
private fun RaceSpeed(time: Instant, race: SectionViewModel.RaceUiState.RaceGoing) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Absolute.SpaceBetween) {
        Text("v=${race.raceModel.instantSpeed.valueKmh.roundToInt()}/h", style = LocalCustomTypography.current.raceIndicatorText)
        Text("ṽ=${race.raceModel.averageSpeed(time).valueKmh.strRound1()}/h", style = LocalCustomTypography.current.raceIndicatorText)
    }
}

@Composable
private fun GoingSpeed(race: SectionViewModel.RaceUiState.Going) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Absolute.SpaceBetween) {
        Text("v=${race.raceModel.instantSpeed.valueKmh.roundToInt()}/h", style = LocalCustomTypography.current.raceIndicatorText)
    }
}

private fun raceTimeDistanceString(time: Instant, race: RaceModel): String {
    return "${timeString(time, race)} / ${distanceString(race.currentDistance)}"
}

private fun timeString(time: Instant, race: RaceModel): String {
    val elapsedOrRemainingHours = TimeHr((time.minus(race.startAtTime).inWholeMilliseconds.toDouble() / 1000 / 3600).absoluteValue).toTimeDayHrMinSec()
    val isElapsed = !(time - race.startAtTime).isNegative()
    val timeString = "${if (!isElapsed) "-" else ""}${elapsedOrRemainingHours.timeStrNoHoursIfZero()}"
    return timeString
}

data class StartOption(val locationAndTime: StartLocationAndTime, val isRace: Boolean) {
    sealed interface StartLocationAndTime {
        val isNow get() = this == StartAtSelectedPositionNow || this == StartNowFromGoingState
    }

    data object StartAtSelectedPositionNow : StartLocationAndTime
    data object StartAtSelectedPositionAtTime : StartLocationAndTime
    data object StartAtSelectedPositionAtTimeKeepOdo : StartLocationAndTime
    data object StartNowFromGoingState : StartLocationAndTime

}

@Composable
private fun RaceControls(
    race: SectionViewModel.RaceUiState,
    selectedPosition: PositionLine?,
    onStartRace: (option: StartOption) -> Unit,
    onFinishRace: () -> Unit,
    onUndoFinishRace: () -> Unit,
    onStopRace: () -> Unit,
    onUndoStopRace: () -> Unit,
    onResetRace: () -> Unit,
    navigateToSection: (Long) -> Unit,
    applyLimit: (SpeedKmh?) -> Unit,
    speedLimitPercent: String?,
    setSpeedLimitPercent: (String?) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    when (race) {
        RaceNotStarted -> {
            GoRow(selectedPosition, hapticFeedback, onStartRace, keepOdo = false)
            RaceRow(selectedPosition, hapticFeedback, onStartRace, keepOdo = false)
        }

        is SectionViewModel.RaceUiState.RaceGoing -> {
            RaceViewElement {
                NewSpeedLimits(applyLimit, speedLimitPercent, setSpeedLimitPercent)
            }
            RaceViewElement {
                StateSwitchButtonsRow {
                    FinishRace(onFinishRace)
                    FinishAndStartRace {
                        onFinishRace()
                        onStartRace(StartOption(StartOption.StartNowFromGoingState, true))
                    }
                }
            }
        }

        is SectionViewModel.RaceUiState.RaceGoingInAnotherSection -> {
            RaceViewElement {
                StateSwitchButtonsRow {
                    Button(onClick = { navigateToSection(race.raceSectionId) }) {
                        Icon(Icons.Rounded.ArrowOutward, "Go to section")
                        Text("Go to race section")
                    }
                    Reset(onResetRace)
                }
            }
        }

        SectionViewModel.RaceUiState.NoRaceServiceConnection -> Unit

        is SectionViewModel.RaceUiState.Stopped -> {
            GoRow(selectedPosition, hapticFeedback, onStartRace, keepOdo = true)
            RaceRow(selectedPosition, hapticFeedback, onStartRace, keepOdo = true)
            RaceViewElement {
                StateSwitchButtonsRow {
                    Button({
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onUndoStopRace()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, "Undo stop")
                        Text("Undo stop")
                    }
                    Reset(onResetRace)
                }
            }
        }

        is SectionViewModel.RaceUiState.Going -> {
            StartOrNextRaceRow(race, hapticFeedback, onStartRace, selectedPosition)
            RaceViewElement {
                StateSwitchButtonsRow {
                    if (race.finishedAt != null && race.raceModelAtFinish != null) {
                        UndoFinish(onUndoFinishRace)
                    }
                    StopRace(onStopRace)
                }
            }
        }
    }
}

@Composable
private fun StartOrNextRaceRow(
    race: SectionViewModel.RaceUiState.Going,
    hapticFeedback: HapticFeedback,
    onStartRace: (option: StartOption) -> Unit,
    selectedPosition: PositionLine?
) {
    RaceViewElement {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.OutlinedFlag, "Next race")
            Text(
                if (race.finishedAt != null && race.raceModelAtFinish != null)
                    "Next race:"
                else "Start race:"
            )

            Button(onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onStartRace(StartOption(StartOption.StartNowFromGoingState, true))
            }) {
                Text("Now, ${(race.raceModel.currentDistance.valueKm.strRound2Exact())}")
            }
            if (selectedPosition != null) {
                selectedPosition.modifier<PositionLineModifier.AstroTime>()?.let { astroTime ->
                    val timeOfDay = astroTime.timeOfDay
                    Button(onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartRace(StartOption(StartOption.StartAtSelectedPositionAtTime, true))
                    }) {
                        Text("${timeOfDay.timeStrNoDayOverflow()}, ${selectedPosition.atKm.valueKm.strRound2Exact()}")
                    }
                    Button(onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartRace(StartOption(StartOption.StartAtSelectedPositionAtTimeKeepOdo, true))
                    }) {
                        Text("${timeOfDay.timeStrNoDayOverflow()}, ${selectedPosition.atKm.valueKm.strRound2Exact()}, keep ODO")
                    }
                } ?: run {
                    if (selectedPosition.atKm != race.raceModel.currentDistance) {
                        Button(onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStartRace(StartOption(StartOption.StartAtSelectedPositionNow, true))
                        }) {
                            Text("Now, ${distanceString(selectedPosition.atKm)}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RaceRow(
    selectedPosition: PositionLine?,
    hapticFeedback: HapticFeedback,
    onStartRace: (option: StartOption) -> Unit,
    keepOdo: Boolean
) {
    RaceViewElement {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.OutlinedFlag, "Race")
            Text("Race${selectedPosition?.atKm?.valueKm?.let { " at position ${it.strRound3()}:" }.orEmpty()}")
            Button(
                enabled = selectedPosition != null,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStartRace(StartOption(StartOption.StartAtSelectedPositionNow, true))
                }) {
                Text("Now")
            }
            val astroTime = selectedPosition?.modifier<PositionLineModifier.AstroTime>()
            if (astroTime != null) {
                val timeOfDay = astroTime.timeOfDay
                Button(onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStartRace(StartOption(StartOption.StartAtSelectedPositionAtTime, true))
                }) {
                    Text("On ${timeOfDay.timeStrNoDayOverflow()}")
                }
                if (keepOdo) {
                    Button(onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartRace(StartOption(StartOption.StartAtSelectedPositionAtTimeKeepOdo, true))
                    }) {
                        Text("On ${timeOfDay.timeStrNoDayOverflow()}, keep ODO")
                    }
                }
            }
        }
    }
}

@Composable
private fun GoRow(
    selectedPosition: PositionLine?,
    hapticFeedback: HapticFeedback,
    onStartRace: (option: StartOption) -> Unit,
    keepOdo: Boolean
) {
    RaceViewElement {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.Timer, "Go")
            Text("Go${selectedPosition?.atKm?.valueKm?.let { " from position ${it.strRound3()}:" }.orEmpty()}")
            Button(
                enabled = selectedPosition != null,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStartRace(StartOption(StartOption.StartAtSelectedPositionNow, false))
                }) {
                Text("Now")
            }
            val astroTime = selectedPosition?.modifier<PositionLineModifier.AstroTime>()
            if (astroTime != null) {
                val timeOfDay = astroTime.timeOfDay
                Button(onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStartRace(StartOption(StartOption.StartAtSelectedPositionAtTime, false))
                }) {
                    Text("On ${timeOfDay.timeStrNoDayOverflow()}")
                }
                if (keepOdo) {
                    Button(onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartRace(StartOption(StartOption.StartAtSelectedPositionAtTimeKeepOdo, false))
                    }) {
                        Text("On ${timeOfDay.timeStrNoDayOverflow()}, keep ODO")
                    }
                }
            }
        }
    }
}

@Composable
fun StateSwitchButtonsRow(content: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        Divider()
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            content()
        }
    }
}

@Composable
private fun Reset(onResetRace: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    Button(onResetRace, colors = ButtonDefaults.buttonColors(backgroundColor = LocalCustomColorsPalette.current.dangerous)) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        Icon(Icons.Rounded.Cancel, "Rest")
        Text("Reset")
    }
}

private fun distanceString(distance: DistanceKm) = distance.valueKm.strRound3Exact()
private fun deltaDistanceString(distance: DistanceKm) = (if (distance.valueKm > 0) "+" else "") + distance.valueKm.strRound3Exact()

private val speedLimitValues: List<Int> = listOf(5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110)

@Composable
private fun NewSpeedLimits(applyLimit: (SpeedKmh?) -> Unit, percent: String?, onPercentChange: (String?) -> Unit) {
    // Hold the full TextFieldValue (text + selection) locally.
    val textFieldValue = remember { mutableStateOf(TextFieldValue(text = percent.orEmpty())) }

    // When the stored text changes externally, update the local TextFieldValue
    // without losing the current cursor position unless the text differs.
    LaunchedEffect(percent) {
        if (percent.orEmpty() != textFieldValue.value.text) {
            textFieldValue.value = textFieldValue.value.copy(text = percent.orEmpty())
        }
    }
    LazyRow(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomSpeedLimitInput("=/h →", { it }, applyLimit)
                Spacer(Modifier.size(2.dp))
                Divider(
                    Modifier
                        .height(45.dp)
                        .width(4.dp), thickness = 2.dp
                )
                Spacer(Modifier.size(2.dp))
            }
        }

        stickyHeader {
            Surface(modifier = Modifier.height(speedLimitButtonSize)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallNumberTextField(
                        Modifier,
                        text = textFieldValue,
                        onChange = {
                            textFieldValue.value = it
                            onPercentChange(it.text)
                        },
                        placeholderString = "100%",
                        suffix = "%"
                    )
                    Spacer(Modifier.size(2.dp))
                    Text("×")
                    Spacer(Modifier.size(1.dp))
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val multiplier = percent?.toDoubleOrNull()?.div(100.0)
                CustomSpeedLimitInput("/h →", { it * (multiplier ?: 1.0) }, applyLimit)

                speedLimitValues.forEach { limit ->
                    SpeedLimitButton("$limit", limit * (multiplier ?: 1.0), applyLimit)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SmallNumberTextField(
    modifier: Modifier,
    text: MutableState<TextFieldValue>,
    onChange: (TextFieldValue) -> Unit = { },
    placeholderString: String?,
    suffix: String?,
    focusRequester: FocusRequester = FocusRequester(),
    isAllowMinus: Boolean = false
) {
    val source = remember { MutableInteractionSource() }

    BasicTextField(
        value = text.value,
        onValueChange = { it: TextFieldValue ->
            text.value = it.copy(text = it.text.filter { char -> char.isDigit() || char == '.' || isAllowMinus && char == '-' })
            onChange(it)
        },
        textStyle = TextStyle(brush = SolidColor(MaterialTheme.colors.onSurface)).plus(LocalCustomTypography.current.raceEditableValue),
        cursorBrush = SolidColor(MaterialTheme.colors.onSurface),
        modifier = modifier
            .indicatorLine(
                enabled = true,
                isError = text.value.text.let { it.isNotEmpty() && it.toDoubleOrNull() == null },
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
                isError = text.value.text.let { it.isNotEmpty() && it.toDoubleOrNull() == null },
                innerTextField = it,
                singleLine = true,
                enabled = true,
                visualTransformation = VisualTransformation.None,
                placeholder = {
                    if (placeholderString != null) {
                        Text(
                            text = placeholderString,
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


@Composable
private fun CustomSpeedLimitInput(placeholder: String, mapSpeed: (Double) -> Double, applyLimit: (SpeedKmh?) -> Unit) {
    val exactNumber = rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    SmallNumberTextField(Modifier, exactNumber, { }, placeholder, "/h")
    val text = exactNumber.value.text
    SpeedLimitButton(
        text.toIntOrNull()?.toString() ?: text.toDoubleOrNull()?.strRound3() ?: "?",
        text.toIntOrNull()?.toDouble()?.let(mapSpeed) ?: text.toDoubleOrNull()?.strRound3()?.toDouble()?.let(mapSpeed),
        applyLimit
    )
}

@Composable
private fun SpeedLimitButton(label: String, limit: Double?, applyLimit: (SpeedKmh?) -> Unit) {
    TextButton(
        modifier = Modifier
            .size(speedLimitButtonSize)
            .border(BorderStroke(4.dp, LocalCustomColorsPalette.current.speedLimit), CircleShape),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent),
        onClick = { applyLimit(limit?.let(::SpeedKmh)) })
    {
        Text(label, style = LocalCustomTypography.current.raceControlButton)
    }
}

private val speedLimitButtonSize = 50.dp

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
        Icon(Icons.Rounded.Flag, "Finish race")
        Text("Finish race")
    }
}

@Composable
private fun FinishAndStartRace(onFinishAndStart: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    Button(
        colors = ButtonDefaults.buttonColors(LocalCustomColorsPalette.current.warning),
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onFinishAndStart()
        }
    ) {
        Icon(Icons.Rounded.Flag, "Start race")
        Icon(Icons.Rounded.OutlinedFlag, "Finish race")
        Text("Finish and start")
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
        Icon(Icons.AutoMirrored.Rounded.Undo, "Undo")
        Text("Undo finish race")
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
        Icon(Icons.Rounded.Stop, "Stop")
        Text("Stop")
    }
}

@Composable
private fun DebugSpeedSlider(onSetDebugSpeed: (Int) -> Unit) {
    var speedSliderValue by remember { mutableIntStateOf(0) }
    val maxDebugSpeed = 100
    RaceViewElement {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = Icons.Rounded.BugReport, "debug")
            Text("Simulate speed:")
            Slider(
                value = speedSliderValue.toFloat() / maxDebugSpeed,
                onValueChange = {
                    speedSliderValue = (it * maxDebugSpeed).roundToInt()
                    onSetDebugSpeed(speedSliderValue)
                },
                Modifier.width(200.dp)
            )
            Text("$speedSliderValue/h", Modifier.wrapContentWidth())
        }
    }
}