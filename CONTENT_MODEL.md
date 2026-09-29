# Content model

`content/characters.json` is the versioned seed bundled in the APK. It currently contains 178 records. `tools/content-build.py` imports a fixed Unicode 17.0.0 snapshot; `tools/content-validate.py` checks completeness, uniqueness, provenance, order and review flags. Source URLs, field mapping, checksums and license links are in each record and `content/README.md`.

Character fields: id, character, pinyin, meaning_zh, meaning_es_optional, meaning_en, radical, stroke_count, common_words, example_sentence, confusable_characters, difficulty, topic, source_status, source, review_status.

The source provides Mandarin citation reading, an English definition, radical information and stroke count. Polyphony, tone changes in context, regional stroke variants and age suitability still need editorial review. Basic source facts can appear offline; the source English definition is available in the parent dictionary. No server or LLM is consulted at lesson time.

Chinese/Spanish glosses and examples in `characters.json` remain teaching drafts. Null means unknown, never fabricate. `NEEDS_REVIEW` drafts must not appear on child learning pages; `APPROVED` teaching content requires an identified parent/editor approval in a future editorial workflow. The pilot requires a parent to explain meanings and help judge expression. It is not a complete independent Chinese course.

Version 0.2 adds `words.json`: 353 selected word entries across the same 178 character IDs, with 1–2 entries per character. Each word stores text, accented/numeric pinyin, a dictionary English sense and `SOURCED_VERIFIED`. Spellings, readings and senses match a pinned CC-CEDICT snapshot; this companion asset does not approve the older draft fields. Children see word text and pinyin; the English dictionary sense is retained as provenance, not shown as a Spanish translation. Attribution, changes and CC BY-SA 4.0 terms are in `content/LICENSE-CC-CEDICT.txt` and `content/README.md`.

`content/audio/manifest.json` maps exact text to an Ogg asset and SHA-256. The 469 unique clips cover every character and selected word; reused words share one clip. These are locally generated Mandarin speech, not human-verified recordings for every meaning. Citation tones, neutral tones and polyphonic readings can differ in context; the child UI provides the word's own sourced pinyin and a contextual note when it differs from the isolated character. Human listening on BOOX remains a release acceptance check. Playback never records the child or requires a remote AI service.

The bounded `WORD` task records low-weight SELF evidence in the existing per-character word-use dimension. It does not claim pronunciation recognition, sentence correctness, individual vocabulary mastery or automatic stage promotion. Dedicated word-level mastery/content-version events remain a later learning-engine extension.

Future typed items: CHARACTER, WORD, SENTENCE, READING, EXPRESSION, REAL_WORLD_MISSION, CREATOR_MISSION, BUILDER_MISSION. Each version includes stable id, prerequisites, suggested stage, target dimensions, language, attribution and review metadata. AI-generated drafts add generated_by, generated_at, model, review_status, approved_by. Learning events refer to content ID plus version; edits cannot rewrite past evidence.
