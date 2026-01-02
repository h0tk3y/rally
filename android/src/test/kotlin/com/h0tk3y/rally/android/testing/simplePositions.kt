package com.h0tk3y.rally.android.testing

internal fun simplePositions(from: Double, to: Double, n: Int) = buildString {
    appendLine("$from setavg 60.0")
    val step = (to - from) / n
    generateSequence(1, Int::inc).map { it * step }.takeWhile { it < to }.forEach {
        appendLine(it)
    }
    appendLine("$to endavg")
}
