package net.shehane.watching

import net.shehane.watching.data.Gemini
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading a Gemini response.
 *
 * Worth pinning down separately because the cloud path cannot be exercised in a
 * build with no key, and a blocked or empty answer looks almost the same as a
 * good one until you go looking for the text.
 */
class GeminiParseTest {

    @Test
    fun `the text comes out of the first candidate`() {
        val body = """
            {"candidates":[{"content":{"parts":[{"text":"- One thing\n- Another"}],
             "role":"model"},"finishReason":"STOP"}]}
        """.trimIndent()
        assertEquals("- One thing\n- Another", Gemini.parse(body))
    }

    @Test
    fun `parts are joined, because a long answer arrives in several`() {
        val body = """
            {"candidates":[{"content":{"parts":[{"text":"- One"},{"text":" thing"}]}}]}
        """.trimIndent()
        assertEquals("- One thing", Gemini.parse(body))
    }

    @Test
    fun `a blocked answer fails loudly instead of showing an empty sheet`() {
        val body = """{"candidates":[{"finishReason":"SAFETY"}],"promptFeedback":{}}"""
        val thrown = runCatching { Gemini.parse(body) }.exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
    }

    @Test
    fun `no candidates at all fails the same way`() {
        val thrown = runCatching { Gemini.parse("""{"candidates":[]}""") }.exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
    }

    @Test
    fun `an answer of only whitespace counts as no answer`() {
        val body = """{"candidates":[{"content":{"parts":[{"text":"   \n  "}]}}]}"""
        val thrown = runCatching { Gemini.parse(body) }.exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
    }
}
