package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.LevelUnit
import com.apollof.protocoltracker.domain.model.PkParams
import com.apollof.protocoltracker.domain.model.Rise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class PresetMigrationTest {
    private val testE = Presets.byId("preset:test-enan")!!.pk!!

    @Test
    fun legacyPresetKineticsBecomeTheCurrentOnes() {
        val legacy = PkParams(172.56, 33.3, 11.3095, 0.72, LevelUnit.NG_DL)
        assertEquals(testE, PresetMigration.upgrade("preset:test-enan", legacy))
        // F is ignored: presets-2026-09b differed from -10a only there.
        val nebido = Presets.byId("preset:test-undec")!!.pk
        assertEquals(nebido, PresetMigration.upgrade("preset:test-undec", PkParams(813.5999999999999, 240.0, 1.187, 0.65, LevelUnit.NG_DL)))
        // Basic compounds were relative (no peak) and are now absolute.
        val tren = Presets.byId("preset:tren-enan")!!.pk
        assertEquals(tren, PresetMigration.upgrade("preset:tren-enan", PkParams(264.0, 48.0, null, 0.71, LevelUnit.NG_DL)))
    }

    @Test
    fun userKineticsAndUnknownCompoundsStay() {
        val edited = PkParams(172.56, 33.3, 12.0, 0.72, LevelUnit.NG_DL)
        assertSame(edited, PresetMigration.upgrade("preset:test-enan", edited))
        val custom = PkParams(172.56, 33.3, 11.3095, 0.72, LevelUnit.NG_DL)
        assertSame(custom, PresetMigration.upgrade("custom-1", custom))
        assertSame(testE, PresetMigration.upgrade("preset:test-enan", testE))
        assertNull(PresetMigration.upgrade("preset:test-enan", null))
    }

    @Test
    fun currentPresetKineticsAreLeftAlone() {
        for (preset in Presets.all) assertEquals(preset.pk, PresetMigration.upgrade(preset.id, preset.pk), preset.id)
    }

    @Test
    fun anchoringChangesOfPresets10cBecomeTheCurrentOnes() {
        val tren10c = PkParams(264.0, 99.0, 8.6975, 0.71, LevelUnit.NG_DL, Rise.FIRST_ORDER)
        val trenNow = Presets.byId("preset:tren-enan")!!.pk
        assertEquals(trenNow, PresetMigration.upgrade("preset:tren-enan", tren10c))
        val dec10c = PkParams(134.39999999999998, 50.4, 7.9625, 0.65, LevelUnit.NG_DL, Rise.FIRST_ORDER)
        assertEquals(Presets.byId("preset:test-dec")!!.pk, PresetMigration.upgrade("preset:test-dec", dec10c))
        // F is ignored here too.
        assertEquals(trenNow, PresetMigration.upgrade("preset:tren-enan", tren10c.copy(activeFraction = 0.5)))
    }

    @Test
    fun editedCopyOfPresets10cStays() {
        val edited = PkParams(264.0, 99.0, 9.0, 0.71, LevelUnit.NG_DL, Rise.FIRST_ORDER)
        assertSame(edited, PresetMigration.upgrade("preset:tren-enan", edited))
    }

    @Test
    fun telmisartanOf10cBecomesThe40MgArm() {
        // As stored in a v0.6.0 backup (presets-2026-10c).
        val stored = PkParams(23.2999999992, 1.69999999992, 770.0625, 0.43, LevelUnit.NG_ML)
        val now = Presets.byId("preset:telmisartan")!!.pk!!
        assertEquals(now, PresetMigration.upgrade("preset:telmisartan", stored))
        assertEquals(408.0, now.peakPerUnit!!, 1e-9)
    }
}
