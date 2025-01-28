package com.h0tk3y.rally

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
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
import androidx.lifecycle.LifecycleCoroutineScope
import com.github.pires.obd.commands.SpeedCommand
import com.github.pires.obd.commands.protocol.EchoOffCommand
import com.github.pires.obd.commands.protocol.LineFeedOffCommand
import com.github.pires.obd.commands.protocol.SelectProtocolCommand
import com.github.pires.obd.commands.protocol.TimeoutCommand
import com.github.pires.obd.enums.ObdProtocols
import com.github.pires.obd.exceptions.NoDataException
import com.github.pires.obd.exceptions.UnknownErrorException
import com.h0tk3y.rally.model.RaceModel
import com.h0tk3y.rally.model.RaceState
import com.h0tk3y.rally.model.interval
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.IOException
import java.util.UUID


class RaceService : Service() {

    private val _raceState = MutableStateFlow<RaceState>(RaceState.NotStarted)
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var notification: Notification

    val raceState: StateFlow<RaceState> get() = _raceState

    private sealed interface BtMacState {
        data object NotInitialized : BtMacState
        data object NotSet : BtMacState
        data class Set(val mac: String) : BtMacState
    }

    private val btMac = MutableStateFlow<BtMacState>(BtMacState.NotInitialized)
    private var debugSpeed: SpeedKmh? = null
    private var debugSpeedJob: Job? = null
    private var deltaDistanceGoingUp = MutableStateFlow(false)

    var calibration = 1.0

    fun setBtMac(mac: String?) {
        btMac.value = mac?.let(BtMacState::Set) ?: BtMacState.NotSet
    }

    fun setDistanceGoingUp(isUp: Boolean) {
        deltaDistanceGoingUp.value = isUp
        updateRaceStateByMoving({ it }, { it }, { it })
    }

    fun setDebugSpeed(speedKmh: SpeedKmh) {
        debugSpeed = if (speedKmh.valueKmh != 0.0) {
            speedKmh
        } else null

        if (debugSpeed == null) {
            updateRaceStateByMoving({ it }, { it }, { SpeedKmh(0.0) })
            debugSpeedJob?.cancel()
            debugSpeedJob = null
        } else {
            if (debugSpeedJob == null) {
                debugSpeedJob = CoroutineScope(Dispatchers.Default).launch {
                    var lastTime = Clock.System.now()
                    while (true) {
                        val newTime = Clock.System.now()
                        ensureActive()
                        val timeElapsedHr = TimeHr.interval(lastTime, newTime)
                        val speed = debugSpeed ?: SpeedKmh(0.0)
                        updateRaceStateByMoving(newDistance = {
                            it + DistanceKm.byMoving(speed, timeElapsedHr) * (if (deltaDistanceGoingUp.value) 1.0 else -1.0)
                        }, newCorrection = { it }, newInstantSpeed = { debugSpeed ?: SpeedKmh(0.0) })

                        delay(100)
                        lastTime = newTime
                    }
                }
            }
        }
    }

    fun startRace(raceSectionId: Long, startAtDistanceKm: DistanceKm, startAtTime: Instant) {
        deltaDistanceGoingUp.value = true
        _raceState.value = RaceState.InRace(
            raceSectionId,
            RaceModel(startAtTime, startAtDistanceKm, startAtDistanceKm, DistanceKm.zero, true, SpeedKmh(0.0))
        )
    }

    fun finishRace() {
        _raceState.value = when (val current = _raceState.value) {
            is RaceState.InRace -> RaceState.Finished(current.raceSectionId, current.raceModel, current.raceModel, Clock.System.now())
            is RaceState.Finished,
            is RaceState.Stopped,
            is RaceState.NotStarted -> current
        }
    }

    fun undoFinishRace() {
        _raceState.value = when (val current = _raceState.value) {
            is RaceState.Finished -> RaceState.InRace(current.raceSectionId, current.finishedRaceModel)
            is RaceState.InRace,
            is RaceState.Stopped,
            is RaceState.NotStarted -> current
        }
    }

