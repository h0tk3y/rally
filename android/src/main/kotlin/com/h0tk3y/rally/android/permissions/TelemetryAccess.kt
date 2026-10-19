package com.h0tk3y.rally.android.permissions

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.h0tk3y.rally.R
import com.h0tk3y.rally.android.TelemetrySource
import com.h0tk3y.rally.android.racecervice.TelemetryPublicState

/** What prevents a telemetry source from working, as explained to the user in [TelemetryAccessDialog] */
sealed interface TelemetryAccessIssue {
    val source: TelemetrySource

    /** The permissions can be requested with the system dialog */
    data class PermissionDenied(override val source: TelemetrySource) : TelemetryAccessIssue

    /** The system won't show the permission dialog anymore; the user can only grant the permissions in the app settings */
    data class PermissionPermanentlyDenied(override val source: TelemetrySource) : TelemetryAccessIssue

    /** The user only allowed the approximate location, which is not enough to measure the distance */
    data object ApproximateLocationOnly : TelemetryAccessIssue {
        override val source get() = TelemetrySource.GPS
    }

    data object LocationDisabled : TelemetryAccessIssue {
        override val source get() = TelemetrySource.GPS
    }

    data object NoGpsHardware : TelemetryAccessIssue {
        override val source get() = TelemetrySource.GPS
    }
}

/** The telemetry states that the user can resolve by tapping the status in the race panel */
fun TelemetryPublicState.accessProblemSource(): TelemetrySource? = when (this) {
    TelemetryPublicState.GpsNoPermissions,
    TelemetryPublicState.GpsNoHardware,
    TelemetryPublicState.GpsLocationDisabled -> TelemetrySource.GPS

    TelemetryPublicState.BtNoPermissions -> TelemetrySource.BT_OBD
    else -> null
}

/**
 * Requests the permissions of the telemetry sources and explains to the user why they are needed.
 *
 * Built on the official permission request contract: it shows an explanation first when Android suggests one
 * ([ActivityCompat.shouldShowRequestPermissionRationale]) and detects a permanent denial right after a request, which is the only
 * moment when this can be told apart from the first request. With a permanent denial, it offers to open the app settings.
 *
 * Show the dialogs with [TelemetryAccessDialogHost].
 */
