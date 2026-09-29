# China Quest · 我的中国远征 · Misión China

**Learn · Create · Explore / 学习 · 创造 · 探索**

A family learning system, starting with two independent offline Chinese learners on BOOX Nova Air C. Paper, conversation and real life are part of every lesson. The first China trip is a milestone, not an ending.

## Current work

The first offline Android pilot is built and signed: **[ChinaQuest-0.1.1-pilot.apk](artifacts/ChinaQuest-0.1.1-pilot.apk)** (about 0.95 MB). Two independent profiles, 178 source-backed characters, new learning, review, quiz, paper/voice self-assessment, saved progress, PIN-protected parent overview and optional report export. **57 automated tests pass: 39 Android/core, 12 local backend and 6 web tests, plus 8 browser acceptance checks.** Physical BOOX acceptance is still pending; no device is attached.

Read [BOOX installation](docs/BOOX_INSTALL.md) and [PROGRESS.md](PROGRESS.md) for actual evidence and limitations. Parent-guided teaching translations/examples still need review. Later features are not shipped simply because their architecture is described.

## Build and verify

On this prepared Windows machine, run `tools/android-build.ps1` and then `tools/package-apk.ps1 -SkipBuild`. New environments use `tools/setup-android.ps1`; see [environment notes](docs/ENVIRONMENT.md). Validate content with `python tools/content-validate.py`. In `backend/`, use `npm ci --ignore-scripts` then `npm test` for the local PostgreSQL/RLS contract suite. Versioned sources and lockfiles are in the repository; toolchains, signing keys and generated artifacts remain local.

For the parent dashboard, run `npm ci --ignore-scripts`, `npm test`, `npm run typecheck` and `npm run build` in `web/`, then `npm run start`. On this prepared machine the existing build can also be started with `tools/start-dashboard.ps1`. Open **[the local parent dashboard](http://127.0.0.1:3210)** on this computer. Import the report explicitly exported from the BOOX parent area; a clearly marked synthetic demo is also available. No report is uploaded or saved in browser storage. See [web usage](web/README.md) and [browser verification](docs/QA_PHASE_3.md). The loopback address is not a remotely accessible phone or cloud deployment.

## Repository

- `android/`: Android app and platform-independent learning engines.
- `web/`: Next.js parent dashboard (Phase 3).
- `backend/`: Supabase schema, sync contracts and isolation tests (Phase 2).
- `content/`: versioned character content and provenance.
- `docs/`: detailed specs, verification and operating guides.
- `tools/`: reproducible builds, content validation and QA.
- `kid-lab/`: isolated future child proposals and prototypes, never production authority.
- `artifacts/`: local APK and evidence (binary artifacts excluded from source control).

## Principles

No ads, feeds, punishments for missed days, sibling rankings, automatic purchases, public child identities or AI dependency. Daily targets are configurable. Long-term skills survive seasons. Parent approval is required for production coding changes and future publication.

Development is intentionally staged. Native parent overview provides same-device daily visibility before cloud accounts exist. The local Next.js parent dashboard consumes an explicitly exported report; it is not a deployed cloud service. Real device pairing, parent accounts, authenticated push/pull and production deployment remain gated.
