// The model of the app -- the engine's answers, the blogs, the connection,
// what is kept on the device -- as a build of its own. The Android app
// beside it includes it, and so does the desktop app in its own
// repository; neither has to bring the other's tools along to build it.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
    versionCatalogs {
        // The Android app's versions: the model is built with what the app is built with.
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "core"
