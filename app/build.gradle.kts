import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mika.assistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mika.assistant"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0-elevenlabs"
        val proxyUrl = (project.findProperty("ELEVENLABS_PROXY_URL") as String?) ?: ""
        buildConfigField("String", "ELEVENLABS_PROXY_URL", "\"${proxyUrl}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Keep the LiteRT-LM dependency required by LocalGemmaEngine.
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
