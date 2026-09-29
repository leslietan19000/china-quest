# Architecture

## Boundaries
`android:app` owns native UI, SQLite repositories and local parent gate. `android:core` owns deterministic ReviewScheduler, MasteryCalculator, content policy, RewardEngine and SeasonEngine. Domain code does not import Android, Supabase or AI SDKs.

SQLite is the Phase 1 local source of truth. Every child record is scoped by child ID; sessions and stamps use unique child/date keys. Individual learning events are immutable and use stable UUIDs. Completion and rewards are committed transactionally. Day boundaries follow local device dates; future family sync adds an explicit family timezone and captures event offsets. No network permission in the initial APK.

The seed content is packaged inside the APK. All approved seed facts, mastery, pending review and at least seven dated plans are local. An interrupted session resumes from persisted task steps. Review overflow remains due and is visible rather than silently discarded.

The 0.2 feedback iteration adds three local adapters without changing the cloud gate: parent-screened prior knowledge changes new-content selection; the sourced word companion enriches character lessons; `OfflineChineseSpeech` plays bundled media and only optionally falls back to a non-network Android Chinese voice. App lifecycle/navigation stops playback, a fresh tap replaces the current sound, and there is no autoplay. Schema v2 is additive and preserves the v1 session format. `WORD` self-assessment uses the existing word-use skill and typed sync mapping; it has no parent authority.

## Sync seam (Phase 2)
A transactional outbox shares the local transaction with learning changes. The future transport uses a parent-authorized device identity and idempotent server event ingestion. The server binds a device to one family and permitted children; it never trusts payload family IDs. Parent-authored weekly evidence and reward approval have higher authority than child self-report. Append-only events are replayed by versioned reducers; conflicts do not choose a latest score blindly.

Device pairing, key revocation, secure local token storage, retries with bounded exponential backoff and integration-tested RLS are release gates. No service key on a device. A transport failure cannot disable the local repository. Phase 1 does not claim cloud sync.

## Backend and dashboard
Supabase Postgres, Auth and private Storage behind family and child policies. Parents authenticate; children do not need email addresses. Next.js dashboard uses only a publishable key plus authenticated parent session; privileged server operations verify role, family membership and entity ownership. Secrets never appear in browser bundles. Backend schema is not considered production-ready until isolation tests pass against Postgres.

## Extensibility
Content is typed and versioned; characters are one learning object kind. Seasons reference missions, not ownership of mastery. Travel, creator and builder modules reference child/family IDs and content IDs. AI returns drafts with provenance/review metadata through interfaces, never unreviewed facts or production changes. Feature proposal → sandbox project/version → test results → child feedback → parent review → approved merge is auditable.

## Views decision
Use platform Android Views for a small, predictable dependency surface and explicit static page transitions. Compose remains an option. This is an engineering default, not a claim of measured superiority on BOOX; verify latency, font rendering and ghosting on actual hardware before changing the rendering strategy.
