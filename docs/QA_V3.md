# China Quest 0.3.0 preview verification

Artifact: `artifacts/ChinaQuest-0.3.0-preview.apk` (4,370,207 bytes). Branch: `kid/family/color-scenes`, based on the shipped 0.2.0 implementation. No merge to main or public deployment was performed.

SHA-256: `b6aecec6409f8472fd3cd5066f77ba93ac632f555b0974666b307180b2f192a0`.

## Actual checks

- Final `tools/android-build.ps1` completed `:core:test`, `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`. **22 core + 53 Android = 75 tests**, with zero failures/errors/skips. Unchanged core outputs were reused by Gradle; Android tests ran for the final source changes.
- Lint: **0 errors, 12 warnings**. Categories: existing synchronous security-PIN preference writes, target SDK compatibility, localization/concatenated native labels, and brush draw allocations. This preview does not claim measured BOOX brush performance.
- Native graphics tests verify visible fill and brush pixels, glyph clipping, a white hole in 口, retained dark outlines, and normalized artwork through resizing. Additional cases cover cancellation, active-pointer lift/missing pointers/detach, malformed/oversized data and action capacity without silently dropping previous color.
- Scene tests exercise all six finite sequences, end-state boundaries, replay, wrong target, stray UP, canceled and multi-touch gestures, real canvas drawing and accessible explicit clicks.
- Storage/UI acceptance verifies child-scoped work/scene/opinions, close/reopen, Activity recreation, undoable clearing, return to the same lesson cursor, no creative mastery/event/stamp changes, existing learning schema v2/PIN/complete-day preservation, and workshop access after a completed day. Existing full-lesson, wrong-answer, parent gate, seven-day planning, migration and speech tests still pass.
- A deliberate SQLite write-failure trigger tests that Done/system Back cannot discard an unsaved drawing; the same drawing survives Activity recreation and saves when the trigger is removed. Force-stop/power loss with unavailable storage remains outside that guarantee.
- Seven native creative screenshots were generated at 744×992. The workshop, filled/painted 口, eating ending, open door/panda and drinking layout were visually inspected. Other static steps were rendered by the scene test. These are software-rendered images, not BOOX photos. See `android/app/build/screenshots/v3-*.png`.
- `tools/package-apk.ps1 -SkipBuild` verified APK Signature Scheme v2. The signer certificate SHA-256 is `e61cf0ee1b129e26be91e6680682d00eb477ec74ff103cfeded0f5685a437b98`, identical to 0.2.0 (already verified against 0.1.1). `aapt dump badging` confirms package `org.chinaquest.app`, versionCode 4, min SDK 26, target SDK 34. `aapt dump permissions` shows no requested permissions.
- ZIP inspection compared all **475 existing content assets**, including **469 Ogg clips**, byte-for-byte with the delivered v2 APK. No content or speech regeneration occurred; the existing dictionary licensing/source records remain bundled.

Machine-readable evidence: `artifacts/android-verification-0.3.0.json`. Build artifacts and screenshots are local and excluded from Git.

## Repairs and limits

The first build found two occurrences of a Kotlin interpolated identifier immediately followed by Chinese text. Both were corrected to explicit `${character}` interpolation. The next full build passed. A focused review then identified the failed-save navigation gap; it was fixed with pending-draft state and a fault-injection regression, and the final complete build passed. No identical failing build was repeatedly retried.

No new runtime dependency, Android permission, account, cloud upload, purchase or public publication was introduced. Backend and Next.js code/contracts are unchanged; their previous verification is recorded in QA_V2 / QA_PHASE_3 and was not rerun unnecessarily.

The first APK was confirmed by the parent as installed and used. This **v3** preview still needs a physical BOOX cover-install and checks of preserved PIN/progress, named colors, pen/finger response, scroll interception, ghosting, sleep/resume and offline sound. There is no claim of hardware performance, handwriting recognition, pronunciation scoring, full animation/video, creative-cloud backup or a secure child-operated AI coding sandbox. Proposal status TESTING means ready for family tryout; it is not parent acceptance or authority to merge.
