package com.h0tk3y.rally

import android.Manifest
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.pires.obd.commands.protocol.EchoOffCommand
import com.github.pires.obd.commands.protocol.LineFeedOffCommand
import com.github.pires.obd.commands.protocol.SelectProtocolCommand
import com.github.pires.obd.commands.protocol.TimeoutCommand
import com.github.pires.obd.enums.ObdProtocols
import com.github.pires.obd.exceptions.BusInitException
import com.github.pires.obd.exceptions.NoDataException
import com.github.pires.obd.exceptions.NonNumericResponseException
import com.github.pires.obd.exceptions.UnableToConnectException
import com.github.pires.obd.exceptions.UnknownErrorException
import com.h0tk3y.rally.android.MainActivity
import com.h0tk3y.rally.model.RaceModel
import com.h0tk3y.rally.model.RaceState
import com.h0tk3y.rally.model.duration
import com.h0tk3y.rally.model.interval
import com.h0tk3y.rally.obd.MySpeedCommand
import com.h0tk3y.rally.obd.SelectEcuCommand
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
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration


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

    fun distanceCorrection(distanceKm: DistanceKm) {
        updateRaceStateByMoving(newDistance = { it + distanceKm })
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
                    while (isActive) {
                        val newTime = Clock.System.now()
                        val timeElapsedHr = TimeHr.interval(lastTime, newTime)
                        val speed = debugSpeed ?: SpeedKmh(0.0)
                        updateRaceStateByMoving(newDistance = {
                            it + DistanceKm.byMoving(speed, timeElapsedHr) * (if (deltaDistanceGoingUp.value) 1.0 else -1.0)
                        }, newInstantSpeed = { debugSpeed ?: SpeedKmh(0.0) })
                        delay(100)
                        lastTime = newTime
                    }
                }
            }
        }
    }

    fun startRace(raceSectionId: Long, startAtDistanceKm: DistanceKm, setDistance: DistanceKm, startAtTime: Instant) {
        deltaDistanceGoingUp.value = true
        val previousFinishInstant = when (val current = _raceState.value) {
            is RaceState.Going -> current.finishedAt
            is RaceState.Stopped -> current.finishedAt
            is RaceState.InRace -> Clock.System.now()
            else -> null
        }
        val previousFinishModel = when (val current = _raceState.value) {
            is RaceState.Going -> current.finishedRaceModel
            is RaceState.Stopped -> current.finishedModel
            is RaceState.InRace -> current.raceModel
            else -> null
        }

        val current = _raceState.value

        val newRaceModel = RaceModel(startAtTime, startAtDistanceKm, setDistance, DistanceKm.zero, true, SpeedKmh(0.0))

        val raceModelOfGoing = when (current) {
            is RaceState.Going -> current.raceModel
            is RaceState.InRace -> current.goingModel
            else -> newRaceModel
        }

        _raceState.value = RaceState.InRace(
            raceSectionId,
            newRaceModel,
            previousFinishInstant,
            previousFinishModel,
            raceModelOfGoing
        )
    }

    fun go(raceSectionId: Long, startAtDistanceKm: DistanceKm, setDistance: DistanceKm, startAtTime: Instant) {
        deltaDistanceGoingUp.value = true
        _raceState.value = RaceState.Going(
            raceSectionId,
            RaceModel(startAtTime, startAtDistanceKm, setDistance, DistanceKm.zero, true, SpeedKmh(0.0)),
            null,
            null
        )
    }

    fun finishRace(withRaceModel: RaceModel) {
        _raceState.value = when (val current = _raceState.value) {
            is RaceState.InRace -> RaceState.Going(
                current.raceSectionId,
                current.goingModel.copy(
                    currentDistance = withRaceModel.currentDistance,
                    distanceGoingUp = current.raceModel.distanceGoingUp,
                    instantSpeed = current.raceModel.instantSpeed
                ),
                withRaceModel,
                Clock.System.now()
            )

            is RaceState.Going,
            is RaceState.Stopped,
            is RaceState.NotStarted -> current
        }
    }

    fun undoFinishRace() {
        _raceState.value = when (val current = _raceState.value) {
            is RaceState.Going -> if (current.finishedRaceModel != null) RaceState.InRace(
                current.raceSectionId,
                current.finishedRaceModel.copy(
                    currentDistance = current.raceModel.currentDistance,
                    distanceGoingUp = current.raceModel.distanceGoingUp,
                    instantSpeed = current.raceModel.instantSpeed
                ),
                null,
                null,
                current.raceModel
            ) else current

            is RaceState.InRace,
            is RaceState.Stopped,
            is RaceState.NotStarted -> current
        }
    }

    fun stopRace() {
        _raceState.value = when (val current = _raceState.value) {
            is RaceState.Going -> RaceState.Stopped(current.raceSectionId, current.raceModel, Clock.System.now(), current.finishedAt, current.finishedRaceModel)
            is RaceState.InRace -> RaceState.Stopped(current.raceSectionId, current.raceModel, Clock.System.now(), null, null)
            RaceState.NotStarted -> current
            is RaceState.Stopped -> current
        }
    }

    fun undoStop() {
        _raceState.value = when (val current = _raceState.value) {
            is RaceState.Stopped -> RaceState.Going(current.raceSectionIdAtStop, current.raceModelAtStop, current.finishedModel, current.finishedAt)
            is RaceState.InRace,
            RaceState.NotStarted,
            is RaceState.Going -> current
        }
    }

    fun resetRace() {
        _raceState.value = RaceState.NotStarted
    }

    private fun updateRaceStateByMoving(
        newDistance: (DistanceKm) -> DistanceKm = { it },
        newCorrection: (DistanceKm) -> DistanceKm = { it },
        newInstantSpeed: (SpeedKmh) -> SpeedKmh = { it }
    ): Boolean =
        when (val currentState = _raceState.value) {
            is RaceState.InRace -> {
                _raceState.value = currentState.copy(
                    raceModel = updateRaceModel(currentState.raceModel, newDistance, newCorrection, newInstantSpeed)
                )
                true
            }

            is RaceState.Going -> {
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
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentTitle("h0tk3y's Rally Tool")
            .setContentText("Starting race service")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        startForeground(RACE_NOTIFICATION_ID, notification)
    }

    private fun postRaceStateNotification() {
        val state = raceState.value

        val titleState = when (state) {
            is RaceState.Going -> ": Going"
            is RaceState.InRace -> ": In race"
            RaceState.NotStarted -> ""
            is RaceState.Stopped -> ": Stopped"
        }

        fun deltaTimeText(startAtTime: Instant) =
            TimeHr.duration(Clock.System.now() - startAtTime).toTimeDayHrMinSec().timeStrNoHoursIfZero()

        val contentText = when (state) {
            is RaceState.Going -> {
                state.raceModel.currentDistance.valueKm.strRound3() + " / " + deltaTimeText(state.raceModel.startAtTime)
            }

            is RaceState.InRace -> state.raceModel.currentDistance.valueKm.strRound3() + " / " + deltaTimeText(state.raceModel.startAtTime)
            RaceState.NotStarted -> "Not started"
            else -> null
        }

        val intent = Intent(this, MainActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
        )


        notification = NotificationCompat.Builder(this, TIMER_SERVICE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentTitle("h0tk3y's Rally Tool$titleState")
            .let { if (contentText != null) it.setContentText(contentText) else it }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PERMISSION_GRANTED) {
            return
        }
        notificationManager.notify(RACE_NOTIFICATION_ID, notification)
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

        sealed interface PrimaryState : BtConnectionState

        data object NotInitialized : BtConnectionState, PrimaryState
        data object NoTargetMacAddress : BtConnectionState, PrimaryState
        data object NoPermissions : BtConnectionState, PrimaryState
        data class Connecting(override val device: BluetoothDevice) : HasDevice, PrimaryState
        data class LostConnection(override val device: BluetoothDevice) : HasDevice, PrimaryState
        data class Disconnecting(override val device: BluetoothDevice, override val socket: BluetoothSocket) : HasDevice, HasSocket
        data class Connected(val device: BluetoothDevice, override val socket: BluetoothSocket) : BtConnectionState, HasSocket, PrimaryState

        fun toPublicState(): BtPublicState {
            return when (this) {
                NotInitialized -> BtPublicState.NotInitialized
                NoTargetMacAddress -> BtPublicState.NoTargetMacAddress
                NoPermissions -> BtPublicState.NoPermissions
                is Connected -> BtPublicState.Working
                is Connecting -> BtPublicState.Connecting

                is Disconnecting,
                is LostConnection -> BtPublicState.Reconnecting
            }
        }
    }

    private fun raceJob(): Job = CoroutineScope(Dispatchers.Default).launch {
        launch {
            while (true) {
                postRaceStateNotification()
                delay(1000L)
            }
        }
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
            launchBtMainLoopAndFreeResources(btAdapter.getRemoteDevice(btMac))
        } else {
            btState.value = BtConnectionState.NoTargetMacAddress
        }
    }

    private suspend fun launchBtMainLoopAndFreeResources(device: BluetoothDevice) {
        withContext(Dispatchers.IO) {
            launch {
                coroutineContext.btMainLoop(device)
            }
        }.also {
            it.invokeOnCompletion {
                btState.value = BtConnectionState.NotInitialized
            }
        }
    }

    @RequiresPermission(BLUETOOTH_CONNECT)
    private fun btSocketForDevice(device: BluetoothDevice): BluetoothSocket =
        device.createRfcommSocketToServiceRecord(sppUuid)

    private suspend fun CoroutineContext.btMainLoop(device: BluetoothDevice) {
        while (isActive) {
            if (ActivityCompat.checkSelfPermission(this@RaceService, BLUETOOTH_CONNECT) != PERMISSION_GRANTED) {
                btState.value = BtConnectionState.NoPermissions
                Log.d("raceService", "missing Bluetooth permissions")
                delay(5000L)
                continue
            }

            btState.value = BtConnectionState.Connecting(device)
            try {
                Log.i("raceService", "connecting to $device")
                btSocketForDevice(device)
            } catch (e: IOException) {
                Log.i("raceService", "failed to connect to $device", e)
                continue
            }.use { socket ->
                ensureActive()
                try {
                    initObdDevice(socket)
                    btState.value = BtConnectionState.Connected(device, socket)
                    btCommunicationLoop(socket)
                } catch (e: IOException) {
                    Log.i(tag, "failed to initialize $device", e)
                } catch (e: NoDataException) {
                    Log.i(tag, "no data from device", e)
                } catch (e: UnableToConnectException) {
                    Log.i(tag, "unable to connect to $device", e)
                } catch (e: BusInitException) {
                    Log.i(tag, "bus init exception", e)
                } catch (e: UnknownErrorException) {
                    Log.i(tag, "unknown error", e)
                } catch (e: NonNumericResponseException) {
                    Log.i(tag, "non-numeric response", e)
                }
            }
        }
    }

    @RequiresPermission(BLUETOOTH_CONNECT)
    private fun initObdDevice(socket: BluetoothSocket) {
        socket.connect()

        val input = socket.inputStream
        val output = socket.outputStream

        EchoOffCommand().run(input, output)
        LineFeedOffCommand().run(input, output)
        TimeoutCommand(100).run(input, output)
        SelectEcuCommand().run(input, output)
        SelectProtocolCommand(ObdProtocols.AUTO).run(input, output)
    }

    private fun CoroutineContext.btCommunicationLoop(socket: BluetoothSocket) {
        var lastTime = Clock.System.now()

        while (isActive) {
            val input = socket.inputStream
            val output = socket.outputStream

            try {
                val speedCommand = MySpeedCommand().apply { run(input, output) }
                val time = Clock.System.now()
                val speed = speedCommand.metricSpeed
                Log.d("debug speed", "raw data: ${speedCommand.debugData}, response: $speed")
                val speedKmh = SpeedKmh(speed.toDouble() / calibration)
                updateRaceStateFromObdData(speedKmh, time - lastTime, speed)
                lastTime = time
            } catch (e: NoDataException) {
                Log.i(tag, "no data from device, continuing")
            }
        }
    }

    private fun updateRaceStateFromObdData(speedKmh: SpeedKmh, deltaTime: Duration, speed: Int) {
        updateRaceStateByMoving(
            newDistance = { dst ->
                val deltaDistance = DistanceKm.byMoving(
                    speedKmh,
                    TimeHr(deltaTime.inWholeMicroseconds / 1000_000.0 / 3600.0)
                )
                val newDst = dst + deltaDistance.times(if (deltaDistanceGoingUp.value) 1.0 else -1.0)
                Log.i("raceData", "speed: $speed, distance: ${dst.valueKm.strRound3()}")
                newDst
            },
            newInstantSpeed = { speedKmh }
        )
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

private const val tag = "raceService"
