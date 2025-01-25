package com.h0tk3y.rally.model

import com.h0tk3y.rally.DistanceKm
import kotlinx.datetime.Instant

sealed interface RaceState {
    data object NotStarted : RaceState
    data class Stopped(
        val raceModel: RaceModel,
        val stoppedAt: Instant
    ) : RaceState
    data class InRace(
        val raceSectionId: Long,
        val raceModel: RaceModel,
        val serial: Long
    ) : RaceState
}

data class RaceModel(
    val startTime: Instant,
    val startAtDistance: DistanceKm,
    val currentDistance: DistanceKm,
    val distanceCorrection: DistanceKm,
    val deltaDistanceGoingUp: Boolean
)