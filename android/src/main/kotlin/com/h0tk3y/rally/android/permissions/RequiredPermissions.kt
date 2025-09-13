package com.h0tk3y.rally.android.permissions

import android.Manifest
import android.content.Context
import android.os.Build


object RequiredPermissions {

    fun permissionsForRaceService(context: Context) = run {
        val targetSdkVersion: Int = context.applicationInfo.targetSdkVersion

        val bluetoothPermissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetSdkVersion >= Build.VERSION_CODES.S -> {
                listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetSdkVersion >= Build.VERSION_CODES.Q -> {
                listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            else -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        val gpsPermissions =
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)

        val notificationPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        else emptyList()

        (notificationPermissions + bluetoothPermissions + gpsPermissions).toTypedArray ()
    }
}