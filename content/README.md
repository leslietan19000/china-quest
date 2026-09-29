# Character seed data

`characters.json` contains a beginner curriculum seed of 178 simplified Chinese characters. The first five are 一、二、三、人、口 for an easy first day. The rest are grouped in a practical order covering numbers, family, nature, body, animals, food, school, actions, city and travel, and daily life. This is a project starter sequence, not an official grade-level ranking.

## Verified character facts

Pinyin (`kMandarin`), English gloss (`kDefinition`), radical number (`kRSUnicode`, mapped to its ideograph using `CJKRadicals.txt`), and total stroke count (`kTotalStrokes`) come from Unicode's Unihan 17.0.0. The first 100 entries, and all later entries in this seed, have all four fields populated. English glosses are dictionary data copied from Unihan; they are not Chinese or Spanish translations written for this project.

The exact source files used to build the JSON are retained in `../tools/`:

| File | Official URL | SHA-256 |
| --- | --- | --- |
| `content-unihan-17.0.0.zip` | <https://www.unicode.org/Public/17.0.0/ucd/Unihan.zip> | `F7A48B2B545ACFAA77B2D607AE28747404CE02BAEFEE16396C5D2D7A8EF34B5E` |
| `content-cjkradicals-17.0.0.txt` | <https://www.unicode.org/Public/17.0.0/ucd/CJKRadicals.txt> | `826F83BE25CD18FB8A5015A514704504E1982E840EA14D058BF583E1CC620C83` |

The full upstream license is distributed as [`LICENSE-UNICODE.txt`](LICENSE-UNICODE.txt), copied from <https://www.unicode.org/license.txt> (SHA-256 `E7A93B009565CFCE55919A381437AC4DB883E9DA2126FA28B91D12732BC53D96`). Unicode's copyright and permission notice therefore accompanies the derived data. The `source` object on each record identifies the version, exact source properties, URLs, and checksums.

## Reading and radical representation

The `pinyin` value preserves the complete `kMandarin` source string, including space-separated readings in source order. For example, 地 is `de dì`; the content does not choose one reading for a particular word or lesson. A learning screen should provide context before presenting a reading.

`radical` is the third field of Unicode's `CJKRadicals.txt` mapping: the CJK unified ideograph corresponding to the Kangxi radical number in `kRSUnicode`. It uses Han ideographs such as 人 and 水, not the separate Kangxi Radicals block glyphs at U+2F00 onward. This is a dictionary/index label only; do not use it to demonstrate stroke order or handwriting form.

## Teaching content and display

Chinese glosses and Spanish glosses are `null` until supplied and checked from an appropriate dictionary. A small set of suggested words and example sentences is a project draft, not verified dictionary data. `review_status` is `NEEDS_REVIEW` on every record, and `source_status` distinguishes sourced Unicode facts from unreviewed teaching content. Keep unreviewed words or sentences out of child-facing pages until a parent or qualified reviewer approves them. The initial lessons can use verified character, pinyin, English gloss, radical, and stroke count for recognition, reading aloud, handwriting practice, and finding the character in the family's surroundings.

## Child-facing common words

`words.json` is a versioned companion asset for the 178-character sequence. It preserves the exact `characters.json` order and IDs. Each record has `id`, `character`, and one or two `words`; each word has `text` (simplified Chinese), `pinyin` (tone marks), `pinyin_numbered` (tone numbers, with `5` for a neutral syllable), `meaning_en` (one selected dictionary sense), and `source_status: SOURCED_VERIFIED`. The word text is suitable for a tap-to-speak control. The English gloss is source data, not a child-facing Spanish translation. The three characters with one suitable selected entry are 朋、她、苹. The full asset contains 353 word entries. The selection and sequence are curated for this family curriculum; `SOURCED_VERIFIED` means the spelling, reading, and selected English sense match the pinned dictionary, not that the teaching choice has been externally certified.

The source is **CC-CEDICT**, maintained by MDBG and community contributors, licensed [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). We used the dictionary snapshot dated `2022-12-13T04:11:43Z` (121,398 entries) retained as `../tools/content-cedict-mirror.u8`, fetched from the [liamsaliba/cc-cedict-stardict mirror](https://raw.githubusercontent.com/liamsaliba/cc-cedict-stardict/main/data/cedict_ts.u8). The pinned SHA-256 is `A429F52B5F411B8922A7A772DA3096E10F1A0F167132B7E410618B72A48E0390`. [MDBG's CC-CEDICT page](https://www.mdbg.net/chinese/dictionary?page=cc-cedict) identifies the dictionary and license; its site prohibits scripted access, so the data was obtained from the mirror. We selected entries and one English sense per entry, normalized pinyin capitalization, and converted tone numbers to display accents; these are changes from the source data. The attribution and redistribution notice is in [`LICENSE-CC-CEDICT.txt`](LICENSE-CC-CEDICT.txt). Distribute that notice with any shipped copy of `words.json` and preserve CC BY-SA 4.0 for this derived word asset.

`characters.json.common_words` and `example_sentence` remain teaching drafts with `NEEDS_REVIEW` and should not be substituted for the verified companion asset. A few character readings differ from their reading inside a word; display the word's own pinyin below the word. The dictionary records citation forms, so a speech engine may apply natural tone changes in context.

The curated subset excludes region-specific or ambiguous colloquial readings when a clearer beginner word is available. For example, the snapshot lists 狗狗 with a `gou3 gou1` reading; this pack uses 狗熊 (`gou3 xiong2`, “black bear”) instead.

## Rebuild and validate

From the repository root, run:

```powershell
python tools/content-build.py
python tools/content-words-build.py
python tools/content-validate.py
```

All scripts use only the Python standard library. The build scripts check pinned source hashes before extracting fields. The validator checks the character schema and source attribution, word order and coverage, exact dictionary spellings/readings/senses, pinyin tone conversion, review flags, and the first-day ordering.
