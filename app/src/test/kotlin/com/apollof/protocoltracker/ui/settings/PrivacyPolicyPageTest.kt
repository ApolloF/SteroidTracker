package com.apollof.protocoltracker.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.ProtocolTrackerApp
import com.apollof.protocoltracker.R
import com.apollof.protocoltracker.ui.theme.ProtocolTrackerTheme
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings › Privacy policy shows the bundled policy: its sections, bullets and where the same text is published. */
@RunWith(AndroidJUnit4::class)
class PrivacyPolicyPageTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsTheBundledPolicy() {
        compose.setContent { ProtocolTrackerTheme { SettingsPageScreen(SettingsPage.PRIVACY, onBack = {}) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Permissions").fetchSemanticsNodes().isNotEmpty() }
        // Bold marks are dropped; the title line is not repeated under the page title.
        compose.onNodeWithText("Notifications, exact alarms and vibration:", substring = true).performScrollTo().assertIsDisplayed()
        for (title in listOf("SteroidTracker privacy policy", "Privacy policy (Google Play version)")) {
            assertTrue(compose.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty(), title)
        }
        val where = ApplicationProvider.getApplicationContext<ProtocolTrackerApp>().getString(R.string.privacy_policy_where)
        compose.onNodeWithText(where).performScrollTo().assertIsDisplayed()
    }
}
