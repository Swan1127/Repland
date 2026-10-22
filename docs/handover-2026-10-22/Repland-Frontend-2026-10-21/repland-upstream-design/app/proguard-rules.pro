# Repland keeps all AI capability local. Nothing here is uploaded.
#
# Room generates implementations reflectively looked up by the runtime; keep them.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# pdfbox-android ships optional font/CMap resources and uses reflection internally.
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.**

# The debug AI advisor is loaded by name from ReplandApplication via Class.forName.
# Release builds intentionally fall back to NoOpAiAdvisor, so no keep rule is needed
# for the debug package; keeping this comment documents that the fallback is by design.

# Kotlin metadata / coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlin.Metadata { public <methods>; }
