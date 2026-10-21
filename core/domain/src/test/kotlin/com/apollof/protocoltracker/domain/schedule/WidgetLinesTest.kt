package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.Amount
import com.apollof.protocoltracker.domain.model.BaseUnit
import com.apollof.protocoltracker.domain.model.CompoundCategory
import com.apollof.protocoltracker.domain.model.DaySlot
import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.DoseSnapshot
import com.apollof.protocoltracker.domain.model.DoseUnit
import com.apollof.protocoltracker.domain.model.LogStatus
import com.apollof.protocoltracker.domain.model.PlanItem
import com.apollof.protocoltracker.domain.model.Schedule
import com.apollof.protocoltracker.domain.model.Timing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WidgetLinesTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val morning = PlanItem("m", null, "c", Amount(40.0, DoseUnit.MG), schedule = Schedule.Daily(listOf(Timing.Slot(DaySlot.MORNING))), sortOrder = 1)
    private val pre = PlanItem("p", null, "c", Amount(50.0, DoseUnit.MG), schedule = Schedule.Daily(listOf(Timing.Slot(DaySlot.PRE_WORKOUT))), sortOrder = 2)

    private fun at(date: String, time: String) = ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone).toInstant()
    private fun log(key: String, takenAt: Instant, status: LogStatus = LogStatus.TAKEN) = DoseLog(
        key, "i", "c", key, null, takenAt, Amount(1.0, DoseUnit.MG), null, status,
        snapshot = DoseSnapshot("C", "C", CompoundCategory.SUPPORT, BaseUnit.MG, null), createdAt = takenAt,
    )

    @Test
    fun missedFirstThenTodayInPlanOrderWithDoneInPlace() {
        val now = at("2026-09-25", "09:00")
        val logs = listOf(
            log(slotOccurrenceKey("m", LocalDate.parse("2026-09-25"), DaySlot.MORNING), at("2026-09-25", "08:10")),
            log(slotOccurrenceKey("m", LocalDate.parse("2026-09-24"), DaySlot.MORNING), at("2026-09-24", "08:10")),
            log(slotOccurrenceKey("m", LocalDate.parse("2026-09-23"), DaySlot.MORNING), at("2026-09-23", "08:10")),
        )
        val lines = buildAgenda(emptyList(), listOf(morning, pre), logs, now, zone, IntervalAnchors.NONE)
            .widgetLines { it.dayOfWeek.name.take(3) }

        assertEquals(listOf("WED", "THU", "Morning", "Pre-workout"), lines.map { it.label })
        assertEquals(listOf(true, true, false, true), lines.map { it.checkable })
        assertEquals(AgendaStatus.MISSED, lines.nextDose()!!.entry.status)
    }

    @Test
    fun nothingLeftHasNoNextDose() {
        val now = at("2026-09-25", "09:00")
        val key = slotOccurrenceKey("m", LocalDate.parse("2026-09-25"), DaySlot.MORNING)
        val logs = listOf(log(key, at("2026-09-25", "08:10"), LogStatus.SKIPPED))
        val lines = buildAgenda(emptyList(), listOf(morning), logs, now, zone, IntervalAnchors.NONE).widgetLines { "" }
            .filter { it.label == "Morning" }

        assertEquals(listOf(AgendaStatus.SKIPPED), lines.map { it.entry.status })
        assertNull(lines.nextDose())
    }
}
