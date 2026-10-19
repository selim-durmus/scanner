# PdfBox-Android ports Apache PDFBox and loads fonts, filters and resources by name and
# reflection. Keep it whole; it also references desktop java.awt/javax classes that
# don't exist on Android.
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn java.awt.**
-dontwarn javax.**
-dontwarn org.bouncycastle.**

# ML Kit, Room and Compose ship their own consumer rules.
