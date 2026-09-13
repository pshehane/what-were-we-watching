# What Were We Watching

An Android app for one household. It answers one question quickly: **who is on the couch, and what are we in the middle of?**

Tap the people who are sitting down. The list re-sorts into shows everyone seated is part-way through, then shows only some of them are on. Every card says which streaming service to open and which profile to pick.

> **Status:** versions 1.0 and 2.0 are complete. Suggestions (3.0) are built; the guided first-run setup is not. The app runs on several Android phones, and 141 unit tests pass.

---

## What it does

### The couch

- **Seat the couch.** Tap faces on and off. A show is a full match only when the people on the show and the people seated are the same set. If someone on the show is not seated, it drops to "some of you", because that person would fall behind.
- **How long have we got?** Set a length of time (30 minutes to 3 hours) or a bedtime. Each card then says how many episodes fit and when they would end. The last episode may run up to 10 minutes past the limit and still count.
- **Track by episode.** Tap `+1` on a card.
- **Two ways to demote.** Swipe left a short way to mark a show *not in the mood*. It shrinks and comes back by itself after two weeks. Swipe further to *shelve* it until you undo it.

### Adding a show

- **Search is the whole add flow.** Results are filtered to your home country by default. Shows not streaming there are collapsed under one line, and each one lists the countries where it does stream.
- **Prefilled.** Who is watching comes from the couch, the service from TMDB, the profile from the default you set, and the position from the start.

### The show screen

