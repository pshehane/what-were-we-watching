package net.shehane.watching

import net.shehane.watching.data.Merge
import net.shehane.watching.data.Summary
import net.shehane.watching.model.Library
import net.shehane.watching.model.SavedRecap
import net.shehane.watching.model.Show
import net.shehane.watching.model.Tombstone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saved recaps: when one is shown instead of asking a model, and how two phones'
 * copies merge.
 */
class SavedRecapTest {

    private fun saved(
        source: String,
        upTo: String = "S2 E3",
        showId: String = "1",
        text: String = "- The story so far.",
        updatedAt: String = EARLY,
    ) = SavedRecap(showId = showId, upTo = upTo, text = text, source = source, updatedAt = updatedAt)

    private fun show(id: String) = Show(id = id, title = "Arcane", addedAt = EARLY, updatedAt = EARLY)

    // --------------------------------------------------------------- reuse

    @Test
    fun `a cloud recap is reused whichever writer is chosen`() {
        assertNotNull(Summary.reusable(saved(Library.SUMMARY_CLOUD), Library.SUMMARY_CLOUD, "S2 E3"))
        assertNotNull(Summary.reusable(saved(Library.SUMMARY_CLOUD), Library.SUMMARY_DEVICE, "S2 E3"))
    }

    @Test
    fun `a phone-written recap is only reused when the phone is the chosen writer`() {
        assertNotNull(Summary.reusable(saved(Library.SUMMARY_DEVICE), Library.SUMMARY_DEVICE, "S2 E3"))
        assertNull(Summary.reusable(saved(Library.SUMMARY_DEVICE), Library.SUMMARY_CLOUD, "S2 E3"))
    }

    @Test
    fun `a recap that stops before a different episode is not reused`() {
        assertNull(Summary.reusable(saved(Library.SUMMARY_CLOUD), Library.SUMMARY_CLOUD, "S2 E4"))
    }

    @Test
    fun `nothing is reused when recaps are turned off`() {
        assertNull(Summary.reusable(saved(Library.SUMMARY_CLOUD), Library.SUMMARY_NONE, "S2 E3"))
    }

    @Test
    fun `nothing saved means nothing reused`() {
        assertNull(Summary.reusable(null, Library.SUMMARY_CLOUD, "S2 E3"))
    }

    // --------------------------------------------------------------- merge

    @Test
    fun `the newer recap for a show wins the merge, whichever copy it is in`() {
        val older = Library(
            updatedAt = EARLY,
            shows = listOf(show("1")),
            recaps = listOf(saved(Library.SUMMARY_CLOUD, upTo = "S1 E9", text = "old", updatedAt = EARLY)),
        )
        val newer = Library(
            updatedAt = LATE,
            shows = listOf(show("1")),
            recaps = listOf(saved(Library.SUMMARY_CLOUD, upTo = "S2 E3", text = "new", updatedAt = LATE)),
        )

        assertEquals("new", Merge.libraries(older, newer).recaps.single().text)
        assertEquals("new", Merge.libraries(newer, older).recaps.single().text)
    }

    @Test
    fun `recaps for different shows from two phones are both kept`() {
        val a = Library(updatedAt = EARLY, shows = listOf(show("1"), show("2")),
            recaps = listOf(saved(Library.SUMMARY_CLOUD, showId = "1")))
        val b = Library(updatedAt = LATE, shows = listOf(show("1"), show("2")),
            recaps = listOf(saved(Library.SUMMARY_CLOUD, showId = "2")))

        assertEquals(setOf("1", "2"), Merge.libraries(a, b).recaps.map { it.showId }.toSet())
    }

    @Test
    fun `a recap for a show deleted on the other phone is dropped`() {
        val stillHasIt = Library(
            updatedAt = EARLY,
            shows = listOf(show("1")),
            recaps = listOf(saved(Library.SUMMARY_CLOUD)),
        )
        val deletedIt = Library(updatedAt = LATE, deleted = listOf(Tombstone("1", "show", LATE)))

        val merged = Merge.libraries(stillHasIt, deletedIt)
        assertTrue(merged.shows.isEmpty())
        assertTrue(merged.recaps.isEmpty())
    }

    private companion object {
        const val EARLY = "2026-01-01T10:00:00Z"
        const val LATE = "2026-01-02T10:00:00Z"
    }
}
