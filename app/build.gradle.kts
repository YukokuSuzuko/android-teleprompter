plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

// Release signing is opt-in. Never ship APKs signed with Android's shared debug key.
// CI provides these values from GitHub Actions secrets; local builds can use the same env vars.
val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("ANDROID_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
  releaseKeystorePath,
  releaseStorePassword,
  releaseKeyAlias,
  releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
  namespace = "io.github.yukokusuzuko.teleprompter"
  compileSdk = 36
  defaultConfig {
    applicationId = "io.github.yukokusuzuko.teleprompter"
    minSdk = 26
    targetSdk = 36
    versionCode = 3
    versionName = "1.2"
    ndk {
      abiFilters += "arm64-v8a"
    }
  }

  androidResources {
    noCompress += listOf("onnx", "txt")
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = file(releaseKeystorePath!!)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      // Without all four values, release outputs are intentionally unsigned.
      // Never publish them; the release workflow verifies signing before upload.
      if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Offline Chinese streaming ASR & Pinyin alignment
  implementation(files("libs/sherpa-onnx-2.0.1.aar"))
  implementation("com.belerweb:pinyin4j:2.5.1")
}
