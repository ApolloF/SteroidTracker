package com.apollof.protocoltracker.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.data.TimeFormat
import com.apollof.protocoltracker.dayStartsAtMidnight
import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseBasis
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.Formulation
import com.apollof.protocoltracker.domain.model.HAIR_SHEDDING_LABELS
import com.apollof.protocoltracker.domain.model.InjectionSites
import com.apollof.protocoltracker.domain.model.JournalEntry
import com.apollof.protocoltracker.domain.model.MarkerResult
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.SiteChoice
import com.apollof.protocoltracker.domain.model.SiteWrite
import com.apollof.protocoltracker.domain.model.Timing
import com.apollof.protocoltracker.domain.pk.LabUnits
import com.apollof.protocoltracker.domain.pk.Presets
import com.apollof.protocoltracker.domain.units.DisplayFormat
import com.apollof.protocoltracker.ui.health.BloodworkInput
import com.apollof.protocoltracker.ui.health.BloodworkSheet
import com.apollof.protocoltracker.ui.health.SymptomInput
import com.apollof.protocoltracker.ui.health.SymptomSheet
import com.apollof.protocoltracker.ui.journal.JournalScreen
import com.apollof.protocoltracker.ui.plan.ItemEditorScreen
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import com.apollof.protocoltracker.ui.today.LogDoseSheet
import com.apollof.protocoltracker.ui.today.LogTarget
import com.apollof.protocoltracker.ui.today.NoteSheet
import com.apollof.protocoltracker.ui.today.TodayScreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Restore the real screen as well as its sheet: preserving fields alone would still dismiss the draft. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp")
class SheetRestorationTest {
    @get:Rule
    val compose = createComposeRule()

