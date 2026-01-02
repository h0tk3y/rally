package com.h0tk3y.rally.android.scenes

import com.h0tk3y.rally.DistanceKm
import com.h0tk3y.rally.android.LoadState
import com.h0tk3y.rally.android.db.DatabaseOperations
import com.h0tk3y.rally.android.db.SectionInsertOrRenameResult
import com.h0tk3y.rally.db.Event
import com.h0tk3y.rally.db.Section
import com.h0tk3y.rally.model.RaceEventKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

class InMemoryDatabaseOperations : DatabaseOperations {
    val sections = MutableStateFlow<Set<Section>>(emptySet())
    val events = MutableStateFlow<Set<Event>>(emptySet())

    override fun selectAllSections(): Flow<LoadState<List<Section>>> =
        sections.map { LoadState.Loaded(it.toList()) }

    override fun selectSectionById(id: Long): Flow<LoadState<Section>> =
        sections.map { it.find { section -> section.id == id }?.let { found -> LoadState.Loaded(found) } ?: LoadState.EMPTY }

    override fun deleteSectionById(id: Long) {
        sections.update { it.filter { section -> section.id != id }.toSet() }
    }

    override fun updateSectionPositions(id: Long, serializedPositions: String) {
        sections.update {
            it.map { section ->
                when (section.id) {
                    id -> section.copy(serializedPositions = serializedPositions)
                    else -> section
                }
            }.toSet()
        }
    }

    override fun createEmptySection(name: String): SectionInsertOrRenameResult = when {
        sections.value.any { it.name == name } -> SectionInsertOrRenameResult.AlreadyExists
        else -> {
            val result = Section(id = (sections.value.maxOfOrNull { it.id } ?: 0) + 1, name = name, serializedPositions = "")
            sections.update {
                (it + result).toSet()
            }
            SectionInsertOrRenameResult.Success(result)
        }
    }

    override fun createSection(name: String, positions: String?): SectionInsertOrRenameResult =
        when (val insertEmpty = createEmptySection(name)) {
            is SectionInsertOrRenameResult.Success -> {
                updateSectionPositions(insertEmpty.section.id, positions ?: "")
                SectionInsertOrRenameResult.Success(sections.value.single { it.id == insertEmpty.section.id })
            }

            is SectionInsertOrRenameResult.AlreadyExists -> insertEmpty
        }

    override fun renameSection(id: Long, newName: String): SectionInsertOrRenameResult =
        if (sections.value.any { it.name == newName })
            SectionInsertOrRenameResult.AlreadyExists
        else {
            val section = sections.value.find { it.id == id }
                ?: error("no section with id $id")
            val result = section.copy(name = newName)
            sections.update {
                it.map { section ->
                    when (section.id) {
                        id -> result
                        else -> section
                    }
                }.toSet()
            }
            SectionInsertOrRenameResult.Success(result)
        }

    override fun insertEvent(
        kind: RaceEventKind,
        sectionId: Long,
        distanceKm: DistanceKm,
        timestamp: Instant,
        sinceTimestamp: Instant?
    ) {
        events.update { it + Event(id = (events.value.maxOfOrNull { event -> event.id } ?: 0) + 1, sectionId, distanceKm.valueKm, kind, timestamp, sinceTimestamp) }
    }

    override fun selectEventsForSection(section: Section): Flow<LoadState<List<Event>>> =
        events.map { LoadState.Loaded(it.filter { event -> event.sectionId == section.id }) }

    override fun deleteEventsForSection(section: Section) {
        events.update { it.filter { event -> event.sectionId != section.id }.toSet() }
    }
}