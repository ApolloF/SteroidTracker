package com.apollof.protocoltracker.domain.io

import com.apollof.protocoltracker.domain.io.PolicyText.Bullet
import com.apollof.protocoltracker.domain.io.PolicyText.Heading
import com.apollof.protocoltracker.domain.io.PolicyText.Paragraph
import kotlin.test.Test
import kotlin.test.assertEquals

class PolicyTextTest {
    @Test
    fun readsHeadingsParagraphsAndBullets() {
        val text = """
            # App privacy policy

            Last updated: 4 October 2026

            The app keeps your data
            on this phone.

            ## Permissions

            - **Notifications:** for dose reminders.
            - Run at startup: to set *reminders*
              again after a restart.
            After the list.
        """.trimIndent()
        assertEquals(
            listOf(
                Heading(1, "App privacy policy"),
                Paragraph("Last updated: 4 October 2026"),
                Paragraph("The app keeps your data on this phone."),
                Heading(2, "Permissions"),
                Bullet("Notifications: for dose reminders."),
                Bullet("Run at startup: to set reminders again after a restart."),
                Paragraph("After the list."),
            ),
            PolicyText.parse(text),
        )
    }

    @Test
    fun emptyTextHasNoBlocks() {
        assertEquals(emptyList(), PolicyText.parse(""))
        assertEquals(emptyList(), PolicyText.parse("\n\n  \n"))
    }
}