@Stable
class TelemetryAccessController internal constructor(
    private val context: Context,
    private val activity: Activity?,
) {
    internal lateinit var launcher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>
    internal var onAccessMaybeGranted: () -> Unit = { }

    /** The issue explained in the dialog, if it is shown */
    var shownIssue: TelemetryAccessIssue? by mutableStateOf(null)
        private set

    private var pendingRequest: PendingRequest? = null
    private var inFlightRequest: PendingRequest? = null

    private class PendingRequest(
        val source: TelemetrySource,
        val permissions: RacePermissionSet,
        val onReady: (() -> Unit)?
    )

    /**
     * Runs [onReady] if the [source] has the permissions it needs. Otherwise, asks the user for them, either with the system dialog
     * or, if needed, with an explanation first, and runs [onReady] once they are granted.
     *
     * The optional permissions (notifications) are asked for along the way but never block [onReady].
     */
    fun runWhenReady(source: TelemetrySource, btDeviceSelected: Boolean, onReady: () -> Unit) {
        val permissions = racePermissions(source, btDeviceSelected)
        val request = PendingRequest(source, permissions, onReady)
        when (val issue = currentIssue(source, permissions.required)) {
            null -> {
                requestOptionalOnce(permissions)
                onReady()
            }

            is TelemetryAccessIssue.PermissionDenied ->
                if (permissions.required.any { shouldShowRationale(it) }) {
                    show(issue, request)
                } else {
                    // The first request, or a permanent denial, which is detected from the result
                    launch(request)
                }

            // These do not prevent the race mode; the race panel shows them and explains them on a tap
            TelemetryAccessIssue.NoGpsHardware, TelemetryAccessIssue.LocationDisabled -> onReady()

            else -> show(issue, request)
        }
    }

    /** Explains the problem with the [source] that the race panel reports, e.g. on a tap on its status */
    fun explain(source: TelemetrySource, btDeviceSelected: Boolean, telemetryState: TelemetryPublicState) {
        val permissions = racePermissions(source, btDeviceSelected)
        val issue = when (telemetryState) {
            TelemetryPublicState.GpsLocationDisabled -> TelemetryAccessIssue.LocationDisabled
            else -> currentIssue(source, permissions.required)
        }
        if (issue == null) {
            // Granted in the meantime, e.g. in the system settings
            onAccessMaybeGranted()
        } else {
            show(issue, PendingRequest(source, permissions, onReady = null))
        }
    }

    fun dismiss() {
        shownIssue = null
        pendingRequest = null
    }

    internal fun confirm() {
        val issue = shownIssue ?: return
        val request = pendingRequest
        shownIssue = null
        when (issue) {
            is TelemetryAccessIssue.PermissionDenied,
            TelemetryAccessIssue.ApproximateLocationOnly -> if (request != null) launch(request)

            // Keep the pending request: it completes when the user comes back with the permission granted
            is TelemetryAccessIssue.PermissionPermanentlyDenied -> openActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            )

            TelemetryAccessIssue.LocationDisabled -> openActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

            TelemetryAccessIssue.NoGpsHardware -> pendingRequest = null
        }
    }

    internal fun onResult(result: Map<String, Boolean>) {
        val request = inFlightRequest ?: return
        inFlightRequest = null

        // Right after a request, a denied permission with no rationale suggested means that the system won't ask for it anymore
        permanentlyDenied = permanentlyDenied + request.permissions.required.filter { !context.isGranted(it) && !shouldShowRationale(it) }

        val issue = currentIssue(request.source, request.permissions.required)
        if (issue == null || issue == TelemetryAccessIssue.LocationDisabled || issue == TelemetryAccessIssue.NoGpsHardware) {
            onAccessMaybeGranted()
            request.onReady?.invoke()
        } else if (result.isNotEmpty()) {
            // An empty result means that the request was interrupted rather than denied
            show(issue, request)
        }
    }

    internal fun onResume() {
        val request = pendingRequest
        if (request != null && shownIssue == null && currentIssue(request.source, request.permissions.required) == null) {
            // Back from the app settings with the permissions granted
            pendingRequest = null
            permanentlyDenied = permanentlyDenied.filterNot { context.isGranted(it) }.toSet()
            request.onReady?.invoke()
        }
        onAccessMaybeGranted()
    }

    private fun currentIssue(source: TelemetrySource, required: List<String>): TelemetryAccessIssue? {
        if (source == TelemetrySource.GPS && !context.hasGpsHardware()) {
            return TelemetryAccessIssue.NoGpsHardware
        }
        val missing = required.filterNot { context.isGranted(it) }
        return when {
            missing.isEmpty() -> null
            missing.any { it in permanentlyDenied } -> TelemetryAccessIssue.PermissionPermanentlyDenied(source)
            source == TelemetrySource.GPS && context.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION) ->
                TelemetryAccessIssue.ApproximateLocationOnly

            else -> TelemetryAccessIssue.PermissionDenied(source)
        }
    }

    private fun show(issue: TelemetryAccessIssue, request: PendingRequest) {
        pendingRequest = request
        shownIssue = issue
    }

    private fun launch(request: PendingRequest) {
        pendingRequest = null
        inFlightRequest = request
        notificationsAsked = true
        launcher.launch(request.permissions.all.filterNot { context.isGranted(it) }.toTypedArray())
    }

    private fun requestOptionalOnce(permissions: RacePermissionSet) {
        val missing = permissions.optional.filterNot { context.isGranted(it) }
        if (missing.isNotEmpty() && !notificationsAsked) {
            notificationsAsked = true
            launcher.launch(missing.toTypedArray())
        }
    }

    private fun shouldShowRationale(permission: String) =
        activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)

    private fun openActivity(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w("permissions", "cannot open $intent", e)
        }
    }

    private companion object {
        /** Not to bother the user about the optional notifications more than once per app launch */
        var notificationsAsked = false

        /** Shared by the screens, so that a denial detected in one of them is known in the others */
        var permanentlyDenied = setOf<String>()
    }
}

