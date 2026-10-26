plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.harish.videoplayer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.harish.videoplayer"
        minSdk = 30
        targetSdk = 37
        // CI (GitHub Actions) passes these from the tag / run number; local builds use the defaults
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes from environment variables (GitHub Actions secrets).
    // Nothing secret is stored in the repo; without them, release builds are simply unsigned.
    val releaseKeystore = System.getenv("KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // Kotlin's jvmTarget follows this automatically with AGP 9 built-in Kotlin
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    lint {
        // Media3 marks some useful APIs (resize mode, bitmap loader...) as "unstable".
        // They are fine to use in an app; this stops lint from failing release builds over them.
        disable += "UnsafeOptInUsageError"
    }
}

dependencies {
    // One Compose BOM for every configuration (versions of all Compose libs come from it)
    val composeBom = platform(libs.androidx.compose.bom)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)   // collectAsStateWithLifecycle
    implementation(libs.androidx.lifecycle.viewmodel)          // ViewModel, viewModelScope
    implementation(libs.androidx.navigation.compose)

    // Compose
    implementation(composeBom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    // Media3: the video engine, the video surface view and the media session/notification
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    // Software audio decoders (Dolby Digital / AC-3, E-AC-3, DTS, TrueHD, FLAC, Opus...) for
    // phones whose built-in decoders can't play a track
    implementation(libs.media3.ffmpeg.decoder)

    // Casting: Google Cast for Chromecast / Google TV (DLNA smart TVs use plain HTTP, no library)
    implementation(libs.play.services.cast.framework)
    implementation(libs.androidx.mediarouter)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose) // hiltViewModel()

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
