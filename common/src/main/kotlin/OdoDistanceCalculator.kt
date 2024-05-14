package com.h0tk3y.rally

object OdoDistanceCalculator {
    fun calculateOdoDistances(
        roadmap: List<PositionLine>,
        calibrationCoefficientOdoByLegend: Double = 1.0
    ): Map<LineNumber, DistanceKm> = buildMap {
        var lineWithOdoModifier: PositionLine? = null
        fun odoModifier() = lineWithOdoModifier!!.modifier<PositionLineModifier.OdoDistance>()!!.distanceKm

        roadmap.forEach {
            if (it.modifier<PositionLineModifier.OdoDistance>() != null) {
                lineWithOdoModifier = it
            }

            lineWithOdoModifier?.let { start ->
                put(it.lineNumber, odoModifier() + (it.atKm - start.atKm) * calibrationCoefficientOdoByLegend)
            }
        }
    }
}