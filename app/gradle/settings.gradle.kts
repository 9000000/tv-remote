pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "tv-remote"
// Only the build setup lives here, so that app/ shows the parts themselves: android, tv, shared, desktop (the computer's
// server on the shared Kotlin core, a trial) and web (the Python server, not part of this build). Open this folder in Android Studio.
include(":shared", ":android", ":tv", ":desktop")
for (name in listOf("shared", "android", "tv", "desktop")) project(":$name").projectDir = file("../$name")