/**
 * @param onAccessMaybeGranted called when a permission may have been granted: after a request or when the screen is resumed,
 *   e.g. after returning from the system settings. Use it to retry a telemetry source that was blocked.
 */
@Composable
fun rememberTelemetryAccessController(onAccessMaybeGranted: () -> Unit = { }): TelemetryAccessController {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val controller = remember(context, activity) { TelemetryAccessController(context, activity) }
    val currentOnAccessMaybeGranted by rememberUpdatedState(onAccessMaybeGranted)
    controller.onAccessMaybeGranted = { currentOnAccessMaybeGranted() }
    controller.launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions(), controller::onResult)

    LifecycleResumeEffect(controller) {
        controller.onResume()
        onPauseOrDispose { }
    }
    return controller
}

/**
 * @param onChangeSource lets the user pick another source when the current one can't work on this device;
 *   `null` if the user is already in the settings.
 */
@Composable
fun TelemetryAccessDialogHost(controller: TelemetryAccessController, onChangeSource: (() -> Unit)?) {
    val issue = controller.shownIssue ?: return
    TelemetryAccessDialog(
        issue,
        onConfirm = controller::confirm,
        onDismiss = controller::dismiss,
        onChangeSource = onChangeSource?.let { { controller.dismiss(); it() } }
    )
}

@Composable
private fun TelemetryAccessDialog(
    issue: TelemetryAccessIssue,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onChangeSource: (() -> Unit)?
) {
    val reason = when (issue.source) {
        TelemetrySource.BT_OBD -> stringResource(R.string.permissionReasonObd)
        else -> stringResource(R.string.permissionReasonGps)
    }
    val text = when (issue) {
        is TelemetryAccessIssue.PermissionDenied -> reason
        is TelemetryAccessIssue.PermissionPermanentlyDenied -> reason + "\n\n" + stringResource(R.string.permissionPermanentlyDenied)
        TelemetryAccessIssue.ApproximateLocationOnly -> stringResource(R.string.permissionApproximateLocationOnly)
        TelemetryAccessIssue.LocationDisabled -> stringResource(R.string.permissionLocationDisabled)
        TelemetryAccessIssue.NoGpsHardware -> stringResource(R.string.permissionNoGpsHardware)
    }
    val confirmText = when (issue) {
        is TelemetryAccessIssue.PermissionDenied,
        TelemetryAccessIssue.ApproximateLocationOnly -> stringResource(R.string.permissionButtonAllow)

        is TelemetryAccessIssue.PermissionPermanentlyDenied -> stringResource(R.string.permissionButtonOpenAppSettings)
        TelemetryAccessIssue.LocationDisabled -> stringResource(R.string.permissionButtonTurnOnLocation)
        TelemetryAccessIssue.NoGpsHardware -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (issue.source) {
                    TelemetrySource.BT_OBD -> stringResource(R.string.permissionTitleObd)
                    else -> stringResource(R.string.permissionTitleGps)
                }
            )
        },
        text = { Text(text) },
        confirmButton = {
            when {
                confirmText != null -> TextButton(onClick = onConfirm) { Text(confirmText) }
                onChangeSource != null -> TextButton(onClick = onChangeSource) { Text(stringResource(R.string.permissionButtonChangeSource)) }
                else -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.permissionButtonOk)) }
            }
        },
        dismissButton = {
            if (confirmText != null || onChangeSource != null) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.permissionButtonNotNow)) }
            }
        }
    )
}
