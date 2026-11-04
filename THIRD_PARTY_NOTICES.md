# Third-Party Software and Asset Notices

PrecisionCAD Studio incorporates open-source libraries, build tooling, and font assets distributed under their respective upstream open-source licenses. First-party source code in this repository is licensed under the [Apache License, Version 2.0](LICENSE).

---

## 1. Bundled Font Assets

The following TrueType font files are bundled unmodified in the application resources (`app/src/main/res/font/`) and are distributed under the **SIL Open Font License, Version 1.1** (`OFL-1.1`). The full license text is provided in [`third_party_licenses/OFL-1.1.txt`](third_party_licenses/OFL-1.1.txt).

| Font Asset | Embedded Font Family & Version | Upstream Copyright Notice | License | Upstream Project |
| :--- | :--- | :--- | :--- | :--- |
| `app/src/main/res/font/jetbrains_mono.ttf` | `JetBrains Mono Regular` (`Version 2.211`, PostScript name `JetBrainsMono-Regular`) | `Copyright 2020 The JetBrains Mono Project Authors (https://github.com/JetBrains/JetBrainsMono)` | `OFL-1.1` ([Full Text](third_party_licenses/OFL-1.1.txt)) | <https://github.com/JetBrains/JetBrainsMono> |
| `app/src/main/res/font/space_grotesk.ttf` | `Space Grotesk Light Regular` (`Version 2.000`, PostScript name `SpaceGroteskLight-Regular`) | `Copyright 2020 The Space Grotesk Project Authors (https://github.com/floriankarsten/space-grotesk)` | `OFL-1.1` ([Full Text](third_party_licenses/OFL-1.1.txt)) | <https://github.com/floriankarsten/space-grotesk> |

---

## 2. Runtime Libraries Shipped in the Application

All runtime libraries compiled into the PrecisionCAD Studio Android application (`app/build.gradle.kts` and `gradle/libs.versions.toml`) are distributed under the **Apache License, Version 2.0** ([`LICENSE`](LICENSE)).

| Component / Group | Maven Coordinates | Declared Version | License | Upstream Project / Publisher |
| :--- | :--- | :--- | :--- | :--- |
| **AndroidX Core KTX** | `androidx.core:core-ktx` | `1.19.1` | `Apache-2.0` | Android Open Source Project (<https://developer.android.com/jetpack/androidx>) |
| **AndroidX Activity Compose** | `androidx.activity:activity-compose` | `1.13.0` | `Apache-2.0` | Android Open Source Project (<https://developer.android.com/jetpack/androidx>) |
| **AndroidX Lifecycle** | `androidx.lifecycle:lifecycle-runtime-ktx`<br>`androidx.lifecycle:lifecycle-viewmodel-compose`<br>`androidx.lifecycle:lifecycle-runtime-compose` | `2.11.0` | `Apache-2.0` | Android Open Source Project (<https://developer.android.com/jetpack/androidx>) |
| **AndroidX Jetpack Compose** | `androidx.compose:compose-bom`<br>`androidx.compose.ui:ui`<br>`androidx.compose.ui:ui-graphics`<br>`androidx.compose.ui:ui-tooling-preview`<br>`androidx.compose.material3:material3`<br>`androidx.compose.material:material-icons-core`<br>`androidx.compose.material:material-icons-extended` | BOM `2026.09.00` | `Apache-2.0` | Android Open Source Project (<https://developer.android.com/jetpack/compose>) |
| **AndroidX Room Persistence** | `androidx.room:room-runtime`<br>`androidx.room:room-ktx`<br>`androidx.room:room-compiler` (KSP) | `2.8.5` | `Apache-2.0` | Android Open Source Project (<https://developer.android.com/training/data-storage/room>) |
| **Kotlin Standard Library & Coroutines** | `org.jetbrains.kotlin:kotlin-stdlib`<br>`org.jetbrains.kotlinx:kotlinx-coroutines-core`<br>`org.jetbrains.kotlinx:kotlinx-coroutines-android` | Kotlin `2.4.21`<br>Coroutines `1.11.0` | `Apache-2.0` | JetBrains s.r.o. (<https://github.com/JetBrains/kotlin>, <https://github.com/Kotlin/kotlinx.coroutines>) |
| **Square Moshi & Okio** | `com.squareup.moshi:moshi`<br>`com.squareup.moshi:moshi-kotlin`<br>`com.squareup.moshi:moshi-kotlin-codegen` (KSP)<br>`com.squareup.okio:okio` (transitive) | `1.15.2` | `Apache-2.0` | Block, Inc. / Square (<https://github.com/square/moshi>, <https://github.com/square/okio>) |

---

## 3. Build-Time Plugins & Test-Only Dependencies

The following plugins and test frameworks are used during Gradle compilation, static analysis, and unit/instrumentation testing (`testImplementation`, `androidTestImplementation`, `debugImplementation`). They are **not** packaged into the production release APK.

| Component / Group | Maven / Plugin Identifier | Declared Version | License | Scope |
| :--- | :--- | :--- | :--- | :--- |
| **Gradle Wrapper** | `org.gradle:gradle-wrapper` | `9.8.2` | `Apache-2.0` | Build system wrapper |
| **Android Gradle Plugin (AGP)** | `com.android.application` | `9.1.1` | `Apache-2.0` | Gradle build plugin |
| **Kotlin Compose Compiler Plugin** | `org.jetbrains.kotlin.plugin.compose` | `2.4.21` | `Apache-2.0` | Gradle build plugin |
| **Kotlin Symbol Processing (KSP)** | `com.google.devtools.ksp` | `2.3.13` | `Apache-2.0` | Code generation plugin |
| **Secrets Gradle Plugin** | `com.google.android.libraries.mapsplatform.secrets-gradle-plugin` | `2.0.1` | `Apache-2.0` | BuildConfig property injection plugin |
| **Google Services Gradle Plugin** | `com.google.gms.google-services` | `4.5.0` | `Apache-2.0` | Gradle build plugin |
| **JUnit 4** | `junit:junit` | `4.13.2` | `EPL-1.0` | Unit & instrumentation test runner only |
| **Robolectric** | `org.robolectric:robolectric` | `4.17` | `MIT` / `Apache-2.0` | Local JVM unit test environment only |
| **AndroidX Test & Espresso** | `androidx.test:core` (`1.7.0`)<br>`androidx.test:runner` (`1.7.0`)<br>`androidx.test.ext:junit` (`1.3.0`)<br>`androidx.test.espresso:espresso-core` (`3.7.0`)<br>`androidx.compose.ui:ui-test-junit4`<br>`androidx.compose.ui:ui-test-manifest` | BOM `2026.09.00` / `1.7.0` / `1.3.0` / `3.7.0` | `Apache-2.0` | Unit, debug, and instrumentation test suites only |
| **Kotlinx Coroutines Test** | `org.jetbrains.kotlinx:kotlinx-coroutines-test` | `1.11.0` | `Apache-2.0` | Unit test suite only |

---

## 4. License Text References

- **Apache License, Version 2.0**: [`LICENSE`](LICENSE)
- **SIL Open Font License, Version 1.1**: [`third_party_licenses/OFL-1.1.txt`](third_party_licenses/OFL-1.1.txt)
