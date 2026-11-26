plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Versionsnummer kommt aus GitHub Actions (-PversionCode=<run_number>)
val vCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "de.rico.irlmian"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.rico.irlmian"
        minSdk = 26
        targetSdk = 34
        versionCode = vCode
        versionName = "1.0.$vCode"
    }

    // Fester Schlüssel im Repo, damit neue Versionen sich über die alte installieren lassen
    signingConfigs {
        create("release") {
            storeFile = file("../keystore/irlm-ian.jks")
            storePassword = "irlmian"
            keyAlias = "irlmian"
            keyPassword = "irlmian"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
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
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
