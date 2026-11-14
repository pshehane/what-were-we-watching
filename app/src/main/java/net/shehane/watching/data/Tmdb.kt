package net.shehane.watching.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.shehane.watching.BuildConfig
import net.shehane.watching.model.Service
import net.shehane.watching.model.Show

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

    /**
     * One row of a result list, from either catalog.
     *
     * TMDB names the same two things differently: a series has `name` and
     * `first_air_date`, a film has `title` and `release_date`. Both are read here
     * and the rest of the app sees one pair of properties, so no screen has to
     * know which catalog a row came from.
     */
    @Serializable
    data class SearchItem(
        val id: Int,
        @SerialName("name") val seriesName: String = "",
        @SerialName("title") val movieTitle: String = "",
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        val overview: String? = null,
        /** Only /search/multi sends this. Every other endpoint is one catalog. */
        @SerialName("media_type") val mediaType: String? = null,
        /**
         * Which catalog this came from, as the app's own word. Not from the
         * response: single-catalog endpoints do not say, so the call that knows
         * stamps it. Defaults to a series, which is what every TV endpoint returns.
         */
        @Transient val medium: String = Show.MEDIUM_TV,
    ) {
        val name: String get() = seriesName.ifBlank { movieTitle }
        val year: Int? get() = (firstAirDate ?: releaseDate)?.take(4)?.toIntOrNull()
        val isMovie: Boolean get() = medium == Show.MEDIUM_MOVIE
    }

    /** Stamps rows from a single-catalog endpoint with the catalog they came from. */
    private fun List<SearchItem>.asMedium(medium: String): List<SearchItem> =
        if (medium == Show.MEDIUM_TV) this else map { it.copy(medium = medium) }

    /**
     * Both catalogs in one list, which is what makes adding a film no different
     * from adding a series: one box, one list, and the row says which it is.
     *
     * TMDB also returns people from this endpoint. They are dropped, along with
     * anything with no title, which is how an unreleased stub usually arrives.
     */
    suspend fun search(query: String): List<SearchItem> {
        if (query.isBlank() || !isConfigured) return emptyList()
        val url = withKey(
            "$BASE/search/multi?query=${Http.encode(query)}&include_adult=false&language=en-US&page=1"
        )
        val body = Http.getString(url, authHeaders())
        return json.decodeFromString(SearchResponse.serializer(), body).results
            .filter { it.mediaType == "tv" || it.mediaType == "movie" }
            .filter { it.name.isNotBlank() }
            .map { it.copy(medium = if (it.mediaType == "movie") Show.MEDIUM_MOVIE else Show.MEDIUM_TV) }
    }

    /**
     * What TMDB puts next to a show. This is the only guessing in the whole app,
     * and it is worth being clear whose guess it is: we choose which show to ask
     * about, they answer.
     *
     * A film's neighbours are films and a series' are series, because that is how
     * the two catalogs are kept. Asking about what you last loved therefore
     * suggests more of the same kind, which is the useful answer anyway.
     */
    suspend fun recommendations(tmdbId: Int, medium: String = Show.MEDIUM_TV): List<SearchItem> {
        if (!isConfigured) return emptyList()
        val url = withKey("$BASE/${path(medium)}/$tmdbId/recommendations?language=en-US&page=1")
        val body = Http.getString(url, authHeaders())
        return json.decodeFromString(SearchResponse.serializer(), body).results.asMedium(medium)
    }

    /** "tv" or "movie" as TMDB spells it in a URL. They happen to match our words. */
    private fun path(medium: String): String =
        if (medium == Show.MEDIUM_MOVIE) "movie" else "tv"

    // -------------------------------------------------------------------- cast

    @Serializable
    private data class AggregateCredits(val cast: List<CastMember> = emptyList())

    @Serializable
    data class CastMember(
        val name: String = "",
        val order: Int = 999,
        @SerialName("total_episode_count") val episodes: Int = 0,
        val roles: List<Role> = emptyList(),
    ) {
        val character: String? get() = roles.firstOrNull()?.character?.takeIf { it.isNotBlank() }
    }

    @Serializable
    data class Role(val character: String = "")

    /**
     * The people the show is actually about.
     *
     * TMDB lists everyone who ever appeared, fifteen hundred of them for a long
     * running show, so this keeps only those billed near the top who are in a good
     * share of the episodes. Guest stars fail both halves of that.
     */
    suspend fun mainCast(
        tmdbId: Int,
        episodeCount: Int?,
        limit: Int = 8,
        medium: String = Show.MEDIUM_TV,
    ): List<CastMember> {
        if (!isConfigured) return emptyList()
        if (medium == Show.MEDIUM_MOVIE) return movieCast(tmdbId, limit)
        val url = withKey("$BASE/tv/$tmdbId/aggregate_credits?language=en-US")
        val body = Http.getString(url, authHeaders())
        val all = json.decodeFromString(AggregateCredits.serializer(), body).cast

        val enough = if (episodeCount != null && episodeCount > 0) episodeCount / 2 else 0
        return all
            .filter { it.order < 15 && it.episodes >= enough && it.character != null }
            .sortedWith(compareByDescending<CastMember> { it.episodes }.thenBy { it.order })
            .take(limit)
    }

    /**
     * A film's billing is simply its billing. There is no episode count to weigh,
     * so the top of the list is the answer.
     */
    private suspend fun movieCast(tmdbId: Int, limit: Int): List<CastMember> {
        val url = withKey("$BASE/movie/$tmdbId/credits?language=en-US")
        val body = Http.getString(url, authHeaders())
        return json.decodeFromString(MovieCredits.serializer(), body).cast
            .filter { it.character.isNotBlank() }
            .sortedBy { it.order }
            .take(limit)
            .map { CastMember(name = it.name, order = it.order, roles = listOf(Role(it.character))) }
    }

    @Serializable
    private data class MovieCredits(val cast: List<MovieCastMember> = emptyList())

    @Serializable
    private data class MovieCastMember(
        val name: String = "",
        val order: Int = 999,
        val character: String = "",
    )

    // ------------------------------------------------------------------ season

    @Serializable
    data class Episode(
        @SerialName("season_number") val season: Int = 0,
        @SerialName("episode_number") val episode: Int = 0,
        val name: String = "",
        val overview: String? = null,
        @SerialName("air_date") val airDate: String? = null,
        /** Minutes. Filled in for most episodes, unlike the show-level field. */
        val runtime: Int? = null,
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
        @SerialName("name") val seriesName: String = "",
        @SerialName("title") val movieTitle: String = "",
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("number_of_seasons") val seasonCount: Int? = null,
        @SerialName("number_of_episodes") val episodeCount: Int? = null,
        // A list, because a show can have run at several lengths over its life.
        // The first entry is the usual one.
        @SerialName("episode_run_time") val runTimes: List<Int> = emptyList(),
        /** A film's length, in minutes. The series endpoint has no such field. */
        val runtime: Int? = null,
        val overview: String? = null,
    ) {
        val name: String get() = seriesName.ifBlank { movieTitle }
        val year: Int? get() = (firstAirDate ?: releaseDate)?.take(4)?.toIntOrNull()

        /**
         * How long one sitting is, whichever catalog answered. A series publishes
         * a list of episode lengths and often an empty one; a film publishes the
         * single number that is the whole point of it.
         */
        val sittingMinutes: Int?
            get() = runtime?.takeIf { it > 0 } ?: runTimes.firstOrNull { it > 0 }
    }

    suspend fun detail(tmdbId: Int, medium: String = Show.MEDIUM_TV): Detail {
        val body = Http.getString(withKey("$BASE/${path(medium)}/$tmdbId?language=en-US"), authHeaders())
        return json.decodeFromString(Detail.serializer(), body)
    }

    // --------------------------------------------------------------- providers

    /**
     * Provider names rather than provider ids. TMDB's numeric ids are stable but
     * opaque, and matching on the name means a service the user typed in by hand
     * still lines up without anyone looking an id up.
     */
    suspend fun providerNames(
        tmdbId: Int,
        region: String = DEFAULT_REGION,
        medium: String = Show.MEDIUM_TV,
    ): List<String> {
        val body = Http.getString(withKey("$BASE/${path(medium)}/$tmdbId/watch/providers"), authHeaders())
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
    suspend fun availabilityEverywhere(
        tmdbId: Int,
        medium: String = Show.MEDIUM_TV,
    ): Map<String, Availability> {
        val body = Http.getString(withKey("$BASE/${path(medium)}/$tmdbId/watch/providers"), authHeaders())
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
    suspend fun popularIn(
        region: String,
        providerIds: List<Int> = emptyList(),
        medium: String = Show.MEDIUM_TV,
    ): List<SearchItem> {
        if (!isConfigured) return emptyList()
        val onlyMine =
            if (providerIds.isEmpty()) ""
            else "&with_watch_providers=" + providerIds.joinToString("|")
        val url = withKey(
            "$BASE/discover/${path(medium)}?watch_region=$region&with_watch_monetization_types=flatrate" +
                onlyMine + "&sort_by=popularity.desc&page=1&language=en-US"
        )
        val body = Http.getString(url, authHeaders())
        return json.decodeFromString(SearchResponse.serializer(), body).results.asMedium(medium)
    }

    /**
     * Both catalogs, taken in turn so neither one owns the top of the list.
     *
     * Films are more numerous and churn faster than series, so concatenating
     * would bury every series below a wall of them.
     */
    suspend fun popularBothIn(region: String, providerIds: List<Int> = emptyList()): List<SearchItem> {
        val series = runCatching { popularIn(region, providerIds, Show.MEDIUM_TV) }.getOrDefault(emptyList())
        val films = runCatching { popularIn(region, providerIds, Show.MEDIUM_MOVIE) }.getOrDefault(emptyList())
        return buildList {
            for (i in 0 until maxOf(series.size, films.size)) {
                series.getOrNull(i)?.let(::add)
                films.getOrNull(i)?.let(::add)
            }
        }
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
                // The tv and movie provider lists carry the same ids for the same
                // services, so one fetch answers for both catalogs.
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
    suspend fun wikipediaUrl(
        tmdbId: Int,
        title: String,
        year: Int?,
        medium: String = Show.MEDIUM_TV,
    ): String? {
        val viaWikidata = runCatching { wikipediaViaWikidata(tmdbId, medium) }.getOrNull()
        if (viaWikidata != null) return viaWikidata
        return runCatching { wikipediaViaSearch(title, year, medium) }.getOrNull()
    }

    private suspend fun wikipediaViaWikidata(tmdbId: Int, medium: String): String? {
        val body = Http.getString(withKey("$BASE/${path(medium)}/$tmdbId/external_ids"), authHeaders())
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

    private suspend fun wikipediaViaSearch(title: String, year: Int?, medium: String): String? {
        val kind = if (medium == Show.MEDIUM_MOVIE) "film" else "TV series"
        val q = if (year != null) "$title $kind $year" else "$title $kind"
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
