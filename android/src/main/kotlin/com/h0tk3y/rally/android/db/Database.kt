package com.h0tk3y.rally.android.db;

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.h0tk3y.rally.android.LoadState
import com.h0tk3y.rally.db.AppDatabase
import com.h0tk3y.rally.db.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class Database(databaseDriverFactory: DatabaseDriverFactory) {
    private val database = AppDatabase(
        databaseDriverFactory.createDriver()
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
        return dbQuery.updatePositions(serializedPositions, id)
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
}

sealed interface SectionInsertOrRenameResult {
    data class Success(val section: Section) : SectionInsertOrRenameResult
    object AlreadyExists : SectionInsertOrRenameResult
}