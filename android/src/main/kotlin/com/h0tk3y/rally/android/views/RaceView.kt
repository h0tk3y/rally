package com.h0tk3y.rally.android.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.h0tk3y.rally.BuildConfig
import com.h0tk3y.rally.PositionLine
import com.h0tk3y.rally.PositionLineModifier
import com.h0tk3y.rally.TimeHr
import com.h0tk3y.rally.TimeOfDay
import com.h0tk3y.rally.android.scenes.SectionViewModel
import com.h0tk3y.rally.android.scenes.SectionViewModel.RaceUiState.RaceNotStarted
import com.h0tk3y.rally.android.theme.LocalCustomColorsPalette
import com.h0tk3y.rally.model.RaceModel
import com.h0tk3y.rally.modifier
import com.h0tk3y.rally.strRound3
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun RaceView(
    race: SectionViewModel.RaceUiState,
    selectedPosition: PositionLine?,
    onStartRace: (startTime: Instant) -> Unit,
    onStopRace: () -> Unit,
    onResetRace: () -> Unit,
    onSetDebugSpeed: (Int) -> Unit,
    navigateToSection: (Long) -> Unit,
    modifier: Modifier
) {
    Surface(modifier.padding(16.dp)) {
        Column {
            RaceStatus(race)
            RaceControls(race, selectedPosition, onStartRace, onStopRace, onResetRace, onSetDebugSpeed, navigateToSection)
        }
    }
}

@Composable
private fun RaceViewElement(content: @Composable () -> Unit) {
    val elementModifier = Modifier.padding(4.dp)
    Box(elementModifier) {
        content()
    }
}

@Composable
private fun RaceStatus(race: SectionViewModel.RaceUiState) {
    RaceViewElement {
        val text = when (race) {
            SectionViewModel.RaceUiState.NoRaceServiceConnection -> "Not connected to race service"
            SectionViewModel.RaceUiState.NotInRaceMode -> "Not in race mode"
            is SectionViewModel.RaceUiState.RaceGoing -> {
                raceStatusText(race.raceModel)
            }

            is SectionViewModel.RaceUiState.RaceGoingInAnotherSection -> "Race going in another section"
            RaceNotStarted -> "Race not started"
            is SectionViewModel.RaceUiState.RaceStopped -> "Stopped, ${raceStatusText(race.raceModel)}"
        }
        Text(text)
    }
}

@Composable
private fun raceStatusText(race: RaceModel): String {
    val elapsedOrRemainingHours = Clock.System.now().minus(race.startTime).inWholeMilliseconds.toDouble() / 1000 / 3600
    val isElapsed = elapsedOrRemainingHours >= 0
    val duration = TimeOfDay(0, 0, 0, 0) + TimeHr(elapsedOrRemainingHours.absoluteValue).toMinSec()
    val distance = race.currentDistance
    return "${if (!isElapsed) "-" else ""}${duration.timeStrNoDayOverflow()} / ${distance.valueKm.strRound3()}"
}

@Composable
private fun RaceControls(
    race: SectionViewModel.RaceUiState,
    selectedPosition: PositionLine?,
    onStartRace: (startTime: Instant) -> Unit,
    onStopRace: () -> Unit,
    onResetRace: () -> Unit,
    onSetDebugSpeed: (Int) -> Unit,
    navigateToSection: (Long) -> Unit
) {
    when (race) {
        RaceNotStarted -> {
            RaceViewElement {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Start${selectedPosition?.atKm?.valueKm?.let { " at ${it.strRound3()}:" }.orEmpty()}")
                    Button(
                        enabled = selectedPosition != null,
                        onClick = {
                            onStartRace(Clock.System.now())
                        }) {
                        Text("Now")
                    }
                    val astroTime = selectedPosition?.modifier<PositionLineModifier.AstroTime>()
                    if (astroTime != null) {
                        val timeOfDay = astroTime.timeOfDay
                        val startInstant = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.atTime(
                            LocalTime(timeOfDay.hr, timeOfDay.min, timeOfDay.sec)
                        )
                        Button(onClick = {
                            onStartRace(startInstant.toInstant(TimeZone.currentSystemDefault()))
                        }) {
                            Text("At ${timeOfDay.timeStrNoDayOverflow()}")
                        }
                    }
                }
            }
        }

        is SectionViewModel.RaceUiState.RaceGoing -> {
            RaceViewElement {
                FinishRace(onStopRace)
            }
        }

        is SectionViewModel.RaceUiState.RaceGoingInAnotherSection -> {
            RaceViewElement {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { navigateToSection(race.raceSectionId) }) {
                        Text("Go to race section")
                    }
                    FinishRace(onStopRace)
                }
            }
        }

        SectionViewModel.RaceUiState.NoRaceServiceConnection,
        SectionViewModel.RaceUiState.NotInRaceMode -> Unit

        is SectionViewModel.RaceUiState.RaceStopped -> {
            RaceViewElement {
                Button(onResetRace) {
                    Text("Reset")
                }
            }
        }
    }
    if (BuildConfig.DEBUG) {
        DebugSpeedSlider(onSetDebugSpeed)
    }
}

@Composable
private fun FinishRace(onStopRace: () -> Unit) {
    Button(
        colors = ButtonDefaults.buttonColors(LocalCustomColorsPalette.current.dangerous),
        onClick = { onStopRace() }
    ) {
        Text("Finish")
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
                Modifier.width(200.dp)
            )
            Text("$speedSliderValue/h", Modifier.wrapContentWidth())
        }
    }
}