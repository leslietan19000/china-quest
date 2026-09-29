# Ordered roadmap and exit conditions

Each phase ends with build, relevant tests, fixes and PROGRESS.md update. Future capabilities are not counted as shipped simply because their schema is described.

| Phase | Exit condition | Status |
|---|---|---|
| 0 Repository/docs | Isolated repository, product boundaries, security and data contracts | Done |
| 1 Local Android | Installable APK, both child flows, persistence, parent daily visibility, tests | Software passed; BOOX hardware acceptance pending |
| 2 Backend/sync | RLS/isolation, event idempotency, pairing, authenticated push/pull and offline recovery | Local schema/contracts verified; pairing/transport/cloud activation incomplete |
| 3 Parent web | Parent auth and real family state/controls; local report import can precede remote activation | Local report dashboard built and browser-tested; live auth/state/controls incomplete |
| 4 Rewards | Configurable real-life choices and parent approvals, idempotent ledger | Core rules tested; workflow not shipped |
| 5 Seasons | Narrative missions, preserved lifetime mastery, next journey | Core transitions tested; workflow not shipped |
| 6 Weekly tests/reports | Parent evidence, 10–15-item test, meaningful family reports | Not started |
| 7 E-Ink optimization | Real BOOX measurements, restart/sleep/touch/large-font checks | Static theme delivered; hardware pending |
| 8 Travel architecture | Versioned private memories and mission contracts | Documented only |
| 9 Creator architecture | Skills, missions, storyboard/private-media contracts | Documented only |
| 10 Builder prototype | Proposal → sandbox preview → test → child feedback → exact-version parent approval | Documented only |

No branch may poll indefinitely. Dependency actions stop after two equivalent failures. Real accounts, fees, production deployments and child-data cloud activation need the user's decision at the concrete release step. Code, tests and local prototypes proceed independently.
