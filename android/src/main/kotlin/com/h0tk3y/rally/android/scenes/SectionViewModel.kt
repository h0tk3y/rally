package com.h0tk3y.rally.android.scenes

import com.h0tk3y.rally.*
import com.h0tk3y.rally.android.LoadState
import com.h0tk3y.rally.android.PreferenceRepository
import com.h0tk3y.rally.android.db.Database
import com.h0tk3y.rally.android.views.GridKey
import com.h0tk3y.rally.db.Section
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.tlaster.precompose.viewmodel.ViewModel
import moe.tlaster.precompose.viewmodel.viewModelScope

class SectionViewModel(
    private val sectionId: Long,
    private val database: Database,
    private val prefs: PreferenceRepository
) : ViewModel(), EditorControls {
    private val parser = InputRoadmapParser(DefaultModifierValidator())

    private val _section: MutableStateFlow<LoadState<Section>> = MutableStateFlow(LoadState.LOADING)
    private val _inputPositions: MutableStateFlow<List<RoadmapInputLine>> = MutableStateFlow(emptyList())
    private val _selectedLineNumber: MutableStateFlow<LineNumber> = MutableStateFlow(LineNumber(1, 0))
    private val _preprocessedPositions: MutableStateFlow<List<RoadmapInputLine>> = MutableStateFlow(emptyList())
    private val _results: MutableStateFlow<RallyTimesResult> = MutableStateFlow(RallyTimesResultFailure(emptyList()))
    private val _editorFocus: MutableStateFlow<EditorFocus> = MutableStateFlow(EditorFocus(0, DataKind.Distance))
    private val _editorState: MutableStateFlow<EditorState> = MutableStateFlow(EditorState(false))
    private val _subsMatching: MutableStateFlow<SubsMatch> = MutableStateFlow(SubsMatch.EMPTY)

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
            _preprocessedPositions.collectLatest {
                val lines = it.filterIsInstance<PositionLine>()
                val rallyTimes = RallyTimesIntervalsCalculator().rallyTimes(lines)
                _results.value = rallyTimes
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
    val editorState = _editorState.asStateFlow()
    val editorFocus = _editorFocus.asStateFlow()
    val inputPositions = _inputPositions.asStateFlow()
    val preprocessedPositions = _preprocessedPositions.asStateFlow()
    val results = _results.asStateFlow()
    val subsMatching = _subsMatching.asStateFlow()
    val timeAllowance = prefs.userPreferencesFlow.map { it.allowance }

    override fun switchEditor() {
        val currentEditorState = _editorState.value
        val isEnabled = !currentEditorState.isEnabled
        _editorState.value = currentEditorState.copy(isEnabled = isEnabled)
        sanitizeSelection()
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
            updateFocus(newFocus)
        }

    }

    override fun moveCursor(indexDelta: Int) {
        val current = _editorFocus.value
        val next = sanitizeFocus(current.copy(cursor = current.cursor + indexDelta))
        updateFocus(next)
    }

    override fun keyPress(key: GridKey) {
        when (key) {
            GridKey.SETAVG, GridKey.THENAVG, GridKey.ENDAVG, GridKey.SYNTH -> focusOrSwitchModifier(key)
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
            GridKey.REMOVE -> currentItem?.let(::deletePosition)
        }
    }

    override fun deletePosition(line: RoadmapInputLine) {
        check(editorState.value.isEnabled)
        check(editorState.value.canDelete)

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

    fun deleteCharacter() {
        currentItem?.let { item ->
            val editorFocus = _editorFocus.value
            val currentText = itemText(item, editorFocus.kind)

            if (currentText != null) {
                val cursor = editorFocus.cursor
                when {
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

    fun enterCharacter(char: Char) {
        currentItem?.let { item ->
            val editorFocus = _editorFocus.value
            val currentText = itemText(item, editorFocus.kind)
            if (currentText != null) {
                val cursor = editorFocus.cursor
                val newText = when {
                    char.isDigit() -> currentText.take(cursor) + char + currentText.drop(cursor)
                    char == '.' -> currentText.take(cursor).replace(".", "") + char + currentText.drop(cursor)
                        .replace(".", "")

                    else -> currentText
                }
                val newCursor = when {
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

    private val currentItem: PositionLine?
        get() = currentItems.find { it.lineNumber == _selectedLineNumber.value } as? PositionLine

    private fun updateFocus(editorFocus: EditorFocus) {
        _editorFocus.value = editorFocus
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
        if (text.endsWith(".0")) text.indexOf(".") else text.length

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
        var canEnterDot = current.canEnterDot

        currentItem?.let { item ->
            canMoveLeft = focus.cursor != 0 || hasPreviousField(item, focus)
            canMoveRight = focus.cursor != itemText(item, focus.kind)?.length || hasNextField(item, focus)
            canEnterDigits = itemText(
                item,
                focus.kind
            )?.let { focus.cursor != it.length || it.indexOf('.') == -1 || it.substringAfterLast(".").length < 3 }
                ?: canEnterDigits

            canEnterDot =
                focus.kind == DataKind.Distance || 
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
                GridKey.SETAVG -> item.modifier<PositionLineModifier.SetAvgSpeed>()
                GridKey.THENAVG -> item.modifier<PositionLineModifier.ThenAvgSpeed>()
                GridKey.ENDAVG -> item.modifier<PositionLineModifier.EndAvgSpeed>()
                GridKey.SYNTH -> item.modifier<PositionLineModifier.AddSynthetic>()
                else -> return
            }

            fun moveFocusToModifier(line: RoadmapInputLine) {
                val focusTarget = when (key) {
                    GridKey.SETAVG, GridKey.THENAVG -> DataKind.AverageSpeed
                    GridKey.SYNTH -> DataKind.SyntheticCount
                    else -> null
                }
                if (focusTarget != null) {
                    val itemText = itemText(line, focusTarget)!!
                    updateFocus(EditorFocus(reasonableEditorStartPosition(itemText), focusTarget))
                } else {
                    updateFocus(sanitizeFocus(focus))
                }
            }

            val modifiers = item.modifiers
            if (modifier != null) {
                val shouldRemove = when (key) {
                    GridKey.SETAVG, GridKey.THENAVG -> focus.kind == DataKind.AverageSpeed
                    GridKey.ENDAVG -> true
                    GridKey.SYNTH -> focus.kind == DataKind.SyntheticCount || focus.kind == DataKind.SyntheticInterval
                    else -> false
                }
                if (shouldRemove) {
                    val newItem = item.copy(modifiers = modifiers.filterNot { it == modifier })
                    updateInputPositions(currentItems.toMutableList().apply { set(indexOf(item), newItem) })
                    updateFocus(sanitizeFocus(focus))
                } else {
                    moveFocusToModifier(item)
                }
            } else {
                val existingAvg = item.modifier<PositionLineModifier.SetAvg>()?.setavg
                val modifierToAdd = when (key) {
                    GridKey.SETAVG -> PositionLineModifier.SetAvgSpeed(existingAvg ?: SpeedKmh(0.0))
                    GridKey.THENAVG -> PositionLineModifier.ThenAvgSpeed(existingAvg ?: SpeedKmh(0.0))
                    GridKey.ENDAVG -> PositionLineModifier.EndAvgSpeed(null)
                    GridKey.SYNTH -> PositionLineModifier.AddSynthetic(DistanceKm(0.1), 10)
                    else -> null
                }
                if (modifierToAdd != null) {
                    val filteredModifiers =
                        if (modifierToAdd is PositionLineModifier.SetAvg || modifierToAdd is PositionLineModifier.EndAvg)
                            modifiers.filter { it !is PositionLineModifier.SetAvg && it !is PositionLineModifier.EndAvgSpeed }
                        else modifiers
                    val newItem = item.copy(modifiers = filteredModifiers + modifierToAdd)
                    updateInputPositions(currentItems.toMutableList()
                        .apply { set(indexOf(item), newItem) })
                    moveFocusToModifier(newItem)
                }
            }
        }
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

    private fun updateInputPositions(positions: List<RoadmapInputLine>) {
        _inputPositions.value = positions
        database.updateSectionPositions(sectionId, InputToTextSerializer.serializeToText(positions))
    }

    private fun sanitizeText(original: String, dataKind: DataKind) = when (dataKind) {
        DataKind.Distance, DataKind.AverageSpeed, DataKind.SyntheticInterval -> original.toDouble3Digits().toString()
        DataKind.SyntheticCount -> (original.toIntOrNull()?.coerceAtMost(1000) ?: "0").toString()
    }

    private fun updateCurrentText(item: PositionLine, editorFocus: EditorFocus, newText: String): String {
        val indexOfCurrentItem = currentItems.indexOf(item)
        val modifiers = item.modifiers
        val sanitizedText = sanitizeText(newText, editorFocus.kind)
        val newItem = when (editorFocus.kind) {
            DataKind.Distance -> item.copy(atKm = DistanceKm(sanitizedText.toDouble()))
            DataKind.AverageSpeed -> item.copy(modifiers = modifiers.map {
                when (it) {
                    is PositionLineModifier.SetAvgSpeed -> it.copy(SpeedKmh(sanitizedText.toDouble()))
                    is PositionLineModifier.ThenAvgSpeed -> it.copy(SpeedKmh(sanitizedText.toDouble()))
                    else -> it
                }
            })

            DataKind.SyntheticInterval -> item.copy(modifiers = modifiers.map {
                if (it is PositionLineModifier.AddSynthetic) it.copy(interval = DistanceKm(sanitizedText.toDouble3Digits())) else it
            })

            DataKind.SyntheticCount -> item.copy(modifiers = modifiers.map {
                if (it is PositionLineModifier.AddSynthetic) it.copy(count = sanitizedText.toInt()) else it
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
                    listOf(PositionLineModifier.SetAvgSpeed(SpeedKmh(60.0)))
                ),
                PositionLine(DistanceKm(1.0), LineNumber(2, 0), listOf(PositionLineModifier.EndAvgSpeed(null))),
            )
        } else {
            result
        }
    } else {
        emptyList()
    }
}