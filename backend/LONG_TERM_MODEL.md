# Later domain tables

These are explicit model boundaries for later phases. They are **not** created by the Phase 2 development schema or exposed as working Android features.

- `seasons`: immutable season identity, content version and goal rules. `child_season_progress`: `(family_id, child_id, season_id)` progress and ending choice. Completing a season starts a new progress row while `child_learning_profiles` and `child_character_mastery` remain untouched.
- `travel_stops`: a curated stop belongs to a season and has a locale, verified source and content hash. `child_travel_visits`: child, stop, visit date and parent-approved evidence. Location tracking is not implied.
- `creator_proposals`: typed artifact category, child owner, stage, content version/hash, submitted date and status (`DRAFT`, `PENDING`, `APPROVED`, `REJECTED`). `creator_reviews`: a separate parent/moderator identity, decision date and the **exact proposal version hash** reviewed. A later edit creates a new version and clears the effective approval. No child may approve or merge its own proposal.
- `builder_projects`: project owner, stage and state. `builder_components` and `builder_submissions`: typed component references and immutable submitted versions. `builder_reviews` binds parent approval to a submission hash. Client writes must remain child scoped, and trusted review/merge operations must be separate from child submission permissions.
- `reward_awards`: derived, immutable child/day or family/week awards referencing evidence, with unique natural keys. Monetary value, public ranking and purchases are outside this model.

The canonical product entities remain those in root DB_SCHEMA.md: travel_missions and travel_memories; creator_skills, creator_missions, creator_progress, creator_projects, media_assets and storyboards; child_feature_proposals, builder_projects, builder_versions, builder_feedback and test_results. Names such as travel_stops/creator_proposals above describe possible supporting entities, not replacements for those required contracts. Reward budgets may be private parent data; they must never enter child-readable rewards or client payloads. Future public media states are PRIVATE, FAMILY_SHARED, PARENT_REVIEW, APPROVED_FOR_PUBLICATION, with parent authority required for the final transition.

Before implementing these tables, define lifecycle commands, RLS policies, composite family/child foreign keys, content moderation and replay tests for each. Do not replace the entities with one opaque JSON field or expose approval writes to a child role.
