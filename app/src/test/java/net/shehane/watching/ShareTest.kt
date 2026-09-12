package net.shehane.watching

import net.shehane.watching.data.Share
import net.shehane.watching.model.Library
import net.shehane.watching.model.Service
import net.shehane.watching.model.Show
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The message that leaves the phone. Worth pinning down because it is the one
 * thing in this app another person reads, and because it must not carry our own
 * filing with it.
 */
class ShareTest {

    private val library = Library(
        services = listOf(
            Service("appletv", "Apple TV+", "#D6D2CC", profiles = emptyList()),
        ),
    )

    private fun show(
        title: String = "Silo",
        year: Int? = 2023,
        serviceId: String? = "appletv",
        wikipediaUrl: String? = null,
    ) = Show(
        id = "1",
        tmdbId = 125988,
        title = title,
        year = year,
        serviceId = serviceId,
        profileId = "family",
        wikipediaUrl = wikipediaUrl,
    )

    @Test
    fun `the opener leads, because that is the part addressed to a person`() {
        val text = Share.message(library, show(), Share.Opener.WAIT)
        assertTrue(text.startsWith("Interested, should we wait for you?"))
    }

    @Test
    fun `the show, its year and its service all travel`() {
        val text = Share.message(library, show(), Share.Opener.RECOMMEND)
        assertTrue(text.contains("Silo (2023)"))
        assertTrue(text.contains("On Apple TV+"))
    }

    @Test
    fun `the profile never travels, because it is our filing and not theirs`() {
        val text = Share.message(library, show(), Share.Opener.RECOMMEND)
        assertFalse(text.contains("family", ignoreCase = true))
    }

    @Test
    fun `a show with no service still sends something worth reading`() {
        val text = Share.message(library, show(serviceId = null), Share.Opener.CONFIRM)
        assertTrue(text.contains("Silo"))
        assertFalse(text.contains("On "))
    }

    @Test
    fun `a show with no year does not send an empty bracket`() {
        val text = Share.message(library, show(year = null), Share.Opener.CONFIRM)
        assertTrue(text.contains("Silo"))
        assertFalse(text.contains("("))
    }

    @Test
    fun `the link is included when we have one`() {
        val url = "https://en.wikipedia.org/wiki/Silo_(TV_series)"
        val text = Share.message(library, show(wikipediaUrl = url), Share.Opener.RECOMMEND)
        assertTrue(text.trimEnd().endsWith(url))
    }

    @Test
    fun `all three openers are offered and none is blank`() {
        assertEquals(3, Share.Opener.entries.size)
        assertTrue(Share.Opener.entries.all { it.text.isNotBlank() })
    }
}
