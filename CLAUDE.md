# Barakah — Project Guide

Android app (Kotlin, Jetpack Compose, Material 3) that builds a **daily schedule around the five prayers** for a Muslim indie hacker. Prayer times are computed offline, the day is laid out as timed blocks (worship, deep work, marketing, family, sleep…), and reminders with ayah/hadith are scheduled as notifications. Everything is local and offline; there is no backend.

- Package / appId: `com.example.barakah` · single module `:app`
- minSdk 24 · targetSdk/compileSdk 37 · Java 11 · AGP 9.4.1 · Kotlin 2.2.10 · Compose BOM 2026.02.01
- Dependencies: Compose (material3, ui, tooling), activity-compose, core-ktx, lifecycle-runtime-ktx. No Room, Hilt, Retrofit or navigation library.
- Default user context: Kebili, Tunisia (`Africa/Tunis`). Other cities are in `CITIES` (`model/Models.kt`).

## Build & run

```bash
./gradlew assembleDebug        # build debug APK
./gradlew installDebug         # install on a connected device/emulator
./gradlew test                 # JVM unit tests (only the template ExampleUnitTest exists)
./gradlew connectedAndroidTest # instrumented tests (template only)
```

Not a git repository yet. `local.properties` is machine-specific (SDK path).

## Source map (`app/src/main/java/com/example/barakah`)

| Path | Role |
|---|---|
| `MainActivity.kt` | Entry point. Creates notification channel, asks `POST_NOTIFICATIONS` (API 33+), calls `AlarmScheduler.reschedule` on every `onResume`. |
| `model/Models.kt` | All data types: `Category`, `Anchor`, `Kind`, `DhuhrMode`, `BlockTemplate`, `Block`, `City`/`CITIES`, `Settings`, `DailyLog`, `Product`/`ProductStatus`. |
| `prayer/PrayerCalculator.kt` | Offline astronomical prayer times (PrayTimes.org method). `Prayer` enum, `PrayerConfig`. Per-prayer minute adjustments match the official Tunisian calendar. |
| `schedule/ScheduleEngine.kt` | Core logic: template + date → list of concrete `Block`s. |
| `schedule/DefaultTemplate.kt` | The default week (ordered list of `BlockTemplate`). |
| `data/Store.kt` | Persistence: SharedPreferences `"barakah"` holding JSON strings. |
| `coach/Coach.kt` | Daily tips (rotating by epoch day), per-block notification messages, milestones, indie warnings, day notes. |
| `coach/Reminders.kt` | Ayah/hadith pool grouped by `Act`; `forBlock`/`pick` choose one deterministically. |
| `notify/Alarms.kt` | `AlarmScheduler`, `Notifier`, `AlarmReceiver`, `BootReceiver`. |
| `ui/AppViewModel.kt` | Single `AndroidViewModel` holding all state (Compose `mutableStateOf`), caches blocks per date, scores, streaks, X-growth weekly stats. |
| `ui/BarakahApp.kt` | Scaffold + bottom nav; tabs `TODAY, TRACK, INDIE, STATS, SETTINGS` (plain `when`, no NavHost). |
| `ui/*Screen.kt`, `XTrackCard.kt`, `Components.kt`, `ui/theme/` | Screens, shared widgets (`SectionCard`, `StatTile`, `Stepper`, time formatters), theme. |

## How the schedule works (the important part)

`BlockTemplate(id, title, category, anchor, offsetMin, durationMin, kind, days, enabled)` is a rule, not a time. `ScheduleEngine.build(date)` turns the ordered template list into `Block`s for that date:

1. **Filter** templates by `enabled` and `days` (1=Mon … 7=Sun; empty = every day).
2. **Start time** comes from `anchor` + `offsetMin`: a prayer (Fajr/Sunrise/Dhuhr/Asr/Maghrib/Isha), `BEDTIME`, `DHUHR_IQAMA`, `JUMUAH`, `LUNCH` (auto-placed), or `PREVIOUS` (starts when the latest block so far ends — **template order matters**).
3. **End time** comes from `kind`:
   - `NORMAL` fixed duration · `PRAYER` never moved (iqama/khutba-aware) · `FILL` until the earliest later anchored block (dropped if < 15 min) · `FRIENDS` capped 3h winter / 2h summer and never past bedtime − 30 min review · `RECOVERY_SLEEP` only if ≥ 45 min of sleep is missing (max 240 min) · `SLEEP` until next morning's wake · `TAHAJJUD` only if the night allows ≥ 5h30 real sleep.
4. `resolveOverlaps` shortens anything colliding with a prayer; `withFreeTime` inserts "Free time / buffer" blocks for gaps ≥ 15 min (ids prefixed `free-`, not checkable).

