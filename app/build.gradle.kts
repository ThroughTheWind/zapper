plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The app version lives once, in gradle/libs.versions.toml. The version code is derived from it
// (MAJOR * 1_000_000 + MINOR * 1_000 + PATCH) so the two can never disagree: 0.1.0 is 1000.
val zapperVersion = libs.versions.zapper.get()
val zapperVersionCode: Int =
    run {
        val parts = zapperVersion.split(".").map { it.toIntOrNull() ?: -1 }
        require(parts.size == 3 && parts.all { it in 0..999 }) {
            "The zapper version must be MAJOR.MINOR.PATCH with parts from 0 to 999, got $zapperVersion"
        }
        parts[0] * 1_000_000 + parts[1] * 1_000 + parts[2]
    }

// Release signing comes from the environment, which the release workflow fills from GitHub
// secrets (see docs/releasing.md). Without it, assembleRelease still builds an unsigned APK.
val releaseKeystore = providers.environmentVariable("ZAPPER_KEYSTORE_FILE").orNull

android {
    namespace = "io.github.throughthewind.zapper"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "io.github.throughthewind.zapper"
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
        versionCode = zapperVersionCode
        versionName = zapperVersion
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("ZAPPER_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ZAPPER_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ZAPPER_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }

    buildFeatures {
        compose = true
    }

    // F-Droid and reproducible builds: no Google-signed dependency metadata blob in the APK (ADR-0003).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:remote"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":adapters:samsung"))
    debugImplementation(project(":adapters:fake"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
