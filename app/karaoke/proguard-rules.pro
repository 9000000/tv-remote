# The player page reaches its bridge by reflection from JS.
-keepclassmembers class dev.tvremote.karaoke.MainActivity$Bridge { @android.webkit.JavascriptInterface <methods>; }
