# ==========================================
# VIETSTREAM MAX - PROGUARD RULES
# ==========================================

# Preserve runtime annotations and generic signatures
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Exceptions

# Preserve Gson serialized fields
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Preserve AndroidX Keep annotations
-keep @androidx.annotation.Keep class * { *; }

-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Preserve Retrofit HTTP method annotations
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

# ==========================================
# END VIETSTREAM MAX
# ==========================================