Key derived rules:
- **Summer** = Isha at/after 20:00 (`summerIshaThresholdMin`). Affects friends cap.
- **Bedtime** = base 22:30, pushed later if Isha + 15 + friends cap + 30 min review needs it.
- **Night** (ends on morning of date): bedtime(prev day) → Fajr−10 min, or Tahajjud wake if enough sleep. Real sleep = in-bed minus 20 min to fall asleep; target 390 min.
- **Dhuhr iqama**: `BEFORE_ASR` (Asr − 30 min, Kebili: stay at mosque until Asr, not on Friday) or `FIXED` (13:00); never earlier than adhan + 5 min. Prayer blocks start `mosqueArriveMin` (10) / `jumuahArriveMin` (20) before iqama/khutba.
- Block ids are used as keys for completions (`date → Set<templateId>`), and some ids are special-cased: `deep1`, `breakfast`, `asr`, `isha`, `review`, `weekly_review`, `dhuhr`, `jumuah`. **Renaming these ids in `DefaultTemplate` breaks notifications/coaching** (`Alarms.kt`, `Coach.kt`, `Reminders.actFor`).

## Notifications

- `AlarmScheduler.reschedule` cancels the previous alarms (count stored in `Store.scheduledAlarmCount`), then plans today + tomorrow: block-start alerts, a prayer heads-up (`prayerHeadsUpMin`, default 10), a daily tip 5 min after breakfast, and "smart" checks evaluated at fire time (`CHECK_FOCUS`, `CHECK_DAY`, `CHECK_BUILD`, `CHECK_BRAND` → `evaluateCheck` reads what you logged and returns `null` when there's nothing to say).
- Max 120 alarms; a refresh alarm fires at 00:05 each night. `BootReceiver` re-plans on boot, time/timezone change, and package replace.
- Uses `setExactAndAllowWhileIdle` when `canScheduleExact`, else inexact fallback.
- Faith reminders (ayah/hadith appended) toggled by `Settings.faithReminders`.

## Persistence

`Store` keys: `settings`, `templates`, `done`, `logs`, `products`, `metric_<name>` (`mrr`, `followers`), `alarm_count`. All JSON via `org.json` with `opt*` defaults, so adding a field to a model means: add default in the data class, read with a default in `load*`, write in `save*`. Corrupt/missing data falls back to defaults (`runCatching`). Templates are stored whole once edited; `resetTemplates()` returns to `DefaultTemplate`.

## UI conventions

- State lives in `AppViewModel`; screens take `vm` and read its `mutableStateOf` properties. Any change that affects the plan must go through `planChanged()` (rebuilds engine, clears block cache, bumps `planVersion`, reschedules alarms).
- Use `vm.now()`/`vm.today()` (timezone from settings), not `LocalDate.now()`.
- Reuse components in `Components.kt` before adding new ones. Emoji are used as icons throughout.
- Category colors are defined on the `Category` enum.

## Features at a glance

- **Today**: next-prayer countdown, "now" card, timeline with check-off, ONE-thing focus, daily tip.
- **Track**: daily log — Quran pages, posts, sport, sadaqa, wins/notes, X (Twitter) growth counters (`XTrackCard`: replies, builder follows, conversations, followers).
- **Indie**: products (status, MRR, users, URL), milestones, neglect warnings.
- **Stats**: prayer/day/category scores, streaks, weekly X stats.
- **Settings**: city + prayer angles/adjustments, mosque times, bedtime/friends/sleep, notifications, week-template editor.

## Known gaps / things to watch

- Tests are only the Android Studio templates. `ScheduleEngine` and `PrayerCalculator` are pure Kotlin and ideal for JVM unit tests (summer/winter, Friday, Kebili vs fixed Dhuhr, tahajjud, overlap resolution, DST in Paris).
- `release` build has `optimization.enable = false` (no minification). No signing config.
- `SCHEDULE_EXACT_ALARM` needs user grant on Android 12+/14+; the app falls back to inexact alarms silently.
- Persistence is SharedPreferences with whole-map rewrites (`logs`, `done`) — fine now, may need Room if data grows.
- `allowBackup="true"` with backup/extraction rules in `res/xml` — review what personal data (logs, products) should be excluded.
- Not under version control; consider `git init`.
- UI is English-only strings mostly hardcoded in Kotlin (only `app_name` in `strings.xml`); `supportsRtl` is on, so Arabic localization is feasible.

## Guidelines when changing code

- Keep the engine pure (no Android imports) so it stays unit-testable.
- Prayers are sacred: never let new block logic move or shorten a `Kind.PRAYER` block.
- Never invent user data: e.g. `DailyLog.xLogged` exists to distinguish "logged 0" from "didn't log".
- Keep it offline: no network permissions or libraries without a clear reason.
