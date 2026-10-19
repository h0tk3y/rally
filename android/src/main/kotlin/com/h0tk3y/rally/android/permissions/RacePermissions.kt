package com.h0tk3y.rally.android.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import com.h0tk3y.rally.android.TelemetrySource

/**
 * The runtime permissions that a telemetry source needs.
 *
 * Without the [required] ones the source cannot work, so the race mode does not start.
 * The [optional] ones only improve the experience (e.g. the race state notification), and a denial is fine.
 */
data class RacePermissionSet(val required: List<String>, val optional: List<String>) {
    val all: List<String> get() = required + optional
}

fun racePermissions(source: TelemetrySource, sdkInt: Int = Build.VERSION.SDK_INT): RacePermissionSet {
    val required = when (source) {
        // Since Android 12, fine location must be requested together with coarse location, or the request is ignored.
        // The user can still grant just the approximate location, which is not enough for measuring the distance.
        TelemetrySource.GPS -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        TelemetrySource.BT_OBD -> bluetoothConnectPermissions(sdkInt)
        TelemetrySource.SIMULATION, TelemetrySource.OUTGAUGE -> emptyList()
    }
    return RacePermissionSet(required, notificationPermissions(sdkInt))
}

/** Connecting to an already paired device needs no runtime permission before Android 12; scanning is done by the system picker. */
fun bluetoothConnectPermissions(sdkInt: Int = Build.VERSION.SDK_INT): List<String> =
    if (sdkInt >= Build.VERSION_CODES.S) listOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyList()

fun notificationPermissions(sdkInt: Int = Build.VERSION.SDK_INT): List<String> =
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()

/**
 * The foreground service type of the local race service for the current telemetry source.
 *
 * Only a service with the `location` type may receive location updates while the app is not visible. Starting a service with
 * that type requires a location permission (on Android 14+), so the type is only used when the GPS source can actually work.
 * Everything else uses `connectedDevice`, which only needs the auto-granted `CHANGE_NETWORK_STATE` permission and thus
 * can't fail if the user revokes a permission.
 */
fun raceServiceType(source: TelemetrySource, fineLocationGranted: Boolean): Int =
    if (source == TelemetrySource.GPS && fineLocationGranted)
        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    else
        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE

fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.hasFineLocation(): Boolean = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

fun Context.hasGpsHardware(): Boolean = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)

/** Without GPS hardware (e.g. Wi-Fi-only tablets), defaulting to the GPS source would leave the user with a source that can't work. */
fun Context.defaultTelemetrySource(): TelemetrySource =
    if (hasGpsHardware()) TelemetrySource.GPS else TelemetrySource.BT_OBD
