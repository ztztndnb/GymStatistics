# GymStatistics — Technical Handoff Document

**Version:** 1.0.6 (`versionCode` = 7)
**Target:** Android, minSdk 26 / targetSdk 35 / compileSdk 36
**Language / UI:** Kotlin 2.0.21 · Jetpack Compose · Material3
**Persistence:** kotlinx.serialization → local JSON (offline-first)
**LAN sync:** embedded NanoHTTPD server (phone = source of truth)

> This document is written for a developer taking over the project in a *new session*.
> It documents the current state, build/environment constraints, architecture, features,
> release process, and the verification recipes actually used.

---

## 1. What the app does

A personal gym/workout tracker that is **offline-first and single-source-of-truth on the phone**,
with a **LAN browser dashboard** served by the phone so a computer on the same WiFi can view and
export the data, and optionally push data back to the phone (import).

Key features:

- Record a daily workout: each save records **one action** (movement) with
  `data (value) + optional unit + count(个数) + sets(组数)`. Repeated saves append more actions to the same day.
- Date selection: a "week bar" (Sun..Sat) + a month calendar; **left/right swipe** switches day.
- Each action is shown as its own card on the main page, with per-item **edit / delete** (delete asks for confirmation).
- **History** page: groups all actions by name, lists them sorted by times-added (desc).
  A drill-down shows that action's records (multi-key sorted), per-unit **trend charts**
  (data trend and total-count trend, one chart per unit), and a **fatigue warning**
  (yellow marker when done within the last 3 days) that can be turned off per action.
- **LAN sync** page: toggle the embedded server, show the `http://<ip>:8931` link with
  **copy-to-clipboard**, and a **"import from computer"** entry (pick a JSON file, import
  all or by date, prompt on duplicates).

---

## 2. Project layout

```
GymStatistics/
├─ settings.gradle.kts            # pluginManagement + repos (google, mavenCentral), :app
├─ build.gradle.kts               # plugin versions (AGP 8.7.3, Kotlin 2.0.21, Compose, serialization)
│                                 # + `tasks.wrapper` (gradle 8.10, validateDistributionUrl=false)
├─ gradle.properties              # jvmargs, useAndroidX, suppressUnsupportedCompileSdk=36
├─ gradle/wrapper/…               # wrapper pinned to gradle-8.10 (cached locally on this machine)
├─ local.properties               # sdk.dir → C:\Users\zhang\AppData\Local\Android\Sdk  (gitignored)
├─ keystore.properties            # release keystore creds  (gitignored)
├─ keystore/gymstatistics-release.jks  # release signing keystore  (gitignored)
├─ README.md                      # user-facing run/release notes
├─ gymstatistics.csv              # (a sample export copied from tests — not needed by the app)
└─ app/
   ├─ build.gradle.kts            # android{} config, signingConfig release, R8 release rules, deps
   ├─ proguard-rules.pro          # kotlinx.serialization + fi.iki.elonen keep rules
   └─ src/main/
      ├─ AndroidManifest.xml      # INTERNET, ACCESS_NETWORK_STATE, usesCleartextTraffic=true, MainActivity
      ├─ assets/dashboard.html    # the PC browser dashboard (served at "/")
      ├─ res/…                    # adaptive launcher icon, strings, theme
      └─ java/com/gymstatistics/
         ├─ MainActivity.kt       # ComponentActivity; reads intent extras, sets Compose content
         ├─ GymViewModel.kt       # AndroidViewModel + UiState; all state mutations; import; demo data
         ├─ data/
         │  ├─ Models.kt          # ExerciseRecord, SessionRecord, WorkoutData, SyncPayload, Import* types
         │  ├─ History.kt         # ActionRecord/ActionSummary, buildActions, comparator, chart series
         │  └─ WorkoutRepository.kt  # AppJson (Json config) + JSON load/save in filesDir
         ├─ server/
         │  └─ LanSyncServer.kt   # NanoHTTPD subclass: dashboard + export + import endpoints
         └─ ui/
            └─ GymApp.kt          # ALL Compose UI: screens, charts, dialogs, date widgets, animations
```

---

## 3. Version matrix

