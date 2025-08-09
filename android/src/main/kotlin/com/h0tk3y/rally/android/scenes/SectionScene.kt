package com.h0tk3y.rally.android.scenes

import android.content.ClipData
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.h0tk3y.rally.InputToTextSerializer
import com.h0tk3y.rally.LineNumber
import com.h0tk3y.rally.PositionLine
import com.h0tk3y.rally.PositionLineModifier
import com.h0tk3y.rally.PositionLineModifier.AddSynthetic
import com.h0tk3y.rally.PositionLineModifier.IsSynthetic
import com.h0tk3y.rally.PositionLineModifier.SetAvg
import com.h0tk3y.rally.RallyTimesResultFailure
import com.h0tk3y.rally.RallyTimesResultSuccess
import com.h0tk3y.rally.RoadmapInputLine
import com.h0tk3y.rally.SpeedKmh
import com.h0tk3y.rally.SubsMatch
import com.h0tk3y.rally.android.LoadState
import com.h0tk3y.rally.android.db.SectionInsertOrRenameResult
import com.h0tk3y.rally.android.permissions.RequiredPermissions
import com.h0tk3y.rally.android.racecervice.TelemetryPublicState
import com.h0tk3y.rally.android.scenes.DataKind.AstroTime
import com.h0tk3y.rally.android.scenes.DataKind.AverageSpeed
import com.h0tk3y.rally.android.scenes.DataKind.Distance
import com.h0tk3y.rally.android.scenes.DataKind.OdoDistance
import com.h0tk3y.rally.android.scenes.DataKind.SyntheticCount
import com.h0tk3y.rally.android.scenes.DataKind.SyntheticInterval
import com.h0tk3y.rally.android.valueOrNull
import com.h0tk3y.rally.android.views.GridKey
import com.h0tk3y.rally.android.views.Keyboard
import com.h0tk3y.rally.android.views.PositionsListView
import com.h0tk3y.rally.android.views.RaceView
import com.h0tk3y.rally.modifier
import com.h0tk3y.rally.strRound3
import kotlinx.coroutines.launch

interface SectionOperations {
    fun deleteThisSection()
    fun navigateToEventLog()
    fun renameThisSection(newName: String): SectionInsertOrRenameResult
    fun createSection(newName: String, serializedPositions: String): SectionInsertOrRenameResult
    fun navigateToNewSection(sectionId: Long, isRace: Boolean)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SectionScene(
    sectionOps: SectionOperations?,
    onBack: () -> Unit,
    model: CommonSectionViewModel,
    emptySectionView: @Composable () -> Unit,
    onGoToSettings: (calibrateFromCurrentDistance: Double?) -> Unit,
) {
    val section by model.section.collectAsState(LoadState.LOADING)
    val positions by model.inputPositions.collectAsState(emptyList())
    val selectedLineIndex by model.selectedLineIndex.collectAsState(LineNumber(1, 0))
    val raceCurrentLineIndex by model.raceCurrentLineIndex.collectAsState(null)
    val preprocessed by model.preprocessedPositions.collectAsState(emptyList())
    val results by model.results.collectAsState(RallyTimesResultFailure(emptyList()))
    val odoValues by model.odoValues.collectAsState(emptyMap())
    val subsMatch by model.subsMatching.collectAsState(SubsMatch.EMPTY)

    val editorState by when (model) {
        is EditableSectionViewModel -> model.editorState.collectAsState(
            EditorState(
                isEnabled = false,
                canEnterDot = true,
                canEnterDigits = true,
                maxDigit = 9,
                canMoveUp = true,
                canMoveDown = true,
                canMoveLeft = true,
                canMoveRight = true
            )
        )

        else -> remember { mutableStateOf(EditorState.disabled) }
    }
    val editorFocus by when (model) {
        is EditableSectionViewModel -> model.editorFocus.collectAsState()
        else -> remember { mutableStateOf(EditorFocus(0, Distance)) }
    }
    val allowance by model.timeAllowance.collectAsState(null)
    val raceState by model.raceState.collectAsState(RaceUiState.NoRaceServiceConnection)
    val rememberSpeed by model.rememberSpeed.collectAsState(null)
    val raceUiVisible by model.raceUiVisible.collectAsState(false)
    val telemetryState by model.telemetryState.collectAsState(TelemetryPublicState.NotInitialized)
    val speedLimitPercent by model.speedLimitPercent.collectAsState(null)

    var showDuplicateDialog by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }

    var showMenu by rememberSaveable { mutableStateOf(false) }

