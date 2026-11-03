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
// 3. In GitHub Actions / CI, automatically uses `VERSION_CODE` or `storedVersionCode + GITHUB_RUN_NUMBER`
//    so every CI release APK has a strictly increasing versionCode for seamless Android upgrade installs.
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

val isReleaseAssembleTask = gradle.startParameter.taskNames.any { taskName ->
  taskName.contains("assembleRelease", ignoreCase = true) ||
    taskName.contains("bundleRelease", ignoreCase = true)
}
val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val explicitEnvVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull()

val resolvedVersionCode = when {
  explicitEnvVersionCode != null && explicitEnvVersionCode > 0 -> explicitEnvVersionCode
  ciRunNumber != null && ciRunNumber > 0 -> storedVersionCode + ciRunNumber
  isReleaseAssembleTask -> {
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

val releaseRepoOwner = System.getenv("GITHUB_RELEASE_OWNER")?.takeIf { it.isNotBlank() }
  ?: versionProps.getProperty("GITHUB_RELEASE_OWNER", "AnimeXplayXD")
val releaseRepoName = System.getenv("GITHUB_RELEASE_REPO")?.takeIf { it.isNotBlank() }
  ?: versionProps.getProperty("GITHUB_RELEASE_REPO", "PrecisionCAD-Studio")

android {
  namespace = "com.example"
  compileSdk {
    version = release(37) {
      minorApiLevel = 2
    }
  }

  defaultConfig {
    applicationId = "com.precisioncad.studio"
    minSdk = 24
    targetSdk = 37
    versionCode = resolvedVersionCode
    versionName = resolvedVersionName

    buildConfigField("String", "GITHUB_RELEASE_OWNER", "\"$releaseRepoOwner\"")
    buildConfigField("String", "GITHUB_RELEASE_REPO", "\"$releaseRepoName\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  splits {
    abi {
      isEnable = false
    }
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android release and debug packages.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "upload"
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
      // Do not remove or modify this signingConfig assignment.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment.
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
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
      all { testTask ->
        testTask.jvmArgs(
          "--add-opens=java.base/java.lang=ALL-UNNAMED",
          "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
          "--add-opens=java.base/java.io=ALL-UNNAMED",
          "--add-opens=java.base/java.net=ALL-UNNAMED",
          "--add-opens=java.base/java.security=ALL-UNNAMED",
          "--add-opens=java.base/java.text=ALL-UNNAMED",
          "--add-opens=java.base/java.util=ALL-UNNAMED",
          "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED"
        )
        val localRobolectricCache = file("/home/appuser/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7")
        if (localRobolectricCache.isDirectory) {
          testTask.systemProperty("robolectric.dependency.dir", localRobolectricCache.absolutePath)
          testTask.systemProperty("robolectric.offline", "true")
        }
      }
    }
  }
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

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.moshi.kotlin)
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
