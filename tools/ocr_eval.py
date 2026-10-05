"""Desktop OCR probe (Tesseract 5, same traineddata the app will ship). NOT a phone benchmark. Synthetic rendered text only."""
import sys, subprocess, difflib, glob, os
from PIL import Image, ImageDraw, ImageFont
TD = sys.argv[1]; OUT = sys.argv[2]
def font(pattern, size):
    f = sorted(glob.glob(pattern)); return ImageFont.truetype(f[0], size) if f else None
CODE = 'def total(items):\n    s = 0\n    for i in range(len(items)):\n        s += items[i] * 0.5  # O vs 0, l vs 1\n    return s if s != 0 else None\n'
S = {
 "eng-code": ("eng", "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", CODE),
 "ben": ("ben", "/usr/share/fonts/truetype/noto/NotoSansBengali-Regular.ttf", "জ্ঞান অর্জন করা সবার জন্য জরুরি।\nপ্রতি রবিবার গ্রন্থাগারে গল্প হয়।"),
 "ara": ("ara", "/usr/share/fonts/truetype/noto/NotoNaskhArabic-Regular.ttf", "العلم نور والجهل ظلام\nمن جد وجد ومن زرع حصد"),
 "urd": ("urd", "/usr/share/fonts/truetype/noto/NotoNaskhArabic-Regular.ttf", "علم حاصل کرنا ہر انسان کے لیے ضروری ہے\nکیونکہ علم سے ہی ترقی ممکن ہے"),
 "hin": ("hin", "/usr/share/fonts/truetype/noto/NotoSansDevanagari-Regular.ttf", "पुस्तकालय में हर रविवार को\nबच्चों के लिए कहानी होती है"),
}
lines = ["# OCR পরীক্ষা (ডেস্কটপ Tesseract 5.3.4, synthetic রেন্ডার করা লেখা; ফোন নয়)\n", f"traineddata: `{TD}`\n", "| নমুনা | অক্ষর-মিল (similarity) | OCR আউটপুট |\n|---|---|---|"]
for k, (lang, fp, text) in S.items():
    if not os.path.exists(fp): lines.append(f"| {k} | ফন্ট নেই | - |"); continue
    f = ImageFont.truetype(fp, 34); img = Image.new("L", (1100, 60 + 60 * (text.count("\n") + 1)), 255); d = ImageDraw.Draw(img)
    d.multiline_text((30, 25), text, font=f, fill=0, spacing=14); img.save(f"{k}.png")
    r = subprocess.run(["tesseract", f"{k}.png", "-", "-l", lang, "--tessdata-dir", TD, "--psm", "6"], capture_output=True, text=True).stdout
    norm = lambda s: "".join(s.split())
    sim = difflib.SequenceMatcher(None, norm(text), norm(r)).ratio()
    lines.append(f"| {k} | {sim:.2f} | `{r.strip()[:140]!r}` |")
open(OUT, "w").write("\n".join(lines) + "\n"); print("\n".join(lines))
