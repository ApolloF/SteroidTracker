package com.apollof.protocoltracker

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class StartupUpkeepTest {
    @Test
    fun aFailedStepIsRecordedAndTheNextStillRuns() = runBlocking {
        val ran = ArrayList<String>()
        val upkeep = StartupUpkeep(
            listOf(
                StartupUpkeep.Step("updating presets") { ran += "presets"; error("disk full") },
                StartupUpkeep.Step("updating log times") { ran += "times" },
            ),
        )
        upkeep.run()
        assertEquals(listOf("presets", "times"), ran)
        assertEquals(listOf("updating presets"), upkeep.failed.value)
    }

    @Test
    fun cancellationIsNotAFailure() {
        val upkeep = StartupUpkeep(listOf(StartupUpkeep.Step("updating presets") { throw CancellationException("stopped") }))
        assertFailsWith<CancellationException> { runBlocking { upkeep.run() } }
        assertEquals(emptyList(), upkeep.failed.value)
    }
}