    val currentSection = section
    if (currentSection is LoadState.Loaded) {
        if (sectionOps != null && showDuplicateDialog) {
            CreateOrRenameSectionDialog(
                DialogKind.DUPLICATE,
                currentSection.value,
                { showDuplicateDialog = false },
                { newName, _ ->
                    val serializedPositions = InputToTextSerializer.serializeToText(positions)
                    when (val result = sectionOps.createSection(newName, serializedPositions)) {
                        is SectionInsertOrRenameResult.AlreadyExists -> {
                            ItemSaveResult.AlreadyExists
                        }

                        is SectionInsertOrRenameResult.Success -> {
                            showDuplicateDialog = false
                            sectionOps.navigateToNewSection(result.section.id, false)
                            ItemSaveResult.Ok(result.section)
                        }
                    }
                })
        }

        if (sectionOps != null && showRenameDialog) {
            CreateOrRenameSectionDialog(
                DialogKind.RENAME,
                currentSection.value,
                { showRenameDialog = false },
                { newName, _ ->
                    when (val result = sectionOps.renameThisSection(newName)) {
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
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                backgroundColor = MaterialTheme.colors.surface,
                title = {
                    Text(
                        when (currentSection) {
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
                    if (model is EditableSectionViewModel) {
                        Button(onClick = { model.switchEditor() }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (!editorState.isEnabled) Icons.Rounded.Edit else Icons.Rounded.Done,
                                    "Switch editor"
                                )
                                Text(if (editorState.isEnabled) "Calculate" else "Edit")
                            }
                        }

                        Spacer(Modifier.width(4.dp))
                    }

                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Show menu")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (model is RaceModelControls) {
                            val context = LocalContext.current
                            val launchServiceAfterObtainingPermission = permissionRequester(whenObtained = {
                                model.enterRaceMode()
                            }, whenFailedToObtain = {
                                Toast.makeText(context, "Please grant the required permissions", Toast.LENGTH_LONG).show()
                            })
                            DropdownMenuItem(onClick = {
                                if (editorState.isEnabled && model is EditableSectionViewModel) {
                                    model.switchEditor()
                                    when (raceState) {
                                        is RaceUiState.NoRaceServiceConnection -> launchServiceAfterObtainingPermission()
                                        else -> Unit
                                    }
                                } else when (raceState) {
                                    is RaceUiState.NoRaceServiceConnection -> launchServiceAfterObtainingPermission()
                                    is RaceUiState.RaceGoing,
                                    is RaceUiState.RaceGoingInAnotherSection,
                                    is RaceUiState.Stopped,
                                    is RaceUiState.Going,
                                    RaceUiState.RaceNotStarted ->
                                        if (raceUiVisible) model.leaveRaceMode(false) else model.enterRaceMode()
                                }
                                showMenu = false
                            }) {
                                Icon(imageVector = Icons.Rounded.Flag, "Race")
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (editorState.isEnabled) "Race mode" else
                                        when (raceState) {
                                            RaceUiState.NoRaceServiceConnection -> "Race mode"

                                            is RaceUiState.RaceGoingInAnotherSection,
                                            is RaceUiState.Stopped,
                                            is RaceUiState.Going,
                                            is RaceUiState.RaceGoing ->
                                                if (raceUiVisible) "Hide race panel" else "Show race panel"

                                            RaceUiState.RaceNotStarted -> "Stop race service"
                                        }
                                )
                            }
                            if (raceState is RaceUiState.Stopped) {
                                DropdownMenuItem(onClick = {
                                    model.leaveRaceMode(forceStop = true)
                                    showMenu = false
                                }) {
                                    Icon(imageVector = Icons.Rounded.Cancel, "Drop race state and stop")
                                    Spacer(Modifier.width(8.dp))
                                    Text("Reset and stop race service")
                                }
                            }

                            Divider()
                        }

                        if (sectionOps != null) {
                            var inDeleteConfirmation by remember { mutableStateOf(false) }
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
                                    sectionOps.deleteThisSection()
                                    if (model is RaceModelControls) {
                                        model.leaveRaceMode(forceStop = true)
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, "Tap again to delete")
                                    Spacer(Modifier.width(8.dp))
                                    Text("Tap again to delete")
                                }
                            }
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

                        if (currentSection is LoadState.Loaded) {
                            val clipboardManager = LocalClipboard.current
                            DropdownMenuItem(onClick = {
                                model.viewModelScope.launch {
                                    clipboardManager.setClipEntry(
                                        ClipEntry(
                                            ClipData.newPlainText(
                                                "Serialized positions of section${section.valueOrNull()?.name?.let { " '$it'" }.orEmpty()}",
                                                currentSection.value.serializedPositions
                                            )
                                        )
                                    )
                                }
                                showMenu = false
                            }) {
                                Icon(Icons.Default.Share, "Export as text")
                                Spacer(Modifier.width(8.dp))
                                Text("Copy as text")
                            }
                            Divider()
                        }
                        DropdownMenuItem(onClick = {
                            onGoToSettings((raceState as? RaceUiState.HasRaceModel)?.raceModel?.currentDistance?.valueKm)
                            showMenu = false
                        }) {
                            Icon(Icons.Default.Settings, "Settings")
                            Spacer(Modifier.width(8.dp))
                            Text("Settings")
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
                        val listModifier = Modifier
                            .fillMaxHeight()
                            .weight(1.0f, true)
                            .fillMaxWidth(0.5f)
                        val keyboardModifier = Modifier
                            .fillMaxHeight()
                            .weight(1.0f, true)
                            .fillMaxWidth(0.5f)
                        content(listModifier, keyboardModifier)
                    }
                } else {
                    Column(Modifier.padding(padding)) {
                        val listModifier = Modifier
                            .fillMaxWidth()
                            .weight(1.0f)
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
                                odoValues,
                                positionsToDisplay,
                                selectedLineIndex,
                                raceCurrentLineIndex,
                                model as? EditorControls,
                                editorState,
                                editorFocus,
                                results,
                                subsMatch,
                                allowance,
                                !editorState.isEnabled && raceUiVisible,
                                raceState as? RaceUiState.RaceGoing
                            )
                        }

                        LoadState.LOADING -> CenterTextBox("Loading sections...")
                        LoadState.EMPTY -> Box(Modifier.fillMaxSize(), Alignment.Center) { emptySectionView() }
                        LoadState.FAILED -> CenterTextBox("Something went wrong")
                    }
                }
                if (model is EditorControls && editorState.isEnabled) {
                    Keyboard(editorState, model, keyboardModifier)
                } else if (raceUiVisible) {
                    RaceView(
                        race = raceState,
                        distanceLocalizer = (results as? RallyTimesResultSuccess)?.raceTimeDistanceLocalizer,
                        sectionDistanceLocalizer = (results as? RallyTimesResultSuccess)?.sectionTimeDistanceLocalizer,
                        telemetryState = telemetryState,
                        speedLimitPercent = speedLimitPercent,
                        selectedPosition = preprocessed.firstOrNull { it.lineNumber == selectedLineIndex } as? PositionLine,
                        navigateToSection = { sectionOps?.navigateToNewSection(it, true) },
                        goToEventLog = sectionOps?.let { it::navigateToEventLog },
                        goToSettings = { 
                            if (model is RaceModelControls)
                                onGoToSettings((model.raceState as? RaceUiState.HasRaceModel)?.raceModel?.currentDistance?.valueKm)
                            else null
                        },
                        modifier = keyboardModifier,
                        addPositionMaybeWithSpeed = { speed ->
                            val currentRaceState = raceState
                            if (model is EditableSectionViewModel && currentRaceState is RaceUiState.HasRaceModel) {
                                val newItem = model.maybeCreateItemAtDistance(
                                    currentRaceState.raceModel.currentDistance,
                                    forceCreateIfExists = true,
                                    listOfNotNull(speed?.let(PositionLineModifier::ThenAvgSpeed))
                                )
                                if (model.selectedLineIndex.value == newItem.lineNumber) {
                                    model.selectLine(newItem.lineNumber, null)
                                }
                            }
                        },
                        allowance = allowance,
                        rememberSpeed = rememberSpeed,
                        setDebugSpeed = { (model as? PersistedSectionViewModel)?.setDebugSpeed(SpeedKmh(it.toDouble())) },
                        raceControls = model as? RaceModelControls,
                    )
                }
            }
        }
    )
}

