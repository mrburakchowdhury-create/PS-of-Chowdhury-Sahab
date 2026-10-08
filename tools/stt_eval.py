"""Speech-to-text check of the AI model (Gemma 4 E4B, desktop CPU). Clips: 16 kHz mono WAV + reference text in refs.json (see docs/STT_EVAL.md for the source).
Usage: python3 tools/stt_eval.py <folder with refs.json and the wavs> [noise_dB]"""
import json, os, subprocess, sys, time, litert_lm
folder = sys.argv[1]; noise = float(sys.argv[2]) if len(sys.argv) > 2 else None
refs = json.load(open(os.path.join(folder, "refs.json")))
def edit(a, b):
    n, m = len(a), len(b); d = list(range(m + 1))
    for i in range(1, n + 1):
        p = d[:]; d[0] = i
        for j in range(1, m + 1): d[j] = min(p[j] + 1, d[j - 1] + 1, p[j - 1] + (a[i - 1] != b[j - 1]))
    return d[m]
def clip(f):
    path = os.path.join(folder, f)
    if noise is None: return path
    out = path.replace(".wav", f"_n{int(noise)}.wav")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-f", "lavfi", "-i", "anoisesrc=color=white:amplitude=0.05", "-filter_complex", f"[1:a]volume={noise}dB[n];[0:a][n]amix=inputs=2:duration=first:normalize=0", "-ar", "16000", "-ac", "1", out], check=True)
    return out
INSTR = "এই অডিওতে যা বলা হয়েছে হুবহু বাংলা ভাষায় লিখে দাও। শুধু বলা কথাটুকু লেখো, অন্য কিছু (ব্যাখ্যা, অনুবাদ, উদ্ধৃতি-চিহ্ন) নয়। কিছু বোঝা না গেলে কিছুই লিখো না।"
rows = []; tot_e = tot_n = 0
with litert_lm.Engine("/opt/models/gemma-4-E4B-it.litertlm", backend=litert_lm.Backend.CPU(), audio_backend=litert_lm.Backend.CPU()) as e:
    for r in refs:
        data = open(clip(r["f"]), "rb").read(); t0 = time.time()
        with e.create_conversation() as c:
            resp = c.send_message({"role": "user", "content": [litert_lm.Content.AudioBytes(data).to_json(), {"type": "text", "text": INSTR}]})
        hyp = (resp["content"][0]["text"] if isinstance(resp, dict) else str(resp)).strip().strip('"“”')
        err = edit(r["text"], hyp); tot_e += err; tot_n += len(r["text"])
        rows.append((r["text"], hyp, err / max(len(r["text"]), 1), time.time() - t0)); print(rows[-1], flush=True)
print(f"TOTAL CER {tot_e / tot_n:.3f} over {len(refs)} clips")
with open(os.path.join(os.path.dirname(__file__), "..", "docs", "STT_EVAL.md"), "a") as f:
    f.write(f"\n## Run: noise={'none' if noise is None else str(noise)+' dB white noise'}  —  CER {tot_e / tot_n:.3f} over {len(refs)} clips\n\n")
    for ref, hyp, cer, t in rows: f.write(f"- REF: {ref}\n  HYP: {hyp}\n  CER {cer:.2f}, {t:.1f}s\n")
