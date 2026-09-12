# What Were We Watching

An Android app for one household to answer a single question fast: **who is on the couch, and what are we in the middle of?**

Tap the people who are actually sitting down. The list re-sorts instantly into shows everyone seated is part-way through, then shows only some of them are on, with the missing person named. Every card carries the two things that get you watching: which service, and which profile to pick once you are there.

> **Status: version 1.0 is complete and builds clean. It has not yet run on a phone.**
> The pure logic is unit tested. Everything that only exists on a device — posters, the live TMDB calls, the swipe gesture — is unverified.

---

## What it does

- **Seat the couch.** Tap faces on and off. A show is a full match only when *every* seated person is on it; if two of the four are missing, it drops to "some of you" rather than being offered, because those two would fall behind.
- **Add in one tap.** Search is the whole flow. Who is watching comes from the couch bar, the service from where TMDB says it streams, the profile from the default you marked, and the episode from the beginning.
- **Track by episode or by season.** A `+1` on the card, or a "Finished S2" button when you have been watching without opening the app.
- **Two ways to demote.** Swipe left: the first zone marks a show *not in the mood* — half height, no artwork, and it comes back on its own after two weeks. Dragging further *shelves* it — the smallest row on the screen, still searchable, and it stays down until you undo it.
- **Your data stays yours.** Everything lives in a plain `library.json`, with a `library.csv` written beside it that opens in Sheets or Excel.

## Requirements

