package com.h0tk3y.rally.android.scenes

import android.annotation.SuppressLint
import android.app.Service
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.h0tk3y.rally.CommentLine
import com.h0tk3y.rally.DefaultModifierValidator
import com.h0tk3y.rally.DistanceKm
import com.h0tk3y.rally.InputRoadmapParser
import com.h0tk3y.rally.InputToTextSerializer
import com.h0tk3y.rally.LineNumber
import com.h0tk3y.rally.OdoDistanceCalculator
import com.h0tk3y.rally.PositionLine
import com.h0tk3y.rally.PositionLineModifier
import com.h0tk3y.rally.PositionLineModifier.AddSynthetic
import com.h0tk3y.rally.PositionLineModifier.AstroTime
import com.h0tk3y.rally.PositionLineModifier.EndAvg
import com.h0tk3y.rally.PositionLineModifier.EndAvgSpeed
import com.h0tk3y.rally.PositionLineModifier.OdoDistance
import com.h0tk3y.rally.PositionLineModifier.SetAvg
import com.h0tk3y.rally.PositionLineModifier.SetAvgSpeed
import com.h0tk3y.rally.PositionLineModifier.ThenAvgSpeed
import com.h0tk3y.rally.RallyTimesIntervalsCalculator
import com.h0tk3y.rally.RallyTimesResult
import com.h0tk3y.rally.RallyTimesResultFailure
import com.h0tk3y.rally.RoadmapInputLine
import com.h0tk3y.rally.SpeedKmh
import com.h0tk3y.rally.SubsMatch
import com.h0tk3y.rally.SubsMatcher
import com.h0tk3y.rally.TimeDayHrMinSec
import com.h0tk3y.rally.TimeHr
import com.h0tk3y.rally.android.LoadState
import com.h0tk3y.rally.android.PreferenceRepository
import com.h0tk3y.rally.android.db.Database
import com.h0tk3y.rally.android.racecervice.CommonRaceService
import com.h0tk3y.rally.android.racecervice.LocalRaceService
import com.h0tk3y.rally.android.racecervice.TcpStreamedRaceService
import com.h0tk3y.rally.android.racecervice.TelemetryPublicState
import com.h0tk3y.rally.android.views.GridKey
import com.h0tk3y.rally.android.views.StartOption
import com.h0tk3y.rally.db.Section
import com.h0tk3y.rally.isEndAvg
import com.h0tk3y.rally.isSetAvg
import com.h0tk3y.rally.isThenAvg
import com.h0tk3y.rally.model.RaceEventKind
import com.h0tk3y.rally.model.RaceEventKind.RACE_START
import com.h0tk3y.rally.model.RaceEventKind.SECTION_START
import com.h0tk3y.rally.model.RaceModel
import com.h0tk3y.rally.model.RaceState
import com.h0tk3y.rally.model.duration
import com.h0tk3y.rally.modifier
import com.h0tk3y.rally.preprocessRoadmap
import com.h0tk3y.rally.roundTo3Digits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.CompletableFuture
import kotlin.math.sign

interface RaceServiceHolder<S : CommonRaceService> {
    fun setRaceServiceConnector(connector: () -> Unit)
    fun setRaceServiceDisconnector(disconnector: () -> Unit)
    fun onServiceConnected(raceService: S)
    fun onServiceDisconnected()
}

interface CommonSectionViewModel {
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

abstract class StatefulSectionViewModel : ViewModel(), CommonSectionViewModel {
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

    suspend fun extracted() {
        _section.collectLatest { onSectionUpdate(it) }
    }

