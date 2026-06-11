plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.figly.glyph"
    compileSdk = 35

    defaultConfig {
        minSdk = 33
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-morphology"))
    implementation(project(":data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // The Glyph Matrix SDK is packaged once, by :app. Compile-only here so the
    // library AAR doesn't try to embed a local AAR (unsupported by AGP).
    compileOnly(rootProject.files("libs/glyph-matrix-sdk-2.0.aar"))
    testImplementation(libs.junit)
}
