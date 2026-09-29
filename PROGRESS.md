# Progress

## Phase 0 — Repository / docs / architecture
**Completed:** environment inventory; isolated project directories and local Git repository; documented product, offline architecture and decisions. Existing workspace has Node 24.18.0, npm 11.16.0, Git 2.55.0, Python 3.14 and ripgrep. Project-local JDK / SDK / Gradle have been provisioned without changing global PATH.

**Tests passed:** none yet. Documentation creation is not a build or runtime test.

**Known issues:** BOOX hardware not yet observed. No dedicated cloud project is configured.

**Next phase:** Phase 1 native offline Android milestone; first build and deterministic engine tests.

## Phase 1 — Local APK milestone built and verified; BOOX acceptance pending
Implemented native UI, PIN setup/gate, two profiles, seven local plans, new content, due review, recognition quiz, spoken/written self-assessment, resumable transactions, daily stamp and local parent overview. 178 source-backed characters validate. Teaching translations/examples remain gated pending review.

**Completed:** installable, debug-signed `artifacts/ChinaQuest-0.1.0-pilot.apk`. Native parent overview displays both children independently. All source and reproducible build scripts are in the local Git repository.

**Tests passed:** 16 JVM core + 15 Android Robolectric tests; 178-record content validator; lint 0 errors (7 documented warnings); APK v2 signature; native rendered screens visually inspected. See [QA_PHASE_1.md](docs/QA_PHASE_1.md).

**Known issues:** actual BOOX installation/refresh not tested because no device is connected; translations/examples await adult editorial review; no audio or handwriting recognition; no online PIN recovery, restorable backup or cloud sync yet; debug signing only. Parent weekly-test workflow comes in Phase 6.

**Next phase:** Phase 2 backend schema, isolation tests and idempotent sync boundary. Actual cloud activation requires choosing/authorizing a family project; an unrelated existing Supabase project will not be reused automatically.

Environment blocker resolved: Java NIO's AF_UNIX path failed twice. TCP-only diagnostic succeeded; project-local standard JDK TCP fallback verified with `NIO pipe OK`, allowing the build. No BOOX device is connected according to adb.

## Phase 2 — Local boundary implemented; remote activation incomplete

**Completed:** transactional SQLite outbox adapter; bounded sync coordinator; typed Android-to-server event mapping; executable Postgres schema with family/child RLS, parent-only authority, composite event ownership keys, immutable event ingestion and payload-conflict detection. Optional parent report export is added in 0.1.1.

**Tests passed:** Android now **22 core + 17 Android = 39 tests passed**, 0 failures/skips; lint 0 errors, 7 documented warnings; 0.1.1 APK v2 signature verified. Backend **12/12** embedded-Postgres/contract tests passed; these use PGlite and a test auth shim, not a hosted Supabase instance. Final APK: `artifacts/ChinaQuest-0.1.1-pilot.apk`; hash/test evidence in `artifacts/android-verification.json`. Unicode redistribution license is bundled.

**Known issues:** no dedicated cloud project or accounts provisioned, no device pairing/revocation/secure token transport, no reducer parity and bidirectional setting reconciliation. APK still has no Internet permission. Hosted Supabase advisors and physical reconnect tests cannot yet be run. Full Phase 2 is not marked complete.

**Next:** while account activation remains unconfigured, continue the independent local part of Phase 3: a Next.js parent view consuming the explicitly exported report. It will clearly identify offline snapshots and cannot pretend to change the BOOX remotely.

## Later phases
2 Backend/sync → 3 Parent web dashboard → 4 rewards → 5 seasons/China Quest → 6 weekly tests/reports → 7 measured E-Ink optimization → 8 travel architecture → 9 creator architecture → 10 Builder Lab prototype.
