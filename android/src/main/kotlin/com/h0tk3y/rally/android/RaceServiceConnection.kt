package com.h0tk3y.rally.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.h0tk3y.rally.android.racecervice.CommonRaceService
import com.h0tk3y.rally.android.racecervice.LocalRaceService
import com.h0tk3y.rally.android.racecervice.TcpStreamedRaceService

class RaceServiceConnection<S : CommonRaceService>(
    private val context: Context,
    val serviceConsumer: (S) -> Unit,
    val onDisconnected: () -> Unit,
    val binderMapping: (IBinder?) -> S
) : ServiceConnection {

    var isBound = false
        private set

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        isBound = true
        serviceConsumer(binderMapping(service))
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        onDisconnected()
        isBound = false
    }

    fun markBound() {
        isBound = true
    }

    fun disconnectIfConnected() {
        if (isBound) {
            try {
                context.unbindService(this)
            } catch (e: IllegalArgumentException) {
                // Ignore if not registered or already unbound
            }
            isBound = false
        }
    }
}

fun <S : CommonRaceService> probeExistingService(
    context: Context,
    serviceClass: Class<S>,
    connection: RaceServiceConnection<S>
): Boolean {
    val intent = Intent(context, serviceClass)
    val bound = context.bindService(intent, connection, 0)
    if (bound) {
        connection.markBound()
    }
    return bound
}

fun <S : CommonRaceService> connectToService(
    context: Context,
    serviceClass: Class<S>,
    connection: RaceServiceConnection<S>
) {
    val intent = Intent(context, serviceClass)
    if (context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
        connection.markBound()
    }
    try {
        ContextCompat.startForegroundService(context, intent)
    } catch (e: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException (Android 12+): the app went to the background before the service started.
        // The service can't run in the foreground then, so don't leave it running half-started; the user can enter the race mode again.
        Log.w("raceService", "not allowed to start the race service now", e)
        connection.disconnectIfConnected()
    }
}

fun localRaceServiceConnection(
    context: Context,
    serviceConsumer: (LocalRaceService) -> Unit,
    onDisconnected: () -> Unit
) = RaceServiceConnection(
    context,
    serviceConsumer,
    onDisconnected,
    { (it as LocalRaceService.LocalBinder).service }
)

fun tcpStreamedRaceServiceConnection(
    context: Context,
    serviceConsumer: (TcpStreamedRaceService) -> Unit,
    onDisconnected: () -> Unit
) = RaceServiceConnection(
    context,
    serviceConsumer,
    onDisconnected,
    { (it as TcpStreamedRaceService.LocalBinder).service }
)