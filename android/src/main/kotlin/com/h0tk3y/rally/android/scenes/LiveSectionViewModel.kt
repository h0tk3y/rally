package com.h0tk3y.rally.android.scenes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.h0tk3y.rally.*
import com.h0tk3y.rally.PositionLineModifier.*
import com.h0tk3y.rally.android.LoadState
import com.h0tk3y.rally.android.PreferenceRepository
import com.h0tk3y.rally.android.TelemetrySource
import com.h0tk3y.rally.android.db.DatabaseOperations
import com.h0tk3y.rally.android.racecervice.CommonRaceService
import com.h0tk3y.rally.android.racecervice.PrimaryRaceService
import com.h0tk3y.rally.android.racecervice.StreamedRaceService
import com.h0tk3y.rally.android.racecervice.TelemetryPublicState
import com.h0tk3y.rally.android.views.GridKey
import com.h0tk3y.rally.android.views.StartOption
import com.h0tk3y.rally.db.Section
import com.h0tk3y.rally.model.RaceEventKind
import com.h0tk3y.rally.model.RaceEventKind.RACE_START
import com.h0tk3y.rally.model.RaceEventKind.SECTION_START
import com.h0tk3y.rally.model.RaceModel
import com.h0tk3y.rally.model.RaceState
import com.h0tk3y.rally.model.duration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.datetime.*
import java.util.concurrent.CompletableFuture
import kotlin.math.sign
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

interface RaceServiceHolder<S : CommonRaceService> {
    fun setRaceServiceConnector(connector: () -> Unit)
    fun setRaceServiceDisconnector(disconnector: () -> Unit)
    fun onServiceConnected(raceService: S)
    fun onServiceDisconnected()
}

interface CommonSectionViewModel {
    val instant: StateFlow<Instant>

    val viewModelScope: CoroutineScope

    val section: StateFlow<LoadState<Section>>
    val inputPositions: StateFlow<List<RoadmapInputLine>>
    val preprocessedPositions: StateFlow<List<RoadmapInputLine>>
    val results: StateFlow<RallyTimesResult>
    val odoValues: StateFlow<Map<LineNumber, DistanceKm>>
    val subsMatching: StateFlow<SubsMatch>

    val calibration: Flow<Double>

    val selectedLineIndex: StateFlow<LineNumber>
    val raceCurrentLineIndex: Flow<LineNumber?>

    val raceState: Flow<RaceUiState>
    val speedLimitPercent: Flow<String?>
    val rememberSpeed: StateFlow<SpeedKmh?>

    val telemetryState: StateFlow<TelemetryPublicState>

    val raceUiVisible: StateFlow<Boolean>
    val timeAllowance: Flow<TimeAllowance?>
}

abstract class StatefulSectionViewModel(
    private val coroutineDispatcher: CoroutineDispatcher
) : ViewModel(), CommonSectionViewModel {

    protected val _instant: MutableStateFlow<Instant> = MutableStateFlow(Clock.System.now())
    protected val _section: MutableStateFlow<LoadState<Section>> = MutableStateFlow(LoadState.EMPTY)
    protected val _inputPositions: MutableStateFlow<List<RoadmapInputLine>> = MutableStateFlow(emptyList())
    protected val _preprocessedPositions: MutableStateFlow<List<RoadmapInputLine>> = MutableStateFlow(emptyList())
    protected val _results: MutableStateFlow<RallyTimesResult> = MutableStateFlow(RallyTimesResultFailure(emptyList()))
    protected val _odoValues: MutableStateFlow<Map<LineNumber, DistanceKm>> = MutableStateFlow(emptyMap())
    protected val _subsMatching: MutableStateFlow<SubsMatch> = MutableStateFlow(SubsMatch.EMPTY)
    protected val _selectedLineIndex: MutableStateFlow<LineNumber> = MutableStateFlow(LineNumber(1, 0))
    protected val _rememberSpeed: MutableStateFlow<SpeedKmh?> = MutableStateFlow(null)
    protected val _telemetryState: MutableStateFlow<TelemetryPublicState> = MutableStateFlow(TelemetryPublicState.NotInitialized)
    protected val _raceUiVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override val instant: StateFlow<Instant> = _instant
    override val section: StateFlow<LoadState<Section>> = _section
    override val inputPositions: StateFlow<List<RoadmapInputLine>> = _inputPositions
    override val preprocessedPositions: StateFlow<List<RoadmapInputLine>> = _preprocessedPositions
    override val results: StateFlow<RallyTimesResult> = _results
    override val odoValues: StateFlow<Map<LineNumber, DistanceKm>> = _odoValues
    override val subsMatching: StateFlow<SubsMatch> = _subsMatching
    override val selectedLineIndex: StateFlow<LineNumber> = _selectedLineIndex
    override val rememberSpeed: StateFlow<SpeedKmh?> = _rememberSpeed
    override val telemetryState: StateFlow<TelemetryPublicState> = _telemetryState
    override val raceUiVisible: StateFlow<Boolean> = _raceUiVisible

    protected val _subInitComplete = CompletableFuture<Unit>()
    protected fun subInitComplete() {
        _subInitComplete.complete(Unit)
    }

    protected open val currentItems: Collection<RoadmapInputLine>
        get() = preprocessedPositions.value

    protected open fun onUpdateInputPositions() = Unit

    protected open fun onLineNumberChange() = Unit

    protected open fun onSectionUpdate(section: LoadState<Section>) = Unit

    protected fun launchDispatchable(action: suspend CoroutineScope.() -> Unit) = viewModelScope.launch(coroutineDispatcher, block = action)

    init {
        launchDispatchable {
            _subInitComplete.await()

            launch {
                _section.collectLatest<LoadState<Section>> { 
                    onSectionUpdate(it) 
                }
            }
            launch {
                _inputPositions.collectLatest {
                    _preprocessedPositions.value = maybePreprocess(it)
                    // sanitizeSelection()
                    onUpdateInputPositions()
                    _subsMatching.value = SubsMatcher().matchSubs(it.filterIsInstance<PositionLine>())
                }
            }
            launch {
                _selectedLineIndex.collectLatest {
                    onLineNumberChange()
                }
            }
            launch {
                combineTransform(_preprocessedPositions, calibration) { a, b -> emit(a to b) }.collectLatest { (it, calibrationFactor) ->
                    val lines = it.filterIsInstance<PositionLine>()
                    this@launch.launch {
                        val odo = OdoDistanceCalculator.calculateOdoDistances(lines, calibrationFactor)
                        _odoValues.value = odo

                        val rallyTimes = RallyTimesIntervalsCalculator().rallyTimes(lines)
                        _results.value = rallyTimes
                    }
                }
            }
        }
    }

    private fun maybePreprocess(positions: List<RoadmapInputLine>): List<RoadmapInputLine> =
        if (positions.isEmpty()) emptyList() else preprocessRoadmap(positions).toList()
}


interface RaceModelControls {
    /** The telemetry source of the race service; the race mode should only be entered when the source has its permissions */
    val telemetrySource: Flow<TelemetrySource>

    /** Enters the race mode, starting the race service if needed. Check the permissions of [telemetrySource] first. */
    fun enterRaceMode()

    /** Retries the telemetry source if it was blocked by a missing permission that might have been granted now */
    fun recheckTelemetryAccess()
    fun startRace(startOption: StartOption)
    fun finishRace()
    fun undoFinishRace()
    fun stopRace()
    fun undoStopRace()
    fun resetRace()
    fun setGoingForward(isGoingForward: Boolean)
    fun distanceCorrection(distanceKm: DistanceKm)
    fun setRememberSpeed(speedKmh: SpeedKmh?)
    fun leaveRaceMode(forceStop: Boolean)
    fun hideRaceUi()
    fun setSpeedLimitPercent(value: String?)
}

/**
 * Explains once that the battery optimization may stop the race service in the background.
 */
interface BatteryHintControls {
    /** Returns `true` only the first time, unless the app is already exempt from the optimization */
    suspend fun consumeBatteryHint(isIgnoringBatteryOptimizations: Boolean): Boolean
}

/**
 * Lets the user swap the places of the positions list and the race panel. Only the driver HUD supports this.
 */
interface PanelSwapControls {
    val panelsSwapped: StateFlow<Boolean>
    fun togglePanelsSwapped()
}

interface EditableSectionViewModel : CommonSectionViewModel, EditorControls {
    val editorState: StateFlow<EditorState>
    val editorFocus: StateFlow<EditorFocus>
    fun deletePosition(line: RoadmapInputLine)
    fun maybeCreateItemAtDistance(distanceKm: DistanceKm, forceCreateIfExists: Boolean, addModifiers: List<PositionLineModifier> = emptyList()): PositionLine

