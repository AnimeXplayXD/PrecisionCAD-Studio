# Build, Versioning & GitHub Actions Release Pipeline

## 1. Android Application Identity

- **Application Name**: `PrecisionCAD Studio` (`app/src/main/res/values/strings.xml` and `metadata.json`)
- **Application ID**: `com.precisioncad.studio` (`app/build.gradle.kts`)
- **Compile / Target SDK**: Android API 36 (`minSdk = 24`)
- **Universal Single-APK Output**: ABI and density splits are disabled (`splits { abi { isEnable = false } }`) so `:app:assembleDebug` and `:app:assembleRelease` always emit exactly one universal installable APK per variant (`app-debug.apk` and `app-release.apk`).

---

## 2. Automated Versioning (`version.properties` + `app/build.gradle.kts`)

- Base version numbers (`VERSION_MAJOR`, `VERSION_MINOR`, `VERSION_PATCH`, `VERSION_CODE`) live in root `version.properties`.
- In GitHub Actions CI, `VERSION_CODE` is computed deterministically as `BASE_CODE + GITHUB_RUN_NUMBER`, guaranteeing strictly increasing version codes across all CI runs.
- Local release builds automatically increment `VERSION_CODE` in `version.properties`.

---

## 3. GitHub Actions Workflow (`.github/workflows/build-signed-apk.yml`)

1. **Modern Android SDK Provisioning**:
   - Uses `android-actions/setup-android@v3` and installs only supported SDK packages (`platform-tools`, `platforms;android-36`, `build-tools;36.0.0`) after accepting licenses. Never references the obsolete `tools` package.
2. **Release Signing & Verification**:
   - Uses repository secrets (`KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) when configured, or generates a deterministic JKS signing key (`alias: upload`) so CI builds remain self-contained.
   - Verifies both `app-release.apk` and `app-debug.apk` with `apksigner verify --verbose --print-certs` and enforces that each variant directory contains exactly 1 APK.
3. **Canonical Artifacts & Public GitHub Release**:
   - Emits:
     - `PrecisionCAD-Studio-v<versionName>-b<versionCode>-signed-release.apk`
     - `PrecisionCAD-Studio-v<versionName>-b<versionCode>-debug.apk`
     - `SHA256SUMS.txt`
   - Automatically publishes a public GitHub Release on pushes to `main`, `master`, or `v*` tags.
