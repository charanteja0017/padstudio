import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9 compiles Kotlin itself; only compiler plugins are added (same setup as WiFiBar).
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries)
}

android {
    namespace = "com.charan.padstudio"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.charan.padstudio"
        // Pad 6 (India) runs Android 14 and Pad 7/8 Android 16; 31 keeps Glance's adaptive grids and blur APIs.
        minSdk = 31
        targetSdk = 37
        versionCode = 2
        versionName = "0.2"
    }

    buildFeatures {
        compose = true
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
    implementation(project(":core"))
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material.icons.core)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.work.runtime)
    implementation(libs.kotlinx.coroutines.android)
    // The live 3D pen (pen/PenModelView.kt). Brings Filament's native libraries; see the APK size note there.
    implementation(libs.sceneview)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
