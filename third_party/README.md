# third_party — natives rebuilt from source for 16 KB page-size devices

Android 15+/16 devices can run with 16 KB memory pages. The published Tesseract4Android and androidx.graphics-path libraries have `LOAD` segments aligned to 16 KB but a
`GNU_RELRO` segment that ends on a 4 KB boundary (check: `(VirtAddr + MemSiz) % 16384 != 0`). We therefore build those native libraries from source with
`-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384` using NDK r29. Nothing was patched in the ELF headers and nothing was only "zipaligned".

| Library | What | Source | Toolchain |
|---|---|---|---|
| `tesseract4android/tesseract4android-4.9.0-ps16k.aar` | libtesseract, libleptonica, libjpeg, libpngx + Java wrapper (arm64-v8a, x86_64) | https://github.com/adaptech-cz/Tesseract4Android tag **4.9.0** (commit `15c534717b1cb58261b58d4e4c1200c7f81f668c`), Apache-2.0, plus `16kb-build.patch` (NDK version + linker flags only; no code change) | NDK 29.0.14206865, CMake 3.22.1, AGP 8.10.1 / Gradle 8.11.1 (the project's own) |
| `graphics-path/` → `app/src/main/jniLibs/*/libandroidx.graphics.path.so` | AndroidX `graphics-path` JNI (path conic→quad, used by Compose on API < 34) | AOSP `platform/frameworks/support` commit in `graphics-path/SOURCE_COMMIT`, directory `graphics/graphics-path/src/main/cpp`, Apache-2.0; compile flags copied from that module's `build.gradle` | NDK 29.0.14206865 clang |

The app prefers its own `jniLibs/libandroidx.graphics.path.so` over the one inside the Maven artifact (`packaging.jniLibs.pickFirsts`; checked by `tools/check_apk.py`/CI).
The rebuilt graphics-path library has the same JNI surface as the released 1.1.0 binary (same exported symbol `JNI_OnLoad`, same registered class/method signatures).

Rebuild and verify: `tools/rebuild_native_16k.sh` then `python3 tools/check_elf16k.py <apk or .so files>`.
Licences: `tesseract4android/LICENSE.Tesseract4Android` (Apache-2.0); the C++ sources in `graphics-path/` carry their own Apache-2.0 headers.
