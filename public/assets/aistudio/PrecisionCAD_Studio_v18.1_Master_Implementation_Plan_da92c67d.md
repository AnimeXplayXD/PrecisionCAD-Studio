# PrecisionCAD Studio — Version 18.1 Master Implementation Plan

**Implementation scope:** Release signing bootstrap, GitHub Actions reliability, current Release lint remediation, licensing-inventory correctness, and release verification.

**Prepared:** 3 November 2026  
**Baseline:** the latest supplied `PrecisionCAD-Studio-main.zip`, together with the matching Release lint report, licensing-inventory artifact, and GitHub Actions failure description.  
**Application identity:** `com.precisioncad.studio`  
**Repository:** `AnimeXplayXD/PrecisionCAD-Studio`  
**Status:** implementation specification; no source changes are implied by this document.

---

## 0. Instructions to the coding agent

### 0.1 Mission

Implement V18.1 as a controlled follow-up to the successfully implemented V18 application. V18.1 is primarily a **release-pipeline and inventory-quality hardening release**. Do not reopen completed V18 CAD/UI features unless a regression is discovered directly caused by a V18.1 change.

The required outcomes are:

1. Provision a permanent Android production signing identity safely, using the user's already-configured, repository-scoped GitHub fine-grained token only for the one-time GitHub Actions secret setup.
2. Preserve the signing identity of any previously distributed PrecisionCAD Studio APK. Never generate a replacement signing key merely to make CI green.
3. Correct the Release workflow so production signing is fail-closed, reproducible, certificate-verified, and publishes exactly one production APK after a successful trusted build.
4. Remove the current 19 Release lint warnings without disabling detectors or hiding warnings in a baseline.
5. Migrate GitHub Actions from deprecated Node 20-era action releases to maintained Node 24-compatible releases and use an explicitly selected runner image.
6. Repair the informational licensing inventory so ScanCode actually runs, all expected dependency configurations are accounted for, the generated SBOM has the correct application version, and every reported licence value has traceable evidence.
7. Keep licensing discovery strictly informational. It must not choose a project licence, evaluate compatibility, approve or reject dependencies, or block builds, merges, or releases.
8. Preserve V18's existing CAD functionality, updater behaviour, application identity, SDK target, icon assets, storage, and file formats.

### 0.2 Execution discipline

- Work against the live GitHub repository `AnimeXplayXD/PrecisionCAD-Studio` and verify its current branch and commit before editing. The ZIP archive has no `.git` history, so its contents do not independently prove the live repository's current HEAD.
- Record the live commit SHA, branch, archive SHA-256 (when the supplied archive is the working baseline), Gradle version, AGP version, Kotlin plugin version, KSP version, JDK version and Android SDK packages before changes.
- Do not mix signing setup, toolchain migration, lint cleanup and licensing-script changes into one unreviewable edit. Make the changes in ordered, testable groups.
- Preserve all existing V18 functional behaviour unless a V18.1 requirement explicitly changes it.
- Never display, echo, log, commit, upload as a normal artifact, or place in application code any GitHub PAT, keystore Base64 value, keystore password, private key or other secret.
- Never weaken the release fail-closed policy, use a Debug key for a production Release, or generate a new key without passing the signing-identity gate in Section 3.4.
- Do not invent a successful build. Report actual commands, results, warnings and uncompleted work.
- If a permission, credential, signing artifact or prior release APK is unavailable, stop at the relevant gate and report exactly what is missing. Do not silently widen permissions or substitute credentials.

### 0.3 V18 is the completed baseline

The V18 application implementation has been reported as successful. V18.1 must preserve, not reimplement, the following:

- Strictly 2D Android CAD application using Kotlin and Jetpack Compose.
- Native project identity and application ID `com.precisioncad.studio`.
- Android `minSdk = 24`, `targetSdk = 37`, and compilation against API 37.2 where the stable SDK package is available.
- Existing `.pcad` native storage, DXF/PDF/SVG interoperability, QDIM, command previews, Grid Snap, ORTHO, OSNAP, Visual Polar Tracking and PolarSnap.
- V18 Paper Space title-block behaviour, zoom-adaptive snapping, shared card motion, OSNAP labels, keyboard/controller layout, and the V18 updater/release catalogue.
- Existing in-app GitHub updater: Internet access is declared in the manifest; package-installation-source permission and Android's system package confirmation remain separate requirements.
- The three supplied launcher-image assets already integrated in the current source archive. V18.1 must not generate or replace these images.
- Existing README and subsystem documentation. Update them rather than creating duplicate files.

Do not change `applicationId` from `com.precisioncad.studio`. The current source still has `namespace = "com.example"`; a namespace/package migration is not required to solve the V18.1 signing failure and is outside this release unless a concrete V18.1 build defect makes it necessary.

### 0.4 V18.1 is not a project-licence decision

The user wants an open-source licence decision to remain separate. This plan only improves identification and reporting of licence evidence for source files, dependencies and bundled materials. Do not create a root `LICENSE` file, select MIT/Apache/GPL or another licence, add licence headers, or make legal compatibility recommendations.

---

## 1. Source baseline and verified observations

### 1.1 Supplied artifacts

| Artifact | Role | Verified identity |
|---|---|---|
| `PrecisionCAD-Studio-main.zip` | Latest source baseline supplied for V18.1 | SHA-256 `a14ff01ce178cb3ace13fb69035525d30c8234b1dbac3e475d2f82f072d394e1` |
| `android-lint-release-reports (1).zip` | Release lint HTML, text and XML report | SHA-256 `3316a742b99be02cc50c65a9eed42c167a6fe684d92551197d14e5a37e3586ae` |
| `precisioncad-licensing-inventory-16c5e3fcdb85938ff037aa5c2376223dc85665ab.zip` | Dependency BOM, source inventory and status report | SHA-256 `b8c203ba847b59d5ad94f9a768429b307d93046ef485f6cebbb9e12242a12fad` |

The licensing artifact records commit `16c5e3fcdb85938ff037aa5c2376223dc85665ab` on `main`. The lint text report is from the same workflow family. The supplied source ZIP has no `.git` directory, so confirm the live repository HEAD and do not claim that the ZIP hash proves it is byte-for-byte identical to that commit.

### 1.2 Current Android/build configuration in the source ZIP

| Setting | Current source value | V18.1 treatment |
|---|---:|---|
| `namespace` | `com.example` | Preserve unless a concrete build defect requires a separate migration |
| `applicationId` | `com.precisioncad.studio` | Preserve exactly |
| `minSdk` | 24 | Preserve |
| `compileSdk` | `release(37) { minorApiLevel = 2 }` | Preserve after confirming stable SDK availability |
| `targetSdk` | 37 | Preserve |
| Android Build Tools in workflows | 37.0.0 | Preserve unless SDK package verification requires a documented stable update |
| CI JDK | 21 | Preserve initially |
| Java source/target compatibility | 11 | Preserve; the JDK used to run Gradle is a separate setting |
| AGP | 9.1.1 | Upgrade through the compatibility gate in Section 5 |
| Gradle Wrapper distribution | 9.3.1 | Upgrade through the compatibility gate in Section 5 |
| Kotlin Compose plugin | 2.4.21 | Preserve unless compatibility testing identifies a specific need |
| KSP | 2.3.13 | Verify actual plugin resolution and processor compatibility; do not change blindly |
| Compose BOM | 2026.09.00 | Preserve unless dependency testing requires a specific correction |
| Room | 2.8.5 | Preserve |
| Lifecycle | 2.11.0 | Preserve |
| Core KTX | 1.19.1 | Preserve |
| Activity Compose | 1.13.0 | Preserve |
| Coroutines | 1.11.0 | Preserve |
| Robolectric | 4.17 | Preserve unless test failures demonstrate a compatibility problem |
| CycloneDX Gradle plugin | 3.5.1 | Reuse as the authoritative dependency/SBOM source where supported |

The current source includes the complete Gradle Wrapper files: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, and `gradle/wrapper/gradle-wrapper.properties`. An earlier V18 inspection described the Wrapper as missing; that observation is superseded by this latest archive. Do not recreate the Wrapper unnecessarily. Confirm `gradlew` executable mode in the live repository and retain the Wrapper JAR/scripts.

### 1.3 Current signing configuration in `app/build.gradle.kts`

The Release signing configuration reads:

- `KEYSTORE_PATH`, defaulting to `${rootDir}/my-upload-key.jks`;
- `STORE_PASSWORD`;
- `KEY_ALIAS`, defaulting to `upload`;
- `KEY_PASSWORD`.

The `release` build type explicitly uses `signingConfigs.getByName("release")`. Preserve this assignment; the source contains comments that it is required for the AI Studio Android build environment.

The CI signing workflow decodes `KEYSTORE_BASE64` into a runner-temporary `.jks` and currently supports legacy alternate secret names for some values. Standardise on the canonical secret names after successful provisioning.

### 1.4 Current Android manifest

The latest source already declares:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

Internet permission is a normal install-time permission; no runtime Internet permission dialog is needed. `REQUEST_INSTALL_PACKAGES` is separate and is used for the direct APK installation flow. Do not add a runtime Internet permission request. Retain the existing PackageInstaller permission behaviour and system confirmation.

### 1.5 Current workflow files

- `.github/workflows/build-signed-apk.yml`
- `.github/workflows/lint.yml`
- `.github/workflows/licensing-inventory.yml`

All three still use older action versions in the supplied source. Exact current action recommendations are in Section 6.

### 1.6 Current documentation

The source already contains:

- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/BUILD_AND_RELEASE.md`
- `docs/COMMANDS_AND_PREVIEWS.md`
- `docs/DIMENSION_SYSTEM.md`
- `docs/GEOMETRY_AND_COORDINATES.md`
- `docs/LICENSING_INVENTORY.md`
- `docs/STORAGE_AND_FORMATS.md`

Update the existing README and documentation where V18.1 changes build, release signing, CI actions, or inventory behaviour. Do not create duplicate README or licensing documents.

---

## 2. V18.1 workstreams and implementation order

| Phase | Workstream | Required completion result |
|---:|---|---|
| 0 | Preflight and baseline | Verify live repository, current commit, token availability, previous APK signing identity, and current workflow state |
| 1 | Release-signing identity gate | Determine whether to reuse an existing production key or safely establish a first production key; do not guess |
| 2 | GitHub secret provisioning | Create the five canonical Actions secrets using the configured fine-grained token and GitHub's encrypted-secret API |
| 3 | Release signing workflow hardening | Require all signing values and certificate fingerprint, verify the decoded key and APK certificate, avoid exposing passwords through workflow files/logs |
| 4 | GitHub Actions runtime migration | Update action versions and pin the runner image; retain least-privilege and correct artifact packaging |
| 5 | Build toolchain alignment | Align AGP/Gradle with Kotlin and KSP; keep API 37.2/target 37 |
| 6 | Current Release lint remediation | Resolve the 19 warnings in the supplied current report |
| 7 | Licensing inventory repair | Run real ScanCode, use actual package evidence, account for expected Gradle configurations and correct SBOM provenance |
| 8 | CI/release end-to-end validation | Demonstrate Debug, PR, Release, signature, artifact and publication paths |
| 9 | Documentation and handoff | Update documentation and provide evidence-based completion report |

Do not begin Phase 2 key generation until Phase 1 confirms signing continuity or an explicitly authorised fresh-install transition.

---

# PART A — RELEASE SIGNING AND GITHUB SECRET PROVISIONING

## 3. Autonomous production release-signing setup

### 3.1 Root cause of the reported workflow failure

The latest `.github/workflows/build-signed-apk.yml` contains the step:

`Prepare Persistent Release Signing Keystore (Fail-Closed Policy)`.

It runs for non-pull-request events when the requested build variant is not Debug-only. For a normal `push` event, `github.event.inputs.build_variant` is empty; the empty value is not equal to `debug`, so a production Release signing step is expected to run.

The step currently checks for:

- `KEYSTORE_BASE64` (or its currently supported alternate `ANDROID_KEYSTORE_BASE64`);
- `STORE_PASSWORD` (or `ANDROID_KEYSTORE_PASSWORD`);
- `KEY_PASSWORD` (or `ANDROID_KEY_PASSWORD`).

If any are absent, it exits with code 1 and reports:

```text
ERROR: Missing required production release signing secrets (KEYSTORE_BASE64, STORE_PASSWORD, KEY_PASSWORD).
Production Release builds fail closed and never generate a replacement signing key.
```

This is a deliberate safety stop. It is not a Kotlin compilation error, a lint failure, a licensing-scan failure, or an Android SDK failure. The build stops before Release assembly.

### 3.2 Token already configured in Google AI Studio

The user has created and configured a fine-grained GitHub Personal Access Token for this one-time provisioning task.

Expected secure credential name, if the user used the recommended name:

`GITHUB_RELEASE_ADMIN_TOKEN`

The token is intended to have:

- Resource owner: the user's GitHub account;
- Repository access: only `AnimeXplayXD/PrecisionCAD-Studio`;
- Repository permission: **Secrets — Read and write**;
- Automatically included Metadata read permission;
- Short expiry (the user was advised to use seven days).

#### Agent preflight

1. Check whether the credential is available to the AI agent's **secure execution environment**, not merely whether it appears in an application-runtime Secrets panel.
2. Check presence without printing the value. A presence test must output only `available` or `unavailable`.
3. Do not echo it, include it in a prompt transcript, place it in a command-line argument, commit it, write it to `.env`, or expose it in a generated artifact.
4. If the expected name is absent, inspect only the secure credential names exposed to the agent. Do not ask the user to paste the token into ordinary chat or source code.
5. If AI Studio's Secrets facility does not inject the value into the agent's shell/tool environment, stop and report that limitation. Do not invent an access path or move the token into application source.
6. Use the credential only to provision the repository Actions secrets. The build workflow itself must not depend on this PAT.

#### Verify the token scope

Use the GitHub repository Actions-secrets public-key endpoint as the least-invasive functional permission test:

`GET https://api.github.com/repos/AnimeXplayXD/PrecisionCAD-Studio/actions/secrets/public-key`

Use headers:

- `Accept: application/vnd.github+json`
- `Authorization: Bearer <securely injected token>`
- `X-GitHub-Api-Version: 2026-03-10` (or the current documented stable API version verified at implementation time).

Expected result: HTTP 200 with `key_id` and `key`. If access is denied, stop and report the HTTP status and safe error description. Do not broaden token permissions without the user's action.

The repository token requires `Secrets: read` for the public-key endpoint and `Secrets: write` for creating/updating repository secrets. The user's selected `Secrets: Read and write` access level covers both operations. Metadata read is automatically included by GitHub.

### 3.3 Distinguish the temporary GitHub token from permanent signing secrets

There are two different credential classes:

**Temporary GitHub administration token**

- Name in the AI Studio secure credential store: `GITHUB_RELEASE_ADMIN_TOKEN` (if that is the configured name).
- Used only by the agent for one-time GitHub secret provisioning.
- Must not be stored as a repository Actions secret.
- Must not be placed in the Android application, APK, build config, `.env`, Git history or workflow YAML.
- After provisioning and verification, the user should revoke/delete this temporary token from GitHub Settings → Developer settings → Personal access tokens → Fine-grained tokens. Do not assume the agent can revoke a user-owned token through its repository-scoped credential.

**Permanent production signing secrets**

These are the repository Actions secrets used by the Release workflow:

| Canonical secret | Meaning | Required |
|---|---|---|
| `KEYSTORE_BASE64` | Base64 encoding of the permanent production keystore bytes | Yes |
| `STORE_PASSWORD` | Keystore password | Yes |
| `KEY_PASSWORD` | Private-key password | Yes |
| `KEY_ALIAS` | Exact alias of the signing key | Yes |
| `RELEASE_CERT_SHA256` | Trusted SHA-256 certificate fingerprint, normalised or normalisable | Yes |

Do not store the temporary PAT in this list.

### 3.4 Mandatory signing-identity continuity gate

**Do not generate a new production keystore until the agent establishes whether an earlier production APK has already been distributed or installed.**

The latest source ZIP contains no `.jks`, `.keystore`, APK or AAB file. The source archive therefore does not itself establish the production signing identity.

The agent must perform these checks in order:

1. Inspect the live repository's published GitHub Releases and their APK assets using the already-authorised GitHub repository connection, if it has suitable read access.
2. Inspect prior successful GitHub Actions artifacts, if available and authorised, for the last production Release APK.
3. Inspect only the expected AI Studio/repository working locations for an existing `my-upload-key.jks` or other documented signing keystore. Do not print passwords or key contents.
4. If a prior production APK is available, download it to a temporary secure workspace and inspect its certificate using Android Build Tools:

   ```bash
   apksigner verify --verbose --print-certs previous-release.apk
   ```

5. If a candidate keystore is found, inspect its certificate using `keytool -list -v` and compare its normalised SHA-256 fingerprint with the previous APK's signing certificate.
6. Record only non-secret metadata: APK package ID, version code/name, signer SHA-256 fingerprint, candidate keystore alias, and whether the fingerprints match.
7. If the original keystore is found and its certificate matches, reuse it. Do not replace it.
8. If the previous APK is found but no matching private keystore is recoverable, stop. A public certificate fingerprint cannot reconstruct the private key. Report the update-installation consequence and request the user's explicit choice of recovery/migration path.
9. If the previous production APK is not accessible and the agent cannot establish whether one has been distributed (including an AI Studio-generated installed build), stop and request the previous APK or an explicit user confirmation that a clean-install migration is acceptable.
10. Only when no previous production signing identity has been distributed/used, or the user explicitly approves the fresh-install transition, may the agent create a new permanent signing identity.

A different signing certificate under the same `applicationId` generally prevents Android from accepting the new APK as an in-place update. Do not treat reinstalling as harmless: it may remove app-private drawing data. Preserve/backup user `.pcad` documents and explain the effect before any authorised fresh-install migration.

### 3.5 Create a permanent keystore only after the gate passes

If a new production signing identity is authorised and no existing identity must be preserved:

- Use `keytool` from the configured JDK.
- Generate exactly one long-lived production signing keystore.
- Use a strong cryptographically random store password and private-key password. Do not use `android`, a repository name, a date, or a fixed literal.
- Use the actual key alias consistently. `upload` may be used if chosen as the initial alias because the current workflow defaults to that alias, but record it explicitly in `KEY_ALIAS`.
- Use a widely supported Android APK signing algorithm/key configuration. Do not experiment with exotic key algorithms or short validity periods.
- Set a long certificate validity appropriate for a long-lived sideloaded application signing identity.
- Verify the key can sign a test APK and that Android `apksigner` verifies the result.
- Never create another production keystore on later builds. Reuse the same key for all compatible updates.

The generated keystore is a permanent production asset. GitHub Actions secrets are not a backup: GitHub does not reveal existing secret values after creation.

### 3.6 Permanent keystore backup requirement

Before completing secret provisioning:

1. Create a separate backup of the permanent keystore.
2. Encrypt the backup with a strong passphrase separate from the keystore's own passwords.
3. Deliver/store the encrypted backup through a secure user-accessible mechanism outside the Git repository and outside ordinary Actions artifacts.
4. Ensure the user has a way to retain the backup and its encryption passphrase separately.
5. Do not claim the backup exists until its bytes are actually created, verified and made available to the user.
6. If the agent workspace cannot safely provide a user-recoverable encrypted backup, stop before generating a new key. Do not generate a key that would exist only in an ephemeral runner or inaccessible AI Studio workspace.

Never commit the keystore, its Base64 encoding, passwords, private key material or an unencrypted backup to Git. Do not upload the production keystore as a normal build artifact.

### 3.7 Provision repository secrets through GitHub's encrypted-secret API

Use GitHub's repository public key and LibSodium encryption. The raw secret value must never be sent unencrypted to the REST API.

#### API sequence

1. Obtain repository public key:

   `GET /repos/AnimeXplayXD/PrecisionCAD-Studio/actions/secrets/public-key`

2. Decode the returned Base64 public key.
3. Encrypt each secret with LibSodium sealed-box encryption (for example, PyNaCl `SealedBox(PublicKey(...)).encrypt(...)`).
4. Base64-encode the encrypted ciphertext.
5. Create/update each secret:

   `PUT /repos/AnimeXplayXD/PrecisionCAD-Studio/actions/secrets/{secret_name}`

   JSON body:

   ```json
   {
     "encrypted_value": "<base64 sealed-box ciphertext>",
     "key_id": "<public-key key_id>"
   }
   ```

