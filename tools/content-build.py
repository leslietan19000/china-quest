#!/usr/bin/env python3
"""Build the reviewed-facts character seed from the pinned Unicode 17.0.0 data."""

from __future__ import annotations

import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UNIHAN = ROOT / "tools" / "content-unihan-17.0.0.zip"
RADICALS = ROOT / "tools" / "content-cjkradicals-17.0.0.txt"
OUTPUT = ROOT / "content" / "characters.json"

UNIHAN_URL = "https://www.unicode.org/Public/17.0.0/ucd/Unihan.zip"
RADICALS_URL = "https://www.unicode.org/Public/17.0.0/ucd/CJKRadicals.txt"
LICENSE_URL = "https://www.unicode.org/terms_of_use.html"
UNIHAN_SHA256 = "F7A48B2B545ACFAA77B2D607AE28747404CE02BAEFEE16396C5D2D7A8EF34B5E"
RADICALS_SHA256 = "826F83BE25CD18FB8A5015A514704504E1982E840EA14D058BF583E1CC620C83"

# The sequence is a starter curriculum, not a claim of official grade order.
# Day one starts with 一、二、三、人、口 as requested.
CURRICULUM = [
    ("numbers", "一二三人口四五六七八九十百千"),
    ("family", "我你他她们男女父母家爷奶哥姐弟妹朋友名字好"),
    ("nature", "日月水火山木土石田雨风云天星空大中小"),
    ("body", "身手目耳鼻头发心足牙口"),
    ("animals", "猫狗鸟鱼马牛羊鸡鸭虫兔"),
    ("food", "米饭面茶水果苹果菜肉蛋奶糖包吃喝"),
    ("school", "学校学生老师书本字笔纸桌椅门课读写"),
    ("actions", "走跑跳看听说睡玩坐站来去上下开关洗穿"),
    ("city_travel", "城市场路车公交地铁站店钱买卖住医院医药公园中国智利飞机船海"),
    ("daily_life", "电话手机衣服天气时间星期今天明天早晚左边右边前后里外红绿黑白颜色工作周末问题答案"),
]

WORD_SUGGESTIONS = {
    "一": ["一个", "一天"], "二": ["二月", "第二"], "三": ["三个", "三天"],
    "人": ["大人", "家人"], "口": ["人口", "门口"], "日": ["日子", "生日"],
    "月": ["月亮", "一月"], "水": ["开水", "水果"], "火": ["火车", "火山"],
    "山": ["大山", "山水"], "木": ["木头", "木马"], "家": ["大家", "回家"],
    "父": ["父母", "父亲"], "母": ["母亲", "父母"], "女": ["女人", "女儿"],
    "子": ["孩子", "儿子"], "口": ["人口", "门口"], "大": ["大小", "大家"],
    "小": ["大小", "小孩"], "中": ["中国", "中间"], "上": ["上学", "上面"],
    "下": ["下面", "下雨"], "学": ["学生", "学校"], "生": ["学生", "生日"],
    "校": ["学校", "校门"], "书": ["书本", "看书"], "字": ["汉字", "写字"],
    "手": ["手机", "手心"], "目": ["目光", "耳目"], "耳": ["耳朵", "耳机"],
    "心": ["开心", "小心"], "足": ["足球", "满足"], "猫": ["小猫", "猫咪"],
    "狗": ["小狗", "狗狗"], "鸟": ["小鸟", "鸟儿"], "鱼": ["小鱼", "鱼肉"],
    "米": ["大米", "米饭"], "饭": ["米饭", "吃饭"], "茶": ["茶水", "喝茶"],
    "果": ["水果", "苹果"], "菜": ["青菜", "白菜"], "肉": ["牛肉", "肉包"],
    "蛋": ["鸡蛋", "蛋白"], "奶": ["牛奶", "奶茶"], "车": ["火车", "汽车"],
    "路": ["马路", "路口"], "书": ["看书", "书包"], "雨": ["下雨", "雨水"],
    "风": ["大风", "风车"], "云": ["白云", "云朵"], "天": ["今天", "明天"],
    "好": ["你好", "好人"], "我": ["我们", "我的"], "你": ["你好", "你们"],
    "他": ["他们", "他的"], "她": ["她们", "她的"], "们": ["我们", "你们"],
    "朋友": ["好朋友", "朋友们"],
}

