package com.h0tk3y.rally.android.db

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.h0tk3y.rally.DistanceKm
import com.h0tk3y.rally.android.LoadState
import com.h0tk3y.rally.db.AppDatabase
import com.h0tk3y.rally.db.Event
import com.h0tk3y.rally.db.Section
import com.h0tk3y.rally.model.RaceEventKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

class Database(databaseDriverFactory: DatabaseDriverFactory) {
    private val database = AppDatabase(
        databaseDriverFactory.createDriver(),
        EventAdapter = Event.Adapter(
            EnumColumnAdapter(),
            timestampAdapter = instantAdapter,
            sinceTimestampAdapter = instantAdapter,
        )
    )
    private val dbQuery = database.appDatabaseQueries

    internal fun selectAllSections(): Flow<LoadState<List<Section>>> {
        return dbQuery.selectAllSections()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { LoadState.Loaded(it) }
    }

    internal fun selectSectionById(id: Long): Flow<LoadState<Section>> {
        return dbQuery
            .selectSectionById(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it?.let { LoadState.Loaded(it) } ?: LoadState.EMPTY }
    }

    internal fun deleteSectionById(id: Long) {
        dbQuery.deleteSection(id)
    }

    internal fun updateSectionPositions(id: Long, serializedPositions: String): Unit {
        dbQuery.updatePositions(serializedPositions, id)
    }
    
    internal fun createEmptySection(name: String): SectionInsertOrRenameResult {
        return createSection(name, null)
    }

    internal fun createSection(name: String, positions: String?): SectionInsertOrRenameResult {
        return dbQuery.transactionWithResult {
            val exists = dbQuery.selectSectionByExactName(name).executeAsOneOrNull() != null
            if (exists)
                SectionInsertOrRenameResult.AlreadyExists
            else {
                if (positions == null) {
                    dbQuery.createEmptySection(null, name)
                } else {
                    dbQuery.createSection(null, name, positions)
                }
                val id = dbQuery.lastInsertedId().executeAsOne()
                SectionInsertOrRenameResult.Success(Section(id, name, ""))
            }
        }
    }

    internal fun renameSection(id: Long, newName: String): SectionInsertOrRenameResult {
        return dbQuery.transactionWithResult {
            val exists = dbQuery.selectSectionByExactName(newName).executeAsOneOrNull() != null
            if (exists)
                SectionInsertOrRenameResult.AlreadyExists
            else {
                dbQuery.updateName(newName, id)
                SectionInsertOrRenameResult.Success(Section(id, newName, ""))
            }
        }
    }
    
    internal fun insertEvent(
        kind: RaceEventKind,
        sectionId: Long,
        distanceKm: DistanceKm,
        timestamp: Instant,
        sinceTimestamp: Instant?
    ) {
        dbQuery.createEvent(id = null, sectionId, distanceKm.valueKm, kind, timestamp, sinceTimestamp)
    }

    internal fun selectEventsForSection(section: Section): Flow<LoadState<List<Event>>> {
        return dbQuery.selectEventsBySectionId(section.id).asFlow()
            .mapToList(Dispatchers.Default)
            .map { LoadState.Loaded(it) }
    }

    internal fun deleteEventsForSection(section: Section) {
        dbQuery.deleteEventsForSection(section.id)
    }
}

sealed interface SectionInsertOrRenameResult {
    data class Success(val section: Section) : SectionInsertOrRenameResult
    object AlreadyExists : SectionInsertOrRenameResult
}

private val instantAdapter = object : ColumnAdapter<Instant, String> {
    override fun decode(databaseValue: String): Instant =
        Instant.parse(databaseValue)

    override fun encode(value: Instant): String =
        value.toString()
}