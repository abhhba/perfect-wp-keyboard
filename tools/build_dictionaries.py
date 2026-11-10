"""Builds the word lists in app/src/main/assets from the Android Open Source Project data (Apache-2.0).

    python tools/build_dictionaries.py <rawdict_utf16_65105_freq.txt> <en_wordlist.combined.gz>

Sources (both Apache License 2.0, Copyright The Android Open Source Project):
  platform/packages/inputmethods/PinyinIME   jni/data/rawdict_utf16_65105_freq.txt
  platform/packages/inputmethods/LatinIME    dictionaries/en_wordlist.combined.gz

Output:
  zh.tsv  syllables joined by ' (e.g. ni'hao), the word, a frequency 1..65535 (log scale)
  en.tsv  the word, a frequency 1..255
"""
import gzip
import math
import os
import sys

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")


def chinese(path):
    rows = {}
    text = open(path, "rb").read().decode("utf-16")
    for line in text.splitlines():
        parts = line.split()
        if len(parts) < 4:
            continue
        word, freq, _flag, syllables = parts[0], float(parts[1]), parts[2], parts[3:]
        if len(syllables) != len(word):
            continue
        key = "'".join(syllables)
        # A log scale keeps the order and fits in a small number.
        f = max(1, min(65535, int(round(math.log(freq + 1.0) * 4000))))
        if rows.get((key, word), 0) < f:
            rows[(key, word)] = f
    out = sorted(rows.items(), key=lambda kv: (kv[0][0], -kv[1]))
    with open(os.path.join(OUT, "zh.tsv"), "w", encoding="utf-8", newline="\n") as fh:
        for (key, word), f in out:
            fh.write(f"{key}\t{word}\t{f}\n")
    return len(out)


def english(path):
    out = []
    with gzip.open(path, "rt", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line.startswith("word="):
                continue
            fields = dict(p.split("=", 1) for p in line.split(",") if "=" in p)
            word = fields.get("word", "")
            flags = fields.get("flags", "")
            if not word or "offensive" in flags or "possibly_offensive" in fields:
                continue
            if any(ch.isdigit() for ch in word) or " " in word:
                continue
            f = int(fields.get("f", "0") or 0)
            if f <= 0:
                continue
            out.append((word, f))
    with open(os.path.join(OUT, "en.tsv"), "w", encoding="utf-8", newline="\n") as fh:
        for word, f in out:
            fh.write(f"{word}\t{f}\n")
    return len(out)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    print("zh", chinese(sys.argv[1]))
    print("en", english(sys.argv[2]))
