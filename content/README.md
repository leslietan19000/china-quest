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

## Rebuild and validate

From the repository root, run:

```powershell
python tools/content-build.py
python tools/content-validate.py
```

Both scripts use only the Python standard library. The build script checks the pinned source hashes before extracting fields. The validator checks the JSON schema essentials, complete sourced core fields, source attribution, review flags, and the first-day ordering.
