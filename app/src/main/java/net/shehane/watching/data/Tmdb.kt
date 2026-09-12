package net.shehane.watching.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.shehane.watching.BuildConfig
import net.shehane.watching.model.Service

/**
 * The catalog. One call while you type, three more when you tap a result, and
 * then never again for that show: everything useful is copied onto the show
 * record so opening something you already track makes no network call at all.
 */
object Tmdb {

    private const val BASE = "https://api.themoviedb.org/3"
    const val IMAGE_SMALL = "https://image.tmdb.org/t/p/w185"
    const val IMAGE_LARGE = "https://image.tmdb.org/t/p/w500"

    /** Streaming availability is per country; this is the one place that assumes US. */
    const val REGION = "US"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val isConfigured: Boolean
        get() = BuildConfig.TMDB_READ_TOKEN.isNotBlank() || BuildConfig.TMDB_API_KEY.isNotBlank()

    private fun authHeaders(): Map<String, String> =
        if (BuildConfig.TMDB_READ_TOKEN.isNotBlank()) {
            mapOf("Authorization" to "Bearer ${BuildConfig.TMDB_READ_TOKEN}")
        } else {
            emptyMap()
        }

    /** The older key goes on the query string when no bearer token is configured. */
    private fun withKey(url: String): String =
        if (BuildConfig.TMDB_READ_TOKEN.isNotBlank() || BuildConfig.TMDB_API_KEY.isBlank()) url
        else url + (if ('?' in url) "&" else "?") + "api_key=${BuildConfig.TMDB_API_KEY}"

    // ------------------------------------------------------------------ search

    @Serializable
    private data class SearchResponse(val results: List<SearchItem> = emptyList())

    @Serializable
    data class SearchItem(
        val id: Int,
        val name: String = "",
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        val overview: String? = null,
    ) {
        val year: Int? get() = firstAirDate?.take(4)?.toIntOrNull()
    }

    suspend fun search(query: String): List<SearchItem> {
        if (query.isBlank() || !isConfigured) return emptyList()
        val url = withKey("$BASE/search/tv?query=${Http.encode(query)}&include_adult=false&language=en-US&page=1")
        val body = Http.getString(url, authHeaders())
        return json.decodeFromString(SearchResponse.serializer(), body).results
    }

    // ------------------------------------------------------------------ detail

    @Serializable
    data class Detail(
        val id: Int,
        val name: String = "",
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("number_of_seasons") val seasonCount: Int? = null,
        @SerialName("number_of_episodes") val episodeCount: Int? = null,
        val overview: String? = null,
    ) {
        val year: Int? get() = firstAirDate?.take(4)?.toIntOrNull()
    }

    suspend fun detail(tmdbId: Int): Detail {
        val body = Http.getString(withKey("$BASE/tv/$tmdbId?language=en-US"), authHeaders())
        return json.decodeFromString(Detail.serializer(), body)
    }

    // --------------------------------------------------------------- providers

    /**
     * Provider names rather than provider ids. TMDB's numeric ids are stable but
     * opaque, and matching on the name means a service the user typed in by hand
     * still lines up without anyone looking an id up.
     */
    suspend fun providerNames(tmdbId: Int, region: String = REGION): List<String> {
        val body = Http.getString(withKey("$BASE/tv/$tmdbId/watch/providers"), authHeaders())
        val root = json.parseToJsonElement(body).jsonObject
        val results = root["results"]?.jsonObject ?: return emptyList()
        val forRegion = results[region]?.jsonObject ?: return emptyList()

        // flatrate is "included with the subscription", which is what we care about.
        // free and ads are close enough to count; rent and buy are not.
        val names = mutableListOf<String>()
        for (key in listOf("flatrate", "free", "ads")) {
            val arr = forRegion[key] ?: continue
            for (el in arr as? kotlinx.serialization.json.JsonArray ?: continue) {
                val name = (el as? JsonObject)?.get("provider_name")?.jsonPrimitive?.contentOrNull
                if (!name.isNullOrBlank()) names += name
            }
        }
        return names.distinct()
    }