    val soundFlow: SharedFlow<SoundEvent>
}

enum class SoundEvent {
    BEEP_UP, BEEP_START, BEEP_FINISH, BEEP_REVERSE
}

sealed interface RaceUiState {
    sealed interface HasRaceModel {
        val raceModel: RaceModel
    }

    data object NoRaceServiceConnection : RaceUiState

    data object RaceNotStarted : RaceUiState

    data class RaceGoingInAnotherSection(
        val raceSectionId: Long
    ) : RaceUiState

    data class RaceGoing(
        override val raceModel: RaceModel,
        val serial: Long,
        val lastFinishAt: Instant?,
        val lastFinishModel: RaceModel?,
        val raceModelOfGoingAtSection: RaceModel
    ) : RaceUiState, HasRaceModel

    data class Going(
        override val raceModel: RaceModel,
        val raceModelAtFinish: RaceModel?,
        val finishedAt: Instant?,
        val serial: Long
    ) : RaceUiState, HasRaceModel

    data class Stopped(
        val stoppedAt: Instant,
        override val raceModel: RaceModel,
        val finishedAt: Instant?,
        val raceModelAtFinish: RaceModel?,
    ) : RaceUiState, HasRaceModel
}

class LiveSectionViewModel(
    private val sectionId: Long,
    private val database: DatabaseOperations,
    private val prefs: PreferenceRepository,
    coroutineDispatcher: CoroutineDispatcher
) : StatefulSectionViewModel(coroutineDispatcher), EditableSectionViewModel, RaceModelControls, BatteryHintControls,
    RaceServiceHolder<PrimaryRaceService> {
    private val parser = InputRoadmapParser(DefaultModifierValidator())

    private val _raceCurrentLineNumber: MutableStateFlow<LineNumber> = MutableStateFlow(LineNumber(1, 0))
    private val _editorFocus: MutableStateFlow<EditorFocus> = MutableStateFlow(EditorFocus(0, DataKind.Distance))
    private val _editorState: MutableStateFlow<EditorState> = MutableStateFlow(EditorState(false))

    override val section: StateFlow<LoadState<Section>> = _section.asStateFlow()

    private val _soundFlow: MutableSharedFlow<SoundEvent> = MutableSharedFlow()
    override val soundFlow: SharedFlow<SoundEvent> = _soundFlow

    // Race mode:
    private val _raceState: MutableStateFlow<RaceUiState> = MutableStateFlow(RaceUiState.NoRaceServiceConnection)

    override val selectedLineIndex: StateFlow<LineNumber> = _selectedLineIndex.asStateFlow()
    override val raceCurrentLineIndex: Flow<LineNumber?> =
        _raceCurrentLineNumber.combine(_raceState) { number, state -> number.takeIf { state is RaceUiState.RaceGoing || state is RaceUiState.Going } }
    override val editorState: StateFlow<EditorState> = _editorState.asStateFlow()
    override val editorFocus: StateFlow<EditorFocus> = _editorFocus.asStateFlow()
    override val inputPositions: StateFlow<List<RoadmapInputLine>> = _inputPositions.asStateFlow()
    override val preprocessedPositions: StateFlow<List<RoadmapInputLine>> = _preprocessedPositions.asStateFlow()
    override val results: StateFlow<RallyTimesResult> = _results.asStateFlow()
    override val odoValues: StateFlow<Map<LineNumber, DistanceKm>> = _odoValues.asStateFlow()
    override val subsMatching: StateFlow<SubsMatch> = _subsMatching.asStateFlow()
    override val raceState: Flow<RaceUiState> = _raceState.distinctUntilChanged { _, _ -> false }
    override val rememberSpeed: StateFlow<SpeedKmh?> = _rememberSpeed.asStateFlow()
    override val raceUiVisible: StateFlow<Boolean> = _raceUiVisible.asStateFlow()
    override val telemetryState: StateFlow<TelemetryPublicState> = _telemetryState.asStateFlow()
    override val timeAllowance: Flow<TimeAllowance?> = prefs.userPreferencesFlow.map { it.allowance }
    private val _speedLimitPercent = MutableStateFlow<String?>(null)
    override val speedLimitPercent: StateFlow<String?> = _speedLimitPercent.asStateFlow()

    private var service: PrimaryRaceService? = null

    private var serviceRelatedJob: Job? = null
    private var serviceConnector: () -> Unit = { error("no connector") }
    private var serviceDisconnector: () -> Unit = { error("no disconnector") }

    private var soundFeedback: Boolean = false

    override val calibration: Flow<Double> = prefs.userPreferencesFlow.map { it.calibration }

    override fun onSectionUpdate(section: LoadState<Section>) {
        // Keep the current positions on a parse error rather than replacing them with a placeholder that could get saved
        _inputPositions.value = try {
            positionLines(section)
        } catch (e: IllegalStateException) {
            Log.e("LiveSectionViewModel", "Failed to parse section positions", e)
            return
        }
    }

    override fun hideRaceUi() {
        _raceUiVisible.value = false
    }

    override fun onUpdateInputPositions() {
        super.onUpdateInputPositions()
        sanitizeSelection()
        updateEditorConstraints()
    }

    override fun onLineNumberChange() {
        updateEditorConstraints()
    }

    override val viewModelScope: CoroutineScope
        get() = (this as ViewModel).viewModelScope

    init {
        subInitComplete()

        launchDispatchable {
            launch {
                prefs.userPreferencesFlow.collectLatest {
                    soundFeedback = it.soundFeedback
                }
            }
            launch {
                while (isActive) {
                    _instant.value = Clock.System.now()
                    delay(200.milliseconds)
                }
            }
            launch {
                _inputPositions.collectLatest {
                    _preprocessedPositions.value = maybePreprocess(it)
                    _subsMatching.value = SubsMatcher().matchSubs(it.filterIsInstance<PositionLine>())
                }
            }
            launch {
                _editorFocus.collectLatest {
                    updateEditorConstraints()
                }
            }
            launch {
                database.selectSectionById(sectionId).collect {
                    _section.value = it
                }
            }
        }
    }

    override fun setRaceServiceConnector(connector: () -> Unit) {
        serviceConnector = connector
    }

    override fun setRaceServiceDisconnector(disconnector: () -> Unit) {
        serviceDisconnector = disconnector
    }

    private fun positionLines(it: LoadState<Section>) = if (it is LoadState.Loaded) {
        val result = parser.parseRoadmap(it.value.serializedPositions.reader()).filterIsInstance<PositionLine>()
        result.ifEmpty {
            listOf(
                PositionLine(
                    DistanceKm(0.0),
                    LineNumber(1, 0),
                    listOf(SetAvgSpeed(SpeedKmh(60.0)))
                ),
                PositionLine(DistanceKm(1.0), LineNumber(2, 0), listOf()),
            )
        }
    } else {
        emptyList()
    }

    override fun onServiceConnected(raceService: PrimaryRaceService) {
        service = raceService
        _raceUiVisible.value = true

        serviceRelatedJob = launchDispatchable {
            launch {
                val raceStateFlow = raceService.raceState
                raceStateFlow.collectLatest { newState ->
                    val old = _raceState.value
                    _raceState.value = raceStateToUiState(newState)
                    updateCurrentRaceLine()
                    handleRaceStatesDelta(old, _raceState.value)
                }
            }
            launch {
                raceService.rememberSpeedLimit.collectLatest {
                    _rememberSpeed.value = it
                }
            }
            launch {
                raceService.speedLimitPercent.collectLatest {
                    if (_speedLimitPercent.value != it) {
                        _speedLimitPercent.value = it
                        updateEditorConstraints()
                    }
                }
            }
            launch {
                raceService.telemetryPublicState.collectLatest {
                    _telemetryState.value = it
                }
            }
            launch {
                _section.collectLatest { section ->
                    if (section is LoadState.Loaded)
                        raceService.updateSection(section.value)
                }
            }
            launch {
                _preprocessedPositions.collectLatest { lines ->
                    raceService.updatePositions(lines.filterIsInstance<PositionLine>())
                }
            }
            launch {
                _selectedLineIndex.collectLatest { index ->
                    raceService.updateCurrentLine(index)
                }
            }
            launch {
                _raceCurrentLineNumber.collectLatest { index ->
                    raceService.updateCurrentRaceLine(index)
                }
            }
        }
    }

    private fun updateCurrentRaceLine() {
        val raceState = _raceState.value
        if (raceState is RaceUiState.HasRaceModel) {
            val inRaceAtKm = raceState.raceModel.currentDistance
            val position = _preprocessedPositions.value.lastOrNull { it is PositionLine && it.atKm.roundTo3Digits() <= inRaceAtKm.roundTo3Digits() }
            if (position != null) {
                val currentRaceLineSelected = _raceCurrentLineNumber.value == _selectedLineIndex.value
                _raceCurrentLineNumber.value = position.lineNumber
                if (currentRaceLineSelected && !_editorState.value.isEnabled) {
                    selectLine(position.lineNumber, null)
                }
            }
        }
    }

    private fun handleRaceStatesDelta(old: RaceUiState, new: RaceUiState) {
        if (old is RaceUiState.RaceGoing && new is RaceUiState.RaceGoing && !old.raceModel.distanceGoingUp && !new.raceModel.distanceGoingUp &&
            new.raceModel.currentDistance >= new.raceModel.startAtDistance
        ) {
            val range = listOf(old.raceModel.currentDistance, new.raceModel.currentDistance).sorted().let { (from, to) -> from..to }
            val removeThenAvgFrom = currentItems.filterIsInstance<PositionLine>().filter { it.atKm in range && it.modifier<ThenAvgSpeed>() != null }
            removeThenAvgFrom.forEach { item ->
                removeModifiersFromItem(item) { it is ThenAvgSpeed }
            }
        }
    }

    private fun removeModifiersFromItem(
        item: PositionLine,
        removePredicate: (PositionLineModifier) -> Boolean
    ): PositionLine {
        val indexOfItem = _inputPositions.value.indexOf(item)
        return updateInputPositionAt(
            _inputPositions.value[indexOfItem].lineNumber,
            item.copy(modifiers = item.modifiers.filterNot(removePredicate))
        )
    }


    override fun onServiceDisconnected() {
        service = null
        _raceState.value = RaceUiState.NoRaceServiceConnection
        serviceRelatedJob?.cancel()
    }

    override fun startRace(startOption: StartOption) {
        launchDispatchable {
            if (soundFeedback) _soundFlow.emit(SoundEvent.BEEP_START)
        }

        service?.run {
            val nowToSecond = with(TimeZone.currentSystemDefault()) {
                val nowExact = Clock.System.now().toLocalDateTime()
                val localToSecond = LocalDateTime(nowExact.date, LocalTime(nowExact.hour, nowExact.minute, nowExact.second))
                localToSecond.toInstant()
            }

            val currentRaceState = raceState.value
            val startDistance = when (startOption.locationAndTime) {
                StartOption.StartAtSelectedPositionAtTimeKeepOdo,
                StartOption.StartAtSelectedPositionAtTime,
                StartOption.StartAtSelectedPositionNow -> currentItem?.atKm ?: DistanceKm.zero

                StartOption.StartNowFromGoingState -> when (currentRaceState) {
                    RaceState.NotStarted -> DistanceKm.zero
                    is RaceState.Going -> currentRaceState.raceModel.currentDistance
                    is RaceState.InRace -> currentRaceState.raceModel.currentDistance
                    is RaceState.Stopped -> currentRaceState.raceModelAtStop.startAtDistance
                }
            }.roundTo3Digits()

            database.insertEvent(
                if (startOption.isRace) RACE_START else SECTION_START,
                sectionId,
                startDistance,
                nowToSecond,
                sinceTimestamp = null
            )

            val speedAtDistance: SpeedKmh? =
                rememberSpeed.value ?: currentItem?.takeIf { it.atKm == startDistance }?.modifier<SetAvg>()?.setavg
                ?: findPureSpeedForDistance(startDistance)

            val startTime = when (startOption.locationAndTime) {
                StartOption.StartAtSelectedPositionAtTimeKeepOdo,
                StartOption.StartAtSelectedPositionAtTime -> {
                    currentItem?.modifier<AstroTime>()?.timeOfDay?.let { timeOfDay ->
                        nowToSecond.toLocalDateTime(TimeZone.currentSystemDefault()).date.atTime(
                            LocalTime(timeOfDay.hr, timeOfDay.min, timeOfDay.sec)
                        ).toInstant(TimeZone.currentSystemDefault())
                    } ?: nowToSecond
                }

                StartOption.StartAtSelectedPositionNow,
                StartOption.StartNowFromGoingState -> nowToSecond
            }

            val startModifiersToAdd = listOfNotNull(
                if (startOption.isRace) SetAvgSpeed(speedAtDistance ?: SpeedKmh(60.0)) else null,
                if (startOption.locationAndTime.isNow || currentItem != currentItems.lastOrNull { it is PositionLine && it.atKm == currentItem?.atKm })
                    AstroTime(
                        TimeHr.duration(
                            startTime - startTime.toLocalDateTime(TimeZone.currentSystemDefault()).date.atStartOfDayIn(TimeZone.currentSystemDefault())
                        ).toTimeDayHrMinSec()
                    ) else null
            )

            val line =
                currentItem
                    ?.takeIf {
                        !startOption.isRace ||
                                it.atKm == startDistance &&
                                it != currentItems.firstOrNull() &&
                                it == currentItems.lastOrNull { d -> d is PositionLine && d.atKm == it.atKm }
                    }
                    ?.takeIf { IsSynthetic !in it.modifiers }
                    ?.takeIf { startModifiersToAdd.none { m -> m is SetAvg } || it.modifiers.none { m -> m is EndAvg } }
                    ?.let { addModifiersToItem(it, startModifiersToAdd) }
                    ?: maybeCreateItemAtDistanceFromModel(
                        startDistance,
                        forceCreateIfExists = true,
                        addModifiers = startModifiersToAdd
                    )

            selectLine(line.lineNumber, null)

            val setDistance = if (startOption.locationAndTime == StartOption.StartAtSelectedPositionAtTimeKeepOdo)
                when (currentRaceState) {
                    is RaceState.Going -> currentRaceState.raceModel.currentDistance
                    is RaceState.InRace -> currentRaceState.raceModel.currentDistance
                    is RaceState.Stopped -> currentRaceState.raceModelAtStop.currentDistance
                    RaceState.NotStarted -> startDistance
                } else startDistance

            if (startOption.isRace) {
                startRace(sectionId, startDistance, setDistance, startTime)
            } else go(sectionId, startDistance, setDistance, startTime)
        }
    }

    private fun findPureSpeedForDistance(startDistance: DistanceKm): SpeedKmh? {
        val speedStack = ArrayDeque<SpeedKmh>()
        var lastRaceLimit: SpeedKmh? = null
        currentItems.filterIsInstance<PositionLine>().filter { it.atKm <= startDistance }.forEach { position ->
            if (position.isEndAvg) {
                if (speedStack.isEmpty()) {
                    return null
                } else {
                    speedStack.removeLastOrNull()
                }
            }
            if (position.isSetAvg) {
                speedStack.addLast(position.modifier<SetAvg>()!!.setavg)
            }
            if (position.isThenAvg || position.isSetAvg && speedStack.size > 1) {
                lastRaceLimit = speedStack.last()
            }
        }
        return lastRaceLimit ?: speedStack.lastOrNull()
    }

    fun setDebugSpeed(speedKmh: SpeedKmh) {
        service?.setDebugSpeed(speedKmh)
    }

    private fun raceStateToUiState(
        raceState: RaceState
    ): RaceUiState = when (raceState) {
        is RaceState.InRace, is RaceState.Going -> {
            raceState as RaceState.HasCurrentSection
            when {
                raceState.raceSectionId != (section.value as? LoadState.Loaded)?.value?.id ->
                    RaceUiState.RaceGoingInAnotherSection(raceState.raceSectionId)

                raceState is RaceState.Going -> RaceUiState.Going(raceState.raceModel, raceState.finishedRaceModel, raceState.finishedAt, 0L)
                raceState is RaceState.InRace -> RaceUiState.RaceGoing(
                    raceState.raceModel,
                    0L,
                    raceState.previousFinishAt,
                    raceState.previousFinishModel,
                    raceState.goingModel
                )

                else -> error("unexpected race state")
            }
        }

        RaceState.NotStarted -> RaceUiState.RaceNotStarted
        is RaceState.Stopped -> RaceUiState.Stopped(raceState.stoppedAt, raceState.raceModelAtStop, raceState.finishedAt, raceState.finishedModel)
    }

    override fun switchEditor() {
        val currentEditorState = _editorState.value
        val isEnabled = !currentEditorState.isEnabled
        _editorState.value = currentEditorState.copy(isEnabled = isEnabled)
        if (!isEnabled) {
            _editorFocus.value = _editorFocus.value.copy(rawText = null, selectAll = false)
        } else {
            val item = currentItem
            val currentFocus = _editorFocus.value
            val text = item?.let { itemText(it, currentFocus.kind) }.orEmpty()
            val newFocus = currentFocus.copy(
                cursor = reasonableEditorStartPosition(text),
                rawText = null
            )
            updateFocus(sanitizeFocus(newFocus))
        }
        sanitizeSelection()
    }

    override val telemetrySource: Flow<TelemetrySource> = prefs.userPreferencesFlow
        .map { it.telemetrySource }
        .distinctUntilChanged()

    override fun recheckTelemetryAccess() {
        service?.recheckTelemetryAccess()
    }

    override suspend fun consumeBatteryHint(isIgnoringBatteryOptimizations: Boolean): Boolean {
        if (isIgnoringBatteryOptimizations || prefs.userPreferencesFlow.first().batteryHintShown) return false
        prefs.saveBatteryHintShown()
        return true
    }

    override fun enterRaceMode() {
        _raceUiVisible.value = true
        if (_raceState.value is RaceUiState.NoRaceServiceConnection) {
            serviceConnector()
        }
    }

    override fun finishRace() {
        launchDispatchable {
            if (soundFeedback) _soundFlow.emit(SoundEvent.BEEP_FINISH)
        }

        val currentState = _raceState.value
        if (currentState is RaceUiState.RaceGoing) {
            val finishDistance = currentState.raceModel.currentDistance.roundTo3Digits()
            maybeCreateItemAtDistanceFromModel(
                finishDistance,
                true,
                if (shouldEndStretchAtFinish(finishDistance)) listOf(EndAvgSpeed(null)) else emptyList()
            )
            restoreStartAtime(currentState.raceModelOfGoingAtSection)
            service?.finishRace(currentState.raceModel)

            database.insertEvent(
                RaceEventKind.RACE_FINISH,
                sectionId,
                currentState.raceModel.currentDistance,
                Clock.System.now(),
                sinceTimestamp = currentState.raceModel.startAtTime
            )
        }
    }

    /**
     * Whether the position added at the race finish should end a stretch with an `endavg`.
     *
     * The race start opens a stretch with a `setavg`, but the roadmap may already end that stretch (when the race started at a
     * typed `setavg` that has a typed `endavg`), and the finish may even be before the race start. An `endavg` at the finish
     * ends the innermost open stretch, so every later position becomes one stretch less deep. Add it only if a stretch is open
     * at the finish and every later position is at least two stretches deep: then no later position falls outside all
     * stretches, and no later `endavg` is left without a stretch to end.
     */
    private fun shouldEndStretchAtFinish(finishDistance: DistanceKm): Boolean {
        val positions = currentItems.filterIsInstance<PositionLine>()
        val finishIndex = insertionIndex(positions, finishDistance)
        val depths = stretchDepthsBefore(positions)
        return depths[finishIndex] >= 1 && (finishIndex until positions.size).all { depths[it] >= 2 }
    }

    /** For each position, how many stretches are open just before it; the last item is the number open after the last position. */
    private fun stretchDepthsBefore(positions: List<PositionLine>): IntArray {
        val depths = IntArray(positions.size + 1)
        positions.forEachIndexed { index, position ->
            val depth = depths[index]
            depths[index + 1] = when {
                position.isThenAvg -> maxOf(depth, 1)
                position.isSetAvg -> depth + 1
                position.isEndAvg -> maxOf(depth - 1, 0)
                else -> depth
            }
        }
        return depths
    }

    override fun undoFinishRace() {
        val currentState = _raceState.value
        if (currentState is RaceUiState.Going && currentState.raceModelAtFinish != null) {
            // The finish added the last position at its distance, with an `endavg` or with no marks:
            val finishPosition = currentItems.filterIsInstance<PositionLine>().findLast {
                it.atKm == currentState.raceModelAtFinish.currentDistance.roundTo3Digits()
            }?.takeIf { it.modifiers.all { modifier -> modifier is EndAvgSpeed } }
            if (finishPosition != null) {
                deletePosition(finishPosition)
            }
            restoreStartAtime(currentState.raceModelAtFinish)
            database.insertEvent(
                RaceEventKind.RACE_UNDO_FINISH,
                sectionId,
                currentState.raceModel.currentDistance,
                Clock.System.now(),
                sinceTimestamp = null
            )
        }
        service?.undoFinishRace()
    }

    private fun restoreStartAtime(modelOfGoingAgSection: RaceModel) {
        val startDistance = modelOfGoingAgSection.startAtDistance.roundTo3Digits()
        val startTime = modelOfGoingAgSection.startAtTime
        maybeCreateItemAtDistanceFromModel(
            startDistance,
            forceCreateIfExists = false,
            listOf(AstroTime(TimeDayHrMinSec.of(startTime.toLocalDateTime(TimeZone.currentSystemDefault()).time)))
        )
    }

    override fun stopRace() {
        launchDispatchable {
            if (soundFeedback) _soundFlow.emit(SoundEvent.BEEP_FINISH)
        }

        service?.stopRace()
        val raceModelAtStop = (service?.raceState?.value as? RaceState.Stopped)?.raceModelAtStop
        database.insertEvent(
            RaceEventKind.SECTION_FINISH,
            sectionId,
            raceModelAtStop?.currentDistance ?: DistanceKm.zero,
            Clock.System.now(),
            sinceTimestamp = raceModelAtStop?.startAtTime
        )
    }

    override fun undoStopRace() {
        val stopDistance = (service?.raceState?.value as? RaceState.Stopped)?.raceModelAtStop?.currentDistance ?: DistanceKm.zero
        database.insertEvent(
            RaceEventKind.SECTION_UNDO_FINISH,
            sectionId,
            stopDistance,
            Clock.System.now(),
            sinceTimestamp = null
        )
        service?.undoStop()
    }

    override fun resetRace() {
        service?.resetRace()
    }

    override fun setGoingForward(isGoingForward: Boolean) {
        service?.setDistanceGoingUp(isGoingForward)

        if (!isGoingForward) {
            launchDispatchable {
                while (service?.raceState?.value?.let { it is RaceState.MovingWithRaceModel && !(it.raceModel.distanceGoingUp) } == true) {
                    if (soundFeedback) _soundFlow.emit(SoundEvent.BEEP_REVERSE)
                    delay(3000L.milliseconds)
                }
            }
        }
    }

    override fun distanceCorrection(distanceKm: DistanceKm) {
        service?.distanceCorrection(distanceKm)
    }

    override fun setRememberSpeed(speedKmh: SpeedKmh?) {
        service?.setRememberSpeedLimit(speedKmh)
    }

    override fun leaveRaceMode(forceStop: Boolean) {
        if (forceStop || _raceState.value is RaceUiState.RaceNotStarted) {
            service?.forceStop()
        }
        serviceDisconnector()
        _raceUiVisible.value = false
        _raceState.value = RaceUiState.NoRaceServiceConnection
    }

    private fun fieldText(item: PositionLine, kind: DataKind): String? {
        val focus = _editorFocus.value
        return if (focus.kind == kind && focus.rawText != null) {
            focus.rawText
        } else {
            itemText(item, kind)
        }
    }

    override fun selectLine(index: LineNumber, field: DataKind?) {
        val items = currentItems

        val item = items.find { it.lineNumber == index } ?: items.find { it.lineNumber.number == index.subNumber }
        val lineNumber = item?.lineNumber ?: LineNumber(1, 0)
        val oldLineNumber = _selectedLineIndex.value
        _selectedLineIndex.value = lineNumber

        updateEditorConstraints()

        if (item != null) {
            val currentFocus = _editorFocus.value
            val newKind = field ?: if (itemText(
                    item,
                    currentFocus.kind
                ) != null
            ) currentFocus.kind else DataKind.Distance

            val isSameLineAndField = oldLineNumber == lineNumber && currentFocus.kind == newKind
            if (!isSameLineAndField) {
                val newFocus = currentFocus.copy(
                    cursor = reasonableEditorStartPosition(itemText(item, newKind).orEmpty()),
                    kind = newKind,
                    rawText = null
                )
                updateFocus(sanitizeFocus(newFocus))
            }
        }
    }

    override fun moveCursor(indexDelta: Int) {
        val current = _editorFocus.value
        val next = sanitizeFocus(current.copy(cursor = current.cursor + indexDelta))
        updateFocus(next)
    }

    override fun keyPress(key: GridKey) {
        if (_editorFocus.value.selectAll) {
            if (keyPressWithFieldSelected(key)) return
            _editorFocus.value = _editorFocus.value.copy(selectAll = false)
            if (key == GridKey.LEFT || key == GridKey.RIGHT) return
        }
        when (key) {
            GridKey.SETAVG, GridKey.THENAVG, GridKey.ENDAVG, GridKey.ATIME, GridKey.PERCENT -> focusOrSwitchModifier(key)
            GridKey.APPLY_LAST_PERCENT -> applyLastPercent()
            GridKey.N_1, GridKey.N_2, GridKey.N_3, GridKey.N_4, GridKey.N_5, GridKey.N_6, GridKey.N_7, GridKey.N_8, GridKey.N_9, GridKey.N_0 -> enterCharacter(
                key.name.last()
            )

            GridKey.DOT -> if (_editorState.value.canEnterDot) enterCharacter('.')
            GridKey.NOP -> Unit
            GridKey.UP -> selectPreviousItem()
            GridKey.DOWN -> selectNextItem()
            GridKey.DEL -> deleteCharacter()
            GridKey.LEFT -> moveCursorOrSwitchField(-1)
            GridKey.RIGHT -> moveCursorOrSwitchField(1)
            GridKey.ADD -> addItem()
            GridKey.REMOVE -> currentItem?.let(::deletePositionViaEditor)
        }
    }

    /**
     * Handles a key that replaces the whole selected distance text: a digit starts a new value, a dot starts a fraction,
     * and DEL clears the field. Returns false if the key should instead be handled as usual.
     */
    private fun keyPressWithFieldSelected(key: GridKey): Boolean {
        val focus = _editorFocus.value
        val item = currentItem ?: return false
        if (focus.kind != DataKind.Distance) return false
        val (newText, newCursor) = when (key) {
            GridKey.N_1, GridKey.N_2, GridKey.N_3, GridKey.N_4, GridKey.N_5, GridKey.N_6, GridKey.N_7, GridKey.N_8, GridKey.N_9, GridKey.N_0 ->
                "${key.name.last()}.0" to 1
            GridKey.DOT -> if (_editorState.value.canEnterDot) "0.0" to 2 else return false
            GridKey.DEL -> "0.0" to 1
            else -> return false
        }
        updateCurrentText(item, focus.copy(selectAll = false), newText, newCursor)
        return true
    }

    override fun deletePositionViaEditor(line: RoadmapInputLine) {
        check(editorState.value.isEnabled)
        check(editorState.value.canDelete)
        deletePosition(line)
    }

    override fun deletePosition(line: RoadmapInputLine) {
        val lines = currentItems
        if (line !in lines) {
            return
        }

        val newItems = recalculateLineNumbers(lines.filter { it != line })
        updateInputPositions(newItems)
    }

    override fun selectNextItem() {
        val items = currentItems.toList()
        val currentItemIndex = items.indexOfFirst { it.lineNumber == _selectedLineIndex.value }
        if (currentItemIndex != -1 && currentItemIndex != items.lastIndex) {
            selectLine(items[currentItemIndex + 1].lineNumber, null)
        }
    }

    override fun selectPreviousItem() {
        val items = currentItems.toList()
        val currentItemIndex = items.indexOfFirst { it.lineNumber == _selectedLineIndex.value }
        if (currentItemIndex != -1 && currentItemIndex != 0) {
            selectLine(items[currentItemIndex - 1].lineNumber, null)
        }
    }

    override fun addItem() {
        createNewItem()?.let {
            launchDispatchable {
                selectLine(it, DataKind.Distance)
                val text = currentItem?.let { item -> itemText(item, DataKind.Distance) } ?: return@launchDispatchable
                _editorFocus.value = _editorFocus.value.copy(cursor = text.length, rawText = null, selectAll = true)
            }
        }
    }

    override fun setSpeedLimitPercent(value: String?) {
        _speedLimitPercent.value = value
        if (_raceState.value !is RaceUiState.RaceGoing) {
            val currentRemember = _rememberSpeed.value
            if (currentRemember != null) {
                val percentDouble = value?.toDoubleOrNull()
                _rememberSpeed.value = if (percentDouble != null && percentDouble != 100.0) {
                    SpeedWithPercentage(PlainSpeedKmh(currentRemember.baseSpeedKmh), percentDouble)
                } else {
                    PlainSpeedKmh(currentRemember.baseSpeedKmh)
                }
            }
        }
        service?.setSpeedLimitPercent(value)
        updateEditorConstraints()
    }

    override fun deleteCharacter() {
        currentItem?.let { item ->
            val editorFocus = _editorFocus.value
            val currentText = fieldText(item, editorFocus.kind)

            if (currentText != null) {
                val cursor = editorFocus.cursor
                when {
                    editorFocus.kind == DataKind.AstroTime -> {
                        if (cursor != 0) {
                            val isAfterColon = currentText.getOrNull(cursor - 1) == ':'
                            val takeChars = if (isAfterColon) cursor - 2 else cursor - 1
                            val dropChars = cursor
                            updateCurrentText(
                                item,
                                editorFocus,
                                currentText.take(takeChars) + '0' + (if (isAfterColon) ":" else "") + currentText.drop(dropChars),
                                if (isAfterColon) cursor - 2 else cursor - 1
                            )
                        }
                    }

                    currentText.indexOf('.') == 1 && cursor == 1 -> {
                        val newText = if (editorFocus.kind == DataKind.AverageSpeed || editorFocus.kind == DataKind.SpeedPercentage) "" else "0" + currentText.drop(1)
                        updateCurrentText(item, editorFocus, newText, if (newText.isEmpty()) 0 else cursor)
                    }

                    // Keep single trailing zero after dot: e.g. "1.0|" when deleting ends up as "1.|0"
                    currentText.indexOf('.') != -1 && currentText.substring(currentText.indexOf('.') + 1) == "0" && cursor == currentText.indexOf('.') + 2 -> {
                        updateCurrentText(item, editorFocus, currentText, cursor - 1)
                    }

                    // TODO: this is non-intuitive
                    currentText.getOrNull(cursor - 1) == '.' -> {
                        moveCursor(-1)
                    }

                    else -> {
                        if (cursor > 0) {
                            val textWithoutChar = currentText.take(cursor - 1) + currentText.drop(cursor)
                            val (newText, newCursor) = if (textWithoutChar.endsWith('.')) {
                                (textWithoutChar + "0") to (cursor - 1)
                            } else {
                                textWithoutChar to (cursor - 1)
                            }
                            updateCurrentText(item, editorFocus, newText, newCursor)
                        }
                    }
                }
            }
        }
    }

    private fun enterCharacter(char: Char) {
        currentItem?.let { item ->
            val editorFocus = _editorFocus.value
            val currentText = fieldText(item, editorFocus.kind) ?: return@let
            val cursor = editorFocus.cursor

            if (editorFocus.kind == DataKind.AstroTime) {
                val newText = fixTimeAfterEntered(currentText.take(cursor) + char + currentText.drop(cursor + 1))
                val newCursor = if (char.isDigit() && currentText.getOrNull(cursor + 1) == ':') cursor + 2 else cursor + 1
                updateCurrentText(item, editorFocus, newText, newCursor)
                return
            }

            val dotIndex = currentText.indexOf('.')

            // Exception 1: do not introduce a leading zero in the integral part
            if (char == '0') {
                val inIntegralPart = dotIndex == -1 || cursor <= dotIndex
                if (inIntegralPart && (cursor == 0 && currentText.isNotEmpty() || currentText.startsWith("0"))) {
                    return
                }
            }

            // Exception 2: entering '0' between dot and existing single trailing zero
            if (char == '0' && dotIndex != -1 && currentText.substring(dotIndex + 1) == "0" && cursor == dotIndex + 1) {
                updateCurrentText(item, editorFocus, currentText, cursor + 1)
                return
            }

            if (char.isDigit() && cursor == currentText.length && dotIndex != -1 && currentText.substringAfter('.').length >= 3) {
                return
            }

            val candidateText = when {
                char.isDigit() -> when {
                    currentText == "0" -> "$char"
                    currentText.startsWith("0.") && cursor == 1 -> "$char" + currentText.drop(1)
                    dotIndex != -1 && currentText.substring(dotIndex + 1) == "0" && cursor == dotIndex + 1 ->
                        currentText.take(cursor) + char + currentText.drop(cursor + 1)
                    else -> currentText.take(cursor) + char + currentText.drop(cursor)
                }
                char == '.' -> {
                    val candidate = currentText.take(cursor).replace(".", "") + char + currentText.drop(cursor).replace(".", "")
                    if (candidate.contains('.') && candidate.substringAfter('.', "").length > 3) {
                        candidate.substringBefore('.') + "." + candidate.substringAfter('.').take(3)
                    } else candidate
                }
                else -> currentText
            }

            val newText = if (char.isDigit() && candidateText.contains('.') && candidateText.substringAfter('.', "").length > 3) {
                candidateText.substringBefore('.') + "." + candidateText.substringAfter('.').take(3)
            } else {
                candidateText
            }

            val newCursor = when {
                char.isDigit() && currentText == "0" -> 1
                char.isDigit() && currentText.startsWith("0.") && cursor == 1 -> 1
                char == '.' && dotIndex != -1 && cursor > dotIndex -> cursor
                else -> cursor + 1
            }

            updateCurrentText(item, editorFocus, newText, newCursor)
        }
    }

    private fun fixTimeAfterEntered(timeString: String): String {
        val parts = timeString.split(":")
        val resultParts = parts.toMutableList()
        if (parts[0].toInt() > 23) resultParts[0] = "23"
        return resultParts.joinToString(":")
    }

    private val currentItem: PositionLine?
        get() = currentItems.find { it.lineNumber == _selectedLineIndex.value } as? PositionLine

    private fun updateFocus(editorFocus: EditorFocus) {
        val maxCursor = (editorFocus.rawText ?: currentItem?.let { itemText(it, editorFocus.kind) })?.length ?: editorFocus.cursor
        val clampedCursor = editorFocus.cursor.coerceIn(0, maxCursor)
        val focusWithSanitizedCursor = if (editorFocus.kind == DataKind.AstroTime) {
            if (clampedCursor == 2 || clampedCursor == 5) editorFocus.copy(cursor = clampedCursor + 1) else editorFocus.copy(cursor = clampedCursor)
        } else editorFocus.copy(cursor = clampedCursor)
        // Any focus change other than the one made by adding an item drops the selection
        _editorFocus.value = focusWithSanitizedCursor.copy(selectAll = false)
    }

    private fun maybePreprocess(positions: List<RoadmapInputLine>): List<RoadmapInputLine> =
        if (positions.isEmpty()) emptyList() else preprocessRoadmap(positions).toList()

    private fun moveCursorOrSwitchField(indexDelta: Int) {
        val current = _editorFocus.value

        currentItem?.let { item ->
            val currentText = fieldText(item, current.kind)
            val fields = presentFields(item)
            val currentFieldIndex = fields.indexOf(current.kind)
            if (currentFieldIndex != -1) {
                when {
                    indexDelta < 0 && current.cursor == 0 && currentFieldIndex > 0 -> {
                        val newField = fields[currentFieldIndex - 1]
                        updateFocus(current.copy(cursor = reasonableEditorStartPosition(itemText(item, newField)!!), kind = newField, rawText = null))
                    }

                    indexDelta > 0 && current.cursor == currentText?.length && currentFieldIndex < fields.size - 1 -> {
                        val newField = fields[currentFieldIndex + 1]
                        updateFocus(current.copy(cursor = reasonableEditorStartPosition(itemText(item, newField)!!), kind = newField, rawText = null))
                    }

                    current.kind == DataKind.AstroTime -> {
                        if (currentText?.getOrNull(current.cursor + indexDelta) == ':') {
                            moveCursor(indexDelta + indexDelta.sign)
                        } else {
                            moveCursor(indexDelta)
                        }
                    }

                    else -> moveCursor(indexDelta)
                }
            }
        }
    }

    override val currentItems: Collection<RoadmapInputLine>
        get() = if (_editorState.value.isEnabled) {
            _inputPositions.value
        } else {
            _preprocessedPositions.value
        }

    private fun sanitizeFocus(editorFocus: EditorFocus): EditorFocus =
        when (val item = currentItem) {
            null -> {
                EditorFocus(0, DataKind.Distance, null)
            }

            else -> {
                val newKind = if (itemText(item, editorFocus.kind) == null) DataKind.Distance else editorFocus.kind
                val text = if (editorFocus.kind == newKind && editorFocus.rawText != null) editorFocus.rawText else checkNotNull(itemText(item, newKind))
                val position =
                    if (editorFocus.kind == newKind)
                        editorFocus.cursor.coerceIn(0, text.length)
                    else reasonableEditorStartPosition(text)
                val rawText = if (editorFocus.kind == newKind) editorFocus.rawText else null
                EditorFocus(position, newKind, rawText)
            }
        }

    private fun reasonableEditorStartPosition(text: String): Int =
        if (text.contains(":")) 0 else
            if (text.endsWith(".0")) text.indexOf(".")
            else text.length

    private fun updateEditorConstraints() {
        val current = _editorState.value
        val focus = _editorFocus.value

        val items = currentItems
        val lineNumber = _selectedLineIndex.value

        val itemIndex = items.indexOfFirst { it.lineNumber == lineNumber }
        val canMoveUp = if (itemIndex != -1) itemIndex != 0 else false
        val canMoveDown = if (itemIndex != -1) itemIndex != items.size - 1 else false

        var canMoveLeft = current.canMoveLeft
        var canMoveRight = current.canMoveRight
        var canEnterDigits = current.canEnterDigits
        var maxDigit = 9
        var canEnterDot = current.canEnterDot

        currentItem?.let { item ->
            val curText = fieldText(item, focus.kind)
            canMoveLeft = focus.cursor != 0 || hasPreviousField(item, focus)
            canMoveRight = focus.cursor != curText?.length || hasNextField(item, focus)
            canEnterDigits = curText?.let { focus.cursor != it.length || it.indexOf('.') == -1 || it.substringAfterLast(".").length < 3 }
                ?: canEnterDigits

            if (focus.kind == DataKind.AstroTime) {
                item.modifier<AstroTime>()?.timeOfDay?.let { time ->
                    when (focus.cursor) {
                        0 -> maxDigit = 2
                        1 -> if (time.hr >= 20) maxDigit = 3
                        3 -> maxDigit = 5
                        6 -> maxDigit = 5
                    }
                }
            }

            canEnterDot =
                focus.kind == DataKind.Distance ||
                        focus.kind == DataKind.AverageSpeed ||
                        focus.kind == DataKind.SpeedPercentage
        }

        val canSetPercent = currentItem?.modifier<SetAvg>() != null

        _editorState.value =
            current.copy(
                canMoveDown = canMoveDown,
                canMoveUp = canMoveUp,
                canDelete = itemIndex != -1,
                canEnterDot = canEnterDot,
                canMoveLeft = canMoveLeft,
                canMoveRight = canMoveRight,
                canEnterDigits = canEnterDigits,
                maxDigit = maxDigit,
                canSetPercent = canSetPercent,
                lastEnteredPercent = _speedLimitPercent.value
            )
    }

    private fun hasPreviousField(item: PositionLine, focus: EditorFocus): Boolean =
        presentFields(item).first() != focus.kind

    private fun hasNextField(item: PositionLine, focus: EditorFocus): Boolean =
        presentFields(item).last() != focus.kind

    private fun focusOrSwitchModifier(key: GridKey) {
        val focus = _editorFocus.value
        currentItem?.let { item ->
            val modifier = when (key) {
                GridKey.SETAVG -> item.modifier<SetAvgSpeed>()
                GridKey.THENAVG -> item.modifier<ThenAvgSpeed>()
                GridKey.ENDAVG -> item.modifier<EndAvgSpeed>()
                GridKey.ATIME -> item.modifier<AstroTime>()
                GridKey.PERCENT -> item.modifier<SetAvg>()
                else -> return
            }

            fun moveFocusToModifier(line: RoadmapInputLine) {
                val focusTarget = when (key) {
                    GridKey.SETAVG, GridKey.THENAVG -> DataKind.AverageSpeed
                    GridKey.PERCENT -> DataKind.SpeedPercentage
                    GridKey.ATIME -> DataKind.AstroTime
                    else -> null
                }
                if (focusTarget != null && itemText(line, focusTarget) != null) {
                    val itemText = itemText(line, focusTarget)!!
                    updateFocus(EditorFocus(reasonableEditorStartPosition(itemText), focusTarget, null))
                } else {
                    updateFocus(sanitizeFocus(focus.copy(rawText = null)))
                }
            }

            if (modifier != null) {
                if (key == GridKey.PERCENT) {
                    val setAvg = modifier as SetAvg
                    if (setAvg.setavg is SpeedWithPercentage) {
                        if (focus.kind == DataKind.SpeedPercentage) {
                            val plainSpeed = PlainSpeedKmh(setAvg.setavg.baseSpeedKmh)
                            val newItem = addModifiersToItem(item, listOf(setAvg.copyWithSpeed(plainSpeed)))
                            val speedText = itemText(newItem, DataKind.AverageSpeed) ?: ""
                            updateFocus(EditorFocus(reasonableEditorStartPosition(speedText), DataKind.AverageSpeed, null))
                        } else {
                            moveFocusToModifier(item)
                        }
                    } else {
                        val speedWithPercent = SpeedWithPercentage(setAvg.setavg, 100.0)
                        val newItem = addModifiersToItem(item, listOf(setAvg.copyWithSpeed(speedWithPercent)))
                        updateFocus(EditorFocus(0, DataKind.SpeedPercentage, ""))
                    }
                } else {
                    val dataToRemove = when (key) {
                        GridKey.SETAVG -> listOfNotNull(DataKind.AverageSpeed, DataKind.SpeedPercentage.takeIf { it in presentFields(item) }).takeIf { focus.kind in it }
                        GridKey.THENAVG -> listOfNotNull(DataKind.AverageSpeed, DataKind.SpeedPercentage.takeIf { it in presentFields(item) }).takeIf { focus.kind in it }
                        GridKey.ENDAVG -> listOf()
                        GridKey.ATIME -> DataKind.AstroTime.takeIf(focus.kind::equals)?.let(::listOf)
                        else -> null
                    }
                    if (dataToRemove != null) {
                        val newItem = item.copy(modifiers = item.modifiers.filterNot { it == modifier })
                        updateInputPositions(currentItems.toMutableList().apply { set(indexOf(item), newItem) })
                        if (focus.kind in dataToRemove) {
                            updateFocus(sanitizeFocus(focus.copy(kind = presentFields(item).takeWhile { it !in dataToRemove }.last(), rawText = null)))
                        }
                    } else {
                        moveFocusToModifier(item)
                    }
                }
            } else {
                val existingAvg = item.modifier<SetAvg>()?.setavg
                val modifierToAdd = when (key) {
                    GridKey.SETAVG -> SetAvgSpeed(existingAvg ?: SpeedKmh(0.0))
                    GridKey.THENAVG -> ThenAvgSpeed(existingAvg ?: SpeedKmh(0.0))
                    GridKey.ATIME -> AstroTime(TimeDayHrMinSec(0, 0, 0, 0))
                    GridKey.ENDAVG -> EndAvgSpeed(null)
                    else -> null
                }
                if (modifierToAdd != null) {
                    val newItem = addModifiersToItem(item, listOf(modifierToAdd))
                    if (existingAvg == null && (key == GridKey.SETAVG || key == GridKey.THENAVG)) {
                        updateFocus(EditorFocus(0, DataKind.AverageSpeed, ""))
                    } else {
                        moveFocusToModifier(newItem)
                    }
                }
            }
        }
    }

    private fun applyLastPercent() {
        val item = currentItem ?: return
        val existingAvg = item.modifier<SetAvg>() ?: return
        val percent = _speedLimitPercent.value?.toDoubleOrNull() ?: return
        val newSpeed = SpeedWithPercentage(existingAvg.setavg.baseSpeed, percent)
        val newItem = addModifiersToItem(item, listOf(existingAvg.copyWithSpeed(newSpeed)))
        val itemText = itemText(newItem, DataKind.SpeedPercentage) ?: ""
        updateFocus(EditorFocus(reasonableEditorStartPosition(itemText), DataKind.SpeedPercentage, null))
    }

    private fun addModifiersToItem(
        item: PositionLine,
        modifiersToAdd: List<PositionLineModifier>
    ): PositionLine {
        val indexOfItem = _inputPositions.value.indexOfFirst { (it as? PositionLine)?.lineNumber == item.lineNumber }
        if (indexOfItem == -1) return item
        return updateInputPositionAt(
            _inputPositions.value[indexOfItem].lineNumber,
            item.copy(modifiers = addOrReplaceModifiers(item.modifiers, modifiersToAdd))
        )
    }

    private fun recalculateLineNumbers(lines: Collection<RoadmapInputLine>) =
        lines.mapIndexed { index, roadmapInputLine ->
            val lineNumber = LineNumber(index + 1, 0)
            when (roadmapInputLine) {
                is CommentLine -> roadmapInputLine.copy(lineNumber = lineNumber)
                is PositionLine -> roadmapInputLine.copy(lineNumber = lineNumber)
            }
        }

    private fun createNewItem(): LineNumber? {
        val items = currentItems
        val selectedIndex = items.indexOfFirst { it.lineNumber == _selectedLineIndex.value }
        if (selectedIndex != -1) {
            val newItemIndex = selectedIndex + 1
            // Positions are reordered by distance, so start from the distance of the position above to stay in place
            val distance = items.take(newItemIndex).filterIsInstance<PositionLine>().lastOrNull()?.atKm ?: DistanceKm(0.0)
            val positions = recalculateLineNumbers(
                buildList {
                    addAll(items.take(newItemIndex))
                    add(PositionLine(distance, LineNumber(1, 0), emptyList()))
                    addAll(items.drop(newItemIndex))
                }
            )
            val item = positions[newItemIndex]
            updateInputPositions(positions)
            return item.lineNumber
        }
        return null
    }

    private fun addOrReplaceModifiers(originalModifiers: List<PositionLineModifier>, newModifiers: List<PositionLineModifier>): List<PositionLineModifier> {
        val avgSpeedInNewModifiers = newModifiers.any { it is SetAvg || it is EndAvg }
        return originalModifiers.filter { original ->
            newModifiers.none { new -> new::class == original::class }

                    && (!avgSpeedInNewModifiers || (original !is SetAvg && original !is EndAvg))
        } + newModifiers
    }

    override fun maybeCreateItemAtDistance(distanceKm: DistanceKm, forceCreateIfExists: Boolean, addModifiers: List<PositionLineModifier>): PositionLine {
        if (_raceState.value is RaceUiState.HasRaceModel && _raceUiVisible.value) {
            launchDispatchable {
                if (soundFeedback) _soundFlow.emit(SoundEvent.BEEP_UP)
            }
        }

        return maybeCreateItemAtDistanceFromModel(distanceKm, forceCreateIfExists, addModifiers)
    }

    /** Where a new position at [distance] goes: after all the positions at that distance. */
    private fun insertionIndex(items: List<RoadmapInputLine>, distance: DistanceKm): Int =
        items.indexOfFirst { it is PositionLine && it.atKm > distance }.takeIf { it != -1 } ?: items.size

    private fun maybeCreateItemAtDistanceFromModel(
        distanceKm: DistanceKm,
        forceCreateIfExists: Boolean,
        addModifiers: List<PositionLineModifier>
    ): PositionLine {
        val roundedDistance = distanceKm.roundTo3Digits()

        val items = _inputPositions.value

        if (!forceCreateIfExists) {
            items.asSequence().filterIsInstance<PositionLine>().findLast { it.atKm == roundedDistance }?.let { matchingLine ->
                return addModifiersToItem(matchingLine, addModifiers)
            }
        }

        val indexToInsert = insertionIndex(items, roundedDistance)

        val shouldSelectLine = indexToInsert == items.indexOfFirst { it.lineNumber == selectedLineIndex.value } + 1

        val positionLine = PositionLine(roundedDistance, LineNumber(1, 0), addModifiers)
        val hasATime = positionLine.modifier<AstroTime>() != null

        fun maybeRemoveAtime(line: RoadmapInputLine): RoadmapInputLine = when (line) {
            is PositionLine -> if (hasATime) line.copy(modifiers = line.modifiers.filter { it !is AstroTime }) else line
            else -> line
        }

        val positions = recalculateLineNumbers(
            items.take(indexToInsert).map(::maybeRemoveAtime) + positionLine + items.drop(indexToInsert).map(::maybeRemoveAtime)
        )
        val item = positions[indexToInsert]
        updateInputPositions(positions)
        if (shouldSelectLine) {
            selectLine(item.lineNumber, null)
        }
        updateCurrentRaceLine()
        return item as PositionLine
    }

    private fun updateInputPositions(positions: List<RoadmapInputLine>) {
        _inputPositions.value = positions
        database.updateSectionPositions(sectionId, InputToTextSerializer.serializeToText(positions))
    }

    private fun updateInputPositionAt(lineNumber: LineNumber, newPositionLine: PositionLine): PositionLine {
        val newLineHasATime = newPositionLine.modifier<AstroTime>() != null

        fun maybeRemoveATime(line: RoadmapInputLine): RoadmapInputLine =
            when (line) {
                is PositionLine -> if (newLineHasATime) line.copy(modifiers = line.modifiers.filter { it !is AstroTime }) else line
                else -> line
            }

        val (newPositions, result) = _inputPositions.value.run {
            val index = indexOfFirst { it.lineNumber == lineNumber }
            val result = recalculateLineNumbers(take(index).map(::maybeRemoveATime) + newPositionLine + drop(index + 1).map(::maybeRemoveATime))
            result to result[index] as PositionLine
        }

        updateInputPositions(newPositions)

        return result
    }

    private fun SetAvg.copyWithSpeed(newSpeed: SpeedKmh): SetAvg = when (this) {
        is SetAvgSpeed -> copy(setavg = newSpeed)
        is ThenAvgSpeed -> copy(setavg = newSpeed)
    }

    private val SpeedKmh.baseSpeed: SpeedKmh
        get() = when (this) {
            is SpeedWithPercentage -> baseSpeed
            else -> this
        }

    private fun computeRawText(kind: DataKind, newText: String, canonicalText: String?): String? = when {
        (kind == DataKind.SpeedPercentage || kind == DataKind.AverageSpeed) && newText.isEmpty() -> ""
        newText != canonicalText && (newText.endsWith('.') || (newText.contains('.') && newText.endsWith('0'))) -> {
            if (newText.contains('.') && newText.substringAfter('.').length > 3) null else newText
        }
        else -> null
    }

    internal fun updateCurrentText(item: PositionLine, editorFocus: EditorFocus, newText: String, newCursor: Int = editorFocus.cursor): String {
        val modifiers = item.modifiers
        val newItem = when (editorFocus.kind) {
            DataKind.Distance -> {
                val dist = newText.toDouble3DigitsOrDefault(0.0)
                item.copy(atKm = DistanceKm(dist))
            }

            DataKind.AverageSpeed -> {
                val speed = newText.toDouble3DigitsOrDefault(0.0)
                item.copy(modifiers = modifiers.map { modifier ->
                    if (modifier is SetAvg) {
                        val newSpeed = when (val current = modifier.setavg) {
                            is SpeedWithPercentage -> current.copy(baseSpeed = PlainSpeedKmh(speed))
                            else -> PlainSpeedKmh(speed)
                        }
                        modifier.copyWithSpeed(newSpeed)
                    } else modifier
                })
            }

            DataKind.SpeedPercentage -> {
                val newPercentVal = newText.toDouble3DigitsOrDefault(100.0)
                val formattedPercent = if (newText.isEmpty() || newText == ".") null else newPercentVal.formatPercentage()
                _speedLimitPercent.value = formattedPercent
                service?.setSpeedLimitPercent(formattedPercent)
                item.copy(modifiers = modifiers.map { modifier ->
                    if (modifier is SetAvg) {
                        val newSpeed = SpeedWithPercentage(modifier.setavg.baseSpeed, newPercentVal)
                        modifier.copyWithSpeed(newSpeed)
                    } else modifier
                })
            }

            DataKind.AstroTime -> item.copy(modifiers = modifiers.map {
                run {
                    if (it is AstroTime) it.copy(timeOfDay = TimeDayHrMinSec.tryParse(newText) ?: return@run it) else it
                }
            })

        }

        val canonicalText = itemText(newItem, editorFocus.kind)
        val rawText = computeRawText(editorFocus.kind, newText, canonicalText)

        val allPositions = _inputPositions.value
        val currentPosIndex = allPositions.indexOfFirst { it.lineNumber == item.lineNumber }
        if (currentPosIndex != -1) {
            val (newPositions, newLineSelection) = if (editorFocus.kind == DataKind.Distance) {
                val remaining = allPositions.filterIndexed { index, _ -> index != currentPosIndex }
                val targetDistance = newItem.atKm
                val indexToInsert = remaining.indices.firstOrNull { i ->
                    val it = remaining[i]
                    it is PositionLine && (it.atKm > targetDistance || (it.atKm == targetDistance && i >= currentPosIndex))
                } ?: remaining.size
                val updated = recalculateLineNumbers(
                    buildList {
                        addAll(remaining.take(indexToInsert))
                        add(newItem)
                        addAll(remaining.drop(indexToInsert))
                    }
                )
                val resultingItem = updated[indexToInsert] as PositionLine
                updated to resultingItem.lineNumber
            } else {
                val allPositions = _inputPositions.value
                allPositions.toMutableList().apply { set(currentPosIndex, newItem) } to item.lineNumber
            }
            _selectedLineIndex.value = newLineSelection
            updateInputPositions(newPositions)
        }

        updateFocus(editorFocus.copy(cursor = newCursor, rawText = rawText))
        updateEditorConstraints()

        return newText
    }

    private fun sanitizeSelection() {
        val currentIndex = _selectedLineIndex.value
        if (currentItems.any { it.lineNumber == currentIndex }) {
            return
        }
        val numbers = currentItems.map { it.lineNumber }
        val next = numbers.filter { it > currentIndex }.minOrNull()
        if (next != null) {
            selectLine(next, null)
        } else {
            val prev = numbers.filter { it < currentIndex }.maxOrNull()
            if (prev != null) {
                selectLine(prev, null)
            } else {
                selectLine(LineNumber(1, 0), null)
            }
        }
    }
}