| Component | Version | Notes |
|---|---|---|
| Android Gradle Plugin | **8.7.3** | Must match locally-available Gradle (see §4) |
| Gradle (wrapper) | **8.10** | Distribution only available **locally cached** on this machine |
| Kotlin | 2.0.21 | + `org.jetbrains.kotlin.plugin.compose` 2.0.21, + serialization 2.0.21 |
| Jetpack Compose BOM | 2024.12.01 | material3, ui, icons-core |
| kotlinx.serialization | 1.7.3 | JSON persistence |
| kotlinx.coroutines | 1.9.0 | |
| Activity Compose | 1.9.3 | |
| Lifecycle (viewmodel + runtime-compose) | 2.8.7 | |
| NanoHTTPD | 2.3.1 | **Important:** core classes are in `fi.iki.elonen.*` |
| compileSdk / buildTools | 36 / 36.1.0 | API 36 via suppression (AGP 8.7.3 caps at 35) |
| minSdk / targetSdk | 26 / 35 | |
| Java | 17 (source/target), compiled with JDK 21 | |

---

## 4. Build environment & constraints (READ FIRST)

This project was built on a machine with specific network/registry limits. A new developer
**must** know these before building:

1. **Gradle distribution downloads do NOT work.** `downloads.gradle.org` times out and
   `github.com` is unreachable. Only `google()` (`dl.google.com`) and `mavenCentral()`
   are reachable. Therefore:
   - The wrapper is pinned to **gradle-8.10** and `validateDistributionUrl = false`.
   - On **this** machine, use the installed Gradle directly (it is cached):
     `C:\Program Files\gradle-8.10\bin\gradle.bat` (also on PATH as `gradle`).
   - On a NEW machine with working network, the wrapper may work normally and download 8.10,
     but then **AGP must be bumped** to a version that matches the newer Gradle, OR keep Gradle 8.10.

2. **AGP is pinned to 8.7.3** because it is the highest AGP compatible with Gradle 8.10
   (AGP 8.8+ requires Gradle ≥ 8.10.2). Gradle 8.10 was chosen because it is the only
   usable distribution available offline here.

3. **compileSdk 36 is "unsupported" by AGP 8.7.3**, so `gradle.properties` sets
   `android.suppressUnsupportedCompileSdk=36`. Keep that line. The `android-36` platform
   and `build-tools;36.1.0` are installed on this machine.

4. **NanoHTTPD package is `fi.iki.elonen`**, NOT `org.nanohttpd`. All imports in
   `LanSyncServer.kt` use `fi.iki.elonen.NanoHTTPD`, `fi.iki.elonen.NanoHTTPD.IHTTPSession`,
   `fi.iki.elonen.NanoHTTPD.Response`, `fi.iki.elonen.NanoHTTPD.Response.Status`.

5. **SDK location / tools** (this machine):
   - SDK: `C:\Users\zhang\AppData\Local\Android\Sdk`
   - adb: `...\platform-tools\adb.exe` (not on PATH — call by full path)
   - Emulator AVD: `Medium_Phone_API_36.1` (API 36, x86_64)
   - JDK 21: `C:\Program Files\Java\jdk-21.0.11` (Gradle daemon uses it)
   - `cmdline-tools`/`sdkmanager` are **not** installed; `kotlin` is **not** on PATH (not needed).

6. **Debug vs Release signatures differ.** The debug app sign with the debug keystore; the
   release APK uses `keystore.properties`. To install a release APK over an installed debug
   APK you must first `adb uninstall com.gymstatistics`.

---

## 5. Build & run

```powershell
# From project root (works on THIS machine using cached Gradle 8.10):
gradle assembleDebug          # debug APK: app/build/outputs/apk/debug/app-debug.apk
gradle assembleRelease        # release APK (R8 + signed): app/build/outputs/apk/release/app-release.apk
```

