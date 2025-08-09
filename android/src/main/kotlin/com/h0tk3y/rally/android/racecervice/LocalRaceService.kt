package com.h0tk3y.rally.android.racecervice

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
import android.content.Context
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
import com.h0tk3y.rally.DistanceKm
import com.h0tk3y.rally.LineNumber
import com.h0tk3y.rally.PositionLine
import com.h0tk3y.rally.PositionLineModifier
import com.h0tk3y.rally.SpeedKmh
import com.h0tk3y.rally.TimeHr
import com.h0tk3y.rally.android.MainActivity
import com.h0tk3y.rally.android.PreferenceRepository
import com.h0tk3y.rally.android.TelemetrySource
import com.h0tk3y.rally.android.TelemetrySource.BT_OBD
import com.h0tk3y.rally.android.dataStore
import com.h0tk3y.rally.android.racecervice.RaceNotificationUtils.RACE_NOTIFICATION_ID
import com.h0tk3y.rally.android.racecervice.SerializationUtils.json
import com.h0tk3y.rally.db.Section
import com.h0tk3y.rally.model.RaceModel
import com.h0tk3y.rally.model.RaceState
import com.h0tk3y.rally.model.duration
import com.h0tk3y.rally.model.interval
import com.h0tk3y.rally.obd.MySpeedCommand
import com.h0tk3y.rally.obd.SelectEcuCommand
import com.h0tk3y.rally.strRound3
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration


interface CommonRaceService {
    val raceState: StateFlow<RaceState>
    val rememberSpeedLimit: StateFlow<SpeedKmh?>
    val telemetryPublicState: Flow<TelemetryPublicState>
}

interface StreamSourceService {
    fun updatePositions(positionsList: List<PositionLine>)
    fun updateCurrentLine(currentLine: LineNumber)
    fun updateSection(section: Section)
    fun updateCurrentRaceLine(currentLine: LineNumber)
}

sealed interface TelemetryPublicState {
    data object Simulation : TelemetryPublicState

    data object WaitingForStream : TelemetryPublicState
    data class ReceivesStream(val isDelayed: Boolean) : TelemetryPublicState

    data object NotInitialized : TelemetryPublicState
    data object BtNoTargetMacAddress : TelemetryPublicState
    data object BtNoPermissions : TelemetryPublicState
    data object BtConnecting : TelemetryPublicState
    data object BtWorking : TelemetryPublicState
    data object BtReconnecting : TelemetryPublicState
}


interface RaceServiceControls : CommonRaceService {
    fun setDistanceGoingUp(isUp: Boolean)
    fun distanceCorrection(distanceKm: DistanceKm)
    fun setDebugSpeed(speedKmh: SpeedKmh)
    fun setRememberSpeedLimit(speedKmh: SpeedKmh?)
    fun startRace(raceSectionId: Long, startAtDistanceKm: DistanceKm, setDistance: DistanceKm, startAtTime: Instant)
    fun go(raceSectionId: Long, startAtDistanceKm: DistanceKm, setDistance: DistanceKm, startAtTime: Instant)
    fun finishRace(withRaceModel: RaceModel)
    fun undoFinishRace()
    fun stopRace()
    fun undoStop()
    fun resetRace()
}

interface StreamedRaceService : CommonRaceService {
    val section: StateFlow<Section?>
    val positions: StateFlow<List<PositionLine>?>
    val currentLine: StateFlow<LineNumber>
    val currentRaceLine: StateFlow<LineNumber?>
}

class TcpStreamedRaceService : StreamedRaceService, Service() {
    private val _raceState = MutableStateFlow<RaceState>(RaceState.NotStarted)
    override val raceState: StateFlow<RaceState> get() = _raceState

    private val _rememberSpeedLimit = MutableStateFlow<SpeedKmh?>(null)
    override val rememberSpeedLimit: StateFlow<SpeedKmh?> get() = _rememberSpeedLimit

    private val _section = MutableStateFlow<Section?>(null)
    override val section: StateFlow<Section?> get() = _section

    private val _positions = MutableStateFlow<List<PositionLine>?>(null)
    override val positions: StateFlow<List<PositionLine>?> get() = _positions

    private val _currentLine = MutableStateFlow(LineNumber(1, 0))
    override val currentLine: StateFlow<LineNumber> get() = _currentLine

    private val _currentRaceLine = MutableStateFlow<LineNumber?>(null)
    override val currentRaceLine: StateFlow<LineNumber?> get() = _currentRaceLine

