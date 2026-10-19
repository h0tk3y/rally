package com.h0tk3y.rally.android.scenes

import android.content.ClipData
import android.content.res.Configuration
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.ExtendedFloatingActionButton
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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.h0tk3y.rally.InputToTextSerializer
import com.h0tk3y.rally.LineNumber
import com.h0tk3y.rally.PositionLine
import com.h0tk3y.rally.PositionLineModifier
import com.h0tk3y.rally.PositionLineModifier.IsSynthetic
import com.h0tk3y.rally.PositionLineModifier.SetAvg
import com.h0tk3y.rally.R
import com.h0tk3y.rally.RallyTimesResultFailure
import com.h0tk3y.rally.RallyTimesResultSuccess
import com.h0tk3y.rally.RoadmapInputLine
import com.h0tk3y.rally.SpeedKmh
import com.h0tk3y.rally.SubsMatch
import com.h0tk3y.rally.android.LoadState
import com.h0tk3y.rally.android.db.SectionInsertOrRenameResult
import com.h0tk3y.rally.android.permissions.TelemetryAccessDialogHost
import com.h0tk3y.rally.android.permissions.rememberTelemetryAccessController
import com.h0tk3y.rally.android.racecervice.TelemetryPublicState
import com.h0tk3y.rally.android.util.BatteryOptimizationHintDialog
import com.h0tk3y.rally.android.util.isIgnoringBatteryOptimizations
import com.h0tk3y.rally.android.util.openBatteryOptimizationSettings
import com.h0tk3y.rally.android.scenes.DataKind.AstroTime
import com.h0tk3y.rally.android.scenes.DataKind.AverageSpeed
import com.h0tk3y.rally.android.scenes.DataKind.Distance
import com.h0tk3y.rally.android.scenes.DataKind.SpeedPercentage
import com.h0tk3y.rally.android.valueOrNull
import com.h0tk3y.rally.android.views.GridKey
import com.h0tk3y.rally.android.views.Keyboard
import com.h0tk3y.rally.android.views.PositionsListView
import com.h0tk3y.rally.android.views.RaceView
import com.h0tk3y.rally.formatPercentage
import com.h0tk3y.rally.modifier
import com.h0tk3y.rally.strRound3
import kotlinx.coroutines.flow.first
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
    startInRaceMode: Boolean = false,
) {
    val section by model.section.collectAsState(LoadState.LOADING)
    val positions by model.inputPositions.collectAsState(emptyList())
    val selectedLineIndex by model.selectedLineIndex.collectAsState(LineNumber(1, 0))
    val raceCurrentLineIndex by model.raceCurrentLineIndex.collectAsState(null)
    val preprocessed by model.preprocessedPositions.collectAsState(emptyList())
    val results by model.results.collectAsState(RallyTimesResultFailure(emptyList()))
    val odoValues by model.odoValues.collectAsState(emptyMap())
    val subsMatch by model.subsMatching.collectAsState(SubsMatch.EMPTY)

    if (model is EditableSectionViewModel) {
        val context = LocalContext.current
        val sounds = remember {
            mapOf(
                SoundEvent.BEEP_UP to MediaPlayer.create(context, R.raw.beep_up),
                SoundEvent.BEEP_START to MediaPlayer.create(context, R.raw.beep_start),
                SoundEvent.BEEP_FINISH to MediaPlayer.create(context, R.raw.beep_finish),
                SoundEvent.BEEP_REVERSE to MediaPlayer.create(context, R.raw.beep_reverse)
            )
        }
        LaunchedEffect(Unit) {
            model.soundFlow.collect { 
                sounds.getValue(it).start()
            }
        }
    }


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
    val raceStateState = model.raceState.collectAsState(RaceUiState.NoRaceServiceConnection)
    val raceState by raceStateState
    // The race state changes with every telemetry update; read this flag where the details are not needed:
    val inTimedRace by remember(raceStateState) { derivedStateOf { raceStateState.value is RaceUiState.RaceGoing } }
    val rememberSpeed by model.rememberSpeed.collectAsState(null)
    val raceUiVisible by model.raceUiVisible.collectAsState(false)
    val telemetryState by model.telemetryState.collectAsState(TelemetryPublicState.NotInitialized)
    val speedLimitPercent by model.speedLimitPercent.collectAsState(null)
    val panelsSwapped by when (model) {
        is PanelSwapControls -> model.panelsSwapped.collectAsState()
        else -> remember { mutableStateOf(false) }
    }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

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

    val isLargeScreen = LocalConfiguration.current.screenWidthDp >= 600
    val showRaceModeFab = !isLargeScreen && !editorState.isEnabled && !raceUiVisible && model is RaceModelControls

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val telemetryAccess = rememberTelemetryAccessController(onAccessMaybeGranted = { (model as? RaceModelControls)?.recheckTelemetryAccess() })
    var showBatteryHint by rememberSaveable { mutableStateOf(false) }
    val goToSettings = { onGoToSettings((raceState as? RaceUiState.HasRaceModel)?.raceModel?.currentDistance?.valueKm) }

    TelemetryAccessDialogHost(telemetryAccess, onChangeSource = goToSettings)

    val explainTelemetryProblem: (TelemetryPublicState) -> Unit = { state ->
        if (model is RaceModelControls) {
            coroutineScope.launch {
                telemetryAccess.explain(model.telemetrySource.first(), state)
            }
        }
    }

    // A permission can go missing after the race mode has started, e.g. when the user sets up an OBD adapter in the settings.
    // The status in the race panel is easy to miss, so explain it once per screen.
    var explainedMissingPermission by rememberSaveable { mutableStateOf(false) }
    val isMissingPermission = telemetryState == TelemetryPublicState.BtNoPermissions || telemetryState == TelemetryPublicState.GpsNoPermissions
    if (isMissingPermission && raceUiVisible && !explainedMissingPermission) {
        LaunchedEffect(Unit) {
            explainedMissingPermission = true
            explainTelemetryProblem(telemetryState)
        }
    }
    if (showBatteryHint) {
        BatteryOptimizationHintDialog(
            onOpenSettings = {
                showBatteryHint = false
                context.openBatteryOptimizationSettings()
            },
            onDismiss = { showBatteryHint = false }
        )
    }

    /** All the ways into the race mode start the race service only after checking the permissions of the telemetry source */
    val launchServiceAfterObtainingPermission: () -> Unit = {
        if (model is RaceModelControls) {
            coroutineScope.launch {
                telemetryAccess.runWhenReady(model.telemetrySource.first()) {
                    model.enterRaceMode()
                    if (model is LiveSectionViewModel) {
                        coroutineScope.launch {
                            if (model.consumeBatteryHint(context.isIgnoringBatteryOptimizations())) {
                                showBatteryHint = true
                            }
                        }
                    }
                }
            }
        }
    }

    val onStartRaceMode: () -> Unit = {
        if (raceState is RaceUiState.NoRaceServiceConnection) {
            launchServiceAfterObtainingPermission()
        } else {
            (model as? RaceModelControls)?.enterRaceMode()
        }
    }

    // Opened from the race panel of another section, or restored after the process was killed; enter the race mode only once
    var startedInRaceMode by rememberSaveable { mutableStateOf(false) }
    if (startInRaceMode && !startedInRaceMode) {
        LaunchedEffect(Unit) {
            startedInRaceMode = true
            onStartRaceMode()
        }
    }

    @Composable
    fun layout(content: @Composable (listModifier: Modifier, keyboardModifier: Modifier) -> Unit) {
        if (isLandscape) {
            Row(Modifier.imePadding()) {
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
            Column(Modifier.imePadding()) {
                val listModifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f)
                val keyboardModifier = Modifier.fillMaxWidth()
                content(listModifier, keyboardModifier)
            }
        }
    }

    @Composable
    fun raceModeFab() {
        if (showRaceModeFab) {
            ExtendedFloatingActionButton(
                onClick = onStartRaceMode,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = null
                    )
                },
                text = {
                    Text(stringResource(R.string.buttonRaceMode))
                }
            )
        }
    }

    @Composable
    fun topBar() {
        TopAppBar(
            backgroundColor = MaterialTheme.colors.surface,
            title = {
                Text(
                    when (currentSection) {
                        is LoadState.Loaded -> currentSection.value.name
                        is LoadState.LOADING -> stringResource(R.string.stateLoading)
                        LoadState.EMPTY -> ""
                        LoadState.FAILED -> stringResource(R.string.stateFailedToLoadSection)
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
                                stringResource(R.string.buttonSwitchEditor)
                            )
                            Text(if (editorState.isEnabled) stringResource(R.string.buttonCalculate) else stringResource(R.string.buttonEdit))
                        }
                    }

                    Spacer(Modifier.width(4.dp))
                }

                if (isLargeScreen && !editorState.isEnabled && !raceUiVisible && model is RaceModelControls) {
                    Button(onClick = onStartRaceMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Flag,
                                contentDescription = stringResource(R.string.buttonRaceMode)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.buttonRaceMode))
                        }
                    }

                    Spacer(Modifier.width(4.dp))
                }

                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.showMenu))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (model is RaceModelControls) {
                        if (raceState !is RaceUiState.RaceNotStarted) {
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
                                        if (raceUiVisible) model.hideRaceUi() else model.enterRaceMode()
                                }
                                showMenu = false
                            }) {
                                Icon(imageVector = Icons.Rounded.Flag, stringResource(R.string.buttonRaceMode))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (editorState.isEnabled) {
                                        when (raceState) {
                                            is RaceUiState.NoRaceServiceConnection -> stringResource(R.string.buttonRaceMode)
                                            else -> stringResource(R.string.buttonShowRacePanel)
                                        }
                                    } else
                                        when (raceState) {
                                            RaceUiState.NoRaceServiceConnection -> stringResource(R.string.buttonRaceMode)

                                            is RaceUiState.RaceGoingInAnotherSection,
                                            is RaceUiState.Stopped,
                                            is RaceUiState.Going,
                                            RaceUiState.RaceNotStarted,
                                            is RaceUiState.RaceGoing ->
                                                if (raceUiVisible)
                                                    stringResource(R.string.buttonHideRacePanel)
                                                else
                                                    stringResource(R.string.buttonShowRacePanel)
                                        }
                                )
                            }
                        }
                        if (raceState is RaceUiState.Stopped || raceState is RaceUiState.RaceNotStarted) {
                            DropdownMenuItem(onClick = {
                                model.leaveRaceMode(forceStop = true)
                                showMenu = false
                            }) {
                                Icon(imageVector = Icons.Rounded.Cancel, stringResource(R.string.buttonDropRaceState))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.buttonDropRaceState))
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
                                Icon(Icons.Default.Delete, stringResource(R.string.buttonDeleteThisSection))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.buttonDeleteThisSection))
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
                                Icon(Icons.Default.DeleteForever, stringResource(R.string.buttonTapAgainToDelete))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.buttonTapAgainToDelete))
                            }
                        }
                        DropdownMenuItem(onClick = {
                            showMenu = false
                            showDuplicateDialog = true
                        }) {
                            Icon(Icons.Default.Add, stringResource(R.string.buttonDuplicateThisSection))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.buttonDuplicateThisSection))
                        }

                        DropdownMenuItem(onClick = {
                            showMenu = false
                            showRenameDialog = true
                        }) {
                            Icon(Icons.Default.Edit, stringResource(R.string.buttonRenameThisSection))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.buttonRenameThisSection))
                        }
                    }

                    if (model is PanelSwapControls) {
                        DropdownMenuItem(onClick = {
                            model.togglePanelsSwapped()
                            showMenu = false
                        }) {
                            Icon(
                                if (isLandscape) Icons.Default.SwapHoriz else Icons.Default.SwapVert,
                                stringResource(R.string.buttonSwapListAndRacePanel)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.buttonSwapListAndRacePanel))
                        }
                        Divider()
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
                            Icon(Icons.Default.Share, stringResource(R.string.buttonCopyAsText))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.buttonCopyAsText))
                        }
                        Divider()
                    }
                    DropdownMenuItem(onClick = {
                        goToSettings()
                        showMenu = false
                    }) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings))
                    }
                }
            }
        )
    }

    // Hoisted, so that the list keeps its scroll position when the HUD panels are swapped:
    val listState = rememberLazyListState()

    @Composable
    fun listContent(padding: PaddingValues) {
        Box(Modifier.padding(padding)) {
            when (section) {
                is LoadState.Loaded -> {
                    val hasErrors = results is RallyTimesResultFailure
                    val positionsToDisplay = remember(editorState.isEnabled, positions, preprocessed, hasErrors) {
                        if (editorState.isEnabled) positions else preprocessed.filter {
                            !hasErrors || it !is PositionLine || it.modifier<IsSynthetic>() == null
                        }
                    }
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
                        inTimedRace,
                        // Let the last items scroll above the FAB: FAB height (56dp) + its margin (16dp) + a gap (16dp)
                        contentPadding = if (showRaceModeFab) PaddingValues(bottom = 88.dp) else PaddingValues()
                    )
                }

                LoadState.LOADING -> CenterTextBox(stringResource(R.string.stateLoadingSections))
                LoadState.EMPTY -> Box(Modifier.fillMaxSize(), Alignment.Center) { emptySectionView() }
                LoadState.FAILED -> CenterTextBox(stringResource(R.string.stateSomethingWentWrong))
            }
        }
    }

    val isKeyboardVisible = model is EditorControls && editorState.isEnabled

    @Composable
    fun bottomPanel(keyboardModifier: Modifier) {
        if (model is EditorControls && editorState.isEnabled) {
            Keyboard(editorState, model, keyboardModifier)
        } else if (raceUiVisible) {
            RaceView(
                timeFlow = model.instant,
                race = raceState,
                distanceLocalizer = (results as? RallyTimesResultSuccess)?.raceTimeDistanceLocalizer,
                sectionDistanceLocalizer = (results as? RallyTimesResultSuccess)?.sectionTimeDistanceLocalizer,
                telemetryState = telemetryState,
                speedLimitPercent = speedLimitPercent,
                selectedPosition = preprocessed.firstOrNull { it.lineNumber == selectedLineIndex } as? PositionLine,
                navigateToSection = { sectionOps?.navigateToNewSection(it, true) },
                goToEventLog = sectionOps?.let { it::navigateToEventLog },
                goToSettings = {
                    if (model is RaceModelControls) goToSettings()
                },
                onTelemetryAccessProblem = explainTelemetryProblem,
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
                setDebugSpeed = { (model as? LiveSectionViewModel)?.setDebugSpeed(SpeedKmh(it.toDouble())) },
                raceControls = model as? RaceModelControls,
            )
        }
    }

    if (panelsSwapped && raceUiVisible && !isKeyboardVisible) {
        // Driver HUD with swapped panels: the race panel goes first, the top bar stays at the top
        if (isLandscape) {
            Row(Modifier.imePadding()) {
                val panelModifier = Modifier
                    .fillMaxHeight()
                    .weight(1.0f, true)
                    .fillMaxWidth(0.5f)
                val listModifier = Modifier
                    .fillMaxHeight()
                    .weight(1.0f, true)
                    .fillMaxWidth(0.5f)
                bottomPanel(panelModifier)
                Scaffold(
                    modifier = listModifier.imePadding(),
                    floatingActionButton = { raceModeFab() },
                    topBar = { topBar() },
                    content = { padding -> listContent(padding) }
                )
            }
        } else {
            Column(Modifier.imePadding()) {
                topBar()
                bottomPanel(Modifier.fillMaxWidth())
                Scaffold(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.0f)
                        .imePadding(),
                    floatingActionButton = { raceModeFab() },
                    content = { padding -> listContent(padding) }
                )
            }
        }
    } else {
        layout { listModifier, keyboardModifier ->
            Scaffold(
                modifier = listModifier.imePadding(),
                floatingActionButton = { raceModeFab() },
                topBar = { topBar() },
                content = { padding -> listContent(padding) }
            )

            bottomPanel(keyboardModifier)
        }
    }
}

