package com.apollof.protocoltracker.domain.schedule

import com.apollof.protocoltracker.domain.model.DoseLog
import com.apollof.protocoltracker.domain.model.LogStatus
import java.time.Instant

/** A taken dose of a plan item: which occurrence it confirmed and when it was actually taken. */
data class TakenDose(val planItemId: String, val occurrenceKey: String, val takenAt: Instant)

/** A taken unscheduled dose of a compound: it restarts that compound's interval plan items too. */
data class ExtraDose(val compoundId: String, val takenAt: Instant)

/**
 * Taken doses that re-anchor interval schedules with "count from last dose" on (see [occurrences]).
 * Must hold every taken dose, not a window: the deciding dose can be older than any view's range.
 */
class IntervalAnchors(doses: Collection<TakenDose>, extras: Collection<ExtraDose> = emptyList()) {
    private val byItem: Map<String, List<TakenDose>> = doses.groupBy { it.planItemId }
    private val extrasByCompound: Map<String, List<Instant>> = extras.groupBy({ it.compoundId }, { it.takenAt })

    fun forItem(itemId: String): List<TakenDose> = byItem[itemId].orEmpty()

    fun extrasFor(compoundId: String): List<Instant> = extrasByCompound[compoundId].orEmpty()

    val isEmpty: Boolean get() = byItem.isEmpty() && extrasByCompound.isEmpty()

    override fun equals(other: Any?): Boolean =
        other is IntervalAnchors && other.byItem == byItem && other.extrasByCompound == extrasByCompound
    override fun hashCode(): Int = 31 * byItem.hashCode() + extrasByCompound.hashCode()

    companion object {
        /** Plain plan grid, ignoring logs. */
        val NONE = IntervalAnchors(emptyList())

        /** Anchors from logs; only taken doses count (skipped doses never move the plan). */
        fun from(logs: Collection<DoseLog>): IntervalAnchors {
            val taken = logs.filter { it.status == LogStatus.TAKEN }
            return IntervalAnchors(
                taken.mapNotNull { log -> TakenDose(log.planItemId ?: return@mapNotNull null, log.occurrenceKey ?: return@mapNotNull null, log.takenAt) },
                taken.filter { it.occurrenceKey == null }.map { ExtraDose(it.compoundId, it.takenAt) },
            )
        }
    }
}
