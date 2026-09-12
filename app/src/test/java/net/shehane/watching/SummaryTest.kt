package net.shehane.watching

import net.shehane.watching.data.Recap
import net.shehane.watching.data.Summary
import net.shehane.watching.model.Library
import net.shehane.watching.model.Position
import net.shehane.watching.model.Show
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which summariser answers, and what reaches it.
 *
 * The second half matters most: whatever model runs, it can only ever see text
 * that is already behind the position, so a spoiler would have to be invented
 * rather than leaked.
 */
class SummaryTest {

    // --------------------------------------------------------- falling through

    @Test
    fun `cloud runs when there is a key and a network`() {
        val (source, why) = Summary.resolve(
            Library.SUMMARY_CLOUD, hasCloudKey = true, hasNetwork = true, hasDeviceModel = true,
        )
        assertEquals(Summary.Source.CLOUD, source)
        assertNull(why)
    }

    @Test
    fun `a build with no key falls to the phone, and says so`() {
        val (source, why) = Summary.resolve(
            Library.SUMMARY_CLOUD, hasCloudKey = false, hasNetwork = true, hasDeviceModel = true,
        )
        assertEquals(Summary.Source.DEVICE, source)
        assertEquals(Summary.Fallback.NO_KEY, why)
    }

    @Test
    fun `no network falls to the phone, which is the whole point of having it`() {
        val (source, why) = Summary.resolve(
            Library.SUMMARY_CLOUD, hasCloudKey = true, hasNetwork = false, hasDeviceModel = true,
        )
        assertEquals(Summary.Source.DEVICE, source)
        assertEquals(Summary.Fallback.NO_NETWORK, why)
    }

    @Test
    fun `no network and no on-device model lands on the synopses`() {
        val (source, why) = Summary.resolve(
            Library.SUMMARY_CLOUD, hasCloudKey = true, hasNetwork = false, hasDeviceModel = false,
        )
        assertEquals(Summary.Source.RAW, source)
        assertEquals(Summary.Fallback.NO_NETWORK, why)
    }

    @Test
    fun `choosing the phone and not having one lands on the synopses`() {
        val (source, why) = Summary.resolve(
            Library.SUMMARY_DEVICE, hasCloudKey = true, hasNetwork = true, hasDeviceModel = false,
        )
        assertEquals(Summary.Source.RAW, source)
        assertEquals(Summary.Fallback.NO_DEVICE_MODEL, why)
    }

    @Test
    fun `choosing the phone never quietly goes to the cloud`() {
        val (source, why) = Summary.resolve(
            Library.SUMMARY_DEVICE, hasCloudKey = true, hasNetwork = true, hasDeviceModel = true,
        )
        assertEquals(Summary.Source.DEVICE, source)
        assertNull(why)
    }

    @Test
    fun `asking for no summary is honoured whatever else is available`() {
        val (source, why) = Summary.resolve(
            Library.SUMMARY_NONE, hasCloudKey = true, hasNetwork = true, hasDeviceModel = true,
        )
        assertEquals(Summary.Source.RAW, source)
        assertNull(why)
    }

    // ------------------------------------------------------------ what it sees

    private fun ep(season: Int, n: Int) = net.shehane.watching.data.Tmdb.Episode(
        season = season,
        episode = n,
        name = "S${season}E$n title",
        overview = "SECRET-S${season}E$n",
    )

    private val seasons = (1..3).associateWith { n ->
        net.shehane.watching.data.Tmdb.Season(
            number = n,
            overview = "Season $n overall",
            episodes = (1..4).map { ep(n, it) },
        )
    }

    private fun show(at: Position) =
        Show(id = "1", title = "A Show", tmdbId = 1, seasonCount = 3, position = at)

    @Test
    fun `the text handed to a model stops at the position`() {
        val body = Summary.sourceText(Recap.catchUp(show(Position(2, 2)), seasons))

        assertTrue(body.contains("SECRET-S2E2"))
        assertFalse(body.contains("SECRET-S2E3"))
        assertFalse(body.contains("SECRET-S3E1"))
    }

    @Test
    fun `the text reads forwards even though the list shows newest first`() {
        val body = Summary.sourceText(Recap.catchUp(show(Position(2, 3)), seasons))
        assertTrue(body.indexOf("SECRET-S1E1") < body.indexOf("SECRET-S2E3"))
    }

    @Test
    fun `the prompt carries the spoiler rule and the characters, in order`() {
        val prompt = Summary.prompt(
            show = show(Position(2, 2)),
            upTo = "S2 E3",
            characters = listOf("John Nolan", "Lucy Chen"),
            body = "body",
        )

        assertTrue(prompt.contains("spoiler-free"))
        assertTrue(prompt.contains("S2 E3"))
        assertTrue(prompt.indexOf("John Nolan") < prompt.indexOf("Lucy Chen"))
        assertTrue(prompt.contains("one bullet for the story so far", ignoreCase = true))
    }

    @Test
    fun `a show with no named cast still asks for the story`() {
        val prompt = Summary.prompt(show(Position(1, 1)), "S1 E2", emptyList(), "body")
        assertTrue(prompt.contains("story so far", ignoreCase = true))
        assertFalse(prompt.contains("each of these characters"))
    }

    // ------------------------------------------------------------ the bullets

    @Test
    fun `bullets survive whichever marker the model reached for`() {
        val text = "- One thing\n* Another thing\n• A third\n\n  \n"
        assertEquals(listOf("One thing", "Another thing", "A third"), Summary.bullets(text))
    }

    @Test
    fun `prose with no markers is still one point rather than nothing`() {
        assertEquals(listOf("Just a sentence."), Summary.bullets("Just a sentence."))
    }

    @Test
    fun `the on-device count is the formula, clamped to what the API can express`() {
        // One for the plot plus one per character, but ML Kit only offers 1 to 3.
        assertEquals(1, Summary.deviceBulletCount(0))
        assertEquals(2, Summary.deviceBulletCount(1))
        assertEquals(3, Summary.deviceBulletCount(2))
        assertEquals(3, Summary.deviceBulletCount(8))
    }
}
