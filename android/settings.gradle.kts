// LivePL na Androida (app: LivePL, Poczta LivePL, LivePL Connect) i TVML (tvml).
// Budowanie: ./gradlew assembleDebug (opis w README.md).
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LivePL"
include(":app")
include(":tvml")
