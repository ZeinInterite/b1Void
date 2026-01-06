plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") // Для Hilt, если будет использоваться
    id("com.google.dagger.hilt.android") // Для Hilt, если будет использоваться
}

android {
    namespace = "com.example.b1void.core.network"
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

    // Зависимости от core:domain и core:model
    implementation(project(":core:domain"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))

    // Firebase
    implementation("com.google.firebase:firebase-auth:23.2.0")
    implementation("com.google.firebase:firebase-storage:21.0.1")

    // Dropbox
    implementation ("com.dropbox.core:dropbox-core-sdk:7.0.0")
    implementation ("com.dropbox.core:dropbox-android-sdk:7.0.0")
}
