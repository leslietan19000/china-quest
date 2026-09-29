# Content model

`content/characters.json` is the versioned seed bundled in the APK. It currently contains 178 records. `tools/content-build.py` imports a fixed Unicode 17.0.0 snapshot; `tools/content-validate.py` checks completeness, uniqueness, provenance, order and review flags. Source URLs, field mapping, checksums and license links are in each record and `content/README.md`.

Character fields: id, character, pinyin, meaning_zh, meaning_es_optional, meaning_en, radical, stroke_count, common_words, example_sentence, confusable_characters, difficulty, topic, source_status, source, review_status.

The source provides Mandarin citation reading, an English definition, radical information and stroke count. Polyphony, tone changes in context, regional stroke variants and age suitability still need editorial review. Basic source facts can appear offline; the source English definition is available in the parent dictionary. No server or LLM is consulted at lesson time.

Chinese/Spanish glosses, common words and examples are teaching content. Null means unknown, never fabricate. `NEEDS_REVIEW` drafts must not appear on child learning pages; `APPROVED` teaching content requires an identified parent/editor approval in a future editorial workflow. Current pilot deliberately requires a parent to explain meaning and model spoken Chinese. It is not a complete independent Chinese course.

Future typed items: CHARACTER, WORD, SENTENCE, READING, EXPRESSION, REAL_WORLD_MISSION, CREATOR_MISSION, BUILDER_MISSION. Each version includes stable id, prerequisites, suggested stage, target dimensions, language, attribution and review metadata. AI-generated drafts add generated_by, generated_at, model, review_status, approved_by. Learning events refer to content ID plus version; edits cannot rewrite past evidence.