Run on emulator/device:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.gymstatistics/.MainActivity --ez extra_seed_demo true --ez extra_sync true
```

Intent extras supported by `MainActivity`:

| Extra | Type | Effect |
|---|---|---|
| `extra_seed_demo` | boolean | If data is empty, write the demo dataset (30-day / 6-action). |
| `extra_sync` | boolean | Auto-enable the LAN sync server on launch. |

---

## 6. Architecture & data flow

- **Single Activity, single ViewModel** (`MainActivity` → `GymApp` → `GymViewModel`).
- **Offline-first:** the whole dataset is `WorkoutData` kept in `uiState.data` (Compose state)
  and **mirrored to `filesDir/workouts.json`** by `WorkoutRepository.save(...)` (async IO)
  on every mutation. `load()` is called once in `init`.
- **Phone = source of truth.** The LAN server always reads the current in-memory snapshot via a
  `dataProvider: () -> WorkoutData` lambda, so changes are immediately visible on the PC dashboard.
- **Import** writes back into the same `WorkoutData` and persists it.
- All data mutations go through `persist(newData)` in the ViewModel (sets state + saves JSON),
  and set a `message` that surfaces as a Snackbar.

### Data model (`data/Models.kt`)

```kotlin
ExerciseRecord(name, id, data: Double?, unit: String, count: Int?, sets: Int?)
SessionRecord(id, date: String(yyyy-MM-dd), note, exercises: List<ExerciseRecord>)
WorkoutData(version, sessions: List<SessionRecord>, suppressedFatigue: Set<String>)
SyncPayload(exportedAt, sessions)         // what the server returns
ImportMode.ALL | ImportMode.DATE(date)     // import scoping
ImportResult(added, duplicates, skipped, overwritten)
ImportRequest(mode, date?, overwriteDuplicates, sessions)   // POST /api/import body
ImportResponse(added, duplicates, skipped, overwritten)     // POST /api/import response
```

### Display format
Each exercise displays as `60kg · 10 × 3组`, or `60kg · 10个` when `sets <= 1` (see `exerciseLine`,
and the equivalent JS in `dashboard.html`). `总个数 = count × (sets ?: 1)`.

---

## 7. UI (`ui/GymApp.kt`)

All UI is in one file. High-level structure:

### Screen switching (animated)
- `private enum class Screen { MAIN, HISTORY, SYNC }`
- `GymApp` holds `var screen` and renders branches through an `AnimatedContent` with
  horizontal slide-in/out + fade transitions (forward = slide from right, backward = slide from left).
- `BackHandler` is registered inside `HistoryScreen` and `SyncPage` so the system back / edge-swipe
  returns to the previous level (not the app): history detail → list → main; sync → main.

### MAIN screen
- Date header ("今天 ▾" opens `MonthCalendarDialog`), `WeekDayBar` (Sun..Sat, selected day highlighted).
- **Swipe to change date:** the main content `Column` has `pointerInput { detectHorizontalDragGestures }`;
  left swipe → next day, right swipe → previous day (`selectedDate.plusDays(1)/(-1)`).
  The header + week bar + list are wrapped in an `AnimatedContent` keyed by `selectedDate`
  (horizontal slide based on date comparison).
- Per-day list: each exercise renders as `ActionItemCard` (edit + delete with confirm dialog).
- Bottom: two full-width buttons — "记录动作" (opens add dialog) and "局域网同步" (goes to SYNC).

### Add / Edit dialog
- `AddSessionDialog(defaultDate, initial: ExerciseRecord?, initialNote, isEditing, onDismiss, onConfirm)`
  — one action per save; pre-fills when editing or when picked from history; note field at the bottom.

### History screen
- `HistoryScreen(data, initial, onSetFatigueSuppressed, onBack, onPickFill)`.
- List → drill-down detail via an `AnimatedContent` (vertical slide).
- List rows: name + count + **fatigue warning** chip (⚠), computed by `fatigueInfoOf`:
  done within `FATIGUE_WINDOW_DAYS = 3` (i.e. `daysSince <= 3`) and not suppressed.
- Detail: a `疲劳预警` toggle (calls `setFatigueSuppressed`); per-unit trend charts via `TrendChart`.
- Tapping a record calls `onPickFill` → fills the add dialog on MAIN.

### Trend chart
- `TrendChart(title, series: List<ChartSeries>, yAxisLabel)` draws grid lines, y-axis **numeric ticks**
  (nice step 1/2/5×10ⁿ), a **horizontal** unit label, and the x-axis **dates** (`shortDate`).
- **Important:** all labels are drawn with the **pre-measured** overload
  `drawText(textLayoutResult, topLeft)` (from `textMeasurer.measure(...)`). Do **not** use the
  `drawText(textMeasurer, text, topLeft, style)` overload and do **not** wrap the unit label in
  `rotate(-90f)` — both caused `maxHeight(-90)` crashes in a recorded layer. Keep it as-is.

### Sync page
- `SyncPage(viewModel, onBack)`: toggle server (`setSync`), show `syncUrl`, copy-to-clipboard via
  `LocalClipboardManager`, and the **import** entry:
  - `rememberLauncherForActivityResult(OpenDocument())` → reads JSON → `parseImportSessions(json)`.
  - `ImportDialog` lets the user choose **全部导入 / 按日期导入** (date text field) and shows the
    count of dates/records.
  - On import, `importPreview(...)` counts existing dates; if duplicates → `检测到重复` dialog with
    **合并(覆盖)** / **跳过重复**; otherwise import directly. Result shown via ViewModel message Snackbar.

---

## 8. LAN sync server (`server/LanSyncServer.kt`)

Port **8931**, binds `0.0.0.0`. Started/stopped by `GymViewModel.setSync(enabled)`.

| Route | Method | Purpose |
|---|---|---|
| `/`, `/index.html`, `/dashboard` | GET | The PC dashboard (`assets/dashboard.html`). |
| `/api/health` | GET | `{"status":"ok"}` |
| `/api/workouts` | GET | JSON `SyncPayload{exportedAt, sessions}` (used by dashboard). |
| `/api/export.json` | GET | JSON download; `Content-Disposition: attachment; filename="gymstatistics_<stamp>.json"`. |
| `/api/export.csv` | GET | CSV download; **UTF-8 BOM prefix** + `charset=utf-8`; timestamped filename. |
| `/api/import` | POST | Import data back into the phone (see §9). |

Notes:
- `respond(...)` adds `Access-Control-Allow-Origin: *`.
- CSV columns: `date,exercise,data,unit,count,sets`. The BOM (`\uFEFF`) ensures Excel reads Chinese correctly.
- Filename timestamp format: `yyyyMMdd-HHmmss`.

### `dashboard.html`
Self-contained HTML/JS served by the phone. It:
- fetches `api/workouts` every 5 s, renders stats (days/actions/sets/count) and per-day/exercise lines.
- exports via **JS blob download** with a timestamped filename (`downloadExport('api/export.json', 'gymstatistics', 'json')`).

---

## 9. Import feature

Two ways, sharing the same merge logic in `GymViewModel.importSessions(...)`:

1. **Phone UI (file picker)** — shown on the Sync page (`ImportDialog`).
2. **Server endpoint** — `POST /api/import`:
   - Body (`ImportRequest`): `{"mode":"all"|"date","date":"yyyy-MM-dd","overwriteDuplicates":bool,"sessions":[...]}`
   - Response (`ImportResponse`): `{"added":N,"duplicates":N,"skipped":N,"overwritten":N}`
   - The server calls `importHandler(sessions, mode, overwrite)` → `GymViewModel.importSessions`.

Merge semantics (`importSessions`):
- group incoming by date, filter by mode (`ALL` or `DATE(date)`);
- date already exists on phone → count as **duplicate**; `overwriteDuplicates=true` merges the
  incoming exercises into that day's session (`overwritten`), else **skipped**;
- new date → **added**.

`importPreview(incoming, mode)` returns how many incoming dates already exist (drives the prompt).

---

## 10. Release process

- `app/build.gradle.kts`: `versionCode = 7`, `versionName = "1.0.6"`.
- `release` build type: `isMinifyEnabled = true`, `isShrinkResources = true`,
  `proguardFiles(...)` = `proguard-rules.pro`. R8 shrinks the release APK to ~1.2 MB.
- **Signing:** reads `keystore.properties` (gitignored) which points to
  `keystore/gymstatistics-release.jks` (alias `gymstatistics`). `storeFile` is resolved with
  `rootProject.file(...)` (so it must be relative to the project root, not the app module).
- `proguard-rules.pro` keeps `kotlinx.serialization` serializers and `fi.iki.elonen.**`,
  and `com.gymstatistics.data.**`.

> ⚠️ The included keystore/password are placeholders used for a **local** release. For a real store
> release, generate your own strong keystore, keep it secret and backed up. Losing it prevents
> updating already-installed builds with the same signature.

Build:

```powershell
gradle assembleRelease
# output: app\build\outputs\apk\release\app-release.apk (signed)
```

Verify:

```powershell
# version / package
aapt dump badging app\build\outputs\apk\release\app-release.apk
# signature
apksigner verify app\build\outputs\apk\release\app-release.apk
```

---

## 11. Testing & verification recipes used in this project

The previous model session was **text-only** (could not see screenshots), so verification was
done with `adb`/`uiautomator`/`curl` rather than screenshots. Keep these recipes handy.

```powershell
# Boot the emulator
$em = "C:\Users\zhang\AppData\Local\Android\Sdk\emulator\emulator.exe"
& $em -avd Medium_Phone_API_36.1 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect

