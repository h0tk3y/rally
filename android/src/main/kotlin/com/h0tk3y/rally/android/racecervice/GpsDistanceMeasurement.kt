package com.h0tk3y.rally.android.racecervice

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class GpsDistanceMeasurement(
    private val context: Context,
    private val serviceScope: CoroutineScope
) {
    private val locationManager: LocationManager = getSystemService(context, LocationManager::class.java)
        ?: error("LocationManager is null")
    
    private val executor: Executor = ContextCompat.getMainExecutor(context)

    private val locationListener = object : LocationListenerCompat {
        override fun onLocationChanged(location: Location) {
            serviceScope.launch {
                distanceAccumulator.ingest(location)
            }
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {}
    }

    private val gnssCallback = object : GnssStatusCompat.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatusCompat) {
            distanceAccumulator.updateGnssQuality(status)
        }
    }

    val distanceAccumulator = DistanceAccumulator()

    fun requestPlatformUpdates(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(context, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED)
            return false

        val req = LocationRequestCompat.Builder(500)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .build()

        LocationManagerCompat.requestLocationUpdates(
            locationManager, LocationManager.GPS_PROVIDER, req, executor, locationListener
        )

        LocationManagerCompat.registerGnssStatusCallback(locationManager, executor, gnssCallback)
        return true
    }

    fun stopPlatformUpdates() {
        locationManager.removeUpdates(locationListener)
        LocationManagerCompat.unregisterGnssStatusCallback(locationManager, gnssCallback)
    }
}