    /** Picks the user's own service that matches one of TMDB's provider names. */
    fun matchService(services: List<Service>, providerNames: List<String>): Service? {
        val normalisedProviders = providerNames.map(::normalise)
        return services.firstOrNull { service ->
            val s = normalise(service.name)
            normalisedProviders.any { p -> p == s || p.startsWith(s) || s.startsWith(p) }
        }
    }

    /**
     * "Disney Plus", "Disney+", "HBO Max" and "Max" all have to land on the same
     * thing, so punctuation goes, "plus" goes, and the handful of rebrands that
     * matter are folded by hand.
     */
    private fun normalise(raw: String): String {
        var s = raw.lowercase().replace(Regex("[^a-z0-9]"), "")
        s = s.removeSuffix("plus")
        s = when {
            s.startsWith("hbomax") -> "max"
            s.startsWith("amazonprimevideo") -> "primevideo"
            s.startsWith("amazonprime") -> "primevideo"
            s == "prime" -> "primevideo"
            s.startsWith("appletv") -> "appletv"
            s.startsWith("disney") -> "disney"
            s.startsWith("paramount") -> "paramount"
            else -> s
        }
        return s
    }

    // ------------------------------------------------------------- wikipedia

    /**
     * TMDB hands out a Wikidata id; Wikidata hands out the article title. When
     * that chain breaks anywhere, fall back to searching Wikipedia for the title,
     * which is what a person would do.
     */
    suspend fun wikipediaUrl(tmdbId: Int, title: String, year: Int?): String? {
        val viaWikidata = runCatching { wikipediaViaWikidata(tmdbId) }.getOrNull()
        if (viaWikidata != null) return viaWikidata
        return runCatching { wikipediaViaSearch(title, year) }.getOrNull()
    }

    private suspend fun wikipediaViaWikidata(tmdbId: Int): String? {
        val body = Http.getString(withKey("$BASE/tv/$tmdbId/external_ids"), authHeaders())
        val wikidataId = json.parseToJsonElement(body).jsonObject["wikidata_id"]
            ?.jsonPrimitive?.contentOrNull
        if (wikidataId.isNullOrBlank()) return null

        val wd = Http.getString(
            "https://www.wikidata.org/w/api.php?action=wbgetentities" +
                "&ids=${Http.encode(wikidataId)}&props=sitelinks&format=json&origin=*"
        )
        val title = json.parseToJsonElement(wd).jsonObject["entities"]?.jsonObject
            ?.get(wikidataId)?.jsonObject
            ?.get("sitelinks")?.jsonObject
            ?.get("enwiki")?.jsonObject
            ?.get("title")?.jsonPrimitive?.contentOrNull
            ?: return null
        return "https://en.wikipedia.org/wiki/" + title.replace(' ', '_')
    }

    private suspend fun wikipediaViaSearch(title: String, year: Int?): String? {
        val q = if (year != null) "$title TV series $year" else "$title TV series"
        val body = Http.getString(
            "https://en.wikipedia.org/w/api.php?action=query&list=search" +
                "&srsearch=${Http.encode(q)}&srlimit=1&format=json&origin=*"
        )
        val hit = json.parseToJsonElement(body).jsonObject["query"]?.jsonObject
            ?.get("search") as? kotlinx.serialization.json.JsonArray ?: return null
        val name = (hit.firstOrNull() as? JsonObject)?.get("title")?.jsonPrimitive?.contentOrNull
            ?: return null
        return "https://en.wikipedia.org/wiki/" + name.replace(' ', '_')
    }
}

/** Null instead of throwing when a JSON value is absent or not a string. */
private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
    get() = runCatching { if (isString || content != "null") content else null }.getOrNull()
