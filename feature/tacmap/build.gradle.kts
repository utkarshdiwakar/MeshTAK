/*
 * MeshTAK :feature:tacmap — the tactical map transplanted from NodeCast
 * (OmniTAK-Android, Apache-2.0). Deliberately a plain Android library module,
 * NOT one of the repo's KMP convention plugins: the ported code is
 * Android-only androidx Compose, and isolating its dependencies (MapLibre,
 * AndroidSVG, MGRS, TIFF) here keeps the androidApp diff — and future
 * upstream merges — small.
 *
 * Namespace matches the ported package so ~100 NodeCast files compile
 * verbatim (R class, BuildConfig, imports).
 */
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "soy.engindearing.omnitak.mobile"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        // MeshtasticCoTConverter stamps this into CoT <takv> details.
        buildConfigField("String", "VERSION_NAME", "\"0.1.0\"")
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
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose via the repo's CMP catalog entries so versions stay aligned
    // with androidApp (CMP artifacts resolve to androidx on Android).
    implementation(libs.compose.multiplatform.runtime)
    implementation(libs.compose.multiplatform.foundation)
    implementation(libs.compose.multiplatform.material3)
    // material-icons-extended is a frozen artifact (androidx stopped shipping
    // it with newer Compose trains); 1.7.8 is the terminal version and is
    // compatible with current runtimes.
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.datastore.preferences)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")

    // Map stack (versions carried over from NodeCast, field-proven together)
    implementation("org.maplibre.gl:android-sdk:11.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("mil.nga.mgrs:mgrs-android:2.2.3")
    implementation("mil.nga:tiff:3.0.0")
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
