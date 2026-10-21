pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "tv-remote"
// Only the build setup lives here, so that app/ shows the parts themselves: android, tv, home (TV Home), channels (TV Channels), shared, desktop (the computer
// app, on the shared Kotlin core). Open this folder in Android Studio.
include(":shared", ":android", ":tv", ":home", ":channels", ":desktop")
for (name in listOf("shared", "android", "tv", "home", "channels", "desktop")) project(":$name").projectDir = file("../$name")
