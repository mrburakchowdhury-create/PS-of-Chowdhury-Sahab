#!/usr/bin/env python3
"""16 KB page-size check for native libraries. Usage: check_elf16k.py <app.apk | lib.so ...>
For an APK: every .so of every ABI must (1) be STORED (uncompressed) at an offset that is a multiple of 16384 (needed because extractNativeLibs=false),
(2) have all PT_LOAD p_align % 16384 == 0, and (3) have (p_vaddr + p_memsz) % 16384 == 0 for PT_GNU_RELRO. Exit 1 on any failure.
(Only 64-bit ABIs are meaningful for 16 KB devices; 32-bit ABIs are reported too.)"""
import os, subprocess, sys, tempfile, zipfile, struct

def elf_check(path):
    out = subprocess.run(["readelf", "-lW", path], capture_output=True, text=True).stdout
    loads, relro = [], None
    for l in out.splitlines():
        p = l.split()
        if not p: continue
        if p[0] == "LOAD": loads.append(int(p[-1], 16))
        if p[0] == "GNU_RELRO": relro = (int(p[2], 16) + int(p[5], 16)) % 16384
    ok_load = bool(loads) and all(a % 16384 == 0 for a in loads); ok_relro = relro in (0, None)
    return ok_load, ok_relro, sorted({hex(a) for a in loads}), relro

def zip_offsets(apk):
    """(name -> (data_offset, stored)) from the local headers."""
    res = {}
    with open(apk, "rb") as f, zipfile.ZipFile(apk) as z:
        for i in z.infolist():
            f.seek(i.header_offset); h = f.read(30); n, e = struct.unpack("<HH", h[26:30])
            res[i.filename] = (i.header_offset + 30 + n + e, i.compress_type == zipfile.ZIP_STORED)
    return res

bad = 0
for arg in sys.argv[1:]:
    if arg.endswith(".apk"):
        offs = zip_offsets(arg); tmp = tempfile.mkdtemp()
        with zipfile.ZipFile(arg) as z:
            for n in sorted(x for x in z.namelist() if x.startswith("lib/") and x.endswith(".so")):
                p = z.extract(n, tmp); off, stored = offs[n]
                zip_ok = stored and off % 16384 == 0
                a, b, al, r = elf_check(p); ok = zip_ok and a and b; bad += (not ok)
                print(("PASS" if ok else "FAIL"), n, f"zip stored={stored} offset%16384={off % 16384}", "LOAD", al, "RELRO end%16384 =", r)
    else:
        a, b, al, r = elf_check(arg); ok = a and b; bad += (not ok)
        print(("PASS" if ok else "FAIL"), arg, "LOAD", al, "RELRO end%16384 =", r)
sys.exit(1 if bad else 0)
