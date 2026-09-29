# Database schema and isolation

## Phase 1 shipped SQLite

| Table | Key / role |
|---|---|
| children | id; family_id, display_name, avatar, age_group, learning_stage, daily_target, review_only, word_practice, preferences, current_season/current_stage, created_at |
| characters | stable content id, ordinal, full source record JSON |
| child_learning_profiles | child_id; nine long-term level fields, initialized to zero pending validated stage assessment |
| mastery | (child_id,character_id); four distinct scores plus word evidence, successful days, parent checks, spacing step, due date and lapses |
| plans | (child_id,day); at least seven local dated new-content allocations |
| sessions | (child_id,day); persisted task sequence, next cursor and completed flag |
| events | immutable UUID, child_id, session day, kind, evidence payload and timestamp |
| outbox | event_id; transactional event envelope, attempt count and acknowledgement |
| daily_stamps | (child_id,day); exactly one completion stamp per child/day |
| prior_knowledge | (child_id,character_id); parent-confirmed starting knowledge, confirmation timestamp and next check date |

Foreign keys are enabled. Mastery/due index begins with child_id. SQLite app-private storage provides local isolation through explicit child filters, composite keys and transactional repository APIs. It is not Supabase RLS. Profile switching intentionally permits a family member to select either child; this is a supervised shared device.

Schema v2 uses an additive `ALTER TABLE` and creates `prior_knowledge` with a child/date index. The database name, application ID, PIN preferences and signing key remain unchanged. No migration drops tables or rewrites sessions, events, mastery or stamps. A frozen copy of the shipped v1 CREATE statements is used by the upgrade regression test.

Parent placement excludes selected characters from new-letter allocation without writing fabricated mastery, test evidence or rewards. Existing progress remains. New placement starts a check seven days later; real review outcomes then use the ordinary scheduler. Parent corrections may remove placement flags, while the immutable settings event records the change. These settings never enter the child sync outbox. Unstarted plans are regenerated for seven days; active and completed sessions retain their tasks/cursor. The existing parent-export schema stays at v1 for dashboard compatibility; the additional prior-knowledge count appears in the BOOX parent area only.

`ReviewState.history` is not duplicated inside each state snapshot; the append-only events table preserves full history. Snapshots store current schedule and counters. This avoids growing a copied JSON list for every answer. Device dates earlier than previous evidence use the latest evidence date for scheduling and record both the observed session date and effective date; cloud time/zone reconciliation is a Phase 2 gate.

## Future Postgres contracts

Identity: families, parents, children, paired_devices. Parent membership is authoritative server data, never user-editable JWT metadata.

Learning: characters, character_words, character_examples, child_character_mastery, child_learning_profiles, learning_sessions, review_events, quiz_attempts, weekly_tests, weekly_test_items.

Narrative: seasons, season_stages, season_missions, child_season_progress, season_rewards, quests, quest_stages. These reference lifetime learning evidence; season changes cannot cascade-delete mastery.

Rewards: daily_stamps, choice_tickets, master_stars, family_stars, rewards, reward_redemptions, wish_items. Reward type enum: FOOD, RESTAURANT, TOY, BOOK, EXPERIENCE, FAMILY_ACTIVITY, CHOICE, CREATOR_PRIVILEGE, TRAVEL_PRIVILEGE, CUSTOM. Parent-only budgets must use a separate inaccessible relation rather than exposing a hidden UI field in a child-readable row.

Travel: travel_missions, travel_memories (id,family_id,child_id,place,mission_id,content_ids,notes,media_asset_ids,captured_at,visibility). Creator: creator_skills, creator_missions, creator_progress, creator_projects, media_assets, storyboards. All media private by default.

Builder: child_feature_proposals, builder_projects, builder_versions, builder_feedback, test_results. Compatibility names `feature_proposals`, `build_projects`, `build_versions`, `child_feedback` refer to these canonical entities, not duplicated stores. Approval references an immutable version hash.

All child-owned remote rows carry family_id and child_id with composite foreign keys that prevent cross-family assignment. RLS select/update/check policies enforce parent membership or device-child grants. No unrestricted authenticated-role policy. Raw client events cannot assert parent authority. These contracts do not imply that a remote database has been created or tested.
