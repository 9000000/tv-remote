pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "tv-remote"
// Only the build setup lives here, so that app/ shows the parts themselves: android, tv, shared, web (the Python web UI,
// not part of this build). Open this folder in Android Studio.
include(":shared", ":android", ":tv")
for (name in listOf("shared", "android", "tv")) project(":$name").projectDir = file("../$name")
