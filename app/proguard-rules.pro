# PdfBox-Android ports Apache PDFBox and loads fonts, filters and resources by name and
# reflection. Keep it whole; it also references desktop java.awt/javax classes that
# don't exist on Android.
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn java.awt.**
-dontwarn javax.**
-dontwarn org.bouncycastle.**

# ML Kit wires its pieces together by reflection (component registrars found through the
# manifest). AGP 9 runs R8 in full mode, which strips fields its own consumer rules don't
# cover: text recognition then fails with an NPE inside TextRecognition.getClient().
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-dontwarn com.google.mlkit.**

# Room and Compose ship their own consumer rules.
