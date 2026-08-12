plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

group = "com.taqlyn"
version = "0.1.0-SNAPSHOT"

android {
    namespace = "com.taqlyn.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    // Play Install Referrer — ONLY consumed inside adapters/InstallReferrer.kt
    implementation("com.android.installreferrer:installreferrer:2.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

// Enables RN / Flutter consumers via includeBuild + dependencySubstitution
// or `./gradlew :taqlyn-sdk:publishToMavenLocal`.
publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.taqlyn"
            artifactId = "taqlyn-sdk"
            version = project.version.toString()
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