class StreamedSectionViewModel(
    private val prefs: PreferenceRepository,
    coroutineDispatcher: CoroutineDispatcher
) : StatefulSectionViewModel(coroutineDispatcher),
    RaceServiceHolder<StreamedRaceService>, PanelSwapControls {
    override val viewModelScope: CoroutineScope
        get() = (this as ViewModel).viewModelScope

    override val panelsSwapped: StateFlow<Boolean> =
        prefs.userPreferencesFlow.map { it.hudSwapPanels }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    override fun togglePanelsSwapped() {
        val newValue = !panelsSwapped.value
        viewModelScope.launch {
            prefs.saveHudSwapPanels(newValue)
        }
    }

    private val _calibration = MutableStateFlow(1.0)

    override val calibration: Flow<Double> get() = _calibration

    private val _raceCurrentLineIndex = MutableStateFlow<LineNumber?>(null)
    override val raceCurrentLineIndex: Flow<LineNumber?> get() = _raceCurrentLineIndex

    private val _raceState = MutableStateFlow<RaceUiState>(RaceUiState.RaceNotStarted)
    override val raceState: Flow<RaceUiState> get() = _raceState

    private val _speedLimitPercent = MutableStateFlow<String?>(null)
    override val speedLimitPercent: Flow<String?> get() = _speedLimitPercent

    private val _timeAllowance = MutableStateFlow<TimeAllowance?>(null)
    override val timeAllowance: Flow<TimeAllowance?> get() = _timeAllowance

    private var service: StreamedRaceService? = null

    private var serviceRelatedJob: Job? = null
    private var serviceConnector: () -> Unit = { error("no connector") }
    private var serviceDisconnector: () -> Unit = { error("no disconnector") }

    init {
        subInitComplete()
    }

    override fun setRaceServiceConnector(connector: () -> Unit) {
        serviceConnector = connector
    }

    override fun setRaceServiceDisconnector(disconnector: () -> Unit) {
        serviceDisconnector = disconnector
    }

    override fun onServiceConnected(raceService: StreamedRaceService) {
        service = raceService
        _raceUiVisible.value = true
        serviceRelatedJob?.cancel()
        serviceRelatedJob = launchDispatchable {
            launch {
                service?.telemetryPublicState?.collectLatest { teleState ->
                    _telemetryState.value = teleState
                }
            }
            launch {
                service?.section?.collectLatest {
                    _section.value = it?.let { LoadState.Loaded(it) } ?: LoadState.EMPTY
                }
            }
            launch {
                service?.positions?.collectLatest {
                    _inputPositions.value = it.orEmpty()
                    _preprocessedPositions.value = it.orEmpty()
                }
            }
            launch {
                service?.instant?.collectLatest {
                    _instant.value = it
                }
            }
            launch {
                service?.currentLine?.collectLatest {
                    _selectedLineIndex.value = it
                }
            }
            launch {
                service?.currentRaceLine?.collectLatest {
                    _raceCurrentLineIndex.value = it
                }
            }
            launch {
                service?.raceState?.collectLatest {
                    _raceState.value = raceStateToUiState(it)
                }
            }
            launch {
                service?.rememberSpeedLimit?.collectLatest {
                    _rememberSpeed.value = it
                }
            }
            launch {
                service?.speedLimitPercent?.collectLatest {
                    _speedLimitPercent.value = it
                }
            }
        }
    }

    private fun raceStateToUiState(
        raceState: RaceState
    ): RaceUiState = when (raceState) {
        is RaceState.InRace, is RaceState.Going -> {
            raceState as RaceState.HasCurrentSection
            when (raceState) {
                is RaceState.Going -> RaceUiState.Going(
                    raceState.raceModel,
                    raceState.finishedRaceModel,
                    raceState.finishedAt,
                    0L
                )

                is RaceState.InRace -> RaceUiState.RaceGoing(
                    raceState.raceModel,
                    0L,
                    raceState.previousFinishAt,
                    raceState.previousFinishModel,
                    raceState.goingModel
                )
            }
        }

        RaceState.NotStarted -> RaceUiState.RaceNotStarted
        is RaceState.Stopped -> RaceUiState.Stopped(raceState.stoppedAt, raceState.raceModelAtStop, raceState.finishedAt, raceState.finishedModel)
    }

    override fun onServiceDisconnected() {
        service = null
        _raceState.value = RaceUiState.NoRaceServiceConnection
        serviceRelatedJob?.cancel()
        _raceUiVisible.value = false
    }

    fun close() {
        service?.forceStop()
    }

    fun disconnectFromReceiver() {
        serviceDisconnector()
    }
}