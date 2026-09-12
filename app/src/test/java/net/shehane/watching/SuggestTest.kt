package net.shehane.watching

import java.time.Instant
import java.time.temporal.ChronoUnit
import net.shehane.watching.data.Suggest
import net.shehane.watching.data.Tmdb
import net.shehane.watching.model.Library
import net.shehane.watching.model.Person
import net.shehane.watching.model.Service
import net.shehane.watching.model.Show
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The suggestions engine. Two things matter enough to pin down here: that the
 * sections which claim not to guess really do not, and that a verdict is never
 * invented for a show nobody has judged.
 */
class SuggestTest {

    private val people = listOf(
        Person("me", "Me", "#E9A06A", 0),
        Person("ben", "Ben", "#F09FBC", 1),
        Person("cal", "Cal", "#6FC0DE", 2),
    )

    private val services = listOf(
        Service("netflix", "Netflix", "#E0574C"),
    )

    private fun daysAgo(n: Long): String =
        Instant.parse(NOW).minus(n, ChronoUnit.DAYS).toString()

    private fun show(
        id: String,
        title: String,
        state: String = Show.STATE_ACTIVE,
        serviceId: String? = "netflix",
        with: List<String> = emptyList(),
        liked: Boolean? = null,
        tmdbId: Int? = 100,
        lastWatchedAt: String? = daysAgo(1),
    ) = Show(
        id = id,
        tmdbId = tmdbId,
        title = title,
        serviceId = serviceId,
        watchedWith = with,
        state = state,
        liked = liked,
        addedAt = daysAgo(90),
        updatedAt = daysAgo(1),
        lastWatchedAt = lastWatchedAt,
    )

    private fun library(vararg shows: Show) =
        Library(people = people, services = services, shows = shows.toList())

    // --------------------------------------------------------- ready tonight

    @Test
    fun `a wishlist show on a service you have is ready tonight`() {
        val lib = library(show("1", "Silo", state = Show.STATE_WISHLIST))
        val result = Suggest.build(lib, setOf("me"))

        assertEquals(1, result.ready.size)
        assertEquals("Silo", result.ready[0].show.title)
    }

    @Test
    fun `a wishlist show on no service is not an answer to what should we watch`() {
        val lib = library(show("1", "Silo", state = Show.STATE_WISHLIST, serviceId = null))
        assertTrue(Suggest.build(lib, setOf("me")).ready.isEmpty())
    }

    @Test
    fun `a wishlist show on a service that is not in the library is not ready`() {
        val lib = library(show("1", "Silo", state = Show.STATE_WISHLIST, serviceId = "starz"))
        assertTrue(Suggest.build(lib, setOf("me")).ready.isEmpty())
    }

    @Test
    fun `ready is exact only when the couch and the show are the same set`() {
        val lib = library(
            show("1", "Silo", state = Show.STATE_WISHLIST, with = listOf("ben")),
            show("2", "Andor", state = Show.STATE_WISHLIST, with = listOf("ben", "cal")),
        )
        val result = Suggest.build(lib, setOf("me", "ben"))

        assertEquals("Silo", result.ready.first { it.exact }.show.title)
        assertEquals("Andor", result.ready.first { !it.exact }.show.title)
    }

    @Test
    fun `an exact match is offered before an older inexact one`() {
        val lib = library(
            show("1", "Andor", state = Show.STATE_WISHLIST, with = listOf("ben", "cal")),
            show("2", "Silo", state = Show.STATE_WISHLIST, with = listOf("ben")),
        )
        val result = Suggest.build(lib, setOf("me", "ben"))
        assertEquals("Silo", result.ready[0].show.title)
    }

    @Test
    fun `a started show is never offered as ready tonight`() {
        val lib = library(show("1", "Silo", state = Show.STATE_ACTIVE))
        assertTrue(Suggest.build(lib, setOf("me")).ready.isEmpty())
    }

    // ---------------------------------------------------------------- stalled

    @Test
    fun `an active show untouched for longer than the limit is stalled`() {
        val lib = library(show("1", "Fallout", lastWatchedAt = daysAgo(Suggest.STALE_DAYS + 10)))
        val result = Suggest.build(lib, setOf("me"))

        assertEquals(1, result.stalled.size)
        assertTrue(result.stalled[0].days >= Suggest.STALE_DAYS)
    }

