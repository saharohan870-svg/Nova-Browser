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
        versionCode = 4
        versionName = "0.4.0"
    }

    // Ship an Android-phone APK without bundling desktop and other CPU architectures.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
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
    implementation("org.mozilla.geckoview:geckoview:149.0.20260330092855")
    implementation("io.github.dokar3:quickjs-kt-android:1.0.15")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("com.wireguard.android:tunnel:1.0.20260102")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    testImplementation("junit:junit:4.13.2")
}
