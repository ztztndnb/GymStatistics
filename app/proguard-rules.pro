# --- kotlinx.serialization (keep serializers used by the JSON persistence) ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.gymstatistics.data.**$$serializer { *; }
-keepclassmembers class com.gymstatistics.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.gymstatistics.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- NanoHTTPD (embedded LAN server) ---
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# --- Model data classes ---
-keep class com.gymstatistics.data.** { *; }
