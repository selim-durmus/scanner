# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- PdfBox-Android (PDF merge/append) ---
# Ports Apache PDFBox/FontBox, which reference desktop java.awt/javax classes that
# don't exist on Android and use reflection for font handling. Keep it whole.
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn java.awt.**
-dontwarn javax.**

# --- ML Kit (document scanner + text recognition) ---
# ML Kit ships its own consumer rules; these just silence optional-dependency warnings.
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**