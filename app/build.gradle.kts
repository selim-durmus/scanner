plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tuttoposto.scanner"
    compileSdk = 36

    defaultConfig {
        // v1 shipped as a debug-signed com.example.scanner. v2 is a fresh package so both
        // install side by side. No data moves: both keep their PDFs in Documents/Scanner.
        applicationId = "com.tuttoposto.scanner"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"

        manifestPlaceholders["appLabel"] = "Scanner"
    }

    // Release signing lives outside the repo, in ~/.gradle/gradle.properties:
    //   scannerKeystore, scannerKeystorePassword, scannerKeyAlias
    // Without them, assembleRelease still works but produces an unsigned APK.
    // Keep that keystore backed up: updates only install over an APK signed by the same key.
    val releaseKeystore = providers.gradleProperty("scannerKeystore").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.gradleProperty("scannerKeystorePassword").get()
                keyAlias = providers.gradleProperty("scannerKeyAlias").get()
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        debug {
            // Separate package so debug installs never share settings or the index with the real app.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "Scanner (debug)"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Camera capture (edge detection, crop, filters) and on-device OCR, both via Play services.
    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.text.recognition)

    // Page edits: merge, reorder, rotate, delete.
    implementation(libs.pdfbox.android)

    testImplementation(libs.junit)
}
