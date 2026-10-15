// The computer's server on the same Kotlin core the two apps use (a trial, next to the Python one in app/web): the core's
// files are compiled here as they are, without the few that only run on Android (picture, sound, the on-TV helpers).
plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    jvmToolchain(17)
    sourceSets["main"].kotlin.srcDir("../shared/src/main/kotlin")
}
// Android only: decoding the picture and sound (TvAndroid, AudioOut), the classes started on the TV (Meta, Caps), the QR bitmap
sourceSets["main"].kotlin.exclude("**/core/TvAndroid.kt", "**/core/AudioOut.kt", "**/core/Meta.kt", "**/core/Caps.kt", "**/core/Qr.kt")

dependencies {
    implementation("org.json:json:20240303")                         // on Android org.json is part of the system
}

application {
    mainClass.set("dev.tvremote.desktop.MainKt")
}
