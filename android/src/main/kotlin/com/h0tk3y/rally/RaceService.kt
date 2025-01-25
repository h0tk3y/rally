package com.h0tk3y.rally

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.pires.obd.commands.SpeedCommand
import com.h0tk3y.rally.model.RaceModel
import com.h0tk3y.rally.model.RaceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.IOException
import java.util.UUID


class RaceService : Service() {

    private val _raceState = MutableStateFlow<RaceState>(RaceState.NotStarted)
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var notification: Notification

    val raceState: StateFlow<RaceState> get() = _raceState
    private var debugSpeed: SpeedKmh? = null
    private var debugSpeedJob: Job? = null

    var calibration = 1.0

    fun setDebugSpeed(speedKmh: SpeedKmh) {
        debugSpeed = if (speedKmh.valueKmh != 0.0) {
            speedKmh
        } else null

        if (debugSpeed == null) {
            debugSpeedJob?.cancel()
            debugSpeedJob = null
        } else {
            if (debugSpeedJob == null) {
                debugSpeedJob = CoroutineScope(Dispatchers.Default).launch {
                    var time = Clock.System.now()
                    while (true) {
                        val newTime = Clock.System.now()
                        ensureActive()
                        _raceState.value = when (val current = raceState.value) {
                            is RaceState.InRace -> {
                                val timeElapsedHr = TimeHr((newTime - time).inWholeMilliseconds / 1000.0 / 3600.0)
                                val newDistance = current.raceModel.currentDistance + DistanceKm.byMoving(debugSpeed ?: SpeedKmh(0.0), timeElapsedHr)
                                current.copy(raceModel = current.raceModel.copy(currentDistance = newDistance))
                            }

                            else -> current
                        }
                        delay(100)
                        time = newTime
                    }
                }
            }
        }
    }

    fun startRace(raceSectionId: Long, startAtDistanceKm: DistanceKm, startAtTime: Instant) {
        _raceState.value = RaceState.InRace(raceSectionId, RaceModel(startAtTime, startAtDistanceKm, startAtDistanceKm, DistanceKm.zero, true), 0L)
    }

    fun stopRace() {
        _raceState.value = when (val current = _raceState.value) {
            is RaceState.InRace -> RaceState.Stopped(current.raceModel, Clock.System.now())
            is RaceState.Stopped -> current
            is RaceState.NotStarted -> RaceState.NotStarted
        }
    }

    fun setCurrentDistance(distanceKm: DistanceKm, asCorrectionOf: DistanceKm = DistanceKm.zero): Boolean {
        when (val currentState = _raceState.value) {
            is RaceState.InRace -> {
                _raceState.value = currentState.copy(
                    raceModel = currentState.raceModel.copy(
                        currentDistance = distanceKm,
                        distanceCorrection = currentState.raceModel.distanceCorrection + asCorrectionOf
                    )
                )
                return true
            }

            is RaceState.Stopped,
            RaceState.NotStarted -> return false
        }
    }

