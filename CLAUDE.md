# Working on this repository

This repository is **public**. Read this before changing anything.

## Rules

- **No household data in the repo.** Real names, profiles, account emails, device serials and signing fingerprints never go in tracked files. Use generic names (Ana, Ben, Cal, Dee) in code, tests, docs and design files.
- **No secrets in the repo.** Keys are read at build time from `~/.api-keys.json` (groups `tmdb` and `gemini`). A missing key must still produce a working build.
- **Private notes go in `docs/`.** That folder is git-ignored. `docs/handoff.md` has the device list, test results and open decisions.
- **Public APIs only.** Do not add anything that needs a rooted phone, hidden APIs or system permissions.

## Commands

```bash
.\deploy.ps1                          # build, install keeping the library, launch
.\deploy.ps1 -Serial <serial> -Log    # pick a device, stream logcat
.\gradlew.bat :app:testDebugUnitTest  # unit tests
.\gradlew.bat :aicore-check:assembleDebug  # the standalone AICore test app (tools/aicore-check)
```

Gradle needs `JAVA_HOME` set to Android Studio's JBR: `C:/Program Files/Android/Android Studio/jbr`.

## Code conventions

- Compose **foundation only**, no Material. Every screen is drawn by hand to match `design/`.
- No database, DI framework, networking library or image library. `Http.kt` wraps `HttpURLConnection`. `PosterCache.kt` caches posters.
- Rules that matter live as pure functions in `data/` (`Couch`, `Suggest`, `Recap`, `Summary`, `TimeLeft`, `Merge`, `Csv`) and are unit tested without a device.
- Compose cannot see state read inside a lambda. Pass state values into composables, not functions that read state.
- Gesture thresholds are in dp, not pixels.
- Every JSON record carries `updatedAt`. Merge is record by record. Library-level settings come from the newer copy (see `Merge.libraries`).
- Log tags: `Watching.OnDevice`, `Watching.Summary`.

## Verifying changes

- Run the unit tests.
- Install on a phone and use the feature. Several bugs in this project were only found that way.
- Read the on-device library with `adb shell run-as net.shehane.watching cat files/library.json` (debug builds only).
- If you add test data to a real library, remove it through the app afterwards.

## Commits

Write commit messages in plain English: what changed, why, and how it was verified. End with the co-author line used in the existing history.
