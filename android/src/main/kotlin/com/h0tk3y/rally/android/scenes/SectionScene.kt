package com.h0tk3y.rally.android.scenes

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.h0tk3y.rally.*
import com.h0tk3y.rally.PositionLineModifier.*
import com.h0tk3y.rally.android.LoadState
import com.h0tk3y.rally.android.db.Database
import com.h0tk3y.rally.android.db.SectionInsertOrRenameResult
import com.h0tk3y.rally.android.scenes.DataKind.*
import com.h0tk3y.rally.android.views.GridKey
import com.h0tk3y.rally.android.views.GridKey.*
import com.h0tk3y.rally.android.views.Keyboard
import com.h0tk3y.rally.android.views.PositionsListView
import com.h0tk3y.rally.db.Section
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import moe.tlaster.precompose.viewmodel.ViewModel
import moe.tlaster.precompose.viewmodel.viewModel
import moe.tlaster.precompose.viewmodel.viewModelScope

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SectionScene(
    sectionId: Long,
    database: Database,
    onDeleteSection: () -> Unit,
    onNavigateToNewSection: (Long) -> Unit,
    onBack: () -> Unit
) {
    val model = viewModel(SectionViewModel::class) { SectionViewModel(sectionId, database) }
    val section by model.section.collectAsState(LoadState.LOADING)
    val positions by model.inputPositions.collectAsState(emptyList())
    val selectedLineIndex by model.selectedLineIndex.collectAsState(LineNumber(1, 0))
    val preprocessed by model.preprocessedPositions.collectAsState(emptyList())
    val results by model.results.collectAsState(RallyTimesResultFailure(emptyList()))
    val editorState by model.editorState.collectAsState(
        EditorState(false, true, true, true, true, true, true, true)
    )
    val editorFocus by model.editorFocus.collectAsState()

    var showDuplicateDialog by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }

    var showMenu by rememberSaveable { mutableStateOf(false) }

    val currentSection = section
    if (currentSection is LoadState.Loaded) {
        if (showDuplicateDialog) {
            CreateOrRenameSectionDialog(
                DialogKind.DUPLICATE,
                currentSection.value,
                { showDuplicateDialog = false },
                { newName ->
                    val serializedPositions = InputToTextSerializer.serializeToText(positions)
                    when (val result = database.createSection(newName, serializedPositions)) {
                        is SectionInsertOrRenameResult.AlreadyExists -> {
                            ItemSaveResult.AlreadyExists
                        }

                        is SectionInsertOrRenameResult.Success -> {
                            showDuplicateDialog = false
                            onNavigateToNewSection(result.section.id)
                            ItemSaveResult.Ok(result.section)
                        }
                    }
                })
        }

        if (showRenameDialog) {
            CreateOrRenameSectionDialog(
                DialogKind.RENAME,
                currentSection.value,
                { showRenameDialog = false },
                { newName -> 
                    when (val result = database.renameSection(sectionId, newName)) {
                        is SectionInsertOrRenameResult.AlreadyExists -> ItemSaveResult.AlreadyExists
                        is SectionInsertOrRenameResult.Success -> {
                            showRenameDialog = false
                            ItemSaveResult.Ok(result.section)
                        }
                    }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                backgroundColor = MaterialTheme.colors.surface,
                title = {
                    Text(
                        when (val currentSection = section) {
                            is LoadState.Loaded -> currentSection.value.name
                            is LoadState.LOADING -> "Loading"
                            LoadState.EMPTY -> ""
                            LoadState.FAILED -> "Failed to load section"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    var inDeleteConfirmation by remember { mutableStateOf(false) }

                    Button(onClick = { model.switchEditor() }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (!editorState.isEnabled) Icons.Rounded.Edit else Icons.Rounded.Done,
                                "Switch editor"
                            )
                            Text(if (editorState.isEnabled) "Calculate" else "Edit")
                        }
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Show menu")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (!inDeleteConfirmation) {
                            DropdownMenuItem(onClick = {
                                inDeleteConfirmation = true
                            }) {
                                Icon(Icons.Default.Delete, "Delete section")
                                Spacer(Modifier.width(8.dp))
                                Text("Delete this section")
                            }
                        } else {
                            DropdownMenuItem(onClick = {
                                inDeleteConfirmation = false
                                onDeleteSection()
                            }) {
                                Icon(Icons.Default.Delete, "Tap again to delete")
                                Spacer(Modifier.width(8.dp))
                                Text("Tap again to delete")
                            }
                        }
                        if (currentSection is LoadState.Loaded) {
                            DropdownMenuItem(onClick = {
                                showMenu = false
                                showDuplicateDialog = true
                            }) {
                                Icon(Icons.Default.Add, "Duplicate this section")
                                Spacer(Modifier.width(8.dp))
                                Text("Duplicate this section")
                            }

                            DropdownMenuItem(onClick = {
                                showMenu = false
                                showRenameDialog = true
                            }) {
                                Icon(Icons.Default.Edit, "Rename this section")
                                Spacer(Modifier.width(8.dp))
                                Text("Rename this section")
                            }
                        }
                    }
                }
            )
        },
        content = { padding ->
            @Composable
            fun layout(content: @Composable (listModifier: Modifier, keyboardModifier: Modifier) -> Unit) {
                if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    Row(Modifier.padding(padding)) {
                        val listModifier = Modifier.fillMaxHeight().weight(1.0f)
                        val keyboardModifier = Modifier.fillMaxHeight().weight(1.0f)
                        content(listModifier, keyboardModifier)
                    }
                } else {
                    Column(Modifier.padding(padding)) {
                        val listModifier = Modifier.fillMaxWidth().weight(1.0f)
                        val keyboardModifier = Modifier.fillMaxWidth()
                        content(listModifier, keyboardModifier)
                    }
                }
            }
            layout { listModifier, keyboardModifier ->
                Box(listModifier) {
                    when (section) {
                        is LoadState.Loaded -> {
                            val hasErrors = results is RallyTimesResultFailure
                            val positionsToDisplay =
                                if (editorState.isEnabled) positions else preprocessed.filter {
                                    !hasErrors || it !is PositionLine || it.modifier<IsSynthetic>() == null
                                }
                            val listState = rememberLazyListState()
                            PositionsListView(
                                listState,
                                positionsToDisplay,
                                selectedLineIndex,
                                model,
                                editorState,
                                editorFocus,
                                results
                            )
                        }

                        LoadState.LOADING -> CenterTextBox("Loading sections...")
                        LoadState.EMPTY -> CenterTextBox("The section does not exist")
                        LoadState.FAILED -> CenterTextBox("Something went wrong")
                    }
                }
                if (editorState.isEnabled) {
                    Keyboard(editorState, model, keyboardModifier)
                }
            }
        }
    )
}

