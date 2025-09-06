package com.h0tk3y.rally.android.racecervice

import android.location.Location
import androidx.core.location.GnssStatusCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class DistanceAccumulator {
    private var lastAcceptedFix: Location? = null
    private var stationarySinceMs: Long? = null
    private var distanceBySpeedAcc = 0.0

    // Tunables (start with these, tune on real routes)
    private val maxHdopMeters = 12.0            // use acc as proxy
    private val minSatsForGood = 7              // tweak per device/area
    private val minMoveMeters = 1.0             // segment threshold
    private val maxJumpSpeedMps = 100.0         // sanity cap
    private val stationarySpeedMps = 0.3        // below this we consider stopping
    private val stationaryHoldMs = 3000L
    private val totalDistanceFlow = MutableStateFlow(0.0)
    private val currentSpeedFlow = MutableStateFlow(0.0)
    private val isGoodLocationFlow = MutableStateFlow(false)
    private val satsInUseFlow = MutableStateFlow(0)
    private val lastFixTimeFlow = MutableStateFlow<Instant?>(null)

    val currentSpeedKmh: StateFlow<Double> get() = currentSpeedFlow
    val totalDistanceMeters: StateFlow<Double> get() = totalDistanceFlow
    val isGoodLocation: StateFlow<Boolean> get() = isGoodLocationFlow
    val satsInUse: StateFlow<Int> get() = satsInUseFlow
    val lastFixTime: StateFlow<Instant?> get() = lastFixTimeFlow

    fun updateGnssQuality(status: GnssStatusCompat) {
        satsInUseFlow.value = (0 until status.satelliteCount).count { status.usedInFix(it) }
    }

    fun ingest(raw: Location) {
        if (!raw.hasAccuracy() || raw.accuracy.isNaN()) return

        // Gate 1: obvious outliers
        if (raw.speed.isFinite() && raw.speed > maxJumpSpeedMps) return
        if (raw.accuracy > 50f && satsInUseFlow.value < 4) return // very poor

        val prev = lastAcceptedFix
        if (prev == null) {
            if (isGood(raw)) {
                gpsFix(raw)
            }
            return
        }

        val dt = (raw.time - prev.time).coerceAtLeast(0L) / 1000.0
        if (dt <= 0.0) return

        val posDist = geodesicDistance(prev, raw)          // meters
        val posOk = isGood(raw) && posDist >= minMoveMeters

        // Stationary detection (freeze drift)
        val isSlow = raw.hasSpeed() && raw.speed <= stationarySpeedMps
        if (isSlow) {
            if (stationarySinceMs == null) {
                stationarySinceMs = raw.time
            }
        } else {
            stationarySinceMs = null
        }

        val stationary = stationarySinceMs?.let { raw.time - it >= stationaryHoldMs } ?: false

        val useSpeed = !posOk && raw.hasSpeed() && !stationary
        val incr = when {
            stationary -> 0.0
            posOk -> {
                (posDist - distanceBySpeedAcc).coerceAtLeast(0.0).also {
                    distanceBySpeedAcc = 0.0
                }
            }

            useSpeed -> {
                (raw.speed.toDouble() * dt).also { distanceBySpeedAcc += it }
            }

            else -> 0.0
        }

        isGoodLocationFlow.value = posOk || stationary

        if (posOk || useSpeed || stationary) {
            currentSpeedFlow.value = speedMpsToKmh(raw.speed.toDouble())
            lastAcceptedFix = raw
            lastFixTimeFlow.value = Clock.System.now()
        }
        if (incr > 0.0) {
            totalDistanceFlow.value += incr
        }
    }

    private fun gpsFix(raw: Location) {
        lastAcceptedFix = raw
        lastFixTimeFlow.value = Clock.System.now()
    }

    private fun isGood(l: Location): Boolean {
        val acc = l.accuracy.toDouble()
        val satsOk = satsInUseFlow.value >= minSatsForGood
        // Gate on accuracy AND satellites if you have them
        return acc <= maxHdopMeters || satsOk
    }

    private fun geodesicDistance(a: Location, b: Location): Double {
        // fast Haversine; use a Vincenty impl for long segments if desired
        val r = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val sinDLat = sin(dLat / 2)
        val sinDLon = sin(dLon / 2)
        val h = sinDLat * sinDLat + cos(lat1) * cos(lat2) * sinDLon * sinDLon
        return 2 * r * asin(sqrt(h))
    }

    private fun speedMpsToKmh(speedMps: Double) = speedMps / 5 * 18
}