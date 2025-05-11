plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.b1void"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.b1void"
        minSdk = 25
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    implementation ("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.google.firebase:firebase-storage-ktx:21.0.1")
    implementation("com.google.firebase:firebase-storage:21.0.1")
    implementation("androidx.datastore:datastore-core-android:1.1.1")
    implementation("com.google.firebase:firebase-crashlytics-buildtools:3.0.3")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.16.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation ("androidx.datastore:datastore-preferences:1.1.1")
    implementation ("androidx.activity:activity-ktx:1.9.3")
    implementation ("androidx.fragment:fragment-ktx:1.8.5")
    implementation ("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation ("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    implementation("com.google.android.gms:play-services-auth:21.3.0")

    implementation ("com.dropbox.core:dropbox-core-sdk:7.0.0")
    implementation ("com.dropbox.core:dropbox-android-sdk:7.0.0")

    implementation ("com.squareup.picasso:picasso:2.71828")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.firebase:firebase-database:21.0.0")
    implementation("com.google.firebase:firebase-auth:23.2.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    implementation("androidx.activity:activity-ktx:1.10.1")

    implementation("com.github.yukuku:ambilwarna:2.0.1")

    api("com.otaliastudios:cameraview:2.7.2")

    implementation("net.lingala.zip4j:zip4j:2.9.0")

    implementation ("com.google.code.gson:gson:2.10.1")

}
