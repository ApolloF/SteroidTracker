package com.apollof.protocoltracker.domain.pk

import java.time.Instant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every preset's single dose, drawn the way the Levels chart draws it (`LevelScale.curve`, `Levels.sample`): the sampled
 * curve peaks exactly at the preset's Tmax, the peak is the preset's Cmax per unit (in its display unit, or the active
 * amount for a relative curve), it meets the decay without a jump, and it halves one half-life after the peak.
 */
class CmaxTmaxTest {
    private val hourMs = 3_600_000.0

    @Test
    fun everyPresetPeaksAtItsTmaxWithItsCmax() {
        val presets = Presets.all.filter { it.pk != null }
        assertTrue(presets.size > 50, "presets with kinetics: ${presets.size}")
        for (preset in presets) {
            val pk = preset.pk!!
            val relative = pk.peakPerUnit == null
            val scale = LevelScale(relative, pk.levelUnit, preset.baseUnit)
            val cmax = if (relative) pk.activeFraction else pk.peakPerUnit * pk.levelUnit.perNgDl
            val dose = scale.curve(DoseEvent(0, 1.0, pk, planned = false))
            val tmaxMs = Math.round(pk.tmaxH * hourMs)
            val series = Levels.sample(listOf(dose), Instant.EPOCH, Instant.ofEpochMilli(Math.round((pk.tmaxH + 3 * pk.halfLifeH) * hourMs)), points = 2000)

            val peakAt = series.values.indices.maxBy { series.values[it] }
            assertEquals(tmaxMs, series.times[peakAt], "${preset.id}: Tmax")
            assertClose(cmax, series.values[peakAt], "${preset.id}: Cmax")
            // No jump where the rise meets the decay (1 ms either side moves a 14-minute peak by about 1e-6).
            assertClose(cmax, CurveEngine.contribution(dose, tmaxMs - 1), "${preset.id}: rise ends at Cmax", 1e-5)
            assertClose(cmax, CurveEngine.contribution(dose, tmaxMs + 1), "${preset.id}: decay starts at Cmax", 1e-5)
            assertClose(cmax / 2, CurveEngine.contribution(dose, tmaxMs + Math.round(pk.halfLifeH * hourMs)), "${preset.id}: half-life", 1e-6)
        }
    }

    /** The figures the sheet gives for a typical dose, end to end (Cmax per mg × multiplier × dose, Tmax in days). */
    @Test
    fun typicalDosesPeakWhereTheSheetSays() {
        fun peak(id: String, dose: Double): Pair<Double, Double> {
            val preset = Presets.byId("preset:$id")!!
            val pk = preset.pk!!
            val curve = LevelScale(false, pk.levelUnit, preset.baseUnit).curve(DoseEvent(0, dose, pk, planned = false))
            val series = Levels.sample(listOf(curve), Instant.EPOCH, Instant.ofEpochMilli(Math.round((pk.tmaxH + pk.halfLifeH) * hourMs)), points = 2000)
            val i = series.values.indices.maxBy { series.values[it] }
            return series.values[i] to series.times[i] / hourMs
        }
        // Test C 200 mg: 5.56 ng/dL per mg × 0.6 × 200 at 4.5 d.
        peak("test-cyp", 200.0).let { (c, t) -> assertClose(667.2, c, "Test C Cmax"); assertClose(108.0, t, "Test C Tmax") }
        // Test E 250 mg: 11.3095 × 0.33 × 250 = 933.0 ng/dL at 1.3875 d (docs/MODELS.md, sampling note).
        peak("test-enan", 250.0).let { (c, t) -> assertClose(933.03, c, "Test E Cmax", 1e-5); assertClose(33.3, t, "Test E Tmax") }
        // Anavar 20 mg: 772 × 0.3 × 20 = 4632 ng/dL at 1.6 h.
        peak("oxandrolone", 20.0).let { (c, t) -> assertClose(4632.0, c, "Anavar Cmax", 1e-5); assertClose(1.6, t, "Anavar Tmax", 1e-6) }
        // Tadalafil 20 mg: 345 ng/mL at 2.5 h.
        peak("tadalafil", 20.0).let { (c, t) -> assertClose(345.0, c, "Tadalafil Cmax"); assertClose(2.5, t, "Tadalafil Tmax", 1e-6) }
    }

    private fun assertClose(expected: Double, actual: Double, what: String, rel: Double = 1e-9) =
        assertTrue(abs(actual - expected) <= rel * abs(expected), "$what: expected $expected, got $actual")
}
