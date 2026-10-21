package com.apollof.protocoltracker.domain.schedule

import java.time.LocalDate

/** One dose on the home-screen widget; [label] is its part of the day, clock time or (missed) day. */
data class WidgetLine(val entry: AgendaEntry, val label: String) {
    /** The one-tap check logs missed and pending doses; done ones only show their state. */
    val checkable: Boolean get() = !entry.done
}

/**
 * Today's agenda as the widget lists it: missed doses of earlier days first (labelled by [dayLabel]), then each group's
 * doses in plan order with the done ones in place, so a row keeps its position when it is checked.
 */
fun Agenda.widgetLines(dayLabel: (LocalDate) -> String): List<WidgetLine> =
    missed.mapNotNull { e -> e.occurrence?.let { WidgetLine(e, dayLabel(it.localDate)) } } +
        groups.flatMap { g -> g.entries.filter { it.occurrence != null }.map { WidgetLine(it, g.label) } }

/** The dose the compact widget shows: the first one still to log, missed doses first. */
fun List<WidgetLine>.nextDose(): WidgetLine? = firstOrNull { it.checkable }
