# Build, Versioning & GitHub Actions Release Pipeline

## 1. Android Application Identity & SDK Target

- **Application Name**: `PrecisionCAD Studio` (`app/src/main/res/values/strings.xml`)
- **Application ID & Namespace**: `com.precisioncad.studio` (`app/build.gradle.kts`)
- **Application Theme Resource**: `Theme.PrecisionCADStudio` (`app/src/main/res/values/themes.xml`)
- **Compile SDK**: Android API `37`, minor API level `2` (`compileSdk { version = release(37) { minorApiLevel = 2 } }`)
- **Target SDK**: `37` (`minSdk = 24`)
- **Toolchain**:
  - Gradle Wrapper: `9.8.2` (`./gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) with verified `distributionSha256Sum=21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`
  - Android Gradle Plugin (AGP): `9.1.1`
  - Kotlin & Compose Compiler Plugin: `2.4.21`
  - KSP: `2.3.13`
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

## 2. Static Versioning & Release Gate (`version.properties` + `app/build.gradle.kts`)

- Version numbers (`VERSION_MAJOR=1`, `VERSION_MINOR=18`, `VERSION_PATCH=4`, `VERSION_CODE=32`) are stored in root `version.properties`.
- Both Gradle and GitHub Actions CI read `VERSION_CODE` and `VERSION_MAJOR.VERSION_MINOR.VERSION_PATCH` directly from `version.properties` without adding `GITHUB_RUN_NUMBER`, so CI re-runs and build-only dispatches never increment the build number.
- When publishing a stable GitHub Release, CI queries **all paginated** GitHub Releases (`per_page=100`, `page=1..N`) and fails closed unless `VERSION_CODE` is strictly greater than the highest previously published stable release. Non-publishing runs (pull requests, `debug` builds, and `workflow_dispatch` runs with `publish_release=false`) are permitted to build with the current `VERSION_CODE`.
- Manual `version_name_override` values on `workflow_dispatch` are validated via environment variables (`INPUT_VERSION_NAME_OVERRIDE`) without direct shell expansion of `${{ inputs.* }}`.

---

## 3. Fail-Closed 5-Secret Release Signing & Step-Scoped Credential Hygiene

- **Canonical Repository Signing Secrets**:
  Production Release builds in `.github/workflows/build-signed-apk.yml` fail closed and never generate a replacement or deterministic fallback keystore. They require all five repository Actions secrets:
  1. `RELEASE_KEYSTORE_BASE64` (or `KEYSTORE_BASE64`) — Base64-encoded release keystore
  2. `RELEASE_KEYSTORE_PASSWORD` (or `STORE_PASSWORD`) — Keystore password
  3. `RELEASE_KEY_ALIAS` (or `KEY_ALIAS`) — Signing key alias
  4. `RELEASE_KEY_PASSWORD` (or `KEY_PASSWORD`) — Key password
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

## 5. CI Test, Lint Topology & Stall Watchdog Safeguards

- **Separated Serial Gates (`.github/workflows/build-signed-apk.yml`)**:
  - The signed-APK job sets `timeout-minutes: 60` overall and executes unit tests, Robolectric Compose UI tests, and `:app:lintRelease` as five separate serial steps (`timeout-minutes: 15` each) prior to APK assembly — never combined in a single Gradle invocation and never using `continue-on-error: true` or `abortOnError = false`.
  - Every Gradle gate invokes `--no-daemon --no-parallel --max-workers=2 --no-configuration-cache --console=plain --info --stacktrace`, while `app/build.gradle.kts` enables per-test `STARTED`/`PASSED`/`FAILED`/`SKIPPED` logging (`maxParallelForks = 1`) so stalled tests identify exact class and method names immediately.
- **Standalone PR Lint (`.github/workflows/lint.yml`)**:
  - Runs `:app:lintRelease` with `timeout-minutes: 15` on `pull_request` and `workflow_dispatch` (avoiding duplicate `lintRelease` execution on branch/tag pushes where `build-signed-apk.yml` already enforces `:app:lintRelease`) using an isolated, step-scoped throwaway keystore deleted on exit.
- **Non-Secret Watchdog & Report Artifacts**:
  - `scripts/ci-gradle-diagnostics.sh` wraps each CI Gradle step (`--watchdog-seconds 720`). If a step stays active past 12 minutes (or exits non-zero), it records non-secret host CPU/RAM/disk metrics, process command names (`ps ... comm`), and two spaced `jcmd Thread.print -l` samples (`25s` apart) into `$RUNNER_TEMP/precisioncad-ci-diagnostics/` without logging environment variables, passwords, or keystore secrets.
  - Both workflows upload test XML/HTML (`app/build/test-results/testDebugUnitTest/`, `app/build/reports/tests/testDebugUnitTest/`), lint reports (`app/build/reports/lint-results-release.*`), and diagnostics (`precisioncad-ci-diagnostics` / `precisioncad-lint-diagnostics`) using `if: always()`.
- **Local Per-Class Reproduction Commands**:
  ```bash
  ./gradlew :app:testDebugUnitTest --tests 'com.precisioncad.studio.PrecisionCADUnitTest' --no-daemon --console=plain --info --stacktrace
  ./gradlew :app:testDebugUnitTest --tests 'com.precisioncad.studio.CadIncomingDocumentRouterTest' --no-daemon --console=plain --info --stacktrace
  ./gradlew :app:testDebugUnitTest --tests 'com.precisioncad.studio.ExternalCadImportRobolectricTest' --no-daemon --console=plain --info --stacktrace
  ./gradlew :app:testDebugUnitTest --tests 'com.precisioncad.studio.PrecisionCADRobolectricTest' --no-daemon --console=plain --info --stacktrace
  ```

---

## 6. Open-Source Licensing, Visual Asset Copyright & Third-Party Notices

- **First-Party Source Code**: Licensed under the [Apache License, Version 2.0](../LICENSE) (with required Section 4(d) attributions and exclusions in [`NOTICE`](../NOTICE)).
- **Application Identity, Icons & Visual Assets (`All Rights Reserved`)**: The **PrecisionCAD Studio** name, official `com.precisioncad.studio` application identity, and all launcher icons / brand artwork are **Copyright © 2026 AnimeXplayXD — All Rights Reserved** and are excluded from the Apache-2.0 license grant as defined in [`NOTICE`](../NOTICE) and [`TRADEMARKS.md`](../TRADEMARKS.md).
- **Third-Party Libraries & Bundled Fonts**: Documented in [`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md) and [`third_party_licenses/OFL-1.1.txt`](../third_party_licenses/OFL-1.1.txt).
