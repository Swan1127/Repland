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

# Preserve line information for locally captured crash diagnostics without exposing
# source-file paths in a release artifact.
-keepattributes SourceFile,LineNumberTable,*Annotation*
-renamesourcefileattribute SourceFile

# Room generates its database implementation at build time. Keeping the database
# entry point avoids a shrinker-related migration failure on an upgraded install.
-keep @androidx.room.Database class * { *; }

# The on-device timetable importer uses PDFBox classes discovered while parsing a
# document. Keep this small local parsing surface intact in minified builds.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }

# JPEG2000 support is an optional PDFBox plug-in and is not bundled with the local
# timetable importer. Normal PDFs continue to parse; R8 must not require the absent
# Gemalto implementation merely because PDFBox has an optional reference to it.
-dontwarn com.gemalto.jp2.**
