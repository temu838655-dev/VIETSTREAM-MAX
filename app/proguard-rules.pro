
# ==========================================
# VIETSTREAM MAX - PROGUARD CONFIGURATION
# ==========================================

# Preserve runtime annotations and generic signatures
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Preserve application data models for Gson serialization
-keep class com.vietstream.max.** { *; }

# Preserve AndroidX Keep annotations
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Preserve Gson serialized fields
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Retrofit configuration
-keep,allowobfuscation,allowshrinking class retrofit2.** { *; }

# Media3 Keep annotations
-keepclassmembers class androidx.media3.** {
    @androidx.annotation.Keep *;
}

# End of configuration
