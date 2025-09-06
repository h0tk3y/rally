package com.h0tk3y.rally.android.views

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.filled.OutlinedFlag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OutlinedFlag
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.h0tk3y.rally.DistanceKm
import com.h0tk3y.rally.PositionLine
import com.h0tk3y.rally.PositionLineModifier
import com.h0tk3y.rally.R
import com.h0tk3y.rally.SpeedKmh
import com.h0tk3y.rally.TimeDistanceLocalizer
import com.h0tk3y.rally.TimeHr
import com.h0tk3y.rally.TimeMinSec
import com.h0tk3y.rally.android.racecervice.TelemetryPublicState
import com.h0tk3y.rally.android.scenes.RaceModelControls
import com.h0tk3y.rally.android.scenes.RaceUiState
import com.h0tk3y.rally.android.scenes.RaceUiState.RaceNotStarted
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
    timeProvider: () -> Instant,
    race: RaceUiState,
    distanceLocalizer: TimeDistanceLocalizer?,
    sectionDistanceLocalizer: TimeDistanceLocalizer?,
    telemetryState: TelemetryPublicState,
    speedLimitPercent: String?,
    selectedPosition: PositionLine?,
    navigateToSection: (Long) -> Unit,
    goToEventLog: (() -> Unit)?,
    goToSettings: (() -> Unit)?,
    modifier: Modifier,
    addPositionMaybeWithSpeed: (SpeedKmh?) -> Unit,
    allowance: TimeAllowance?,
    rememberSpeed: SpeedKmh?,
    setDebugSpeed: (Int) -> Unit,
    raceControls: RaceModelControls?
) {
    val time by produceState(Clock.System.now()) {
        while (true) {
            val time = timeProvider()
            val currentTime = time.toLocalDateTime(TimeZone.currentSystemDefault())
            val millisToNextWholeSecond = 1000 - (currentTime.time.toMillisecondOfDay() - currentTime.time.toSecondOfDay() * 1000)
            value = time
            val delayTime = millisToNextWholeSecond.toLong()
            delay(delayTime)
        }
    }

    val isTablet = isTablet()

    var showRememberedSpeedControls by remember { mutableStateOf(false) }

    WithVolumeControlsIfAvailable(raceControls) {
        Surface(modifier.padding(4.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {

                val canRememberSpeed = race is RaceUiState.Going || race is RaceNotStarted

                @Composable
                fun moreRaceControls() = MoreRaceControls(
                    canRememberSpeed,
                    raceControls,
                    telemetryState,
                    onAddPositionAtCurrentDistance = { addPositionMaybeWithSpeed(null) },
                    onGoToEventLog = goToEventLog,
                    onGoToSettings = goToSettings,
                    switchRememberedSpeedControls = {
                        if (canRememberSpeed) showRememberedSpeedControls = !showRememberedSpeedControls
                    },
                    rememberSpeed,
                    isTablet
                )

                if (!isTablet) {
                    moreRaceControls()
                }

                RaceStatus(race, time, raceControls, distanceLocalizer, sectionDistanceLocalizer, allowance, selectedPosition)
                if (raceControls != null) {
                    RaceControls(
                        race,
                        showRememberedSpeedControls,
                        selectedPosition,
                        navigateToSection,
                        speedLimitPercent,
                        raceControls,
                        addPositionMaybeWithSpeed
                    )
                }
                if (telemetryState == TelemetryPublicState.Simulation) {
                    DebugSpeedSlider(setDebugSpeed, (race as? RaceUiState.HasRaceModel)?.raceModel?.instantSpeed)
                }

                if (isTablet) {
                    Spacer(Modifier.weight(1f))
                    moreRaceControls()
                }
            }
        }
    }
}

@Composable
private fun WithVolumeControlsIfAvailable(raceModelControls: RaceModelControls?, content: @Composable () -> Unit) {
    if (raceModelControls != null) {
        VolumeKeyHandler(
            onVolumeUp = { raceModelControls.distanceCorrection(DistanceKm(0.01)) },
            onVolumeDown = { raceModelControls.distanceCorrection(DistanceKm(-0.01)) }
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
private fun MoreRaceControls(
    canRememberSpeed: Boolean,
    raceModelControls: RaceModelControls?,
    telemetryState: TelemetryPublicState,
    onAddPositionAtCurrentDistance: () -> Unit,
    onGoToEventLog: (() -> Unit)?,
    onGoToSettings: (() -> Unit)?,
    switchRememberedSpeedControls: () -> Unit,
    rememberSpeed: SpeedKmh?,
    isBigUi: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onGoToEventLog != null) {
            IconButton(onClick = { onGoToEventLog() }) {
                Icon(Icons.Rounded.History, contentDescription = stringResource(R.string.eventLog))
            }
        }
        TelemetryStatus(telemetryState, onGoToSettings ?: { })
        Spacer(Modifier.weight(1f))
        SpeedLimitLikeButton(
            label = rememberSpeed?.valueKmh?.roundToInt()?.toString() ?: stringResource(R.string.vSpeedButton),
            isEnabled = canRememberSpeed && raceModelControls != null,
            typography = if (isBigUi) LocalCustomTypography.current.raceControlButton else MaterialTheme.typography.caption,
            size = if (isBigUi) 48.dp else 24.dp,
        ) {
            switchRememberedSpeedControls()
        }
        if (raceModelControls != null) {
            val hapticFeedback = LocalHapticFeedback.current
            IconButton(onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onAddPositionAtCurrentDistance()
            }) {
                Icon(
                    Icons.Rounded.AddLocationAlt, contentDescription = stringResource(R.string.buttonAddPassedPosition),
                    Modifier.size(if (isBigUi) 48.dp else 28.dp)
                )
            }
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
private fun TelemetryStatus(
    telemetryPublicState: TelemetryPublicState,
    goToSettings: () -> Unit
) {
    RaceViewElement(Modifier.clickable(onClick = { goToSettings() })) {
        val text = when (telemetryPublicState) {
            TelemetryPublicState.NotInitialized -> stringResource(R.string.telemetryNotInitialized)
            TelemetryPublicState.BtConnecting -> stringResource(R.string.telemetryObdConnecting)

            TelemetryPublicState.BtReconnecting -> stringResource(R.string.telemetryObdReconnecting)

            TelemetryPublicState.BtNoPermissions -> stringResource(R.string.telemetryObdPermissions)
            TelemetryPublicState.BtNoTargetMacAddress -> stringResource(R.string.telemetryObdNoMac)

            TelemetryPublicState.BtWorking -> stringResource(R.string.telemetryObdOk)
            TelemetryPublicState.Simulation -> stringResource(R.string.telemetrySimulationOk)
            is TelemetryPublicState.ReceivesStream ->
                stringResource(R.string.telemetryData) + (if (telemetryPublicState.isDelayed) stringResource(R.string.telemetryDataDelay) else stringResource(R.string.telemetryOk))

            TelemetryPublicState.IncompatibleStream -> stringResource(R.string.telemetryDataIncompatible)

            TelemetryPublicState.WaitingForStream -> stringResource(R.string.telemetryDataWaiting)

            is TelemetryPublicState.GpsGood -> stringResource(R.string.telemetryGpsGood) + satsString(telemetryPublicState.sats)
            is TelemetryPublicState.GpsProblematic -> stringResource(R.string.telemetryGpsProblematic) + satsString(telemetryPublicState.sats)
            is TelemetryPublicState.GpsNoPosition -> stringResource(R.string.telemetryGpsNoPosition) + satsString(telemetryPublicState.sats)
            is TelemetryPublicState.GpsWaiting -> stringResource(R.string.telemetryGpsWaiting) + satsString(telemetryPublicState.sats)
            TelemetryPublicState.GpsNoPermissions -> stringResource(R.string.telemetryGpsNoPermission)
        }
        val color = when (telemetryPublicState) {
            is TelemetryPublicState.ReceivesStream -> {
                if (telemetryPublicState.isDelayed) LocalCustomColorsPalette.current.warning else Color.Unspecified
            }

            TelemetryPublicState.Simulation,
            is TelemetryPublicState.GpsGood,
            TelemetryPublicState.BtWorking -> Color.Unspecified

            TelemetryPublicState.BtConnecting,
            TelemetryPublicState.BtNoPermissions,
            TelemetryPublicState.BtNoTargetMacAddress,
            TelemetryPublicState.NotInitialized,
            TelemetryPublicState.WaitingForStream,
            is TelemetryPublicState.GpsNoPosition,
            is TelemetryPublicState.GpsProblematic,
            is TelemetryPublicState.GpsWaiting,
            TelemetryPublicState.GpsNoPermissions,
            TelemetryPublicState.IncompatibleStream,
            TelemetryPublicState.BtReconnecting -> LocalCustomColorsPalette.current.warning

        }
        Text(text, color = color)
    }
}

@Composable
private fun satsString(sats: Int) = LocalContext.current.getString(R.string.satStatusPattern, sats)


@Composable
private fun RaceStatus(
    race: RaceUiState,
    time: Instant,
    raceControls: RaceModelControls?,
    distanceLocalizer: TimeDistanceLocalizer?,
    sectionDistanceLocalizer: TimeDistanceLocalizer?,
    allowance: TimeAllowance?,
    selectedPosition: PositionLine?
) {
    val actualTime = maxOf(time, Clock.System.now())

    if (race is RaceUiState.Going || race is RaceUiState.RaceGoing) {
        KeepScreenOn()
    }

    RaceViewElement {
        when (race) {
            RaceUiState.NoRaceServiceConnection -> Text(stringResource(R.string.notConnectedToRaceService))
            is RaceUiState.RaceGoing -> {
                Column {
                    if (race.lastFinishAt != null && race.lastFinishModel != null) {
                        Text(stringResource(R.string.finishedTimeDistancePrefix) + raceTimeDistanceString(race.lastFinishAt, race.lastFinishModel))
                    }
                    RaceTimeDistance(
                        actualTime, race, selectedPosition, isSectionTime = false, raceControls, distanceLocalizer, allowance
                    )
                    RaceSpeed(actualTime, race)
                }
            }

            is RaceUiState.Going -> {
                Column {
                    if (race.finishedAt != null && race.raceModelAtFinish != null) {
                        Text(stringResource(R.string.finishedTimeDistancePrefix) + raceTimeDistanceString(race.finishedAt, race.raceModelAtFinish))
                    }
                    RaceTimeDistance(
                        actualTime, race, selectedPosition, isSectionTime = true, raceControls, sectionDistanceLocalizer, allowance
                    )
                    GoingSpeed(race)
                }
            }

            is RaceUiState.RaceGoingInAnotherSection -> Text(stringResource(R.string.raceGoingInAnotherSection))
            RaceNotStarted -> Text(stringResource(R.string.raceNotStarted))
            is RaceUiState.Stopped -> Column {
                if (race.finishedAt != null && race.raceModelAtFinish != null) {
                    Text(stringResource(R.string.finishedTimeDistancePrefix) + raceTimeDistanceString(race.finishedAt, race.raceModelAtFinish))
                }
                Text(stringResource(R.string.stoppedTimeDistancePrefix) + raceTimeDistanceString(race.stoppedAt, race.raceModel))
            }
        }
    }
}

@Composable
private fun RaceTimeDistance(
    time: Instant,
    race: RaceUiState,
    selectedPosition: PositionLine?,
    isSectionTime: Boolean,
    raceControls: RaceModelControls?,
    distanceLocalizer: TimeDistanceLocalizer?,
    allowance: TimeAllowance?
) {
    val raceModel = when (race) {
        is RaceUiState.RaceGoing -> race.raceModel
        is RaceUiState.Going -> race.raceModel
        else -> null
    }
    if (raceModel != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (race is RaceUiState.RaceGoing) Icons.Default.OutlinedFlag else Icons.Default.Timer, stringResource(R.string.raceStatusHint) + race)
                Spacer(Modifier.width(8.dp))
                Text(timeString(time, raceModel), style = LocalCustomTypography.current.raceIndicatorText)
            }
            if (distanceLocalizer != null) {
                val expectedTime = distanceLocalizer.getExpectedTimeForDistance(raceModel.currentDistance)
                if (expectedTime != null) {
                    val delta = time - (raceModel.startAtTime + expectedTime.timeHours.hours)
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
                    if (isSectionTime && allowance != null)
                        Text(
                            "(${stringResource(R.string.allowanceLetter)}${allowedTime?.let { ":$it" }.orEmpty()})"
                        )
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

            var isEditing by rememberSaveable { mutableStateOf(false) }
            if (!isEditing) {
                Text(distanceString(raceModel.currentDistance), style = LocalCustomTypography.current.raceIndicatorText, modifier = Modifier.clickable {
                    isEditing = true
                })
                if (selectedPosition != null) {
                    Text(deltaDistanceString(raceModel.currentDistance - selectedPosition.atKm), style = LocalCustomTypography.current.raceSmallIndicatorText)
                }
                TextButton(
                    onClick = {
                        if (raceControls != null) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            raceControls.setGoingForward(!raceModel.distanceGoingUp)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(if (!raceModel.distanceGoingUp) LocalCustomColorsPalette.current.dangerous else Color.Unspecified)
                ) {
                    Icon(Icons.AutoMirrored.Default.Undo, stringResource(R.string.switchDirectionHint))
                }
                Spacer(Modifier.weight(1.0f))
                if (raceControls != null) {
                    TextButton(onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        raceControls.distanceCorrection(DistanceKm(-0.1))
                    }) {
                        Text(
                            stringResource(R.string.buttonMinus01),
                            color = MaterialTheme.colors.onSurface,
                            style = LocalCustomTypography.current.raceControlButton
                        )
                    }
                    TextButton(onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        raceControls.distanceCorrection(DistanceKm(0.1))
                    }) {
                        Text(
                            stringResource(R.string.buttonPlus01),
                            color = MaterialTheme.colors.onSurface,
                            style = LocalCustomTypography.current.raceControlButton
                        )
                    }
                }
            } else if (raceControls != null) {
                val text = rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(distanceString(raceModel.currentDistance))) }
                val focusRequester = remember { FocusRequester() }
                SmallNumberTextField(Modifier, text, { }, "0.000", null, focusRequester = focusRequester)
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                IconButton(
                    enabled = text.value.text.toDoubleOrNull() != null,
                    onClick = {
                        raceControls.distanceCorrection(DistanceKm(text.value.text.toDouble()) - raceModel.currentDistance)
                        isEditing = false
                    }) {
                    Icon(Icons.Rounded.Done, stringResource(R.string.buttonApplyOdo))
                }

                IconButton(onClick = {
                    isEditing = false
                }) {
                    Icon(Icons.Rounded.Close, stringResource(android.R.string.cancel))
                }

                Spacer(Modifier.weight(1.0f))

                if (selectedPosition != null) {
                    Button({
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        raceControls.distanceCorrection(selectedPosition.atKm - raceModel.currentDistance)
                        isEditing = false
                    }) { Text(stringResource(R.string.setToPositionPrefix) + selectedPosition.atKm.valueKm.strRound3()) }
                }
            }
        }
    }
}

