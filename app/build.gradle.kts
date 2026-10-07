plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.example.candlealert"
    compileSdk = 35

    val buildVersionCode = providers.gradleProperty("VERSION_CODE").orNull?.toIntOrNull() ?: 1

    defaultConfig {
        applicationId = "com.example.candlealert"
        minSdk = 26
        targetSdk = 35
        versionCode = buildVersionCode
        versionName = "1.$buildVersionCode"
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("CANDLEALERT_KEYSTORE_PATH")
            val storePwd = System.getenv("CANDLEALERT_STORE_PASSWORD")
            val keyPwd = System.getenv("CANDLEALERT_KEY_PASSWORD")
            if (!storePath.isNullOrBlank() && !storePwd.isNullOrBlank() && !keyPwd.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = storePwd
                keyAlias = "candlealert"
                keyPassword = keyPwd
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
