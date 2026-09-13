package net.shehane.watching

import java.time.LocalTime
import net.shehane.watching.data.TimeLeft
import net.shehane.watching.model.Show
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The evening arithmetic.
 *
 * The slack is the interesting part. An episode that runs a few minutes past
 * bedtime still counts, because nobody stops one that close to the end, and a
 * strict answer would be wrong more often than a loose one.
 */
class TimeLeftTest {

    private fun show(minutes: Int?) =
        Show(id = "1", title = "A Show", runtimeMinutes = minutes)

    // ------------------------------------------------------------ what fits

    @Test
    fun `whole episodes fit`() {
        val fits = TimeLeft.fits(show(45), 90)
        assertEquals(2, fits.episodes)
        assertEquals(90, fits.runtime)
        assertEquals(0, fits.overrunMinutes)
    }

    @Test
    fun `one that only just spills over still counts`() {
        // Three 45s is 135 against 130 available: five minutes over, inside slack.
        val fits = TimeLeft.fits(show(45), 130)
        assertEquals(3, fits.episodes)
        assertEquals(5, fits.overrunMinutes)
    }

    @Test
    fun `one that spills too far does not`() {
        // Three 45s against 110 is 25 minutes over, well outside slack.
        val fits = TimeLeft.fits(show(45), 110)
        assertEquals(2, fits.episodes)
        assertEquals(0, fits.overrunMinutes)
    }

    @Test
    fun `exactly at the slack limit counts`() {
        val fits = TimeLeft.fits(show(45), 45 - TimeLeft.SLACK_MINUTES)
        assertEquals(1, fits.episodes)
        assertEquals(TimeLeft.SLACK_MINUTES, fits.overrunMinutes)
    }

    @Test
    fun `a minute past the slack limit does not`() {
        val fits = TimeLeft.fits(show(45), 45 - TimeLeft.SLACK_MINUTES - 1)
        assertEquals(0, fits.episodes)
        assertTrue(fits.fitsNothing)
    }

    @Test
    fun `no time at all fits nothing`() {
        assertTrue(TimeLeft.fits(show(45), 0).fitsNothing)
        assertTrue(TimeLeft.fits(show(45), -20).fitsNothing)
    }

    @Test
    fun `an unknown runtime is guessed and flagged as a guess`() {
        val fits = TimeLeft.fits(show(null), 120)
        assertTrue(fits.assumed)
        assertEquals(120 / TimeLeft.ASSUMED_MINUTES, fits.episodes)
    }

    @Test
    fun `a known runtime is not flagged`() {
        assertFalse(TimeLeft.fits(show(22), 60).assumed)
    }

    @Test
    fun `a half hour comedy fits more than a drama in the same evening`() {
        val comedy = TimeLeft.fits(show(22), 90).episodes
        val drama = TimeLeft.fits(show(55), 90).episodes
        assertTrue(comedy > drama)
    }

    // ------------------------------------------------------ typical runtime

    @Test
    fun `the typical runtime is the median episode`() {
        // Silo season 1, as TMDB lists it.
        val silo = listOf(62, 52, 66, 49, 54, 55, 51, 48, 48, 49)
        assertEquals(51, TimeLeft.typicalRuntime(silo))
    }

    @Test
    fun `a double-length finale does not drag the typical runtime up`() {
        assertEquals(22, TimeLeft.typicalRuntime(listOf(22, 22, 22, 23, 44)))
    }

    @Test
    fun `missing and zero runtimes are ignored`() {
        assertEquals(45, TimeLeft.typicalRuntime(listOf(null, 0, 45, null)))
    }

    @Test
    fun `no runtimes at all gives no answer`() {
        assertEquals(null, TimeLeft.typicalRuntime(listOf(null, null)))
        assertEquals(null, TimeLeft.typicalRuntime(emptyList()))
    }

    // -------------------------------------------------------------- the clock

    @Test
    fun `a duration is simply itself`() {
        val budget = TimeLeft.duration(90)
        assertEquals(90, TimeLeft.remaining(budget, LocalTime.of(20, 0)))
    }

    @Test
    fun `a bedtime counts down as the evening goes on`() {
        val bedtime = TimeLeft.bedtime(23 * 60)
        assertEquals(180, TimeLeft.remaining(bedtime, LocalTime.of(20, 0)))
        assertEquals(30, TimeLeft.remaining(bedtime, LocalTime.of(22, 30)))
    }

    @Test
    fun `a bedtime after midnight is tonight, not yesterday`() {
        // It is 1am and bedtime is 2am: an hour away, not twenty-three hours ago.
        val bedtime = TimeLeft.bedtime(2 * 60)
        assertEquals(60, TimeLeft.remaining(bedtime, LocalTime.of(1, 0)))
    }

    @Test
    fun `a bedtime just gone reads as gone rather than as tomorrow`() {
        val bedtime = TimeLeft.bedtime(23 * 60)
        assertTrue(TimeLeft.remaining(bedtime, LocalTime.of(23, 20)) < 0)
    }

    @Test
    fun `no budget means no time`() {
        assertEquals(0, TimeLeft.remaining(TimeLeft.none(), LocalTime.of(20, 0)))
        assertFalse(TimeLeft.none().isSet)
    }

    // ------------------------------------------------------------- the words

    @Test
    fun `durations read the way people say them`() {
        assertEquals("45m", TimeLeft.spell(45))
        assertEquals("1h", TimeLeft.spell(60))
        assertEquals("1h 45m", TimeLeft.spell(105))
        assertEquals("2h", TimeLeft.spell(120))
        assertEquals("", TimeLeft.spell(0))
    }

    @Test
    fun `clock times read on a twelve hour dial`() {
        assertEquals("11:00 pm", TimeLeft.spellClock(23 * 60))
        assertEquals("12:30 am", TimeLeft.spellClock(30))
        assertEquals("12:00 pm", TimeLeft.spellClock(12 * 60))
        assertEquals("9:05 pm", TimeLeft.spellClock(21 * 60 + 5))
    }

    @Test
    fun `an end time is now plus the runtime`() {
        assertEquals("10:30 pm", TimeLeft.endsAt(LocalTime.of(20, 0), 150))
    }

    @Test
    fun `an end time can run past midnight`() {
        assertEquals("12:30 am", TimeLeft.endsAt(LocalTime.of(23, 0), 90))
    }

    @Test
    fun `the offered bedtimes are round half hours ahead of now`() {
        val at = TimeLeft.defaultBedtime(LocalTime.of(20, 11))
        assertEquals(0, at % 30)
        assertTrue(at > 20 * 60 + 11)
    }
}