@Composable
private fun RaceSpeed(time: Instant, race: RaceUiState.RaceGoing) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Absolute.SpaceBetween) {
        Text(
            "v=${race.raceModel.instantSpeed.valueKmh.roundToInt()}" + stringResource(R.string.perHourSuffix),
            style = LocalCustomTypography.current.raceIndicatorText
        )
        Text(
            "ṽ=${race.raceModel.averageSpeed(time).valueKmh.strRound1()}" + stringResource(R.string.perHourSuffix),
            style = LocalCustomTypography.current.raceIndicatorText
        )
    }
}

@Composable
private fun GoingSpeed(race: RaceUiState.Going) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Absolute.SpaceBetween) {
        Text(
            "v=${race.raceModel.instantSpeed.valueKmh.roundToInt()}" + stringResource(R.string.perHourSuffix),
            style = LocalCustomTypography.current.raceIndicatorText
        )
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
    race: RaceUiState,
    showRememberedSpeedControls: Boolean,
    selectedPosition: PositionLine?,
    navigateToSection: (Long) -> Unit,
    speedLimitPercent: String?,
    raceControls: RaceModelControls,
    addPositionMaybeWithSpeed: (SpeedKmh?) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    when (race) {
        RaceNotStarted -> {
            GoRow(selectedPosition, hapticFeedback, raceControls::startRace, keepOdo = false)
            RaceRow(selectedPosition, hapticFeedback, raceControls::startRace, keepOdo = false)
            if (showRememberedSpeedControls) {
                RaceViewElement {
                    NewSpeedLimits(
                        withClearButton = true,
                        applyLimit = { raceControls.setRememberSpeed(it) },
                        percent = speedLimitPercent,
                        onPercentChange = raceControls::setSpeedLimitPercent
                    )
                }
            }
        }

        is RaceUiState.RaceGoing -> {
            RaceViewElement {
                NewSpeedLimits(
                    false,
                    {
                        raceControls.setRememberSpeed(it)
                        addPositionMaybeWithSpeed(it)
                    },
                    speedLimitPercent,
                    raceControls::setSpeedLimitPercent
                )
            }
            RaceViewElement {
                StateSwitchButtonsRow {
                    FinishRace(raceControls::finishRace)
                    FinishAndStartRace {
                        raceControls.finishRace()
                        raceControls.startRace(StartOption(StartOption.StartNowFromGoingState, true))
                    }
                }
            }
        }

        is RaceUiState.RaceGoingInAnotherSection -> {
            RaceViewElement {
                StateSwitchButtonsRow {
                    Button(onClick = { navigateToSection(race.raceSectionId) }) {
                        Icon(Icons.Rounded.ArrowOutward, stringResource(R.string.buttonGoToRaceSection))
                        Text(stringResource(R.string.buttonGoToRaceSection))
                    }
                }
            }
        }

        RaceUiState.NoRaceServiceConnection -> Unit

        is RaceUiState.Stopped -> {
            GoRow(selectedPosition, hapticFeedback, raceControls::startRace, keepOdo = true)
            RaceRow(selectedPosition, hapticFeedback, raceControls::startRace, keepOdo = true)
            RaceViewElement {
                StateSwitchButtonsRow {
                    Button({
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        raceControls.undoStopRace()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.buttonUndoStop))
                        Text(stringResource(R.string.buttonUndoStop))
                    }
                }
            }
        }

        is RaceUiState.Going -> {
            if (showRememberedSpeedControls) {
                RaceViewElement {
                    NewSpeedLimits(
                        withClearButton = true,
                        applyLimit = { raceControls.setRememberSpeed(it) },
                        percent = speedLimitPercent,
                        onPercentChange = raceControls::setSpeedLimitPercent
                    )
                }
            }
            StartOrNextRaceRow(race, hapticFeedback, raceControls::startRace, selectedPosition)
            RaceViewElement {
                StateSwitchButtonsRow {
                    if (race.finishedAt != null && race.raceModelAtFinish != null) {
                        UndoFinish(raceControls::undoFinishRace)
                    }
                    StopRace(raceControls::stopRace)
                }
            }
        }
    }
}

