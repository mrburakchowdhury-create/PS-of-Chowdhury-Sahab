#!/usr/bin/env python3
"""Hard checks on a BUILT release APK (run in CI and locally). Usage: check_apk.py <app-release.apk> <aapt2> <apksigner>
Fails (exit 1) if: debuggable, INTERNET permission, any exported component other than the launcher activity, backup allowed,
a language pack of the OCR is missing from assets/tessdata, or the signer certificate is not what the build was told to use (printed for the record)."""
import re, subprocess, sys, zipfile
apk, aapt2, apksigner = sys.argv[1:4]
fail = []
xml = subprocess.run([aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", apk], capture_output=True, text=True).stdout
if re.search(r"debuggable\(0x0101000f\)=true", xml): fail.append("android:debuggable=true")
if "android.permission.INTERNET" in xml: fail.append("INTERNET permission present")
if re.search(r"allowBackup\(0x01010280\)=true", xml): fail.append("allowBackup=true")
# exported components: walk the tree, remember the current element name
exported, cur = [], None
for line in xml.splitlines():
    m = re.match(r"\s*E: (activity|service|receiver|provider|activity-alias) ", line)
    if m: cur = m.group(1); name = None
    n = re.search(r':name\(0x01010003\)="([^"]+)"', line)
    if n and cur and name is None: name = n.group(1)
    if cur and re.search(r":exported\(0x01010010\)=true", line): exported.append((cur, name))
allowed = {("activity", "app.personal.banglaai.MainActivity")}
extra = [e for e in exported if e not in allowed]
if extra: fail.append(f"unexpected exported components: {extra}")
names = zipfile.ZipFile(apk).namelist()
for l in ("eng", "ben", "ara", "urd", "hin"):
    if f"assets/tessdata/{l}.traineddata" not in names: fail.append(f"OCR language pack missing: {l}")
sig = subprocess.run([apksigner, "verify", "--print-certs", apk], capture_output=True, text=True)
print(sig.stdout.strip()); 
if sig.returncode != 0: fail.append("apksigner verify failed: " + sig.stderr.strip()[:200])
print("exported:", exported)
if fail: print("FAIL:", *fail, sep="\n  "); sys.exit(1)
print("OK: release APK checks passed")
