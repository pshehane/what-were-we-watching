# AICore check

A one-screen app that tests whether AICore (Android's on-device model service) answers on a phone. It is separate from What Were We Watching, so a failure here cannot be caused by that app's prompts or code.

It uses only public ML Kit GenAI APIs, at the same versions as the main app.

## What it does

**Run all checks** prints one line per call, with OK or FAIL and the time taken:

- Prompt API: `checkStatus()`, `getBaseModelName()`, `getTokenLimit()`, `generateContent("Say hello.")`, `warmup()`, then `generateContent` again.
- Summarization API: `checkFeatureStatus()`, `getBaseModelName()`, `prepareInferenceEngine()`, `runInference()` on a 600-character paragraph.

A failure prints the exception class, the ML Kit error code and its name, and the message for each cause.

**Download prompt model** starts the model download and prints each status change.

**Copy report** copies the whole output, including the phone model, build, and the AICore version, so it can be pasted into a bug report.

## Build and install

```bash
.\gradlew.bat :aicore-check:assembleDebug
```

```bash
adb install -r tools/aicore-check/build/outputs/apk/debug/aicore-check-debug.apk
```

The same output goes to logcat under the tag `AICoreCheck`.
