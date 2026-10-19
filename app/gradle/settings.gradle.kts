pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "tv-remote"
// Only the build setup lives here, so that app/ shows the parts themselves: android, tv, home (TV Home), shared, desktop (the computer
// app, on the shared Kotlin core). Open this folder in Android Studio.
include(":shared", ":android", ":tv", ":home", ":desktop")
for (name in listOf("shared", "android", "tv", "home", "desktop")) project(":$name").projectDir = file("../$name")
