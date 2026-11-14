package net.shehane.watching

import net.shehane.watching.data.Cast
import net.shehane.watching.data.Tmdb
import net.shehane.watching.model.Show
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cast search's rules: which credits are worth a row, who appears once
 * rather than twice, and what two people have in common.
 */
class CastTest {

    private fun credit(
        id: Int,
        title: String,
        movie: Boolean = true,
        character: String? = null,
        job: String? = null,
        popularity: Double = 1.0,
        year: String = "2016-01-01",
    ) = Tmdb.CreditRow(
        id = id,
        seriesName = if (movie) "" else title,
        movieTitle = if (movie) title else "",
        mediaType = if (movie) "movie" else "tv",
        firstAirDate = if (movie) null else year,
        releaseDate = if (movie) year else null,
        character = character,
        job = job,
        popularity = popularity,
    )

    private fun person(id: Int, name: String, character: String? = null, job: String? = null, order: Int = 0) =
        Tmdb.PersonRow(id = id, name = name, character = character, job = job, order = order)

    // ------------------------------------------------------------- credits

    @Test
    fun `acting and crew credits come back in one list`() {
        val credits = Cast.creditsFrom(
            cast = listOf(credit(1, "Arrival", character = "Louise")),
            crew = listOf(credit(2, "Dune", job = "Director")),
        )

        assertEquals(setOf("Arrival", "Dune"), credits.map { it.item.name }.toSet())
        assertEquals("Louise", credits.single { it.item.name == "Arrival" }.role)
        assertEquals("Director", credits.single { it.item.name == "Dune" }.role)
    }

    @Test
    fun `crew jobs nobody searches for are left out`() {
        val credits = Cast.creditsFrom(
            cast = emptyList(),
            crew = listOf(
                credit(1, "Dune", job = "Director"),
                credit(2, "Dune", job = "Second Assistant Director"),
                credit(3, "Dune", job = "Catering"),
            ),
        )

        assertEquals(listOf("Director"), credits.map { it.role })
    }

    @Test
    fun `somebody who directed and scored the same film gets one row saying both`() {
        val credits = Cast.creditsFrom(
            cast = emptyList(),
            crew = listOf(
                credit(1, "Us", job = "Director"),
                credit(1, "Us", job = "Original Music Composer"),
            ),
        )

        assertEquals(1, credits.size)
        assertEquals("Director, Original Music Composer", credits.single().role)
    }

    @Test
    fun `the best known work comes first`() {
        val credits = Cast.creditsFrom(
            cast = listOf(
                credit(1, "Bit part", popularity = 0.5),
                credit(2, "The famous one", popularity = 90.0),
                credit(3, "The middling one", popularity = 12.0),
            ),
            crew = emptyList(),
        )

        assertEquals(
            listOf("The famous one", "The middling one", "Bit part"),
            credits.map { it.item.name },
        )
    }

    @Test
    fun `a popular series does not bury the films somebody led`() {
        // Real numbers from TMDB: a three-episode guest spot on The Office scores
        // 1701, while Arrival, which she carried, scores 19.5. Comparing the two
        // directly put every film she made below television she passed through.
        val credits = Cast.creditsFrom(
            cast = listOf(
                credit(1, "The Office", movie = false, character = "Katy", popularity = 1701.0)
                    .copy(episodes = 3),
                credit(2, "Arrival", character = "Louise Banks", popularity = 19.5),
            ),
            crew = emptyList(),
        )

        assertEquals("Arrival", credits.first().item.name)
        assertEquals("The Office", credits[1].item.name)
    }

    @Test
    fun `the two catalogs take turns rather than one owning the top`() {
        val credits = Cast.creditsFrom(
            cast = listOf(
                credit(1, "Big series", movie = false, popularity = 900.0).copy(episodes = 60),
                credit(2, "Small series", movie = false, popularity = 400.0).copy(episodes = 60),
                credit(3, "Big film", popularity = 30.0),
                credit(4, "Small film", popularity = 10.0),
            ),
            crew = emptyList(),
        )

        assertEquals(
            listOf("Big film", "Big series", "Small film", "Small series"),
            credits.map { it.item.name },
        )
    }

    @Test
    fun `a regular on a series outranks a guest on a more popular one`() {
        val credits = Cast.creditsFrom(
            cast = listOf(
                credit(1, "Guested once", movie = false, popularity = 500.0).copy(episodes = 1),
                credit(2, "Was the lead", movie = false, popularity = 200.0).copy(episodes = 40),
            ),
            crew = emptyList(),
        )

        assertEquals("Was the lead", credits.first().item.name)
    }

    @Test
    fun `a film and a series sharing an id stay separate`() {
        val credits = Cast.creditsFrom(
            cast = listOf(
                credit(7, "Fargo", movie = true, character = "Jerry"),
                credit(7, "Fargo", movie = false, character = "Lester"),
            ),
            crew = emptyList(),
        )

        assertEquals(2, credits.size)
        assertEquals(
            setOf(Show.MEDIUM_MOVIE, Show.MEDIUM_TV),
            credits.map { it.item.medium }.toSet(),
        )
    }

    @Test
    fun `turning up as yourself sorts below actual work`() {
        val credits = Cast.creditsFrom(
            cast = listOf(
                // A nightly show that has run for years outscores any film.
                credit(1, "The Tonight Show", movie = false, character = "Self", popularity = 900.0),
                credit(2, "The Late Show", movie = false, character = "Self - Guest", popularity = 800.0),
            ),
            crew = listOf(credit(3, "Dune", job = "Director", popularity = 80.0)),
        )

        assertEquals("Dune", credits.first().item.name)
        assertEquals(listOf(false, true, true), credits.map { it.appearance })
    }

