package net.shehane.watching

import net.shehane.watching.data.Couch
import net.shehane.watching.data.Csv
import net.shehane.watching.data.Merge
import net.shehane.watching.model.Library
import net.shehane.watching.model.Person
import net.shehane.watching.model.Position
import net.shehane.watching.model.Profile
import net.shehane.watching.model.Service
import net.shehane.watching.model.Show
import net.shehane.watching.model.Tombstone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two pieces where a bug would be silent and expensive: the couch match, and
 * the merge that keeps two phones from erasing each other. Both are pure, so both
 * can be tested without a device.
 */
class LibraryLogicTest {

    private val people = listOf(
        Person("me", "Me", "#E9A06A", 0, T1),
        Person("ben", "Ben", "#F09FBC", 1, T1),
        Person("cal", "Cal", "#6FC0DE", 2, T1),
        Person("dee", "Dee", "#85CE9A", 3, T1),
    )

    private fun show(
        id: String,
        title: String,
        with: List<String>,
        state: String = Show.STATE_ACTIVE,
        updatedAt: String = T1,
        lastWatchedAt: String? = T1,
    ) = Show(
        id = id, title = title, watchedWith = with, state = state,
        addedAt = T1, updatedAt = updatedAt, lastWatchedAt = lastWatchedAt,
    )

    private fun library(vararg shows: Show) =
        Library(updatedAt = T1, people = people, shows = shows.toList())

    // ------------------------------------------------------------- the match

    @Test
    fun `a show everyone watches is a full match when everyone is seated`() {
        val lib = library(show("1", "Arcane", listOf("ben", "cal", "dee")))
        val result = Couch.build(lib, setOf("me", "ben", "cal", "dee"))

        assertEquals(1, result.primary.size)
        assertEquals(0, result.secondary.size)
        assertTrue(result.primary[0].missing.isEmpty())
    }

    @Test
    fun `the same show is only a partial match when someone on it is absent`() {
        // The rule the whole app turns on: two of you watching a show the other two
        // are also on would leave those two behind, so it is NOT offered as a full
        // match. Getting this backwards is the single most expensive mistake here.
        val lib = library(show("1", "Arcane", listOf("ben", "cal", "dee")))
        val result = Couch.build(lib, setOf("me", "ben"))

        assertEquals(0, result.primary.size)
        assertEquals(1, result.secondary.size)
        assertEquals(listOf("cal", "dee"), result.secondary[0].absent)
        assertTrue(result.secondary[0].missing.isEmpty())
    }

    @Test
    fun `a full match needs the show's people and the couch to be the same set`() {
        val lib = library(show("1", "Arcane", listOf("ben")))

        // Exactly the show's people are seated.
        val exact = Couch.build(lib, setOf("me", "ben"))
        assertEquals(1, exact.primary.size)
        assertTrue(exact.primary[0].absent.isEmpty())
        assertTrue(exact.primary[0].missing.isEmpty())

        // One extra person on the couch who is not on the show.
        val extra = Couch.build(lib, setOf("me", "ben", "cal"))
        assertEquals(0, extra.primary.size)
        assertEquals(listOf("cal"), extra.secondary[0].missing)

        // One of the show's people has gone to bed.
        val short = Couch.build(lib, setOf("me"))
        assertEquals(0, short.primary.size)
        assertEquals(listOf("ben"), short.secondary[0].absent)
    }

    @Test
    fun `shows nobody would fall behind on sort above shows someone would`() {
        val lib = library(
            show("1", "Everyone", listOf("ben", "cal")),
            show("2", "JustUs", listOf("ben")),
        )
        // Seated: me and ben. "JustUs" is an exact match; "Everyone" leaves cal behind.
        val result = Couch.build(lib, setOf("me", "ben"))

        assertEquals(listOf("JustUs"), result.primary.map { it.show.title })
        assertEquals(listOf("Everyone"), result.secondary.map { it.show.title })
    }

    @Test
    fun `a show a seated person is not on lands in the partial list`() {
        val lib = library(show("1", "Shogun", listOf("ben")))
        val result = Couch.build(lib, setOf("me", "ben", "cal"))

        assertEquals(0, result.primary.size)
        assertEquals(1, result.secondary.size)
        assertEquals(listOf("cal"), result.secondary[0].missing)
        assertEquals(listOf("me", "ben"), result.secondary[0].matched)
        assertTrue(result.secondary[0].absent.isEmpty())
    }

    @Test
    fun `shows nobody seated is on are not listed at all`() {
        // Bluey is watched by Me and Dee. With only Cal seated, nobody on
        // the couch is on it, so it is not offered in either list.
        val lib = library(show("1", "Bluey", listOf("dee")))
        val result = Couch.build(lib, setOf("cal"))

        assertEquals(0, result.primary.size)
        assertEquals(0, result.secondary.size)
    }

    @Test
    fun `snoozed and finished shows are kept out of the two live lists`() {
        val lib = library(
            show("1", "Active", emptyList()),
            show("2", "Snoozed", emptyList(), state = Show.STATE_SNOOZED),
            show("3", "Shelved", emptyList(), state = Show.STATE_ABANDONED),
            show("4", "Done", emptyList(), state = Show.STATE_FINISHED),
        )
        val result = Couch.build(lib, setOf("me"))

        assertEquals(1, result.primary.size)
        assertEquals(1, result.snoozed.size)
        assertEquals(2, result.abandoned.size)
    }

