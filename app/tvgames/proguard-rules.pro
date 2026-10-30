# LibretroDroid calls into its native library by name, and the page bridge is reached by reflection from JS.
-keep class com.swordfish.libretrodroid.** { *; }
-keepclassmembers class dev.tvremote.games.WebGameActivity$Bridge { @android.webkit.JavascriptInterface <methods>; }
