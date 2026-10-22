plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.voicelists"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.voicelists"
        minSdk = 31
        targetSdk = 34
        versionCode = 6
        versionName = "1.5"
    }

    // A fixed key, kept in the repo, so every build can be installed over the previous one.
    signingConfigs {
        create("personal") {
            storeFile = rootProject.file("keystore/voicelists.jks")
            storePassword = "voicelists"
            keyAlias = "voicelists"
            keyPassword = "voicelists"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("personal")
        }
        debug {
            signingConfig = signingConfigs.getByName("personal")
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
