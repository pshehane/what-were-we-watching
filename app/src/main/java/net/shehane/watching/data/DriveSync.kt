package net.shehane.watching.data

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Collections

/**
 * The cloud half: library.json and library.csv in a folder on your own Drive.
 *
 * The scope is drive.file, which lets the app see only files it created. It cannot
 * read anything else in your Drive, which is why it is the right scope even though
 * it means the folder has to be created by the app rather than picked.
 *
 * Sign-in carries no client id and no secret. Google matches the OAuth client by
 * package name plus signing certificate at runtime, so there is nothing to keep
 * out of this public repository and nothing to configure in the build.
 */
class DriveSync(private val context: Context) {

    companion object {
        const val FOLDER_NAME = "What Were We Watching"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val PREFS = "drive"
        private const val KEY_LAST_SYNC = "last_sync"
    }

    sealed interface State {
        data object SignedOut : State
        data class Ready(val account: String?, val lastSync: String?) : State
        data class Failed(val message: String) : State
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val signInOptions: GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()

    val account: GoogleSignInAccount?
        get() = GoogleSignIn.getLastSignedInAccount(context)

    val isSignedIn: Boolean get() = account != null

    fun state(): State {
        val acct = account ?: return State.SignedOut
        return State.Ready(acct.email, prefs.getString(KEY_LAST_SYNC, null))
    }

    /** Launch this with an activity-result contract; the reply comes back as an Intent. */
    fun signInIntent(): Intent = GoogleSignIn.getClient(context, signInOptions).signInIntent

    fun onSignInResult(data: Intent?): Result<Unit> = runCatching {
        GoogleSignIn.getSignedInAccountFromIntent(data).getResult(
            com.google.android.gms.common.api.ApiException::class.java
        )
        Unit
    }

    fun signOut() {
        GoogleSignIn.getClient(context, signInOptions).signOut()
        prefs.edit().remove(KEY_LAST_SYNC).apply()
    }

    // ------------------------------------------------------------------ drive

    private fun driveOrNull(): Drive? {
        val acct = account ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = acct.account ?: return null
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("What Were We Watching")
            .build()
    }

    /**
     * The round trip. Download whatever is on Drive, merge it into what is on this
     * phone record by record, then upload the result and rewrite the CSV.
     *
     * Returns true when the remote copy actually changed something here.
     */
    suspend fun sync(store: LibraryStore): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val drive = driveOrNull() ?: throw IllegalStateException("Not signed in to Drive")
            val folderId = ensureFolder(drive)

            var changedLocally = false
            val remoteJsonId = findFile(drive, folderId, LibraryStore.JSON_NAME)
            if (remoteJsonId != null) {
                val text = download(drive, remoteJsonId)
                val remote = store.parse(text)
                if (remote != null) {
                    val before = store.library.value
                    store.mergeIn(remote)
                    changedLocally = store.library.value != before
                }
            }

            upload(drive, folderId, remoteJsonId, LibraryStore.JSON_NAME, "application/json", store.jsonText())

            val remoteCsvId = findFile(drive, folderId, LibraryStore.CSV_NAME)
            upload(drive, folderId, remoteCsvId, LibraryStore.CSV_NAME, "text/csv", store.csvText())

            prefs.edit().putString(KEY_LAST_SYNC, Clock.now()).apply()
            changedLocally
        }
    }

    private fun ensureFolder(drive: Drive): String {
        val existing = drive.files().list()
            .setSpaces("drive")
            .setQ("mimeType='$FOLDER_MIME' and name='$FOLDER_NAME' and trashed=false")
            .setFields("files(id)")
            .execute()
            .files
            ?.firstOrNull()
            ?.id
        if (existing != null) return existing

        val metadata = com.google.api.services.drive.model.File().apply {
            name = FOLDER_NAME
            mimeType = FOLDER_MIME
        }
        return drive.files().create(metadata).setFields("id").execute().id
    }

    private fun findFile(drive: Drive, folderId: String, name: String): String? =
        drive.files().list()
            .setSpaces("drive")
            .setQ("name='$name' and '$folderId' in parents and trashed=false")
            .setFields("files(id)")
            .execute()
            .files
            ?.firstOrNull()
            ?.id

    private fun download(drive: Drive, fileId: String): String {
        val out = ByteArrayOutputStream()
        drive.files().get(fileId).executeMediaAndDownloadTo(out)
        return out.toString(Charsets.UTF_8.name())
    }

    /**
     * Updating an existing file by its id is what keeps this from littering the
     * folder with a fresh copy every time it saves.
     */
    private fun upload(
        drive: Drive,
        folderId: String,
        existingId: String?,
        name: String,
        mime: String,
        content: String,
    ) {
        val body = ByteArrayContent(mime, content.toByteArray(Charsets.UTF_8))
        if (existingId != null) {
            drive.files().update(existingId, null, body).execute()
        } else {
            val metadata = com.google.api.services.drive.model.File().apply {
                this.name = name
                parents = listOf(folderId)
            }
            drive.files().create(metadata, body).setFields("id").execute()
        }
    }
}
