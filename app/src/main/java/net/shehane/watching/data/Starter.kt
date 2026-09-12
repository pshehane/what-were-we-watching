package net.shehane.watching.data

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.shehane.watching.BuildConfig
import net.shehane.watching.model.Library
import net.shehane.watching.model.Person
import net.shehane.watching.model.Profile
import net.shehane.watching.model.Service
import java.util.Locale

/**
 * What the library looks like the very first time the app runs.
 *
 * This repository is public, so no household's names are in it. Real names come
 * from an optional starter file in the *builder's* home directory, which the
 * build embeds into the APK. See starter-file.example.json.
 *
 * With no starter file the app still works: it seeds one person called "Me" and
 * the common services with no profiles, and everything else is added from the
 * Services and profiles screen. Turning that screen into a guided first-run
 * setup is a version 3 job; nothing here blocks it.
 */
object Starter {

    // ---------------------------------------------------------------- the file

    @Serializable
    private data class StarterFile(
        val me: StarterPerson? = null,
        val people: List<StarterPerson> = emptyList(),
        val services: List<StarterService> = emptyList(),
    )

    @Serializable
    private data class StarterPerson(
        val name: String,
        val color: String? = null,
    )

    @Serializable
    private data class StarterService(
        val name: String,
        val tint: String? = null,
        val profiles: List<String> = emptyList(),
        val default: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Colours handed out in order when the starter file does not name one. */
    private val PERSON_COLOURS = listOf(
        "#E9A06A", "#F09FBC", "#6FC0DE", "#85CE9A", "#D9B36B", "#B49BE0",
    )

    /**
     * The services most households have. Names of streaming services are not
     * private, and starting with an empty list would make the add sheet useless
     * on day one. Profiles are left empty, because those are the part that
     * differs per household.
     */
    private val COMMON_SERVICES = listOf(
        "Netflix" to "#E0574C",
        "Max" to "#A88BE8",
        "Hulu" to "#4FD98F",
        "Disney+" to "#7FA8E8",
        "Prime Video" to "#86BEDD",
        "Apple TV+" to "#D6D2CC",
    )

    /** True when this build was made on a machine that had a starter file. */
    val hasStarter: Boolean get() = BuildConfig.STARTER_SEED.isNotBlank()

    // ----------------------------------------------------------------- seeding

    fun seed(meId: String): Library = seedFromJson(decode(), meId)

    /**
     * Split out from [seed] so first-run seeding can be tested on the JVM: this
     * half touches no Android class. A null, blank or malformed document falls
     * back to the generic seed rather than failing, because a bad starter file
     * must never be the reason the app will not open.
     */
    internal fun seedFromJson(text: String?, meId: String): Library {
        val now = Clock.now()
        if (text.isNullOrBlank()) return generic(meId, now)
        val file = runCatching { json.decodeFromString(StarterFile.serializer(), text) }.getOrNull()
        return if (file == null) generic(meId, now) else fromFile(file, meId, now)
    }

    /** The embedded starter file, or null when this build had none. */
    private fun decode(): String? {
        val raw = BuildConfig.STARTER_SEED
        if (raw.isBlank()) return null
        return runCatching {
            String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun generic(meId: String, now: String): Library = Library(
        updatedAt = now,
        people = listOf(Person(meId, "Me", PERSON_COLOURS[0], 0, now)),
        services = COMMON_SERVICES.mapIndexed { _, (name, tint) ->
            Service(id = slug(name), name = name, tint = tint, updatedAt = now)
        },
    )

    private fun fromFile(file: StarterFile, meId: String, now: String): Library {
        val people = mutableListOf<Person>()
        people += Person(
            id = meId,
            name = file.me?.name?.takeIf { it.isNotBlank() } ?: "Me",
            color = file.me?.color ?: PERSON_COLOURS[0],
            order = 0,
            updatedAt = now,
        )
        file.people.forEachIndexed { index, p ->
            if (p.name.isBlank()) return@forEachIndexed
            val id = slug(p.name, people.map { it.id })
            people += Person(
                id = id,
                name = p.name.trim(),
                color = p.color ?: PERSON_COLOURS[(index + 1) % PERSON_COLOURS.size],
                order = index + 1,
                updatedAt = now,
            )
        }

        val sourceServices = file.services.ifEmpty {
            COMMON_SERVICES.map { (name, tint) -> StarterService(name = name, tint = tint) }
        }
        val services = mutableListOf<Service>()
        for (s in sourceServices) {
            if (s.name.isBlank()) continue
            val serviceId = slug(s.name, services.map { it.id })
            val profiles = mutableListOf<Profile>()
            for (name in s.profiles) {
                if (name.isBlank()) continue
                profiles += Profile(slug("$serviceId-$name", profiles.map { it.id }), name.trim())
            }
            val defaultId = profiles.firstOrNull { it.name.equals(s.default?.trim(), true) }?.id
                ?: profiles.firstOrNull()?.id
            services += Service(
                id = serviceId,
                name = s.name.trim(),
                tint = s.tint ?: "#B0A79A",
                defaultProfileId = defaultId,
                profiles = profiles,
                updatedAt = now,
            )
        }

        return Library(updatedAt = now, people = people, services = services)
    }

    // ----------------------------------------------------------------- helpers

    private fun slug(raw: String, taken: List<String> = emptyList()): String {
        val base = raw.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "item" }
        if (base !in taken) return base
        var n = 2
        while ("$base-$n" in taken) n++
        return "$base-$n"
    }
}
