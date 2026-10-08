#!/usr/bin/env bash
# Rebuilds the two natively-built third-party pieces for 16 KB page-size devices (see third_party/README.md). Needs: Android SDK with NDK 29.0.14206865 + CMake 3.22.1, git, JDK 17+.
set -euo pipefail
: "${ANDROID_HOME:?set ANDROID_HOME}"; ROOT="$(cd "$(dirname "$0")/.." && pwd)"; W="$(mktemp -d)"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --install "ndk;29.0.14206865" "cmake;3.22.1"
# 1) Tesseract4Android 4.9.0 from source, patched only for NDK version + linker flags
git clone --branch 4.9.0 https://github.com/adaptech-cz/Tesseract4Android.git "$W/t4a"
( cd "$W/t4a" && git apply "$ROOT/third_party/tesseract4android/16kb-build.patch" && echo "sdk.dir=$ANDROID_HOME" > local.properties && ./gradlew :tesseract4android:assembleStandardRelease --no-daemon )
cp "$W/t4a/tesseract4android/build/outputs/aar/tesseract4android-standard-release.aar" "$ROOT/third_party/tesseract4android/tesseract4android-4.9.0-ps16k.aar"
# 2) androidx graphics-path JNI from the vendored sources (flags copied from the module's build.gradle)
CLANG="$ANDROID_HOME/ndk/29.0.14206865/toolchains/llvm/prebuilt/linux-x86_64/bin"; S="$ROOT/third_party/graphics-path"
for pair in aarch64:arm64-v8a x86_64:x86_64; do T=${pair%%:*}; D=${pair##*:}; mkdir -p "$ROOT/app/src/main/jniLibs/$D"
  "$CLANG/${T}-linux-android31-clang++" -shared -fPIC -O3 -std=c++17 -Wno-unused-command-line-argument -fno-exceptions -fno-unwind-tables -fno-asynchronous-unwind-tables -fno-rtti \
    -ffast-math -ffp-contract=fast -fvisibility-inlines-hidden -fvisibility=hidden -fomit-frame-pointer -ffunction-sections -fdata-sections \
    "$S/Conic.cpp" "$S/PathIterator.cpp" "$S/pathway.cpp" -o "$ROOT/app/src/main/jniLibs/$D/libandroidx.graphics.path.so" \
    -Wl,--hash-style=both -Wl,-Bsymbolic-functions -Wl,--gc-sections -Wl,--version-script="$S/libandroidx.graphics.path.map" -nostdlib++ \
    -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 -Wl,-soname,libandroidx.graphics.path.so -s
done
python3 "$ROOT/tools/check_elf16k.py" "$ROOT"/app/src/main/jniLibs/*/*.so
echo "Now run ./gradlew assembleRelease and: python3 tools/check_elf16k.py app/build/outputs/apk/release/app-release.apk"
