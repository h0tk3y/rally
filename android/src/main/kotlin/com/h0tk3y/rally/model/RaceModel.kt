package com.h0tk3y.rally.model

import com.h0tk3y.rally.DistanceKm
import com.h0tk3y.rally.SpeedKmh
import com.h0tk3y.rally.TimeHr
import kotlinx.datetime.Instant
import kotlin.time.Duration

sealed interface RaceState {
    sealed interface HasCurrentSection : RaceState {
        val raceSectionId: Long   
    }
    
    sealed interface MovingWithRaceModel : HasCurrentSection {
        val raceModel: RaceModel
    }
    
    data object NotStarted : RaceState

    data class Stopped(
        val raceSectionIdAtStop: Long,
        val raceModelAtStop: RaceModel,
        val stoppedAt: Instant,
        val finishedAt: Instant?,
        val finishedModel: RaceModel?,
    ) : RaceState
    
    data class InRace(
        override val raceSectionId: Long,
        override val raceModel: RaceModel,
        val previousFinishAt: Instant?,
        val previousFinishModel: RaceModel?,
        val goingModel: RaceModel
    ) : RaceState, MovingWithRaceModel
    
    data class Going(
        override val raceSectionId: Long,
        override val raceModel: RaceModel,
        val finishedRaceModel: RaceModel?,
        val finishedAt: Instant?
    ) : RaceState, MovingWithRaceModel
}

data class RaceModel(
    val startAtTime: Instant,
    val startAtDistance: DistanceKm,

    val currentDistance: DistanceKm,

    val distanceCorrection: DistanceKm,
    val distanceGoingUp: Boolean,
    val instantSpeed: SpeedKmh,
) {
    fun averageSpeed(atTime: Instant): SpeedKmh =
        if (atTime > startAtTime) {
            SpeedKmh.averageAt(currentDistance - startAtDistance, TimeHr.interval(startAtTime, atTime))
        } else SpeedKmh(0.0)
}

fun TimeHr.Companion.interval(from: Instant, to: Instant): TimeHr =
    TimeHr.duration(to - from)

fun TimeHr.Companion.duration(duration: Duration): TimeHr =
    TimeHr(duration.inWholeMicroseconds / 1000_000.0 / 3600)