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
    dependencySubstitution {
        substitute(module("com.taqlyn.nav:model")).using(project(":model"))
        substitute(module("com.taqlyn.nav:navigation2")).using(project(":navigation2"))
    }
}

include(":taqlyn-sdk")
include(":sample")