    @Test
    fun `the most recently watched show comes first`() {
        val lib = library(
            show("old", "Older", emptyList(), lastWatchedAt = T1),
            show("new", "Newer", emptyList(), lastWatchedAt = T3),
        )
        val result = Couch.build(lib, setOf("me"))
        assertEquals("Newer", result.primary[0].show.title)
    }

    // ------------------------------------------------------------- the merge

    @Test
    fun `edits to different shows on two phones both survive`() {
        // The whole point of merging per record. A whole-file overwrite would drop
        // one of these two edits entirely.
        val phoneA = library(
            show("1", "Severance", emptyList(), updatedAt = T3),
            show("2", "The Bear", emptyList(), updatedAt = T1),
        )
        val phoneB = library(
            show("1", "Severance", emptyList(), updatedAt = T1),
            show("2", "The Bear", emptyList(), updatedAt = T3),
        )

        val merged = Merge.libraries(phoneA, phoneB)
        assertEquals(2, merged.shows.size)
        assertEquals(T3, merged.shows.first { it.id == "1" }.updatedAt)
        assertEquals(T3, merged.shows.first { it.id == "2" }.updatedAt)
    }

    @Test
    fun `the later edit to the same show wins`() {
        val older = library(show("1", "Old title", emptyList(), updatedAt = T1))
        val newer = library(show("1", "New title", emptyList(), updatedAt = T3))

        assertEquals("New title", Merge.libraries(older, newer).shows.single().title)
        assertEquals("New title", Merge.libraries(newer, older).shows.single().title)
    }

    @Test
    fun `a deletion is not undone by an older copy from the other phone`() {
        val stillHasIt = library(show("1", "Westworld", emptyList(), updatedAt = T1))
        val deletedIt = Library(
            updatedAt = T2,
            people = people,
            shows = emptyList(),
            deleted = listOf(Tombstone("1", "show", T2)),
        )

        val merged = Merge.libraries(stillHasIt, deletedIt)
        assertTrue(merged.shows.isEmpty())
        assertEquals(1, merged.deleted.size)
    }

    @Test
    fun `a show edited after it was deleted elsewhere comes back`() {
        val edited = library(show("1", "Westworld", emptyList(), updatedAt = T3))
        val deletedEarlier = Library(
            updatedAt = T2,
            people = people,
            deleted = listOf(Tombstone("1", "show", T2)),
        )

        val merged = Merge.libraries(edited, deletedEarlier)
        assertEquals(1, merged.shows.size)
    }

    @Test
    fun `profiles added on two phones to the same service both survive`() {
        // The service record itself is last-writer-wins, but losing a profile
        // because the other phone touched the service a second later would be
        // exactly the silent loss this design exists to avoid.
        val a = Library(
            updatedAt = T1, people = people,
            services = listOf(
                Service("max", "Max", "#A88BE8", profiles = listOf(Profile("max-dad", "Dad")), updatedAt = T1)
            ),
        )
        val b = Library(
            updatedAt = T2, people = people,
            services = listOf(
                Service("max", "Max", "#A88BE8", profiles = listOf(Profile("max-kids", "Kids")), updatedAt = T2)
            ),
        )

        val merged = Merge.libraries(a, b)
        val ids = merged.services.single().profiles.map { it.id }.toSet()
        assertEquals(setOf("max-dad", "max-kids"), ids)
    }

    @Test
    fun `merging a library with itself changes nothing`() {
        val lib = library(show("1", "Arcane", listOf("cal")))
        val merged = Merge.libraries(lib, lib)
        assertEquals(lib.shows, merged.shows)
    }

    // --------------------------------------------------------------- the csv

    @Test
    fun `a title containing a comma is quoted rather than splitting the row`() {
        val lib = Library(
            updatedAt = T1,
            people = people,
            services = listOf(
                Service("max", "Max", "#A88BE8", defaultProfileId = "max-family",
                    profiles = listOf(Profile("max-family", "Family")), updatedAt = T1)
            ),
            shows = listOf(
                Show(
                    id = "1", title = "Hello, Goodbye", year = 2024,
                    serviceId = "max", profileId = "max-family",
                    watchedWith = listOf("ben", "cal"),
                    position = Position(2, 4),
                    addedAt = T1, updatedAt = T1, lastWatchedAt = T1,
                )
            ),
        )

        val csv = Csv.render(lib)
        val dataRow = csv.lines()[1]

        // The title is quoted because it contains a comma. The names are not,
        // because a semicolon needs no quoting - which is exactly why they are
        // joined with one.
        assertTrue(dataRow.startsWith("\"Hello, Goodbye\","))
        assertTrue(dataRow.contains("Ben; Cal"))
        assertFalse(dataRow.contains("\"Ben; Cal\""))
        assertTrue(dataRow.contains("Max,Family"))
        assertEquals(2, csv.trim().lines().size)
    }

    @Test
    fun `snoozed and finished shows stay in the csv with their status`() {
        val lib = library(
            show("1", "Active", emptyList()),
            show("2", "Shelved", emptyList(), state = Show.STATE_ABANDONED),
        )
        val csv = Csv.render(lib)
        assertTrue(csv.contains("abandoned"))
        assertTrue(csv.contains("active"))
        assertFalse(csv.contains("null"))
    }

    companion object {
        private const val T1 = "2026-01-01T10:00:00Z"
        private const val T2 = "2026-02-01T10:00:00Z"
        private const val T3 = "2026-03-01T10:00:00Z"
    }
}
