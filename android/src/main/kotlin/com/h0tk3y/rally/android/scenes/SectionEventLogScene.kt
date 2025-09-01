package com.h0tk3y.rally.android.scenes

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.OutlinedFlag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.h0tk3y.rally.R
import com.h0tk3y.rally.android.LoadState
import com.h0tk3y.rally.android.db.Database
import com.h0tk3y.rally.db.Event
import com.h0tk3y.rally.db.Section
import com.h0tk3y.rally.model.RaceEventKind
import com.h0tk3y.rally.strRound3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration

@Composable
fun SectionEventLogScene(
    model: SectionEventLogViewModel,
    onBack: () -> Unit
) {
    val events by model.sectionEvents.collectAsState()

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                backgroundColor = MaterialTheme.colors.surface,
                title = {
                    Text(stringResource(R.string.eventLog))
                },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        model.deleteAllForCurrentSection()
                    }) {
                        Icon(Icons.Default.DeleteSweep, stringResource(R.string.buttonDeleteEvents))
                    }
                }
            )
        },
        content = { padding ->
            val listState = rememberLazyListState()
            LazyColumn(Modifier.padding(padding), listState) {
                when (val currentEvents = events) {
                    is LoadState.Loaded -> {
                        itemsIndexed(currentEvents.value) { index, item ->
                            val duration = item.sinceTimestamp?.minus(item.timestamp)?.times(-1)
                            val durationText = duration?.formatToHms()?.let { " ($it)" }.orEmpty()

                            Row(Modifier.padding(8.dp), verticalAlignment = CenterVertically) {
                                Icon(
                                    when (item.kind) {
                                        RaceEventKind.SECTION_START -> Icons.Default.PlayArrow
                                        RaceEventKind.SECTION_FINISH -> Icons.Default.Stop
                                        RaceEventKind.RACE_START -> Icons.Default.OutlinedFlag
                                        RaceEventKind.RACE_FINISH -> Icons.Default.Flag
                                        RaceEventKind.SECTION_UNDO_FINISH,
                                        RaceEventKind.RACE_UNDO_FINISH -> Icons.AutoMirrored.Default.Undo
                                    }, stringResource(R.string.eventKindPrefix) + item.kind, Modifier.weight(1f)
                                )
                                Text(item.distance.strRound3(), Modifier.weight(1.5f))
                                Text(item.timestamp.toLocalDateTime(TimeZone.currentSystemDefault()).time.format(localTimeFormat), Modifier.weight(2f))
                                Text(
                                    when (item.kind) {
                                        RaceEventKind.SECTION_START -> stringResource(R.string.eventSectionStarted)
                                        RaceEventKind.SECTION_FINISH -> stringResource(R.string.eventSectionFinished)
                                        RaceEventKind.SECTION_UNDO_FINISH -> stringResource(R.string.eventUndoSectionFinish)
                                        RaceEventKind.RACE_START -> stringResource(R.string.eventRaceStarted)
                                        RaceEventKind.RACE_FINISH -> stringResource(R.string.eventRaceFinished)
                                        RaceEventKind.RACE_UNDO_FINISH -> stringResource(R.string.eventUndoRaceFinish)
                                    } + durationText, Modifier.weight(4f)
                                )
                            }
                        }
                    }

                    else -> Unit
                }
            }
        }
    )
}

private val localTimeFormat = LocalTime.Format {
    hour()
    char(':')
    minute()
    char(':')
    second()
}

class SectionEventLogViewModel(val sectionId: Long, val database: Database) : ViewModel() {
    private val _section = MutableStateFlow<LoadState<Section>>(LoadState.LOADING)
    private val _sectionEvents = MutableStateFlow<LoadState<List<Event>>>(LoadState.EMPTY)

    val section = _section.asStateFlow()
    val sectionEvents = _sectionEvents.asStateFlow()

    init {
        viewModelScope.launch {
            database.selectSectionById(sectionId).collect { section ->
                _section.value = section

                if (section is LoadState.Loaded) {
                    database.selectEventsForSection(section.value).collect { events ->
                        _sectionEvents.value = events
                    }
                }
            }
        }
    }

    fun deleteAllForCurrentSection() {
        viewModelScope.launch {
            val currentSection = (section.value as? LoadState.Loaded)?.value
            if (currentSection != null) {
                database.deleteEventsForSection(currentSection)
            }
        }
    }
}

fun Duration.formatToHms(): String {
    val totalSeconds = inWholeSeconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return buildString {
        if (hours > 0) {
            append("$hours".padStart(2, '0'))
            append(":")
        }
        this.append("$minutes".padStart(2, '0'))
        append(":")
        append("$seconds".padStart(2, '0'))
    }
}