    fun stopRace() {
        _raceState.value = when (val current = _raceState.value) {
            is RaceState.Finished -> RaceState.Stopped(current.raceSectionId, current.raceModel, Clock.System.now())
            is RaceState.InRace -> RaceState.Stopped(current.raceSectionId, current.raceModel, Clock.System.now())
            RaceState.NotStarted -> current
            is RaceState.Stopped -> current
        }
    }

    fun resetRace() {
        _raceState.value = RaceState.NotStarted
    }

    fun setCurrentDistance(distanceKm: DistanceKm): Boolean =
        updateRaceStateByMoving({ distanceKm }, { DistanceKm.zero }, { it })

    private fun updateRaceStateByMoving(
        newDistance: (DistanceKm) -> DistanceKm,
        newCorrection: (DistanceKm) -> DistanceKm,
        newInstantSpeed: (SpeedKmh) -> SpeedKmh
    ): Boolean =
        when (val currentState = _raceState.value) {
            is RaceState.InRace -> {
                _raceState.value = currentState.copy(
                    raceModel = updateRaceModel(currentState.raceModel, newDistance, newCorrection, newInstantSpeed)
                )
                true
            }

            is RaceState.Finished -> {
                _raceState.value = currentState.copy(
                    raceModel = updateRaceModel(currentState.raceModel, newDistance, newCorrection, newInstantSpeed)
                )
                true
            }

            is RaceState.Stopped,
            RaceState.NotStarted -> false
        }

    private fun updateRaceModel(
        current: RaceModel,
        newDistance: (DistanceKm) -> DistanceKm,
        newCorrection: (DistanceKm) -> DistanceKm,
        newInstantSpeed: (SpeedKmh) -> SpeedKmh
    ) = current.copy(
        currentDistance = newDistance(current.currentDistance),
        distanceCorrection = newCorrection(current.distanceCorrection),
        instantSpeed = newInstantSpeed(current.instantSpeed),
        distanceGoingUp = deltaDistanceGoingUp.value,
    )

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
    val btPublicState = btState.map { it.toPublicState() }

    sealed interface BtPublicState {
        data object NotInitialized : BtPublicState
        data object NoTargetMacAddress : BtPublicState
        data object NoPermissions : BtPublicState
        data object Connecting : BtPublicState
        data object Working : BtPublicState
        data object Reconnecting : BtPublicState
    }

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
        data class LostConnection(override val device: BluetoothDevice) : HasDevice, CancelableState
        data class Disconnecting(override val device: BluetoothDevice, override val socket: BluetoothSocket) : HasDevice, HasSocket
        data class Connected(val device: BluetoothDevice, override val socket: BluetoothSocket) : BtConnectionState, HasSocket, CancelableState

