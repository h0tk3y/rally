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
import com.h0tk3y.rally.android.racecervice.RaceService
import com.h0tk3y.rally.android.racecervice.RaceService.BtPublicState
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
import com.h0tk3y.rally.android.views.GridKey
import com.h0tk3y.rally.android.views.StartOption
import com.h0tk3y.rally.db.Section
import com.h0tk3y.rally.isEndAvg
import com.h0tk3y.rally.isSetAvg
import com.h0tk3y.rally.isThenAvg
import com.h0tk3y.rally.model.RaceModel
import com.h0tk3y.rally.model.RaceState
import com.h0tk3y.rally.model.duration
import com.h0tk3y.rally.modifier
import com.h0tk3y.rally.preprocessRoadmap
import com.h0tk3y.rally.roundTo3Digits
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.sign

class SectionViewModel(
    private val sectionId: Long,
    private val database: Database,
    private val prefs: PreferenceRepository,
) : ViewModel(), EditorControls {
    private val parser = InputRoadmapParser(DefaultModifierValidator())

    private val _section: MutableStateFlow<LoadState<Section>> = MutableStateFlow(LoadState.LOADING)
    private val _inputPositions: MutableStateFlow<List<RoadmapInputLine>> = MutableStateFlow(emptyList())
    private val _selectedLineNumber: MutableStateFlow<LineNumber> = MutableStateFlow(LineNumber(1, 0))
    private val _raceCurrentLineNumber: MutableStateFlow<LineNumber> = MutableStateFlow(LineNumber(1, 0))
    private val _preprocessedPositions: MutableStateFlow<List<RoadmapInputLine>> = MutableStateFlow(emptyList())
    private val _results: MutableStateFlow<RallyTimesResult> = MutableStateFlow(RallyTimesResultFailure(emptyList()))
    private val _odoValues: MutableStateFlow<Map<LineNumber, DistanceKm>> = MutableStateFlow(emptyMap())
    private val _editorFocus: MutableStateFlow<EditorFocus> = MutableStateFlow(EditorFocus(0, DataKind.Distance))
    private val _editorState: MutableStateFlow<EditorState> = MutableStateFlow(EditorState(false))
    private val _subsMatching: MutableStateFlow<SubsMatch> = MutableStateFlow(SubsMatch.EMPTY)

    // Race mode:
    private val _raceUiVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val _raceState: MutableStateFlow<RaceUiState> = MutableStateFlow(RaceUiState.NoRaceServiceConnection)
    private val _btState: MutableStateFlow<BtPublicState> = MutableStateFlow(BtPublicState.NotInitialized)

    val calibration = prefs.userPreferencesFlow.map { it.calibration }

    init {
        viewModelScope.launch {
            _section.collectLatest {
                _inputPositions.value = positionLines(it)
            }
        }
        viewModelScope.launch {
            _inputPositions.collectLatest {
                _preprocessedPositions.value = maybePreprocess(it)
                sanitizeSelection()
                updateEditorConstraints()
                _subsMatching.value = SubsMatcher().matchSubs(it.filterIsInstance<PositionLine>())
            }
        }
        viewModelScope.launch {
            _selectedLineNumber.collectLatest {
                updateEditorConstraints()
            }
        }
        viewModelScope.launch {
            _editorFocus.collectLatest {
                updateEditorConstraints()
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
        viewModelScope.launch {
            database.selectSectionById(sectionId).collect {
                _section.value = it
            }
        }
    }

    val section = _section.asStateFlow()

    val selectedLineIndex = _selectedLineNumber.asStateFlow()
    val raceCurrentLineIndex =
        _raceCurrentLineNumber.combine(_raceState) { number, state -> number.takeIf { state is RaceUiState.RaceGoing || state is RaceUiState.Going } }
    val editorState = _editorState.asStateFlow()
    val editorFocus = _editorFocus.asStateFlow()
    val inputPositions = _inputPositions.asStateFlow()
    val preprocessedPositions = _preprocessedPositions.asStateFlow()
    val results = _results.asStateFlow()
    val odoValues = _odoValues.asStateFlow()
    val subsMatching = _subsMatching.asStateFlow()
    val raceState = _raceState.distinctUntilChanged { _, _ -> false }
    val raceUiVisible = _raceUiVisible.asStateFlow()
    val btState = _btState.asStateFlow()
    val timeAllowance = prefs.userPreferencesFlow.map { it.allowance }
    val btMac = prefs.userPreferencesFlow.map { it.btMac }
    val speedLimitPercent = prefs.userPreferencesFlow.map { it.speedLimitPercent }

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

    @SuppressLint("StaticFieldLeak")
    private var service: RaceService? = null

    private var serviceRelatedJob: Job? = null
    private var serviceConnector: () -> Unit = { error("no connector") }
    private var serviceDisconnector: () -> Unit = { error("no disconnector") }

    fun setRaceServiceConnector(connector: () -> Unit) {
        serviceConnector = connector
    }

    fun setRaceServiceDisconnector(disconnector: () -> Unit) {
        serviceDisconnector = disconnector
    }

    fun onServiceConnected(raceService: RaceService) {
        service = raceService

        serviceRelatedJob = viewModelScope.launch {
            launch {
                raceService.raceState.collectLatest { newState ->
                    if (newState is RaceState.MovingWithRaceModel && newState.raceSectionId == sectionId) {
                        val positionLines = _preprocessedPositions.value
                        val inRaceAtKm = newState.raceModel.currentDistance.roundTo3Digits()
                        val position = positionLines.lastOrNull { it is PositionLine && it.atKm <= inRaceAtKm }
                        if (position != null) {
                            val currentRaceLineSelected = _raceCurrentLineNumber.value == _selectedLineNumber.value
                            _raceCurrentLineNumber.value = position.lineNumber
                            if (currentRaceLineSelected && !_editorState.value.isEnabled) {
                                selectLine(position.lineNumber, null)
                            }
                        }
                    }
                    _raceState.value = raceStateToUiState(newState)
                }
            }
            launch {
                raceService.btPublicState.collectLatest {
                    _btState.value = it
                }
            }
            launch {
                calibration.collectLatest {
                    raceService.calibration = it
                }
            }
            launch {
                btMac.collectLatest {
                    raceService.setBtMac(it)
                }
            }
        }
    }

    fun onServiceDisconnected() {
        service = null
        _raceState.value = RaceUiState.NoRaceServiceConnection
        serviceRelatedJob?.cancel()
    }

    fun startRace(startOption: StartOption) {
        service?.run {
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

            val speedAtDistance: SpeedKmh? =
                currentItem?.takeIf { it.atKm == startDistance }?.modifier<SetAvg>()?.setavg
                    ?: findPureSpeedForDistance(startDistance)

            val now = Clock.System.now()

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

    private fun raceStateToUiState(raceState: RaceState): RaceUiState = when (raceState) {
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

    fun enterRaceMode() {
        _raceState.value = RaceUiState.NoRaceServiceConnection
        _raceUiVisible.value = true
        serviceConnector()
    }

    fun finishRace() {
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
        }
    }

    fun undoFinishRace() {
        val currentState = _raceState.value
        if (currentState is RaceUiState.Going && currentState.raceModelAtFinish != null) {
            val findLast = currentItems.filterIsInstance<PositionLine>().findLast {
                it.atKm == currentState.raceModelAtFinish.currentDistance.roundTo3Digits() && it.modifier<EndAvgSpeed>() != null
            }
            if (findLast != null) {
                deletePosition(findLast)
            }
            restoreStartAtime(currentState.raceModelAtFinish)
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

    fun stopRace() {
        service?.stopRace()
    }

    fun undoStopRace() {
        service?.undoStop()
    }

    fun resetRace() {
        service?.resetRace()
    }

    fun setGoingForward(isGoingForward: Boolean) {
        service?.setDistanceGoingUp(isGoingForward)
    }

    fun distanceCorrection(distanceKm: DistanceKm) {
        service?.distanceCorrection(distanceKm)
    }

    fun leaveRaceMode() {
        if (_raceState.value is RaceUiState.RaceNotStarted) {
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
        _selectedLineNumber.value = lineNumber

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

    fun deletePosition(line: RoadmapInputLine) {
        val lines = currentItems
        if (line !in lines) {
            return
        }

        val newItems = recalculateLineNumbers(lines.filter { it != line })
        updateInputPositions(newItems)
    }

    override fun selectNextItem() {
        val items = currentItems.toList()
        val currentItemIndex = items.indexOfFirst { it.lineNumber == _selectedLineNumber.value }
        if (currentItemIndex != -1 && currentItemIndex != items.lastIndex) {
            selectLine(items[currentItemIndex + 1].lineNumber, null)
        }
    }

    override fun selectPreviousItem() {
        val items = currentItems.toList()
        val currentItemIndex = items.indexOfFirst { it.lineNumber == _selectedLineNumber.value }
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

    fun setAllowance(timeAllowance: TimeAllowance?) {
        viewModelScope.launch {
            prefs.saveTimeAllowance(timeAllowance)
        }
    }

    fun setBtMac(mac: String?) {
        viewModelScope.launch {
            prefs.saveBtMac(mac)
        }
    }

    fun setSpeedLimitPercent(value: String?) {
        viewModelScope.launch {
            prefs.saveSpeedLimitPercent(value)
        }
    }

    fun setCalibration(calibration: Double) {
        viewModelScope.launch {
            prefs.saveCalibrationFactor(calibration)
        }
    }

    fun deleteCharacter() {
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
        get() = currentItems.find { it.lineNumber == _selectedLineNumber.value } as? PositionLine

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

    private val currentItems: Collection<RoadmapInputLine>
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
        val lineNumber = _selectedLineNumber.value

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
                else -> error("Unexpected line $roadmapInputLine")
            }
        }

    private fun createNewItem(positionRelativeToCurrent: Int): LineNumber? {
        val items = currentItems
        val selectedIndex = items.indexOfFirst { it.lineNumber == _selectedLineNumber.value }
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

    fun maybeCreateItemAtDistance(distanceKm: DistanceKm, forceCreateIfExists: Boolean, addModifiers: List<PositionLineModifier> = emptyList()): PositionLine {
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
        val currentIndex = _selectedLineNumber.value
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

    private fun positionLines(it: LoadState<Section>) = if (it is LoadState.Loaded) {
        val result = parser.parseRoadmap(it.value.serializedPositions.reader()).filterIsInstance<PositionLine>()
        if (result.isEmpty()) {
            listOf(
                PositionLine(
                    DistanceKm(0.0),
                    LineNumber(1, 0),
                    listOf(SetAvgSpeed(SpeedKmh(60.0)))
                ),
                PositionLine(DistanceKm(1.0), LineNumber(2, 0), listOf()),
            )
        } else {
            result
        }
    } else {
        emptyList()
    }
}