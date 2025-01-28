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
    
    data object NotStarted : RaceState

    data class Stopped(
        val raceSectionIdAtStop: Long,
        val raceModelAtStop: RaceModel,
        val stoppedAt: Instant
    ) : RaceState
    
    data class InRace(
        override val raceSectionId: Long,
        val raceModel: RaceModel,
    ) : RaceState, HasCurrentSection
    
    data class Finished(
        override val raceSectionId: Long,
        val raceModel: RaceModel,
        val finishedRaceModel: RaceModel,
        val finishedAt: Instant
    ) : RaceState, HasCurrentSection
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
    TimeHr(duration.inWholeMilliseconds / 1000.0 / 3600.0)