    override fun onCreate() {
        super.onCreate()

        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()

        notification = NotificationCompat.Builder(this, TIMER_SERVICE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentTitle("Race service")
            .setContentText("Race service running")
            .setOngoing(true) // an ongoing notification means can't dismiss by the user.
            .setOnlyAlertOnce(true)
            .build()

        startForeground(RACE_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                TIMER_SERVICE_NOTIFICATION_CHANNEL_ID,
                "Race Service",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    override fun stopService(name: Intent?): Boolean {
        raceJob?.cancel()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        raceJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent): IBinder {
        return LocalBinder()
    }

    private var raceJob: Job? = null
    private var btState = MutableStateFlow<BtConnectionState>(BtConnectionState.NotInitialized)

    private sealed interface BtConnectionState {
        sealed interface HasDevice : BtConnectionState {
            val device: BluetoothDevice
        }

        sealed interface HasSocket : BtConnectionState {
            val socket: BluetoothSocket
        }

        sealed interface CancelableState : BtConnectionState

        data object NotInitialized : BtConnectionState, CancelableState
        data object NoTargetMacAddress : BtConnectionState, CancelableState
        data object NoPermissions : BtConnectionState, CancelableState
        data class Connecting(override val device: BluetoothDevice) : HasDevice, CancelableState
        data class ConnectingWithSocket(override val device: BluetoothDevice, override val socket: BluetoothSocket) : HasDevice, HasSocket
        data class LostConnection(override val device: BluetoothDevice) : HasDevice
        data class Disconnecting(override val device: BluetoothDevice, override val socket: BluetoothSocket) : HasDevice, HasSocket
        data class Connected(val device: BluetoothDevice, override val socket: BluetoothSocket) : BtConnectionState, HasSocket, CancelableState
    }

    private fun raceJob(device: BluetoothDevice): Job {
        btState.value = BtConnectionState.Connecting(device)

        var lastTime = -1L

        return CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                if (btState.value is BtConnectionState.CancelableState) {
                    ensureActive()
                }

                when (val currentBtState = btState.value) {
                    is BtConnectionState.Connected -> {
                        try {
                            val input = currentBtState.socket.inputStream
                            val output = currentBtState.socket.outputStream
                            if (input == null || output == null) {
                                btState.value = BtConnectionState.LostConnection(device)
                            }
                            val speedCommand = SpeedCommand()
                            speedCommand.run(currentBtState.socket.inputStream, currentBtState.socket.outputStream)
                            val speed = speedCommand.metricSpeed
                            val time = System.currentTimeMillis()
                            if (lastTime != -1L) {
                                _raceState.value = when (val raceState = _raceState.value) {
                                    is RaceState.NotStarted,
                                    is RaceState.Stopped -> raceState
                                    
                                    is RaceState.InRace -> {
                                        val dst = raceState.raceModel.currentDistance
                                        val newDst = dst + DistanceKm.byMoving(
                                            SpeedKmh(speed.toDouble() / calibration),
                                            TimeHr((time - lastTime) / 1000.0 / 3600.0)
                                        )
                                        Log.i("raceData", "speed: $speed, distance: ${dst.valueKm.strRound3()}")
                                        raceState.copy(raceModel = raceState.raceModel.copy(currentDistance = newDst))
                                    }

                                }
                            }
                            lastTime = time
                        } catch (e: IOException) {
                            Log.d("raceService", "can't execute command", e)
                            btState.value = BtConnectionState.LostConnection(device)
                        }
                    }

                    is BtConnectionState.Connecting -> {
                        Log.i("raceService", "connecting to $device")
                        try {
                            val socket = run {
                                if (ActivityCompat.checkSelfPermission(
                                        this@RaceService,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    btState.value = BtConnectionState.NoPermissions
                                }
                                device.createRfcommSocketToServiceRecord(sppUuid)
                            }
                            btState.value = BtConnectionState.ConnectingWithSocket(device, socket)
                            try {
                                socket.connect()
                            } catch (e: IOException) {
                                btState.value = BtConnectionState.Disconnecting(device, socket)
                            }
                            btState.value = BtConnectionState.Connected(device, socket)
                        } catch (e: IOException) {
                            btState.value = BtConnectionState.LostConnection(device)
                        }
                    }

                    is BtConnectionState.Disconnecting -> {
                        try {
                            currentBtState.socket.close()
                        } finally {
                            btState.value = BtConnectionState.LostConnection(device)
                        }
                    }

                    is BtConnectionState.LostConnection -> {
                        Log.i("raceService", "lost bluetooth connection to $device")
                        btState.value = BtConnectionState.Connecting(device)
                    }

                    BtConnectionState.NoPermissions -> break
                    BtConnectionState.NoTargetMacAddress -> break

                    is BtConnectionState.ConnectingWithSocket,
                    BtConnectionState.NotInitialized -> error("unexpected state")
                }
            }
        }.also {
            it.invokeOnCompletion {
                val currentBtState = btState.value
                if (currentBtState is BtConnectionState.HasSocket) {
                    try {
                        currentBtState.socket.close()
                    } catch (_: IOException) {
                    }
                }
                btState.value = BtConnectionState.NotInitialized
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val btAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val device = btAdapter.getRemoteDevice("6A:0D:E4:F4:C7:41")

        synchronized(this) {
            if (raceJob == null) {
                val launchedJob = raceJob(device)
                raceJob = launchedJob
                launchedJob.invokeOnCompletion {
                    raceJob = null
                }
            }
        }

        return START_STICKY
    }


    inner class LocalBinder : Binder() {
        val service: RaceService = this@RaceService
    }

    companion object {
        private const val TIMER_SERVICE_NOTIFICATION_CHANNEL_ID = "RaceService"
        private const val RACE_NOTIFICATION_ID = 1
        private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}