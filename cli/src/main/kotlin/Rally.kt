package com.h0tk3y.rally

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.defaultStdin
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.inputStream

fun main(args: Array<String>) {
    TimetableCommand().main(args)
}

class TimetableCommand : CliktCommand() {
    private val calibrationFactor by option(
        "--calibration-factor", "-tar",
        help = "the calibration coefficient for the distance, " +
                "calculated as the measured mileage divided by the known calibration mileage; " +
                "used for representing the distances in a way that they match the measurements"
    ).double()

    private val input by option(
        "--input",
        help = "the input roadmap data file; if not specified, the standard input is used"
    ).inputStream().defaultStdin()

    override fun run() {
        val parser = InputRoadmapParser(DefaultModifierValidator())
        val formatter = ResultsFormatter()
        
        val input: List<RoadmapInputLine> = input.use {
            parser.parseRoadmap(it.reader())
        }
        val preprocessed = preprocessRoadmap(input)

        val calculator = RallyTimesIntervalsCalculator()
        val result = calculator.rallyTimes(preprocessed.filterIsInstance<PositionLine>())

        when (result) {
            is RallyTimesResultSuccess -> {
                val calculatedAverages = calculateAverages(preprocessed, result)
                println(formatter.formatResults(preprocessed, result, calculatedAverages, calibrationFactor))
            }

            is RallyTimesResultFailure -> {
                println(formatter.formatFailures(result))
            }
        }
    }
}