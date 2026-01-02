package com.h0tk3y.rally.android.scenes

import com.h0tk3y.rally.android.LoadState
import com.h0tk3y.rally.android.db.DatabaseOperations
import com.h0tk3y.rally.db.Event
import com.h0tk3y.rally.db.Section
import com.h0tk3y.rally.model.RaceEventKind
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SectionEventLogViewModelTest {

    @Test
    fun `loads section and events from database and triggers the delete operation`() = runTest(UnconfinedTestDispatcher()) {
        val sectionId = 1L
        val eventId = 2L
        val testEvent = Event(eventId, sectionId, 0.0, RaceEventKind.RACE_START, Instant.fromEpochSeconds(0), null)
        val testSection = Section(sectionId, "test", "0.0\n60.0")

        val db = mockk<DatabaseOperations>() {
            every { selectSectionById(any()) }.returns(MutableStateFlow(LoadState.Loaded(testSection)))
            every { selectEventsForSection(any()) }.returns(MutableStateFlow(LoadState.Loaded(listOf(testEvent))))
            every { deleteEventsForSection(any()) }.returns(Unit)
        }

        val myViewModel = SectionEventLogViewModel(sectionId, db)

        verify(exactly = 1) { db.selectSectionById(sectionId).let { } }
        verify(exactly = 1) { db.selectEventsForSection(testSection).let { } }

        assertEquals(myViewModel.section.value, LoadState.Loaded(testSection))
        assertEquals(myViewModel.sectionEvents.value, LoadState.Loaded(listOf(testEvent)))

        myViewModel.deleteAllForCurrentSection()

        verify(exactly = 1) { db.deleteEventsForSection(testSection) }
    }
}