@Composable
private fun StartOrNextRaceRow(
    race: RaceUiState.Going,
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
            Icon(Icons.Rounded.OutlinedFlag, stringResource(R.string.preLabelNextRace))
            Text(
                if (race.finishedAt != null && race.raceModelAtFinish != null)
                    stringResource(R.string.preLabelNextRace)
                else stringResource(R.string.preLabelStartRace)
            )

            Button(onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onStartRace(StartOption(StartOption.StartNowFromGoingState, true))
            }) {
                Text(stringResource(R.string.nowAtPosition, (race.raceModel.currentDistance.valueKm.strRound2Exact())))
            }
            if (selectedPosition != null) {
                selectedPosition.modifier<PositionLineModifier.AstroTime>()?.let { astroTime ->
                    val timeOfDay = astroTime.timeOfDay
                    Button(onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartRace(StartOption(StartOption.StartAtSelectedPositionAtTime, true))
                    }) {
                        Text(stringResource(R.string.inTimeAtPosition, timeOfDay.timeStrNoDayOverflow(), selectedPosition.atKm.valueKm.strRound2Exact()))
                    }
                    Button(onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartRace(StartOption(StartOption.StartAtSelectedPositionAtTimeKeepOdo, true))
                    }) {
                        Text(stringResource(R.string.inTimeAtPositionKeepOdo, timeOfDay.timeStrNoDayOverflow(), selectedPosition.atKm.valueKm.strRound2Exact()))
                    }
                } ?: run {
                    if (selectedPosition.atKm != race.raceModel.currentDistance) {
                        Button(onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onStartRace(StartOption(StartOption.StartAtSelectedPositionNow, true))
                        }) {
                            Text(stringResource(R.string.nowAtPosition, distanceString(selectedPosition.atKm)))
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
            Icon(Icons.Rounded.OutlinedFlag, stringResource(R.string.preLabelStartRace))
            val text = stringResource(
                R.string.raceRowLinePrefix, selectedPosition?.atKm?.valueKm
                    ?.let { " " + stringResource(R.string.raceRowAtPositionPart, it.strRound3()) }.orEmpty()
            ) + ":"
            Text(text)
            Button(
                enabled = selectedPosition != null,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStartRace(StartOption(StartOption.StartAtSelectedPositionNow, true))
                }) {
                Text(stringResource(R.string.raceRowButtonNow))
            }
            val astroTime = selectedPosition?.modifier<PositionLineModifier.AstroTime>()
            if (astroTime != null) {
                val timeOfDay = astroTime.timeOfDay
                Button(onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStartRace(StartOption(StartOption.StartAtSelectedPositionAtTime, true))
                }) {
                    Text(stringResource(R.string.raceRowButtonOnTime, timeOfDay.timeStrNoDayOverflow()))
                }
                if (keepOdo) {
                    Button(onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartRace(StartOption(StartOption.StartAtSelectedPositionAtTimeKeepOdo, true))
                    }) {
                        Text(stringResource(R.string.raceRowButtonOnTimeKeepOdo, timeOfDay.timeStrNoDayOverflow()))
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
            Icon(Icons.Rounded.Timer, stringResource(R.string.goRowPrefixGo))
            Text(
                "${stringResource(R.string.goRowPrefixGo)}${
                    selectedPosition?.atKm?.valueKm?.let {
                        " " + stringResource(R.string.goRowFromPositionPart, it.strRound3())
                    }.orEmpty()
                }:"
            )
            Button(
                enabled = selectedPosition != null,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStartRace(StartOption(StartOption.StartAtSelectedPositionNow, false))
                }) {
                Text(stringResource(R.string.raceRowButtonNow))
            }
            val astroTime = selectedPosition?.modifier<PositionLineModifier.AstroTime>()
            if (astroTime != null) {
                val timeOfDay = astroTime.timeOfDay
                Button(onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStartRace(StartOption(StartOption.StartAtSelectedPositionAtTime, false))
                }) {
                    Text(stringResource(R.string.raceRowButtonOnTime, timeOfDay.timeStrNoDayOverflow()))
                }
                if (keepOdo) {
                    Button(onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartRace(StartOption(StartOption.StartAtSelectedPositionAtTimeKeepOdo, false))
                    }) {
                        Text(stringResource(R.string.raceRowButtonOnTimeKeepOdo, timeOfDay.timeStrNoDayOverflow()))
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

private fun distanceString(distance: DistanceKm) = distance.valueKm.strRound3Exact()
private fun deltaDistanceString(distance: DistanceKm) = (if (distance.valueKm > 0) "+" else "") + distance.valueKm.strRound3Exact()

private val speedLimitValues: List<Int> = listOf(5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110)

@Composable
private fun NewSpeedLimits(
    withClearButton: Boolean,
    applyLimit: (SpeedKmh?) -> Unit,
    percent: String?,
    onPercentChange: (String?) -> Unit
) {
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
                CustomSpeedLimitInput("=${stringResource(R.string.perHourSuffix)} →", { it }, applyLimit)
                Spacer(Modifier.size(2.dp))
                if (withClearButton) {
                    IconButton(onClick = { applyLimit(null) }) {
                        Icon(Icons.Rounded.Clear, stringResource(R.string.buttonClearSpeedLimit))
                    }
                }
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
                CustomSpeedLimitInput("${stringResource(R.string.perHourSuffix)} →", { it * (multiplier ?: 1.0) }, applyLimit)

                speedLimitValues.forEach { limit ->
                    SpeedLimitButton("$limit", limit * (multiplier ?: 1.0), applyLimit)
                }
            }
        }
    }
}

