# Shrink only: keep the real names so a crash report can be read without a mapping file.
-dontobfuscate

# Started on the TV by name with app_process (see Meta.kt, Caps.kt), so nothing in the app refers to them.
-keep class dev.tvremote.core.Meta { public static void main(java.lang.String[]); }
-keep class dev.tvremote.core.Caps { public static void main(java.lang.String[]); }

# Found by name (core/AdbTls.kt): the TLS 1.3 backend for Wireless debugging pairing.
-keep class dev.tvremote.ConscryptBackend { <init>(); }