    @Test
    fun `a show watched this week is not stalled`() {
        val lib = library(show("1", "Fallout", lastWatchedAt = daysAgo(3)))
        assertTrue(Suggest.build(lib, setOf("me")).stalled.isEmpty())
    }

    @Test
    fun `a snoozed show is not stalled, because you already said not now`() {
        val lib = library(
            show("1", "Fallout", state = Show.STATE_SNOOZED, lastWatchedAt = daysAgo(99))
        )
        assertTrue(Suggest.build(lib, setOf("me")).stalled.isEmpty())
    }

    @Test
    fun `the longest wait comes first`() {
        val lib = library(
            show("1", "Fallout", lastWatchedAt = daysAgo(30)),
            show("2", "Dark", lastWatchedAt = daysAgo(120)),
        )
        val result = Suggest.build(lib, setOf("me"))
        assertEquals("Dark", result.stalled[0].show.title)
    }

    // ------------------------------------------------------------------- seed

    @Test
    fun `a loved show is the seed even when a plainer one was finished later`() {
        val lib = library(
            show("1", "Severance", state = Show.STATE_FINISHED, liked = true, lastWatchedAt = daysAgo(60)),
            show("2", "Filler", state = Show.STATE_FINISHED, lastWatchedAt = daysAgo(2)),
        )
        assertEquals("Severance", Suggest.build(lib, emptySet()).seed?.title)
    }

    @Test
    fun `the most recent of two loved shows wins`() {
        val lib = library(
            show("1", "Severance", state = Show.STATE_FINISHED, liked = true, lastWatchedAt = daysAgo(60)),
            show("2", "Arcane", state = Show.STATE_FINISHED, liked = true, lastWatchedAt = daysAgo(5)),
        )
        assertEquals("Arcane", Suggest.build(lib, emptySet()).seed?.title)
    }

    @Test
    fun `a show with no tmdb id cannot be a seed, because nothing can be asked about it`() {
        val lib = library(
            show("1", "Home video", state = Show.STATE_FINISHED, liked = true, tmdbId = null)
        )
        assertNull(Suggest.build(lib, emptySet()).seed)
    }

    @Test
    fun `an empty library has no seed and nothing to say`() {
        val result = Suggest.build(Library(people = people), emptySet())
        assertNull(result.seed)
        assertTrue(result.isEmpty)
        assertFalse(result.enoughToGuess)
    }

    // -------------------------------------------------------- counting and no

    @Test
    fun `finished is counted whatever the verdict, and an unjudged show is not a no`() {
        val lib = library(
            show("1", "A", state = Show.STATE_FINISHED, liked = true),
            show("2", "B", state = Show.STATE_FINISHED, liked = false),
            show("3", "C", state = Show.STATE_FINISHED),
        )
        val result = Suggest.build(lib, emptySet())

        assertEquals(3, result.finishedCount)
        assertFalse(lib.shows[2].isLoved)
        assertFalse(lib.shows[2].isNotLoved)
        assertTrue(lib.shows[1].isNotLoved)
    }

    @Test
    fun `guessing waits until there is enough finished to guess from`() {
        val few = library(show("1", "A", state = Show.STATE_FINISHED))
        assertFalse(Suggest.build(few, emptySet()).enoughToGuess)

        val many = library(
            *(1..Suggest.ENOUGH_FINISHED)
                .map { show("$it", "Show $it", state = Show.STATE_FINISHED) }
                .toTypedArray()
        )
        assertTrue(Suggest.build(many, emptySet()).enoughToGuess)
    }

    @Test
    fun `a recommendation already in the library is never offered back`() {
        val lib = library(show("1", "Dark", tmdbId = 70523))
        val items = listOf(
            Tmdb.SearchItem(id = 70523, name = "Dark"),
            Tmdb.SearchItem(id = 1399, name = "Something else"),
        )
        val left = Suggest.unseen(lib, items)

        assertEquals(1, left.size)
        assertEquals(1399, left[0].id)
    }

    private companion object {
        const val NOW = "2026-09-12T10:00:00Z"
    }
}
