# Keep the public no-argument module entry while allowing R8 to optimize and
# rename its implementation. java_init.list is rewritten to the final name.
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

-adaptresourcefilecontents META-INF/xposed/java_init.list
-dontwarn io.github.libxposed.annotation.**

# Classes created from the Android manifest.
-keep class io.github.arno0129.lechangecleaner.LechangeApp { *; }
-keep class io.github.arno0129.lechangecleaner.SettingsActivity { *; }
-keep class io.github.libxposed.service.** { *; }
-keep class io.github.libxposed.api.** { *; }
