# Phase 1 verification — 2026-09-28 (Chile)

## Executed
- Full Android build: `tools/android-build.ps1` with Gradle 8.9, JDK 17.0.20.1, AGP 8.7.3, Kotlin 2.0.21; **BUILD SUCCESSFUL**.
- JVM core JUnit: **16 passed**, 0 failed/skipped. Review ladder, same-day repetition, lapse history, date ordering/Santiago DST, mastery weighting/multiple days, configurable content policy, cooperative rewards, season transition preservation.
- Android Robolectric API 28: **15 passed**, 0 failed/skipped. Persistence after reopen, two-child isolation, seven-day plans, same-day review, error outcome, idempotency, review-only, goal changes, PIN throttling, whole first lesson by actual view clicks, child switch, parent relock, activity recreation, no network permission, static render.
- Android lint: **0 errors, 7 warnings**. Remaining warnings: deliberate synchronous PIN preference writes (2), pilot target API 34 (1), hardcoded bilingual strings awaiting resource localization (4). Android 12+ data extraction rule warning fixed and retested.
- Content validation: **178 records PASS**, complete source-backed core fields and explicit editorial flags.
- APK signature verification: **PASS**, APK v2, one RSA 2048 Android Debug signer. Package `org.chinaquest.app`, min SDK 26, target 34, version `0.1.0-pilot`.
- `adb devices`: **no connected device**.

## Visual review
Robolectric native graphics generated `android/app/build/screenshots/01-profile-picker.png`, `02-today.png`, `03-character.png`. Inspected at 744 × 992: Chinese and accented pinyin render, high contrast, primary controls visible, no clipping at this test size. These are synthetic renderings, not BOOX photographs or emulator execution.

## Failures diagnosed and fixed
1. Gradle checksum PowerShell error: stopped the repeated endpoint call; verified already-downloaded ZIP against official Gradle checksum web page. No corrupted download was executed.
2. Windows Java AF_UNIX failure: two build failures, then a separate bounded local probe showed TCP worked. Read bundled JDK source and enabled its standard TCP fallback, proven by the probe before resuming builds.
3. API 28 test resource-close cast: SDK 35 declares AutoCloseable on a class whose API 28 version lacks it. Test now explicitly closes the SQLite helper. Production code did not use that cast.

## Not yet verified
Physical BOOX installation, e-ink ghosting/refresh, real family session duration, device power/sleep behavior, Android 11 real runtime, large-font/hardware accessibility, production signing, backups, cloud sync or remote dashboard. No test here proves these.

Source facts are traceable; educational translations and most examples remain unreviewed. The pilot is parent-guided. Stable mastery cannot be earned until the future parent evidence workflow is implemented.
