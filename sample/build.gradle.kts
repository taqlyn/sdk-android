plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.taqlyn.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.taqlyn.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Override via Gradle props, e.g.:
        //   ./gradlew :sample:assembleDebug \
        //     -PTAQLYN_API_BASE_URL=https://api.rutvik.qzz.io \
        //     -PTAQLYN_CLIENT_ID=app_test_… \
        //     -PTAQLYN_PUBLIC_KEY_ID=pk_test_…
        // Defaults target the public tunnel demo host (see docs/guides/public-demo.md).
        val apiBase =
            (findProperty("TAQLYN_API_BASE_URL") as String?) ?: "https://api.rutvik.qzz.io"
        val clientId = (findProperty("TAQLYN_CLIENT_ID") as String?) ?: "app_test_sample"
        val publicKeyId = (findProperty("TAQLYN_PUBLIC_KEY_ID") as String?) ?: "pk_test_sample"
        buildConfigField("String", "TAQLYN_API_BASE_URL", "\"$apiBase\"")
        buildConfigField("String", "TAQLYN_CLIENT_ID", "\"$clientId\"")
        buildConfigField("String", "TAQLYN_PUBLIC_KEY_ID", "\"$publicKeyId\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":taqlyn-sdk"))
    implementation("com.taqlyn.nav:navigation2:0.1.0-SNAPSHOT")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
