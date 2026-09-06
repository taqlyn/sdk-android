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

val navCompose = file("../nav-compose")
val hasNavCompose = navCompose.resolve("settings.gradle.kts").isFile
if (hasNavCompose) {
    includeBuild(navCompose) {
        // In-package sample only. Customer apps take `com.taqlyn.nav:*` from Maven Central.
        dependencySubstitution {
            substitute(module("com.taqlyn.nav:model")).using(project(":model"))
            substitute(module("com.taqlyn.nav:navigation2")).using(project(":navigation2"))
        }
    }
}

include(":taqlyn-sdk")
if (hasNavCompose) {
    include(":sample")
}
