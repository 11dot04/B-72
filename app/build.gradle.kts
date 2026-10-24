plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    // Same id as the Flutter build, so this installs over it and can read its files.
    namespace = "com.b72.keep_canvas"
    // Defines the precise 37.1 minor API level
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.b72.keep_canvas"
        minSdk = 26          // variable fonts need 26
        targetSdk = 36
        versionCode = 100    // above the Flutter build's, so the update is accepted
        versionName = "2.0.0"
        // free key from themoviedb.org, stored as the TMDB_KEY repository secret; empty is fine
        buildConfigField("String", "TMDB_KEY", "\"${System.getenv("TMDB_KEY") ?: ""}\"")
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../keystore/b72.jks")
            storePassword = "b72-sideload"
            keyAlias = "b72"
            keyPassword = "b72-sideload"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val compose = "1.13.0-beta02"
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.ui:ui:$compose")
    implementation("androidx.compose.ui:ui-graphics:$compose")
    implementation("androidx.compose.foundation:foundation:$compose")
    implementation("androidx.compose.animation:animation:$compose")
    implementation("androidx.compose.material3:material3:1.5.0-beta02")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.graphics:graphics-shapes:1.1.0")
    implementation("androidx.palette:palette:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}
