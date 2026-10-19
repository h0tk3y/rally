package com.h0tk3y.rally.android.permissions

import android.Manifest
import androidx.compose.runtime.Immutable
import com.h0tk3y.rally.android.TelemetrySource

/** What prevents a telemetry source from working, as explained to the user in the access dialog */
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

/** What to do once the permissions of a source are granted. An enum rather than a callback, so that it survives in the saved state. */
enum class AccessPurpose {
    NONE, ENTER_RACE_MODE, PICK_BT_DEVICE
}

@Immutable
data class AccessRequest(val source: TelemetrySource, val purpose: AccessPurpose)

/**
 * The state of the permission flow of one screen.
 *
 * @property pending a request waiting for the user: in the explanation dialog, or in the app settings
 * @property inFlight a request that the system permission dialog is showing
 */
@Immutable
data class AccessState(
    val shownIssue: TelemetryAccessIssue? = null,
    val pending: AccessRequest? = null,
    val inFlight: AccessRequest? = null,
    val coarseGrantedBeforeRequest: Boolean = false,
)

interface PermissionChecker {
    fun isGranted(permission: String): Boolean
    fun shouldShowRationale(permission: String): Boolean
    fun hasGpsHardware(): Boolean
}

/**
 * What the app has learned about the permissions from the request results, shared by the screens.
 *
 * Android tells whether it would still show its permission dialog only indirectly: it suggests a rationale after a denial,
 * and stops suggesting it once it won't ask anymore. With no rationale, a denial is permanent only if there was a denial before:
 * a dialog dismissed with Back does not count as one.
 */
class PermissionHistory {
    internal val permanentlyDenied = mutableSetOf<String>()
    internal val deniedBefore = mutableSetOf<String>()
    var notificationsAsked = false
}

internal object TelemetryAccessPolicy {
    private const val FINE = Manifest.permission.ACCESS_FINE_LOCATION
    private const val COARSE = Manifest.permission.ACCESS_COARSE_LOCATION

    fun currentIssue(source: TelemetrySource, checker: PermissionChecker, history: PermissionHistory): TelemetryAccessIssue? {
        if (source == TelemetrySource.GPS && !checker.hasGpsHardware()) {
            return TelemetryAccessIssue.NoGpsHardware
        }
        val missing = racePermissions(source).required.filterNot(checker::isGranted)
        return when {
            missing.isEmpty() -> null
            missing.any { it in history.permanentlyDenied } -> TelemetryAccessIssue.PermissionPermanentlyDenied(source)
            source == TelemetrySource.GPS && checker.isGranted(COARSE) -> TelemetryAccessIssue.ApproximateLocationOnly
            else -> TelemetryAccessIssue.PermissionDenied(source)
        }
    }

    /** Whether to explain the permissions before showing the system dialog, as Android suggests */
    fun shouldExplainFirst(source: TelemetrySource, checker: PermissionChecker): Boolean =
        racePermissions(source).required.any(checker::shouldShowRationale)

    /** Updates the [history] right after a permission request, which is the only moment a permanent denial can be detected */
    fun recordResult(source: TelemetrySource, coarseGrantedBeforeRequest: Boolean, checker: PermissionChecker, history: PermissionHistory) {
        // Android suggests no rationale for the precise location right after the user picks the approximate one,
        // but it still shows the dialog to upgrade to the precise location
        val justPickedApproximate = !coarseGrantedBeforeRequest && checker.isGranted(COARSE)

        for (permission in racePermissions(source).required) {
            when {
                checker.isGranted(permission) -> {
                    history.permanentlyDenied -= permission
                    history.deniedBefore -= permission
                }

                checker.shouldShowRationale(permission) -> history.deniedBefore += permission
                permission == FINE && justPickedApproximate -> Unit
                permission in history.deniedBefore -> history.permanentlyDenied += permission

                // Dismissed with Back, or the system did not show the dialog at all; the next result tells these apart
                else -> history.deniedBefore += permission
            }
        }
    }

    /** Forgets the denials of the permissions that the user has granted in the meantime, e.g. in the app settings */
    fun forgetGranted(checker: PermissionChecker, history: PermissionHistory) {
        history.permanentlyDenied.removeAll(checker::isGranted)
        history.deniedBefore.removeAll(checker::isGranted)
    }
}
