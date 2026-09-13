package net.shehane.watching.data

import net.shehane.watching.model.Library
import net.shehane.watching.model.Person
import net.shehane.watching.model.Service
import net.shehane.watching.model.Show
import net.shehane.watching.model.Tombstone

/**
 * Record-by-record merge of two copies of the library.
 *
 * This is the whole reason the sync does not lose work. A whole-file overwrite
 * means the second phone to upload erases the first. Here, each show, person and
 * service is compared on its own updatedAt, so edits to different records on two
 * offline phones all survive. Only a collision on the same record loses anything,
 * and it loses that one record's edit rather than everything.
 *
 * Deletions are tombstones with their own date, so an older copy cannot resurrect
 * something the other phone deleted.
 */
object Merge {

    fun libraries(a: Library, b: Library): Library {
        val tombstones = mergeTombstones(a.deleted, b.deleted)
        val graveyard = tombstones.associateBy { it.id }

        fun buried(id: String, updatedAt: String): Boolean {
            val t = graveyard[id] ?: return false
            // A record edited after it was deleted elsewhere comes back. That is the
            // same last-writer-wins rule the rest of the merge uses.
            return !Clock.newer(updatedAt, t.deletedAt)
        }

        val people = mergeBy(a.people, b.people, { it.id }, { it.updatedAt })
            .filterNot { buried(it.id, it.updatedAt) }
        val services = mergeServices(a.services, b.services)
            .filterNot { buried(it.id, it.updatedAt) }
        val shows = mergeBy(a.shows, b.shows, { it.id }, { it.updatedAt })
            .filterNot { buried(it.id, it.updatedAt) }

        // One saved recap per show, newest wins. A recap for a show that is no
        // longer in the library is dropped with it.
        val showIds = shows.mapTo(HashSet()) { it.id }
        val recaps = mergeBy(a.recaps, b.recaps, { it.showId }, { it.updatedAt })
            .filter { it.showId in showIds }

        // The settings that live on the library itself rather than on a record.
        // They have no updatedAt of their own, so the newer library wins outright.
        // Leaving them out of this constructor silently reset them to the defaults
        // on every sync, which is how a setting could be changed and then undo
        // itself a few seconds later.
        val newer = if (Clock.newer(a.updatedAt, b.updatedAt)) a else b

        return Library(
            schemaVersion = maxOf(a.schemaVersion, b.schemaVersion),
            updatedAt = newer.updatedAt,
            people = people.sortedBy { it.order },
            services = services.sortedBy { it.name.lowercase() },
            homeCountry = newer.homeCountry,
            summaryMode = newer.summaryMode,
            shows = shows,
            recaps = recaps,
            deleted = tombstones,
        )
    }

    /**
     * Services merge one level deeper: two phones can each add a profile to the
     * same service, and losing one because the service record is a second newer
     * would be exactly the kind of silent loss this whole design avoids.
     */
    private fun mergeServices(a: List<Service>, b: List<Service>): List<Service> {
        val byId = LinkedHashMap<String, Service>()
        for (s in a) byId[s.id] = s
        for (s in b) {
            val existing = byId[s.id]
            byId[s.id] = if (existing == null) s else mergeService(existing, s)
        }
        return byId.values.toList()
    }

    private fun mergeService(a: Service, b: Service): Service {
        val winner = if (Clock.newer(a.updatedAt, b.updatedAt)) a else b
        val profiles = LinkedHashMap<String, net.shehane.watching.model.Profile>()
        for (p in a.profiles) profiles[p.id] = p
        for (p in b.profiles) profiles.putIfAbsent(p.id, p)
        // The winner's name for a profile wins, but neither side's profiles vanish.
        for (p in winner.profiles) profiles[p.id] = p
        val defaultId = winner.defaultProfileId?.takeIf { id -> profiles.containsKey(id) }
            ?: profiles.keys.firstOrNull()
        return winner.copy(profiles = profiles.values.toList(), defaultProfileId = defaultId)
    }

    private fun <T> mergeBy(
        a: List<T>,
        b: List<T>,
        id: (T) -> String,
        updatedAt: (T) -> String,
    ): List<T> {
        val byId = LinkedHashMap<String, T>()
        for (item in a) byId[id(item)] = item
        for (item in b) {
            val existing = byId[id(item)]
            byId[id(item)] = when {
                existing == null -> item
                Clock.newer(updatedAt(item), updatedAt(existing)) -> item
                else -> existing
            }
        }
        return byId.values.toList()
    }

    private fun mergeTombstones(a: List<Tombstone>, b: List<Tombstone>): List<Tombstone> {
        val byId = LinkedHashMap<String, Tombstone>()
        for (t in a) byId[t.id] = t
        for (t in b) {
            val existing = byId[t.id]
            byId[t.id] = when {
                existing == null -> t
                Clock.newer(t.deletedAt, existing.deletedAt) -> t
                else -> existing
            }
        }
        return byId.values.toList()
    }

    // Kept so the unused-import warning on Person/Show does not appear; both types
    // are referenced only through the generic helper above.
    @Suppress("unused")
    private fun typeAnchors(p: Person, s: Show) = p.id + s.id
}