interface EditorControls {
    fun switchEditor()
    fun selectLine(index: LineNumber, field: DataKind?)
    fun moveCursor(indexDelta: Int)
    fun keyPress(key: GridKey)
    fun deletePositionViaEditor(line: RoadmapInputLine)
    fun selectNextItem()
    fun selectPreviousItem()
    fun addItem()
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
    val canSetPercent: Boolean = true,
    val lastEnteredPercent: String? = null
) {
    companion object {
        val disabled = EditorState(false)
    }
}

data class EditorFocus(
    val cursor: Int,
    val kind: DataKind,
    val rawText: String? = null,
    /** The whole field text is selected, so that typing replaces it rather than editing at the cursor. */
    val selectAll: Boolean = false
)

enum class DataKind {
    Distance, AverageSpeed, SpeedPercentage, AstroTime
}

fun itemText(line: RoadmapInputLine, dataKind: DataKind): String? =
    if (line !is PositionLine)
        null
    else when (dataKind) {
        Distance -> line.atKm.valueKm.strRound3()
        AverageSpeed -> line.modifier<SetAvg>()?.setavg?.baseSpeedKmh?.strRound3()
        SpeedPercentage -> line.modifier<SetAvg>()?.setavg?.percentage?.formatPercentage()
        AstroTime -> line.modifier<PositionLineModifier.AstroTime>()?.timeOfDay?.timeStrNoDayOverflow()
    }

fun presentFields(item: PositionLine) = buildList {
    add(Distance)
    val setAvg = item.modifier<SetAvg>()
    if (setAvg != null) {
        add(AverageSpeed)
        if (setAvg.setavg.percentage != null) {
            add(SpeedPercentage)
        }
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

fun String.toDouble3DigitsOrDefault(default: Double = 0.0): Double =
    if (isEmpty() || this == ".") default else toDouble3Digits()