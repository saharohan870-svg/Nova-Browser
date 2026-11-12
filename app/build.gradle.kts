plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zorvi.browser"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.zorvi.browser"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"
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
    // GeckoView is the primary web rendering engine. Keep QuickJS for the experimental Zorvi engine.
    implementation("org.mozilla.geckoview:geckoview-nightly:162.0.20261111213251")
    implementation("io.github.dokar3:quickjs-kt-android:1.0.15")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