@Composable
private fun CustomSpeedLimitInput(placeholder: String, mapSpeed: (Double) -> Double, applyLimit: (SpeedKmh?) -> Unit) {
    val exactNumber = rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    SmallNumberTextField(Modifier, exactNumber, { }, placeholder, stringResource(R.string.perHourSuffix))
    val text = exactNumber.value.text
    SpeedLimitButton(
        text.toIntOrNull()?.toString() ?: text.toDoubleOrNull()?.strRound3() ?: "?",
        text.toIntOrNull()?.toDouble()?.let(mapSpeed) ?: text.toDoubleOrNull()?.strRound3()?.toDouble()?.let(mapSpeed),
        applyLimit
    )
}

@Composable
private fun SpeedLimitButton(label: String, limit: Double?, applyLimit: (SpeedKmh?) -> Unit) {
    SpeedLimitLikeButton(
        label,
        isEnabled = true,
        speedLimitButtonSize,
        LocalCustomTypography.current.raceControlButton
    ) {
        applyLimit(limit?.let(::SpeedKmh))
    }
}

@Composable
private fun SpeedLimitLikeButton(
    label: String,
    isEnabled: Boolean,
    size: Dp,
    typography: TextStyle,
    onClick: () -> Unit
) {
    TextButton(
        modifier = Modifier
            .border(
                BorderStroke(
                    (size.value / 10).dp,
                    LocalCustomColorsPalette.current.speedLimit
                ), CircleShape
            )
            .size(size),
        enabled = isEnabled,
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent),
        onClick = { onClick() }
    ) {
        Text(label, style = typography)
    }
}

