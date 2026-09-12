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

    /** Fallback only. The real value is Library.homeCountry, set on the setup screen. */
    const val DEFAULT_REGION = "US"

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

    /**
     * What TMDB puts next to a show. This is the only guessing in the whole app,
     * and it is worth being clear whose guess it is: we choose which show to ask
     * about, they answer.
     */
    suspend fun recommendations(tmdbId: Int): List<SearchItem> {
        if (!isConfigured) return emptyList()
        val url = withKey("$BASE/tv/$tmdbId/recommendations?language=en-US&page=1")
        val body = Http.getString(url, authHeaders())
        return json.decodeFromString(SearchResponse.serializer(), body).results
    }

    // ------------------------------------------------------------------ season

    @Serializable
    data class Episode(
        @SerialName("season_number") val season: Int = 0,
        @SerialName("episode_number") val episode: Int = 0,
        val name: String = "",
        val overview: String? = null,
        @SerialName("air_date") val airDate: String? = null,
    ) {
        /** TMDB sends an empty string rather than omitting the field. */
        val summary: String? get() = overview?.takeIf { it.isNotBlank() }
    }

    @Serializable
    data class Season(
        @SerialName("season_number") val number: Int = 0,
        val name: String = "",
        val overview: String? = null,
        val episodes: List<Episode> = emptyList(),
    ) {
        val summary: String? get() = overview?.takeIf { it.isNotBlank() }
    }

    /**
     * One season, with every episode's title and synopsis in the same response.
     *
     * This answers both "what is the next one" and "what happened before it", so
     * neither needs a call of its own.
     */
    suspend fun season(tmdbId: Int, number: Int): Season {
        val url = withKey("$BASE/tv/$tmdbId/season/$number?language=en-US")
        val body = Http.getString(url, authHeaders())
        return json.decodeFromString(Season.serializer(), body)
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
    suspend fun providerNames(tmdbId: Int, region: String = DEFAULT_REGION): List<String> {
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


    // ------------------------------------------------------- travelling

    @Serializable
    data class Country(
        @SerialName("iso_3166_1") val code: String,
        @SerialName("english_name") val name: String,
    )

    /** Every country TMDB knows, for the picker. Fetched once and held for the session. */
    private var countryCache: List<Country>? = null

    suspend fun countries(): List<Country> {
        countryCache?.let { return it }
        val body = Http.getString(withKey("$BASE/configuration/countries?language=en-US"), authHeaders())
        val list = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Country.serializer()), body)
            .filter { it.name.isNotBlank() }
            .sortedBy { it.name }
        countryCache = list
        return list
    }

    /**
     * What a show costs in one country. Rights are sold per country, so a show on
     * Hulu at home can be on Netflix abroad, or missing entirely. That difference
     * is the entire point of the travelling screen.
     */
    data class Availability(
        val included: List<String>,
        val rentOrBuy: List<String>,
        val link: String?,
    ) {
        val isStreamable: Boolean get() = included.isNotEmpty()
        val isAnywhere: Boolean get() = included.isNotEmpty() || rentOrBuy.isNotEmpty()
    }

    /**
     * Every country at once, keyed by ISO code.
     *
     * The endpoint returns the whole world in one response - often a hundred-odd
     * countries - so asking for one country and discarding the rest meant a fresh
     * call every time the country changed, and no way to answer "well, where IS
     * it available then?". One fetch per show now serves both questions.
     */
    suspend fun availabilityEverywhere(tmdbId: Int): Map<String, Availability> {
        val body = Http.getString(withKey("$BASE/tv/$tmdbId/watch/providers"), authHeaders())
        val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonObject
            ?: return emptyMap()

        return results.mapNotNull { (code, value) ->
            val forRegion = value as? JsonObject ?: return@mapNotNull null

            fun names(key: String): List<String> {
                val arr = forRegion[key] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
                return arr.mapNotNull { (it as? JsonObject)?.get("provider_name")?.jsonPrimitive?.contentOrNull }
            }

            code to Availability(
                included = (names("flatrate") + names("free") + names("ads")).distinct(),
                rentOrBuy = (names("rent") + names("buy")).distinct(),
                link = forRegion["link"]?.jsonPrimitive?.contentOrNull,
            )
        }.toMap()
    }

    /**
     * What is popular and included with a subscription in one country. Discover
     * rather than the plain popular list, because "popular" with no region says
     * nothing about whether you could actually watch it where you are standing.
     */
    suspend fun popularIn(region: String, providerIds: List<Int> = emptyList()): List<SearchItem> {
        if (!isConfigured) return emptyList()
        val onlyMine =
            if (providerIds.isEmpty()) ""
            else "&with_watch_providers=" + providerIds.joinToString("|")
        val url = withKey(
            "$BASE/discover/tv?watch_region=$region&with_watch_monetization_types=flatrate" +
                onlyMine + "&sort_by=popularity.desc&page=1&language=en-US"
        )
        val body = Http.getString(url, authHeaders())
        return json.decodeFromString(SearchResponse.serializer(), body).results
    }

    @Serializable
    private data class ProviderList(val results: List<ProviderRow> = emptyList())

    @Serializable
    private data class ProviderRow(
        @SerialName("provider_id") val id: Int,
        @SerialName("provider_name") val name: String = "",
    )

    private val providerCache = mutableMapOf<String, List<ProviderRow>>()

    /**
     * TMDB's ids for the services you actually pay for, matched by name the same
     * way an added show is matched to a service.
     *
     * Without this the "popular" list is whatever is popular on any subscription
     * in the country, which is not the same claim at all. An empty answer means we
     * could not match anything, and the caller should say so rather than pretend.
     */
    suspend fun providerIdsFor(region: String, services: List<Service>): List<Int> {
        if (!isConfigured || services.isEmpty()) return emptyList()
        val rows = providerCache.getOrPut(region) {
            runCatching {
                val body = Http.getString(
                    withKey("$BASE/watch/providers/tv?language=en-US&watch_region=$region"),
                    authHeaders(),
                )
                json.decodeFromString(ProviderList.serializer(), body).results
            }.getOrDefault(emptyList())
        }
        if (rows.isEmpty()) return emptyList()

        return services.mapNotNull { service ->
            val s = normalise(service.name)
            rows.firstOrNull { row ->
                val p = normalise(row.name)
                p == s || p.startsWith(s) || s.startsWith(p)
            }?.id
        }.distinct()
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
