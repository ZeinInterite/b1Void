plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.b1void"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.b1void"
        minSdk = 27
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        
        // Оптимизация для слабых устройств
        multiDexEnabled = true
        
        // Оптимизация для старых устройств
        ndk {
            abiFilters.addAll(setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
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
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Using Kotlin 2.1 Compose Compiler plugin; no composeOptions needed.

    testOptions {
        // Run tests in isolation to avoid UI мерцание/параллелизм
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

dependencies {
    // Exclude conflicting annotations to resolve duplicate class errors
    configurations.all {
        exclude(group = "com.intellij", module = "annotations")
    }
    // Core AndroidX removed. These dependencies should be handled by core modules.

    // Compose BOM and core
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    // AndroidX Preference removed. Should be provided by core modules.

    // Kotlin Coroutines removed. Should be provided by core modules.

    // Datastore removed. Should be provided by core modules.

    // Play Services Auth removed. Should be provided by core modules.

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.54")
    implementation("androidx.hilt:hilt-common:1.2.0")

    ksp("com.google.dagger:hilt-compiler:2.54")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Compose Navigation
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Hilt Work removed. It was contributing to the original Hilt error.

    // LeakCanary removed. Can be added to a dedicated debug module if needed.

    // Зависимости от feature модулей
    implementation(project(":feature:camera"))
    implementation(project(":feature:gallery"))

    // Зависимости от core модулей
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:camera"))

    // Added to resolve adapter, UI, and worker dependencies
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // CameraX
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Coroutines для CameraX
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Firebase
    // implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    // implementation("com.google.firebase:firebase-auth-ktx")
    // implementation("com.google.firebase:firebase-database-ktx")
    // implementation("com.google.firebase:firebase-storage-ktx")

    // Dropbox
    // implementation("com.dropbox.core:dropbox-core-sdk:6.0.0")

    // Glide
    // implementation("com.github.bumptech.glide:glide:4.16.0")

    // SwipeRefreshLayout
    // implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0-alpha01")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestUtil("androidx.test:orchestrator:1.5.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    // Espresso contrib for pinch gestures, RecyclerView, etc.
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.6.1")
    // Compose UI testing
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
