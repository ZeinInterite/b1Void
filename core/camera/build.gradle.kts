plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") // Для Hilt, если будет использоваться
    id("com.google.dagger.hilt.android") // Для Hilt, если будет использоваться
}

android {
    namespace = "com.example.b1void.core.camera"
    compileSdk = 35

    defaultConfig {
        minSdk = 27
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    // Зависимости Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-compiler:2.50")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Зависимости CameraX
    val camerax_version = "1.5.2" // Используем обновленную версию
    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")
    implementation("androidx.camera:camera-view:${camerax_version}")
    implementation("androidx.camera:camera-video:${camerax_version}")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Зависимость от core:domain, чтобы использовать интерфейсы
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
}
