#!/usr/bin/env python3
"""Validate the seed character content and its source/review metadata."""

from __future__ import annotations

import json
import hashlib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / "content" / "characters.json"
WORDS = ROOT / "content" / "words.json"
CEDICT = ROOT / "tools" / "content-cedict-mirror.u8"
CEDICT_SHA256 = "A429F52B5F411B8922A7A772DA3096E10F1A0F167132B7E410618B72A48E0390"
REQUIRED = {
    "id", "character", "pinyin", "meaning_zh", "meaning_es_optional",
    "meaning_en", "radical", "stroke_count", "common_words",
    "example_sentence", "confusable_characters", "difficulty", "topic",
    "source_status", "source", "review_status",
}
CORE_FACT_FIELDS = ("pinyin", "meaning_en", "radical", "stroke_count")
CEDICT_ENTRY_RE = re.compile(r"^(\S+) (\S+) \[([^]]+)\] /(.+)/$")
TONE_MARKS = {
    "a": "āáǎà", "e": "ēéěè", "i": "īíǐì", "o": "ōóǒò",
    "u": "ūúǔù", "ü": "ǖǘǚǜ",
}


def marked_pinyin(numbered: str) -> str:
    result = []
    for syllable in numbered.split():
        match = re.fullmatch(r"([a-zü:]+)([1-5])", syllable)
        if not match:
            raise ValueError(f"invalid numeric pinyin syllable {syllable!r}")
        letters, tone = match.groups()
        letters = letters.replace("u:", "ü")
        if tone != "5":
            if "a" in letters:
                index = letters.index("a")
            elif "e" in letters:
                index = letters.index("e")
            elif "ou" in letters:
                index = letters.index("o")
            else:
                index = max(i for i, char in enumerate(letters) if char in "aeiouü")
            letters = letters[:index] + TONE_MARKS[letters[index]][int(tone) - 1] + letters[index + 1:]
        result.append(letters)
    return " ".join(result)


def validate_words(characters: list[dict], word_records: object) -> list[str]:
    errors: list[str] = []
    if not isinstance(word_records, list):
        return ["words.json root must be a JSON array"]
    if len(word_records) != len(characters):
        errors.append(f"words.json must have {len(characters)} records; found {len(word_records)}")
    if not CEDICT.exists():
        return errors + [f"missing pinned CC-CEDICT source: {CEDICT}"]
    digest = hashlib.sha256(CEDICT.read_bytes()).hexdigest().upper()
    if digest != CEDICT_SHA256:
        return errors + [f"CC-CEDICT source checksum mismatch: {digest}"]
    entries: dict[tuple[str, str], list[list[str]]] = {}
    for line in CEDICT.read_text(encoding="utf-8-sig").splitlines():
        match = CEDICT_ENTRY_RE.match(line)
        if match:
            _, simplified, pinyin, meanings = match.groups()
            entries.setdefault((simplified, pinyin.lower()), []).append(meanings.split("/"))
    for index, (character, word_record) in enumerate(zip(characters, word_records)):
        where = f"words[{index}]"
        if not isinstance(word_record, dict):
            errors.append(f"{where} must be an object")
            continue
        if word_record.get("id") != character["id"] or word_record.get("character") != character["character"]:
            errors.append(f"{where} id/character must match characters.json order")
        words = word_record.get("words")
        if not isinstance(words, list) or not 1 <= len(words) <= 2:
            errors.append(f"{where}.words must contain 1–2 entries")
            continue
        seen: set[str] = set()
        for word_index, word in enumerate(words):
            word_where = f"{where}.words[{word_index}]"
            if not isinstance(word, dict):
                errors.append(f"{word_where} must be an object")
                continue
            if set(word) != {"text", "pinyin", "pinyin_numbered", "meaning_en", "source_status"}:
                errors.append(f"{word_where} has missing or unexpected fields")
                continue
            text = word["text"]
            numbered = word["pinyin_numbered"]
            meaning = word["meaning_en"]
            if not isinstance(text, str) or character["character"] not in text or not 2 <= len(text) <= 4:
                errors.append(f"{word_where}.text must be a 2–4 character word containing the target character")
                continue
            if text in seen:
                errors.append(f"{word_where}.text duplicates {text}")
            seen.add(text)
            if not isinstance(numbered, str) or not isinstance(meaning, str) or not meaning:
                errors.append(f"{word_where} needs numeric pinyin and English gloss")
                continue
            if not any(meaning in meanings for meanings in entries.get((text, numbered), [])):
                errors.append(f"{word_where} spelling, reading or English sense absent from pinned CC-CEDICT")
            try:
                marked = marked_pinyin(numbered)
            except (ValueError, KeyError) as exc:
                errors.append(f"{word_where}.pinyin_numbered: {exc}")
            else:
                if word["pinyin"] != marked:
                    errors.append(f"{word_where}.pinyin must be {marked!r}")
            if word["source_status"] != "SOURCED_VERIFIED":
                errors.append(f"{word_where}.source_status must be SOURCED_VERIFIED")
    return errors


