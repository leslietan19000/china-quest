#!/usr/bin/env python3
"""Validate the seed character content and its source/review metadata."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / "content" / "characters.json"
REQUIRED = {
    "id", "character", "pinyin", "meaning_zh", "meaning_es_optional",
    "meaning_en", "radical", "stroke_count", "common_words",
    "example_sentence", "confusable_characters", "difficulty", "topic",
    "source_status", "source", "review_status",
}
CORE_FACT_FIELDS = ("pinyin", "meaning_en", "radical", "stroke_count")


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
    if errors:
        print(f"FAIL: {len(errors)} issue(s) in {len(records) if isinstance(records, list) else 0} records")
        for error in errors:
            print(f"- {error}")
        return 1
    print(f"PASS: {len(records)} records; sourced core fields complete; first day begins 一二三人口; provenance and review flags valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
