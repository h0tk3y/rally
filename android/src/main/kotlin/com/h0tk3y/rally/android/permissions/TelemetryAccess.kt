package com.h0tk3y.rally.android.permissions

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.h0tk3y.rally.R
import com.h0tk3y.rally.android.TelemetrySource
import com.h0tk3y.rally.android.racecervice.TelemetryPublicState

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
 * ([ActivityCompat.shouldShowRequestPermissionRationale]), detects a permanent denial from the request results (see
 * [TelemetryAccessPolicy]) and then offers to open the app settings.
 *
 * The state of the flow survives configuration changes and process death, so that a permission granted after a rotation still
 * completes the request. Show the dialogs with [TelemetryAccessDialogHost].
 */
@Stable
class TelemetryAccessController internal constructor(
    private val checker: PermissionChecker,
    private val history: PermissionHistory,
    private val state: MutableState<AccessState>,
    private val openActivity: (Intent) -> Unit,
    private val packageName: String,
    private val onReady: (AccessPurpose) -> Unit,
    private val onAccessMaybeGranted: () -> Unit,
) {
    internal lateinit var launcher: ActivityResultLauncher<Array<String>>

    /** The issue explained in the dialog, if it is shown */
    val shownIssue: TelemetryAccessIssue? get() = state.value.shownIssue

    /**
     * Completes the [purpose] (see the `onReady` of [rememberTelemetryAccessController]) if the [source] has the permissions it needs.
     * Otherwise, asks the user for them, either with the system dialog or, if needed, with an explanation first, and completes the
     * [purpose] once they are granted.
     *
     * The optional permissions (notifications) are asked for along the way but never block the [purpose].
     */
    fun runWhenReady(source: TelemetrySource, purpose: AccessPurpose) {
        val request = AccessRequest(source, purpose)
        when (val issue = TelemetryAccessPolicy.currentIssue(source, checker, history)) {
            null -> {
                requestOptionalOnce(source)
                onReady(purpose)
            }

            // Does not prevent the race mode; the race panel shows it and explains it on a tap
            TelemetryAccessIssue.NoGpsHardware -> onReady(purpose)

            is TelemetryAccessIssue.PermissionDenied ->
                if (TelemetryAccessPolicy.shouldExplainFirst(source, checker)) show(issue, request) else launch(request)

            else -> show(issue, request)
        }
    }

    /** Explains the problem with the [source] that the race panel reports, e.g. on a tap on its status */
    fun explain(source: TelemetrySource, telemetryState: TelemetryPublicState) {
        val issue = when (telemetryState) {
            TelemetryPublicState.GpsLocationDisabled -> TelemetryAccessIssue.LocationDisabled
            else -> TelemetryAccessPolicy.currentIssue(source, checker, history)
        }
        if (issue == null) {
            // Granted in the meantime, e.g. in the system settings
            onAccessMaybeGranted()
        } else {
            show(issue, AccessRequest(source, AccessPurpose.NONE))
        }
    }

    fun dismiss() {
        state.value = state.value.copy(shownIssue = null, pending = null)
    }

    internal fun confirm() {
        val current = state.value
        val issue = current.shownIssue ?: return
        state.value = current.copy(shownIssue = null)
        when (issue) {
            is TelemetryAccessIssue.PermissionDenied,
            TelemetryAccessIssue.ApproximateLocationOnly -> current.pending?.let(::launch)

            // Keep the pending request: it completes when the user comes back with the permission granted
            is TelemetryAccessIssue.PermissionPermanentlyDenied -> openActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            )

            TelemetryAccessIssue.LocationDisabled -> {
                dismiss()
                openActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }

            TelemetryAccessIssue.NoGpsHardware -> dismiss()
        }
    }

    internal fun onResult(result: Map<String, Boolean>) {
        val current = state.value
        val request = current.inFlight ?: return
        state.value = current.copy(inFlight = null)

        TelemetryAccessPolicy.recordResult(request.source, current.coarseGrantedBeforeRequest, checker, history)
        if (result.isEmpty()) {
            return // The request was interrupted rather than answered
        }
        when (val issue = TelemetryAccessPolicy.currentIssue(request.source, checker, history)) {
            null, TelemetryAccessIssue.NoGpsHardware -> {
                onAccessMaybeGranted()
                onReady(request.purpose)
            }

            else -> show(issue, request)
        }
    }

    internal fun onResume() {
        TelemetryAccessPolicy.forgetGranted(checker, history)
        val current = state.value
        val request = current.pending
        if (request != null && current.shownIssue == null && TelemetryAccessPolicy.currentIssue(request.source, checker, history) == null) {
            // Back from the app settings with the permissions granted
            state.value = current.copy(pending = null)
            onReady(request.purpose)
        }
        onAccessMaybeGranted()
    }

    private fun show(issue: TelemetryAccessIssue, request: AccessRequest) {
        state.value = state.value.copy(shownIssue = issue, pending = request)
    }

    private fun launch(request: AccessRequest) {
        state.value = state.value.copy(
            pending = null,
            inFlight = request,
            coarseGrantedBeforeRequest = checker.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        )
        history.notificationsAsked = true
        launcher.launch(racePermissions(request.source).all.filterNot(checker::isGranted).toTypedArray())
    }

    private fun requestOptionalOnce(source: TelemetrySource) {
        val missing = racePermissions(source).optional.filterNot(checker::isGranted)
        if (missing.isNotEmpty() && !history.notificationsAsked) {
            history.notificationsAsked = true
            launcher.launch(missing.toTypedArray())
        }
    }
}