def validate(records: object) -> list[str]:
    errors: list[str] = []
    if not isinstance(records, list):
        return ["root must be a JSON array"]
    if len(records) < 100:
        errors.append(f"need at least 100 records; found {len(records)}")
    ids: set[str] = set()
    characters: set[str] = set()
    for index, record in enumerate(records):
        where = f"record[{index}]"
        if not isinstance(record, dict):
            errors.append(f"{where} must be an object")
            continue
        missing = REQUIRED - record.keys()
        if missing:
            errors.append(f"{where} missing keys: {', '.join(sorted(missing))}")
            continue
        character = record["character"]
        if not isinstance(character, str) or len(character) != 1 or not ("\u3400" <= character <= "\u9fff"):
            errors.append(f"{where}.character must be one CJK unified ideograph")
        elif character in characters:
            errors.append(f"{where}.character duplicates {character}")
        else:
            characters.add(character)
        if not isinstance(record["id"], str) or not record["id"].startswith("hanzi-"):
            errors.append(f"{where}.id must use the hanzi-<codepoint> form")
        elif record["id"] in ids:
            errors.append(f"{where}.id duplicates {record['id']}")
        else:
            ids.add(record["id"])
        if isinstance(character, str) and len(character) == 1 and record["id"] != f"hanzi-{ord(character):04X}":
            errors.append(f"{where}.id does not match the character code point")
        for field in CORE_FACT_FIELDS:
            if record[field] is None or record[field] == "":
                errors.append(f"{where}.{field} is required for the sourced core")
        if not isinstance(record["stroke_count"], int) or record["stroke_count"] < 1:
            errors.append(f"{where}.stroke_count must be a positive integer")
        if not isinstance(record["difficulty"], int) or not 1 <= record["difficulty"] <= 10:
            errors.append(f"{where}.difficulty must be from 1 to 10")
        for field in ("common_words", "confusable_characters"):
            if not isinstance(record[field], list) or any(not isinstance(item, str) for item in record[field]):
                errors.append(f"{where}.{field} must be an array of strings")
        for field in ("meaning_zh", "meaning_es_optional", "example_sentence"):
            if record[field] is not None and not isinstance(record[field], str):
                errors.append(f"{where}.{field} must be a string or null")
        source = record["source"]
        if not isinstance(source, dict):
            errors.append(f"{where}.source must be an object")
        else:
            for key in ("url", "version", "fields", "sha256", "radical_mapping_url", "radical_mapping_sha256", "license_url"):
                if not source.get(key):
                    errors.append(f"{where}.source.{key} is required")
            if isinstance(source.get("fields"), dict):
                for field in ("pinyin", "meaning_en", "radical", "stroke_count"):
                    if not source["fields"].get(field):
                        errors.append(f"{where}.source.fields.{field} must identify its source property")
        if record["review_status"] != "NEEDS_REVIEW":
            errors.append(f"{where}.review_status must remain NEEDS_REVIEW until a human review is recorded")
        if "NEEDS_REVIEW" not in record["source_status"]:
            errors.append(f"{where}.source_status must disclose the unreviewed teaching fields")
    expected_start = ["一", "二", "三", "人", "口"]
    actual_start = [record.get("character") for record in records[:len(expected_start)] if isinstance(record, dict)]
    if actual_start != expected_start:
        errors.append(f"first five records must be {''.join(expected_start)}; found {''.join(str(c) for c in actual_start)}")
    return errors


def main() -> int:
    try:
        records = json.loads(CONTENT.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"FAIL: cannot read {CONTENT}: {exc}", file=sys.stderr)
        return 1
    errors = validate(records)
    try:
        word_records = json.loads(WORDS.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        errors.append(f"cannot read {WORDS}: {exc}")
        word_records = None
    if word_records is not None and isinstance(records, list):
        errors.extend(validate_words(records, word_records))
    if errors:
        print(f"FAIL: {len(errors)} issue(s) in {len(records) if isinstance(records, list) else 0} records")
        for error in errors:
            print(f"- {error}")
        return 1
    word_count = sum(len(record["words"]) for record in word_records)
    print(f"PASS: {len(records)} characters and {word_count} CC-CEDICT-backed words; first day begins 一二三人口; provenance and review flags valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
