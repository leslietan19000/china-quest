#!/usr/bin/env python3
"""Build curated beginner words from a pinned CC-CEDICT snapshot.

The list below is a human-curated teaching selection. The source file supplies
the spelling, reading, and English dictionary gloss; no translation is guessed.
"""

from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools" / "content-cedict-mirror.u8"
CHARACTERS = ROOT / "content" / "characters.json"
OUTPUT = ROOT / "content" / "words.json"
SOURCE_SHA256 = "A429F52B5F411B8922A7A772DA3096E10F1A0F167132B7E410618B72A48E0390"

# Character order comes exclusively from characters.json. Every selected word
# contains its target character and must have an exact CC-CEDICT entry.
SELECTIONS = """
一:一月,一起
二:二月,第二
三:三月,三十
人:大人,人们
口:门口,口水
四:四月,四季
五:五月,五十
六:六月,星期六
七:七月,七夕
八:八月,八十
九:九月,九十
十:十月,十分
百:百年,百万
千:千米,千万
我:我们,自我介绍
你:你好,你们
他:他们,其他
她:她们
们:我们,你们
男:男孩,男人
女:女孩,女人
父:父母,父亲
母:母亲,父母
家:家人,大家
爷:爷爷,老爷爷
奶:奶奶,牛奶
哥:哥哥,大哥
姐:姐姐,大姐
弟:弟弟,兄弟
妹:妹妹,姐妹
朋:朋友
友:朋友,友好
名:名字,有名
字:名字,汉字
好:你好,好吃
日:日子,生日
月:月亮,月饼
水:水果,开水
火:火车,火山
山:高山,火山
木:木材,木马
土:土地,泥土
石:石头,石块
田:田地,稻田
雨:下雨,雨伞
风:大风,风车
云:白云,云朵
天:今天,明天
星:星星,星期
空:天空,空气
大:大小,大家
中:中国,中间
小:小孩,小猫
身:身体,身高
手:手机,洗手
目:目光,节目
耳:耳朵,耳机
鼻:鼻子,鼻孔
头:头发,点头
发:头发,发现
心:开心,小心
足:足球,足够
牙:牙齿,刷牙
猫:小猫,猫咪
狗:小狗,狗熊
鸟:小鸟,飞鸟
鱼:金鱼,鱼肉
马:马路,骑马
牛:牛奶,牛肉
羊:山羊,羊肉
鸡:鸡蛋,小鸡
鸭:鸭子,烤鸭
虫:虫子,昆虫
兔:兔子,兔年
米:米饭,大米
饭:吃饭,米饭
面:面包,面条
茶:喝茶,茶叶
果:水果,苹果
苹:苹果
菜:青菜,白菜
肉:牛肉,鸡肉
蛋:鸡蛋,蛋糕
糖:白糖,糖果
包:书包,面包
吃:吃饭,好吃
喝:喝茶,好喝
学:学校,学生
校:学校,校长
生:生日,学生
老:老师,老人
师:老师,教师
书:看书,书包
本:书本,本子
笔:铅笔,毛笔
纸:纸巾,纸张
桌:桌子,书桌
椅:椅子,轮椅
门:大门,门口
课:上课,课本
读:读书,阅读
写:写字,写作
走:走路,走开
跑:跑步,跑道
跳:跳舞,跳高
看:看书,看见
听:听见,听话
说:说话,听说
睡:睡觉,睡衣
玩:玩具,好玩
坐:坐下,坐车
站:车站,站起来
来:回来,过来
去:回去,去年
上:上学,上面
下:下面,下雨
开:开门,开心
关:关门,关心
洗:洗手,洗澡
穿:穿过,穿上
城:城市,长城
市:城市,市场
场:市场,操场
路:马路,走路
车:汽车,火车
公:公园,公交车
交:公交车,交通
地:地方,地图
铁:地铁,铁路
店:商店,书店
钱:花钱,零钱
买:买东西,买卖
卖:买卖,卖出
住:住房,住院
医:医生,医院
院:医院,院子
药:吃药,药店
园:公园,花园
国:中国,国家
智:智慧,智力
利:便利,有利
飞:飞机,飞鸟
机:飞机,手机
船:小船,船票
海:大海,海水
电:电话,电视
话:说话,电话
衣:衣服,睡衣
服:衣服,校服
气:天气,空气
时:时间,小时
间:时间,中间
期:星期,假期
今:今天,今年
明:明天,明白
早:早上,早饭
晚:晚上,晚饭
左:左边,左手
边:左边,右边
右:右边,右手
前:前面,以前
后:后面,以后
里:里面,公里
外:外面,外国
红:红色,红灯
绿:绿色,绿茶
黑:黑色,黑板
白:白色,白菜
颜:颜色,颜料
色:颜色,红色
工:工作,工人
作:工作,作业
周:周末,周围
末:周末,末尾
问:问题,问好
题:问题,题目
答:答案,回答
案:答案,图案
"""