6. Use the GitHub API version header documented at implementation time.
7. Apply this operation to the five canonical names in Section 3.3.
8. Keep the GitHub PAT only in the secure environment variable/credential channel. Do not place it in the request body, URL, log message or shell command text.
9. Prefer an in-memory encryption flow. If temporary input files are unavoidable, create them in a private temporary directory, set restrictive permissions, and delete them in a guaranteed cleanup handler.

GitHub CLI `gh secret set` with standard input may be used instead if the agent can authenticate without printing/storing the token. Do not place secret values in command-line arguments. The REST API method above is the canonical fallback.

#### Verification

- List repository Actions secret names using the API endpoint that returns names/metadata only; do not attempt to retrieve secret values.
- Confirm that all five canonical names exist.
- Confirm that the secret update request returned a successful response.
- Confirm that `RELEASE_CERT_SHA256` equals the certificate fingerprint established in Section 3.4.
- Do not report passwords or the Base64 keystore value.
- Do not create extra aliases or alternate duplicate secret names unless required for a one-time migration from the currently supported legacy names.

### 3.8 Temporary credential and file cleanup

- Remove the PAT from the secure execution environment when the provisioning operation is complete, if the environment supports deletion.
- If it cannot be removed programmatically, tell the user to delete the `GITHUB_RELEASE_ADMIN_TOKEN` entry from AI Studio's secure credential store and revoke the token in GitHub.
- Delete temporary plaintext password files, Base64 files, temporary keystore copies and decrypted keystores from the agent workspace after the encrypted backup and GitHub secret write are verified.
- Never print values to confirm cleanup.
- The user should revoke the temporary fine-grained PAT once successful provisioning is confirmed. Do not revoke before all required GitHub secret writes and verification have succeeded.

---

## 4. Release workflow hardening

### 4.1 Preserve fail-closed signing

Production Release builds must continue to fail if any required signing secret is missing, malformed, mismatched or invalid. Never generate an ephemeral or replacement production key inside CI.

Pull-request builds and Debug-only manual builds must remain possible without production signing secrets.

| Event/build selection | Production signing required? | Release publication |
|---|---:|---:|
| Pull request | No; build Debug/test path only | Never |
| Manual Debug-only | No | Never |
| Manual Release-only | Yes | Only if `publish_release=true` |
| Manual both | Yes for Release; Debug uses Debug key | Only if `publish_release=true` |
| Push to configured release branch | Yes | Automatic after successful verification, per current V18 behaviour |
| Version tag push `v*` | Yes | Automatic after successful verification, per current V18 behaviour |

The current manual workflow can build a Release even when `publish_release=false`; that still requires production signing. Retain this fact and make the workflow description explicit.

### 4.2 Required secret preflight

The production preflight must require all five canonical secrets after the bootstrap migration:

- `KEYSTORE_BASE64`
- `STORE_PASSWORD`
- `KEY_PASSWORD`
- `KEY_ALIAS`
- `RELEASE_CERT_SHA256`

The current workflow treats alias and expected fingerprint as optional. V18.1 must make both required for production builds once the canonical secrets have been provisioned. If a legacy alias is retained temporarily, it may be used only as a compatibility fallback and must emit a non-secret migration warning; remove that fallback after canonical provisioning is verified.

### 4.3 Secure keystore staging in GitHub Actions

- Decode the Base64 keystore only into `RUNNER_TEMP`.
- Use a unique file path per run.
- Set restrictive file permissions (for example, mode `600`).
- Fail if Base64 decoding fails, the file is empty, the keystore cannot be read, or the requested alias is absent.
- Verify the keystore SHA-256 certificate fingerprint against `RELEASE_CERT_SHA256` before Gradle signs anything.
- Normalise the fingerprint consistently by removing colons and whitespace and lowercasing hex digits.
- Do not write store or key passwords to `$GITHUB_ENV` or workflow outputs. The current workflow writes `STORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` into `$GITHUB_ENV`; remove that persistence for password values.
- Pass required signing passwords as step-scoped environment variables only to the Release Gradle assembly step, which reads them through `System.getenv()` as already implemented in `app/build.gradle.kts`.
- Passing the non-secret temporary `KEYSTORE_PATH` to the Gradle step is acceptable; do not expose the path as a secret.
- Use shell strict mode (`set -euo pipefail`) where compatible and disable shell tracing around sensitive operations.
- Redact any tool diagnostics that could accidentally include environment values.
- Delete the staged keystore using an `if: always()` cleanup step, including after a failed build.

### 4.4 Keep Gradle signing configuration intact

Do not remove or replace the existing `signingConfigs` or the Release `signingConfig` assignment in `app/build.gradle.kts`.

The Release Gradle task must receive:

