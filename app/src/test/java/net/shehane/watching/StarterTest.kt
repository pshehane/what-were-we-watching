package net.shehane.watching

import net.shehane.watching.data.Starter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * First-run seeding. This is the only code that decides what a brand new install
 * looks like, and it is now the thing standing between a public repository and
 * one household's names, so it is worth testing properly.
 */
class StarterTest {

    private val ME = "me"

    // ------------------------------------------------------------- no file

    @Test
    fun `with no starter file the app seeds one person called Me`() {
        val lib = Starter.seedFromJson(null, ME)

        assertEquals(1, lib.people.size)
        assertEquals(ME, lib.people[0].id)
        assertEquals("Me", lib.people[0].name)
    }

    @Test
    fun `with no starter file the common services are there but carry no profiles`() {
        val lib = Starter.seedFromJson(null, ME)

        assertEquals(6, lib.services.size)
        assertNotNull(lib.services.firstOrNull { it.name == "Netflix" })
        assertTrue(lib.services.all { it.profiles.isEmpty() })
        assertTrue(lib.services.all { it.defaultProfileId == null })
    }

    @Test
    fun `a blank document is the same as no document`() {
        assertEquals(1, Starter.seedFromJson("   ", ME).people.size)
    }

    /** A bad starter file must never be the reason the app will not open. */
    @Test
    fun `a malformed document falls back rather than failing`() {
        val lib = Starter.seedFromJson("{ this is not json", ME)

        assertEquals(1, lib.people.size)
        assertEquals("Me", lib.people[0].name)
        assertEquals(6, lib.services.size)
    }

    // ----------------------------------------------------------- with a file

    @Test
    fun `the owner keeps the me id whatever they are called`() {
        val lib = Starter.seedFromJson("""{"me":{"name":"Ana"}}""", ME)

        assertEquals(ME, lib.people[0].id)
        assertEquals("Ana", lib.people[0].name)
    }

    @Test
    fun `other people are seeded in order, after the owner`() {
        val lib = Starter.seedFromJson(
            """{"me":{"name":"Ana"},"people":[{"name":"Ben"},{"name":"Cal"}]}""", ME
        )

        assertEquals(listOf("Ana", "Ben", "Cal"), lib.people.map { it.name })
        assertEquals(listOf("me", "ben", "cal"), lib.people.map { it.id })
        assertEquals(listOf(0, 1, 2), lib.people.map { it.order })
    }

    @Test
    fun `everyone gets a colour even when the file names none`() {
        val lib = Starter.seedFromJson("""{"people":[{"name":"Ben"},{"name":"Cal"}]}""", ME)

        assertTrue(lib.people.all { it.color.startsWith("#") })
        assertEquals(3, lib.people.map { it.color }.toSet().size)
    }

    @Test
    fun `two people with the same name still get distinct ids`() {
        val lib = Starter.seedFromJson("""{"people":[{"name":"Sam"},{"name":"Sam"}]}""", ME)

        assertEquals(3, lib.people.size)
        assertEquals(3, lib.people.map { it.id }.toSet().size)
    }

    @Test
    fun `blank names are skipped rather than seeding an unnamed person`() {
        val lib = Starter.seedFromJson("""{"people":[{"name":"Ben"},{"name":"  "}]}""", ME)

        assertEquals(listOf("Me", "Ben"), lib.people.map { it.name })
    }

    // -------------------------------------------------------------- services

    @Test
    fun `profiles are seeded and the named default is the one marked`() {
        val lib = Starter.seedFromJson(
            """{"services":[{"name":"Netflix","profiles":["Grown-ups","Kids"],"default":"Kids"}]}""",
            ME,
        )

        val netflix = lib.services.single()
        assertEquals(listOf("Grown-ups", "Kids"), netflix.profiles.map { it.name })
        assertEquals("Kids", netflix.defaultProfile?.name)
    }

    @Test
    fun `without a named default the first profile is used`() {
        val lib = Starter.seedFromJson(
            """{"services":[{"name":"Max","profiles":["Family","Dad"]}]}""", ME
        )

        assertEquals("Family", lib.services.single().defaultProfile?.name)
    }

    @Test
    fun `a default naming a profile that does not exist falls back to the first`() {
        val lib = Starter.seedFromJson(
            """{"services":[{"name":"Max","profiles":["Family"],"default":"Nobody"}]}""", ME
        )

        assertEquals("Family", lib.services.single().defaultProfile?.name)
    }

    @Test
    fun `a service with no profiles has no default to mark`() {
        val lib = Starter.seedFromJson("""{"services":[{"name":"Hulu","profiles":[]}]}""", ME)

        assertTrue(lib.services.single().profiles.isEmpty())
        assertNull(lib.services.single().defaultProfileId)
    }

    @Test
    fun `naming people but no services still gets you the common services`() {
        val lib = Starter.seedFromJson("""{"people":[{"name":"Ben"}]}""", ME)

        assertEquals(6, lib.services.size)
    }

    @Test
    fun `unknown keys in the file are ignored rather than rejected`() {
        // The example file carries a _README array; older files may carry anything.
        val lib = Starter.seedFromJson(
            """{"_README":["notes"],"future":42,"me":{"name":"Ana"}}""", ME
        )

        assertEquals("Ana", lib.people[0].name)
    }
}
