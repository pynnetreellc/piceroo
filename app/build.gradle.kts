plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.piceroo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.piceroo.app"
        minSdk = 30 // Android 11: first version with MediaStore trash requests
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    // A fixed key checked into the repo, so every CI build is signed the same way
    // and a new APK installs over the old one (keeping your stats).
    signingConfigs {
        create("roo") {
            storeFile = file("piceroo-signing.p12")
            storeType = "pkcs12"
            storePassword = "android"
            keyAlias = "piceroo"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("roo")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("roo")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
}