private class ActivityPermissionChecker(private val context: Context, private val activity: Activity?) : PermissionChecker {
    override fun isGranted(permission: String) = context.isGranted(permission)
    override fun shouldShowRationale(permission: String) =
        activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)

    override fun hasGpsHardware() = context.hasGpsHardware()
}

/** Keeps the [PermissionHistory] for the whole activity, so that what one screen has learned is known to the others */
class PermissionHistoryViewModel : ViewModel() {
    val history = PermissionHistory()
}

/**
 * @param onReady completes a purpose passed to [TelemetryAccessController.runWhenReady] once the permissions are there
 * @param onAccessMaybeGranted called when a permission may have been granted: after a request or when the screen is resumed,
 *   e.g. after returning from the system settings. Use it to retry a telemetry source that was blocked.
 */
@Composable
fun rememberTelemetryAccessController(
    onReady: (AccessPurpose) -> Unit = { },
    onAccessMaybeGranted: () -> Unit = { }
): TelemetryAccessController {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val history = if (activity is ComponentActivity) {
        viewModel<PermissionHistoryViewModel>(viewModelStoreOwner = activity).history
    } else {
        remember { PermissionHistory() }
    }
    val state = rememberSaveable(stateSaver = AccessStateSaver) { mutableStateOf(AccessState()) }
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnAccessMaybeGranted by rememberUpdatedState(onAccessMaybeGranted)

    val controller = remember(context, activity, history, state) {
        TelemetryAccessController(
            ActivityPermissionChecker(context, activity),
            history,
            state,
            openActivity = { intent ->
                try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: ActivityNotFoundException) {
                    Log.w("permissions", "cannot open $intent", e)
                }
            },
            context.packageName,
            onReady = { currentOnReady(it) },
            onAccessMaybeGranted = { currentOnAccessMaybeGranted() }
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions(), controller::onResult)
    SideEffect { controller.launcher = launcher }

    LifecycleResumeEffect(controller) {
        controller.onResume()
        onPauseOrDispose { }
    }
    return controller
}

private val AccessStateSaver: Saver<AccessState, Any> = listSaver(
    save = { state ->
        listOf(
            state.shownIssue?.let(::encodeIssue),
            state.pending?.let { "${it.source.name}:${it.purpose.name}" },
            state.inFlight?.let { "${it.source.name}:${it.purpose.name}" },
            state.coarseGrantedBeforeRequest
        )
    },
    restore = { saved ->
        fun request(value: Any?) = (value as String?)?.split(":")?.let { (source, purpose) ->
            AccessRequest(TelemetrySource.valueOf(source), AccessPurpose.valueOf(purpose))
        }
        AccessState(
            shownIssue = (saved[0] as String?)?.let(::decodeIssue),
            pending = request(saved[1]),
            inFlight = request(saved[2]),
            coarseGrantedBeforeRequest = saved[3] as Boolean
        )
    }
)

private fun encodeIssue(issue: TelemetryAccessIssue): String = when (issue) {
    is TelemetryAccessIssue.PermissionDenied -> "denied:${issue.source.name}"
    is TelemetryAccessIssue.PermissionPermanentlyDenied -> "permanent:${issue.source.name}"
    TelemetryAccessIssue.ApproximateLocationOnly -> "approximate"
    TelemetryAccessIssue.LocationDisabled -> "locationDisabled"
    TelemetryAccessIssue.NoGpsHardware -> "noGps"
}

private fun decodeIssue(value: String): TelemetryAccessIssue? {
    val kind = value.substringBefore(":")
    val source = value.substringAfter(":", "").takeIf { it.isNotEmpty() }?.let(TelemetrySource::valueOf)
    return when (kind) {
        "denied" -> source?.let(TelemetryAccessIssue::PermissionDenied)
        "permanent" -> source?.let(TelemetryAccessIssue::PermissionPermanentlyDenied)
        "approximate" -> TelemetryAccessIssue.ApproximateLocationOnly
        "locationDisabled" -> TelemetryAccessIssue.LocationDisabled
        "noGps" -> TelemetryAccessIssue.NoGpsHardware
        else -> null
    }
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
