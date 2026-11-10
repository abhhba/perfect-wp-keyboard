"""Builds the word lists in app/src/main/assets.

    python tools/build_dictionaries.py <dir with the sources>

Sources (all under permissive licences):
  rawdict_utf16_65105_freq.txt  AOSP PinyinIME, Apache-2.0: words, readings and frequencies
  en_wordlist.combined.gz       AOSP LatinIME, Apache-2.0: English words and frequencies
  dict.txt.big                  jieba (fxsjy/jieba extra_dict), MIT: 580,000 words with their counts in a large corpus
  large_pinyin.txt              mozillazg/phrase-pinyin-data, MIT: the reading of 410,000 phrases
  pinyin.txt                    mozillazg/pinyin-data, MIT: the readings of every character

Output:
  zh.tsv  syllables joined by ' (ni'hao), the word, a score: (ln p + 30) * 2000, p the probability of the word
  en.tsv  the word, a frequency 1..255
"""
import gzip
import math
import os
import sys
import unicodedata
from collections import defaultdict

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")
TONES = {"ü": "v", "ǖ": "v", "ǘ": "v", "ǚ": "v", "ǜ": "v", "ń": "n", "ň": "n", "ǹ": "n", "ḿ": "m", "ê": "e", "ế": "e", "ề": "e"}


def toneless(p):
    p = "".join(TONES.get(ch, ch) for ch in p.lower())
    return "".join(ch for ch in unicodedata.normalize("NFD", p) if not unicodedata.combining(ch))


def simplified(word):
    try:
        word.encode("gb2312")
        return all("一" <= ch <= "鿿" for ch in word)
    except UnicodeEncodeError:
        return False


def chinese(src):
    # AOSP: frequency per (word, reading).
    aosp = defaultdict(float)
    for line in open(os.path.join(src, "rawdict_utf16_65105_freq.txt"), "rb").read().decode("utf-16").splitlines():
        parts = line.split()
        if len(parts) >= 4 and len(parts) - 3 == len(parts[0]):
            aosp[(parts[0], tuple(parts[3:]))] += float(parts[1])
    aosp_word = defaultdict(float)
    for (w, _), f in aosp.items():
        aosp_word[w] += f
    total_a = sum(aosp_word.values())

    # jieba: corpus counts.
    jieba = defaultdict(int)
    for line in open(os.path.join(src, "dict.txt.big"), encoding="utf-8"):
        parts = line.split()
        if len(parts) >= 2 and simplified(parts[0]):
            jieba[parts[0]] += int(parts[1])
    total_j = sum(jieba.values())

    # Readings of phrases and of characters.
    phrase = {}
    for line in open(os.path.join(src, "large_pinyin.txt"), encoding="utf-8"):
        if ":" not in line or line.startswith("#"):
            continue
        w, p = line.split(":", 1)
        syl = [toneless(x) for x in p.split()]
        if len(syl) == len(w.strip()):
            phrase[w.strip()] = tuple(syl)
    char_readings = {}
    for line in open(os.path.join(src, "pinyin.txt"), encoding="utf-8"):
        if line.startswith("#") or "#" not in line:
            continue
        head, ch = line.split("#", 1)
        ch = ch.strip()
        readings = []
        for r in head.split(":", 1)[1].strip().split(","):
            t = toneless(r.strip())
            if t and t not in readings:
                readings.append(t)
        if len(ch) == 1:
            char_readings[ch] = readings

    def logp(word):
        """The probability of a word: the mean of the two sources' where both know it; one source alone counts a little less."""
        a = aosp_word.get(word); j = jieba.get(word)
        la = math.log(a / total_a) if a else None
        lj = math.log(j / total_j) if j else None
        if la is not None and lj is not None:
            return (la + lj) / 2
        return (la if la is not None else lj) - 1.0

    rows = {}

    def add(word, syl, lp):
        key = ("'".join(syl), word)
        score = max(1, int(round((lp + 30.0) * 2000)))
        if rows.get(key, 0) < score:
            rows[key] = score

    words = set(w for (w, _) in aosp) | set(jieba)
    for w in words:
        if not simplified(w):
            continue
        lp = logp(w)
        if len(w) == 1:
            # A character's readings share its probability as AOSP shares it; else the first reading takes most.
            readings = [(syl, f) for (ww, syl), f in aosp.items() if ww == w] if w in aosp_word else []
            if readings:
                tot = sum(f for _, f in readings)
                for syl, f in readings:
                    add(w, syl, lp + math.log(f / tot))
            elif w in char_readings:
                rs = char_readings[w]
                for i, r in enumerate(rs):
                    add(w, (r,), lp + math.log(0.7 if i == 0 else 0.3 / max(1, len(rs) - 1)))
            continue
        readings = [syl for (ww, syl), _ in aosp.items() if ww == w] if w in aosp_word else []
        if not readings and w in phrase:
            readings = [phrase[w]]
        if not readings:
            # Read character by character only when no character has two readings.
            per = [char_readings.get(ch, []) for ch in w]
            if all(len(r) == 1 for r in per):
                readings = [tuple(r[0] for r in per)]
        for syl in readings:
            add(w, syl, lp)

    # Keep the commonest words: a keyboard's heap is small. Every character is kept.
    singles = {k: v for k, v in rows.items() if len(k[1]) == 1}
    multi = sorted(((k, v) for k, v in rows.items() if len(k[1]) > 1), key=lambda kv: -kv[1])[:LIMIT]
    out = sorted(list(singles.items()) + multi, key=lambda kv: (kv[0][0], -kv[1]))
    with open(os.path.join(OUT, "zh.tsv"), "w", encoding="utf-8", newline="\n") as fh:
        for (key, word), f in out:
            fh.write(f"{key}\t{word}\t{f}\n")
    return len(singles), len(multi)


LIMIT = 260000


def english(src):
    out = []
    with gzip.open(os.path.join(src, "en_wordlist.combined.gz"), "rt", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line.startswith("word="):
                continue
            fields = dict(p.split("=", 1) for p in line.split(",") if "=" in p)
            word = fields.get("word", "")
            if not word or "offensive" in fields.get("flags", "") or "possibly_offensive" in fields:
                continue
            if any(ch.isdigit() for ch in word) or " " in word:
                continue
            f = int(fields.get("f", "0") or 0)
            if f > 0:
                out.append((word, f))
    with open(os.path.join(OUT, "en.tsv"), "w", encoding="utf-8", newline="\n") as fh:
        for word, f in out:
            fh.write(f"{word}\t{f}\n")
    return len(out)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    print("zh", chinese(sys.argv[1]))
    print("en", english(sys.argv[1]))
