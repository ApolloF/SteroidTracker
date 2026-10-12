package com.apollof.protocoltracker.ui.today

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.dayStartsAtMidnight
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import com.apollof.protocoltracker.waitForData
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The clock in Today's corner: a chosen time is what a check records, and "Now" sets it back. */
@RunWith(AndroidJUnit4::class)
class CheckTimeTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    @Before
    fun seed(): Unit = runBlocking {
        container.dayStartsAtMidnight()
        container.repository.seedPresets()
        container.repository.saveItem(
            PlanItem(
                "test-item", null, "preset:test-cyp", Amount(250.0, DoseUnit.MG), DoseBasis.PER_WEEK, Formulation(perMl = 250.0),
                Schedule.Daily(listOf(Timing.Slot(DaySlot.ANY_TIME))), startDate = LocalDate.now(),
            ),
        )
    }

    private fun count(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun aChosenTimeIsWhatTheCheckRecordsAndNowSetsItBack() {
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        compose.waitUntil(15_000) { count("35.71 mg · 0.14 mL") > 0 }

        compose.onNode(hasContentDescription("Check time") and hasText("Now")).performClick()
        compose.onNodeWithText("Checks record").assertExists()
        // The dial opens at the current time; OK keeps its hour and minute.
        compose.onNodeWithText("OK").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Now").fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithContentDescription("Mark Test C taken").performClick()
        compose.waitForData(15_000) { container.repository.allLogsNow().isNotEmpty() }
        val taken = runBlocking { container.repository.allLogsNow().single().takenAt }
        assertEquals(0, taken.epochSecond % 60, "the chosen minute, not the current second")
        assertTrue(taken <= Instant.now() && taken > Instant.now().minusSeconds(180))
        compose.waitUntil(15_000) { count("Test C taken at") > 0 }

        compose.onNode(hasContentDescription("Check time")).performClick()
        compose.onNodeWithText("Now").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Check time") and hasText("Now")).fetchSemanticsNodes().isNotEmpty() }
    }
}
