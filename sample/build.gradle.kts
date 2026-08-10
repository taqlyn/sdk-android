plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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

        // Override via -PTAQLYN_API_BASE_URL=… when pointing at a real API.
        buildConfigField("String", "TAQLYN_API_BASE_URL", "\"https://api.example.com\"")
        buildConfigField("String", "TAQLYN_CLIENT_ID", "\"app_test_sample\"")
        buildConfigField("String", "TAQLYN_PUBLIC_KEY_ID", "\"pk_test_sample\"")
    }

    buildFeatures {
        buildConfig = true
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.material:material:1.12.0")
}
