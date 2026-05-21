# Add project specific ProGuard rules here.
# Keep AList binary related classes
-keep class io.alist.app.alist.** { *; }

# WebView
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep JavaScript interface
-keepattributes JavascriptInterface
-keep public class io.alist.app.ui.WebViewActivity$WebAppInterface {
    public <methods>;
}
