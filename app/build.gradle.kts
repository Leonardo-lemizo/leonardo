import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signaturdaten für die Release-APK (Datei liegt im Projektordner, nicht weitergeben).
val signatur = Properties().apply {
    val datei = rootProject.file("keystore.properties")
    if (datei.exists()) datei.inputStream().use { load(it) }
}

android {
    namespace = "app.leonardo"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.leonardo"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "2.0"
    }

    signingConfigs {
        create("release") {
            if (!signatur.isEmpty) {
                storeFile = rootProject.file(signatur.getProperty("storeFile"))
                storePassword = signatur.getProperty("storePassword")
                keyAlias = signatur.getProperty("keyAlias")
                keyPassword = signatur.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Die Kapitel-JSONs aus ./content werden direkt als Assets eingebunden.
    sourceSets {
        getByName("main") {
            assets.directories.add("../content")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    testImplementation("junit:junit:4.13.2")
    // Nur für Tests: echtes org.json statt der leeren Android-Platzhalter.
    testImplementation("org.json:json:20260814")
}
