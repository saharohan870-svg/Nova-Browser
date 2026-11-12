plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zorvi.browser"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zorvi.browser"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    // Mozilla GeckoView stable artifact; this exact release is published on Mozilla Maven.
    implementation("org.mozilla.geckoview:geckoview:128.0.20240725162350")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Retain the standalone JavaScript engine prototype alongside GeckoView.
    implementation("io.github.dokar3:quickjs-kt-android:1.0.0-alpha13")
    testImplementation("junit:junit:4.13.2")
}
