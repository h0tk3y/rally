package com.h0tk3y.rally.android

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.h0tk3y.rally.android.racecervice.RaceService

class RaceServiceConnection(
    private val context: Context,
    val serviceConsumer: (RaceService) -> Unit,
    val onDisconnected: () -> Unit
) : ServiceConnection {

    private var isConnected = false

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        isConnected = true
        serviceConsumer((service as RaceService.LocalBinder).service)
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
