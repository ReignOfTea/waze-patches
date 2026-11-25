# Extensions are merged into the target APK as a DEX before patching, so anything stripped
# here changes the classes the injected smali calls into.
-dontwarn android.**
-dontwarn androidx.**

# Entry points are called from injected smali, which ProGuard cannot see.
-keep class io.github.forsigh.waze.extension.** { *; }
