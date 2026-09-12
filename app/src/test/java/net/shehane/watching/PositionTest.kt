package net.shehane.watching

import net.shehane.watching.model.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Episode 0 exists so the app can say "we have finished everything before season
 * seven" without knowing how long season six was. Without it, finishing a season
 * had to claim you had watched the opener of the next one.
 */
class PositionTest {

    @Test
    fun `episode zero is not started, and says so rather than naming an episode`() {
        val p = Position(season = 9, episode = 0)
        assertTrue(p.notStarted)
        assertEquals("S9 · start", p.toString())
        assertEquals("S9 start", p.short)
    }

    @Test
    fun `an ordinary position still reads as an episode`() {
        val p = Position(season = 2, episode = 5)
        assertFalse(p.notStarted)
        assertEquals("S2 · E5", p.toString())
        assertEquals("S2 E5", p.short)
    }

    @Test
    fun `next up after not-started is the first episode of that season`() {
        val p = Position(season = 9, episode = 0)
        // The screen renders next up as episode + 1, which is what makes zero work.
        assertEquals(1, p.episode + 1)
        assertEquals(9, p.season)
    }

    @Test
    fun `finishing a season lands before the next one, not inside it`() {
        val after = Position(season = 6, episode = 12).let {
            Position(season = it.season + 1, episode = 0)
        }
        assertEquals(7, after.season)
        assertTrue(after.notStarted)
        // Next up is S7 E1: the opener of season seven is still ahead of you.
        assertEquals(1, after.episode + 1)
    }

    @Test
    fun `stepping back off the first episode lands on not started, not on episode one`() {
        val stepped = (Position(season = 3, episode = 1).episode - 1).coerceAtLeast(0)
        assertEquals(0, stepped)
    }

    @Test
    fun `stepping back again stays put rather than going negative`() {
        val stepped = (Position(season = 3, episode = 0).episode - 1).coerceAtLeast(0)
        assertEquals(0, stepped)
    }
}
