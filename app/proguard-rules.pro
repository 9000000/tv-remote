# Shrink only: keep the real names so a crash report can be read without a mapping file.
-dontobfuscate

# Started on the TV by name with app_process (see Meta.kt), so nothing in the app refers to it.
-keep class dev.tvremote.core.Meta { public static void main(java.lang.String[]); }
