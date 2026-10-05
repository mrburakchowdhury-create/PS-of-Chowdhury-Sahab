# PDFBox-Android: optional JPEG2000 decoder library is not bundled (pages whose scans are JPX will fail and be reported as FAILED, not guessed).
-dontwarn com.gemalto.jp2.**
# JNI-bound classes must keep names/members for native lookup.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.stream.**
