# Development

Run commands from the repository root. See [environment setup](ENVIRONMENT.md), [architecture](../ARCHITECTURE.md), [progress](../PROGRESS.md), and [repository rules](../AGENTS.md).

- `android/`: Kotlin native Views, SQLite and deterministic learning engines.
- `content/`: sourced, versioned facts, words and bundled audio with attribution.
- `web/`: local Next.js parent snapshot viewer; see [web README](../web/README.md).
- `backend/`: future hosted sync schema and local contract tests. No family cloud account is configured.
- `kid-lab/`: child-origin proposals and isolated preview records.

On Windows, `tools/setup-android.ps1` prepares a local toolchain. `tools/android-build.ps1` builds and tests Android; `tools/package-apk.ps1 -SkipBuild` packages the resulting APK. `python tools/content-validate.py` validates content. The original pilot signing key is private and not in this repository: builds on another machine will not be able to overwrite the published APK unless signed with that same private key. Never request or publish it.

For the dashboard, run `npm ci --ignore-scripts`, `npm test`, `npm run typecheck`, `npm run build`, then `npm run start` in `web/`. This runs on the developer's computer, not a public website. Reports remain in browser tab memory. `backend/` uses `npm ci --ignore-scripts` and `npm test` for local contract tests.

Do not publish `.toolchain`, signing keys, `.env`, databases, real child exports or private media. Public APKs are release attachments, not tracked Git files. Preserve third-party source notices. The project has not selected a general open-source license for its own code.
