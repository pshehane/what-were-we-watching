package net.shehane.watching

import android.app.Application
import net.shehane.watching.data.LibraryStore

/**
 * Manual wiring. Six screens and one store do not earn a dependency-injection
 * framework, and every extra plugin is another version to keep in step.
 */
class WatchingApp : Application() {
    lateinit var store: LibraryStore
        private set

    override fun onCreate() {
        super.onCreate()
        store = LibraryStore(this)
    }
}
