package com.h0tk3y.rally.android.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Divider
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.h0tk3y.rally.*
import com.h0tk3y.rally.PositionLineModifier.IsSynthetic
import com.h0tk3y.rally.android.scenes.*
import com.h0tk3y.rally.android.scenes.DataKind.*
import com.h0tk3y.rally.android.theme.LocalCustomColorsPalette

@Composable
fun PositionsListView(
    listState: LazyListState,
    positionsList: List<RoadmapInputLine>,
    selectedLineIndex: LineNumber,
    editorControls: EditorControls,
    editorState: EditorState,
    editorFocus: EditorFocus,
    results: RallyTimesResult,
    subsMatch: SubsMatch,
    allowance: TimeAllowance?
) {
    val isEditorEnabled = editorState.isEnabled

    val interactionSource = remember { MutableInteractionSource() }
    Column {
        if (!isEditorEnabled && results is RallyTimesResultFailure && results.failures.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().background(LocalCustomColorsPalette.current.dangerous)) {
                Text(modifier = Modifier.padding(8.dp), text = "Failed to calculate because of errors")
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
        val errorsByLine = remember {
            if (results is RallyTimesResultFailure) results.failures.groupBy { it.line } else emptyMap()
        }
        LazyColumn(state = listState) {

            itemsIndexed(positionsList) { _, line ->
                val isSelectedLine = line.lineNumber == selectedLineIndex

                val background =
                    if (isSelectedLine) Modifier.background(LocalCustomColorsPalette.current.selection) else Modifier

                Row(
                    modifier = background.padding(8.dp).fillMaxWidth()
                        .clickable(
                            interactionSource,
                            indication = null,
                            onClick = { editorControls.selectLine(line.lineNumber, null) }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when (line) {
                        is PositionLine -> {
                            val position = editorFocus.cursor

                            DataField(
                                Distance,
                                line,
                                position,
                                isEditorEnabled,
                                isSelectedLine,
                                editorFocus,
                                editorControls,
                                editorState,
                                subsMatch
                            )
                            if (line.modifier<PositionLineModifier.EndAvgSpeed>() != null) {
                                val matchId = subsMatch.subNumbers[line]
                                val matchSpeed = subsMatch.endSubMatch[line]?.modifier<PositionLineModifier.SetAvg>()
                                LabelForField(buildString {
                                    append("endavg")
                                    if (matchId != null) append(matchId)
                                })

                                if (matchSpeed != null) {
                                    FocusedTextFieldWithoutKeyboard(
                                        matchSpeed.setavg.valueKmh.strRound3(),
                                        AverageSpeed,
                                        modifier = Modifier.alpha(0.5f),
                                        textStyle = LocalTextStyle.current.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 18.sp,
                                        ),
                                        selectionPosition = position,
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
                                        position,
                                        isEditorEnabled,
                                        isSelectedLine,
                                        editorFocus,
                                        editorControls,
                                        editorState,
                                        subsMatch
                                    )
                                }

                            Row(modifier = Modifier.weight(1.0f), horizontalArrangement = Arrangement.End) {
                                when (results) {
                                    is RallyTimesResultFailure -> {
                                        val failuresInCurrentLine = errorsByLine[line].orEmpty()
                                        if (failuresInCurrentLine.isNotEmpty()) {
                                            Column(horizontalAlignment = Alignment.End) {
                                                failuresInCurrentLine.forEach {
                                                    if (editorState.isEnabled.not() || showFailureInEditor(it.reason)) {
                                                        val text = when (val reason = it.reason) {
                                                            is FailureReason.AverageSpeedUnknown -> "outside intervals"
                                                            is FailureReason.UnexpectedAverageEnd -> "no setavg for this position"
                                                            is FailureReason.NonMatchingAverageEnd -> "endavg speed does not match"
                                                            is FailureReason.DistanceIsNotIncreasing -> "< ${reason.shouldBeAtLeast.valueKm.strRound3()}"
                                                            is FailureReason.OuterIntervalNotCovered -> "outer interval not covered by subs"
                                                        }
                                                        Text(
                                                            text = text,
                                                            color = LocalCustomColorsPalette.current.dangerous
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    is RallyTimesResultSuccess -> {
                                        if (!isEditorEnabled) {
                                            val warningsInCurrentLine = results.warnings.filter { it.line == line }
                                            val go = results.goAtAvgSpeed[line.lineNumber]
                                            if (warningsInCurrentLine.isNotEmpty() || go != null) {
                                                Column(horizontalAlignment = Alignment.End) {
                                                    if (go != null && go.valueKmh >= 0) {
                                                        Text(
                                                            modifier = Modifier.alpha(0.5f),
                                                            text = "➠${go.valueKmh.strRound1()}",
                                                        )
                                                    }
                                                    warningsInCurrentLine.forEach {
                                                        val text = when (val reason = it.reason) {
                                                            is WarningReason.ImpossibleToGetInTime -> "impossible, ${(reason.takes - reason.available).toMinSec()} late"
                                                        }
                                                        Text(
                                                            text = text,
                                                            color = LocalCustomColorsPalette.current.dangerous
                                                        )
                                                    }
                                                }
                                            }
                                            val currentLineTimes = results.timeVectorsAtRoadmapLine[line.lineNumber]
                                            if (currentLineTimes != null) {
                                                val timeValues = currentLineTimes.values
                                                for ((index, value) in timeValues.withIndex()) {
                                                    Text(
                                                        text = value.toMinSec().toString(),
                                                        modifier = Modifier.padding(start = 8.dp, end = 8.dp)
                                                    )
                                                    if (allowance != null && index == timeValues.lastIndex) {
                                                        val allowanceTime = allowance(allowance, value)
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

                        is CommentLine -> {
                            Text(line.commentText)
                        }

                        else -> error("Unexpected line $line")
                    }
                }
                Divider()
            }

        }
    }
}

fun allowance(allowance: TimeAllowance?, time: TimeHr): Int {
    val sec = time.toMinSec().min * 60 + time.toMinSec().sec
    val minutes = sec / 60.0
    return when (allowance) {
        TimeAllowance.BY_TEN_FULL -> (minutes / 10).toInt()
        TimeAllowance.BY_TEN_FULL_PLUS_ONE -> Math.ceil(minutes / 10.0).toInt()
        null -> 0
    }
}

@Composable
private fun DataField(
    field: DataKind,
    line: PositionLine,
    position: Int,
    isEditorEnabled: Boolean,
    isSelectedLine: Boolean,
    editorFocus: EditorFocus,
    editorControls: EditorControls,
    editorState: EditorState,
    subsMatch: SubsMatch
) {
    LabelForField(field, line, subsMatch.subNumbers[line])
    val text = itemText(line, field) ?: ""

    FocusedTextFieldWithoutKeyboard(
        text,
        field,
        modifier = if (line.modifier<IsSynthetic>() != null) Modifier.alpha(0.5f) else Modifier,
        textStyle = LocalTextStyle.current.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 18.sp,
        ),
        selectionPosition = position,
        focused = isEditorEnabled && isSelectedLine && field == editorFocus.kind,
        onTextChange = { },
        onFocused = {
            editorControls.selectLine(line.lineNumber, field)
        },
        onPositionChange = { editorControls.moveCursor(it - position) },
        enabled = editorState.isEnabled
    )
}

fun showFailureInEditor(reason: FailureReason) = reason is FailureReason.DistanceIsNotIncreasing

fun shouldBeDisplayed(field: DataKind, editorState: EditorState): Boolean =
    editorState.isEnabled || field in listOf(Distance, AverageSpeed)

@Composable
fun labelAfterField(kind: DataKind) {
    if (kind == AverageSpeed)
        LabelForField("/h")
    if (kind == SyntheticInterval)
        LabelForField("km")
}

@Composable
fun LabelForField(kind: DataKind, line: PositionLine, matchId: Int?) {
    val padding = Modifier.padding(end = 4.dp)
    val matchStr = if (kind == AverageSpeed) matchId?.toString().orEmpty() else ""
    when (kind) {
        Distance -> Unit
        AverageSpeed -> {
            val modifier = line.modifier<PositionLineModifier.SetAvg>()
            if (modifier != null) {
                LabelForField(
                    text = if (modifier is PositionLineModifier.SetAvgSpeed) "setavg$matchStr" else
                        if (modifier is PositionLineModifier.ThenAvgSpeed) "thenavg$matchStr"
                        else "???",
                )
            } else Unit
        }

        SyntheticCount -> Text("synth", padding)
        SyntheticInterval -> Text("each", padding)
    }
}

@Composable
fun LabelForField(text: String) {
    val padding = Modifier.padding(end = 4.dp)
    Text(text, padding)
}

