plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.rishi.tvlauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.rishi.tvlauncher"
        minSdk = 24
        targetSdk = 35
        // CI passes these from the git tag; local builds use the defaults.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "1.0-dev"
    }

    signingConfigs {
        create("release") {
            storeFile = System.getenv("RELEASE_STORE_FILE")?.let(::file)
            storePassword = System.getenv("RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Published releases must share one key or users can't upgrade in place.
            // Without RELEASE_* env vars (local builds) fall back to the debug key.
            val hasReleaseKey = System.getenv("RELEASE_STORE_FILE") != null
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
