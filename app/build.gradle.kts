plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

val stableKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")

android {
    namespace = "com.example.candlealert"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.candlealert"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("stable") {
            if (stableKeystorePath != null) {
                storeFile = file(stableKeystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: "candlealert"
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (stableKeystorePath != null) {
                signingConfig = signingConfigs.getByName("stable")
            }
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.gms:play-services-auth:21.5.0")
}