    @Test
    fun `the ways TMDB spells an appearance are all recognised`() {
        assertTrue(Cast.isAppearance("Self"))
        assertTrue(Cast.isAppearance("self"))
        assertTrue(Cast.isAppearance("Self - Guest"))
        assertTrue(Cast.isAppearance("Himself"))
        assertTrue(Cast.isAppearance("Herself (archive footage)"))

        // A character whose name merely starts the same way is a real part.
        assertFalse(Cast.isAppearance("Selfridge"))
        assertFalse(Cast.isAppearance("Louise Banks"))
        assertFalse(Cast.isAppearance(null))
    }

    @Test
    fun `a credit that names neither a character nor a job is an appearance`() {
        // A blank character is how a chat show arrives. Two rows for the same
        // show, one blank and one "Self - Guest", must not add up to work.
        val credits = Cast.creditsFrom(
            cast = listOf(
                credit(1, "LIVE with Kelly", movie = false, character = null, popularity = 100.0),
                credit(1, "LIVE with Kelly", movie = false, character = "Self - Guest", popularity = 100.0),
                credit(2, "Arrival", character = "Louise Banks", popularity = 19.5),
            ),
            crew = emptyList(),
        )

        assertEquals("Arrival", credits.first().item.name)
        assertTrue(credits.single { it.item.name == "LIVE with Kelly" }.appearance)
    }

    @Test
    fun `directing something you also appeared in counts as work`() {
        val credits = Cast.creditsFrom(
            cast = listOf(credit(1, "The Disaster Artist", character = "Self")),
            crew = listOf(credit(1, "The Disaster Artist", job = "Director")),
        )

        assertEquals(1, credits.size)
        assertFalse(credits.single().appearance)
    }

    // -------------------------------------------------------------- members

    @Test
    fun `the billed cast come before the crew`() {
        val members = Cast.membersFrom(
            cast = listOf(person(1, "Amy", character = "Louise", order = 0)),
            crew = listOf(person(2, "Denis", job = "Director")),
        )

        assertEquals(listOf("Amy", "Denis"), members.map { it.name })
        assertTrue(members.first().isCast)
    }

    @Test
    fun `somebody who acted and directed appears once with both`() {
        val members = Cast.membersFrom(
            cast = listOf(person(1, "Jordan", character = "Himself", order = 0)),
            crew = listOf(person(1, "Jordan", job = "Director")),
        )

        assertEquals(1, members.size)
        assertEquals("Himself, Director", members.single().role)
    }

    @Test
    fun `crew with jobs outside the list do not appear at all`() {
        val members = Cast.membersFrom(
            cast = emptyList(),
            crew = listOf(person(9, "Someone", job = "Best Boy")),
        )

        assertTrue(members.isEmpty())
    }

    // -------------------------------------------------------------- overlap

    @Test
    fun `two people share only what they both worked on, with each role`() {
        val villeneuve = Cast.creditsFrom(
            cast = emptyList(),
            crew = listOf(
                credit(1, "Arrival", job = "Director", popularity = 30.0),
                credit(2, "Dune", job = "Director", popularity = 80.0),
                credit(3, "Sicario", job = "Director", popularity = 20.0),
            ),
        )
        val zimmer = Cast.creditsFrom(
            cast = emptyList(),
            crew = listOf(
                credit(2, "Dune", job = "Original Music Composer", popularity = 80.0),
                credit(4, "Interstellar", job = "Original Music Composer", popularity = 70.0),
            ),
        )

        val shared = Cast.overlap(villeneuve, zimmer)

        assertEquals(1, shared.size)
        assertEquals("Dune", shared.single().item.name)
        assertEquals("Director", shared.single().roleA)
        assertEquals("Original Music Composer", shared.single().roleB)
    }

    @Test
    fun `a chat show they both sat on does not outrank the film they made`() {
        val adams = Cast.creditsFrom(
            cast = listOf(
                credit(1, "The Tonight Show", movie = false, character = "Self - Guest", popularity = 346.0)
                    .copy(episodes = 2),
                credit(2, "Arrival", character = "Louise Banks", popularity = 19.5),
            ),
            crew = emptyList(),
        )
        val villeneuve = Cast.creditsFrom(
            cast = listOf(
                credit(1, "The Tonight Show", movie = false, character = "Self", popularity = 346.0)
                    .copy(episodes = 1),
            ),
            crew = listOf(credit(2, "Arrival", job = "Director", popularity = 19.5)),
        )

        val shared = Cast.overlap(adams, villeneuve)

        assertEquals("Arrival", shared.first().item.name)
        assertEquals("The Tonight Show", shared[1].item.name)
        assertTrue(shared[1].appearance)
    }

    @Test
    fun `two people with nothing in common share nothing`() {
        val a = Cast.creditsFrom(listOf(credit(1, "One")), emptyList())
        val b = Cast.creditsFrom(listOf(credit(2, "Two")), emptyList())

        assertTrue(Cast.overlap(a, b).isEmpty())
    }

    @Test
    fun `the same id in the two catalogs is not an overlap`() {
        val a = Cast.creditsFrom(listOf(credit(7, "Fargo", movie = true)), emptyList())
        val b = Cast.creditsFrom(listOf(credit(7, "Fargo", movie = false)), emptyList())

        assertTrue(Cast.overlap(a, b).isEmpty())
    }
}
