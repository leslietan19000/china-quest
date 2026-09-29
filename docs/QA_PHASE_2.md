# Phase 2 local verification

`backend/npm test`: **12 tests passed** in PGlite 0.5.8 running PostgreSQL semantics with real RLS and a test-only `auth.uid()` shim. Coverage includes parent family visibility, child-only visibility, denied cross-family/cross-child mutation, immutable membership/derived state, parent-only test authority and budget settings, event replay idempotency/conflict, content approval bound to hash, and composite event ownership.

TypeScript contract tests exercise exact fields, valid calendar dates, the actual Android envelope mapping, rejection of forged parent evidence and parent settings in child transport. No hosted Supabase resources were changed.

Kotlin sync tests cover no calls offline/unpaired, successful/partial acknowledgement, rejection of unrelated acknowledgement IDs, auth failure, two-failure stop and preservation of historical evidence. SQLite integration tests preserve original events after acknowledgement and verify a report counts one actual exposure rather than five planned new items.

Limit: these tests do not validate Supabase Auth token issuance/expiry, PostgREST exposure configuration, remote storage policies, network transport, device pairing, server reducer parity or real-device reconnection. Those remain explicit Phase 2 gates in SYNC_CONTRACT.md.
