# Build, Versioning & GitHub Actions Release Pipeline

## 1. Android Application Identity & SDK Target

- **Application Name**: `PrecisionCAD Studio` (`app/src/main/res/values/strings.xml` and `metadata.json`)
- **Application ID**: `com.precisioncad.studio` (`app/build.gradle.kts`, namespace `com.example`)
- **Compile SDK**: Android API `37`, minor API level `2` (`compileSdk { version = release(37) { minorApiLevel = 2 } }`)
- **Target SDK**: `37` (`minSdk = 24`)
- **Toolchain**:
  - Gradle Wrapper: `9.3.1` (`./gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`)
  - Android Gradle Plugin (AGP): `9.1.1`
  - Kotlin Compose Plugin: `2.2.10`
  - KSP: `2.3.5`
- **Public Release Repository**: `AnimeXplayXD/PrecisionCAD-Studio` (configured centrally via `BuildConfig.GITHUB_RELEASE_OWNER` and `BuildConfig.GITHUB_RELEASE_REPO`).

---

## 2. Automated Versioning (`version.properties` + `app/build.gradle.kts`)

- Base version numbers (`VERSION_MAJOR=1`, `VERSION_MINOR=18`, `VERSION_PATCH=0`, `VERSION_CODE=18`) are stored in root `version.properties`.
- In GitHub Actions CI, `VERSION_CODE` is computed as `BASE_CODE + GITHUB_RUN_NUMBER` and verified against the GitHub Releases API to guarantee that every new stable Release has a `versionCode` strictly greater than the highest previously published stable release.

---

## 3. Fail-Closed Release Signing & Single-APK Output

- **Fail-Closed Production Signing**:
  - `.github/workflows/build-signed-apk.yml` requires repository signing secrets (`KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, and optional `KEY_ALIAS` / `RELEASE_CERT_SHA256`) before running `:app:assembleRelease`.
  - If signing secrets are missing on a non-PR Release build, the workflow fails closed immediately and never generates a replacement production signing key.
  - `.github/workflows/lint.yml` uses a disposable non-production throwaway key (`lint-throwaway-nonprod.jks`) solely so AGP can evaluate variant `signingConfigs` during `./gradlew :app:lintRelease`.
- **Single-APK Output & Direct Unarchived Artifacts**:
  - Each build variant produces at most one universal APK:
    - Release: `PrecisionCAD-Studio-v<versionName>-b<versionCode>-release.apk`
    - Debug: `PrecisionCAD-Studio-v<versionName>-b<versionCode>-debug.apk`
  - Public GitHub Releases attach **only** the single raw production Release APK (never the Debug APK) and embed structured `<!-- PRECISIONCAD-RELEASE-METADATA ... -->` metadata in the release body for the in-app updater.

---

## 4. Informational Licensing Inventory Workflow

- `.github/workflows/licensing-inventory.yml` runs `scripts/generate_licensing_inventory.py` as an independent, non-blocking (`continue-on-error: true`, `contents: read`) job.
- See [`docs/LICENSING_INVENTORY.md`](LICENSING_INVENTORY.md) for full details.
