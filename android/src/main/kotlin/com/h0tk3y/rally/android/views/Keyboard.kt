package com.h0tk3y.rally.android.views

import androidx.compose.foundation.layout.*
import com.h0tk3y.rally.android.views.GridKey.*
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.h0tk3y.rally.R
import com.h0tk3y.rally.android.scenes.EditorControls
import com.h0tk3y.rally.android.scenes.EditorState
import com.h0tk3y.rally.android.theme.LocalCustomColorsPalette

@Composable
fun Keyboard(
    editorState: EditorState,
    editorControls: EditorControls,
    modifier: Modifier
) = with(editorState) {
    Column(verticalArrangement = Arrangement.SpaceEvenly, modifier = modifier) {
        val isEnabled = mapOf(
            DOT to canEnterDot,
            UP to canMoveUp,
            DOWN to canMoveDown,
            LEFT to canMoveLeft,
            RIGHT to canMoveRight,
            REMOVE to canDelete,
            NOP to false,
        ) + GridKey.entries.filter { it.name.startsWith("N_") }.map {
            val n = it.name.substringAfter("N_").toInt()
            val canEnter = n <= maxDigit
            it to canEnter
        }

        keyboardButtonsOrder.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (row == null)
                    Divider()
                else
                    row.forEach {
                        val isButtonEnabled = isEnabled[it] ?: true
                        val hapticFeedback = LocalHapticFeedback.current
                        TextButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                editorControls.keyPress(it)
                            },
                            enabled = isButtonEnabled,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                it.localizedText(),
                                modifier = Modifier.padding(4.dp),
                                fontSize = if (it == DOT || it == DEL || it.name.startsWith("N_")) 24.sp else 14.sp,
                                style = TextStyle(
                                    color = if (isDangerous(it)) LocalCustomColorsPalette.current.dangerous else Color.Unspecified
                                )
                            )
                        }
                    }
            }
        }
    }
}

@Composable
fun GridKey.localizedText(): String {
    return when (this) {
        SETAVG -> stringResource(R.string.keySetavg)
        THENAVG -> stringResource(R.string.keyThen)
        ENDAVG -> stringResource(R.string.keyEndavg)
        SYNTH -> stringResource(R.string.keySynth)
        ATIME -> stringResource(R.string.keyAtime)
        ODO -> stringResource(R.string.keyOdo)
        ADD_BELOW -> stringResource(R.string.keyAddBelow)
        ADD_ABOVE -> stringResource(R.string.keyAddAbove)
        REMOVE -> stringResource(R.string.keyRemove)
        else -> text
    }
}

private val keyboardButtonsOrder = listOf(
    null,
    listOf(REMOVE, SYNTH, ADD_ABOVE, ADD_BELOW),
    null,
    listOf(ATIME, ODO, SETAVG, THENAVG, ENDAVG),
    null,
    listOf(UP, DOWN, LEFT, RIGHT),
    null,
    listOf(N_1, N_2, N_3),
    listOf(N_4, N_5, N_6),
    listOf(N_7, N_8, N_9),
    listOf(DOT, N_0, DEL)
)

fun isDangerous(gridKey: GridKey): Boolean = gridKey === REMOVE || gridKey === DEL

enum class GridKey(val text: String, val isStub: Boolean = false) {
    SETAVG("SET"), THENAVG("THEN"), ENDAVG("END"), SYNTH("SYNTH"), ATIME("ATIME"), ODO("ODO"),
    N_1("1"), N_2("2"), N_3("3"),
    N_4("4"), N_5("5"), N_6("6"),
    N_7("7"), N_8("8"), N_9("9"),
    DOT("."), N_0("0"), NOP("", isStub = true),
    UP("▲"), DOWN("▼"), DEL("⌫"),
    LEFT("◀"), RIGHT("▶"),
    ADD_BELOW("ADD▼"), ADD_ABOVE("ADD▲"),
    REMOVE("DEL ╳")
}
