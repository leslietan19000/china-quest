# Child safety and privacy

Default family-private, no analytics SDK, ads, social graph, public profile, third-party chat or public uploads. First APK has no Internet, microphone, camera or location permission and uses app-private storage with backups disabled. Profiles are anonymous placeholders.

Parent PIN is salted PBKDF2 with persistent attempt throttling, no default PIN and no child-facing reset path. Parent mode locks on backgrounding. This supervises shared-device navigation; it is not protection against a device administrator, a rooted device or extraction from a debug build. Production signing and device threat-model review precede broader distribution.

Only the parent may change learning settings, approve wishes/rewards, change seasons or authorize prototypes/publication. A child may decline or change an AI suggestion. AI outputs are drafts and must not impersonate a trusted parent or teacher.

Cloud release requires verified family/child isolation, parent-owned accounts, device pairing/revocation, private storage, backup/deletion policy and an explicit decision about children's data. Do not upload real child information during development. Money and hidden budgets remain outside child payloads and UI.

No indefinite retention of raw voice/media by default. Future media processing is optional and consented per family. Publication is PRIVATE → FAMILY_SHARED → PARENT_REVIEW → APPROVED_FOR_PUBLICATION, with no child permission to publish.

The 0.3 creative preview stores only local drawing actions, scene steps and per-version feedback. It records no camera or microphone data and sends nothing to AI or a server. Children can freely choose Keep / Change / No; those choices neither affect rewards nor approve a software release. The original parent requests are recorded without guessing which child proposed each idea. This supervised engineering branch is not yet a child-operated secure code-execution sandbox.
