plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.b1void.core.ui"
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

    buildFeatures {
        compose = true
    }

    // Using Kotlin 2.1 Compose Compiler plugin; no composeOptions needed.
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")

    // Compose BOM and core
    implementation(platform("androidx.compose:compose-bom:2024.10.01")) // Добавить
    implementation("androidx.compose.ui:ui") // Добавить
    implementation("androidx.compose.foundation:foundation") // Добавить
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview") // Добавить
    debugImplementation("androidx.compose.ui:ui-tooling") // Добавить
    implementation("androidx.activity:activity-compose:1.9.3") // Добавить
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation(project(":core:domain"))
    implementation(project(":core:model"))

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.54")
    ksp("com.google.dagger:hilt-compiler:2.54")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
}
