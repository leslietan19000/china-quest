# China Quest 0.2.0 verification

Release file: `artifacts/ChinaQuest-0.2.0-pilot.apk` (4,284,025 bytes).

SHA-256: `445f1235c8e3b0534fea6bccf8dd780515d50696ca3a49a40dc128766fd82a0e`.

## Actual checks

- `tools/android-build.ps1`: successful `:core:test`, `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`. Core: 22 tests; Android: 33 tests; no failures, errors or skips. Lint: 0 errors, 8 warnings (existing target/PIN commit/localization limitations plus the added local checkbox text).
- `backend/npm test`: 13 tests passed, including typed word-use self-assessment and rejection of forged parent authority. These remain local Postgres/contract tests, not hosted Supabase tests.
- `python tools/content-validate.py`: 178 character records and 353 word entries validated against pinned source snapshots. The draft character-level words/examples remain hidden; the new companion dictionary subset is used instead.
- `tools/audio-validate.ps1`: all 469 exact-text clips matched SHA-256, with positive ffprobe durations of about 1.22–1.89 seconds. One sample was also checked for non-silent signal. This does not certify phonetic accuracy.
- `tools/package-apk.ps1 -SkipBuild`: APK v2 signature verified. The certificate SHA-256 exactly matches the shipped 0.1.1 APK, enabling a normal same-package update. Final package ZIP inspection verified all 469 clip hashes and both dictionary license notices inside the actual APK.
- Six Robolectric screenshots were generated under `android/app/build/screenshots`. The main learning card, prior-knowledge selector and word challenge were visually inspected at 744×992, with readable text and controls. These are software-rendered screens, not BOOX photos.

Detailed release evidence is saved in `artifacts/android-verification-0.2.0.json`. Generated artifacts are kept locally and excluded from source control.

## Regression coverage

The migration test starts from a frozen copy of the exact CREATE statements in the shipped schema v1, then seeds representative active/completed sessions, scores, review state, outbox evidence, a daily stamp and a configured PIN. Opening it in v2 preserves those values, changes only the additive schema, and remains stable when reopened.

Placement tests cover separate children, seven regenerated plans, no fabricated mastery/stamps, unchanged active/completed lessons, invalid-ID rejection, canceled UI selection, saved placement after reopening and later wrong-answer review. Word tasks have separate low-weight SELF evidence and age-specific limits; parent settings can disable them. Quiz choices avoid multiple answers with the same displayed reading and use stable, varied positions.

The seven speech tests cover packaged audio without an Android TTS engine, real asset bytes and simulated MediaPlayer release, latest-tap behavior, network-only voice refusal, stop/close callbacks, stale errors and the two-failure fallback limit. Robolectric requires media metadata for its simulated player; that metadata was registered explicitly. The test checks actual Ogg asset bytes but does not decode/play audio through a physical speaker.

One test compile error (wrong field name) and one missing Robolectric media registration were diagnosed and fixed before the passing build. Neither was bypassed. No unchanged failing action was retried indefinitely.

## Remaining real-device acceptance

The parent has confirmed v1 installation and real use. No device was attached to adb during this update, so v2 still needs an ordinary BOOX cover-install, preserved-PIN/progress check, media-volume and offline sound check, word pronunciation listening, pause/background stopping, and real E-Ink usability feedback. Do not uninstall or clear data to perform the upgrade.

The unchanged Next.js dashboard was not rebuilt or re-tested unnecessarily in this iteration. Its v1 snapshot contract is preserved. It does not yet display the new prior-knowledge count or control the BOOX remotely.