- Android 14 (API 34) or newer
- Android Studio with the Android SDK
- JDK 17 (Android Studio's bundled JBR is fine)
- A free [TMDB](https://www.themoviedb.org/settings/api) API key

## Setup

### 1. API credentials

Secrets are **not** kept in this repository. Create `~/.api-keys.json` in your home directory:

```json
{
  "tmdb": {
    "apiKey": "your-tmdb-api-key",
    "readToken": "your-tmdb-v4-read-token"
  }
}
```

The build reads it by group and key. A missing file or key yields an empty string rather than a build failure — the app then runs and tells you which credential it is missing. `local.properties` holds nothing but `sdk.dir`.

### 2. Who is in your household (optional)

No names are in this repository. On first run the app seeds one person called
**Me** plus the common streaming services with no profiles, and you add the rest
from the *Services and profiles* screen.

To have a build open with your household already set up, copy
[`starter-file.example.json`](starter-file.example.json) to
`~/.watching-starter.json` and put your own names in it. The build embeds
whatever it finds; the file itself never enters the repository.

```json
{
  "me": { "name": "Me" },
  "people": [ { "name": "Ana" }, { "name": "Ben" } ],
  "services": [
    { "name": "Netflix", "profiles": ["Grown-ups", "Kids"], "default": "Grown-ups" }
  ]
}
```

It seeds a **first run only**. Once the app has written its own `library.json`,
editing the starter file does nothing — change things inside the app. A guided
first-run setup, for someone who would rather not edit JSON at all, is a 3.0 job.

### 3. Google Drive sync (optional)

The app works fully offline without this. Connecting Drive puts `library.json` and `library.csv` in a folder called *What Were We Watching* on your own Drive, so several phones stay in step.

There is **no client id or secret to configure**. Google matches the OAuth client by package name plus signing certificate at runtime. In the [Google Cloud console](https://console.cloud.google.com/):

1. Create a project (or reuse one) and enable the **Google Drive API**.
2. Create an **OAuth client → Android** for `net.shehane.watching`. Debug and release share this package name, so one client covers both.
3. Give both the SHA-1 of your signing certificate:
   ```
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
   ```
4. Add yourself as a test user on the consent screen.

The scope is `drive.file`, which lets the app see only the files it created. It cannot read anything else in your Drive.

### 4. Build and run

```bash
.\deploy.ps1
```

Builds the debug APK, installs it keeping your library, and launches it. It checks for a ready device first and explains what is wrong if there isn't one. Add `-Log` to stream logcat, or `-Serial <serial>` when more than one device is attached.

Plain Gradle works too:

```bash
.\gradlew.bat :app:assembleDebug
```

### 5. Tests

```bash
.\gradlew.bat :app:testDebugUnitTest
```

14 tests over the couch match rule, the two-phone merge, and the CSV escaping. All three are pure functions, so none of them needs a device.

## How your data is stored

One JSON file is the record. Every entry carries its own `updatedAt`, which is what lets two phones merge **record by record** instead of one overwriting the other. Edit different shows on two phones while both are offline and both edits survive; only a collision on the same show loses anything, and it loses that one show's edit rather than the whole day.

```json
{
  "schemaVersion": 1,
  "people":   [ { "id": "ana", "name": "Ana", "color": "#6FC0DE" } ],
  "services": [ { "id": "max", "name": "Max", "defaultProfileId": "max-family",
                  "profiles": [ { "id": "max-family", "name": "Family" } ] } ],
  "shows":    [ { "title": "Severance", "serviceId": "appletv",
                  "watchedWith": ["ana", "ben"],
                  "position": { "season": 2, "episode": 4 },
                  "state": "active" } ]
}
```

The CSV is rewritten from the JSON on every change, one row per show, with plain `YYYY-MM-DD` dates. It is never read back, so editing it changes nothing.

## Project layout

```
deploy.ps1                    build, install, launch
app/src/main/java/net/shehane/watching/
  MainActivity.kt             routing, bottom bar, toasts
  MainViewModel.kt            state, search debounce, sync triggers
  model/Library.kt            the file format, as Kotlin
  data/LibraryStore.kt        in-memory library, library.json, library.csv
  data/Merge.kt               the record-by-record merge
  data/Couch.kt               the match rule
  data/Csv.kt                 the readable copy
  data/Tmdb.kt                search, detail, providers, Wikipedia
  data/DriveSync.kt           Google Sign-In and the Drive round trip
  ui/                         six screens, drawn to match the mockups
design/                       the .dc.html artboards behind the mockups
starter-file.example.json     copy to ~/.watching-starter.json to seed your own
```

No database, no networking library, no image library, no dependency-injection
framework. The library is about a hundred shows, which loads into memory at
launch and filters there, and every dependency avoided is a version that cannot
fall out of step with the others.

## Roadmap

| Version | |
|---|---|
| **1.0** | Track what you are in the middle of, add fast, demote what you are not in the mood for. United States only. |
| **2.0** | A wishlist, and a **Tourist TV** screen: set a country while travelling and see which wishlist items are watchable there. |
| **3.0** | Suggestions, built on the history the CSV has been accumulating since 1.0. Also a guided first-run setup, so a new user configures the app on the phone instead of writing a starter file. |
| *unscheduled* | "Leaving soon" warnings. No free data source exists yet; the space is already reserved on the show screen. |

## Privacy

Nothing personal is in this repository, and that is enforced rather than assumed.
Household names come from a file in your own home directory. API credentials come
from `~/.api-keys.json`. The app talks to exactly two places: TMDB, to look up a
show when you add it, and your own Google Drive, if you connect it. There is no
server, no account, and no telemetry.

## Attribution

This product uses the TMDB API but is not endorsed or certified by TMDB.

Titles, artwork, episode counts and streaming availability come from
[The Movie Database](https://www.themoviedb.org/). Streaming availability is
supplied to TMDB by JustWatch. Wikipedia links are resolved through Wikidata.

The TMDB logo shown on the app's About screen is their own artwork, used for
attribution only. See [`licenses/TMDB-logo.md`](licenses/TMDB-logo.md).

Typefaces: [Instrument Serif](https://fonts.google.com/specimen/Instrument+Serif)
and [Space Grotesk](https://fonts.google.com/specimen/Space+Grotesk), both under
the SIL Open Font Licence 1.1. Licence texts are in [`/licenses`](licenses).

## Licence

[MIT](LICENSE) for the code. The bundled fonts are licensed separately, as above.