    private sealed interface TelemetryState {
        data object NotInitialized : TelemetryState
        data object Connected : TelemetryState
    }

    private var _telemetryState = MutableStateFlow<TelemetryState>(TelemetryState.NotInitialized)

    override val telemetryPublicState: Flow<TelemetryPublicState>
        get() = _telemetryState.map {
            when (it) {
                TelemetryState.NotInitialized -> TelemetryPublicState.WaitingForStream
                TelemetryState.Connected -> TelemetryPublicState.ReceivesStream(false)
            }
        }

    override fun onBind(intent: Intent?): IBinder? {
        return LocalBinder()
    }

    override fun onCreate() {
        super.onCreate()
        RaceNotificationUtils.createNotificationChannel(NotificationManagerCompat.from(this))
        startForeground(RaceNotificationUtils.RECEIVE_STREAM_NOTIFICATION_ID, RaceNotificationUtils.serviceStartNotification(this))
    }

    inner class LocalBinder : Binder() {
        val service: TcpStreamedRaceService = this@TcpStreamedRaceService
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startReceiveJob()

        return START_STICKY
    }

    private var receiveJob: Job? = null

    private fun startReceiveJob() {
        if (receiveJob == null) {
            synchronized(this) {
                if (receiveJob == null) {
                    val launchedJob = receiveJob()
                    receiveJob = launchedJob
                    launchedJob.invokeOnCompletion {
                        receiveJob = null
                    }
                }
            }
        }
    }