@Composable
private fun permissionRequester(
    whenObtained: () -> Unit,
    whenFailedToObtain: () -> Unit
): () -> Unit {
    val context = LocalContext.current

    val permissions = RequiredPermissions.permissionsForRaceService(context)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (permissions.all { ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            whenObtained()
        } else {
            whenFailedToObtain()
        }
    }

    return { launcher.launch(permissions) }
}

interface EditorControls {
    fun switchEditor()
    fun selectLine(index: LineNumber, field: DataKind?)
    fun moveCursor(indexDelta: Int)
    fun keyPress(key: GridKey)
    fun deletePositionViaEditor(line: RoadmapInputLine)
    fun selectNextItem()
    fun selectPreviousItem()
    fun addItemAbove()
    fun addItemBelow()
    fun deleteCharacter()
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
) {
    companion object {
        val disabled = EditorState(false)
    }
}

data class EditorFocus(
    val cursor: Int,
    val kind: DataKind,
)

enum class DataKind {
    Distance, OdoDistance, AverageSpeed, SyntheticCount, SyntheticInterval, AstroTime
}

fun itemText(line: RoadmapInputLine, dataKind: DataKind): String? =
    if (line !is PositionLine)
        null
    else when (dataKind) {
        Distance -> line.atKm.valueKm.strRound3()
        OdoDistance -> line.modifier<PositionLineModifier.OdoDistance>()?.distanceKm?.valueKm?.strRound3()
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
    if (item.modifier<PositionLineModifier.OdoDistance>() != null) {
        add(OdoDistance)
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