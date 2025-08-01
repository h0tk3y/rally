package com.h0tk3y.rally.android

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.h0tk3y.rally.android.racecervice.CommonRaceService
import com.h0tk3y.rally.android.racecervice.LocalRaceService
import com.h0tk3y.rally.android.racecervice.TcpStreamedRaceService

class RaceServiceConnection<S : CommonRaceService>(
    private val context: Context,
    val serviceConsumer: (S) -> Unit,
    val onDisconnected: () -> Unit,
    val binderMapping: (IBinder?) -> S
) : ServiceConnection {

    private var isConnected = false

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        isConnected = true
        serviceConsumer(binderMapping(service))
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        onDisconnected()
        isConnected = false
    }

    fun disconnectIfConnected() {
        if (isConnected) {
            context.unbindService(this)
        }
        isConnected = false
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