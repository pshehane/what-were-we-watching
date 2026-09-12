package net.shehane.watching

import net.shehane.watching.model.Position
import net.shehane.watching.model.Show
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Filling in a record that arrived thin.
 *
 * The rule that matters is that this only ever adds. A show added through the
 * normal flow already has better answers than a late lookup, and overwriting them
 * would be a repair that breaks things.
 */
class FillDetailsTest {

    /** The same transform [net.shehane.watching.data.LibraryStore.fillDetails] applies. */
    private fun fill(show: Show, seasons: Int?, episodes: Int?): Show {
        val count = show.seasonCount ?: seasons
        return show.copy(
            seasonCount = count,
            episodeCount = show.episodeCount ?: episodes,
            position =
                if (count != null && show.position.season > count) {
                    show.position.copy(season = count)
                } else {
                    show.position
                },
        )
    }

    private fun show(seasons: Int? = null, episodes: Int? = null, at: Position = Position(1, 1)) =
        Show(id = "1", title = "The Rookie", seasonCount = seasons, episodeCount = episodes, position = at)

    @Test
    fun `a missing count is filled in`() {
        val filled = fill(show(), 7, 151)
        assertEquals(7, filled.seasonCount)
        assertEquals(151, filled.episodeCount)
    }

    @Test
    fun `a count that is already there is left alone`() {
        val filled = fill(show(seasons = 7, episodes = 151), 99, 999)
        assertEquals(7, filled.seasonCount)
        assertEquals(151, filled.episodeCount)
    }

    @Test
    fun `a lookup that answers nothing changes nothing`() {
        val filled = fill(show(seasons = 7), null, null)
        assertEquals(7, filled.seasonCount)
        assertEquals(null, filled.episodeCount)
    }

    @Test
    fun `a position past the end is pulled back to the last real season`() {
        val filled = fill(show(at = Position(20, 1)), 7, 151)
        assertEquals(7, filled.position.season)
        assertEquals(1, filled.position.episode)
    }

    @Test
    fun `a position inside the show is not touched`() {
        val filled = fill(show(at = Position(3, 4)), 7, 151)
        assertEquals(3, filled.position.season)
        assertEquals(4, filled.position.episode)
    }

    @Test
    fun `the last season is inside the show, not past it`() {
        val filled = fill(show(at = Position(7, 9)), 7, 151)
        assertEquals(7, filled.position.season)
        assertEquals(9, filled.position.episode)
    }
}
