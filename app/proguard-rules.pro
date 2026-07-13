# GeckoView ships consumer rules. Keep JavaScript interfaces and delegates visible.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn org.mozilla.**