ENTRY_RE = re.compile(r"^(\S+) (\S+) \[([^]]+)\] /(.+)/$")
# Explicitly choose the everyday sense when the same spelling has several
# dictionary entries (including capitalized place names).
PREFERRED = {
    "女人": ("nu:3 ren2", "woman"),
    "友好": ("you3 hao3", "friendly"),
    "好吃": ("hao3 chi1", "tasty"),
    "土地": ("tu3 di4", "land"),
    "白云": ("bai2 yun2", "white cloud"),
    "大门": ("da4 men2", "entrance"),
    "好玩": ("hao3 wan2", "amusing"),
    "过来": ("guo4 lai2", "to come over"),
    "下面": ("xia4 mian4", "below"),
    "地方": ("di4 fang5", "area"),
}
PREFERRED_GLOSS = {
    "兔子": "rabbit",
    "校长": "headmaster",
    "交通": "traffic",
    "本子": "notebook",
}
TONE_MARKS = {
    "a": "āáǎà", "e": "ēéěè", "i": "īíǐì", "o": "ōóǒò",
    "u": "ūúǔù", "ü": "ǖǘǚǜ", "A": "ĀÁǍÀ", "E": "ĒÉĚÈ",
    "I": "ĪÍǏÌ", "O": "ŌÓǑÒ", "U": "ŪÚǓÙ", "Ü": "ǕǗǙǛ",
}


def marked_syllable(value: str) -> str:
    match = re.fullmatch(r"([A-Za-züÜvV:]+)([1-5])", value)
    if not match:
        raise ValueError(f"unsupported CC-CEDICT pinyin syllable: {value}")
    letters, tone = match.groups()
    letters = letters.replace("u:", "ü").replace("U:", "Ü").replace("v", "ü").replace("V", "Ü")
    if tone == "5":
        return letters
    lower = letters.lower()
    if "a" in lower:
        index = lower.index("a")
    elif "e" in lower:
        index = lower.index("e")
    elif "ou" in lower:
        index = lower.index("o")
    else:
        index = max(i for i, char in enumerate(lower) if char in "aeiouü")
    return letters[:index] + TONE_MARKS[letters[index]][int(tone) - 1] + letters[index + 1:]


def marked_pinyin(value: str) -> str:
    return " ".join(marked_syllable(syllable) for syllable in value.split())


def selections() -> dict[str, list[str]]:
    result: dict[str, list[str]] = {}
    for line in SELECTIONS.strip().splitlines():
        character, words = line.split(":", 1)
        result[character] = words.split(",")
    return result


def build() -> list[dict[str, object]]:
    digest = hashlib.sha256(SOURCE.read_bytes()).hexdigest().upper()
    if digest != SOURCE_SHA256:
        raise ValueError(f"CC-CEDICT source hash mismatch: {digest}")
    entries: dict[str, list[tuple[str, str]]] = {}
    for line in SOURCE.read_text(encoding="utf-8-sig").splitlines():
        match = ENTRY_RE.match(line)
        if match:
            traditional, simplified, pinyin, meanings = match.groups()
            entries.setdefault(simplified, []).append((pinyin, meanings))
    selected = selections()
    records = json.loads(CHARACTERS.read_text(encoding="utf-8"))
    if set(selected) != {r["character"] for r in records}:
        missing = {r["character"] for r in records} - set(selected)
        extra = set(selected) - {r["character"] for r in records}
        raise ValueError(f"selection coverage mismatch: missing={missing}, extra={extra}")
    output = []
    for record in records:
        character = record["character"]
        words = []
        for text in selected[character]:
            choices = entries.get(text, [])
            if text in PREFERRED:
                preferred_pinyin, preferred_gloss = PREFERRED[text]
                choices = [choice for choice in choices if choice[0] == preferred_pinyin and choice[1].startswith(preferred_gloss)]
            if not choices:
                raise ValueError(f"CC-CEDICT entry absent: {character}: {text}")
            if len(choices) != 1:
                raise ValueError(f"CC-CEDICT entry ambiguous: {character}: {text}: {choices}")
            pinyin, meanings = choices[0]
            senses = meanings.split("/")
            meaning = PREFERRED_GLOSS.get(text, senses[0])
            if meaning not in senses:
                raise ValueError(f"selected sense absent in CC-CEDICT: {text}: {meaning}")
            pinyin = pinyin.lower()
            words.append({
                "text": text,
                "pinyin": marked_pinyin(pinyin),
                "pinyin_numbered": pinyin,
                "meaning_en": meaning,
                "source_status": "SOURCED_VERIFIED",
            })
        output.append({"id": record["id"], "character": character, "words": words})
    return output


if __name__ == "__main__":
    output = build()
    OUTPUT.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(output)} character records and {sum(len(r['words']) for r in output)} words to {OUTPUT}")
