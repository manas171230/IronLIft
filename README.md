# IronLog — Android app project

This is a Android Studio project. It embeds the IronLog web app as an offline 
local file and adds a native piece the browser version can't do: system
notifications that fire on schedule even when the app is fully closed, via `AlarmManager` + a `BroadcastReceiver`.

## What you need
- [Android Studio](https://developer.android.com/studio) (free), which
  bundles the JDK and Android SDK — you don't need to install those
  separately.

## Pushing to GitHub
1. Unzip this project anywhere.
2. `cd IronLogApp`, then `git init`, `git add .`, `git commit -m "Initial commit"`.
3. Check `git status` (or `git show --stat HEAD`) before pushing — confirm
   `local.properties` is **not** in the commit. It shouldn't be, since
   `.gitignore` already excludes it, but it's worth a glance the first time.
4. Create a repo on GitHub, then `git remote add origin <your-repo-url>` and
   `git push -u origin main`.
5. Anyone else who clones the repo (including future-you on another machine)
   copies `local.properties.example` to `local.properties` and fills in
   their own key — see the Gemini section below.

## Build steps
1. Open Android Studio → **Open** → select the `IronLogApp` folder (freshly
   unzipped, or freshly cloned from your new repo).
2. If Android Studio asks to create a Gradle wrapper or update the Android
   Gradle Plugin, accept — first sync can take a few minutes while it
   downloads dependencies.
3. Plug in your phone (with USB debugging on) or start an emulator, then
   press **Run ▶**. That installs a debug build straight to the device —
   good enough to use and test immediately.
4. For a real, shareable, signed APK: **Build → Generate Signed App Bundle /
   APK → APK**, create a new keystore the first time (save it somewhere
   safe — you'll need the same one for any future update), then build the
   **release** variant. The APK lands in `app/release/`.
5. To install the APK on a phone: copy the `.apk` file to the phone and open
   it (you'll need to allow "install unknown apps" for whichever app you
   used to open it — Files, Chrome, etc.).

## What's inside
- `app/src/main/assets/www/index.html` — the full IronLog app (same one
  running as the web artifact), loaded offline from local storage.
- `MainActivity.kt` — hosts it in a `WebView`, requests the notification and
  exact-alarm permissions on first launch.
- `ReminderScheduler.kt` / `ReminderReceiver.kt` / `BootReceiver.kt` — the
  native alarm/notification pipeline. Whenever you add, edit, or toggle a
  reminder in the app, the page hands the reminder list to Android through
  a small JS bridge (`window.AndroidBridge`), which schedules a real system
  alarm per reminder. Each alarm shows a notification and re-arms itself for
  the same time tomorrow, and `BootReceiver` re-arms everything after a
  restart.
- Your logged lifts, meals, water and splits are stored on-device only
  (`localStorage` inside the WebView) — nothing leaves the phone.

## Photo → macro estimate (Gemini API)

Tap "📸 Estimate from photo" in the meal sheet, and the app sends the photo to
the **Gemini API** (Google's free tier — `gemini-2.5-flash`), asking for a
rough name/calories/protein/carbs/fat estimate, then fills the form so you
can review and adjust before saving. This runs through `GeminiClient.kt`,
called from a JS bridge method (`estimateMealPhoto`) on `MainActivity.kt`.

- **The key lives in `local.properties`** (already filled in, gitignored),
  read into the app at build time via `BuildConfig.GEMINI_API_KEY`. It never
  appears in any committed file.
- **Note this only protects the key from git/GitHub, not from someone who
  has the built APK itself** — the key still ends up compiled into
  `BuildConfig`, and anyone can pull strings out of an APK. Fine for a repo
  you control where only you install the app; if you ever hand the APK file
  itself to other people, use your own separate key for that build (or move
  the call behind a small server you control instead of calling Gemini
  directly from the app).
- **If the model name changes or gets retired**, update the `MODEL` constant
  in `GeminiClient.kt` — check current model names at
  https://ai.google.dev/gemini-api/docs/models.
- **A light extra step worth doing in Google Cloud Console**: restrict the
  key to only the Generative Language API, and optionally to your app's
  package name / SHA-1 fingerprint, so it's less useful to anyone even if it
  does leak.

## If you want to change the look, name, or package id
- App name: `app/src/main/res/values/strings.xml`
- Package id (`com.ironlog.app`): update `namespace` and `applicationId` in
  `app/build.gradle.kts`, and the `package` line at the top of each `.kt`
  file, if you want to publish under your own identifier.
- App content: edit `app/src/main/assets/www/index.html` directly, or swap
  in an updated export of the web app.

## Known limitations of this starter
- Android 13+ requires the user to grant the notification permission, and
  Android 12+ requires "Alarms & reminders" access for exact-time alarms —
  the app prompts for both on first launch, but some phone makers hide or
  rename that settings screen, so double-check it's granted if reminders
  don't fire on time.
- Aggressive battery-optimization on some OEM skins (Xiaomi, Oppo, etc.)
  can kill scheduled alarms. If reminders go quiet, check the phone's
  battery-optimization exemption list for IronLog.
- No app icon artwork beyond a simple generated barbell mark — drop your
  own into `res/mipmap-anydpi-v26/ic_launcher.xml` / `ic_launcher_foreground.xml`
  if you want something custom.