private val speedLimitButtonSize = 50.dp

@Composable
private fun FinishRace(onFinishRace: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    Button(
        colors = ButtonDefaults.buttonColors(LocalCustomColorsPalette.current.warning, LocalCustomColorsPalette.current.onWarning),
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onFinishRace()
        }
    ) {
        Icon(Icons.Rounded.Flag, stringResource(R.string.raceButtonFinishRace))
        Text(stringResource(R.string.raceButtonFinishRace))
    }
}

@Composable
private fun FinishAndStartRace(onFinishAndStart: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    Button(
        colors = ButtonDefaults.buttonColors(LocalCustomColorsPalette.current.warning, LocalCustomColorsPalette.current.onWarning),
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onFinishAndStart()
        }
    ) {
        Icon(Icons.Rounded.Flag, stringResource(R.string.raceButtonFinishRace))
        Icon(Icons.Rounded.OutlinedFlag, stringResource(R.string.raceButtonFinishRace))
        Text(stringResource(R.string.raceButtonFinishAndStart))
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
        Icon(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.raceButtonUndoFinishRace))
        Text(stringResource(R.string.raceButtonUndoFinishRace))
    }
}

@Composable
private fun StopRace(onStopRace: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    Button(
        colors = ButtonDefaults.buttonColors(LocalCustomColorsPalette.current.warning, LocalCustomColorsPalette.current.onWarning),
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onStopRace()
        }
    ) {
        Icon(Icons.Rounded.Stop, stringResource(R.string.raceButtonStop))
        Text(stringResource(R.string.raceButtonStop))
    }
}

