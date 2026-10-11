package com.apollof.protocoltracker.ui.today

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.StartupUpkeep
import com.apollof.protocoltracker.ui.ScreenshotApp
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** A failed startup step is shown on Today, not only logged. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp", application = ScreenshotApp::class)
class UpkeepFailedTest {
    @get:Rule
    val compose = createComposeRule()

    private val container get() = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().container

    private fun count(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    @Test
    fun todaySaysWhichStepFailed() {
        compose.setContent { ProtocolTrackerTheme { TodayScreen(onOpenSettings = {}, onOpenPlan = {}) } }
        compose.waitForIdle()
        assertEquals(0, count("Startup update failed"))
        runBlocking { container.upkeep.run(listOf(StartupUpkeep.Step("updating presets") { error("disk full") })) }
        compose.waitUntil(15_000) { count("Startup update failed") > 0 }
        assertEquals(1, count("Not done: updating presets."))
    }
}
