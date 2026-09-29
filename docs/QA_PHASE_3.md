# Phase 3 local parent dashboard verification

The tested deliverable is the local, read-only Next.js dashboard, not a deployed or authenticated cloud service. It consumes an explicit Android parent export and labels the recorded day and export time.

## Build and automated checks

- `web/npm test`: 6 tests passed, covering the report contract, invalid dates, count/progress consistency, duplicate children, unknown fields and file parsing limits.
- `web/npm run typecheck`: passed.
- `web/npm run build`: production build passed.
- `tools/web-qa.cjs`: 8 browser acceptance checks passed against the production build in a fresh, headless Chrome profile.

The browser checks cover an empty page without fabricated family state, an explicitly labeled synthetic demo, child/detail navigation, an actual Android repository export made with synthetic children, separate A=1/B=0 character exposure, a 390px phone viewport without horizontal overflow, malformed-file rejection with previous state cleared, refresh clearing reports with empty browser storage, and no browser runtime errors or external network requests.

The Android fixture comes from `SyncAndSnapshotTest` at `android/app/build/reports/parent-snapshot-acceptance.json`. It exercises the real `QuestStore.parentSnapshot` serialization, rather than a separately hand-authored approximation. Its names and events are test data.

Evidence is saved locally in `artifacts/web-qa/results.json` and four PNGs: empty desktop, demo desktop, Android-report desktop and mobile. The empty desktop, Android-report desktop and mobile screenshots were visually inspected for text clarity, spacing and clipping. This is a responsive browser check, not a physical phone test.

## Running it

Start the already built dashboard with `tools/start-dashboard.ps1` and open `http://127.0.0.1:3210` on this computer. The script refuses to replace a process already using that port. It starts one hidden process, records its PID and logs under `.toolchain`, and does not install an automatic startup task.

For a clean build, follow `web/README.md`. Browser QA additionally uses the prepared machine's Playwright dependency location via `QUEST_NODE_MODULES` and its installed Chrome executable; the script states these prerequisites and fails if the Android fixture is missing. It does not attach to the user's existing browser profile.

## Limits and next gates

There is no live BOOX connection, parent web account, remote setting update, reward approval or cloud publication. The local address cannot be opened from another device. Importing a report does not create a full backup, and file authenticity is not cryptographically attested. The dashboard cannot treat its input as authority for server-side actions.

The actual APK, backend contract and web validator suites total 57 passing automated tests, with 8 additional browser checks. Real BOOX install, sleep/resume, E-Ink refresh quality, family lesson timing, hosted Supabase isolation and reconnect behavior still require their respective environments.