- **Position.** Step one episode at a time, or tap the position to pick a season. "S8 · start" means you have finished everything before season 8.
- **Next up.** Tap it to see that episode's title, air date and synopsis.
- **Catch me up.** The `?` button shows a recap of everything before your position and nothing after it. See [Recaps](#recaps).
- **Episode length.** Shown next to the season count. Tap it to correct it.
- **What we thought.** Once a show is finished, mark it *loved it* or *didn't love it*. Suggestions use this.
- **Share.** Pick one of three fixed messages. The system share sheet opens with the show's name, service, poster and Wikipedia link.

### Wishlist and Tourist TV

- **The wishlist** holds shows you mean to watch. They never appear on the couch list.
- **Tourist TV.** Set a country you are visiting. The wishlist then shows where each show can be watched there, plus what is popular locally. Streaming rights are sold per country.

### Ideas

The Ideas tab suggests what to start. Its sections are ordered by how much guessing each one does:

- **Ready tonight.** Wishlist shows you can watch now, on a service you pay for or one TMDB says carries it in your country.
- **Stalled.** Shows you started and have not touched for 21 days or more.
- **Because you finished X.** TMDB recommendations based on a show you finished. Swipe right for *loved it* or left for *didn't love it*. Either one files the show as watched and removes it. An Undo bar stays for five seconds.
- **Catching it up.** A grid of popular shows on your services. Mark the ones you have already seen. Recommendations start once five shows are marked finished.

### Your data

Everything is in a plain `library.json`. A `library.csv` copy is written beside it for opening in Sheets or Excel.

## Requirements

- Android 14 (API 34) or newer
- Android Studio with the Android SDK
- JDK 17 (Android Studio's bundled JBR works)
- A free [TMDB](https://www.themoviedb.org/settings/api) API key
- Optional: a [Gemini API](https://aistudio.google.com/) key, for recaps written by a cloud model

## Setup

### 1. API credentials

Secrets are **not** kept in this repository. Create `~/.api-keys.json` in your home directory:

```json
{
  "tmdb": {
    "apiKey": "your-tmdb-api-key",
    "readToken": "your-tmdb-v4-read-token"
  },
  "gemini": {
    "apiKey": "your-gemini-api-key"
  }
}
```

The build reads each value by group and key. A missing file or key becomes an empty string, and the build still succeeds. Without a TMDB key, the app tells you the key is missing. Without a Gemini key, the cloud recap option is shown greyed out. `local.properties` holds only `sdk.dir`.

### 2. Who is in your household (optional)

No names are in this repository. On first run the app creates one person called **Me** plus the common streaming services, and you add the rest from the *Services and profiles* screen.

To have a build open with your household already set up, copy [`starter-file.example.json`](starter-file.example.json) to `~/.watching-starter.json` and put your own names in it. The build embeds whatever it finds there. The file itself never enters the repository.

```json
{
  "me": { "name": "Me" },
  "people": [ { "name": "Ana" }, { "name": "Ben" } ],
  "services": [
    { "name": "Netflix", "profiles": ["Grown-ups", "Kids"], "default": "Grown-ups" }
  ]
}
```

It is used on **first run only**. After the app has written its own `library.json`, change things inside the app.

### 3. Google Drive sync (optional)

The app works fully offline without this. Connecting Drive puts `library.json` and `library.csv` in a folder called *What Were We Watching* on your own Drive, so several phones stay in step.

There is **no client id or secret to configure**. Google matches the OAuth client by package name and signing certificate. In the [Google Cloud console](https://console.cloud.google.com/):

1. Create a project (or reuse one) and enable the **Google Drive API**.
2. Create an **OAuth client → Android** for `net.shehane.watching`. Debug and release builds use the same package name, so one client covers both.
3. Give it the SHA-1 of your signing certificate:
   ```
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
   ```
4. Add yourself as a test user on the consent screen.

The scope is `drive.file`, so the app can only see files it created.

### 4. Build and run

```bash
.\deploy.ps1
```

This builds the debug APK, installs it without clearing your library, and launches it. It checks for a ready device first. Add `-Log` to stream logcat, or `-Serial <serial>` when more than one device is attached.

Plain Gradle also works:

```bash
.\gradlew.bat :app:assembleDebug
```

### 5. Tests

```bash
.\gradlew.bat :app:testDebugUnitTest
```

141 tests in 11 files. They cover the couch match rule, the two-phone merge, the CSV, starter-file seeding, suggestions, the recap cut-off, recap fallback order, saved recaps, the share message, positions, the time budget, filling in missing details, and reading Gemini responses. All of it is pure logic, so no device is needed.

## Recaps

*Services and profiles → Catch me up* sets who writes the recap. There are three choices. If the chosen one is not available, the next one down is used, and the recap says why.

1. **A cloud model.** Gemini 2.5 Flash. Needs a Gemini key in the build and a network connection. It is asked for one bullet on the story so far, then one bullet per main character. Main characters come from TMDB's cast list.
2. **This phone.** Android AICore, through ML Kit's GenAI Prompt API, with the Summarization API as a second try. Free, offline, and nothing leaves the phone. If the model still needs downloading, the download runs in the background and the synopses are shown in the meantime.
3. **No summary.** The TMDB synopses of the episodes you have watched.

Every choice only ever sees text from episodes before your position. The recap cannot include anything from the next episode onward.

**Recaps are saved.** A written recap is stored in the library, one per show, and syncs through Drive with everything else. Asking again for the same stopping point, on the same phone or another one, shows the saved recap and sends nothing. A cloud recap is reused whichever writer is chosen. A recap written on the phone is only reused while *This phone* is chosen. *Write it again* under a saved recap asks the model again and replaces it.

**On-device support varies by phone and by AICore version.** In testing on three phones in September 2026, none produced an on-device recap. One phone was not a supported device. One did not offer the feature to third-party apps. One reported the feature as available and then failed when generating. The app handles each of these and shows the synopses instead.

To check a phone without this app, build `tools/aicore-check`. It is a one-screen app that makes the smallest possible ML Kit GenAI calls and copies a report with the error codes. See [its README](tools/aicore-check/README.md).

## How your data is stored

One JSON file is the record. Every entry has its own `updatedAt`, so two phones merge **record by record** instead of one copy overwriting the other. If you edit different shows on two phones while both are offline, both edits are kept.

```json
{
  "schemaVersion": 1,
  "people":   [ { "id": "ana", "name": "Ana", "color": "#6FC0DE" } ],
  "services": [ { "id": "max", "name": "Max", "defaultProfileId": "max-family",
                  "profiles": [ { "id": "max-family", "name": "Family" } ] } ],
  "homeCountry": "US",
  "summaryMode": "cloud",
  "shows":    [ { "title": "Severance", "serviceId": "appletv",
                  "watchedWith": ["ana", "ben"],
                  "position": { "season": 2, "episode": 4 },
                  "seasonCount": 2, "episodeCount": 19, "runtimeMinutes": 52,
                  "state": "finished", "liked": true } ],
  "recaps":   [ { "showId": "…", "upTo": "S2 E5", "source": "cloud",
                  "text": "- The story so far…" } ]
}
```

- `position` is the last episode watched. Episode `0` means none of that season yet.
- `liked` is `true`, `false`, or absent. Absent means nobody has said.
- `recaps` holds at most one saved recap per show. `upTo` is the episode it stops before. The newer copy wins in a merge, and a recap is removed with its show.
- `homeCountry` and `summaryMode` are taken from whichever copy was changed most recently.

The CSV is rewritten from the JSON on every change, one row per show, with plain `YYYY-MM-DD` dates. It is never read back.

**When it syncs with Drive:** when the app starts or comes back to the foreground, 4 seconds after any change, and when you tap *Sync now*.

## Project layout

```
deploy.ps1                    build, install, launch
app/src/main/java/net/shehane/watching/
  MainActivity.kt             routing, bottom bar, toasts, back handling
  MainViewModel.kt            state, search, sync triggers, suggestions, recaps
  model/Library.kt            the file format, as Kotlin
  data/LibraryStore.kt        in-memory library, library.json, library.csv
  data/Merge.kt               the record-by-record merge
  data/Couch.kt               the couch match rule
  data/Suggest.kt             ready tonight, stalled, and the recommendation seed
  data/Recap.kt               next episode and the catch-up cut-off
  data/Summary.kt             recap prompt and fallback order
  data/Gemini.kt              the cloud model
  data/OnDevice.kt            AICore through ML Kit
  data/TimeLeft.kt            the time budget arithmetic
  data/Share.kt               the share sheet message
  data/Csv.kt                 the readable copy
  data/Tmdb.kt                search, detail, seasons, cast, providers, Wikipedia
  data/DriveSync.kt           Google Sign-In and the Drive round trip
  data/Starter.kt             first-run household seeding
  ui/                         the screens, drawn to match the mockups
tools/aicore-check/            standalone app that tests AICore on a phone
design/                       the .dc.html artboards behind the mockups
starter-file.example.json     copy to ~/.watching-starter.json to seed your own
```

There is no database, no networking library, no image library, and no dependency-injection framework. The library is about a hundred shows, which loads into memory at launch.

## Roadmap

| Version | |
|---|---|
| **1.0** | ✅ Track what you are in the middle of, add quickly, demote what you are not in the mood for. |
| **2.0** | ✅ A wishlist, and **Tourist TV**. |
| **3.0** | ✅ Suggestions, votes, recaps, sharing, and the time budget. Not started: a guided first-run setup, so a new user can configure the app on the phone instead of writing a starter file. |
| *unscheduled* | "Leaving soon" warnings. No free data source exists. Space is already reserved on the show screen. |

## Privacy

Nothing personal is in this repository. Household names come from a file in your own home directory. API keys come from `~/.api-keys.json`.

The app connects to:

- **TMDB**, to look up shows.
- **Your own Google Drive**, if you connect it.
- **Gemini**, only if the build has a Gemini key and you choose the cloud recap. It sends the show title, main character names, and synopses of episodes you have already watched. It sends nothing about your household.

On-device recaps are written on the phone. Saved recaps, from either writer, are stored in the library file, so they are in your Drive copy if you connect Drive. There is no server, no account, and no telemetry.

## Attribution

This product uses the TMDB API but is not endorsed or certified by TMDB.

Titles, artwork, episode data, cast and streaming availability come from [The Movie Database](https://www.themoviedb.org/). Streaming availability is supplied to TMDB by JustWatch. Wikipedia links are resolved through Wikidata.

The TMDB logo shown on the app's About screen is their own artwork, used for attribution only. See [`licenses/TMDB-logo.md`](licenses/TMDB-logo.md).

Typefaces: [Instrument Serif](https://fonts.google.com/specimen/Instrument+Serif) and [Space Grotesk](https://fonts.google.com/specimen/Space+Grotesk), both under the SIL Open Font Licence 1.1. Licence texts are in [`/licenses`](licenses).

## Licence

[MIT](LICENSE) for the code. The bundled fonts are licensed separately, as above.
