package com.h0tk3y.rally.android.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.h0tk3y.rally.*
import com.h0tk3y.rally.PositionLineModifier.IsSynthetic
import com.h0tk3y.rally.R
import com.h0tk3y.rally.android.scenes.*
import com.h0tk3y.rally.android.scenes.DataKind.*
import com.h0tk3y.rally.android.theme.LocalCustomColorsPalette
import com.h0tk3y.rally.model.duration
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.absoluteValue
import kotlin.math.ceil

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PositionsListView(
    listState: LazyListState,
    odoDistances: Map<LineNumber, DistanceKm>,
    positionsList: List<RoadmapInputLine>,
    selectedLineIndex: LineNumber,
    raceCurrentLineIndex: LineNumber?,
    editorControls: EditorControls?,
    editorState: EditorState,
    editorFocus: EditorFocus,
    results: RallyTimesResult,
    subsMatch: SubsMatch,
    allowance: TimeAllowance?,
    raceViewShown: Boolean,
    raceState: RaceUiState.RaceGoing?
) {
    val time by produceState(Clock.System.now(), raceState) {
        if (raceState != null) {
            while (true) {
                val time = Clock.System.now()
                val currentTime = time.toLocalDateTime(TimeZone.currentSystemDefault())
                val millisToNextWholeSecond = 1000 - (currentTime.time.toMillisecondOfDay() - currentTime.time.toSecondOfDay() * 1000)
                value = time
                val delayTime = millisToNextWholeSecond.toLong()
                delay(delayTime)
            }
        } else value = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.atStartOfDayIn(TimeZone.currentSystemDefault())
    }

    val isEditorEnabled = editorState.isEnabled

    val interactionSource = remember { MutableInteractionSource() }
    Column {
        if (!isEditorEnabled && results is RallyTimesResultFailure && results.failures.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalCustomColorsPalette.current.dangerous)
            ) {
                Text(modifier = Modifier.padding(8.dp), text = stringResource(R.string.failedToCalculateErrors))
            }
        }
        LaunchedEffect(key1 = positionsList, key2 = editorFocus, key3 = selectedLineIndex) {
            val selectedIndex = positionsList.indexOfFirst { it.lineNumber == selectedLineIndex }
            val visibleItemsInfo = listState.layoutInfo.visibleItemsInfo
            if ((visibleItemsInfo.firstOrNull()?.index ?: 0) > selectedIndex ||
                (visibleItemsInfo.lastOrNull()?.index ?: 0) < selectedIndex
            ) {
                if (selectedIndex != -1) {
                    listState.animateScrollToItem(if (selectedIndex >= 3) selectedIndex - 3 else selectedIndex)
                }
            }
        }
        val errorsByLine = if (results is RallyTimesResultFailure) results.failures.groupBy { it.line } else emptyMap()

        LazyColumn(state = listState) {

            itemsIndexed(positionsList) { _, line ->
                val isSelectedLine = line.lineNumber == selectedLineIndex
                val isRaceCurrentLine = line.lineNumber == raceCurrentLineIndex
                val background = when {
                    isSelectedLine -> Modifier.background(LocalCustomColorsPalette.current.selection)
                    isRaceCurrentLine -> Modifier.background(LocalCustomColorsPalette.current.raceCurrent)
                    else -> Modifier
                }
                Row(
                    modifier = background
                        .padding(8.dp)
                        .fillMaxWidth()
                        .clickable(interactionSource, indication = null, onClick = { editorControls?.selectLine(line.lineNumber, null) }),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (line is PositionLine) {
                        val position = editorFocus.cursor

                        Column(modifier = Modifier.align(Alignment.CenterVertically)) {
                            val currentLineOdo = odoDistances.get(line.lineNumber)
                            DataField(
                                Distance,
                                line,
                                position,
                                isEditorEnabled,
                                isSelectedLine,
                                editorFocus,
                                editorControls,
                                editorState,
                                subsMatch,
                                Modifier.align(Alignment.Start)
                            )
                            if (currentLineOdo != null) {
                                FocusedTextFieldWithoutKeyboard(
                                    currentLineOdo.valueKm.strRound3(),
                                    AverageSpeed,
                                    modifier = Modifier
                                        .alpha(0.5f)
                                        .align(Alignment.Start),
                                    selectionPosition = position,
                                    focused = false,
                                    onTextChange = { },
                                    onFocused = { },
                                    onPositionChange = { },
                                    enabled = false
                                )
                            }
                        }
                    }
                    FlowRow {
                        when (line) {
                            is PositionLine -> {
                                val cursorIndex = editorFocus.cursor

                                if (line.modifier<PositionLineModifier.EndAvgSpeed>() != null) {
                                    val matchId = subsMatch.subNumbers[line]
                                    val matchSpeed = subsMatch.endSubMatch[line]?.modifier<PositionLineModifier.SetAvg>()
                                    LabelForField(
                                        buildString {
                                            append(stringResource(R.string.labelEndavg))
                                            if (matchId != null) append(matchId)
                                        }, Modifier.align(Alignment.CenterVertically)
                                    )

                                    if (matchSpeed != null) {
                                        FocusedTextFieldWithoutKeyboard(
                                            matchSpeed.setavg.valueKmh.strRound3(),
                                            AverageSpeed,
                                            modifier = Modifier
                                                .alpha(0.5f)
                                                .align(Alignment.CenterVertically),
                                            selectionPosition = cursorIndex,
                                            focused = false,
                                            onTextChange = { },
                                            onFocused = { },
                                            onPositionChange = { },
                                            enabled = false
                                        )
                                    }
                                }
                                presentFields(line).filter { it != Distance && shouldBeDisplayed(it, editorState) }
                                    .forEach { field ->
                                        DataField(
                                            field,
                                            line,
                                            cursorIndex,
                                            isEditorEnabled,
                                            isSelectedLine,
                                            editorFocus,
                                            editorControls,
                                            editorState,
                                            subsMatch,
                                            Modifier.align(Alignment.CenterVertically)
                                        )
                                    }

                                when (results) {
                                    is RallyTimesResultFailure -> {
                                        val failuresInCurrentLine = errorsByLine[line].orEmpty()
                                        if (failuresInCurrentLine.isNotEmpty()) {
                                            failuresInCurrentLine.forEach {
                                                if (!editorState.isEnabled || showFailureInEditor(it.reason)) {
                                                    val text = when (val reason = it.reason) {
                                                        is FailureReason.AverageSpeedUnknown -> stringResource(R.string.errOutsideIntervals)
                                                        is FailureReason.UnexpectedAverageEnd -> stringResource(R.string.errNoSetavg)
                                                        is FailureReason.NonMatchingAverageEnd -> stringResource(R.string.errEndAvgDoesNotMatch)
                                                        is FailureReason.DistanceIsNotIncreasing -> "< ${reason.shouldBeAtLeast.valueKm.strRound3()}"
                                                        is FailureReason.OuterIntervalNotCovered -> stringResource(R.string.errOuterNotCovered)
                                                    }
                                                    Text(
                                                        text = text,
                                                        color = LocalCustomColorsPalette.current.dangerous,
                                                        modifier = Modifier.padding(start = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    is RallyTimesResultSuccess -> {
                                        if (!isEditorEnabled) {
                                            val warningsInCurrentLine = results.warnings.filter { it.line == line }
                                            val go = results.goAtAvgSpeed[line.lineNumber]
                                            if (warningsInCurrentLine.isNotEmpty() || go != null) {
                                                if (go != null && go.valueKmh >= 0) {
                                                    Text(
                                                        modifier = Modifier
                                                            .alpha(0.5f)
                                                            .align(Alignment.CenterVertically)
                                                            .padding(start = 4.dp),
                                                        text = "➠${go.valueKmh.strRound1()}",
                                                    )
                                                }
                                                warningsInCurrentLine.forEach {
                                                    val text = when (val reason = it.reason) {
                                                        is WarningReason.ImpossibleToGetInTime -> stringResource(
                                                            R.string.warnLateBy,
                                                            (reason.takes - reason.available).toMinSec()
                                                        )
                                                    }
                                                    Text(
                                                        modifier = Modifier
                                                            .align(Alignment.CenterVertically)
                                                            .padding(start = 4.dp),
                                                        text = text,
                                                        color = LocalCustomColorsPalette.current.dangerous
                                                    )
                                                }
                                            }

                                            val currentLineTimes =
                                                (if (!raceViewShown || raceState != null) results.timeVectorsAtRoadmapLine else results.timesForOuterInterval)[line.lineNumber]

                                            val currentLineAtime =
                                                (if (!raceViewShown || raceState != null) results.astroTimeAtRoadmapLine else results.astroTimeAtRoadmapLineForOuter)[line.lineNumber]

                                            if (currentLineTimes != null) {
                                                val outerTime = currentLineTimes.outer
                                                val timeValues =
                                                    if (currentLineAtime != null) currentLineTimes.values + currentLineAtime else currentLineTimes.values
                                                Row(Modifier.weight(1.0f), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) {
                                                    if (isSelectedLine && raceState != null && currentLineAtime != null) {
                                                        CountdownToPosition(time, currentLineAtime)
                                                    }

                                                    for ((index, value) in timeValues.withIndex()) {
                                                        val text = when (value) {
                                                            is TimeHr -> value.toMinSec().toString()
                                                            is TimeDayHrMinSec -> value.timeStrNoDayOverflow()
                                                            else -> error("unexpected time vector value")
                                                        }
                                                        Text(
                                                            text = text,
                                                            modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                                                        )
                                                        if (raceState == null && allowance != null && index == timeValues.lastIndex) {
                                                            if (outerTime.timeHours.isFinite()) {
                                                                val allowanceTime = allowance(allowance, outerTime)
                                                                Text(
                                                                    text = "-$allowanceTime",
                                                                    modifier = Modifier.padding(start = 0.dp, end = 8.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            is CommentLine -> {
                                Text(line.commentText)
                            }
                        }
                    }
                }
                Divider()
            }

        }
    }
}

@Composable
private fun CountdownToPosition(time: Instant, currentLineAtime: TimeDayHrMinSec) {
    val now = time.toLocalDateTime(TimeZone.currentSystemDefault())
    val targetTime = now.date.plus(currentLineAtime.dayOverflow, DateTimeUnit.DAY)
        .atTime(LocalTime(currentLineAtime.hr, currentLineAtime.min, currentLineAtime.sec))
        .toInstant(TimeZone.currentSystemDefault())
    val countdown = time - targetTime
    if (countdown.isNegative()) {
        val text = if (countdown.inWholeSeconds.absoluteValue < 60) {
            "${countdown.inWholeSeconds}"
        } else {
            "-${TimeHr.duration(countdown.absoluteValue).toTimeDayHrMinSec().timeStrNoHoursIfZero()}"
        }
        Text(
            text = text,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp)
        )
    }
}

fun allowance(allowance: TimeAllowance?, time: TimeHr): Int {
    val sec = time.timeHours * 3600
    val minutes = sec / 60.0
    return when (allowance) {
        TimeAllowance.BY_TEN_FULL -> (minutes / 10).toInt()
        TimeAllowance.BY_TEN_FULL_PLUS_ONE -> ceil(minutes / 10.0).toInt()
        null -> 0
    }
}

@Composable
private fun DataField(
    field: DataKind,
    line: PositionLine,
    cursorIndex: Int,
    isEditorEnabled: Boolean,
    isSelectedLine: Boolean,
    editorFocus: EditorFocus,
    editorControls: EditorControls?,
    editorState: EditorState,
    subsMatch: SubsMatch,
    elementsModifier: Modifier
) {
    LabelForField(field, line, subsMatch.subNumbers[line], elementsModifier)
    val text = itemText(line, field) ?: ""

    FocusedTextFieldWithoutKeyboard(
        text,
        field,
        modifier = if (line.modifier<IsSynthetic>() != null) elementsModifier.alpha(0.5f) else elementsModifier,
        selectionPosition = cursorIndex,
        focused = isEditorEnabled && isSelectedLine && field == editorFocus.kind,
        onTextChange = { },
        onFocused = {
            editorControls?.selectLine(line.lineNumber, field)
        },
        onPositionChange = { editorControls?.moveCursor(it - cursorIndex) },
        enabled = editorState.isEnabled
    )
}

fun showFailureInEditor(reason: FailureReason) = reason is FailureReason.DistanceIsNotIncreasing

fun shouldBeDisplayed(field: DataKind, editorState: EditorState): Boolean =
    editorState.isEnabled || field in listOf(Distance, OdoDistance, AverageSpeed, AstroTime)

@Composable
fun LabelForField(kind: DataKind, line: PositionLine, matchId: Int?, modifier: Modifier) {
    val padding = modifier.padding(end = 2.dp)
    when (kind) {
        Distance -> Unit
        OdoDistance -> Text(stringResource(R.string.labelOdo), padding)
        AverageSpeed -> {
            val matchStr = matchId?.toString().orEmpty()
            val setavg = line.modifier<PositionLineModifier.SetAvg>()
            if (setavg != null) {
                LabelForField(
                    text = if (setavg is PositionLineModifier.SetAvgSpeed) stringResource(R.string.labelSetavg, matchStr) else
                        if (setavg is PositionLineModifier.ThenAvgSpeed) stringResource(R.string.labelThenAvg, matchStr)
                        else "???",
                    padding
                )
            } else Unit
        }

        SyntheticCount -> Text(stringResource(R.string.labelSynth), padding)
        SyntheticInterval -> Text(stringResource(R.string.labelEach), padding)
        AstroTime -> Text(stringResource(R.string.labelAtime), modifier)
    }
}

@Composable
fun LabelForField(text: String, modifier: Modifier) {
    Text(text, modifier.padding(end = 2.dp))
}

