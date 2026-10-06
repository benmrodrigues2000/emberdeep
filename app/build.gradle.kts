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
        versionCode = 2
        versionName = "1.1.0"
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
        // Headless JVM tests: pure logic (model, generation, systems, saves).
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }

    bundle {
        language { enableSplit = false }
    }
}

dependencies {
    // Unit tests only — the shipped app has no runtime dependencies at all.
    testImplementation("junit:junit:4.13.2")
    // Real org.json on the JVM test classpath (Android provides its own).
    testImplementation("org.json:json:20240303")
}

// Surface test output (including the balance simulation report) in CI logs.
tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    maxHeapSize = "1280m"
}
