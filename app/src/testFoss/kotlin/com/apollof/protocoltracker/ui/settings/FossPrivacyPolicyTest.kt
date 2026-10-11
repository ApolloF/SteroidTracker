package com.apollof.protocoltracker.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apollof.protocoltracker.R
import java.io.File
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Settings › Privacy policy in foss shows PRIVACY.md (the published policy) word for word. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class FossPrivacyPolicyTest {
    @Test
    fun bundledPolicyIsPrivacyMd() {
        val resources = ApplicationProvider.getApplicationContext<Application>().resources
        val bundled = resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() }
        // Unit tests run in the app module's folder.
        val published = File("../PRIVACY.md").readText()
        assertEquals(published.replace("\r\n", "\n"), bundled.replace("\r\n", "\n"), "copy PRIVACY.md to app/src/main/res/raw/privacy_policy.md")
    }
}
