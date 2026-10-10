-dontwarn kotlinx.**
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}

# Credential Manager / Google ID — release minify must not strip these.
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn androidx.credentials.**
-dontwarn com.google.android.gms.**

# Xani Lens: ML Kit face + selfie models must survive minify.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**

