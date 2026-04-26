import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

// Read local.properties so we can expose the Gemini key via BuildConfig
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.myapplication2"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.myapplication2"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Gemini API key — set gemini.api.key in local.properties
        buildConfigField(
            "String", "GEMINI_API_KEY",
            "\"${localProperties.getProperty("gemini.api.key", "")}\""
        )
    }

    buildFeatures {
        buildConfig = true
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    implementation("androidx.room:room-runtime:2.6.1")
    implementation(libs.firebase.firestore)
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)

    // Official Google AI Generative AI (Gemini) SDK
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    // Guava for Android — needed for ListenableFuture (Java interop with the SDK)
    implementation("com.google.guava:guava:32.1.3-android")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