    init {
        viewModelScope.launch {
            _subInitComplete.await()

            viewModelScope.launch {
                extracted()
            }
            viewModelScope.launch {
                _inputPositions.collectLatest {
                    _preprocessedPositions.value = maybePreprocess(it)
                    // sanitizeSelection()
                    onUpdateInputPositions()
                    _subsMatching.value = SubsMatcher().matchSubs(it.filterIsInstance<PositionLine>())
                }
            }
            viewModelScope.launch {
                _selectedLineIndex.collectLatest {
                    onLineNumberChange()
                }
            }
            viewModelScope.launch {
                combineTransform(_preprocessedPositions, calibration) { a, b -> emit(a to b) }.collectLatest { (it, calibrationFactor) ->
                    val lines = it.filterIsInstance<PositionLine>()
                    launch {
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
    fun enterRaceMode()
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
    fun setSpeedLimitPercent(value: String?)
}

interface EditableSectionViewModel : CommonSectionViewModel, EditorControls {
    val editorState: StateFlow<EditorState>
    val editorFocus: StateFlow<EditorFocus>
    fun deletePosition(line: RoadmapInputLine)
    fun maybeCreateItemAtDistance(distanceKm: DistanceKm, forceCreateIfExists: Boolean, addModifiers: List<PositionLineModifier> = emptyList()): PositionLine
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

class PersistedSectionViewModel(
    private val sectionId: Long,
    private val database: Database,
    private val prefs: PreferenceRepository,
) : StatefulSectionViewModel(), EditableSectionViewModel, RaceModelControls, RaceServiceHolder<LocalRaceService> {

    private val _raceCurrentLineNumber: MutableStateFlow<LineNumber> = MutableStateFlow(LineNumber(1, 0))
    private val _editorFocus: MutableStateFlow<EditorFocus> = MutableStateFlow(EditorFocus(0, DataKind.Distance))
    private val _editorState: MutableStateFlow<EditorState> = MutableStateFlow(EditorState(false))

    override val section: StateFlow<LoadState<Section>> = _section.asStateFlow()

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
    override val speedLimitPercent: Flow<String?> = prefs.userPreferencesFlow.map { it.speedLimitPercent }

    @SuppressLint("StaticFieldLeak")
    private var service: LocalRaceService? = null

    private var serviceRelatedJob: Job? = null
    private var serviceConnector: () -> Unit = { error("no connector") }
    private var serviceDisconnector: () -> Unit = { error("no disconnector") }


    override val calibration: Flow<Double> = prefs.userPreferencesFlow.map { it.calibration }

    override fun onSectionUpdate(section: LoadState<Section>) {
        _inputPositions.value = positionLines(section)
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

        viewModelScope.launch {
            _inputPositions.collectLatest {
                _preprocessedPositions.value = maybePreprocess(it)
                _subsMatching.value = SubsMatcher().matchSubs(it.filterIsInstance<PositionLine>())
            }
        }
        viewModelScope.launch {
            _editorFocus.collectLatest {
                updateEditorConstraints()
            }
        }
        viewModelScope.launch {
            database.selectSectionById(sectionId).collect {
                _section.value = it
            }
        }
    }

    override fun setRaceServiceConnector(connector: () -> Unit) {
        serviceConnector = connector
    }

    override fun setRaceServiceDisconnector(disconnector: () -> Unit) {
        serviceDisconnector = disconnector
    }

    private val parser = InputRoadmapParser(DefaultModifierValidator())

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

    override fun onServiceConnected(raceService: LocalRaceService) {
        service = raceService

        serviceRelatedJob = viewModelScope.launch {
            launch {
                raceService.raceState.collectLatest { newState ->
                    if (newState is RaceState.MovingWithRaceModel && newState.raceSectionId == sectionId) {
                        val positionLines = _preprocessedPositions.value
                        val inRaceAtKm = newState.raceModel.currentDistance.roundTo3Digits()
                        val position = positionLines.lastOrNull { it is PositionLine && it.atKm <= inRaceAtKm }
                        if (position != null) {
                            val currentRaceLineSelected = _raceCurrentLineNumber.value == _selectedLineIndex.value
                            _raceCurrentLineNumber.value = position.lineNumber
                            if (currentRaceLineSelected && !_editorState.value.isEnabled) {
                                selectLine(position.lineNumber, null)
                            }
                        }
                    }
                    val old = _raceState.value
                    _raceState.value = raceStateToUiState(newState)
                    handleRaceStatesDelta(old, _raceState.value)
                }
            }
            launch {
                raceService.rememberSpeedLimit.collectLatest {
                    _rememberSpeed.value = it
                }
            }
            launch {
                raceService.telemetryPublicState.collectLatest {
                    _telemetryState.value = it
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
        service?.run {
            val now = Clock.System.now()

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
                now,
                sinceTimestamp = null
            )

            val speedAtDistance: SpeedKmh? =
                rememberSpeed.value ?: currentItem?.takeIf { it.atKm == startDistance }?.modifier<SetAvg>()?.setavg
                ?: findPureSpeedForDistance(startDistance)

            val startTime = when (startOption.locationAndTime) {
                StartOption.StartAtSelectedPositionAtTimeKeepOdo,
                StartOption.StartAtSelectedPositionAtTime -> {
                    currentItem?.modifier<AstroTime>()?.timeOfDay?.let { timeOfDay ->
                        now.toLocalDateTime(TimeZone.currentSystemDefault()).date.atTime(
                            LocalTime(timeOfDay.hr, timeOfDay.min, timeOfDay.sec)
                        ).toInstant(TimeZone.currentSystemDefault())
                    } ?: now
                }

                StartOption.StartAtSelectedPositionNow,
                StartOption.StartNowFromGoingState -> now
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
                    ?.takeIf { PositionLineModifier.IsSynthetic !in it.modifiers }
                    ?.let { addModifiersToItem(it, startModifiersToAdd) }
                    ?: maybeCreateItemAtDistance(
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
        sanitizeSelection()
    }

    override fun enterRaceMode() {
        _raceState.value = RaceUiState.NoRaceServiceConnection
        _raceUiVisible.value = true
        serviceConnector()
    }

    override fun finishRace() {
        val currentState = _raceState.value
        if (currentState is RaceUiState.RaceGoing) {
            val addEndAvg = currentItems.filterIsInstance<PositionLine>().fold(0) { acc, it ->
                acc + (if (it.modifier<SetAvg>() != null) 1 else 0) + (if (it.modifier<EndAvg>() != null) -1 else 0)
            } >= 1
            maybeCreateItemAtDistance(
                currentState.raceModel.currentDistance.roundTo3Digits(),
                true,
                if (addEndAvg) listOf(EndAvgSpeed(null)) else emptyList()
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

    override fun undoFinishRace() {
        val currentState = _raceState.value
        if (currentState is RaceUiState.Going && currentState.raceModelAtFinish != null) {
            val findLast = currentItems.filterIsInstance<PositionLine>().findLast {
                it.atKm == currentState.raceModelAtFinish.currentDistance.roundTo3Digits() && it.modifier<EndAvgSpeed>() != null
            }
            if (findLast != null) {
                deletePosition(findLast)
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
        maybeCreateItemAtDistance(
            startDistance,
            forceCreateIfExists = false,
            listOf(AstroTime(TimeDayHrMinSec.of(startTime.toLocalDateTime(TimeZone.currentSystemDefault()).time)))
        )
    }

    override fun stopRace() {
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
    }

    override fun distanceCorrection(distanceKm: DistanceKm) {
        service?.distanceCorrection(distanceKm)
    }

    override fun setRememberSpeed(speedKmh: SpeedKmh?) {
        service?.setRememberSpeedLimit(speedKmh)
    }

    override fun leaveRaceMode(forceStop: Boolean) {
        if (forceStop || _raceState.value is RaceUiState.RaceNotStarted) {
            service?.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            service?.stopSelf()
        }
        serviceDisconnector()
        _raceUiVisible.value = false
        _raceState.value = RaceUiState.NoRaceServiceConnection
    }

    override fun selectLine(index: LineNumber, field: DataKind?) {
        val items = currentItems

        val item = items.find { it.lineNumber == index } ?: items.find { it.lineNumber.number == index.subNumber }
        val lineNumber = item?.lineNumber ?: LineNumber(1, 0)
        _selectedLineIndex.value = lineNumber

        updateEditorConstraints()

        if (item != null) {
            val currentFocus = _editorFocus.value
            val newKind = field ?: if (itemText(
                    item,
                    currentFocus.kind
                ) != null
            ) currentFocus.kind else DataKind.Distance
            val newFocus = currentFocus.copy(
                cursor = reasonableEditorStartPosition(itemText(item, newKind).orEmpty()),
                kind = newKind
            )
            updateFocus(sanitizeFocus(newFocus))
        }

    }

    override fun moveCursor(indexDelta: Int) {
        val current = _editorFocus.value
        val next = sanitizeFocus(current.copy(cursor = current.cursor + indexDelta))
        updateFocus(next)
    }

    override fun keyPress(key: GridKey) {
        when (key) {
            GridKey.SETAVG, GridKey.THENAVG, GridKey.ENDAVG, GridKey.SYNTH, GridKey.ATIME, GridKey.ODO -> focusOrSwitchModifier(key)
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
            GridKey.ADD_BELOW -> addItemBelow()
            GridKey.ADD_ABOVE -> addItemAbove()
            GridKey.REMOVE -> currentItem?.let(::deletePositionViaEditor)
        }
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

    override fun addItemAbove() {
        createNewItem(0)?.let { viewModelScope.launch { selectLine(it, DataKind.Distance) } }
    }

    override fun addItemBelow() {
        createNewItem(1)?.let { viewModelScope.launch { selectLine(it, DataKind.Distance) } }
    }

    override fun setSpeedLimitPercent(value: String?) {
        viewModelScope.launch {
            prefs.saveSpeedLimitPercent(value)
        }
    }

    override fun deleteCharacter() {
        currentItem?.let { item ->
            val editorFocus = _editorFocus.value
            val currentText = itemText(item, editorFocus.kind)

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
                                currentText.take(takeChars) + '0' + (if (isAfterColon) ":" else "") + currentText.drop(dropChars)
                            )
                        }
                        moveCursor(if (currentText.getOrNull(cursor - 1) == ':') -2 else -1)
                    }

                    currentText.indexOf('.') == 1 && cursor == 1 -> {
                        val newText = "0" + currentText.drop(1)
                        updateCurrentText(item, editorFocus, newText)
                    }

                    // TODO: this is non-intuitive
                    currentText.getOrNull(cursor - 1) == '.' -> {
                        moveCursor(-1)
                    }

                    else -> {
                        val newText = if (editorFocus.cursor > 0) {
                            currentText.take(cursor - 1) + currentText.drop(cursor)
                        } else currentText
                        if (newText != currentText) {
                            val realText = updateCurrentText(item, editorFocus, newText)
                            if (realText != "0") {
                                moveCursor(-1)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun enterCharacter(char: Char) {
        currentItem?.let { item ->
            val editorFocus = _editorFocus.value
            val currentText = itemText(item, editorFocus.kind)
            if (currentText != null) {
                val cursor = editorFocus.cursor
                val newText = when {
                    // ATIME:
                    editorFocus.kind == DataKind.AstroTime -> fixTimeAfterEntered(currentText.take(cursor) + char + currentText.drop(cursor + 1))

                    // Others:
                    char.isDigit() -> currentText.take(cursor) + char + currentText.drop(cursor)
                    char == '.' -> currentText.take(cursor).replace(".", "") + char + currentText.drop(cursor)
                        .replace(".", "")

                    else -> currentText
                }
                val newCursor = when {
                    // ATIME:
                    char.isDigit() && currentText.getOrNull(cursor + 1) == ':' -> cursor + 2
                    editorFocus.kind == DataKind.AstroTime -> cursor + 1

                    // Others:
                    char.isDigit() && cursor == 1 && currentText.startsWith("0.") -> cursor
                    char.isDigit() && currentText.indexOf('.')
                        .let { it != -1 && cursor < it } && currentText.startsWith("0") ->
                        cursor

                    char == '.' && currentText.getOrNull(cursor) == '.' -> cursor + 1
                    char == '.' && currentText.indexOf('.').let { it != -1 && cursor < it } -> cursor + 1
                    char == '.' && currentText.indexOf('.').let { it != -1 && cursor > it } -> cursor
                    else -> if (sanitizeText(newText, editorFocus.kind) != currentText) cursor + 1 else cursor
                }
                updateCurrentText(item, editorFocus, newText)
                if (newCursor != cursor) {
                    moveCursor(newCursor - cursor)
                }
            }
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
        val focusWithSanitizedCursor = if (editorFocus.kind == DataKind.AstroTime) {
            if (editorFocus.cursor == 2 || editorFocus.cursor == 5) editorFocus.copy(cursor = editorFocus.cursor + 1) else editorFocus
        } else editorFocus
        _editorFocus.value = focusWithSanitizedCursor
    }

    private fun maybePreprocess(positions: List<RoadmapInputLine>): List<RoadmapInputLine> =
        if (positions.isEmpty()) emptyList() else preprocessRoadmap(positions).toList()

    private fun moveCursorOrSwitchField(indexDelta: Int) {
        val current = _editorFocus.value

        currentItem?.let { item ->
            val currentText = itemText(item, current.kind)
            val fields = presentFields(item)
            val currentFieldIndex = fields.indexOf(current.kind)
            if (currentFieldIndex != -1) {
                when {
                    indexDelta < 0 && current.cursor == 0 && currentFieldIndex > 0 -> {
                        val newField = fields[currentFieldIndex - 1]
                        updateFocus(current.copy(reasonableEditorStartPosition(itemText(item, newField)!!), newField))
                    }

                    indexDelta > 0 && current.cursor == currentText?.length && currentFieldIndex < fields.size -> {
                        val newField = fields[currentFieldIndex + 1]
                        updateFocus(current.copy(reasonableEditorStartPosition(itemText(item, newField)!!), newField))
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
                EditorFocus(0, DataKind.Distance)
            }

            else -> {
                val newKind = if (itemText(item, editorFocus.kind) == null) DataKind.Distance else editorFocus.kind
                val text = checkNotNull(itemText(item, newKind))
                val position =
                    if (editorFocus.kind == newKind)
                        editorFocus.cursor.coerceIn(0, text.length)
                    else reasonableEditorStartPosition(text)
                EditorFocus(position, newKind)
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
            canMoveLeft = focus.cursor != 0 || hasPreviousField(item, focus)
            canMoveRight = focus.cursor != itemText(item, focus.kind)?.length || hasNextField(item, focus)
            canEnterDigits = itemText(
                item,
                focus.kind
            )?.let { focus.cursor != it.length || it.indexOf('.') == -1 || it.substringAfterLast(".").length < 3 }
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
                        focus.kind == DataKind.OdoDistance ||
                        focus.kind == DataKind.AverageSpeed ||
                        focus.kind == DataKind.SyntheticInterval
        }

        _editorState.value =
            current.copy(
                canMoveDown = canMoveDown,
                canMoveUp = canMoveUp,
                canDelete = itemIndex != -1,
                canEnterDot = canEnterDot,
                canMoveLeft = canMoveLeft,
                canMoveRight = canMoveRight,
                canEnterDigits = canEnterDigits,
                maxDigit = maxDigit
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
                GridKey.SYNTH -> item.modifier<AddSynthetic>()
                GridKey.ATIME -> item.modifier<AstroTime>()
                GridKey.ODO -> item.modifier<OdoDistance>()
                else -> return
            }

            fun moveFocusToModifier(line: RoadmapInputLine) {
                val focusTarget = when (key) {
                    GridKey.SETAVG, GridKey.THENAVG -> DataKind.AverageSpeed
                    GridKey.SYNTH -> DataKind.SyntheticCount
                    GridKey.ATIME -> DataKind.AstroTime
                    GridKey.ODO -> DataKind.OdoDistance
                    else -> null
                }
                if (focusTarget != null) {
                    val itemText = itemText(line, focusTarget)!!
                    updateFocus(EditorFocus(reasonableEditorStartPosition(itemText), focusTarget))
                } else {
                    updateFocus(sanitizeFocus(focus))
                }
            }

            if (modifier != null) {
                val dataToRemove = when (key) {
                    GridKey.SETAVG -> DataKind.AverageSpeed.takeIf(focus.kind::equals)?.let(::listOf)
                    GridKey.THENAVG -> DataKind.AverageSpeed.takeIf(focus.kind::equals)?.let(::listOf)
                    GridKey.ENDAVG -> listOf()
                    GridKey.SYNTH -> listOf(DataKind.SyntheticInterval, DataKind.SyntheticCount).takeIf { focus.kind in it }
                    GridKey.ATIME -> DataKind.AstroTime.takeIf(focus.kind::equals)?.let(::listOf)
                    GridKey.ODO -> DataKind.OdoDistance.takeIf(focus.kind::equals)?.let(::listOf)
                    else -> null
                }
                if (dataToRemove != null) {
                    val newItem = item.copy(modifiers = item.modifiers.filterNot { it == modifier })
                    updateInputPositions(currentItems.toMutableList().apply { set(indexOf(item), newItem) })
                    if (focus.kind in dataToRemove) {
                        updateFocus(sanitizeFocus(focus.copy(kind = presentFields(item).takeWhile { it !in dataToRemove }.last())))
                    }
                } else {
                    moveFocusToModifier(item)
                }
            } else {
                val existingAvg = item.modifier<SetAvg>()?.setavg
                val modifierToAdd = when (key) {
                    GridKey.ODO -> OdoDistance(item.atKm)
                    GridKey.SETAVG -> SetAvgSpeed(existingAvg ?: SpeedKmh(0.0))
                    GridKey.THENAVG -> ThenAvgSpeed(existingAvg ?: SpeedKmh(0.0))
                    GridKey.ATIME -> AstroTime(TimeDayHrMinSec(0, 0, 0, 0))

                    GridKey.ENDAVG -> EndAvgSpeed(null)
                    GridKey.SYNTH -> AddSynthetic(DistanceKm(0.1), 10)
                    else -> null
                }
                if (modifierToAdd != null) {
                    val newItem = addModifiersToItem(item, listOf(modifierToAdd))
                    moveFocusToModifier(newItem)
                }
            }
        }
    }

    private fun addModifiersToItem(
        item: PositionLine,
        modifiersToAdd: List<PositionLineModifier>
    ): PositionLine {
        val indexOfItem = _inputPositions.value.indexOf(item)
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

    private fun createNewItem(positionRelativeToCurrent: Int): LineNumber? {
        val items = currentItems
        val selectedIndex = items.indexOfFirst { it.lineNumber == _selectedLineIndex.value }
        if (selectedIndex != -1) {
            val positions = recalculateLineNumbers(
                items.take(selectedIndex + positionRelativeToCurrent) +
                        PositionLine(DistanceKm(0.0), LineNumber(1, 0), emptyList()) +
                        items.drop(selectedIndex + positionRelativeToCurrent)
            )
            val item = positions[selectedIndex + positionRelativeToCurrent]
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
        val roundedDistance = distanceKm.roundTo3Digits()

        val items = _inputPositions.value

        if (!forceCreateIfExists) {
            items.asSequence().filterIsInstance<PositionLine>().findLast { it.atKm == roundedDistance }?.let { matchingLine ->
                return addModifiersToItem(matchingLine, addModifiers)
            }
        }

        val indexToInsert = items.indexOfFirst { it is PositionLine && it.atKm > roundedDistance }.takeIf { it != -1 } ?: items.size
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

    private fun sanitizeText(original: String, dataKind: DataKind) = when (dataKind) {
        DataKind.Distance, DataKind.OdoDistance, DataKind.AverageSpeed, DataKind.SyntheticInterval -> original.toDouble3Digits().toString()

        DataKind.SyntheticCount -> (original.toIntOrNull()?.coerceAtMost(1000) ?: "0").toString()
        DataKind.AstroTime -> original
    }

    private fun updateCurrentText(item: PositionLine, editorFocus: EditorFocus, newText: String): String {
        val indexOfCurrentItem = currentItems.indexOf(item)
        val modifiers = item.modifiers
        val sanitizedText = sanitizeText(newText, editorFocus.kind)
        val newItem = when (editorFocus.kind) {
            DataKind.Distance -> item.copy(atKm = DistanceKm(sanitizedText.toDouble()))
            DataKind.OdoDistance -> item.copy(modifiers = modifiers.map {
                if (it is OdoDistance) it.copy(distanceKm = DistanceKm(sanitizedText.toDouble3Digits())) else it
            })

            DataKind.AverageSpeed -> item.copy(modifiers = modifiers.map {
                when (it) {
                    is SetAvgSpeed -> it.copy(SpeedKmh(sanitizedText.toDouble()))
                    is ThenAvgSpeed -> it.copy(SpeedKmh(sanitizedText.toDouble()))
                    else -> it
                }
            })

            DataKind.SyntheticInterval -> item.copy(modifiers = modifiers.map {
                if (it is AddSynthetic) it.copy(interval = DistanceKm(sanitizedText.toDouble3Digits())) else it
            })

            DataKind.SyntheticCount -> item.copy(modifiers = modifiers.map {
                if (it is AddSynthetic) it.copy(count = sanitizedText.toInt()) else it
            })

            DataKind.AstroTime -> item.copy(modifiers = modifiers.map {
                run {
                    if (it is AstroTime) it.copy(timeOfDay = TimeDayHrMinSec.tryParse(newText) ?: return@run it) else it
                }
            })

        }
        updateInputPositions(currentItems.toMutableList().apply { set(indexOfCurrentItem, newItem) })
        return sanitizedText
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

class StreamedSectionViewModel : StatefulSectionViewModel(), RaceServiceHolder<TcpStreamedRaceService> {
    override val viewModelScope: CoroutineScope
        get() = (this as ViewModel).viewModelScope

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
    
    @SuppressLint("StaticFieldLeak")
    private var service: TcpStreamedRaceService? = null

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

    override fun onServiceConnected(raceService: TcpStreamedRaceService) {
        service = raceService
        _raceUiVisible.value = true
        serviceRelatedJob = viewModelScope.launch {
            viewModelScope.launch {
                service?.telemetryPublicState?.collectLatest { teleState ->
                    _telemetryState.value = teleState
                }
            }
            viewModelScope.launch {
                service?.section?.collectLatest {
                    _section.value = it?.let { LoadState.Loaded(it) } ?: LoadState.EMPTY
                }
            }
            viewModelScope.launch {
                service?.positions?.collectLatest {
                    _inputPositions.value = it.orEmpty()
                    _preprocessedPositions.value = it.orEmpty()
                }
            }
            viewModelScope.launch {
                service?.currentLine?.collectLatest {
                    _selectedLineIndex.value = it
                }
            }
            viewModelScope.launch {
                service?.currentRaceLine?.collectLatest {
                    _raceCurrentLineIndex.value = it
                }
            }
            viewModelScope.launch {
                service?.raceState?.collectLatest {
                    _raceState.value = raceStateToUiState(it)
                }
                delay(1000L)
                if (isActive) {
                    _telemetryState.value = TelemetryPublicState.ReceivesStream(isDelayed = true)
                }
            }
            viewModelScope.launch {
                service?.rememberSpeedLimit?.collectLatest {
                    _rememberSpeed.value = it
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

    fun dispose() {
        service?.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        service?.stopSelf()
        serviceDisconnector()
        _raceUiVisible.value = false
        _raceState.value = RaceUiState.NoRaceServiceConnection
    }
}