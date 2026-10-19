package com.h0tk3y.rally.android.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.content.getSystemService
import com.h0tk3y.rally.R

fun Context.isIgnoringBatteryOptimizations(): Boolean =
    getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(packageName) ?: true

/**
 * Opens the system list where the user can exempt the app from the battery optimization.
 * Unlike asking for the exemption directly, this needs no special permission, which Google Play restricts.
 */
fun Context.openBatteryOptimizationSettings() {
    val intents = listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    )
    for (intent in intents) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // Some vendors remove the screen; try the next one
        }
    }
}

/** Device makers whose own battery savers may stop the app even when it is exempt, and which dontkillmyapp.com describes */
private val dontKillMyAppVendors = setOf(
    "samsung", "xiaomi", "huawei", "honor", "oneplus", "oppo", "realme", "vivo", "meizu", "asus", "lenovo", "motorola",
    "nokia", "sony", "wiko", "tecno", "infinix", "blackview", "unihertz", "google"
)

fun dontKillMyAppUrl(manufacturer: String = Build.MANUFACTURER): String {
    val vendor = manufacturer.lowercase()
    return if (vendor in dontKillMyAppVendors) "https://dontkillmyapp.com/$vendor" else "https://dontkillmyapp.com/"
}

fun Context.openDontKillMyApp() {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dontKillMyAppUrl())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser
    }
}

@Composable
fun BatteryOptimizationHintDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.batteryHintTitle)) },
        text = { Text(stringResource(R.string.batteryHintText)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.batteryButtonOpenSettings)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.permissionButtonNotNow)) }
        }
    )
}