EXAMPLES = {
    "一": "我有一个苹果。", "二": "我家有两个人。", "三": "今天是星期三。",
    "人": "这里有很多人。", "口": "请张开口。", "日": "今天是晴天。",
    "月": "今晚的月亮很亮。", "水": "请喝一杯水。", "火": "火车来了。",
    "山": "我们去看山。", "家": "我和家人在家。", "我": "我喜欢读书。",
    "你": "你好！", "他": "他在学校。", "她": "她喜欢画画。",
    "猫": "小猫在睡觉。", "狗": "小狗在跑。", "鱼": "鱼在水里游。",
    "书": "我在看书。", "学": "我们一起学习。", "吃": "我们一起吃饭。",
    "走": "我们走回家。", "车": "汽车停在路边。", "雨": "今天下雨了。",
}

TOPIC_ORDER = {topic: index for index, (topic, _) in enumerate(CURRICULUM)}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def read_unihan() -> dict[str, dict[str, str]]:
    if sha256(UNIHAN) != UNIHAN_SHA256:
        raise ValueError("Unihan archive SHA-256 does not match the pinned source record")
    if sha256(RADICALS) != RADICALS_SHA256:
        raise ValueError("CJKRadicals SHA-256 does not match the pinned source record")
    data: dict[str, dict[str, str]] = {}
    with zipfile.ZipFile(UNIHAN) as archive:
        for file_name in ("Unihan_Readings.txt", "Unihan_IRGSources.txt"):
            for line in archive.read(file_name).decode("utf-8").splitlines():
                if not line or line.startswith("#"):
                    continue
                codepoint, field, value = line.split("\t", 2)
                if field in {"kMandarin", "kDefinition", "kRSUnicode", "kTotalStrokes"}:
                    data.setdefault(chr(int(codepoint[2:], 16)), {})[field] = value
    return data


def read_radical_map() -> dict[str, str]:
    mapping: dict[str, str] = {}
    for line in RADICALS.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        number, _kangxi_code, ideograph_code = [part.strip() for part in line.split(";")]
        mapping[number] = chr(int(ideograph_code, 16))
    return mapping


def clean_definition(value: str | None) -> str | None:
    if not value:
        return None
    return value.split("<", 1)[0].strip() or None


def build() -> list[dict[str, object]]:
    data = read_unihan()
    radical_map = read_radical_map()
    characters: list[tuple[str, str]] = []
    seen: set[str] = set()
    for topic, sequence in CURRICULUM:
        for character in sequence:
            if character not in seen:
                seen.add(character)
                characters.append((character, topic))

    records = []
    for index, (character, topic) in enumerate(characters, start=1):
        props = data.get(character, {})
        rs_value = props.get("kRSUnicode")
        radical = None
        if rs_value:
            match = re.match(r"([0-9]+'{0,3})\.", rs_value.split()[0])
            if match:
                radical = radical_map.get(match.group(1))
        code = f"{ord(character):04X}"
        records.append({
            "id": f"hanzi-{code}",
            "character": character,
            "pinyin": props.get("kMandarin"),
            "meaning_zh": None,
            "meaning_es_optional": None,
            "meaning_en": clean_definition(props.get("kDefinition")),
            "radical": radical,
            "stroke_count": int(props["kTotalStrokes"]) if props.get("kTotalStrokes", "").isdigit() else None,
            "common_words": WORD_SUGGESTIONS.get(character, []),
            "example_sentence": EXAMPLES.get(character),
            "confusable_characters": [],
            "difficulty": min(10, 1 + (index - 1) // 15),
            "topic": topic,
            "source_status": "UNICODE_17_FACTS_VERIFIED; PEDAGOGICAL_FIELDS_NEEDS_REVIEW",
            "source": {
                "url": UNIHAN_URL,
                "version": "Unicode 17.0.0 / UCD 17.0.0",
                "fields": {
                    "pinyin": "Unihan_Readings.txt:kMandarin",
                    "meaning_en": "Unihan_Readings.txt:kDefinition",
                    "radical": "Unihan_IRGSources.txt:kRSUnicode; CJKRadicals.txt mapping",
                    "stroke_count": "Unihan_IRGSources.txt:kTotalStrokes",
                },
                "sha256": UNIHAN_SHA256,
                "radical_mapping_url": RADICALS_URL,
                "radical_mapping_sha256": RADICALS_SHA256,
                "license_url": LICENSE_URL,
            },
            "review_status": "NEEDS_REVIEW",
        })
    return records


if __name__ == "__main__":
    records = build()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(records)} sourced character records to {OUTPUT}")
