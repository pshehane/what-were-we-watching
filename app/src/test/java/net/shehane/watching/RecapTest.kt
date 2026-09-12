package net.shehane.watching

import net.shehane.watching.data.Recap
import net.shehane.watching.data.Tmdb
import net.shehane.watching.model.Position
import net.shehane.watching.model.Show
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cut-off, mostly. A recap that includes the episode you are about to watch
 * is worse than no recap, so the rule is tested from both sides: everything
 * behind the position is in, everything at or after it is out.
 */
class RecapTest {

    private fun ep(season: Int, n: Int, air: String? = "2020-01-0$n") = Tmdb.Episode(
        season = season,
        episode = n,
        name = "S${season}E$n title",
        overview = "S${season}E$n happened",
        airDate = air,
    )

    private fun season(number: Int, count: Int, overview: String? = "Season $number overall") =
        Tmdb.Season(
            number = number,
            name = "Season $number",
            overview = overview,
            episodes = (1..count).map { ep(number, it) },
        )

    private fun show(at: Position, seasonCount: Int? = 3) =
        Show(id = "1", title = "A Show", tmdbId = 1, seasonCount = seasonCount, position = at)

    private val threeSeasons = mapOf(
        1 to season(1, 4),
        2 to season(2, 4),
        3 to season(3, 4),
    )

    // ------------------------------------------------------------- the cut-off

    @Test
    fun `the episode you are about to watch is never in the recap`() {
        val recap = Recap.catchUp(show(Position(2, 2)), threeSeasons)
        val all = recap.recent.map { "S${it.season}E${it.episode}" }

        assertFalse(all.contains("S2E3"))
        assertTrue(all.contains("S2E2"))
    }

    @Test
    fun `nothing from a later season leaks in`() {
        val recap = Recap.catchUp(show(Position(2, 2)), threeSeasons)
        assertTrue(recap.recent.none { it.season > 2 })
        assertTrue(recap.earlier.none { it.first > 2 })
    }

    @Test
    fun `at the start of a season, everything before it is still there`() {
        val recap = Recap.catchUp(show(Position(3, 0)), threeSeasons)

        assertTrue(recap.recent.isNotEmpty())
        assertTrue(recap.recent.none { it.season == 3 })
        assertEquals("S2E4", recap.recent.first().let { "S${it.season}E${it.episode}" })
    }

    @Test
    fun `at the very beginning there is nothing to recap`() {
        val recap = Recap.catchUp(show(Position(1, 0)), threeSeasons)
        assertTrue(recap.isEmpty)
    }

    @Test
    fun `the most recent episode comes first`() {
        val recap = Recap.catchUp(show(Position(2, 3)), threeSeasons)
        assertEquals(2, recap.recent.first().season)
        assertEquals(3, recap.recent.first().episode)
    }

    @Test
    fun `a long history collapses to season synopses rather than every episode`() {
        val many = (1..6).associateWith { season(it, 10) }
        val recap = Recap.catchUp(show(Position(6, 5), seasonCount = 6), many)

        assertEquals(Recap.EPISODES_SHOWN, recap.recent.size)
        assertTrue(recap.earlier.isNotEmpty())
        // No season is both listed episode by episode and summarised as a whole.
        val listed = recap.recent.map { it.season }.toSet()
        assertTrue(recap.earlier.none { it.first in listed })
    }

    @Test
    fun `the leftover episodes name the season they came from`() {
        val many = (1..6).associateWith { season(it, 10) }
        val recap = Recap.catchUp(show(Position(6, 10), seasonCount = 6), many)

        // Seasons 1 to 5 are summarised; season 6 is partly listed, so the two of
        // its episodes that did not fit are the only ones left over.
        assertEquals(listOf(6), recap.olderSeasons)
        assertEquals(10 - Recap.EPISODES_SHOWN, recap.olderCount)
    }

    @Test
    fun `a season TMDB never wrote up is counted, not silently dropped`() {
        val blank = (1..6).associateWith { season(it, 10, overview = null) }
        val recap = Recap.catchUp(show(Position(6, 5), seasonCount = 6), blank)

        assertTrue(recap.earlier.isEmpty())
        assertTrue(recap.olderCount > 0)
    }

    // ---------------------------------------------------------------- next up

    @Test
    fun `next up is the following episode of the same season`() {
        val next = Recap.nextUp(show(Position(2, 2)), threeSeasons, today = "2026-01-01")
        assertEquals(2, next?.season)
        assertEquals(3, next?.episode)
        assertEquals("S2E3 title", next?.title)
    }

    @Test
    fun `at the end of a season it rolls into the next one rather than inventing an episode`() {
        val resolved = Recap.resolveNext(Position(2, 4), threeSeasons, seasonCount = 3)
        assertEquals(3, resolved?.season)
        assertEquals(1, resolved?.episode)
    }

    @Test
    fun `at the end of the last season there is nothing next`() {
        val resolved = Recap.resolveNext(Position(3, 4), threeSeasons, seasonCount = 3)
        assertNull(resolved)
    }

    @Test
    fun `not started means the first episode of that season is next`() {
        val next = Recap.nextUp(show(Position(2, 0)), threeSeasons, today = "2026-01-01")
        assertEquals(2, next?.season)
        assertEquals(1, next?.episode)
    }

    @Test
    fun `an episode that has not aired says so`() {
        val future = mapOf(2 to Tmdb.Season(number = 2, episodes = listOf(ep(2, 1, air = "2099-01-01"))))
        val next = Recap.nextUp(show(Position(2, 0)), future, today = "2026-09-12T00:00:00Z")
        assertTrue(next!!.unaired)
    }

    @Test
    fun `an episode already out does not claim to be unaired`() {
        val next = Recap.nextUp(show(Position(2, 2)), threeSeasons, today = "2026-09-12T00:00:00Z")
        assertFalse(next!!.unaired)
    }

    @Test
    fun `with nothing loaded the guess is still the next episode number`() {
        val resolved = Recap.resolveNext(Position(4, 7), emptyMap(), seasonCount = 9)
        assertEquals(4, resolved?.season)
        assertEquals(8, resolved?.episode)
    }

    @Test
    fun `a catch-up asks for every season up to where you are`() {
        assertEquals(listOf(1, 2, 3), Recap.seasonsNeeded(show(Position(3, 2))))
    }
}