    override fun stopService(name: Intent?): Boolean {
        receiveJob?.cancel()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        receiveJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private val serviceScope = CoroutineScope(SupervisorJob())

    private fun receiveJob(): Job = serviceScope.launch {
        launch {
            while (isActive) {
                RaceNotificationUtils.postRaceStateNotification(
                    this@TcpStreamedRaceService,
                    NotificationManagerCompat.from(this@TcpStreamedRaceService),
                    raceState.value,
                    RaceNotificationUtils.RaceNotificationKind.TELE
                )
                delay(1000L)
            }
        }

        launch(Dispatchers.IO) { coroutineContext.startNetworkReceiverLoop() }
    }

    private suspend fun CoroutineContext.startNetworkReceiverLoop() {
        while (isActive) {
            try {
                ServerSocket(9999).apply {
                    soTimeout = 2000
                }.use { serverSocket ->
                    serverSocket.accept().apply {
                        soTimeout = 2000
                    }.use { socket ->
                        _telemetryState.value = TelemetryState.Connected
                        DataInputStream(socket.getInputStream()).use { input ->
                            while (isActive) {
                                val size = input.readInt()
                                val buffer = ByteArray(size)
                                input.readFully(buffer)
                                handleFrame(buffer)
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                _telemetryState.value = TelemetryState.NotInitialized
                Log.d("raceService", "network failure in receiver", e)
                delay(500)
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun handleFrame(byteArray: ByteArray) {
        val raceState = json.decodeFromStream<TelemetryFrame>(byteArray.inputStream())
        _section.value = Section(-1, raceState.section?.name ?: "", "")
        _positions.value = raceState.serializedPositions
        _raceState.value = raceState.raceState
        _currentLine.value = raceState.currentLine
        _currentRaceLine.value = raceState.currentRaceLine
        _rememberSpeedLimit.value = raceState.rememberSpeed
    }
}

@Serializable
data class SectionData(val name: String)

@Serializable
data class TelemetryFrame(
    val section: SectionData?,
    val serializedPositions: List<PositionLine>,
    val currentLine: LineNumber,
    val currentRaceLine: LineNumber?,
    val rememberSpeed: SpeedKmh?,
    val raceState: RaceState
)

class LocalRaceService : CommonRaceService, RaceServiceControls, StreamSourceService, Service() {
    private val _raceState = MutableStateFlow<RaceState>(RaceState.NotStarted)
    private val _rememberSpeedLimit = MutableStateFlow<SpeedKmh?>(null)

    private val _section = MutableStateFlow<Section?>(null)
    private val _positions = MutableStateFlow<List<PositionLine>>(emptyList())
    private val _currentLine = MutableStateFlow(LineNumber(1, 0))
    private val _currentRaceLine = MutableStateFlow<LineNumber?>(LineNumber(1, 0))

    override val raceState: StateFlow<RaceState> get() = _raceState
    override val rememberSpeedLimit: StateFlow<SpeedKmh?> = _rememberSpeedLimit

    private val prefs by lazy { PreferenceRepository(dataStore).userPreferencesFlow }
    private val btMac by lazy { prefs.map { it.btMac } }
    private val telemetrySource by lazy { prefs.map { it.telemetrySource } }
    private val sendTeleToIp by lazy { prefs.map { it.sendTeleToIp } }

    private var debugSpeed: SpeedKmh? = null
    private var debugSpeedJob: Job? = null
    private var deltaDistanceGoingUp = MutableStateFlow(false)
    private var calibration = 1.0

    override fun updateSection(section: Section) {
        _section.value = section
    }

    override fun updatePositions(positionsList: List<PositionLine>) {
        _positions.value = positionsList
    }

    override fun updateCurrentLine(currentLine: LineNumber) {
        _currentLine.value = currentLine
    }

    override fun updateCurrentRaceLine(currentLine: LineNumber) {
        _currentRaceLine.value = currentLine
    }

    override fun setDistanceGoingUp(isUp: Boolean) {
        deltaDistanceGoingUp.value = isUp
        updateRaceStateByMoving({ it }, { it }, { it })
    }

    override fun distanceCorrection(distanceKm: DistanceKm) {
        updateRaceStateByMoving(newDistance = { it + distanceKm })
    }

    override fun setDebugSpeed(speedKmh: SpeedKmh) {
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

    override fun setRememberSpeedLimit(speedKmh: SpeedKmh?) {
        _rememberSpeedLimit.value = speedKmh
    }

    override fun startRace(raceSectionId: Long, startAtDistanceKm: DistanceKm, setDistance: DistanceKm, startAtTime: Instant) {
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

    override fun go(raceSectionId: Long, startAtDistanceKm: DistanceKm, setDistance: DistanceKm, startAtTime: Instant) {
        deltaDistanceGoingUp.value = true
        _raceState.value = RaceState.Going(
            raceSectionId,
            RaceModel(startAtTime, startAtDistanceKm, setDistance, DistanceKm.zero, true, SpeedKmh(0.0)),
            null,
            null
        )
    }

    override fun finishRace(withRaceModel: RaceModel) {
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

    override fun undoFinishRace() {
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

    override fun stopRace() {
        _raceState.value = when (val current = _raceState.value) {
            is RaceState.Going -> RaceState.Stopped(current.raceSectionId, current.raceModel, Clock.System.now(), current.finishedAt, current.finishedRaceModel)
            is RaceState.InRace -> RaceState.Stopped(current.raceSectionId, current.raceModel, Clock.System.now(), null, null)
            RaceState.NotStarted -> current
            is RaceState.Stopped -> current
        }
    }

    override fun undoStop() {
        _raceState.value = when (val current = _raceState.value) {
            is RaceState.Stopped -> RaceState.Going(current.raceSectionIdAtStop, current.raceModelAtStop, current.finishedModel, current.finishedAt)
            is RaceState.InRace,
            RaceState.NotStarted,
            is RaceState.Going -> current
        }
    }

    override fun resetRace() {
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
        RaceNotificationUtils.createNotificationChannel(NotificationManagerCompat.from(this))
        startForeground(RACE_NOTIFICATION_ID, RaceNotificationUtils.serviceStartNotification(this))
    }

    override fun stopService(name: Intent?): Boolean {
        raceJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
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
    private var btState = MutableStateFlow<TelemetryState>(TelemetryState.NotInitialized)
    override val telemetryPublicState = btState.map { it.toPublicState() }

    private sealed interface BtConnectionState {
        sealed interface HasDevice : BtConnectionState {
            val device: BluetoothDevice
        }

        sealed interface HasSocket : BtConnectionState {
            val socket: BluetoothSocket
        }

        sealed interface PrimaryState : BtConnectionState

        data object NoTargetMacAddress : PrimaryState
        data object NoPermissions : PrimaryState
        data class Connecting(override val device: BluetoothDevice) : HasDevice, PrimaryState
        data class LostConnection(override val device: BluetoothDevice) : HasDevice, PrimaryState
        data class Disconnecting(override val device: BluetoothDevice, override val socket: BluetoothSocket) : HasDevice, HasSocket
        data class Connected(val device: BluetoothDevice, override val socket: BluetoothSocket) : PrimaryState,
            HasSocket
    }

    private sealed interface TelemetryState {
        data object NotInitialized : TelemetryState
        data object UsesSimulator : TelemetryState
        data object ReceivesStream : TelemetryState
        data class BtTelemetry(val connectionState: BtConnectionState) :
            TelemetryState

        fun toPublicState(): TelemetryPublicState {
            return when (this) {
                NotInitialized -> TelemetryPublicState.NotInitialized
                UsesSimulator -> TelemetryPublicState.Simulation
                is BtTelemetry -> when (this.connectionState) {
                    BtConnectionState.NoTargetMacAddress -> TelemetryPublicState.BtNoTargetMacAddress
                    BtConnectionState.NoPermissions -> TelemetryPublicState.BtNoPermissions
                    is BtConnectionState.Connected -> TelemetryPublicState.BtWorking
                    is BtConnectionState.Connecting -> TelemetryPublicState.BtConnecting

                    is BtConnectionState.Disconnecting,
                    is BtConnectionState.LostConnection -> TelemetryPublicState.BtReconnecting
                }

                ReceivesStream -> TelemetryPublicState.ReceivesStream(false)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob())

    private fun raceJob(): Job = serviceScope.launch {
        launch {
            while (true) {
                RaceNotificationUtils.postRaceStateNotification(
                    this@LocalRaceService,
                    NotificationManagerCompat.from(this@LocalRaceService),
                    raceState.value,
                    RaceNotificationUtils.RaceNotificationKind.LOCAL
                )
                delay(1000L)
            }
        }

        /** Use an explicit job handle instead of [kotlinx.coroutines.flow.Flow.collectLatest]
         * so that cancellation does not block us from updating the state and running another job. */
        var telemetryJob: Job? = null

        launch {
            prefs.map { it.calibration }.collectLatest { calibration = it }
        }

        telemetrySource.zip(btMac, ::Pair)
            .map { if (it.first != BT_OBD) it.copy(second = "") else it }
            .distinctUntilChanged()
            .onEach { (telemetrySource, newBtMac) ->
                telemetryJob?.cancel()

                when (telemetrySource) {
                    BT_OBD -> {
                        debugSpeed = SpeedKmh(0.0)
                        debugSpeedJob?.cancel()

                        if (newBtMac != null) {
                            telemetryJob = serviceScope.launch(Dispatchers.IO) {
                                launch {
                                    startBtMainLoopJob(newBtMac)
                                }
                                launch {
                                    startSendTeleJob()
                                }
                            }
                        } else {
                            btState.value = TelemetryState.BtTelemetry(BtConnectionState.NoTargetMacAddress)
                        }
                    }

                    TelemetrySource.SIMULATION -> {
                        btState.value = TelemetryState.UsesSimulator
                        setDebugSpeed(SpeedKmh(0.0))
                        telemetryJob = launch { startSendTeleJob() }
                    }
                }
            }.launchIn(serviceScope)
    }

    private fun CoroutineScope.startSendTeleJob() {
        var sendJob: Job? = null
        launch(Dispatchers.IO) {
            sendTeleToIp.distinctUntilChanged().onEach { ip ->
                sendJob?.cancel()
                sendJob = launch {
                    if (ip != null) {
                        while (isActive) {
                            sendDataWithSocket(ip)
                        }
                    }
                }
            }.launchIn(this)
        }
    }

    private suspend fun CoroutineScope.sendDataWithSocket(ip: String) {
        try {
            Socket(ip, 9999).apply {
                soTimeout = 2000
            }.use { socket ->
                DataOutputStream(socket.getOutputStream()).use { out ->
                    fun sendFrame(data: ByteArray) {
                        out.writeInt(data.size)
                        out.write(data)
                        out.flush()
                    }

                    while (isActive) {
                        val data = TelemetryFrame(
                            _section.value?.let { SectionData(it.name) },
                            _positions.value,
                            _currentLine.value,
                            _currentRaceLine.value,
                            _rememberSpeedLimit.value,
                            _raceState.value
                        )
                        sendFrame(
                            json.encodeToString<TelemetryFrame>(data).toByteArray()
                        )
                        delay(50)
                    }
                }
            }
        } catch (e: IOException) {
            Log.d("raceService", "network failure in tele sender", e)
            delay(1000)
        }
    }

    private suspend fun startBtMainLoopJob(btMac: String) {
        val btAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (BluetoothAdapter.checkBluetoothAddress(btMac)) {
            launchBtMainLoopAndFreeResources(btAdapter.getRemoteDevice(btMac))
        } else {
            btState.value = TelemetryState.BtTelemetry(BtConnectionState.NoTargetMacAddress)
        }
    }

    private suspend fun launchBtMainLoopAndFreeResources(device: BluetoothDevice) {
        withContext(Dispatchers.IO) {
            launch {
                coroutineContext.btMainLoop(device)
            }
        }.also {
            it.invokeOnCompletion {
                btState.value = TelemetryState.NotInitialized
            }
        }
    }

    @RequiresPermission(BLUETOOTH_CONNECT)
    private fun btSocketForDevice(device: BluetoothDevice): BluetoothSocket =
        device.createRfcommSocketToServiceRecord(sppUuid)

    private suspend fun CoroutineContext.btMainLoop(device: BluetoothDevice) {
        while (isActive) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this@LocalRaceService, BLUETOOTH_CONNECT) != PERMISSION_GRANTED) {
                btState.value = TelemetryState.BtTelemetry(BtConnectionState.NoPermissions)
                Log.d("raceService", "missing Bluetooth permissions")
                delay(5000L)
                continue
            }

            btState.value = TelemetryState.BtTelemetry(BtConnectionState.Connecting(device))
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
                    btState.value = TelemetryState.BtTelemetry(BtConnectionState.Connected(device, socket))
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
            } catch (_: NoDataException) {
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
        val service: LocalRaceService = this@LocalRaceService
    }

    companion object Companion {
        private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

private const val tag = "raceService"

private object SerializationUtils {
    val json = Json {
        classDiscriminator = "type"
        serializersModule = SerializersModule {
            polymorphic(RaceState::class) {
                subclass(RaceState.NotStarted::class)
                subclass(RaceState.Stopped::class)
                subclass(RaceState.InRace::class)
                subclass(RaceState.Going::class)
            }
            polymorphic(PositionLineModifier::class) {
                subclass(PositionLineModifier.SetAvgSpeed::class)
                subclass(PositionLineModifier.ThenAvgSpeed::class)
                subclass(PositionLineModifier.EndAvgSpeed::class)
                subclass(PositionLineModifier.AstroTime::class)
                subclass(PositionLineModifier.OdoDistance::class)
                subclass(PositionLineModifier.AddSynthetic::class)
                subclass(PositionLineModifier.IsSynthetic::class)
                // TODO Review these
                subclass(PositionLineModifier.Here::class)
                subclass(PositionLineModifier.CalculateAverage::class)
                subclass(PositionLineModifier.EndCalculateAverage::class)
            }
        }
    }

}

private object RaceNotificationUtils {
    enum class RaceNotificationKind {
        LOCAL, TELE
    }

    fun postRaceStateNotification(
        context: Context,
        notificationManager: NotificationManagerCompat,
        state: RaceState,
        kind: RaceNotificationKind
    ) {
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

        val intent = Intent(context, MainActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.setAction(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
        )

        val titleMarker = when (kind) {
            RaceNotificationKind.LOCAL -> ""
            RaceNotificationKind.TELE -> " (driver HUD)"
        }

        val notification = NotificationCompat.Builder(context, TIMER_SERVICE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentTitle("$NOTIFICATION_TITLE_PREFIX$titleMarker$titleState")
            .let { if (contentText != null) it.setContentText(contentText) else it }
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PERMISSION_GRANTED) {
            return
        }
        val id = when (kind) {
            RaceNotificationKind.LOCAL -> RACE_NOTIFICATION_ID
            RaceNotificationKind.TELE -> RECEIVE_STREAM_NOTIFICATION_ID
        }
        notificationManager.notify(id, notification)
    }

    fun serviceStartNotification(context: Context): Notification =
        NotificationCompat.Builder(context, TIMER_SERVICE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentTitle(NOTIFICATION_TITLE_PREFIX)
            .setContentText("Starting race service")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    fun createNotificationChannel(notificationManager: NotificationManagerCompat) {
        val serviceChannel = NotificationChannel(
            TIMER_SERVICE_NOTIFICATION_CHANNEL_ID,
            "Race Service",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(serviceChannel)
    }

    private const val NOTIFICATION_TITLE_PREFIX = "Rally"

    const val RACE_NOTIFICATION_ID = 1
    const val RECEIVE_STREAM_NOTIFICATION_ID = 2
    private const val TIMER_SERVICE_NOTIFICATION_CHANNEL_ID = "RaceService"
}