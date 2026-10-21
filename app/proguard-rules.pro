# Project-specific ProGuard/R8 rules.

# Filament is driven through JNI; keep its classes and native method bindings.
-keep class com.google.android.filament.** { *; }
-dontwarn com.google.android.filament.**
