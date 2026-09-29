# China Quest · 我的中国远征 · Misión China

**Learn · Create · Explore / 学习 · 创造 · 探索**

A family learning system, starting with two independent offline Chinese learners on BOOX Nova Air C. Paper, conversation and real life are part of every lesson. The first China trip is a milestone, not an ending.

## Current work

The parent confirmed the first APK installs and is being used by the children on BOOX. The latest child-requested prototype is built and signed: **[ChinaQuest-0.3.0-preview.apk](artifacts/ChinaQuest-0.3.0-preview.apk)** (4.37 MB), on `kid/family/color-scenes`. It adds six-color Hanzi painting, six tap-stepped scenes (mouth/eating, opening/closing, water/drinking), independent saved work and Keep/Change/No feedback. **75 Android/core tests pass; build and lint pass (0 errors, 12 warnings).** Seven creative screenshots were generated and the coloring, workshop and three scene families visually inspected. Physical BOOX color, refresh, touch and cover-install acceptance for this version remains pending. The preview has not been merged to main.

Read the **[creative preview parent guide](docs/V3_PARENT_GUIDE.md)**, [BOOX installation](docs/BOOX_INSTALL.md), [0.3 verification](docs/QA_V3.md) and [PROGRESS.md](PROGRESS.md). Update over the installed app; do not uninstall or clear data. The original package/signature/PIN and learning database remain intact. The [second-version features](docs/V2_PARENT_GUIDE.md) remain: per-child prior-knowledge selection, 178 sourced characters, 353 word entries and 469 offline clips. Chinese/Spanish teaching translations/examples still need review, and synthetic pronunciation needs real-device listening. This iteration does not change backend/web contracts; their previous 13 backend tests, 6 web tests and 8 browser checks were not repeated unnecessarily. Later features are not shipped simply because their architecture is described.

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
