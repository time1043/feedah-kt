# feedah-kt

Kotlin Multiplatform + Compose Multiplatform port of the feedah vocabulary app
(source: `C:\code3\feedah`, Expo/React Native). The migration plan, decisions
and the semantic-parity checklist live in [docs/PORT_PLAN.md](docs/PORT_PLAN.md).

## Targets

| Target | Status |
|---|---|
| Android (`androidApp`) | Primary — `assembleDebug` verified on Windows |
| Desktop JVM (`desktopApp`) | Dev carrier — `jvmTest` runs the logic/data suites here |
| iOS (`iosApp`, arm64 + simulator) | Compiles (`compileKotlinIosArm64` on Windows); runtime needs macOS/CI |
| Web (js/wasmJs) | Removed for phase 1 — restore path below |

## Build & verify

```bash
./gradlew :shared:jvmTest              # logic + data layer semantic tests
./gradlew :shared:compileKotlinIosArm64
./gradlew :androidApp:assembleDebug
./gradlew :desktopApp:build -x test
./gradlew :desktopApp:run              # manual smoke
```

## Architecture

```
com.time1043.feedah_kt
├── logic/        pure Kotlin: dates, format, daily aggregation, screen buses
├── data/db/      SQLDelight schema (.sq), driver factories, FeedahRepo,
│                 seed (composeResources/files/data/*.json), SettingsStore, UsageTracker
├── platform/     TtsPlayer interface + android/ios actuals (desktop no-op)
├── AppGraph      manual DI: init(factory, tts) → repo/settings/usage
└── ui/           theme (11-color palette × 2), navigation routes,
                  6 shared components, 8 screens
```

Stack: SQLDelight 2.4.1 · org.jetbrains.androidx.navigation 2.10.0 ·
kotlinx-serialization 1.12.0 · kotlinx-datetime 0.8.0 · CMP 1.12.1 ·
minSdk 30 (framework SQLite needs 3.24+ for the same upserts the original
ran via bundled expo-sqlite).

## Deliberate exclusions (per plan)

- **Convex cloud sync** — `src/cloud/*`, `convex/`, the account/profile UI and
  the `accountEmail` setting key are not ported. "Clear all data" therefore
  skips the cloud wipe step.
- **Notifications/reminders** — `expo-notifications`, reminder rows, the
  DateTimePicker dependency and the `remindersEnabled`/`reminders` keys are
  not ported. Time-of-day parsing helpers went with them.
- `db/studio.tsx` (dev tool) and the legacy pre-drizzle DB reset.

## Known differences from the TS app

- **Web target removed** — to restore: re-add `js {}`/`wasmJs {}` to
  `shared/build.gradle.kts` + the `webApp` module, add the SQLDelight
  `web-worker-driver` actual and wire the worker script. Code is kept
  common-clean for this.
- **Clear-all-data remounts** the navigation tree (back to Home) instead of
  reloading settings in place.
- **iOS silent-switch hint** (feed + settings) is not shown yet; the
  `silentHintShown` setting key is kept for parity.
- **Icons** are hand-built stroke ImageVectors (`FeedahIcons.kt`) instead of
  Ionicons — no multiplatform icon artifact exists.
- **Desktop TTS** speaks via OS engines (Windows SAPI through a persistent
  PowerShell process, macOS `say`, Linux `spd-say`/`espeak`); Android uses
  TextToSpeech, iOS AVSpeechSynthesizer.
- Search/Select pickers use `DropdownMenu` instead of bottom-sheet modals.
- Old-device data migration (drizzle-era SQLite file import) is out of scope;
  the KMP app starts from a fresh schema v1.

## Running from a device tree

The seed data (`data/*.json`, gitignored upstream) is vendored into
`shared/src/commonMain/composeResources/files/data/` — 2050/700/370; `zz.json`
was dead data upstream and is not carried over.
