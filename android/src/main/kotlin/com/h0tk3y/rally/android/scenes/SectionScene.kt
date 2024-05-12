package com.h0tk3y.rally.android.scenes

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.h0tk3y.rally.android.PreferenceRepository
import com.h0tk3y.rally.android.db.Database
import com.h0tk3y.rally.android.db.SectionInsertOrRenameResult
import com.h0tk3y.rally.android.scenes.DataKind.*
import com.h0tk3y.rally.android.scenes.DataKind.AstroTime
import com.h0tk3y.rally.android.scenes.TimeAllowance.BY_TEN_FULL
import com.h0tk3y.rally.android.scenes.TimeAllowance.BY_TEN_FULL_PLUS_ONE
import com.h0tk3y.rally.android.views.GridKey
import com.h0tk3y.rally.android.views.Keyboard
import com.h0tk3y.rally.android.views.PositionsListView
import moe.tlaster.precompose.viewmodel.viewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SectionScene(
    sectionId: Long,
    database: Database,
    userPreferences: PreferenceRepository,
    onDeleteSection: () -> Unit,
    onNavigateToNewSection: (Long) -> Unit,
    onBack: () -> Unit
) {
    val model = viewModel(SectionViewModel::class) { SectionViewModel(sectionId, database, userPreferences) }
    val section by model.section.collectAsState(LoadState.LOADING)
    val positions by model.inputPositions.collectAsState(emptyList())
    val selectedLineIndex by model.selectedLineIndex.collectAsState(LineNumber(1, 0))
    val preprocessed by model.preprocessedPositions.collectAsState(emptyList())
    val results by model.results.collectAsState(RallyTimesResultFailure(emptyList()))
    val subsMatch by model.subsMatching.collectAsState(SubsMatch.EMPTY)
    val editorState by model.editorState.collectAsState(
        EditorState(false, true, true, 9, true, true, true, true)
    )
    val editorFocus by model.editorFocus.collectAsState()
    val allowance by model.timeAllowance.collectAsState(null)

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
                                showMenu = false
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
                        Divider()
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Allowance")
                            val rbColors = RadioButtonDefaults.colors(MaterialTheme.colors.primary)
                            Row(Modifier.fillMaxWidth().clickable { model.setAllowance(null) }) {
                                RadioButton(colors = rbColors, selected = allowance == null, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text("⦰")
                            }
                            Row(Modifier.fillMaxWidth().clickable { model.setAllowance(BY_TEN_FULL) }) {
                                RadioButton(colors = rbColors, selected = allowance == BY_TEN_FULL, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text("⌊t/10⌋")
                            }
                            Row(Modifier.fillMaxWidth().clickable { model.setAllowance(BY_TEN_FULL_PLUS_ONE) }) {
                                RadioButton(colors = rbColors, selected = allowance == BY_TEN_FULL_PLUS_ONE, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text("⌈t/10⌉")
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
                                results,
                                subsMatch,
                                allowance
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
    val maxDigit: Int = 9,
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
    Distance, AverageSpeed, SyntheticCount, SyntheticInterval, AstroTime
}

fun itemText(line: RoadmapInputLine, dataKind: DataKind): String? =
    if (line !is PositionLine)
        null
    else when (dataKind) {
        Distance -> line.atKm.valueKm.strRound3()
        AverageSpeed -> line.modifier<SetAvg>()?.setavg?.valueKmh?.strRound3()
        SyntheticCount -> line.modifier<AddSynthetic>()?.count.toString()
        SyntheticInterval -> line.modifier<AddSynthetic>()?.interval?.valueKm?.strRound3()
        AstroTime -> line.modifier<PositionLineModifier.AstroTime>()?.timeOfDay?.timeStrNoDayOverflow()
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
    if (item.modifier<PositionLineModifier.AstroTime>() != null) {
        add(AstroTime)
    }
}

fun String.toDouble3Digits() =
    run {
        if (substringAfterLast(".").length > 3)
            substringBeforeLast(".") + "." + substringAfterLast(".").take(3)
        else this
    }.toDouble()