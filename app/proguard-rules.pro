# Keep JNI bridge object and all native methods matching Rust #[no_mangle] exports
-keep class io.rabbitcontrol.android.session.Core {
    *;
}

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Preserve crash stack trace line numbers with minimal size overhead
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Strip verbose diagnostic logs in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
