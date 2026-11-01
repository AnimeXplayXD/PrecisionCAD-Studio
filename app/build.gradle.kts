import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

// Automated Versioning Logic:
// 1. Reads base version metadata from root `version.properties` (if present).
// 2. Auto-increments `VERSION_CODE` in `version.properties` when executing a release build locally.
// 3. In GitHub Actions / CI, automatically uses `VERSION_CODE` or `100 + GITHUB_RUN_NUMBER` so every
//    CI release APK has a strictly increasing versionCode for seamless Android upgrade installs.
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
  if (versionPropsFile.canRead()) {
    versionPropsFile.inputStream().use { load(it) }
  }
}
val versionMajor = versionProps.getProperty("VERSION_MAJOR", "1").toIntOrNull() ?: 1
val versionMinor = versionProps.getProperty("VERSION_MINOR", "0").toIntOrNull() ?: 0
val versionPatch = versionProps.getProperty("VERSION_PATCH", "0").toIntOrNull() ?: 0
val storedVersionCode = versionProps.getProperty("VERSION_CODE", "1").toIntOrNull() ?: 1

val isReleaseBuildTask = gradle.startParameter.taskNames.any { taskName ->
  taskName.contains("Release", ignoreCase = true)
}
val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val explicitEnvVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull()

val resolvedVersionCode = when {
  explicitEnvVersionCode != null && explicitEnvVersionCode > 0 -> explicitEnvVersionCode
  ciRunNumber != null && ciRunNumber > 0 -> storedVersionCode + ciRunNumber
  isReleaseBuildTask -> {
    val nextCode = storedVersionCode + 1
    versionProps.setProperty("VERSION_CODE", nextCode.toString())
    runCatching {
      versionPropsFile.outputStream().use { out ->
        versionProps.store(out, "Auto-incremented by Gradle release build")
      }
    }
    nextCode
  }
  else -> storedVersionCode
}

val gitTagVersion = System.getenv("GITHUB_REF_NAME")
  ?.takeIf { it.startsWith("v") && it.length > 1 }
  ?.removePrefix("v")
val explicitEnvVersionName = System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }

val resolvedVersionName = when {
  explicitEnvVersionName != null -> explicitEnvVersionName
  gitTagVersion != null -> gitTagVersion
  ciRunNumber != null -> "$versionMajor.$versionMinor.$versionPatch.$ciRunNumber"
  else -> "$versionMajor.$versionMinor.$versionPatch"
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.precisioncad.kxpqzm"
    minSdk = 24
    targetSdk = 36
    versionCode = resolvedVersionCode
    versionName = resolvedVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
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
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
