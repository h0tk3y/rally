package com.h0tk3y.rally.android

/**
 * The speeds (km/h) of the speed buttons in the race panel, configurable in the settings.
 *
 * The user types them as text, see [parseSpeedButtonsInput]. They are stored in a strict form, see [encodeSpeedButtons]
 * and [decodeStoredSpeedButtons].
 */
object SpeedButtons {
    val DEFAULT: List<Int> = listOf(5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110)

    /** Three digits, so that the label fits into a round button */
    const val MAX_SPEED = 999

    /** The buttons scroll horizontally, so this only keeps the row from getting unreasonably long */
    const val MAX_COUNT = 30
}

sealed interface SpeedButtonsParseResult {
    data class Valid(val speeds: List<Int>) : SpeedButtonsParseResult

    sealed interface Invalid : SpeedButtonsParseResult

    /** Nothing but separators, or no text at all */
    data object Empty : Invalid

    /** A part that is not made of digits only, such as `abc`, `4O`, `٤٥` or `42.5` */
    data class NotAWholeNumber(val token: String) : Invalid

    /** A number of 4+ digits, or zero (`0`, `000`) */
    data class OutOfRange(val token: String) : Invalid

    /** More than [SpeedButtons.MAX_COUNT] distinct speeds */
    data class TooMany(val count: Int) : Invalid
}

/**
 * Dots and characters that look like one. They are not separators, so that `42.5`, `20.` or `.5` is reported as not a whole
 * number instead of being read as other speeds: the full stop, its fullwidth and small forms, the one/two dot leaders, the
 * ellipsis, the middle dot, the Arabic decimal separator, and the ideographic full stops.
 */
private const val DOT_LIKE = ".\uFF0E\uFE52\u2024\u2025\u2026\u00B7\u066B\u3002\uFF61"

/**
 * Any run of whitespace, punctuation or symbols other than [DOT_LIKE] separates the speeds: `20 40, 60; 80`.
 * Note that this includes `-`, so `-20` is read as 20.
 */
private val inputSeparators = Regex("[[\\s\\p{Z}\\p{P}\\p{S}]&&[^$DOT_LIKE]]+")

private val asciiDigits = Regex("[0-9]+")

/**
 * Parses the speeds the user typed for the speed buttons.
 *
 * * A dot is not a separator, so a fractional speed such as `42.5` is [SpeedButtonsParseResult.NotAWholeNumber].
 * * Each speed is a positive whole number of 1–3 ASCII digits; leading zeros are allowed (`05` is 5), but `0` and `000` are not positive.
 * * The order is kept as typed. A repeated speed is kept only at its first place.
 * * The first invalid part is reported, so that the user can find it.
 */
fun parseSpeedButtonsInput(text: String): SpeedButtonsParseResult {
    val tokens = text.split(inputSeparators).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) {
        return SpeedButtonsParseResult.Empty
    }
    val speeds = LinkedHashSet<Int>()
    for (token in tokens) {
        if (!asciiDigits.matches(token)) {
            return SpeedButtonsParseResult.NotAWholeNumber(token)
        }
        val value = if (token.length <= 3) token.toInt() else null
        if (value == null || value !in 1..SpeedButtons.MAX_SPEED) {
            return SpeedButtonsParseResult.OutOfRange(token)
        }
        speeds += value
    }
    if (speeds.size > SpeedButtons.MAX_COUNT) {
        return SpeedButtonsParseResult.TooMany(speeds.size)
    }
    return SpeedButtonsParseResult.Valid(speeds.toList())
}

/** The stored form, which is also what the settings field shows: the speeds separated by single spaces */
fun encodeSpeedButtons(speeds: List<Int>): String = speeds.joinToString(" ")

/**
 * Reads the stored speeds strictly: anything that [encodeSpeedButtons] could not have written for a valid list gives
 * [SpeedButtons.DEFAULT], so that the race panel always has buttons.
 *
 * This is stricter than [parseSpeedButtonsInput] on purpose: a value written in some other format (for example,
 * `42.5 60` if a later version allows fractional speeds) must not be read as a different list (42, 5, 60).
 */
fun decodeStoredSpeedButtons(stored: String?): List<Int> {
    if (stored.isNullOrEmpty()) return SpeedButtons.DEFAULT
    val parts = stored.split(' ')
    if (parts.size > SpeedButtons.MAX_COUNT) return SpeedButtons.DEFAULT
    val speeds = parts.map { part ->
        if (part.isEmpty() || part.length > 3 || !asciiDigits.matches(part)) return SpeedButtons.DEFAULT
        part.toInt().takeIf { it in 1..SpeedButtons.MAX_SPEED } ?: return SpeedButtons.DEFAULT
    }
    return speeds.distinct()
}