# Wait for boot
& $adb shell getprop sys.boot_completed  # poll until "1"

# Install + launch (demo data + sync)
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell pm clear com.gymstatistics
& $adb shell am start -n com.gymstatistics/.MainActivity --ez extra_seed_demo true --ez extra_sync true

# Reach the LAN server from the host
& $adb forward tcp:8931 tcp:8931
curl.exe -s http://127.0.0.1:8931/api/health
curl.exe -s http://127.0.0.1:8931/api/workouts

# Test import (POST body without a BOM; PowerShell adds a BOM with Set-Content) 
# → write JSON with UTF8-no-BOM via [System.IO.File]::WriteAllText && --data-binary "@file"
curl.exe -s -X POST -H "Content-Type: application/json" --data-binary "@file.json" http://127.0.0.1:8931/api/import

# Read the current screen's text (Compose semantics) to assert on-screen state
& $adb shell uiautomator dump /sdcard/ui.xml
& $adb pull /sdcard/ui.xml ...
$utf8 = [System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes(...))

# Crash / process checks
& $adb shell pidof com.gymstatistics
& $adb logcat -d | Select-String "FATAL EXCEPTION"
```

Notes when importing via curl on PowerShell:
- Pass the JSON body via a **UTF-8 without BOM** file and `--data-binary "@file"`;
  `Set-Content -Encoding UTF8` adds a BOM which breaks kotlinx.serialization parsing
  (`Expected start of the object '{', but had '�'`).

---

## 12. Known issues / caveats

1. **Animations not visually verified** (text-only model). They use standard
   `AnimatedContent` + `slideIn/Out + fadeIn/Out`. If timing/distance feels off on a real device,
   adjust `tween`/offset in the `transitionSpec`s.
2. **Chart label drawing is fragile.** Keep the pre-measured `drawText(TextLayoutResult, topLeft)`
   pattern; avoid `rotate()` for the axis label and the `textMeasurer`-overload of `drawText`.
3. **Fatigue window** is hard-coded: `daysSince <= FATIGUE_WINDOW_DAYS (3)`.
4. **Import file-picker flow** is implemented but not end-to-end automated (no UI automation for the
   system file chooser); the shared merge logic is verified via `POST /api/import`.
5. **`exportedAt` timezone** uses `XXX` offset (device-local). If you need a specific timezone,
   adjust `nowIso()`.
6. `addSession`/`deleteSession` exist but the UI now uses the per-exercise
   `addExercise`/`updateExercise`/`deleteExercise`. They are kept (harmless) but not called by the UI.
7. The demo seed (`extra_seed_demo`) only runs when data is empty (and the file is cleared via `pm clear`).

---

## 13. Suggested next steps

- Bump AGP/Gradle when a network that can download Gradle is available (needs AGP ≥ 8.9 for clean
  compileSdk 36 without suppression).
- Recommended: add a proper **release keystore** and move signing out of the repo.
- Add an **"import to phone"** button on the PC dashboard that POSTs a local file to `/api/import`.
- Optionally add in-app version display / "About" and an "import JSON by pasting" option.
- Consider enabling `versionCode` increments and a `bundle` (AAB) build for store distribution.
