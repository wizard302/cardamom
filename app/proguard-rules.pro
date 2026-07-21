# Project-specific ProGuard/R8 rules.

# --- kotlinx.serialization ---
# The Retrofit converter looks up serializers reflectively, so the generated
# $$serializer classes and the Companion.serializer() methods must survive R8.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-dontwarn kotlinx.serialization.**

-keepclassmembers @kotlinx.serialization.Serializable class * {
    static <fields>;
    static ** Companion;
    static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class **$$serializer { *; }

# Network DTOs are only referenced through reflection by the JSON converter.
-keep class io.github.wizard302.cardamom.data.remote.** { *; }
