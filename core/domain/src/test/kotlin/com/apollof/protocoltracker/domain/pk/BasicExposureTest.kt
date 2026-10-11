package com.apollof.protocoltracker.domain.pk

import com.apollof.protocoltracker.domain.model.PkParams
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Basic esters with exposure anchors give the same exposure per mg of hormone as their anchors (docs/MODELS.md). */
class BasicExposureTest {
    private val anchored = mapOf(
        "test-pp" to PkSheet.TEST_PP,
        "test-iso" to PkSheet.TEST_ISO,
        "test-dec" to PkSheet.TEST_DEC,
        "tren-enan" to PkSheet.TREN_E,
        "tren-hex" to PkSheet.TREN_HEX,
        "mast-enan" to PkSheet.MAST_E,
    )
    private val byRow = mapOf(
        PkSheet.TEST_E to "test-enan", PkSheet.TEST_C to "test-cyp", PkSheet.TEST_P to "test-prop",
        PkSheet.TREN_A to "tren-ace", PkSheet.MAST_P to "mast-prop",
    )

    private fun pk(id: String) = assertNotNull(Presets.byId("preset:$id")?.pk, id)

    /** Area of one 1 mg dose by the closed form, per mg of hormone (ng/dL·h). */
    private fun formulaExposure(id: String, row: PkSheet.SheetRow): Double {
        val p = pk(id)
        return p.peakPerUnit!! * p.areaPerPeakH / row.bioavailability!!
    }

    /** Area of one 1 mg dose summed on a 0.25 h grid out to Tmax + 20 half-lives, per mg of hormone. */
    private fun numericExposure(id: String, row: PkSheet.SheetRow): Double {
        val p = pk(id)
        val stepMs = 900_000L
        val endH = p.tmaxH + 20 * p.halfLifeH
        val times = LongArray((endH * 4).toInt() + 1) { it * stepMs }
        val dose = CurveDose(0, p.peakPerUnit!!, p.tmaxH, p.halfLifeH, p.rise)
        val levels = CurveEngine.simulate(listOf(dose), times)
        var area = 0.0
        for (i in 1 until levels.size) area += (levels[i - 1] + levels[i]) / 2 * 0.25
        return area / row.bioavailability!!
    }

    private fun rowOf(id: String): PkSheet.SheetRow = byRow.entries.first { it.value == id }.key

    @Test
    fun anchoredExposureEqualsTheMeanOfTheAnchors() {
        for ((id, row) in anchored) {
            val anchors = row.exposureAnchors
            assertTrue(anchors.isNotEmpty(), id)
            val expected = anchors.map { formulaExposure(byRow.getValue(it), it) }.average()
            assertEquals(1.0, formulaExposure(id, row) / expected, 1e-9, id)
            val expectedNumeric = anchors.map { numericExposure(byRow.getValue(it), it) }.average()
            assertEquals(1.0, numericExposure(id, row) / expectedNumeric, 0.005, "$id numeric")
        }
    }

    @Test
    fun perMgHormoneRatiosAreOneWhereTheyWere5xOrMore() {
        fun ratio(id: String, other: String) =
            formulaExposure(id, anchored.getValue(id)) / formulaExposure(other, rowOf(other))
        assertEquals(1.0, ratio("tren-enan", "tren-ace"), 1e-9) // was 7.3
        assertEquals(1.0, ratio("tren-hex", "tren-ace"), 1e-9) // was 5.3
        assertEquals(1.0, ratio("mast-enan", "mast-prop"), 1e-9) // was 2.25
        // Test Dec against Test E: the study-based esters differ by ~10 % among themselves.
        val testDec = ratio("test-dec", "test-enan")
        assertTrue(abs(testDec - 1) < 0.1, "Test Dec / Test E = $testDec") // was 1.9
    }

    @Test
    fun peaksPerMg() {
        assertEquals(9.701, pk("test-pp").peakPerUnit!!, 0.001)
        assertEquals(8.504, pk("test-iso").peakPerUnit!!, 0.001)
        assertEquals(4.080, pk("test-dec").peakPerUnit!!, 0.001)
        assertEquals(1.186, pk("tren-enan").peakPerUnit!!, 0.001)
        assertEquals(1.516, pk("tren-hex").peakPerUnit!!, 0.001)
        assertEquals(3.974, pk("mast-enan").peakPerUnit!!, 0.001)
    }

    @Test
    fun unanchoredBasicRowsKeepThePlainPeak() {
        for (row in PkSheet.rows) {
            if (row.model != PkSheet.Model.BASIC || row.exposureAnchors.isNotEmpty()) continue
            val pk: PkParams = PkSheetModel.pk(row)
            assertEquals(14 * row.multiplier * row.bioavailability!! * 0.875, pk.peakPerUnit!!, 1e-12, row.compound + " " + row.type)
        }
        assertEquals(10.6575, pk("tren-ace").peakPerUnit!!, 1e-9)
        assertEquals(10.29, pk("mast-prop").peakPerUnit!!, 1e-9)
    }

    @Test
    fun anchorsAreBasicRowsOfTheSameHormone() {
        for (row in PkSheet.rows) {
            if (row.exposureAnchors.isEmpty()) continue
            assertEquals(PkSheet.Model.BASIC, row.model, row.type)
            assertTrue(row.exposureAnchors.all { it.compound == row.compound && it.exposureAnchors.isEmpty() }, row.type)
        }
    }

    @Test
    fun shapeOfAnchoredRowsIsUnchanged() {
        for ((id, row) in anchored) {
            assertEquals(row.halfLifeD!! * 24, pk(id).halfLifeH, 1e-9, id)
            assertEquals(row.halfLifeD * 24 * 3 / 8, pk(id).tmaxH, 1e-9, id)
        }
    }
}
