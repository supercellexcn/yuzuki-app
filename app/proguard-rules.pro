# 首版不启用混淆。若后续开启 minify，需保留 WebView 的 JS 接口方法。
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
