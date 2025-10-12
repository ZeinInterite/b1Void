plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.b1void.camera"
    compileSdk = 35

    defaultConfig {
        minSdk = 27 // Core target per requirements
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }

    buildFeatures { buildConfig = false }
}

dependencies {
    val camerax_version = "1.3.1"
    api("androidx.camera:camera-core:$camerax_version")
    api("androidx.camera:camera-camera2:$camerax_version")
    api("androidx.camera:camera-lifecycle:$camerax_version")
    api("androidx.camera:camera-view:$camerax_version")
    api("androidx.camera:camera-video:$camerax_version")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Hilt DI (KSP)
    api("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

