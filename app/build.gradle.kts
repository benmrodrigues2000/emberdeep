plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.emberdeep.game"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.emberdeep.game"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/emberdeep-release.jks")
            storePassword = System.getenv("EMBERDEEP_STORE_PASSWORD") ?: "emberdeep2026"
            keyAlias = System.getenv("EMBERDEEP_KEY_ALIAS") ?: "emberdeep"
            keyPassword = System.getenv("EMBERDEEP_KEY_PASSWORD") ?: "emberdeep2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
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

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }

    bundle {
        language { enableSplit = false }
    }
}
