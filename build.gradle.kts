plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

kotlin {
    // Pins the JDK the Kotlin compiler (and kapt's stub-generation task) runs
    // against. Without this, kapt tasks can pick up a different JDK than
    // compileDebugJavaWithJavac and fail with a "jvm target compatibility"
    // mismatch even when compileOptions/kotlinOptions below already agree —
    // a common, confusing kapt-specific failure mode.
    jvmToolchain(17)
}

android {
    namespace = "com.example.gifkeyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.gifkeyboard"
        minSdk = 24       // commitContent (rich content) needs API 25+; we soft-check at runtime
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Drop your real key here, or load from local.properties / env at build time.
        // Leaving it blank makes the app fall back to bundled sample clips automatically.
        buildConfigField("String", "MEDIA_PROVIDER_API_KEY", "\"\"")
        buildConfigField("String", "MEDIA_PROVIDER_BASE_URL", "\"https://tenor.googleapis.com/v2/\"")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")

    // Networking for the media search provider (Tenor/GIPHY-style REST API)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Video playback of MP4/WebM clips inside the keyboard's media tab
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // Local persistence for recents/favorites
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Image thumbnails (static preview frame before video loads)
    implementation("com.github.bumptech.glide:glide:4.16.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