- `KEYSTORE_PATH`
- `STORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The Debug build must continue to use its Debug signing configuration and must never be uploaded as the production Release APK.

### 4.5 Version code and version name integrity

The current workflow reads `version.properties`, derives `VERSION_CODE` using the stored base plus `GITHUB_RUN_NUMBER`, and generates a human-readable version name. Preserve the monotonicity objective while hardening the implementation:

- Never allow a published stable version code to be reused or decreased.
- Retrieve all required stable release metadata reliably; do not silently treat GitHub API failure as an empty release list.
- Paginate the release list or use an equivalent reliable latest-stable query so the comparison does not depend on only an arbitrary first 30 releases.
- Parse machine-readable release metadata as the primary source of published `versionCode`; use tag parsing only as a documented legacy fallback.
- Treat missing/malformed version metadata as an explicit warning or stop condition according to whether a safe monotonic comparison can be established. Do not pretend the maximum is zero when the API call failed.
- Validate `version_name_override` before use (for example, constrain it to the agreed numeric version-name pattern). Do not interpolate user-controlled workflow inputs directly into shell source; pass them through environment variables and validate them.
- Preserve the current generated version name and tag convention unless a concrete collision is identified.
- Record the actual package ID, version name, version code, commit SHA, run number and release tag in the Job Summary and release metadata.
- The SBOM root application component must use the actual effective version name for that run; never use a hard-coded `1.18.0` value.

### 4.6 Build verification before publication

Before a Release APK can be published:

1. Run unit tests.
2. Run Release lint.
3. Build exactly one Release APK.
4. Verify the APK using `apksigner verify --verbose --print-certs`.
5. Verify its package ID is exactly `com.precisioncad.studio`.
6. Verify its version name and version code equal the values calculated for the run.
7. Verify the signing-certificate SHA-256 fingerprint matches `RELEASE_CERT_SHA256` and the established production identity.
8. Verify the APK can be installed as an update over a test installation signed with the same certificate, preserving test data.
9. Generate its SHA-256 digest.
10. Only after all preceding checks succeed may the production GitHub Release be published.

Any signing mismatch must fail the Release path. Do not fall back to Debug signing or silently accept a different fingerprint.

### 4.7 Exactly one APK per variant

The workflow must produce no more than:

- one signed production Release APK;
- one Debug APK when a Debug build is requested or a PR uses the Debug validation path.

The current workflow counts APK outputs and fails if more than one is generated. Preserve that protection.

Suggested asset names:

- `PrecisionCAD-Studio-v<versionName>-b<versionCode>-release.apk`
- `PrecisionCAD-Studio-v<versionName>-b<versionCode>-debug.apk`

Do not create duplicate copies under alternate filenames. Public production Releases must contain only the signed Release APK. Debug APKs remain in Actions artifacts for testing and are not attached to public production Releases.

### 4.8 Correct Actions artifact packaging

The current workflow labels APK uploads as “Direct ... Unarchived” but uses `actions/upload-artifact@v4`, which does not implement the `archive: false` direct-file mode.

Migrate the single-APK artifact uploads to a compatible `actions/upload-artifact@v7` release and set:

```yaml
archive: false
```

This mode supports one file per artifact; the uploaded artifact is the raw APK rather than a ZIP. The artifact's effective name is the file name in this mode, so make the APK filename deterministic and correct.

For multi-file lint and licensing report directories, retain normal archive packaging. Do not set `archive: false` for a multi-file report path.

The public GitHub Release asset must also be the raw `.apk` file, not an Actions artifact ZIP.

### 4.9 GitHub Release publication

Preserve the V18 behaviour:

- Automatic Release publication after a successful trusted push to the configured primary release branch, according to the existing policy.
- Version-tag publication for `v*` tags after build/signing verification.
- Manual publication only when the workflow dispatch input explicitly requests publication and a Release variant is included.
- Use the triggering commit's first-line subject as the Release title, with a deterministic fallback only if the subject is empty.
- Generate the unique release tag using actual version/build metadata and commit short SHA; never overwrite an unrelated published release.
- Attach exactly one raw signed Release APK.
- Include package ID, version name/code, commit SHA, tag, build/run number and APK SHA-256 in machine-readable release metadata and human-readable release notes.
- Do not attach the Debug APK to a production Release.
- Do not publish from pull requests.

The GitHub Release repository must be public for anonymous in-app updater downloads. If the source repository remains private, the release distribution repository must be separately public and the workflow must be explicitly configured to publish there using a narrowly scoped credential. Do not expose a private repository's Release assets as if they were anonymously downloadable.

### 4.10 Permissions and untrusted contributions

- Keep `pull_request` workflows free of production signing secrets.
- Do not use `pull_request_target` to execute fork-controlled source code with production credentials.
- Review and protect changes to `.github/workflows/**`, Gradle build logic, signing scripts and release metadata generation.
- Scope `GITHUB_TOKEN` permissions to the minimum needed. The publishing operation needs `contents: write`; read-only build/lint jobs should use `contents: read` where the workflow structure permits.
- The fine-grained bootstrap PAT is not needed during ordinary CI runs. Do not expose it to GitHub Actions jobs.

---

# PART B — GITHUB ACTIONS RUNTIME AND BUILD TOOLCHAIN

## 5. Toolchain and Android SDK alignment

### 5.1 Preserve API 37.2 and target SDK 37

The latest source already declares:

```kotlin
compileSdk {
    version = release(37) {
        minorApiLevel = 2
    }
}

defaultConfig {
    minSdk = 24
    targetSdk = 37
}
```

The three workflows already attempt to install:

- `platforms;android-37.2`
- `build-tools;37.0.0`
- `platform-tools`

Preserve these settings. At implementation time, verify that API 37.2 is available from the stable SDK channel (`sdkmanager --list --channel=0`) and that the installed stable package matches the configured compile SDK. If the stable 37.2 package is unavailable on the runner, use the latest stable API 37 minor package actually published by the stable repository; do not switch to a preview package or lower the target SDK without reporting the blocker.

### 5.2 Recommended compatibility-first Gradle combination

Current source:

- AGP 9.1.1;
- Gradle 9.3.1;
- Kotlin Compose Gradle plugin 2.4.21;
- KSP 2.3.13;
- JDK 21.

A compatibility-first candidate for the current Kotlin plugin is:

| Component | Candidate |
|---|---:|
| Android Gradle Plugin | 9.3.1 |
| Gradle Wrapper | 9.7.0 |
| Kotlin Compose plugin | 2.4.21 |
| KSP | Preserve 2.3.13 initially; verify its resolved artifact and compatibility |
| JDK | 21 |

The current Kotlin compatibility table lists Kotlin Gradle Plugin 2.4.20–2.4.21 as fully supported with AGP up to 9.3.1 and Gradle up to 9.7.0. AGP 9.3 requires Gradle 9.5.0 or newer. This is why AGP 9.3.1 / Gradle 9.7.0 is the preferred compatibility-first candidate rather than blindly adopting the lint detector's newer AGP 9.5.0 / Gradle 9.8.2 suggestions.

Before changing versions:

1. Confirm official AGP 9.3.1 release notes and Gradle 9.7.0 release availability.
2. Confirm the Kotlin plugin's compatibility matrix at implementation time.
3. Confirm KSP 2.3.13 resolves from the configured plugin repository and works with the selected AGP/Kotlin combination and both Room and Moshi processors.
4. Run clean configuration, KSP generation, unit tests, lint and Release assembly.
5. If KSP 2.3.13 does not resolve or fails, inspect its official release notes and use a stable version explicitly compatible with the selected Kotlin/AGP/Gradle combination. Do not guess a version or introduce a release candidate.
6. Do not change Compose BOM, Room, Lifecycle, Core KTX, Activity Compose or Coroutines versions merely to remove already-resolved dependency warnings; they are already updated in the supplied source.

Do not select AGP 9.5.0 and Gradle 9.8.2 solely because lint reports them as newer. The documented fully supported Kotlin range is the governing constraint unless Kotlin is deliberately upgraded to a version that officially supports that combination.

### 5.3 Keep Java language compatibility separate

The Android module compiles source and target compatibility at Java 11 while CI runs Gradle on JDK 21. Keep that distinction. AGP's runtime JDK requirement does not automatically require changing the application's Java bytecode target.

### 5.4 Gradle Wrapper

The complete Wrapper is present in the latest source archive. Update only its distribution version after the selected AGP/Gradle compatibility decision:

- `gradle-wrapper.properties` distribution URL to the chosen Gradle release;
- regenerate/verify Wrapper scripts and JAR using the selected Gradle version;
- set `gradlew` executable in GitHub Actions;
- use `./gradlew` in every workflow, never a globally installed `gradle` command;
- add `distributionSha256Sum` using the exact official Gradle distribution checksum if available and verified;
- verify Windows `gradlew.bat` remains valid.

The Wrapper's actual distribution version must match the value in the build documentation and the CI job summary.

### 5.5 GitHub Actions action versions and Node runtime

The current three workflows use older action references. Update all occurrences consistently.

Recommended current stable patch releases verified while preparing this plan:

| Action | Current source | V18.1 target |
|---|---|---|
| `actions/checkout` | v4 | `v7.0.1` |
| `actions/setup-java` | v4 | `v6.1.0` |
| `gradle/actions/setup-gradle` | v4 | `v6.4.1` |
| `actions/upload-artifact` | v4 | `v7.0.2` |
| `softprops/action-gh-release` | v2 | `v3.0.3` |
| `android-actions/setup-android` | v4 | `v4.0.4` |

Before committing, verify that the exact tags remain available and use Node 24. Prefer immutable full commit SHAs verified against the official release repositories, with a version comment beside each pin. If the project deliberately uses release tags rather than commit SHAs, use the exact stable patch tags above and enable Dependabot updates for GitHub Actions.

Do not use a floating `@main`, `@master`, or unreviewed major tag where an exact stable patch pin is practical.

GitHub removed the Node 20 runner runtime on 23 September 2026. The V18.1 workflows must not continue to depend on Node 20-based JavaScript actions.

### 5.6 Runner image

The source currently uses `runs-on: ubuntu-latest`. GitHub is migrating `ubuntu-latest` to Ubuntu 26.04 during October–November 2026. Use an explicit runner to make the build reproducible:

- Preferred after validation: `ubuntu-26.04`.
- Temporary fallback only if a demonstrated tool/native-library incompatibility blocks the new image: `ubuntu-24.04`.

Do not return to a floating `ubuntu-latest` label after the migration. Record the chosen runner image in CI summaries and `docs/BUILD_AND_RELEASE.md`.

### 5.7 Action-specific packaging

- Single Release APK: `actions/upload-artifact@v7.0.2`, one file, `archive: false`.
- Single Debug APK: same direct-file approach when used.
- Multiple lint report formats: normal archived artifact.
- Multiple licensing report files: normal archived artifact.
- Release publication: raw APK attached by `softprops/action-gh-release@v3.0.3`.

`archive: false` applies only to a single file; do not use it on multi-file report directories.

---

# PART C — CURRENT RELEASE LINT REMEDIATION

## 6. Current lint report: 19 warnings, zero errors

### 6.1 Correct baseline

The older V18 lint discussion covered 73 findings. That report is superseded by the **latest supplied Release lint report**, which records 19 warnings and zero errors. V18.1 must use the 19-warning report as its current lint baseline, not reopen all 73 older findings as if they still existed.

The current findings are:

| Detector | Count | Files/locations |
|---|---:|---|
| `UseOfNonLambdaOffsetOverload` | 9 | `CadBinaryToggleCard.kt` 531, 588, 921, 1125; `CadDialogsAndPanels.kt` 1045, 1106, 3339; `CadWorkspaceScreen.kt` 2429, 2474 |
| `ModifierParameter` | 4 | `CadBinaryToggleCard.kt` 832, 1066; `CadViewportCanvas.kt` 240; `VirtualCursorController.kt` 226 |
| `FrequentlyChangingValue` | 3 | `CadDialogsAndPanels.kt` 851, 866, 881 |
| `AndroidGradlePluginVersion` | 2 | `gradle/libs.versions.toml` line 2; `gradle/wrapper/gradle-wrapper.properties` line 3 |
| `UseKtx` | 1 | `CadAppUpdater.kt` line 1188 |
| **Total** | **19** | **0 lint errors** |

Do not state that the 19 warnings failed the lint job. The supplied lint job completed successfully; the warnings are remaining quality and maintenance findings.

### 6.2 `ModifierParameter` — four findings

Compose expects an optional `Modifier` parameter to be the first optional parameter of a composable function, after all required parameters.

Affected files:

- `CadBinaryToggleCard.kt` around lines 832 and 1066;
- `CadViewportCanvas.kt` around line 240;
- `VirtualCursorController.kt` around line 226.

Required treatment:

1. Inspect each full composable signature and identify all required parameters, including required parameters that currently appear after defaulted optional parameters.
2. Move `modifier: Modifier = Modifier` to the first optional position after all required parameters.
3. Reorder other defaulted parameters after `modifier`.
4. Update call sites where positional argument order changes. Prefer named arguments for these large composables.
5. Preserve parameter defaults, state semantics, semantics/test tags and modifier chaining order.
6. Compile and run UI tests to catch source-call compatibility problems.

Do not merely suppress `ModifierParameter`.

### 6.3 `FrequentlyChangingValue` — three findings in QDIM selectors

**Source:** `CadDialogsAndPanels.kt` around lines 851, 866 and 881.

The three QDIM selectors currently pass:

```kotlin
scrollActivitySignal = scrollState.value
```

The receiving `QDimHorizontalCardSelector` uses `scrollActivitySignal` in the key of a `LaunchedEffect` that waits two seconds before showing the explanatory callout. Passing the exact scroll pixel value causes the parent and selector to recompose/restart on every scroll-position change.

Required correction:

- The help callout needs to know whether scrolling is active, not the exact scroll pixel.
- Change the signal to a Boolean or an equivalent low-frequency event/state that changes when scrolling starts/stops, not on every pixel.
- Prefer `scrollState.isScrollInProgress` (or a `derivedStateOf`/`snapshotFlow` abstraction if the final code needs a lifecycle-safe state bridge).
- While the parent is scrolling, hide the delayed callout and do not start its two-second timer.
- When scrolling stops, start a fresh delay. Show the callout only if the same card remains selected and no card interaction/transition occurs for the full delay.
- Preserve the existing 0 px layout-shift behaviour: the callout must remain an overlay anchored to its card, not inserted into normal scroll layout.
- Ensure the section-local card inactivity timer is not accidentally reset by another section's scroll.

Do not replace the pixel signal with a Boolean if doing so causes the timer to fire while scrolling is still active. Add tests for start-scroll, continuous-scroll, stop-scroll and card-selection changes.

### 6.4 `UseOfNonLambdaOffsetOverload` — nine findings

The warnings occur where state-backed animation values are passed to the value overload of `Modifier.offset`.

Required correction:

- Use the lambda overload where the offset is derived from animated state.
- Convert Dp values to pixels inside the `Density` lambda, for example with `roundToPx()`, and return `IntOffset`.
- Preserve the existing sign, X/Y direction, peek direction, scale, z-index, alignment, RTL semantics and animation timing.
- Preserve touch/hit-test behaviour: the foreground card remains the only tap target while the rear card is decorative.
- Do not remove the card preview/retraction or idle-peek animations to silence the lint warning.
- Do not move state reads into a parent composable if the layout-phase lambda can consume them safely.

Affected locations:

- `CadBinaryToggleCard.kt`: compact and regular rear-card offsets at approximately 531 and 588, and other card motion offsets at 921 and 1125;
- `CadDialogsAndPanels.kt`: selector/action card offsets at approximately 1045, 1106 and 3339;
- `CadWorkspaceScreen.kt`: floating/overlay motion offsets at approximately 2429 and 2474.

After migration, compare animation trajectories and pixel positions in the same viewport density. Small rounding differences must be deliberate and tested.

### 6.5 `UseKtx` — one finding

**Source:** `CadAppUpdater.kt` around line 1188.

Replace:

```kotlin
Uri.parse("package:${appContext.packageName}")
```

with:

```kotlin
"package:${appContext.packageName}".toUri()
```

Import `androidx.core.net.toUri`. Preserve the exact intent action, flags, URI scheme and Android settings destination. Test the installation-source permission settings flow after the change.

### 6.6 `AndroidGradlePluginVersion` — two findings

These are addressed by the toolchain alignment in Section 5, not by lint suppression. Ensure the chosen AGP/Gradle/Kotlin/KSP combination is compatible and that the Wrapper distribution and version catalog agree.

### 6.7 Lint acceptance

After all changes:

- Run `./gradlew :app:lintRelease --no-daemon --stacktrace`.
- Upload HTML, XML and text reports even if a test/build step fails (`if: always()`).
- Confirm zero errors and zero actionable warnings.
- If a narrow false positive remains, document the exact detector, source expression and reason; do not suppress unrelated lines.
- Do not create a baseline to silence these 19 known warnings.

---

# PART D — INFORMATIONAL LICENSING INVENTORY CORRECTION

## 7. Strict scope and non-goals

### 7.1 Purpose

The licensing workflow identifies and reports observed licence/copyright information for:

- first-party source files;
- direct Gradle dependencies;
- transitive Gradle dependencies;
- Gradle plugins and build logic;
- checked-in/bundled assets, including fonts and image/vector resources;
- local JAR/AAR or vendored archives where present;
- copyright notices, SPDX headers, licence text and package metadata.

It must remain **identification only**.

### 7.2 Absolute non-goals

The inventory must not:

- choose or grant a project-wide open-source licence;
- add, replace or modify a root `LICENSE`, `COPYING`, `NOTICE` or copyright file;
- add SPDX source headers or license badges;
- decide whether a package is permitted or prohibited;
- run licence compatibility analysis or produce a compatibility verdict;
- classify a component as legally safe/unsafe, approved/rejected, allowed/blocked;
- remove, replace or activate dependencies based on a licence finding;
- block builds, tests, merges or Release publication based on licence metadata;
- claim that absence of detected information proves an item has no licence.

Use neutral inventory statuses such as `DETECTED`, `NOT_DETECTED`, `UNKNOWN_METADATA`, `AMBIGUOUS`, `MULTIPLE_FINDINGS`, `SCAN_FAILED`, and `NOT_SCANNED`. Never convert these into legal or permission decisions.

### 7.3 The current report's factual baseline

The supplied licensing status/report records:

| Measurement | Reported value |
|---|---:|
| Scan completeness | `INCOMPLETE` |
| Commit SHA | `16c5e3fcdb85938ff037aa5c2376223dc85665ab` |
| Branch | `main` |
| Total dependency records across configurations | 756 |
| Direct dependency records | 61 |
| Transitive dependency records | 695 |
| Deduplicated CycloneDX components | 327 |
| Repository files scanned by the fallback file inspector | 97 |
| Files with detected licence information | 2 |
| Files with detected copyright | 2 |
| Distinct reported identifier | Apache-2.0 |
| Records with no licence/unknown metadata | 275 |
| Omitted Gradle configurations | 2 testDebug configurations |
| ScanCode result | CLI attempted but failed; fallback inspector used |

These are **report output values**, not a validated legal catalogue. In particular, the current script assigns many dependency labels using group-prefix heuristics; the reported Apache-2.0 totals must not be treated as individually verified package declarations until the evidence correction below is implemented.

The status report records:

- `testDebugUnitTestRuntimeClasspath (rc=1)`;
- `testDebugUnitTestCompileClasspath (rc=1)`;
- `scancode CLI exited with 1`, with only the first 200 characters of stderr retained.

The scan therefore cannot be called complete.

### 7.4 Current source-level defects in `scripts/generate_licensing_inventory.py`

The latest source script contains these confirmed defects:

1. `KNOWN_MAVEN_METADATA_LICENSES` assigns licence identifiers from broad Maven group/artifact prefixes. This is not exact GAV evidence and can mislabel an individual artifact.
2. `lookup_maven_license()` assigns `Apache-2.0` (or other values) based on prefix matches without first reading that exact artifact version's POM licence declarations.
3. ScanCode is installed through `pip install scancode-toolkit==32.3.0`; the supplied run shows a CLI import/startup failure.
4. The default status string is `not-installed (used built-in Repository File Inspector)`. If the CLI exists but fails to run, that status is not accurate.
5. ScanCode stderr is truncated to 200 characters, so the actual failure is not diagnosable from the report.
6. The built-in `scan_repository_files()` results are created before ScanCode. The custom CSV/summary/HTML are generated using `dep_records + file_records`; the actual ScanCode JSON is not normalised and merged into those consolidated reports.
7. If ScanCode fails, the script writes a fallback payload named `scancode-source-inventory.json`, making the filename appear to be genuine ScanCode output even when the scanner did not produce it.
8. The CycloneDX root application version is hard-coded as `1.18.0`, while `version.properties` and CI compute the actual build version dynamically.
9. The script returns success even when one or more expected Gradle configurations or ScanCode failed; status JSON says `INCOMPLETE`, but the exit/result reporting does not clearly distinguish completion from partial output.
10. The current configuration list attempts eight named configurations, but two testDebug configuration queries fail and their full error output is not retained.

V18.1 must correct all ten behaviours.

## 8. ScanCode installation and execution

### 8.1 Pin a working stable ScanCode distribution

The current workflow tries `scancode-toolkit==32.3.0` through pip on Python 3.12. The report proves that this installation path did not produce a usable CLI in that run. Do not repeat the same installation unexamined.

The official ScanCode Toolkit release listing available when this plan was prepared shows the stable `v32.5.0` release with a Linux/Python 3.12 standalone archive:

`scancode-toolkit-v32.5.0_py3.12-linux.tar.gz`

Published SHA-256 for that archive:

`638adcd0af576d1f4d5b64dde228724b3ca4fdee2c4de20d88e4356be353f027`

Use the official `aboutcode-org/scancode-toolkit` release asset. Do not confuse the separate `commoncode-v32.5.2` release with the ScanCode Toolkit `v32.5.0` release. Do not use the v33 release candidate in the production inventory workflow.

Installation procedure:

1. Pin the official archive URL/tag and expected SHA-256.
2. Download to the runner temporary directory.
3. Verify the exact SHA-256 before extraction.
4. Extract into a controlled temporary location.
5. Run the extracted `scancode --version` and `scancode --help` as preflight checks.
6. If either fails, capture complete standard output/error and set scanner status to an explicit failure state.
7. Do not silently fall back and report that ScanCode was installed.
8. Use the ScanCode standalone release archive's own configure/runtime instructions. Do not assume a pip installation is equivalent to a complete scanner install.

The scanner's documented Python prerequisite set includes Python 3.12. The source workflow currently runs Python 3.12.3, which is suitable for the selected Python 3.12 archive.

### 8.2 Scan scope and exclusions

Scan the repository snapshot checked out at the triggering commit. Include source, build scripts, documentation, resources, vector files, bundled artwork, bundled fonts, checked-in archives and local JAR/AAR assets where the scanner can inspect them.

Intentionally exclude:

- `.git/` internals;
- Gradle caches and generated build directories;
- generated `reports/` output from the current scan;
- local `.env` files and other secrets;
- local signing keystores, certificates containing private material, APK/AAB build outputs, compiled classes and native build products.

Distinguish **intentional exclusions** from **unexpected read/scan failures**. Intentional exclusions should be listed as excluded-by-policy and must not automatically make the scan incomplete. Unexpected read errors, scanner omissions or skipped expected files must make the scan status `INCOMPLETE`.

Do not exclude bundled fonts or image assets merely because they are binary. ScanCode should inspect them where supported; the built-in inspector should at least record path, media type and SHA-256 where textual licence data cannot be extracted.

### 8.3 Preserve full diagnostics

For every scanner install/run operation, record:

- command/tool version (without secrets);
- exit code;
- complete stdout/stderr to a dedicated log artifact;
- duration;
- timeout state;
- input root and commit SHA;
- output file path and file existence;
- scanner status enum.

Do not truncate diagnostics to 200 characters. If logs can contain environment data, redact environment variables and credentials before writing them; never log the GitHub PAT or signing values.

Scanner state must distinguish at least:

- `NOT_ATTEMPTED`;
- `INSTALL_FAILED`;
- `INSTALLED`;
- `VERSION_CHECK_FAILED`;
- `SCAN_FAILED`;
- `SCAN_COMPLETE`;
- `SCAN_PARTIAL`.

Do not label an installed-but-failing CLI as `not-installed`.

## 9. Dependency inventory and licence evidence

### 9.1 Use the actual resolved dependency graph

The root build already applies the CycloneDX Gradle plugin (`org.cyclonedx.bom`, catalog version 3.5.1). Use the plugin's generated dependency/SBOM output as the canonical resolved dependency graph where its current version supports the V18.1 Gradle/AGP toolchain.

Before calling the task, inspect the plugin's documented task names and output locations or query the project's task list. Do not assume a task path/output path without verifying it in this repository.

Do not treat the existing Python hand-built CycloneDX JSON as more authoritative than the Gradle-resolved graph. Avoid maintaining two contradictory SBOMs. If the plugin output needs enrichment, enrich a copy/normalised representation while retaining the raw plugin output as provenance.

The dependency inventory must preserve:

- group, artifact, version;
- Maven package URL (PURL) where valid;
- direct/transitive relationship;
- resolved Gradle configuration/scope;
- selected variant where available;
- dependency selection/conflict-resolution facts where available;
- Gradle plugin/build-logic dependency records separately;
- source repository/metadata origin where available.

Do not confuse repeated records across debug/release/test configurations with unique components. Report both configuration-level occurrences and deduplicated components explicitly.

### 9.2 Remove group-prefix licence inference

Delete or replace `KNOWN_MAVEN_METADATA_LICENSES` and `lookup_maven_license()` as a broad group-prefix assignment mechanism.

Never assign a licence to an artifact merely because its Maven group starts with `androidx.`, `org.jetbrains.kotlin`, `com.squareup`, `org.robolectric` or another publisher prefix.

For each exact resolved GAV (group:artifact:version):

1. Locate the exact component metadata/POM used by Gradle, preferably in the resolved Gradle module cache or from the repository metadata that resolved the artifact.
2. Parse that exact POM's `<licenses>` declarations (name, URL, distribution when present).
3. Preserve the raw licence name and URL as observed evidence.
4. Convert to a standard SPDX identifier/expression only when the exact name/URL mapping is reliable and unambiguous.
5. If a package POM contains no licence declaration, record `UNKNOWN_METADATA`/`NOT_DETECTED`; do not infer a value from the group.
6. If multiple declarations appear, preserve them as multiple findings or a raw expression; do not silently select one.
7. Record `evidenceOrigin`, exact GAV, POM location/hash or metadata source, and the raw declaration.

If network metadata retrieval is necessary, it must use the same repository URL that Gradle resolved from where possible. Do not invent a POM or treat a publisher's general website as proof for every artifact/version.

### 9.3 Local/vendor assets

Inspect first-party/bundled assets and source files with ScanCode. For each file-level record, preserve:

- relative repository path;
- SHA-256;
- file type and detected package identity if any;
- detected licence expression/identifier or raw label;
- copyright statements;
- evidence line/range or scanner match details where available;
- scanner/tool and version;
- detection status and ambiguity flags.

Do not infer the project owner's copyright or ownership of generated artwork from file presence alone. Record only observed metadata and user-supplied provenance if it exists in the repository.

### 9.4 Plugin/build logic dependencies

The current script runs `./gradlew buildEnvironment` and parses it as `buildscript:classpath`. Retain this information, but verify whether it captures all plugins declared through the plugins DSL and plugin-management resolution.

Inventory:

- root and module Gradle plugins;
- plugin portal/plugin marker artifacts;
- buildscript classpath dependencies;
- included build/convention plugin dependencies if any;
- KSP processor dependencies;
- application runtime/compile dependencies;
- unit-test and instrumentation-test dependencies.

Mark any build-logic/plugin dependency source that cannot be fully resolved or represented. Do not claim the whole build toolchain was inventoried if only `buildEnvironment` was captured.

## 10. Gradle configuration coverage and completeness

### 10.1 Diagnose the two failed test configurations

The current status report records:

- `testDebugUnitTestRuntimeClasspath (rc=1)`;
- `testDebugUnitTestCompileClasspath (rc=1)`.

The source script currently lists these configurations in `CONFIGURATIONS_TO_QUERY`, runs `:app:dependencies --configuration <name>`, and stores only the configuration name and return code in `omitted_configurations`.

V18.1 must capture the full diagnostic and establish why each query failed. Do not guess that the configuration is absent or harmless.

### 10.2 Discover actual resolvable configurations

Use Gradle's model/resolution API or a small temporary init script/task to enumerate the application's resolvable configurations. Classify them into:

- Release compile/runtime;
- Debug compile/runtime;
- Release/Debug unit-test compile/runtime;
- Android instrumentation-test compile/runtime for variants that exist;
- KSP/KAPT processor configurations;
- buildscript and plugin-management dependencies;
- any other resolvable configurations that are part of the current build.

For each discovered expected configuration, record:

- configuration name;
- whether it exists;
- whether it is resolvable;
- resolution result;
- dependency record count;
- full error if not resolvable;
- whether the failure is an intentional non-applicable variant or an unexpected omission.

Only classify a configuration as non-applicable when Gradle model evidence establishes that it does not exist or is not relevant to the current build. Do not silently skip an existing resolvable configuration.

### 10.3 Avoid misleading totals

Report separately:

- dependency records per configuration;
- unique components by PURL/GAV;
- direct component count;
- transitive component count;
- build plugin/tooling components;
- test-only components;
- records with actual licence metadata;
- records with unknown/not-detected metadata.

Do not sum repeated appearances across debug/release/test scopes and label the result as unique libraries.

### 10.4 Dynamic application version

The current Python BOM writer hard-codes the application component version to `1.18.0`, while `app/build.gradle.kts` resolves `versionName` and `versionCode` from `version.properties`, environment overrides, Git tags and `GITHUB_RUN_NUMBER`.

Remove the hard-coded `1.18.0` value.

Obtain the actual effective application version from the same Gradle/CI metadata used by the APK build. The SBOM and status report must record:

- application ID `com.precisioncad.studio`;
- actual `versionName`;
- actual `versionCode`;
- commit SHA;
- branch/ref;
- build/run identifier;
- UTC timestamp;
- Gradle/AGP/JDK/CycloneDX/ScanCode versions.

If the inventory workflow runs separately and does not have an APK build output, use a shared version-calculation script/task or a verified Gradle property print task. Do not duplicate version logic in Python and YAML in a way that can diverge.

## 11. Licensing report architecture and output

### 11.1 Separate raw evidence from normalised reports

Produce separate raw and normalised files:

1. `scancode-raw.json` — actual unmodified ScanCode JSON output; only use this filename when ScanCode actually produced the file.
2. `repository-file-inspector.json` — secondary built-in hash/header inspector output, clearly labelled as a lightweight fallback/evidence source.
3. `dependency-bom.cdx.json` — CycloneDX JSON from the Gradle plugin or a transparently enriched derivative with provenance.
4. `dependency-bom.cdx.xml` — corresponding XML if generated/validated.
5. `licensing-inventory.csv` — consolidated dependency and file-level observations.
6. `licensing-inventory.html` — human-readable report.
7. `licensing-inventory-summary.md` — concise report summary.
8. `licensing-inventory-status.json` — machine-readable completeness/provenance status.
9. `gradle-build-logic-dependencies.txt` or an equivalent raw build-logic inventory.
10. Complete, sanitised scanner/Gradle diagnostic logs.

Do not write a fallback inspector payload into a file named as if it were genuine ScanCode output.

### 11.2 Merge actual ScanCode findings

The current script creates `file_records` through `scan_repository_files()`, runs ScanCode, and then writes `all_records = dep_records + file_records`. It does not merge actual ScanCode JSON file-level results into CSV/HTML/summary.

V18.1 must parse the real ScanCode JSON and normalise its file, package, licence, copyright and detection evidence into the consolidated inventory. Preserve the raw JSON untouched as provenance.

The built-in inspector may supplement ScanCode with hashes or lightweight header observations, but must not replace the scanner while reporting that ScanCode succeeded.

When ScanCode fails:

- retain the fallback inspector output under its own name;
- mark `scanCompleteness = INCOMPLETE`;
- set `scancodeStatus = SCAN_FAILED` or the appropriate state;
- include full diagnostic log path and exit code;
- do not label the output as a completed ScanCode report.

### 11.3 Status schema

`licensing-inventory-status.json` must include at least:

- disclaimer and explicit informational-only scope;
- schema/report-generator version;
- commit SHA, branch/ref, repository identifier and timestamp;
- effective app versionName/versionCode;
- Python and ScanCode versions/status;
- CycloneDX Gradle plugin version and output path;
- Gradle, AGP, Kotlin, KSP, JDK versions;
- scan completeness state;
- expected and completed Gradle configurations;
- per-configuration exit/resolution status and record counts;
- dependency record count and deduplicated component count;
- direct/transitive counts;
- repository files considered/scanned/excluded/skipped;
- licence/copyright detection counts;
- unknown/not-detected/ambiguous counts;
- tool exit codes and diagnostic paths;
- generated artifact list and SHA-256 hashes.

Differentiate intentional exclusions from unexpected skips. `NOT_DETECTED` is not the same as `SCAN_FAILED`. An unknown licence field is not a legal statement.

### 11.4 Completeness definition

Set `COMPLETE` only when all configured scan stages complete successfully and all expected dependency configurations and in-scope repository files are accounted for, except explicit documented intentional exclusions.

Set `INCOMPLETE` when any of the following occurs:

- ScanCode is not installed, fails its version check, times out or returns a non-zero scan result;
- one or more expected dependency configurations fail or are omitted unexpectedly;
- SBOM generation fails or the output does not validate;
- repository files in scope cannot be read/scanned;
- provenance metadata is missing or inconsistent.

A successfully scanned dependency with no declared licence metadata is still a completed scan record with `UNKNOWN_METADATA`/`NOT_DETECTED`; it does not by itself make the scanner run incomplete.

### 11.5 Human-readable presentation

The HTML/Markdown/CSV must show observed information neutrally. Do not use compliance badges or traffic-light colours that imply approval/rejection.

The report must prominently include:

> Informational inventory only. Automated detection reports licence and copyright information observed in source files, package metadata and bundled materials. It does not select a project licence, determine legal obligations, evaluate compatibility, or decide whether a component may be used. Missing or ambiguous findings require separate human investigation; they are not legal conclusions.

### 11.6 CI non-blocking policy

The licensing workflow must remain informational and must never block Android build, test, merge or Release publication.

However, it must not falsely report a complete scan when a tool or configuration failed.

Recommended pattern:

- Keep the licensing job/workflow non-blocking at the workflow level.
- Allow individual install/scan steps to produce visible failures/diagnostics rather than hiding them with nested `continue-on-error` on every step.
- Add an `if: always()` summary step that reads the status file and states `COMPLETE` or `INCOMPLETE`.
- Add an `if: always()` artifact upload step that preserves every report/log generated, even when ScanCode fails.
- Use a warning annotation/Job Summary for incomplete inventories.
- Ensure the Android build/release workflow does not `needs:` or depend on a successful licence-inventory job.

No licence identifier or missing metadata value may fail a build or stop a Release.

---

# PART E — GITHUB WORKFLOWS AND RELEASE VERIFICATION

## 12. Workflow-specific V18.1 changes

### 12.1 `.github/workflows/build-signed-apk.yml`

Required changes:

- update deprecated Actions versions;
- use explicit Ubuntu runner;
- use `./gradlew` and the selected wrapper version;
- keep API 37.2/Build Tools 37.0.0 installation;
- make signing secrets and fingerprint mandatory for production Release;
- remove password values from `$GITHUB_ENV`;
- stage the keystore securely and clean it on every path;
- verify package ID/version/signature/fingerprint before publishing;
- retain the Debug/PR path without production secrets;
- validate manual workflow inputs through environment variables rather than direct shell interpolation;
- preserve monotonic version code with reliable release pagination/error handling;
- upload exactly one raw APK per variant using the v7 direct artifact mode;
- attach only the signed Release APK to the public GitHub Release;
- preserve commit-subject title, unique tag and machine-readable metadata.

### 12.2 `.github/workflows/lint.yml`

Required changes:

- update actions and runner;
- use complete Gradle Wrapper;
- install API 37.2 / Build Tools 37.0.0;
- preserve a non-production throwaway keystore only for lint variant evaluation if AGP requires a signing file path;
- make it impossible for the throwaway key to be used for Release APK assembly or publication;
- run `:app:lintRelease` and upload the HTML, XML and text report files even if lint fails;
- do not require production signing secrets for the lint-only workflow.

### 12.3 `.github/workflows/licensing-inventory.yml`

Required changes:

- update actions/runner/toolchain consistently;
- install checksum-verified ScanCode standalone release;
- use the Gradle Wrapper;
- prepare only non-secret build placeholders when needed for Gradle configuration;
- query and record all expected configurations;
- generate real CycloneDX and ScanCode outputs;
- retain informational-only non-blocking policy;
- publish a clear COMPLETE/INCOMPLETE status;
- upload reports and diagnostics under `if: always()`.

### 12.4 No signing token in normal CI

The fine-grained `GITHUB_RELEASE_ADMIN_TOKEN` is only for one-time repository secret provisioning. Do not add it to any of the three workflow YAML files or to GitHub Actions secrets. Normal release publication uses the workflow's `GITHUB_TOKEN` with the necessary `contents: write` permission.

---

## 13. Testing and acceptance matrix

### 13.1 Token and secret provisioning

| Test | Expected result |
|---|---|
| AI Studio secure credential presence test | Reports available/unavailable without revealing value |
| Repository public-key API request | HTTP 200 using the fine-grained token |
| Wrong repository or missing permission | Stop with safe, exact diagnostic; no permission escalation |
| Encrypt and write secret | Successful encrypted create/update response |
| List repository secret names | All five canonical names present; no values retrieved |
| Token appears in source/workflow/Android resources | Must not occur |
| Token revoked after provisioning | User can revoke; provisioning no longer requires it |

### 13.2 Signing identity

| Test | Expected result |
|---|---|
| Previous Release APK found | Record package/version/certificate fingerprint |
| Candidate keystore matches previous APK | Reuse it; never generate a replacement |
| Previous APK found but original key missing | Stop and request explicit migration decision |
| No previous distributed APK established | Obtain user confirmation before first permanent key creation if any ambiguity remains |
| New/known keystore backup | Encrypted backup is created, verified and user-recoverable |
| Secret names verified | Five canonical names are present |
| Missing/empty keystore secret | Release fails closed |
| Alias absent or wrong | Release fails closed |
| Fingerprint mismatch | Release fails closed |
| Keystore decode failure | Release fails closed and cleans temporary file |
| Debug-only build with no Release secrets | Debug build can complete |
| PR build from fork | No production signing secret exposure |

### 13.3 Release build and publication

- `./gradlew :app:testDebugUnitTest --no-daemon --stacktrace` passes.
- `./gradlew :app:lintRelease --no-daemon --stacktrace` passes with zero actionable warnings.
- `./gradlew :app:assembleRelease --no-daemon --stacktrace` passes with production secrets.
- Exactly one Release APK is produced.
- `apksigner verify --verbose --print-certs` succeeds.
- APK package ID is `com.precisioncad.studio`.
- APK version metadata matches workflow metadata.
- APK signer fingerprint matches `RELEASE_CERT_SHA256`.
- Release APK installs/updates over a compatible prior installation signed by the same certificate.
- Public GitHub Release contains exactly one raw production `.apk`; no Debug APK and no wrapper ZIP.
- Release title comes from the triggering commit subject.
- Release metadata includes version name/code, package ID, commit SHA, tag, run number and SHA-256.
- Anonymous download works when the configured Release repository is public.

### 13.4 Actions/runtime

- No Node 20 deprecation annotations from the updated JavaScript actions.
- `setup-java`, Gradle setup, checkout, artifact upload and release publication use verified stable compatible versions.
- Runner OS is explicitly selected and recorded.
- `./gradlew --version` reports the selected Wrapper distribution and JDK.
- API 37.2 and Build Tools 37.0.0 are installed from the stable SDK repository.
- Workflow YAML passes syntax validation.

### 13.5 Lint

- All 19 current warnings are resolved.
- No new lint errors or warnings are introduced.
- Scroll/callout behaviour remains unchanged except avoiding per-pixel recomposition.
- Card animations retain position/timing and rear-card touch exclusion after lambda-offset conversion.
- `toUri()` creates the same package settings URI and the installation-source flow still works.

### 13.6 Licensing inventory

- ScanCode standalone archive checksum validates.
- `scancode --version` succeeds and recorded version equals the pinned version.
- A genuine ScanCode raw JSON file is produced.
- Full stderr is preserved on deliberate scanner failure.
- The fallback inspector is not mislabeled as ScanCode output.
- Actual ScanCode file records are merged into the consolidated CSV/HTML/summary.
- The broad group-prefix licence mapping is removed.
- Exact POM metadata is parsed per GAV where present.
- Unknown/ambiguous/not-detected results remain explicit.
- Every expected Gradle configuration is either resolved or has a full diagnostic and reason.
- SBOM root version equals actual application build version, not hard-coded `1.18.0`.
- The report lists expected, completed, excluded and failed inputs separately.
- Workflow status says `INCOMPLETE` when any expected scan stage is missing.
- An incomplete licensing scan does not block build/test/release.
- No project licence is selected, written or enforced.

### 13.7 V18 regression protection

V18.1 must not regress:

- Paper Space title-block background/grid isolation and screen-space typography;
- zoom-adaptive Grid Snap and ORTHO separation;
- Visual Polar Tracking acquisition and PolarSnap angle separation;
- shared transient card animations and section-local inactivity timers;
- QDIM multi-card selection and stable scrolling;
- Selective Dimension Removal selection semantics;
- OSNAP labels and scroll stability;
- floating command dock and keyboard/controller positioning;
- controller expansion/collapse animation;
- Home-screen update badge and release catalogue;
- in-app APK download, integrity checks and PackageInstaller confirmation;
- `.pcad` save, DXF, PDF and SVG import/export.

Do not redesign these V18 surfaces as part of V18.1 unless a regression caused by a V18.1 code change is demonstrated.

---

## 14. Required V18.1 implementation sequence

### Phase 0 — Baseline/preflight

1. Check out the latest `main` commit.
2. Record commit SHA and repository URL.
3. Verify the source files and current actions.
4. Record current APK releases/artifacts, where available.
5. Confirm `GITHUB_RELEASE_ADMIN_TOKEN` is available to the agent without exposing it.
6. Test the token's Secrets read/write permission against the repository public-key endpoint.
7. Record current lint and licensing report metrics.

**Stop if:** the token is unavailable/under-scoped, the repository identity is not the expected repository, or a prior signing identity cannot be established.

### Phase 1 — Signing continuity and secure backup

1. Locate previous Release APK(s) and candidate keystore(s).
2. Extract certificate fingerprints from APK and keystore.
3. Reuse the matching original key if available.
4. If no matching key is available but a previous signed app exists, stop for a user migration decision.
5. If a new identity is explicitly appropriate, generate it once.
6. Create and verify a separately encrypted user-recoverable keystore backup.

### Phase 2 — GitHub Actions secret setup

1. Obtain GitHub repository public key.
2. Encrypt the five canonical values with LibSodium sealed-box encryption.
3. Create/update the five repository Actions secrets.
4. Verify secret names only; do not attempt to read the secret values.
5. Record the certificate fingerprint as non-secret provenance.
6. Clean temporary plaintext/staging data.
7. Ask the user to revoke the temporary PAT once all secret writes and verification succeed.

### Phase 3 — Release workflow hardening

1. Require all five signing secret values for production Release builds.
2. Decode and validate the keystore in `RUNNER_TEMP`.
3. Verify alias and fingerprint.
4. Remove password values from `$GITHUB_ENV`.
5. Pass secrets only to Release Gradle assembly step.
6. Add guaranteed temporary-keystore cleanup.
7. Preserve Debug/PR paths without Release credentials.
8. Verify package/version/signature and exact single-APK output.
9. Correct direct artifact uploads and public Release attachment.

### Phase 4 — Actions runtime/toolchain migration

1. Upgrade Actions to the verified current stable patch releases.
2. Pin Ubuntu 26.04 after testing; use 24.04 only as a documented fallback.
3. Upgrade Gradle Wrapper and AGP to the Kotlin-compatible candidate.
4. Confirm KSP plugin resolution and both KSP processors.
5. Preserve JDK 21 and Java 11 bytecode compatibility.
6. Keep API 37.2/target 37.

### Phase 5 — Current 19 lint warnings

1. Reorder four composable `modifier` parameters.
2. Replace nine state-backed offset overloads with lambda offsets.
3. Replace three per-pixel scroll signals with scroll-activity state/events.
4. Replace `Uri.parse` with `String.toUri()`.
5. Resolve AGP/Gradle version warnings through the toolchain migration.
6. Rerun `lintRelease` and verify zero warnings.

### Phase 6 — Licensing inventory correctness

1. Install ScanCode v32.5.0 standalone archive and verify SHA-256.
2. Preserve full CLI diagnostic logs.
3. Replace broad group-prefix licence assumptions with exact POM/metadata evidence.
4. Use the CycloneDX Gradle plugin's verified resolved graph.
5. Discover and report all expected resolvable Gradle configurations.
6. Merge actual ScanCode results into consolidated output.
7. Correct scanner status, report names and SBOM root version.
8. Preserve informational/non-blocking policy.

### Phase 7 — Integrated verification and documentation

1. Run tests, lint and Release assembly.
2. Verify the Release APK package and signing identity.
3. Verify Release artifact is a raw APK and a single production GitHub Release asset.
4. Execute the licensing scan and inspect status/report completeness.
5. Run the V18 regression matrix.
6. Update README and existing docs.
7. Produce the completion report described below.

---

## 15. Documentation changes

Update existing files rather than adding duplicates:

### `README.md`

- Build prerequisites and exact supported JDK/Gradle/AGP/SDK combination.
- Local Debug and Release build commands.
- Release signing model and safe setup summary without including secret names' values.
- How GitHub Releases are published and where the production APK is obtained.
- In-app updater expectations and Android system installation confirmation.
- Link to the informational licensing-inventory documentation.

### `docs/BUILD_AND_RELEASE.md`

- Actual Gradle Wrapper usage and version.
- CI runner image and Action pins.
- Required GitHub Actions secret names (names only, never values).
- The persistent keystore/certificate continuity requirement.
- Encrypted backup requirement.
- Release variant/Debug/PR behaviour.
- One raw production APK release asset and SHA-256 verification.
- Token is a temporary provisioning credential and is not required by normal CI.

### `docs/LICENSING_INVENTORY.md`

- Informational-only purpose and strict non-goals.
- ScanCode/CycloneDX versions and exact provenance.
- How exact package POM evidence is recorded.
- How unknown/ambiguous/not-detected states are represented.
- How completeness is calculated.
- How to retrieve CI artifacts and diagnostics.
- Explicit statement that the inventory is not a project licence grant, compatibility report or permission gate.

Do not document a licence as the selected PrecisionCAD project licence; no such decision has been made in V18.1.

---

## 16. Deliverables

The agent must deliver:

1. Updated `.github/workflows/build-signed-apk.yml`.
2. Updated `.github/workflows/lint.yml`.
3. Updated `.github/workflows/licensing-inventory.yml`.
4. Updated `gradle/wrapper/gradle-wrapper.properties` and verified Wrapper scripts/JAR, if toolchain migration requires it.
5. Updated `gradle/libs.versions.toml` and root/app Gradle scripts only where required for the selected compatible toolchain and lint fixes.
6. Updated `scripts/generate_licensing_inventory.py` and any small tests or helper modules required.
7. Updated `README.md`, `docs/BUILD_AND_RELEASE.md` and `docs/LICENSING_INVENTORY.md`.
8. A new Release lint report showing the final result.
9. A complete licensing inventory artifact with raw scanner output, SBOM, CSV, HTML, Markdown summary, status JSON and diagnostics.
10. A signed Release APK whose package ID, version and signing certificate are verified.
11. GitHub secret provisioning outcome (names only, never values).
12. A safe user-recoverable encrypted backup of the production signing keystore, if a new or existing key was available to back up.
13. A detailed implementation/test report.

Do not create a new project licence file. Do not include the signing keystore or GitHub PAT in repository contents or release artifacts.

---

## 17. Required final agent report format

The coding agent must report each item as `PASS`, `FAIL`, `BLOCKED` or `NOT RUN`, with evidence.

### A. Repository and credential preflight

- live repository and commit SHA;
- whether `GITHUB_RELEASE_ADMIN_TOKEN` was securely available (boolean only);
- GitHub API public-key permission test result;
- token scope verification result without token value;
- whether the user needs to revoke the PAT.

### B. Signing identity

- whether previous APKs were found;
- previous package ID/version code/name;
- previous signing certificate fingerprint;
- whether the permanent keystore matched;
- whether a new key was generated (and why);
- whether an encrypted backup was created and delivered;
- canonical secret names present (names only);
- no secret values.

### C. Build and workflow

- Gradle/AGP/Kotlin/KSP/JDK/SDK versions;
- workflow action versions and runner image;
- Debug build result;
- Release build result;
- Release APK count and filename;
- package ID/version name/version code;
- signing certificate fingerprint;
- signature verification result;
- Release upload/publication result and asset count.

### D. Lint

- previous warning count: 19;
- final error count;
- final warning count;
- any narrow justified suppressions (ideally none);
- report artifact reference.

### E. Licensing inventory

- ScanCode installation version and checksum verification;
- ScanCode scan outcome and exit code;
- expected/completed/omitted Gradle configurations;
- unique component count and dependency record count;
- source file counts;
- observed identifiers and evidence provenance;
- unknown/ambiguous counts;
- completeness (`COMPLETE` or `INCOMPLETE`);
- report artifact reference;
- explicit confirmation that the scan remained informational and did not gate the build.

### F. Regression

- updater and release catalogue unchanged and tested;
- CAD core, drawing formats and storage regression results;
- any untested or blocked test with reason.

---

## 18. Stop conditions and prohibited shortcuts

Stop and report rather than guessing if:

1. The AI Studio secure credential facility does not expose the configured PAT to the agent's secure execution environment.
2. The token lacks `Secrets: Read and write` for the expected repository.
3. The repository owner/name differs from `AnimeXplayXD/PrecisionCAD-Studio`.
4. A previous production APK is found but its original signing key cannot be recovered.
5. The signing identity of an already distributed installation cannot be established and a new key would risk update compatibility.
6. No secure, user-recoverable keystore backup can be produced.
7. The selected AGP/Gradle/Kotlin/KSP combination is not documented/verified as compatible.
8. API 37.2 is not available as a stable SDK package and a preview would be required.
9. The release versionCode cannot be proven greater than all prior stable published version codes.
10. APK package ID or signing fingerprint does not match the required identity.
11. The production Release can only be built by using a Debug or temporary signing key.
12. ScanCode cannot be installed or its scan fails; mark licensing inventory incomplete and preserve diagnostics rather than fabricating a completed report.
13. Dependency licence metadata is absent or ambiguous; retain unknown/ambiguous status rather than assigning a group-level guess.

Prohibited shortcuts:

- generating a new signing key on every run;
- committing the keystore, Base64 string, passwords or PAT;
- writing passwords to `$GITHUB_ENV` or Job Summary;
- exposing PAT to pull-request or production build steps;
- suppressing the lint detectors to make the report green;
- treating an unknown licence as an approved/prohibited status;
- assigning Apache-2.0 or another licence from a broad Maven group prefix;
- reporting the fallback inspector as ScanCode output;
- truncating the only copy of a failed scanner's diagnostic;
- attaching a Debug APK to the public production Release;
- attaching a zipped artifact in place of the raw production APK;
- changing `applicationId` or losing user drawing data as an incidental toolchain update;
- reimplementing completed V18 features without a demonstrated regression.

---

## 19. Official technical references

These links were checked while preparing this plan. The agent must re-check maintained release tags and compatibility documents at implementation time; never assume a version remains latest indefinitely.

### GitHub Actions secrets and token permissions

- GitHub REST API — Actions secrets: https://docs.github.com/en/rest/actions/secrets
- GitHub fine-grained token permissions: https://docs.github.com/en/rest/authentication/permissions-required-for-fine-grained-personal-access-tokens
- GitHub Actions security hardening: https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions

### Gradle/Android/Kotlin compatibility

- Android Gradle Plugin version/Gradle compatibility: https://developer.android.com/build/releases/about-agp
- Kotlin Gradle Plugin compatibility: https://kotlinlang.org/docs/gradle-configure-project.html
- Android API/SDK platform documentation: https://developer.android.com/tools/releases/platforms
- Gradle Wrapper documentation: https://docs.gradle.org/current/userguide/gradle_wrapper.html

### Actions/runner releases

- actions/checkout releases: https://github.com/actions/checkout/releases
- actions/setup-java releases: https://github.com/actions/setup-java/releases
- gradle/actions releases: https://github.com/gradle/actions/releases
- actions/upload-artifact releases: https://github.com/actions/upload-artifact/releases
- softprops/action-gh-release releases: https://github.com/softprops/action-gh-release/releases
- android-actions/setup-android releases: https://github.com/android-actions/setup-android/releases
- GitHub Ubuntu 26.04 runner notice: https://github.blog/changelog/2026-09-17-ubuntu-26-generally-available-and-latest-migration/

### ScanCode and CycloneDX

- ScanCode Toolkit releases: https://github.com/aboutcode-org/scancode-toolkit/releases/
- ScanCode installation documentation: https://github.com/aboutcode-org/scancode-toolkit/blob/develop/INSTALL.rst
- CycloneDX Gradle plugin: https://github.com/CycloneDX/cyclonedx-gradle-plugin
- CycloneDX Gradle plugin release/plugin page: https://plugins.gradle.org/plugin/org.cyclonedx.bom
- SPDX identifier catalogue: https://spdx.org/licenses/

### Android signing and APK verification

- Android app signing: https://developer.android.com/studio/publish/app-signing
- apksigner documentation: https://developer.android.com/tools/apksigner

---

## 20. V18.1 completion definition

V18.1 is complete only when:

1. The Release signing failure is resolved using the correct persistent signing identity and all five canonical GitHub Actions secrets are verified as present.
2. The temporary fine-grained token is no longer needed and the user has been instructed to revoke it.
3. A secure, recoverable encrypted keystore backup exists.
4. Release signing is fail-closed, certificate-pinned and verified.
5. A successful trusted Release workflow produces exactly one raw signed production APK and publishes it according to the existing V18 release policy.
6. Debug and pull-request builds remain possible without production signing secrets.
7. Current Release lint warnings are zero, without disabled detectors or an artificial baseline.
8. GitHub Actions use maintained Node 24-compatible action releases and an explicitly chosen runner.
9. The licensing inventory uses genuine ScanCode output and an actual resolved dependency graph, with complete provenance and correct `COMPLETE`/`INCOMPLETE` status.
10. Licensing remains informational only and never gates builds or publishing.
11. V18 functionality and user project data remain intact.
12. Existing documentation accurately describes the resulting build, signing and inventory workflows.

---

# Handoff metadata

- Plan version: **18.1**
- Parent baseline: completed V18 implementation
- Source archive: `PrecisionCAD-Studio-main.zip`
- Source archive SHA-256: `a14ff01ce178cb3ace13fb69035525d30c8234b1dbac3e475d2f82f072d394e1`
- Lint report archive SHA-256: `3316a742b99be02cc50c65a9eed42c167a6fe684d92551197d14e5a37e3586ae`
- Licensing report archive SHA-256: `b8c203ba847b59d5ad94f9a768429b307d93046ef485f6cebbb9e12242a12fad`
- Reported CI commit: `16c5e3fcdb85938ff037aa5c2376223dc85665ab`
- Current lint report: 19 warnings, 0 errors
- Current licensing report: `INCOMPLETE`
- Production signing workflow: fails closed because required Actions secrets were not present in the failed run
- User's AI Studio GitHub token: configured by user; agent must verify secure execution access without revealing it
- This document is an implementation plan. It does not claim that the V18.1 source changes, secret provisioning or Release build have already been performed.
