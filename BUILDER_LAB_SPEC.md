# Builder Lab

Goal: Idea → Describe → Build → Try → Change → Keep / Reject. Build judgment and expression, not early syntax memorization. Not a Phase 1 runtime dependency.

## Proposal
`child_feature_proposals`: id, family_id, child_id, title, original_input, input_type (VOICE/TEXT/DRAWING), ai_summary, feature_category, difficulty, status, created_at. Status: IDEA, DESIGNING, PROTOTYPE, TESTING, PARENT_REVIEW, APPROVED, REJECTED, SHIPPED.

Young children can speak an idea and choose among up to three cards. Older children can write a prompt and explore Input / Rule / Result. Ask at most 2–3 simple questions. Preserve the child's original idea separately from an AI rewrite. Always offer Keep / Change / No.

## Versioned sandbox
Proposals link to build_projects, build_versions, test_results and child_feedback. Each version records source branch/commit, mini spec, tests, preview artifact, provenance and adult review. Child rejection remains in history. Portfolio milestones include first idea, test, bug report and shipped feature.

Prototype branches use `kid/<anonymous-child-id>/<slug>`. No production credentials, network egress or live child data in execution. Allowlisted changes may affect content, labels or sandbox UI only. Auth, parent controls, billing, dependencies, secrets, migrations and publishing are protected paths/operations. CI enforces this restriction; a UI button is not a security boundary.

Parent approval must bind to the exact tested version/commit; any subsequent change invalidates approval. AI cannot approve its own work. Only a parent-controlled merge process can promote an approved version. Keep / Change / Delete refers to sandbox copies and never deletes production child history.

## Missions and rewards
Progress from changing a label and choosing a layout to creating a mission, expressing a condition, finding a bug, testing AI and writing a feature prompt. Recognize clear questions, testing and improvement, never code volume. Badges: Idea Maker, Bug Hunter, Tester, Designer, Builder, Story Creator.

## Future acceptance
“I want a dinosaur level” can be stored today as a typed proposal without changing mastery tables. A future isolated prototype, child test and parent-approved version can ship without rebuilding the learning data model.