    private val restoration by lazy { StateRestorationTester(compose) }
    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container
    private fun shown(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(text: String) {
        try {
            compose.waitUntil(30_000) { shown(text) }
        } catch (e: ComposeTimeoutException) {
            val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true)
            val tree = roots.fetchSemanticsNodes().indices.joinToString("\n") { roots[it].printToString() }
            throw AssertionError("Waiting for $text\n$tree", e)
        }
    }
    private fun click(text: String) = compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.OnClick)
    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))
    private fun type(label: String, text: String) = field(label).performTextReplacement(text)

    @Before
    fun setFormats(): Unit = runBlocking {
        container.dayStartsAtMidnight()
        container.settings.update { it.copy(timeFormat = TimeFormat.H24) }
        DisplayFormat.current = DisplayFormat(use24Hour = true)
    }

    @After
    fun resetFormats() { DisplayFormat.current = DisplayFormat() }

    private fun openTodaySheet(name: String) {
        runBlocking { container.repository.seedPresets() }
        restoration.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        waitFor("Log")
        compose.onNodeWithText("Log", useUnmergedTree = true).performClick()
        waitFor(name)
        click(name)
    }

    private fun restore() {
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
    }

    private fun saveEntry(): JournalEntry {
        click("Save")
        compose.waitUntil(15_000) { runBlocking { container.repository.journalNow() }.isNotEmpty() }
        return runBlocking { container.repository.journalNow().maxBy { it.createdAt } }
    }

    @Test
    fun extraDoseKeepsCompoundAmountUnitAndNote() {
        openTodaySheet("Extra dose")
        waitFor("Choose compound")
        type("Search", "anastrozole")
        waitFor("Arimidex (anastrozole)")
        click("Arimidex (anastrozole)")
        type("Dose", "0.375")
        click(DoseUnit.MCG.label)
        type("Note (optional)", "Extra after lunch")

        restore()

        waitFor("LOG EXTRA DOSE")
        field("Dose").assert(hasText("375"))
        field("Note (optional)").assert(hasText("Extra after lunch"))
        click("Log 375 ${DoseUnit.MCG.label}")
        compose.waitUntil(15_000) { runBlocking { container.repository.allLogsNow() }.isNotEmpty() }
        val saved = runBlocking { container.repository.allLogsNow().maxBy { it.createdAt } }
        assertEquals("preset:anastrozole", saved.compoundId)
        assertEquals(Amount(375.0, DoseUnit.MCG), saved.amount)
        assertEquals("Extra after lunch", saved.note)
    }

    @Test
    fun compoundSearchSurvivesBeforeSelection() {
        openTodaySheet("Extra dose")
        waitFor("Choose compound")
        type("Search", "anastrozole")
        restore()
        waitFor("Choose compound")
        field("Search").assert(hasText("anastrozole"))
    }

    @Test
    fun explicitSiteSurvivesRestorationAndReloadedHistory() {
        val compound = Presets.byId("preset:test-cyp")!!
        val chosen = InjectionSites.all[1].key
        val history = mutableStateOf(SiteChoice(null, InjectionSites.all[0].key))
        var saved: SiteWrite? = null
        restoration.setContent {
            ProtocolTrackerTheme {
                LogDoseSheet(
                    LogTarget.Unscheduled(compound), listOf(compound), Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC,
                    onDismiss = {}, onSaveScheduled = { _, _, _, _, _ -> }, onSkip = { _, _ -> },
                    onSaveUnscheduled = { _, _, _, _, site -> saved = site }, sites = { _, _ -> history.value },
                )
            }
        }
        waitFor("All sites")
        click("All sites")
        compose.onNodeWithContentDescription(InjectionSites.longLabel(chosen)).performSemanticsAction(SemanticsActions.OnClick)
        type("Dose", "100")
        compose.runOnIdle { history.value = SiteChoice(null, null) }
        restore()
        compose.runOnIdle { history.value = SiteChoice(null, InjectionSites.all[2].key) }
        compose.onNodeWithContentDescription(InjectionSites.longLabel(chosen)).assertExists()
        click("Log 100 mg")
        compose.waitUntil(15_000) { saved != null }
        assertEquals(SiteWrite.Set(chosen), saved)
    }

    @Test
    fun explicitlyClearedSiteStaysEmptyWhenHistoryReloads() {
        val compound = Presets.byId("preset:test-cyp")!!
        val suggested = InjectionSites.all[0].key
        val history = mutableStateOf(SiteChoice(null, suggested))
        var saved: SiteWrite? = null
        restoration.setContent {
            ProtocolTrackerTheme {
                LogDoseSheet(
                    LogTarget.Unscheduled(compound), listOf(compound), Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC,
                    onDismiss = {}, onSaveScheduled = { _, _, _, _, _ -> }, onSkip = { _, _ -> },
                    onSaveUnscheduled = { _, _, _, _, site -> saved = site }, sites = { _, _ -> history.value },
                )
            }
        }
        compose.onNodeWithContentDescription(InjectionSites.longLabel(suggested)).performSemanticsAction(SemanticsActions.OnClick)
        type("Dose", "100")
        restore()
        compose.runOnIdle { history.value = SiteChoice(null, InjectionSites.all[2].key) }
        click("Log 100 mg")
        compose.waitUntil(15_000) { saved != null }
        assertEquals(SiteWrite.Set(null), saved)
    }

    @Test
    fun scheduledDoseKeepsItsOccurrenceAndAdjustedAmount() {
        runBlocking {
            container.repository.seedPresets()
            container.repository.saveItem(
                PlanItem(
                    "rotation-item", null, "preset:test-cyp", Amount(100.0, DoseUnit.MG), DoseBasis.PER_DOSE,
                    Formulation(perMl = 200.0), Schedule.Daily(listOf(Timing.Slot(DaySlot.ANY_TIME))),
                    startDate = LocalDate.now(container.zone()),
                ),
            )
        }
        restoration.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        waitFor("100 mg · 0.5 mL")
        click("100 mg · 0.5 mL")
        waitFor("Dose")
        type("Dose", "125")
        restore()
        field("Dose").assert(hasText("125"))
        click("Log 125 mg")
        compose.waitUntil(15_000) { runBlocking { container.repository.allLogsNow() }.any { it.planItemId == "rotation-item" } }
        val saved = runBlocking { container.repository.allLogsNow().first { it.planItemId == "rotation-item" } }
        assertEquals(Amount(125.0, DoseUnit.MG), saved.amount)
        assertEquals(Amount(100.0, DoseUnit.MG), saved.plannedAmount)
    }

    @Test
    fun noteKeepsUnsavedTextAndDismissalStartsAFreshDraft() {
        openTodaySheet("Note")
        waitFor("What happened")
        type("What happened", "A note before rotation")
        restore()
        waitFor("What happened")
        field("What happened").assert(hasText("A note before rotation"))
        click("Cancel")
        waitFor("Log")
        compose.onNodeWithText("Log", useUnmergedTree = true).performClick()
        waitFor("Note")
        click("Note")
        waitFor("What happened")
        field("What happened").assert(hasText(""))
    }

    @Test
    fun symptomsKeepSelectionsAndNote() {
        openTodaySheet("Symptoms")
        waitFor("Night sweats")
        click("Night sweats")
        click("7")
        click(HAIR_SHEDDING_LABELS[1])
        type("Note (optional)", "Sleep interrupted")
        restore()
        waitFor("Night sweats")
        field("Note (optional)").assert(hasText("Sleep interrupted"))
        val saved = saveEntry() as JournalEntry.Symptoms
        assertEquals(listOf("night_sweats"), saved.symptoms)
        assertEquals(7, saved.mood)
        assertEquals(2, saved.hairShedding)
        assertEquals("Sleep interrupted", saved.note)
    }

    @Test
    fun clearedMoodHairAndPickedTimeDoNotReturnOnRestoration() {
        val oldTime = Instant.parse("2026-10-01T07:30:00Z")
        val now = Instant.parse("2026-10-02T12:00:00Z")
        val original = JournalEntry.Symptoms("symptoms", oldTime, listOf("night_sweats"), 7, 2, createdAt = oldTime)
        var saved: SymptomInput? = null
        restoration.setContent {
            ProtocolTrackerTheme {
                SymptomSheet(now, ZoneOffset.UTC, onDismiss = {}, onSave = { saved = it }, existing = original)
            }
        }
        click("7")
        click(HAIR_SHEDDING_LABELS[1])
        compose.onNodeWithText("Now ·", substring = true).performSemanticsAction(SemanticsActions.OnClick)
        restore()
        click("Save")
        val result = assertNotNull(saved)
        assertEquals(null, result.mood)
        assertEquals(null, result.hairShedding)
        assertEquals(now, result.at)
        assertEquals(original.symptoms, result.symptoms)
    }

    @Test
    fun bloodPressureKeepsAllFields() {
        openTodaySheet("Blood pressure")
        waitFor("Systolic")
        type("Systolic", "123")
        type("Diastolic", "81")
        type("Pulse (optional)", "67")
        type("Note (optional)", "Seated")
        restore()
        waitFor("Systolic")
        field("Systolic").assert(hasText("123"))
        field("Diastolic").assert(hasText("81"))
        field("Pulse (optional)").assert(hasText("67"))
        val saved = saveEntry() as JournalEntry.BloodPressure
        assertEquals(123, saved.systolic)
        assertEquals(81, saved.diastolic)
        assertEquals(67, saved.pulse)
        assertEquals("Seated", saved.note)
    }

    @Test
    fun bloodworkKeepsTypedMarkersUnitsAndDrawDetails() {
        openTodaySheet("Bloodwork")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("More markers", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("More markers", substring = true).performSemanticsAction(SemanticsActions.OnClick)
        click(LabUnits.SI.label)
        type("Hemoglobin", "9.9")
        type("Lab (optional)", "Lab A")
        type("Note (optional)", "Fasted")
        restore()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("More markers", substring = true).fetchSemanticsNodes().isNotEmpty() }
        field("Hemoglobin").assert(hasText("9.9"))
        val saved = saveEntry() as JournalEntry.Bloodwork
        assertEquals(15.9489, saved.result("hemoglobin")!!.value, 0.00001)
        assertEquals("Lab A", saved.lab)
        assertEquals("Fasted", saved.note)
    }

    @Test
    fun journalNoteSheetAlsoKeepsItsDraft() {
        restoration.setContent { ProtocolTrackerTheme { JournalScreen(onOpenSettings = {}) } }
        compose.onNodeWithContentDescription("Add entry").performSemanticsAction(SemanticsActions.OnClick)
        waitFor("Note")
        click("Note")
        waitFor("What happened")
        type("What happened", "Journal draft")
        restore()
        waitFor("What happened")
        field("What happened").assert(hasText("Journal draft"))
        assertEquals("Journal draft", (saveEntry() as JournalEntry.Note).text)
    }

    @Test
    fun planCompoundPickerKeepsItsSearch() {
        runBlocking { container.repository.seedPresets() }
        restoration.setContent {
            ProtocolTrackerTheme { ItemEditorScreen(null, null, onDone = {}, onNewCompound = {}) }
        }
        waitFor("Choose compound")
        type("Search", "anastrozole")
        restore()
        field("Search").assert(hasText("anastrozole"))
        click("Arimidex (anastrozole)")
        compose.onNodeWithText("Choose compound").assertDoesNotExist()
        restore()
        compose.onNodeWithText("Choose compound").assertDoesNotExist()
    }

    @Test
    fun earlierTimeAndOpenPickerSurviveRestoration() {
        DisplayFormat.current = DisplayFormat(use24Hour = true)
        val now = Instant.parse("2026-10-02T00:30:00Z")
        val expected = Instant.parse("2026-10-01T23:30:00Z")
        var saved: Instant? = null
        restoration.setContent {
            ProtocolTrackerTheme { NoteSheet(now, ZoneOffset.UTC, onDismiss = {}, onSave = { _, at -> saved = at }) }
        }
        type("What happened", "Before midnight")
        click("Earlier…")
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("23", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(hasContentDescription("23", substring = true), useUnmergedTree = true)[0]
            .performClick()
        restore()
        click("OK")
        restore()
        click("Save")
        assertEquals(expected, saved)
    }

    @Test
    fun journalDoseEditKeepsItsSnapshotAndChanges() {
        runBlocking {
            container.repository.seedPresets()
            val compound = Presets.byId("preset:test-cyp")!!
            container.repository.logUnscheduled(compound, Amount(125.0, DoseUnit.MG), compound.defaultFormulation, Instant.now())
        }
        val original = runBlocking { container.repository.allLogsNow().single() }
        restoration.setContent { ProtocolTrackerTheme { JournalScreen(onOpenSettings = {}) } }
        waitFor("125 mg · 0.63 mL")
        compose.onNode(hasClickAction() and hasText("125 mg", substring = true)).performSemanticsAction(SemanticsActions.OnClick)
        waitFor("EDIT DOSE")
        type("Dose", "150")
        type("Note (optional)", "Changed before rotation")
        restore()
        field("Dose").assert(hasText("150"))
        click("Save")
        compose.waitUntil(15_000) {
            runBlocking { container.repository.allLogsNow().single().note } == "Changed before rotation"
        }
        val saved = runBlocking { container.repository.allLogsNow().single() }
        assertEquals(original.copy(amount = Amount(150.0, DoseUnit.MG), note = "Changed before rotation"), saved)
    }

    @Test
    fun todayExistingNoteKeepsTheEntryBeingEdited() {
        val at = Instant.now()
        val original = JournalEntry.Note("existing-note", at, "Original note", at)
        runBlocking { container.repository.saveJournal(original) }
        restoration.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        waitFor("Original note")
        click("Original note")
        waitFor("What happened")
        type("What happened", "Edited before rotation")
        restore()
        field("What happened").assert(hasText("Edited before rotation"))
        click("Save")
        compose.waitUntil(15_000) {
            runBlocking { (container.repository.journalNow().single() as JournalEntry.Note).text } == "Edited before rotation"
        }
        val saved = runBlocking { container.repository.journalNow().single() as JournalEntry.Note }
        assertEquals(original.id, saved.id)
        assertEquals(original.createdAt.toEpochMilli(), saved.createdAt.toEpochMilli())
        assertEquals(original.at.toEpochMilli(), saved.at.toEpochMilli())
    }

    @Test
    fun bloodworkEditKeepsClearedAndUntouchedResultsAndDrawPickers() {
        val at = Instant.parse("2026-10-01T07:30:00Z")
        val kept = MarkerResult("lh", 0.3, qualifier = "<", refLow = 1.7, refHigh = 8.6)
        val removed = MarkerResult("creatinine", 1.085952)
        val original = JournalEntry.Bloodwork("draw", at, listOf(kept, removed), createdAt = at)
        var saved: BloodworkInput? = null
        restoration.setContent {
            ProtocolTrackerTheme {
                BloodworkSheet(at, ZoneOffset.UTC, LabUnits.CONVENTIONAL, onDismiss = {}, onSave = { saved = it }, existing = original)
            }
        }
        type("Creatinine", "")
        compose.onNodeWithContentDescription("Choose Blood draw").performSemanticsAction(SemanticsActions.OnClick)
        waitFor("Thursday, October 15, 2026")
        click("Thursday, October 15, 2026")
        restore()
        click("OK")
        compose.onNodeWithContentDescription("Choose Time").performSemanticsAction(SemanticsActions.OnClick)
        restore()
        click("OK")
        restore()
        click("Save")
        val result = assertNotNull(saved)
        assertEquals(listOf(kept), result.results)
        assertEquals(Instant.parse("2026-10-15T07:30:00Z"), result.at)
    }
}
