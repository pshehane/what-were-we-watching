package net.shehane.watching

import kotlinx.serialization.json.Json
import net.shehane.watching.data.Csv
import net.shehane.watching.data.Merge
import net.shehane.watching.data.TimeLeft
import net.shehane.watching.data.Tmdb
import net.shehane.watching.model.Library
import net.shehane.watching.model.Position
import net.shehane.watching.model.Show
import net.shehane.watching.model.spellRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Movies share the show record, so the tests that matter are the ones about
 * telling the two apart: what a record with no medium is, what each kind prints
 * on a card, and what fits in an evening.
 */
class MovieTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun series(id: String = "1", season: Int = 2, episode: Int = 5, runtime: Int? = 45) =
        Show(
            id = id, title = "Silo", medium = Show.MEDIUM_TV,
            position = Position(season, episode), runtimeMinutes = runtime,
            addedAt = WHEN, updatedAt = WHEN,
        )

    private fun movie(id: String = "2", runtime: Int? = 112, state: String = Show.STATE_ACTIVE) =
        Show(
            id = id, title = "Arrival", medium = Show.MEDIUM_MOVIE,
            position = Position(1, 0), runtimeMinutes = runtime, state = state,
            seasonCount = 1, episodeCount = 1,
            addedAt = WHEN, updatedAt = WHEN,
        )

    // ------------------------------------------------- the old-record promise

    @Test
    fun `a record saved before movies existed reads as a series`() {
        // Exactly the shape the library file has had until now: no medium field.
        val stored = """
            {"id":"1","tmdbId":99,"title":"Reacher","position":{"season":2,"episode":5},
             "state":"active","addedAt":"$WHEN","updatedAt":"$WHEN"}
        """.trimIndent()

        val show = json.decodeFromString(Show.serializer(), stored)

        assertEquals(Show.MEDIUM_TV, show.medium)
        assertTrue(show.isSeries)
        assertFalse(show.isMovie)
        assertEquals("S2 · E5", show.marker)
    }

    @Test
    fun `a whole library with no medium anywhere still loads`() {
        val stored = """
            {"schemaVersion":1,"updatedAt":"$WHEN",
             "shows":[{"id":"1","title":"Reacher","addedAt":"$WHEN","updatedAt":"$WHEN"}]}
        """.trimIndent()

        val library = json.decodeFromString(Library.serializer(), stored)

        assertEquals(1, library.shows.size)
        assertTrue(library.shows.single().isSeries)
    }

    @Test
    fun `the medium survives a merge between two phones`() {
        val a = Library(updatedAt = WHEN, shows = listOf(movie()))
        val b = Library(updatedAt = LATER, shows = listOf(movie().copy(updatedAt = LATER, liked = true)))

        val merged = Merge.libraries(a, b)
        assertTrue(merged.shows.single().isMovie)
        assertEquals(true, merged.shows.single().liked)
    }

    // ------------------------------------------------------------- the marker

    @Test
    fun `a series shows its position and a movie shows its length`() {
        assertEquals("S2 · E5", series().marker)
        assertEquals("1h 52m", movie().marker)

        assertEquals("S2 E5", series().markerShort)
        assertEquals("1h 52m", movie().markerShort)
    }

    @Test
    fun `a movie with no runtime yet says what it is rather than nothing`() {
        assertEquals("Movie", movie(runtime = null).marker)
        assertEquals("Movie", movie(runtime = 0).marker)
    }

    @Test
    fun `runtimes are spelled the way people say them`() {
        assertEquals("47m", spellRuntime(47))
        assertEquals("2h", spellRuntime(120))
        assertEquals("1h 52m", spellRuntime(112))
        assertEquals("", spellRuntime(0))
    }

    // --------------------------------------------------------- the time budget

    @Test
    fun `an evening fits several episodes but only one showing of a film`() {
        // Three hours is four 45-minute episodes, or one 112-minute film.
        assertEquals(4, TimeLeft.fits(series(), 180).episodes)
        assertEquals(1, TimeLeft.fits(movie(), 180).episodes)
    }

    @Test
    fun `a film that does not fit the evening is reported as not fitting`() {
        val fits = TimeLeft.fits(movie(), 60)
        assertTrue(fits.fitsNothing)
        assertEquals(0, fits.episodes)
    }

    @Test
    fun `a film still gets the ten minutes of slack a show does`() {
        // 105 minutes left, a 112-minute film: seven minutes over is still on.
        val fits = TimeLeft.fits(movie(), 105)
        assertEquals(1, fits.episodes)
        assertEquals(7, fits.overrunMinutes)
    }

    // -------------------------------------------------------------------- CSV

    @Test
    fun `the CSV says which kind each row is and leaves a film's episode blank`() {
        val library = Library(updatedAt = WHEN, shows = listOf(series(), movie()))
        val lines = Csv.render(library).trim().lines()
        val header = lines[0].split(",")

        val kind = header.indexOf("Kind")
        val season = header.indexOf("Season")
        val episode = header.indexOf("Episode")
        val minutes = header.indexOf("Minutes")
        assertTrue(kind > 0)

        val rows = lines.drop(1).map { it.split(",") }
        val film = rows.single { it[0] == "Arrival" }
        val show = rows.single { it[0] == "Silo" }

        assertEquals("movie", film[kind])
        assertEquals("", film[season])
        assertEquals("", film[episode])
        assertEquals("112", film[minutes])

        assertEquals("series", show[kind])
        assertEquals("2", show[season])
        assertEquals("5", show[episode])
    }

    // ------------------------------------------------------------- the search

    @Test
    fun `a film result reads its title and year from the movie fields`() {
        val row = """
            {"id":329865,"title":"Arrival","release_date":"2016-11-10",
             "media_type":"movie","overview":"Twelve ships."}
        """.trimIndent()

        val item = json.decodeFromString(Tmdb.SearchItem.serializer(), row)
            .copy(medium = Show.MEDIUM_MOVIE)

        assertEquals("Arrival", item.name)
        assertEquals(2016, item.year)
        assertTrue(item.isMovie)
    }

    @Test
    fun `a series result still reads its name and first air date`() {
        val row = """
            {"id":87739,"name":"Silo","first_air_date":"2023-05-04","media_type":"tv"}
        """.trimIndent()

        val item = json.decodeFromString(Tmdb.SearchItem.serializer(), row)

        assertEquals("Silo", item.name)
        assertEquals(2023, item.year)
        assertFalse(item.isMovie)
    }

    @Test
    fun `a detail answers how long one sitting is for either kind`() {
        val film = """{"id":1,"title":"Arrival","runtime":116}"""
        val show = """{"id":2,"name":"Silo","episode_run_time":[55]}"""
        val neither = """{"id":3,"name":"The Rookie","episode_run_time":[]}"""

        assertEquals(116, json.decodeFromString(Tmdb.Detail.serializer(), film).sittingMinutes)
        assertEquals(55, json.decodeFromString(Tmdb.Detail.serializer(), show).sittingMinutes)
        assertNull(json.decodeFromString(Tmdb.Detail.serializer(), neither).sittingMinutes)
    }

    private companion object {
        const val WHEN = "2026-01-01T10:00:00Z"
        const val LATER = "2026-02-01T10:00:00Z"
    }
}
