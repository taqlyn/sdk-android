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

rootProject.name = "taqlyn-sdk-android"

includeBuild("../nav-compose") {
    // In-package sample only. Customer apps take `com.taqlyn.nav:*` from Maven Central.
    dependencySubstitution {
        substitute(module("com.taqlyn.nav:model")).using(project(":model"))
        substitute(module("com.taqlyn.nav:navigation2")).using(project(":navigation2"))
    }
}

include(":taqlyn-sdk")
include(":sample")
