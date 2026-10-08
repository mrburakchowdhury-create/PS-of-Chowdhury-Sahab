"""Runs the prompts exported by LibraryEvalExportTest through the REAL local model (desktop CPU, not a phone) and writes a readable report.
Usage: python3 tools/eval_library.py  (needs: pip install litert-lm; the model file at /opt/models or $MODEL)."""
import json, os, sys, time
import litert_lm
MODEL = os.environ.get("MODEL", "/opt/models/gemma-4-E4B-it.litertlm")
items = json.load(open(os.path.join(os.path.dirname(__file__), "..", "app", "build", "eval", "library_prompts.json")))
out = ["# Library / translation / word evaluation (desktop CPU, real model; NOT a phone)\n"]
with litert_lm.Engine(MODEL, backend=litert_lm.Backend.CPU) as e:
    for it in items:
        t0 = time.time()
        with e.create_conversation(system_message=it["system"]) as c:
            r = c.send_message(it["prompt"]); txt = r["content"][0]["text"] if isinstance(r, dict) else str(r)
        out.append(f"## {it['kind']}: {it.get('q', '')}\n- sources: {'; '.join(it['sources'])}\n- time: {time.time() - t0:.1f}s\n\n{txt}\n")
        print(out[-1], flush=True)
open(os.path.join(os.path.dirname(__file__), "..", "docs", "LIBRARY_EVAL.md"), "w").write("\n".join(out))
