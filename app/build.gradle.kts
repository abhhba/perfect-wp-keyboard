import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.abhhba.perfectwp"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.abhhba.perfectwp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // The release key is never in the repository: pass -PsigningProperties=<file> with storeFile, storePassword, keyAlias and
    // keyPassword. Without it the release build is unsigned.
    val signingFile = providers.gradleProperty("signingProperties").orNull?.let { file(it) }?.takeIf { it.exists() }
    val release = signingFile?.let { f ->
        val keys = Properties().apply { f.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = file(keys.getProperty("storeFile"))
            storePassword = keys.getProperty("storePassword")
            keyAlias = keys.getProperty("keyAlias")
            keyPassword = keys.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = release
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
