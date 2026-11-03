# Build, Versioning & GitHub Actions Release Pipeline

## 1. Android Application Identity & SDK Target

- **Application Name**: `PrecisionCAD Studio` (`app/src/main/res/values/strings.xml` and `metadata.json`)
- **Application ID**: `com.precisioncad.studio` (`app/build.gradle.kts`, namespace `com.example`)
- **Compile SDK**: Android API `37`, minor API level `2` (`compileSdk { version = release(37) { minorApiLevel = 2 } }`)
- **Target SDK**: `37` (`minSdk = 24`)
- **Toolchain**:
  - Gradle Wrapper: `9.8.2` (`./gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) with verified `distributionSha256Sum=21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`
  - Android Gradle Plugin (AGP): `9.1.1`
  - Kotlin Compose Plugin: `2.2.10`
  - KSP: `2.3.5`
  - GitHub Actions Runner: `ubuntu-26.04` with JDK `21` (Temurin)
  - Node 24-Compatible Actions:
    - `actions/checkout@v7.0.1`
    - `actions/setup-java@v6.1.0`
    - `actions/setup-python@v7.1.0`
    - `gradle/actions/setup-gradle@v6.4.1`
    - `actions/upload-artifact@v7.0.2`
    - `softprops/action-gh-release@v3.0.3`
    - `android-actions/setup-android@v4.0.4`
- **Public Release Repository**: `AnimeXplayXD/PrecisionCAD-Studio` (configured centrally via `BuildConfig.GITHUB_RELEASE_OWNER` and `BuildConfig.GITHUB_RELEASE_REPO`).

---

## 2. Automated Versioning (`version.properties` + `app/build.gradle.kts`)

- Base version numbers (`VERSION_MAJOR=1`, `VERSION_MINOR=18`, `VERSION_PATCH=3`, `VERSION_CODE=20`) are stored in root `version.properties`.
- In GitHub Actions CI, `VERSION_CODE` is computed as `BASE_CODE + GITHUB_RUN_NUMBER` and verified across **all paginated** GitHub Releases (`per_page=100`, `page=1..N`) to guarantee that every new stable Release has a `versionCode` strictly greater than the highest previously published stable release.
- Manual `version_name_override` values on `workflow_dispatch` are validated via environment variables (`INPUT_VERSION_NAME_OVERRIDE`) without direct shell expansion of `${{ inputs.* }}`.

---

## 3. Fail-Closed 5-Secret Release Signing & Step-Scoped Credential Hygiene

- **Canonical Repository Signing Secrets**:
  Production Release builds in `.github/workflows/build-signed-apk.yml` require all five repository Actions secrets:
  1. `KEYSTORE_BASE64` — Base64-encoded PKCS#12 release keystore
  2. `STORE_PASSWORD` — Keystore password
  3. `KEY_PASSWORD` — Key password
  4. `KEY_ALIAS` — Signing key alias (`precisioncad_release`)
  5. `RELEASE_CERT_SHA256` — 64-character hex SHA-256 certificate fingerprint
- **Pre-Build & Post-Sign Certificate Verification (`scripts/verify_apksigner_certificate.py`)**:
  - Before Gradle runs, the workflow decodes the keystore to `$RUNNER_TEMP/precisioncad-release-<runId>-<attempt>.jks` (`chmod 600`), verifies that `KEY_ALIAS` exists via `keytool -list -v`, extracts the hex value after `SHA256:`, validates that both actual and expected values are 64-character hex strings (`^[0-9a-f]{64}$`), and verifies exact equality with `RELEASE_CERT_SHA256`.
  - After `:app:assembleRelease`, the workflow runs `python3 scripts/verify_apksigner_certificate.py --self-test` (14 unit fixtures) and pipes `apksigner verify --verbose --print-certs` output into `scripts/verify_apksigner_certificate.py --expected "$EXPECTED_CERT_SHA256"`.
  - Unlike naive `awk -F':' '{print $2}'` field splitting (which fails on lines with two colons such as `V2 Signer: certificate SHA-256 digest: <hex>` and extracts the label `certificatesha-256digest`), `scripts/verify_apksigner_certificate.py` validates `Number of signers: 1`, requires exactly one `certificate SHA-256 digest:` line, strips everything through the literal `certificate SHA-256 digest:` marker, normalizes both actual and expected digests to 64-character lowercase hex (`^[0-9a-f]{64}$`), and compares them character-for-character.
- **Step-Scoped Password & Keystore Cleanup**:
  - `STORE_PASSWORD` and `KEY_PASSWORD` are never written to `$GITHUB_ENV`; they are passed only as step-scoped `env:` variables on the steps that decode/verify the keystore and invoke Gradle.
  - A final `if: always()` cleanup step removes the staged keystore from `$RUNNER_TEMP` on every job outcome.
- **Lint Throwaway Keystore Isolation**:
  - `.github/workflows/lint.yml` creates its temporary non-production keystore (`lint-throwaway-nonprod.jks`) inside the same shell step that runs `./gradlew :app:lintRelease` and deletes it via `trap 'rm -f "$THROW_KEY"' EXIT` without writing paths or passwords to `$GITHUB_ENV`.

---

## 4. Single-APK Output & Direct Unarchived Artifacts

- Each build variant produces at most one universal APK:
  - Release: `PrecisionCAD-Studio-v<versionName>-b<versionCode>-release.apk`
  - Debug: `PrecisionCAD-Studio-v<versionName>-b<versionCode>-debug.apk`
- Single-file APK artifacts in `actions/upload-artifact@v7.0.2` use `archive: false` so the `.apk` can be downloaded directly from GitHub Actions without unzipping a wrapper archive.
- Public GitHub Releases attach **only** the single raw production Release APK (never the Debug APK) and embed structured `<!-- PRECISIONCAD-RELEASE-METADATA ... -->` metadata in the release body for the in-app updater.

---

## 5. Informational Licensing Inventory Workflow

- `.github/workflows/licensing-inventory.yml` runs `scripts/generate_licensing_inventory.py` as an independent, non-blocking (`continue-on-error: true`, `contents: read`) job.
- See [`docs/LICENSING_INVENTORY.md`](LICENSING_INVENTORY.md) for full details.
