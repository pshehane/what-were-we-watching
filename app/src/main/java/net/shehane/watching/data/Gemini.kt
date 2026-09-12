package net.shehane.watching.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import net.shehane.watching.BuildConfig

/**
 * The cloud summariser.
 *
 * The key is built in from outside the repository, exactly like the TMDB one, and
 * is never asked of the user. A clone of this project builds with an empty key and
 * simply does not offer the cloud option, which is the intended behaviour rather
 * than a degraded one.
 */
object Gemini {

    private const val MODEL = "gemini-2.5-flash"
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"

    private val json = Json { ignoreUnknownKeys = true }

    val isConfigured: Boolean get() = BuildConfig.GEMINI_API_KEY.isNotBlank()

    /**
     * One request, one answer. No streaming: the sheet shows a line while it waits
     * and the whole recap arrives together, which reads better than a recap
     * assembling itself in front of you.
     */
    suspend fun summarise(prompt: String): String {
        require(isConfigured) { "No Gemini key in this build" }

        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", prompt) })
                    })
                })
            })
            putJsonObject("generationConfig") {
                // Low, not zero: a recap should read like prose, not like a form.
                put("temperature", 0.4)
                put("maxOutputTokens", 900)
            }
        }

        val response = Http.send(
            method = "POST",
            url = "$BASE/$MODEL:generateContent",
            headers = mapOf("x-goog-api-key" to BuildConfig.GEMINI_API_KEY),
            body = body.toString().toByteArray(),
            contentType = "application/json",
            readTimeoutMs = Http.MODEL_READ_TIMEOUT,
        )

        return parse(response)
    }

    /** Split out so the shape of a real response can be tested without a key. */
    internal fun parse(response: String): String =
        textOf(json.parseToJsonElement(response).jsonObject)

    /**
     * Digs the answer out. A blocked or empty response has candidates with no
     * parts, which would otherwise surface as a blank sheet rather than an error.
     */
    internal fun textOf(root: JsonObject): String {
        val candidates = root["candidates"]?.jsonArray ?: JsonArray(emptyList())
        val parts = candidates.firstOrNull()
            ?.jsonObject?.get("content")
            ?.jsonObject?.get("parts")?.jsonArray
            ?: throw IllegalStateException("Gemini returned no text")

        val text = parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            .joinToString("")
            .trim()

        if (text.isEmpty()) throw IllegalStateException("Gemini returned no text")
        return text
    }
}