@Composable
private fun DebugSpeedSlider(
    onSetDebugSpeed: (Int) -> Unit,
    speed: SpeedKmh?
) {
    var speedSliderValue by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }

    val maxDebugSpeed = 100
    RaceViewElement {

        // Keep slider in sync with external state when not dragging
        LaunchedEffect(speed) {
            if (!isDragging) {
                speedSliderValue = speed?.valueKmh?.roundToInt() ?: 0
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = Icons.Rounded.Speed, stringResource(R.string.simulateSpeedPrefix))
            Text(stringResource(R.string.simulateSpeedPrefix))
            Slider(
                value = speedSliderValue.toFloat() / maxDebugSpeed,
                onValueChange = {
                    isDragging = true
                    speedSliderValue = (it * maxDebugSpeed).roundToInt()
                    onSetDebugSpeed(speedSliderValue)
                },
                onValueChangeFinished = {
                    isDragging = false
                },
                modifier = Modifier.width(200.dp)
            )
            Text("$speedSliderValue${stringResource(R.string.perHourSuffix)}", Modifier.wrapContentWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun isTablet(): Boolean {
    val windowSizeClass = calculateWindowSizeClass(activity = LocalActivity.current ?: return false)

    return when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            false
        }

        WindowWidthSizeClass.Medium,
        WindowWidthSizeClass.Expanded -> true

        else -> false
    }
}