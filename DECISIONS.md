# Decisions

| ID | Decision | Rationale / consequence |
|---|---|---|
| D001 | Isolated `D:\video cut\china-quest` repository | Existing directory contains unrelated assets; preserve them. |
| D002 | Kotlin, native Views, SQLiteOpenHelper, min API 26 | BOOX target is Android 11; simple offline rendering/storage. SQLite is allowed by product brief; Room can later implement the same repository interface. |
| D003 | Initial local parent dashboard inside Android | Daily parent visibility is part of the first acceptance milestone; web dashboard follows backend. |
| D004 | Parent creates PIN on first launch, no default code | Avoid a public bypass; local gate is for household supervision, not an OS security boundary. |
| D005 | Anonymous Child A / Child B defaults | No real child names, accounts or uploaded data needed. Editable by parent. |
| D006 | Five new items configurable; due review capped per session | Active screen use stays bounded; overdue items remain due. No punishment for missed days. |
| D007 | No automatic mastery from exposure | Evidence dimensions, distinct days and parent evidence prevent a single answer from conferring mastery. |
| D008 | Versioned, sourced facts; uncertain fields NEEDS_REVIEW | LLM-authored teaching drafts never masquerade as verified dictionary facts. |
| D009 | No network permission in first APK | Offline-first milestone can be exercised without any external service; transport is a later adapter. |
| D010 | Debug-signed local pilot APK first | Installable deliverable; production signing and distribution require a parent-controlled release process. |
| D011 | No cloud project or real accounts created | Phase 2 can be developed and tested locally; actual family cloud deployment requires account/privacy choices. |
| D012 | Bounded attempts | At most two materially identical dependency failures; record blocker and require changed conditions before retrying. |
| D013 | Project-local Java TCP fallback | The Windows execution host rejects AF_UNIX NIO pipes even with network approval. A bounded diagnostic confirmed IPv4/IPv6 TCP works. JDK `PipeImpl.createListener` falls back to TCP when `jdk.net.unixdomain.tmpdir` is unavailable; process-local build setting selects that existing fallback. Independent diagnostic then reported NIO pipe OK. No OS network changes. |
| D014 | Preserve events, compact schedule snapshots | All evidence is immutable in events/outbox. Do not duplicate the unbounded ReviewState.history list in every SQLite state JSON. |

Dates in progress records reflect the local Chile timezone where possible; machine UTC can differ by a day.
