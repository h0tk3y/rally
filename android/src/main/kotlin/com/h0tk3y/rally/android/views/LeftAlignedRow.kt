package com.h0tk3y.rally.android.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

@Composable
fun LeftAlignedRow(
    maxLeftWidth: MutableState<Int>,
    leftContent: @Composable () -> Unit,
    rightContent: @Composable () -> Unit,
) {
    val spacingPx = with(LocalDensity.current) { 8.dp.roundToPx() }

    SubcomposeLayout { constraints ->
        val fullWidth = constraints.maxWidth

        // First: measure natural width of leftContent
        val leftNatural = subcompose("leftNatural", leftContent).map {
            it.measure(Constraints())
        }
        val leftWidth = leftNatural.maxOfOrNull { it.width } ?: 0
        if (leftWidth > maxLeftWidth.value) {
            maxLeftWidth.value = leftWidth
        }

        // Second: fixed-width left + fill-max right
        val leftPlaceables = subcompose("leftFixed", leftContent).map {
            it.measure(Constraints.fixedWidth(maxLeftWidth.value))
        }
        val rightPlaceables = subcompose("right", rightContent).map {
            it.measure(
                Constraints(
                    minWidth = fullWidth - maxLeftWidth.value - spacingPx,
                    maxWidth = fullWidth - maxLeftWidth.value - spacingPx
                )
            )
        }

        val height = maxOf(
            leftPlaceables.maxOfOrNull { it.height } ?: 0,
            rightPlaceables.maxOfOrNull { it.height } ?: 0
        )

        layout(fullWidth, height) {
            var x = 0
            leftPlaceables.forEach {
                val y = (height - it.height) / 2
                it.placeRelative(x, y)
                x += it.width + spacingPx
            }
            rightPlaceables.forEach {
                val y = (height - it.height) / 2
                it.placeRelative(x, y)
            }
        }
    }}
