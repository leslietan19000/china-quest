# Phase 2 sync boundary

## Implemented locally
Learning state and its outbox event are committed in the same SQLite transaction. Event UUIDs survive retries. `SyncCoordinator` depends on interfaces, sends at most 50 events in one call, stops after two failed attempts, never deletes learning history and acknowledges only IDs in the sent batch. Offline and unpaired calls perform no transport work. Parent configuration events stay local and never ride the child evidence transport.

The coordinator, SQLite adapter, TypeScript validation and Postgres ingestion contract are implemented and independently tested. This is a transport boundary, **not enabled end-to-end cloud sync**. The APK continues to request no Internet permission. No credentials have been provisioned and no family data uploaded.

Android envelope v1: `id`, local `child_id`, `kind`, `day`, `payload`, `created_at`, `schema_version`. Learning payload adds character_id, task kind, cursor, source, skill, outcome, session_date, effective_date and algorithm_version. A trusted pairing map, not a payload field, maps local child IDs to family/server child UUIDs. A child envelope cannot claim PARENT authority.

Ingestion uses a same-role security-invoker RPC under RLS. Replaying the same ID and identical typed contents is a duplicate acknowledgement; different contents with the same ID are a conflict. Source evidence and actor identity are server-defined. Parent test evidence requires parent membership. Raw clients cannot overwrite derived mastery or grant themselves membership.

## Local interoperability
The parent can explicitly export a small JSON progress report from the Android parent space. This contains child display names, current state and seven days of actual exposure/completion counts. It contains no PIN, credentials, budgets, photos or voice. It is a report, not a full backup; do not use it to restore mastery. No automatic upload occurs. A later parent dashboard can read it in the browser without a server.

## Gates before enabling remote sync
1. User-approved dedicated Supabase project and family accounts; never reuse an unrelated project.
2. Parent-owned provisioning transaction and revocable device pairing. Children require no email.
3. TLS transport, secure Android Keystore-backed token storage, authenticated session refresh/revocation.
4. Apply generated migration, run remote isolation/advisor checks and verify no anonymous access.
5. Server reducer parity with Kotlin and stable algorithm versions; out-of-order events and parent evidence reconciliation.
6. Download/merge parent settings without mutating an in-progress lesson; deliberate family timezone/day-boundary policy for travel.
7. Real-device offline/reconnect and interrupted-batch test before shipping Internet permission.

Transport unavailability, auth failure or quota exhaustion must leave local learning fully available. There is no background retry/poll loop in this pilot. A parent must intervene after retry limits or authentication errors.
