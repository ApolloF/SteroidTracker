package com.apollof.protocoltracker.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.R
import com.apollof.protocoltracker.domain.io.PolicyText
import com.apollof.protocoltracker.domain.pk.PlayWording
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Settings › Privacy policy in Play: its own text, which names the billing permissions and claims nothing offline. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PlayPrivacyPolicyTest {
    private val text: String by lazy {
        ApplicationProvider.getApplicationContext<Application>().resources
            .openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() }
    }

    @Test
    fun isThePlayPolicy() {
        assertTrue(text.startsWith("# Privacy policy (Google Play version)"), text.lineSequence().first())
        for (needed in listOf("Pro purchase", "Internet, view network connections and Google Play billing", "me@apollof.nl")) {
            assertTrue(needed in text, "missing \"$needed\"")
        }
    }

    @Test
    fun noBannedWordOrOfflineClaim() {
        for (block in PolicyText.parse(text)) {
            assertNull(PlayWording.bannedTokenIn(block.text), block.text)
            val lower = block.text.lowercase()
            for (claim in listOf("offline", "no internet", "no network", "network access", "cycletracker", "steroidtracker")) {
                assertFalse(claim in lower, "says \"$claim\": ${block.text}")
            }
        }
    }
}
