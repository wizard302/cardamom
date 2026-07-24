import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing credentials live outside the project — in the user-global
// ~/.gradle/gradle.properties, never in the repo:
//   cardamom.signing.storeFile / .storePassword / .keyAlias / .keyPassword
// When they are absent — other machines, CI — release builds fall back to debug
// signing so the build still works.
val signingStoreFile = (findProperty("cardamom.signing.storeFile") as String?)
    ?.trim()
    ?.replaceFirst(Regex("^~"), System.getProperty("user.home"))
    ?.let(::File)
    ?.takeIf { it.isFile }
val signingStorePassword = (findProperty("cardamom.signing.storePassword") as String?)?.takeIf { it.isNotBlank() }
val signingKeyAlias = (findProperty("cardamom.signing.keyAlias") as String?)?.takeIf { it.isNotBlank() }
val signingKeyPassword = (findProperty("cardamom.signing.keyPassword") as String?)?.takeIf { it.isNotBlank() }
// All four must be present — a half-configured keystore would fail mid-build.
val hasReleaseSigning =
    signingStoreFile != null && signingStorePassword != null && signingKeyAlias != null && signingKeyPassword != null

android {
    namespace = "io.github.wizard302.cardamom"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.wizard302.cardamom"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = signingStoreFile
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
                // v2 is what minSdk 26 needs; v3 additionally allows key
                // rotation later, v4 enables incremental install (adb install
                // --incremental) by writing an .apk.idsig alongside the APK.
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sign with the real release key when its Gradle properties are set;
            // otherwise fall back to debug signing so local/CI builds still work.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    ksp {
        // Exported Room schemas (see CardamomDatabase exportSchema) live in
        // app/schemas and are committed, so migrations stay testable.
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    testOptions {
        // Android stubs (e.g. Uri.parse in model class-initializers) return
        // defaults instead of throwing, so pure-logic tests can construct models.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // AppCompatActivity is required for AppCompatDelegate.setApplicationLocales below API 33.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.taglib)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