        fun toPublicState(): BtPublicState {
            return when (this) {
                NotInitialized -> BtPublicState.NotInitialized
                NoTargetMacAddress -> BtPublicState.NoTargetMacAddress
                NoPermissions -> BtPublicState.NoPermissions
                is Connected -> BtPublicState.Working
                is Connecting,
                is ConnectingWithSocket -> BtPublicState.Connecting

                is Disconnecting,
                is LostConnection -> BtPublicState.Reconnecting
            }
        }
    }

    private fun raceJob(): Job = CoroutineScope(Dispatchers.Default).launch {
        btMac.collectLatest { newBtMac ->
            if (newBtMac is BtMacState.Set) {
                startBtMainLoopJob(newBtMac.mac)
            } else {
                btState.value = BtConnectionState.NoTargetMacAddress
            }
        }
    }

    private suspend fun startBtMainLoopJob(btMac: String) {
        val btAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (BluetoothAdapter.checkBluetoothAddress(btMac)) {
            val device = btAdapter.getRemoteDevice(btMac)
            btState.value = BtConnectionState.Connecting(device)
            launchBtMainLoopAndFreeResources(device)
        } else {
            btState.value = BtConnectionState.NoTargetMacAddress
        }
    }

    private suspend fun launchBtMainLoopAndFreeResources(device: BluetoothDevice) {
        withContext(Dispatchers.IO) {
            launch {
                btMainLoop(device)
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

    private fun CoroutineScope.btMainLoop(device: BluetoothDevice) {
        var lastTime = -1L

        while (true) {
            val myState = btState.value

            fun setBtState(newBtState: BtConnectionState) {
                if (myState is BtConnectionState.CancelableState || newBtState is BtConnectionState.CancelableState) {
                    ensureActive()
                }
                btState.value = myState
            }

            when (myState) {
                is BtConnectionState.Connected -> {
                    try {
                        val input = myState.socket.inputStream
                        val output = myState.socket.outputStream
                        if (input == null || output == null) {
                            setBtState(BtConnectionState.LostConnection(device))
                        }
                        val speedCommand = SpeedCommand()
                        speedCommand.run(myState.socket.inputStream, myState.socket.outputStream)

                        val time = System.currentTimeMillis()
                        if (lastTime != -1L) {
                            val speed = speedCommand.metricSpeed
                            val speedKmh = SpeedKmh(speed.toDouble() / calibration)
                            updateRaceStateByMoving(newDistance = { dst ->
                                val newDst = dst + DistanceKm.byMoving(
                                    speedKmh,
                                    TimeHr((time - lastTime) / 1000.0 / 3600.0)
                                )
                                Log.i("raceData", "speed: $speed, distance: ${dst.valueKm.strRound3()}")
                                newDst
                            }, { it }, { speedKmh })
                        }
                        lastTime = time
                    } catch (e: IOException) {
                        Log.d("raceService", "can't execute command", e)
                        setBtState(BtConnectionState.LostConnection(device))
                    } catch (e: NoDataException) {
                        Log.d("raceService", "no data for command", e)
                    } catch (e: UnknownErrorException) {
                        Log.d("raceService", "unknown error", e)
                        setBtState(BtConnectionState.LostConnection(device))
                    }
                }

                is BtConnectionState.Connecting -> {
                    Log.i("raceService", "connecting to $device")
                    ensureActive()
                    Log.i("raceService", "coroutine is active: $isActive")
                    try {
                        val socket = run {
                            if (ActivityCompat.checkSelfPermission(
                                    this@RaceService,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                setBtState(BtConnectionState.NoPermissions)
                            }
                            device.createRfcommSocketToServiceRecord(sppUuid)
                        }
                        setBtState(BtConnectionState.ConnectingWithSocket(device, socket))
                        try {
                            socket.connect()
                        } catch (e: IOException) {
                            setBtState(BtConnectionState.Disconnecting(device, socket))
                        }

                        val input = socket.inputStream
                        val output = socket.outputStream

                        EchoOffCommand().run(input, output)
                        LineFeedOffCommand().run(input, output)
                        TimeoutCommand(30).run(input, output)
                        SelectProtocolCommand(ObdProtocols.AUTO).run(input, output)

                        setBtState(BtConnectionState.Connected(device, socket))
                    } catch (e: IOException) {
                        setBtState(BtConnectionState.LostConnection(device))
                    } catch (e: NoDataException) {
                        setBtState(BtConnectionState.LostConnection(device))
                    }
                }

                is BtConnectionState.Disconnecting -> {
                    try {
                        myState.socket.close()
                    } finally {
                        setBtState(BtConnectionState.LostConnection(device))
                    }
                }

                is BtConnectionState.LostConnection -> {
                    Log.i("raceService", "lost bluetooth connection to $device")
                    setBtState(BtConnectionState.Connecting(device))
                }

                BtConnectionState.NoPermissions -> break
                BtConnectionState.NoTargetMacAddress -> break

                is BtConnectionState.ConnectingWithSocket,
                BtConnectionState.NotInitialized -> error("unexpected state")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startRaceJob()

        return START_STICKY
    }

    private fun startRaceJob() {
        synchronized(this) {
            if (raceJob == null) {
                val launchedJob = raceJob()
                raceJob = launchedJob
                launchedJob.invokeOnCompletion {
                    raceJob = null
                }
            }
        }
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