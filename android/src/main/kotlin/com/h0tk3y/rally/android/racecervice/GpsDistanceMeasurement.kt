package com.h0tk3y.rally.android.racecervice

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import com.h0tk3y.rally.android.permissions.hasFineLocation
import com.h0tk3y.rally.android.permissions.hasGpsHardware
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

enum class GpsStartResult {
    STARTED, NO_PERMISSION, NO_GPS_HARDWARE
}

class GpsDistanceMeasurement(
    private val context: Context,
    private val serviceScope: CoroutineScope
) {
    private val locationManager: LocationManager = getSystemService(context, LocationManager::class.java)
        ?: error("LocationManager is null")

    private val executor: Executor = ContextCompat.getMainExecutor(context)

    private val _isProviderEnabled = MutableStateFlow(true)

    /** Whether the user has the location turned on; with it off, the GPS gives no fixes */
    val isProviderEnabled: StateFlow<Boolean> = _isProviderEnabled.asStateFlow()

    private val locationListener = object : LocationListenerCompat {
        override fun onLocationChanged(location: Location) {
            serviceScope.launch {
                distanceAccumulator.ingest(location)
            }
        }

        override fun onProviderEnabled(provider: String) {
            _isProviderEnabled.value = true
        }

        override fun onProviderDisabled(provider: String) {
            _isProviderEnabled.value = false
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {}
    }

    private val gnssCallback = object : GnssStatusCompat.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatusCompat) {
            distanceAccumulator.updateGnssQuality(status)
        }
    }

    val distanceAccumulator = DistanceAccumulator()

    fun requestPlatformUpdates(): GpsStartResult {
        if (!context.hasGpsHardware())
            return GpsStartResult.NO_GPS_HARDWARE

        // Approximate location is not enough for the GPS provider, and it is not precise enough to measure the distance anyway
        if (!context.hasFineLocation())
            return GpsStartResult.NO_PERMISSION

        _isProviderEnabled.value = LocationManagerCompat.isLocationEnabled(locationManager) &&
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        val req = LocationRequestCompat.Builder(500)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .build()

        try {
            LocationManagerCompat.requestLocationUpdates(
                locationManager, LocationManager.GPS_PROVIDER, req, executor, locationListener
            )
            LocationManagerCompat.registerGnssStatusCallback(locationManager, executor, gnssCallback)
        } catch (e: SecurityException) {
            // The permission can be revoked between the check and the request
            Log.w("raceService", "no permission for GPS updates", e)
            stopPlatformUpdates()
            return GpsStartResult.NO_PERMISSION
        } catch (e: IllegalArgumentException) {
            Log.w("raceService", "GPS provider is unavailable", e)
            stopPlatformUpdates()
            return GpsStartResult.NO_GPS_HARDWARE
        }
        return GpsStartResult.STARTED
    }

    fun stopPlatformUpdates() {
        // The compat request wraps the listener on older Android versions, so it must be removed through the compat API, too
        LocationManagerCompat.removeUpdates(locationManager, locationListener)
        LocationManagerCompat.unregisterGnssStatusCallback(locationManager, gnssCallback)
    }
}
