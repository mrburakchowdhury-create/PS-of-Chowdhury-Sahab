#!/usr/bin/env python3
"""Builds app/src/main/assets/dict/dictionary.json from Wiktionary data extracted by kaikki.org (CC BY-SA 4.0 / GFDL).

Inputs (downloaded by this script, not stored in the repo):
  * Bengali entries with English glosses  -> Bengali -> English, and (inverted) English -> Bengali
  * English entries that carry Bengali translations (only ~1000 in the extract) -> English -> Bengali, highest weight
Run:  python3 tools/build_dictionary.py   (needs network once; the APK itself never goes online)
"""
import json, re, sys, urllib.request, collections, os

BN_URL = "https://kaikki.org/dictionary/Bengali/kaikki.org-dictionary-Bengali.jsonl"
EN_URL = "https://kaikki.org/dictionary/English/kaikki.org-dictionary-English.jsonl"
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "dict", "dictionary.json")

CACHE = os.environ.get("KAIKKI_CACHE")   # optional folder: reuse an earlier download instead of fetching again
def lines(url):
    if CACHE and os.path.exists(os.path.join(CACHE, os.path.basename(url))):
        yield from open(os.path.join(CACHE, os.path.basename(url)), "rb"); return
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=300) as r:
        buf = b""
        for chunk in iter(lambda: r.read(1 << 20), b""):
            buf += chunk
            *ls, buf = buf.split(b"\n")
            for l in ls: yield l
        if buf.strip(): yield buf

def clean(g):
    g = re.sub(r"\(.*?\)", "", g); g = re.sub(r"\s+", " ", g).strip().rstrip(".")
    return g

en2bn = collections.defaultdict(lambda: collections.defaultdict(float))   # english -> bengali -> weight
bn2en = {}                                                                 # bengali -> [pos, [glosses]]
for l in lines(BN_URL):
    d = json.loads(l)
    w, pos = d.get("word"), d.get("pos")
    if not w or pos in ("suffix", "prefix", "infix", "character", "symbol"): continue
    glosses = []; common = min(len(d.get("senses", [])), 5) * 0.1   # more senses ~ a more common word: breaks ties in its favour
    for s in d.get("senses", []):
        for g in s.get("glosses", [])[:1]:
            g = clean(g)
            if not g: continue
            glosses.append(g[:90])
            parts = [p.strip() for p in re.split(r"[;,]", g) if p.strip()]
            for p in parts:
                n = len(p.split())
                if n == 1: en2bn[p.lower()][w] += (3 if len(parts) == 1 else 2) + common
                elif n == 2 and not p.lower().startswith(("a ", "an ", "the ", "of ", "to ")): en2bn[p.lower()][w] += 1 + common
                elif n == 2 and p.lower().startswith("to "): en2bn[p.lower()[3:]][w] += 2 + common   # verbs: "to eat" -> eat
    if glosses: bn2en.setdefault(w, [pos, []])[1].extend(g for g in glosses[:4] if g not in bn2en.get(w, [pos, []])[1])
for l in lines(EN_URL):
    if b'"lang_code": "bn"' not in l: continue
    d = json.loads(l)
    for t in d.get("translations", []):
        if t.get("lang_code") == "bn" and t.get("word"): en2bn[d["word"].lower()][t["word"]] += 5
core = {}   # hand-picked first meanings for the most common words in the bundled books (tools/core_en_bn.tsv); they come BEFORE the Wiktionary ones
for l in open(os.path.join(os.path.dirname(__file__), "core_en_bn.tsv"), encoding="utf8"):
    if "\t" not in l: continue
    k, v = l.rstrip("\n").split("\t", 1); k = k.strip().lower()
    if "'" in k or " " in k or not k: continue
    core[k] = [x.strip() for x in v.split(";") if x.strip()]
out = {"en": {}, "bn": {}, "core": sorted(core)}
for e, m in en2bn.items():
    if not re.fullmatch(r"[a-z][a-z' -]{0,30}", e): continue
    out["en"][e] = [b for b, _ in sorted(m.items(), key=lambda x: (-x[1], len(x[0])))[:8]]
for k, v in core.items():
    rest = [b for b in out["en"].get(k, []) if b not in v]
    out["en"][k] = (v + rest)[:8]
for b, (pos, gl) in bn2en.items(): out["bn"][b] = {"p": pos, "g": gl[:4]}
os.makedirs(os.path.dirname(OUT), exist_ok=True)
json.dump(out, open(OUT, "w", encoding="utf8"), ensure_ascii=False, separators=(",", ":"))
print("en keys", len(out["en"]), "bn keys", len(out["bn"]), "bytes", os.path.getsize(OUT))