interface EditorControls {
    fun switchEditor()
    fun selectLine(index: LineNumber, field: DataKind?)
    fun moveCursor(indexDelta: Int)
    fun keyPress(key: GridKey)
    fun deletePosition(line: RoadmapInputLine)
    fun selectNextItem()
    fun selectPreviousItem()
    fun addItemAbove()
    fun addItemBelow()
}

data class EditorState(
    val isEnabled: Boolean = true,
    val canEnterDot: Boolean = true,
    val canEnterDigits: Boolean = true,
    val canMoveUp: Boolean = true,
    val canMoveDown: Boolean = true,
    val canMoveLeft: Boolean = true,
    val canMoveRight: Boolean = true,
    val canDelete: Boolean = true,
)

data class EditorFocus(
    val cursor: Int,
    val kind: DataKind,
)

enum class DataKind {
    Distance, AverageSpeed, SyntheticCount, SyntheticInterval,
}

class SectionViewModel(
    private val sectionId: Long,
    private val database: Database
) : ViewModel(), EditorControls {
    private val parser = InputRoadmapParser(DefaultModifierValidator())

    private val _section: MutableStateFlow<LoadState<Section>> = MutableStateFlow(LoadState.LOADING)
    private val _inputPositions: MutableStateFlow<List<RoadmapInputLine>> = MutableStateFlow(emptyList())
    private val _selectedLineNumber: MutableStateFlow<LineNumber> = MutableStateFlow(LineNumber(1, 0))
    private val _preprocessedPositions: MutableStateFlow<List<RoadmapInputLine>> = MutableStateFlow(emptyList())
    private val _results: MutableStateFlow<RallyTimesResult> = MutableStateFlow(RallyTimesResultFailure(emptyList()))
    private val _editorFocus: MutableStateFlow<EditorFocus> = MutableStateFlow(EditorFocus(0, Distance))
    private val _editorState: MutableStateFlow<EditorState> = MutableStateFlow(EditorState(false))

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
                _results.value = RallyTimesIntervalsCalculator().rallyTimes(it.filterIsInstance<PositionLine>())
            }
        }
        viewModelScope.launch {
            database.selectSectionById(sectionId).collect {
                _section.value = it
            }
        }
    }

    private fun maybePreprocess(positions: List<RoadmapInputLine>): List<RoadmapInputLine> =
        if (positions.isEmpty()) emptyList() else preprocessRoadmap(positions).toList()

    val section = _section.asStateFlow()
    val selectedLineIndex = _selectedLineNumber.asStateFlow()
    val editorState = _editorState.asStateFlow()
    val editorFocus = _editorFocus.asStateFlow()
    val inputPositions = _inputPositions.asStateFlow()
    val preprocessedPositions = _preprocessedPositions.asStateFlow()
    val results = _results.asStateFlow()

    override fun switchEditor() {
        val currentEditorState = _editorState.value
        val isEnabled = !currentEditorState.isEnabled
        _editorState.value = currentEditorState.copy(isEnabled = isEnabled)
        sanitizeSelection()
    }

    private val currentItems: Collection<RoadmapInputLine>
        get() = if (_editorState.value.isEnabled) {
            _inputPositions.value
        } else {
            _preprocessedPositions.value
        }

    private val currentItem: PositionLine?
        get() = currentItems.find { it.lineNumber == _selectedLineNumber.value } as? PositionLine

    override fun selectLine(index: LineNumber, field: DataKind?) {
        val items = currentItems

        val item = items.find { it.lineNumber == index } ?: items.find { it.lineNumber.number == index.subNumber }
        val lineNumber = item?.lineNumber ?: LineNumber(1, 0)
        _selectedLineNumber.value = lineNumber

        updateEditorConstraints()

        if (item != null) {
            val currentFocus = _editorFocus.value
            val newKind = field ?: if (itemText(item, currentFocus.kind) != null) currentFocus.kind else Distance
            val newFocus = currentFocus.copy(
                cursor = reasonableEditorStartPosition(itemText(item, newKind).orEmpty()),
                kind = newKind
            )
            updateFocus(newFocus)
        }

    }

    private fun updateFocus(editorFocus: EditorFocus) {
        _editorFocus.value = editorFocus
    }

    override fun moveCursor(indexDelta: Int) {
        val current = _editorFocus.value
        val next = sanitizeFocus(current.copy(cursor = current.cursor + indexDelta))
        updateFocus(next)
    }

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

    private fun sanitizeFocus(editorFocus: EditorFocus): EditorFocus =
        when (val item = currentItem) {
            null -> {
                EditorFocus(0, Distance)
            }

            else -> {
                val newKind = if (itemText(item, editorFocus.kind) == null) Distance else editorFocus.kind
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

            canEnterDot = focus.kind == Distance || focus.kind == AverageSpeed
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

    override fun keyPress(key: GridKey) {
        when (key) {
            SETAVG, THENAVG, ENDAVG, SYNTH -> focusOrSwitchModifier(key)
            N_1, N_2, N_3, N_4, N_5, N_6, N_7, N_8, N_9, N_0 -> enterCharacter(key.name.last())
            DOT -> if (_editorState.value.canEnterDot) enterCharacter('.')
            NOP -> Unit
            UP -> selectPreviousItem()
            DOWN -> selectNextItem()
            DEL -> deleteCharacter()
            LEFT -> moveCursorOrSwitchField(-1)
            RIGHT -> moveCursorOrSwitchField(1)
            ADD_BELOW -> addItemBelow()
            ADD_ABOVE -> addItemAbove()
            REMOVE -> currentItem?.let(::deletePosition)
        }
    }

    private fun focusOrSwitchModifier(key: GridKey) {
        val focus = _editorFocus.value
        currentItem?.let { item ->
            val modifier = when (key) {
                SETAVG -> item.modifier<SetAvgSpeed>()
                THENAVG -> item.modifier<ThenAvgSpeed>()
                ENDAVG -> item.modifier<EndAvgSpeed>()
                SYNTH -> item.modifier<AddSynthetic>()
                else -> return
            }

            fun moveFocusToModifier(line: RoadmapInputLine) {
                val focusTarget = when (key) {
                    SETAVG, THENAVG -> AverageSpeed
                    SYNTH -> SyntheticCount
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
                    SETAVG, THENAVG -> focus.kind == AverageSpeed
                    ENDAVG -> true
                    SYNTH -> focus.kind == SyntheticCount || focus.kind == SyntheticInterval
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
                val existingAvg = item.modifier<SetAvg>()?.setavg
                val modifierToAdd = when (key) {
                    SETAVG -> SetAvgSpeed(existingAvg ?: SpeedKmh(0.0))
                    THENAVG -> ThenAvgSpeed(existingAvg ?: SpeedKmh(0.0))
                    ENDAVG -> EndAvgSpeed(null)
                    SYNTH -> AddSynthetic(DistanceKm(0.1), 10)
                    else -> null
                }
                if (modifierToAdd != null) {
                    val filteredModifiers = if (modifierToAdd is SetAvg || modifierToAdd is EndAvg)
                        modifiers.filter { it !is SetAvg && it !is EndAvgSpeed }
                    else modifiers
                    val newItem = item.copy(modifiers = filteredModifiers + modifierToAdd)
                    updateInputPositions(currentItems.toMutableList()
                        .apply { set(indexOf(item), newItem) })
                    moveFocusToModifier(newItem)
                }
            }
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

    private fun recalculateLineNumbers(lines: Collection<RoadmapInputLine>) =
        lines.mapIndexed { index, roadmapInputLine ->
            val lineNumber = LineNumber(index + 1, 0)
            when (roadmapInputLine) {
                is CommentLine -> roadmapInputLine.copy(lineNumber = lineNumber)
                is PositionLine -> roadmapInputLine.copy(lineNumber = lineNumber)
                else -> error("Unexpected line $roadmapInputLine")
            }
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
        createNewItem(0)?.let { viewModelScope.launch { selectLine(it, Distance) } }
    }

    override fun addItemBelow() {
        createNewItem(1)?.let { viewModelScope.launch { selectLine(it, Distance) } }
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

    private fun sanitizeText(original: String, dataKind: DataKind) = when (dataKind) {
        Distance, AverageSpeed, SyntheticInterval -> original.toDouble3Digits().toString()
        SyntheticCount -> (original.toIntOrNull()?.coerceAtMost(1000) ?: "0").toString()
    }

    private fun updateCurrentText(item: PositionLine, editorFocus: EditorFocus, newText: String): String {
        val indexOfCurrentItem = currentItems.indexOf(item)
        val modifiers = item.modifiers
        val sanitizedText = sanitizeText(newText, editorFocus.kind)
        val newItem = when (editorFocus.kind) {
            Distance -> item.copy(atKm = DistanceKm(sanitizedText.toDouble()))
            AverageSpeed -> item.copy(modifiers = modifiers.map {
                when (it) {
                    is SetAvgSpeed -> it.copy(SpeedKmh(sanitizedText.toDouble()))
                    is ThenAvgSpeed -> it.copy(SpeedKmh(sanitizedText.toDouble()))
                    else -> it
                }
            })

            SyntheticInterval -> item.copy(modifiers = modifiers.map {
                if (it is AddSynthetic) it.copy(interval = DistanceKm(sanitizedText.toDouble3Digits())) else it
            })

            SyntheticCount -> item.copy(modifiers = modifiers.map {
                if (it is AddSynthetic) it.copy(count = sanitizedText.toInt()) else it
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
                PositionLine(DistanceKm(0.0), LineNumber(1, 0), listOf(SetAvgSpeed(SpeedKmh(60.0)))),
                PositionLine(DistanceKm(1.0), LineNumber(2, 0), listOf(EndAvgSpeed(null))),
            )
        } else {
            result
        }
    } else {
        emptyList()
    }
}

fun itemText(line: RoadmapInputLine, dataKind: DataKind): String? =
    if (line !is PositionLine)
        null
    else when (dataKind) {
        Distance -> line.atKm.valueKm.strRound3()
        AverageSpeed -> line.modifier<SetAvg>()?.setavg?.valueKmh?.strRound3()
        SyntheticCount -> line.modifier<AddSynthetic>()?.count.toString()
        SyntheticInterval -> line.modifier<AddSynthetic>()?.interval?.valueKm?.strRound3()
    }

fun presentFields(item: PositionLine) = buildList {
    add(Distance)
    if (item.modifier<SetAvg>() != null) {
        add(AverageSpeed)
    }
    if (item.modifier<AddSynthetic>() != null) {
        add(SyntheticCount)
        add(SyntheticInterval)
    }
}

private fun String.toDouble3Digits() =
    run {
        if (substringAfterLast(".").length > 3)
            substringBeforeLast(".") + "." + substringAfterLast(".").take(3)
        else this
    }.toDouble()