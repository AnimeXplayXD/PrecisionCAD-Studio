# PrecisionCAD Studio — Version 18.2 Master Implementation Plan

**Document type:** Source-aware implementation specification for Google AI Studio coding agent  
**Target application:** PrecisionCAD Studio for Android  
**Target release line:** `1.18.2` (CI may append its generated build suffix)  
**Package ID:** `com.precisioncad.studio`  
**Baseline:** Successfully implemented V18.1 source snapshot supplied for this review  
**Implementation status:** Plan only — no repository source, signing secrets, GitHub settings, or release records were changed while preparing this document.

---

## 0. Execution rule and authority

This document is the complete V18.2 implementation brief. The coding agent must inspect the live repository before editing, compare it with the audited snapshot described below, and then implement only the V18.2 scope. The actual repository checkout and the build output from the failing run take precedence over assumptions in this document.

The V18.1 baseline is considered implemented. Do not recreate, replace, simplify, or regress existing V18.1 features. Treat the work as a focused maintenance and interaction release: preserve the current CAD engine and correct the specific timer, card-deck, navigation, persistence, licensing-bootstrap, and verified build issues described here.

**Stop conditions:**

1. The supplied Build & Sign screenshot establishes the current failure as a missing/inaccessible production-signing-secret set at the fail-closed signing-preparation step. Do not misclassify it as a Gradle or Kotlin compilation failure.
2. Do not alter or regenerate the production signing key, alias, passwords, certificate fingerprint, or signing identity to bypass the confirmed secret-provisioning failure.
3. Do not replace or rework the drawing engine, file formats, geometry model, viewport architecture, or existing launcher assets.
4. Do not remove the informational-only status of the licensing report or turn licence findings into a build/release approval gate.
5. Do not discard an active drawing, pending command, selection, or unsaved edit merely because the user navigates to the Home page.
6. Do not introduce side-by-side Download/Install and Cancel buttons in the Release Catalogue footer.

## 1. Supplied source audit and verified baseline

### 1.1 Supplied material

The following files were present and inspected:

| Material | SHA-256 | Audit result |
|---|---|---|
| `PrecisionCAD-Studio-main.zip` | `1117a9496f95d7ebce54deb55e418143b3a7b1879cbec60f43d63afbc75516e8` | Source snapshot; no `.git` directory was included |
| `android-lint-release-reports (1).zip` | `38b047be6bb4f0168261cb0b46cb7536af872c159b5848b997cb0e456e04d6ba` | Release lint TXT, XML and HTML reports |
| `precisioncad-licensing-inventory-1363111ef36baefc45b2262b6fb69a77b2426bdc.zip` | `adcc430a12226c05f2a517d7b3280ff8d42bebda237ac08e749cacf2214333e8` | Licensing inventory, CycloneDX SBOMs and complete diagnostic bundle |
| `6423.jpg` | user-supplied workspace screenshot | Command-entry area with keyboard visible; Send/Cancel card context |
| `6422.jpg` | user-supplied Release Catalogue screenshot | V18.1 changelog modal with Download and Cancel side-by-side |
| `6432.jpg` | user-supplied GitHub Actions screenshot; SHA-256 `86fc3b403bda6146cf214d2101dfc75f33fa7d23228a1be6cfa20711052412a8` | Build & Sign Android APK run; exact fail-closed signing-secret error and skipped downstream build steps |
| `6433.jpg` | user-supplied GitHub Actions screenshot; SHA-256 `fb9217bc15830ef11e9ef79ea628185969e26697a44661aa2578a8830c3445a1` | Separate Android Lint (Release) run; successful `:app:lintRelease` with zero lint issues and an informational Gradle-version notice |

The source ZIP has no `.git` metadata. The inventory itself reports branch `main`, commit `1363111ef36baefc45b2262b6fb69a77b2426bdc`, and workflow run `41580733313`. The coding agent must verify the actual checked-out commit before implementation; the ZIP alone cannot prove that its source tree exactly corresponds to that commit.

### 1.2 Current project configuration observed in the source snapshot

- `applicationId`: `com.precisioncad.studio`
- `minSdk`: 24
- `targetSdk`: 37
- Compile SDK: Android API 37.2 (`release(37)` with minor API level 2)
- Java source and target compatibility: 11
- JDK used by the supplied licensing run: 21.0.13
- Gradle Wrapper: 9.3.1
- Android Gradle Plugin: 9.1.1
- Kotlin Compose plugin: 2.4.21
- KSP: 2.3.13
- CycloneDX Gradle plugin: 3.5.1
- Version properties in the uploaded source: `VERSION_MAJOR=1`, `VERSION_MINOR=18`, `VERSION_PATCH=1`, `VERSION_CODE=19`
- The build workflow computes a monotonic CI version code using the base code plus `GITHUB_RUN_NUMBER`, and validates it against stable GitHub Releases.
- The manifest already contains `INTERNET` and `REQUEST_INSTALL_PACKAGES`; do not add duplicate permissions.
- The three PrecisionCAD launcher image assets are already integrated. Preserve them byte-for-byte unless a separately approved icon change is requested.

### 1.3 V18.1 baseline to preserve

The following implemented behaviour is not V18.2 reimplementation work:

- Kotlin/Jetpack Compose Android-native 2D CAD workspace and exact double-precision geometry.
- Model Space and Paper Space, title-block isolation, stable screen-space Paper Space typography, sheet units and existing dimension pipeline.
- Zoom-adaptive Grid Snap, separate ORTHO behaviour, Visual Polar Tracking and PolarSnap separation.
- QDIM option-card selectors, card help callouts, selection removal and stable scrolling.
- Shared card-deck visual language, section-local idle timing, lifecycle awareness, and reduced-motion considerations already present in the common card component.
- Floating command dock, keyboard/IME positioning, lower controller dock, viewport overlay arrangement and cursor controls.
- Home screen, saved drawing catalogue, `.pcad` persistence, DXF/PDF/SVG import/export and GitHub Release catalogue/updater.
- Existing project ID, application ID, namespace, storage schema, database, and app-private file behaviour.
- Existing production-signing continuity and fail-closed signing design from V18.1.
- Existing application icon assets and adaptive icon resources.

V18.2 must run regression checks over these features after its targeted changes.

## 2. Release identity and versioning

1. Update `version.properties` from patch `1` to patch `2`: `VERSION_MAJOR=1`, `VERSION_MINOR=18`, `VERSION_PATCH=2`.
2. Preserve the existing CI version-code calculation unless the build log proves it is defective. Do not set an arbitrary fixed `versionCode` and do not bypass the monotonicity check.
3. For branch builds, allow the existing workflow to produce its generated version suffix. For an explicit release tag, use the existing tag-derived version logic.
4. Before publishing, confirm the newly computed version code is strictly greater than every published stable release version code returned by the workflow's paginated GitHub Releases check.
5. Do not create a public release from an unsigned, debug-signed, or incorrectly signed APK.
6. The final report must record the exact version name, version code, tag, commit SHA, and signing certificate SHA-256 without revealing passwords or encoded keystore content.

## 3. Confirmed CI failure — production signing secrets are missing or unavailable

### 3.1 Evidence supplied

The user has now supplied the actual GitHub Actions screenshots and the build's error statement. This replaces the earlier evidence gap; do not ask the user again for the same log excerpt.

**Failed workflow:** `.github/workflows/build-signed-apk.yml`  
**Failed job:** `Build & Sign Android APK`  
**Failed step:** `Prepare Persistent Release Signing Keystore (Fail-Closed Policy)`  
**Observed duration:** the job failed after approximately 38 seconds; the failing guard step itself completed in 0 seconds.

The failing step prints:

```text
ERROR: Missing required production release signing secrets (KEYSTORE_BASE64, STORE_PASSWORD, KEY_PASSWORD, KEY_ALIAS, RELEASE_CERT_SHA256).
Production Release builds fail closed and never generate a replacement signing key.
Error: Process completed with exit code 1.
```

The screenshot shows the following preceding steps completed successfully: checkout, JDK 21 setup, Android SDK setup, API 37.2 / Build-Tools 37.0.0 installation, Gradle setup, Gradle Wrapper verification, automated version calculation/monotonicity check, `.env` preparation, and debug-keystore preparation. The production signing guard is the first failed step.

The following release-path steps were consequently skipped in that run: unit tests and Release lint in the APK workflow, signed Release APK assembly, Debug APK assembly, APK/package/signature verification, Release APK artifact upload, and publication. The screenshot shows separate report/checksum artifact handling, but this does not establish that an APK was built. Do not report that the failed run produced a signed APK.

### 3.2 Root-cause classification

This is **not** a Java/Kotlin source compilation error, Gradle task failure, Android SDK installation error, dependency-resolution failure, lint failure, or APK-signature mismatch. The job deliberately stopped before Gradle unit-test/lint/build tasks because the signing guard did not receive a complete set of production signing values.

The guard checks these five values and aborts if **any one or more** is empty:

| Required GitHub Actions secret | Purpose |
|---|---|
| `KEYSTORE_BASE64` | Base64 representation of the permanent production keystore |
| `STORE_PASSWORD` | Keystore store password |
| `KEY_PASSWORD` | Private-key password |
| `KEY_ALIAS` | Exact alias inside the keystore |
| `RELEASE_CERT_SHA256` | Trusted SHA-256 fingerprint of the signing certificate |

The combined error message lists the entire required set; it does not identify which individual variable(s) were empty. Treat the complete set as unprovisioned until its presence has been verified by secret-name metadata and a successful guarded run. Do not claim that the error message proves five individually distinct absence events.

The current workflow reads these values directly from `${{ secrets.* }}` and the job does not declare a GitHub Actions `environment:`. Therefore, provision the five values as **repository Actions secrets** for `AnimeXplayXD/PrecisionCAD-Studio`. Merely creating similarly named Environment secrets will not make them available to this job unless the workflow is deliberately changed to declare and use that environment. Keep the workflow's fail-closed design intact.

The `.env` file and generated Android **debug** keystore are unrelated to production signing. They do not satisfy, replace, or provide a fallback for any of the five production secrets. Do not weaken the guard or restore a deterministic fallback keystore.

### 3.3 Separate successful Release lint run

The second GitHub screenshot shows a different workflow run, `Android Lint (Release)`, which succeeded. Its `Run Gradle lintRelease` job completed successfully in approximately 7 minutes 45 seconds (7 minutes 49 seconds total workflow duration). The supplied Release lint report also states `No issues found` and has zero XML issue, error, and warning elements.

The job summary displays Gradle `9.3.1` and a notice that the Gradle version is out of date. This is an informational maintenance notice, not the cause of the Build & Sign failure and not a failed task. It is nevertheless a separate maintenance issue that V18.2 must address: update the project wrapper to the latest stable Gradle release identified in §3.7, then prove the full Android toolchain against it. The upgrade must remain independent of the missing-secret repair; it must not weaken or bypass production signing.

### 3.4 Required repair — provision the existing permanent signing identity

The correct repair is to make the **existing, trusted production signing identity** available to the workflow through the five repository Actions secrets. Do not change the APK source, release signing guard, app ID, certificate identity, or Gradle configuration to work around absent secrets.

#### A. Verify signing-key continuity before provisioning

1. Inspect prior GitHub Releases and successful Actions artifacts for a previously distributed production APK.
2. Inspect the authorised AI Studio/repository workspace for the already-established permanent keystore. Never print or expose its bytes, Base64 value, passwords, or private key material.
3. Compare the certificate from a previous production APK (`apksigner verify --verbose --print-certs`) with the candidate keystore certificate (`keytool -list -v`). The SHA-256 fingerprints must match exactly after normalisation.
4. If a prior production APK exists but its matching private keystore cannot be recovered, stop. Do not create a new key; request an explicit migration decision and explain the in-place update and user-data implications.
5. Generate a new key only if no production signing identity has been distributed/used, or the user explicitly authorises a fresh-install transition. A newly generated identity requires a verified, encrypted, user-recoverable backup before provisioning. Never generate a throwaway runner key.

#### B. Verify the temporary GitHub administration credential

The earlier V18.1 workflow established the intended temporary credential name as `GITHUB_RELEASE_ADMIN_TOKEN`. Check whether that fine-grained PAT is available to the AI Studio agent's secure execution environment without displaying its value. Its intended scope is limited to repository `AnimeXplayXD/PrecisionCAD-Studio`, with **Secrets: Read and write** and the automatically included Metadata read permission.

- If the token is available, use it only for the one-time Actions-secret provisioning operation.
- If the token is not available to the agent's secure execution environment, stop and report that limitation. Do not ask the user to paste it into ordinary chat, a source file, `.env`, or a command-line argument.
- Do not store this PAT as a GitHub Actions secret. It is not needed by ordinary CI runs.

#### C. Create/update the five repository secrets securely

Use the permanent key and its verified metadata to populate the exact canonical names in §3.2. Do not create substitute values, duplicate aliases, or a new signing identity to satisfy the check.

For GitHub's REST API, use this sequence:

1. Read the repository Actions public key:
   `GET /repos/AnimeXplayXD/PrecisionCAD-Studio/actions/secrets/public-key`
2. Encrypt each secret value with LibSodium sealed-box encryption using the returned public key and `key_id`.
3. Create or update each exact name with:
   `PUT /repos/AnimeXplayXD/PrecisionCAD-Studio/actions/secrets/{secret_name}`
   and the JSON fields `encrypted_value` and `key_id`.
4. Use the current documented GitHub API version header, the securely injected PAT, and an in-memory encryption flow where practical. Never send raw values to the API, shell history, logs, prompt transcript, workflow YAML, repository files, or artifacts.
5. Confirm the five secret **names** exist using the repository secret metadata/list endpoint. GitHub does not reveal stored secret values; do not attempt to retrieve them.
6. Confirm `RELEASE_CERT_SHA256` is exactly the normalised fingerprint of the established permanent signing certificate. Validate the keystore bytes, passwords, and alias only within the secure execution flow; never log their values.

Because the workflow does not declare an environment, the five secrets must be repository-level Actions secrets. If a future deliberate change introduces an Actions environment, the job must explicitly declare that environment and the secret-provisioning plan must be updated accordingly; do not silently create environment-level secrets and assume this current workflow can read them.

#### D. Cleanup and PAT revocation

After all five repository secret names are confirmed and a guarded release run proves they are usable:

- Delete temporary plaintext/Base64/decoded key files from the agent workspace, using guaranteed cleanup handlers.
- Remove the PAT from the secure execution environment if supported.
- Tell the user to revoke the temporary fine-grained PAT in GitHub after successful provisioning. Do not claim to revoke a user-owned PAT unless an authorised operation actually confirms it.
- Preserve the user's encrypted keystore backup and its separate recovery passphrase; do not delete that backup as part of runner cleanup.

### 3.5 Validation sequence after provisioning

1. Run the existing `workflow_dispatch` **Debug** variant first if an independent compile/build smoke test is required. The production signing guard is intentionally skipped for Debug-only; this does not validate production signing.
2. After the five repository secrets are provisioned, run the **Release** variant using the same guarded workflow. The guard must decode the keystore, find the exact alias, and compare the actual certificate SHA-256 with `RELEASE_CERT_SHA256` before Gradle signing begins.
3. Confirm `:app:testDebugUnitTest`, `:app:lintRelease`, and `:app:assembleRelease` complete successfully in the appropriate run. The independent successful lint run is already evidence for `lintRelease` on its own commit, but rerun it as part of the post-change validation path.
4. Verify the APK with `apksigner verify --verbose --print-certs`; check `applicationId`, version name/code, and the established signer fingerprint.
5. Publish a GitHub Release only when explicitly requested and when the workflow's publication input is enabled. Do not publish from a failed, unsigned, Debug-signed, or fingerprint-mismatched build.

### 3.6 What must not be changed to fix this failure

- Do not remove or bypass `Prepare Persistent Release Signing Keystore (Fail-Closed Policy)`.
- Do not generate a deterministic, hard-coded, debug, or temporary production key.
- Do not make any of the five secrets optional or accept placeholder values.
- Do not place passwords or signing material in `$GITHUB_ENV`, `.env`, source files, repository variables, workflow literals, command-line arguments, build logs, or ordinary artifacts.
- Do not change `applicationId`, alias, signing certificate, `versionCode` policy, or key passwords to bypass the guard.
- Do not present the Gradle Wrapper upgrade in §3.7 as a remedy for this secret-preflight failure; it is a separate toolchain-maintenance requirement and must be validated independently.

**Confirmed diagnosis:** the Release workflow cannot proceed because it does not receive all five required persistent production-signing secrets. The repair is secure provisioning of the correct existing signing identity, followed by a guarded Release rerun—not a code or compiler fix.

### 3.7 Required maintenance — update the Gradle Wrapper

The outdated-version notice in the successful lint workflow must be acted upon in V18.2. It is independent of the signing-secret failure, but it is not to be ignored.

**Verified version selection as of 3 November 2026:**

- Current project Wrapper: `9.3.1`.
- Latest stable Gradle release: **`9.8.2`**, released 19 October 2026. Gradle's release notes recommend upgrading to this patch release; it includes the security fixes introduced in 9.8.1 and an additional 9.8.2 fix. Do not use Gradle `9.9.0-rc1`, a nightly build, or another preview in the production workflow.
- Current Android Gradle Plugin: `9.1.1`. Android's compatibility table gives AGP 9.1 a minimum Gradle version of `9.3.1`, so the existing AGP line's stated minimum is met by `9.8.2`.
- Current Kotlin Gradle/Compose plugin: `2.4.21`. Kotlin's published *fully supported* range for KGP `2.4.20–2.4.21` is Gradle `7.6.3–9.7.0` and AGP `8.5.2–9.3.1`. Therefore Gradle `9.8.2` is one minor release beyond the documented fully-supported Gradle ceiling. Kotlin's documentation permits use of newer Gradle releases but warns that deprecations may appear or some newer features may not work. This is a real compatibility qualification, not a reason to pretend `9.8.2` is fully covered by the matrix.

**V18.2 target:** update `gradle/wrapper/gradle-wrapper.properties` and the checked-in Wrapper scripts/JAR to **Gradle `9.8.2`**, retaining the existing AGP `9.1.1`, Kotlin `2.4.21`, KSP `2.3.13`, JDK 21, Android API 37.2, and Java 11 bytecode settings for the first compatibility pass. Do not bundle unrelated upgrades to AGP, Kotlin, Compose, KSP, SDK levels, or Java bytecode into the wrapper update. Upgrade those only if a reproducible compatibility failure specifically requires it and the replacement versions are verified against official vendor matrices.

**Required implementation sequence:**

1. Verify the live repository's current Wrapper files and baseline build first. Keep the V18.2 source changes isolated from signing-secret provisioning.
2. Upgrade via the Wrapper task, following Gradle's two-pass instruction, for example:
   `./gradlew :wrapper --gradle-version=9.8.2 --distribution-type=bin && ./gradlew :wrapper`
3. Confirm `gradle/wrapper/gradle-wrapper.properties` uses:
   `distributionUrl=https\://services.gradle.org/distributions/gradle-9.8.2-bin.zip`
   Add/update `distributionSha256Sum` using the exact SHA-256 published by Gradle for that exact `gradle-9.8.2-bin.zip` distribution. Do not copy a checksum for the `all` archive or obtain it from an unofficial mirror. Retain the standard Wrapper validation configuration if present.
4. Commit the generated `gradlew`, `gradlew.bat`, `gradle-wrapper.jar`, and wrapper properties as one coherent Wrapper update. Confirm the wrapper JAR is valid and that `./gradlew --version` reports Gradle `9.8.2` running on the intended JDK 21 runtime.
5. Run `./gradlew help --warning-mode=all`. Capture all warnings, especially removed/deprecated Gradle APIs from AGP, Kotlin, KSP, CycloneDX, or project build logic. Resolve actionable warnings where they arise from this project; do not suppress warning categories or hide the Problems report.
6. Execute at minimum `./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug`. Also run all existing build, Compose/UI, dependency-inventory and verification tasks. Run signed Release assembly only after the existing five production signing secrets have been securely provisioned and the fail-closed guard has validated the established signing identity.
7. Exercise the same wrapper in both the lint and build workflows. The successful lint job must report Gradle `9.8.2` and no outdated-version notice. The signed Release job must pass the signing preflight and the build itself; a missing-secret abort does not count as a Gradle 9.8.2 compatibility test.
8. If the project fails specifically under Gradle `9.8.2`, retain the full stack trace and identify the incompatible plugin/task. Do not silently roll back or make unrelated changes. Investigate only the implicated plugin against its official compatibility documentation, propose the smallest supported adjustment, and rerun the complete matrix. `9.7.0` is the documented fully-supported ceiling for the current KGP line, but using it as a fallback would leave the current-version notice unresolved; any such temporary fallback must be explicitly reported as temporary and must not be represented as satisfying the V18.2 final toolchain requirement.

**Acceptance:** the committed wrapper is pinned to Gradle `9.8.2` with its official distribution checksum; `./gradlew --version` confirms the version and JDK; warning-mode output is reviewed; lint, tests, and Debug assembly pass; and the signed Release build passes after signing secrets are restored. The final report must list the exact Wrapper/AGP/KGP/KSP/JDK versions and any remaining compatibility warning.


## 4. V18.2 requirement A — Send / Cancel command-card stack

### 4.1 Source finding

The current Send/Cancel deck is in `CadWorkspaceScreen.kt`, around the persistent command-entry bar. It directly constructs `CadDeckIdleTimerState(scope)` and calls `startOrReset()`, `stop()`, `onUserInteraction()`, and `onOrderExchanged()`.

The shared timer state in `CadBinaryToggleCard.kt` advances its idle and reveal clocks only when driven by `rememberCadDeckIdleTimerState(...)` or `BindCadDeckIdleTimerEffect(...)`. The Send/Cancel deck does not currently attach either driver. The secondary constructor that accepts a `CoroutineScope` delegates to the state constructor; it does not itself start a timer coroutine. Consequently, the direct Send/Cancel state instance has no active 75-second advancement loop. Its intended idle peek cannot be relied upon to run.

The current deck also has independent alpha/scale/rotation properties for both cards and a vertical-drag exchange detector. Preserve its command execution and cancellation semantics while correcting the timer lifecycle and concealed-at-rest presentation.

### 4.2 Required visual state machine

The Send/Cancel deck remains a single compact action position at the right edge of the command input field. It must never render as two adjacent controls.

| State | Foreground card | Rear card | Behaviour |
|---|---|---|---|
| Command deck hidden | Not rendered | Not rendered | Existing visibility rule remains: keyboard visible OR command text non-empty |
| Deck just became visible | Current selected action | Fully concealed behind front | Begin a fresh section-local inactivity interval |
| Direct user interaction | Current selected action | Fully concealed | Reset this deck's timer; do not reset timers in other cards |
| User exchanged card order | Newly selected action | Fully concealed | Update selected action once; reset idle interval |
| 75 seconds of eligible inactivity | Current selected action | Briefly slides into view from the side | Peek for 1 second; no command is executed by the animation |
| Peek completed | Current selected action | Fully concealed | Return smoothly to rest |
| Deck hidden, dialog obscures workspace, or app paused | Not rendered | Concealed and timer stopped | Do not accumulate hidden time |

**Interpretation of “active”:** the deck is active while the command-entry action position is visible. It must initially show only the foreground card. The 75-second idle interval is measured from the last direct interaction with this deck, not from unrelated canvas movement, QDIM scrolling, or other UI activity.

### 4.3 Implementation instructions

1. Replace the un-driven direct state instance with the shared composable timer mechanism. Prefer `rememberCadDeckIdleTimerState(deckId = "command_send_cancel", peekDirection = CardPeekDirection.SIDE, isVisible = isCommandDeckVisible, isEnabled = isCommandDeckVisible)` or the equivalent current shared API.
2. Bind its timer driver through the existing composable lifecycle-aware helper. Do not run a second ad-hoc timer alongside the common state machine.
3. Ensure the command deck timer is eligible only while the deck is visible, interactive, and the host Activity is resumed.
4. On direct input/focus/selection interaction, reset only this deck's idle clock. Do not trigger a post-exchange reveal for the Send/Cancel deck if it conflicts with the rule that the rear action remains concealed during active command operation; the idle-peek interval must start fresh instead.
5. At rest, render the rear action completely behind the foreground card: no exposed red/green strip, label, icon, border, shadow, or hit target. Use correct stacking order, clipping, offsets, and z-index; do not rely on alpha alone if a rear edge can remain visible.
6. At the 75-second threshold, animate the rear card's small lateral slide/rotation/scale reveal, then retract after the 1-second hold. The animation must not change command state or submit/cancel anything.
7. Any direct interaction, deck hide, screen transition, host pause, or reduced-motion state change must cancel or reset the pending peek correctly.
8. Keep existing Send behaviour: when Send is the active foreground card and command text is non-empty, submit once and clear focus using the existing flow.
9. Keep existing Cancel behaviour: when Cancel is the active foreground card, cancel the active command when applicable, clear command text/focus, and close command editing using the established callbacks. Do not let an invisible rear card receive touches.
10. Retain current accessibility semantics and test tags (`command_action_card_stack`, `command_card_send`, `command_card_cancel`) or update tests atomically if the component is extracted.
11. Retain the existing gesture model unless testing proves it conflicts with the requested card concealment. A card exchange must happen once per completed gesture and must not submit a command as a side effect.

### 4.4 Required tests

- At first render and immediately after direct interaction, rear-card alpha/visibility/geometry is fully concealed.
- No idle peek before 75 seconds; a single 1-second peek occurs at the threshold.
- The peek itself does not submit or cancel a command.
- Typing, focusing, tapping, or exchanging the Send/Cancel deck restarts its timer.
- Activity pause/resume and deck hide/show reset eligibility and do not count hidden time.
- Unrelated drawing changes and interactions in other card decks do not restart this timer.
- Send and Cancel each execute at most once per activation.
- Reduced-motion mode suppresses or appropriately minimises the reveal without changing action semantics.

## 5. V18.2 requirement B — Release Catalogue action-card deck

### 5.1 Source finding

`CadReleaseCatalogueDialog.kt` already creates `CadDeckIdleTimerState` and calls `BindCadDeckIdleTimerEffect`, but its fixed footer still lays out the primary Download/Install card and Cancel card in a horizontal `Row`, each with `weight(1f)`. This is exactly the side-by-side arrangement shown in screenshot `6422.jpg`.

### 5.2 Required layout

Replace the horizontal two-button row with one **single-position stacked action-card deck**. The footer must present one foreground action card; the alternate action card is layered behind it. Never place Download/Install and Cancel side by side at any screen width or orientation.

Keep the status subtitle/progress text above the action deck. Preserve the footer's existing place in the dialog so the Release Catalogue list remains scrollable independently and the footer stays anchored at the bottom.

### 5.3 Tap-to-exchange interaction

Use the same motion vocabulary as the existing V18 shared card system: a restrained layered front/rear stack, a brief slide/rotation/scale transition, lifecycle-aware idle peeking, and a direct tap as the input to exchange the selected foreground action.

To avoid making a completely concealed action undiscoverable, include a small explicit exchange affordance on the active foreground card (for example, a compact swap/chevron affordance at the edge) and make the exposed rear-card tab clickable while peeking. The action deck must communicate which action is currently in front.

- Tapping the exchange affordance changes which card is in front and runs the card-exchange animation. It does **not** perform either action.
- Tapping the active foreground card's main body performs that selected action once.
- The rear card cannot execute while it is concealed.
- The 75-second idle peek is visual only; it does not alter the selected card or trigger Download, Install, or Cancel.
- After an exchange, use the common 2.4-second post-exchange reveal/retraction if consistent with the shared deck contract, then return the rear card behind the front. The ordinary 75-second idle interval restarts after interaction.
- Add semantics and adequate hit targets for both the exchange affordance and foreground action. Do not depend solely on a narrow 1–2 dp sliver as the only means to reach the rear action.

### 5.4 Action mapping and state safety

Retain the existing update-state mapping:

| Update state | Primary action card | Secondary action card |
|---|---|---|
| Update available / ordinary checking state | `DOWNLOAD` | `CANCEL` |
| Download in progress | Existing downloading/progress presentation; primary action disabled | `CANCEL` remains available to stop the download/reset the operation |
| Downloaded and verified | `INSTALL` | `CANCEL` / reset using the current callback semantics |
| Install-source permission required | `INSTALL` / permission continuation | `CANCEL` / reset |
| Download or install failure | Preserve the current failure subtitle and existing retry/reset semantics; do not silently relabel a failed operation as successful | `CANCEL` / dismiss or reset according to the current update state |
| Up to date / no downloadable asset | Preserve the current no-update explanation and ensure the primary action cannot start a null or invalid asset download | `CANCEL` / close |

The implementation must inspect the current `CadUpdateUiState` and updater repository before finalising this table. Do not invent a transition that the updater does not support.

- Preserve the current verified-APK requirement before PackageInstaller handoff.
- Preserve the SHA-256 verification step and PackageInstaller permission flow.
- Preserve cancellation semantics while downloading; do not hide Cancel behind a disabled primary card with no exchange route.
- Disable action taps while a state transition is being committed to prevent double downloads, duplicate install intents, or duplicate cancellation callbacks.
- Keep `onDownloadRelease`, `onStartDownload`, `onRequestInstall`, `onCancelOrReset`, and `onDismiss` wired to their existing meanings. Do not call `onDismiss` before a cancellation callback that requires the dialog to remain mounted.
- Keep Download/Install in the existing green treatment and Cancel in the existing destructive-red treatment. Do not change the product palette.

### 5.5 Reuse and architecture

Extract a small reusable action-deck composable only if doing so reduces duplicated state/animation code. It should accept:

- a stable `deckId`;
- foreground and rear action descriptors;
- current foreground selection;
- `onExchange`;
- `onPrimaryAction`;
- an eligibility flag;
- progress/disabled state;
- test tags and accessibility descriptions.

Use `CadDeckIdleTimerState`, `CardPeekDirection`, and the common timer driver rather than duplicating another independent timer. Keep dialog visibility and lifecycle as timer eligibility gates. Do not modify the shared card component's timing rules for unrelated V18.1 cards.

### 5.6 Required tests

- Footer contains a single stacked action surface; no horizontal sibling Download and Cancel buttons.
- The foreground label is correct for each updater state.
- Tap on exchange affordance swaps front/rear and invokes no download/cancel/install callback.
- Tap on the foreground action triggers exactly one matching callback.
- During download, Cancel remains reachable and cancels/reset the active transfer.
- The 75-second peek is visual only and is stopped when the dialog closes or the Activity pauses.
- Scroll/timeline activity does not accidentally reset this footer's timer unless the user directly interacts with the deck.
- The card deck does not obscure the status text, progress bar, close icon, release list, or system navigation in compact portrait layouts.

## 6. V18.2 requirement C — Home-to-canvas return and unsaved session protection

### 6.1 Source finding

The current `CadWorkspaceViewModel.openHomeScreen()` only updates `isHomeScreenVisible = true`; it does not clear `entities`, `layers`, `blocks`, or the active document. This is important: source inspection does not show the Home toggle itself deleting the in-memory drawing model.

However, `CadWorkspaceScreen` currently renders `CadHomeScreen` in an early `if (uiState.isHomeScreenVisible)` branch and then immediately `return`s. That disposes the workspace branch from composition. `CadHomeScreen` has no `onReturnToWorkspace` callback or visible return control. Editor-local state, such as `commandLineInput` stored in a composable, is therefore at risk of being discarded when that branch is removed. Moreover, choosing New Drawing, importing a file, or loading a saved project subsequently replaces the in-memory document. The user currently has no direct route back to the previous active workspace.

### 6.2 Home screen control

Add a clearly visible **Return to Active Drawing** action on the Home page, shown only when an active workspace session exists. It must be available near the top of the Home page without requiring the user to scroll through the saved-drawing catalogue.

- Use a left-arrow / return-to-workspace icon and the explicit label `Return to Active Drawing`.
- Keep it visually distinct from `New Drawing` and `Open Drawing`.
- If the application has only just launched and no active editing session exists, do not display a misleading return action.
- Tapping it must restore the existing in-memory workspace, not create a new blank drawing and not reload an old saved project over the active state.
- The existing canvas Home control remains available and transitions to the Home page using the reverse motion.

### 6.3 Animated page transition

Replace the abrupt early-return page switch with a single coordinated navigation transition. Use the Compose animation framework already present in the source (`AnimatedContent`, `AnimatedVisibility`, or a measured equivalent); do not add a third-party navigation animation library.

Recommended motion:

- Workspace → Home: Home enters from the right with a short horizontal slide and fade; workspace exits in the opposite direction.
- Home → Workspace: the canvas/workspace returns with the reverse slide and fade, producing a deliberate “return to drawing” movement.
- Duration target: approximately 300–420 ms, using a smooth deceleration curve. Respect Android reduced-motion/accessibility settings and avoid excessive parallax.
- Do not scale the drawing viewport, rebase the camera, reset zoom, or resize the CAD canvas merely to animate the page transition.
- Avoid a black flash, duplicated canvas, stale overlay, or interaction leak through the outgoing page.

Use `rememberSaveableStateHolder` with stable page keys such as `home` and `workspace` around the animated content, or an equivalently safe architecture. This is required so `rememberSaveable` editor values survive when the page leaves composition and are restored when the workspace returns. Preserve the ViewModel as the authoritative source for actual drawing entities and command progress.

### 6.4 Active session and document retention

Add an explicit active-session indicator to the ViewModel/UI state. It must become true after starting a new drawing, successfully opening a saved project, importing a valid DXF or PCAD document, and false only when there is no active workspace session. It must remain true when the user navigates to Home.

On Home navigation:

1. Preserve all document data and editor state. Do not call `createNewBlankProject`, `loadProject`, `importDxfString`, or any reset helper.
2. Flush the current automatic quick-save immediately (or perform an equivalent synchronous session checkpoint) before changing the visible page.
3. Preserve the active command, command step points, selection, layers, blocks, units, QDIM state, undo/redo capability, viewport/camera state, and command-line text.
4. Preserve command-line text and editor-local state through the saveable-state holder. If that is not sufficient for process restoration, promote the pending command-line value to ViewModel/SavedStateHandle state.
5. Hide the software keyboard as part of the Home transition where appropriate, but do not clear the field value or cancel the active command. On return, restore field text and command state; the keyboard may remain dismissed until the user taps the field again.
6. Do not mark an unsaved drawing as explicitly saved merely because an autosave/checkpoint file was written.

### 6.5 Protect the drawing when choosing another file

Add reliable dirty-state tracking for the active document. Do not use `canUndo` as the only dirty test; it is an undo capability, not a saved-state identity. Use a document revision / saved revision or an equivalent explicit dirty flag updated by every document mutation, undo, redo, import, new document and explicit save.

If the user is on Home with a dirty active workspace and chooses any action that would replace it (New Drawing, Open Saved Drawing, Open DXF/PCAD, pasted DXF import), present a compact confirmation dialog with three unambiguous choices:

- **Save and Continue** — finish the explicit save successfully, then continue with the pending navigation/import action;
- **Discard and Continue** — discard the active unsaved changes only after the user explicitly confirms, then continue;
- **Stay Here** — dismiss the dialog and retain the active drawing/session unchanged.

Requirements:

- Do not start a file picker or replace the active model until the user's decision is resolved.
- If saving fails, do not continue to the destructive action; show the save failure and retain the drawing.
- Use one pending-action representation so the selected New/Open/Import action is executed once, after confirmation, and cannot be replayed from recomposition.
- If there are no unsaved document changes, allow the requested New/Open action through without an unnecessary prompt.
- A Home → Return to Active Drawing action must never show a discard prompt because it is not destructive.

### 6.6 System Back behaviour

When Home is visible and a live editing session exists, Android Back should return to that active workspace using the same reverse slide transition instead of closing the application immediately. When Home is visible and no active session exists, retain ordinary system Back behaviour. In the workspace, preserve existing BackHandler semantics for the expanded tool drawer and other active overlays; do not steal Back priority from a visible dialog or keyboard.

### 6.7 Required tests

- Home shows `Return to Active Drawing` only when a workspace session exists.
- The return action restores the same project ID, name, entities, layers, blocks, units, selection, active command, command step points, and viewport state.
- Pending command-line text survives Home → Canvas.
- A newly created drawing with unsaved geometry survives Home → Canvas without an explicit save.
- Immediate quick-save/checkpoint is requested on Home navigation, but the drawing remains marked unsaved until explicit save.
- New/Open/Import while dirty prompts Save and Continue / Discard and Continue / Stay Here.
- Save failure blocks replacement; Stay Here preserves everything; Discard works only after explicit selection.
- System Back from Home returns to an existing editing session with animation; first-run Home with no session behaves normally.
- Workspace/Home transition has no canvas zoom reset, viewport resize jump, dropped drawing frame, or input delivered to an obscured page.

## 7. V18.2 requirement D — Licensing inventory and ScanCode recovery

### 7.1 Audit result

The supplied licensing report is substantially improved but is correctly marked `INCOMPLETE`.

Current inventory summary:

| Measure | Supplied result |
|---|---:|
| Unique resolved Maven components | 379 |
| Unique direct components | 105 |
| Unique transitive-only components | 274 |
| Buildscript/plugin components | 167 |
| Test-only components | 56 |
| Components with exact POM licence declarations | 208 |
| Configuration-level dependency records | 1,436 |
| In-scope repository files | 101 |
| ScanCode files merged | 0 |
| Files with a detected licence identifier | 2 |
| Files with a detected copyright notice | 2 |
| Multiple/ambiguous findings | 8 |
| No licence / unknown-metadata records | 291 |
| Unexpected repository read failures | 0 |
| Scan completeness | `INCOMPLETE` |
| ScanCode status | `VERSION_CHECK_FAILED` |
| Python reported by workflow | 3.14.4 |

The CSV contains 1,537 rows: 1,436 configuration-level dependency records plus 101 repository-file records. Repeated configuration occurrences must not be confused with unique dependency components.

### 7.2 Confirmed ScanCode bootstrap cause

The workflow downloaded the pinned ScanCode Toolkit v32.5.0 standalone archive and verified the archive SHA-256 correctly:

`638adcd0af576d1f4d5b64dde228724b3ca4fdee2c4de20d88e4356be353f027`

The archive selected by the workflow is `scancode-toolkit-v32.5.0_py3.12-linux.tar.gz`. Its `configure` script invoked `python3.12`, but the runner exposed Python 3.14.4 and did not have a `python3.12` executable. The exact diagnostic is:

`configure: line 237: python3.12: command not found`

The subsequent `scancode --version` preflight therefore failed. This is a Python runtime mismatch during archive bootstrap, not a SHA-256 mismatch or evidence that the ScanCode archive is corrupt.

### 7.3 Required repair

1. Add an explicit Python 3.12 setup step to `.github/workflows/licensing-inventory.yml` **before** extracting/configuring the ScanCode standalone archive.
2. Use the reviewed stable `actions/setup-python` release `v7.1.0` (or an immutable full commit SHA verified for that release) with `python-version: '3.12.x'`.
3. Immediately verify both `python3.12 --version` and `python --version`; the first must resolve to Python 3.12. Do not silently fall back to Python 3.14.
4. Retain the existing v32.5.0 archive name and SHA-256 unless a new official ScanCode release is deliberately evaluated and its archive checksum is independently verified.
5. Run the standalone archive's `configure` step under Python 3.12. Remove `|| true` from the current configure invocation: setup failure must be a visible failed bootstrap stage, not silently swallowed.
6. Verify `scancode --version` and `scancode --help` successfully before launching the repository scan.
7. Set `SCANCODE_BIN` and the installation-success status only after both preflight checks pass.
8. Run `generate_licensing_inventory.py` using the same Python 3.12 runtime to avoid reporting a mixed/ambiguous interpreter toolchain.
9. Preserve full, untruncated stdout/stderr and the exact return code for download, checksum, configure, version, help and scan stages.
10. Ensure the inventory report accurately distinguishes `ARCHIVE_VERIFIED`, `BOOTSTRAP_FAILED_PYTHON_MISSING`, `VERSION_CHECK_FAILED`, `SCAN_FAILED`, `SCAN_PARTIAL` and `SCAN_COMPLETE`, or equivalent precise labels.
11. If ScanCode cannot run, keep a clearly named `repository-file-inspector.json` fallback report. Do not label the fallback as raw ScanCode output and do not claim ScanCode findings were merged.
12. When ScanCode runs successfully, retain the unmodified `scancode-raw.json` and merge its actual file-level licence/copyright/package results into the consolidated CSV and HTML report.

### 7.4 Dependency configuration inventory

The current expected configuration list includes `releaseUnitTestRuntimeClasspath` and `releaseUnitTestCompileClasspath`. The latest diagnostic says both are absent from the `:app` configuration container and labels them `NOT_PRESENT_IN_VARIANT_MODEL`. The canonical Debug unit-test compile/runtime configurations resolve successfully. These two missing names are not the same as an existing configuration that failed to resolve.

For V18.2:

- Discover the actual Android variant/test configurations created by the current AGP model rather than repeatedly querying hard-coded names that do not exist.
- Include every configuration that actually exists and is intended for the inventory: Debug/Release compile/runtime, available unit-test and instrumentation-test variants, KSP processor classpaths, and the buildscript/plugin classpath.
- Report non-created variant configurations as `NOT_APPLICABLE` (with a short reason) rather than `RESOLUTION_FAILED`.
- Treat an existing, intended, resolvable configuration that fails resolution as a genuine inventory failure, with the full Gradle diagnostic retained.
- Do not report the two current non-existent Release unit-test names as failed dependencies or failed tests.
- Keep configuration-level occurrence counts and deduplicated PURL component counts separate.

### 7.5 Preserve current licence evidence standards

The current script uses exact artifact/version POM metadata and maps a POM declaration to SPDX only when the exact licence name or URL is unambiguous. It explicitly avoids group-prefix licence inference. Preserve this behaviour; do not reintroduce broad group-based defaults.

- Retain exact Maven coordinate `(group, artifact, version)` evidence and POM hash/provenance.
- Follow parent POM licence metadata only when the parent POM is available in the local cache and the exact chain is recorded.
- Preserve raw POM licence names and URLs where no unambiguous SPDX mapping exists.
- Keep unknown metadata as `UNKNOWN_METADATA` or `NOT_DETECTED`; do not convert uncertainty into Apache-2.0, MIT, or another guess.
- Keep the 8 ambiguous/multiple findings visible for later human review.
- Keep the 291 unknown/no-detection records visible; their existence alone is not proof of a compliance defect and must not be concealed.
- Do not select a project licence, assert compatibility, or give a legal conclusion.

### 7.6 SBOM and repository scan outputs

Preserve the current Gradle dependency graph and CycloneDX Gradle plugin as the dependency-source basis. Do not create a competing guessed graph when the existing Gradle graph is available.

Keep these report outputs, with accurate contents and provenance:

- `dependency-bom.cdx.json`
- `dependency-bom.cdx.xml`
- `scancode-raw.json` (only genuine ScanCode output)
- `repository-file-inspector.json` (built-in fallback inspector)
- `licensing-inventory.csv`
- `licensing-inventory.html`
- `licensing-inventory-summary.md`
- `licensing-inventory-status.json`
- `gradle-build-logic-dependencies.txt`
- full diagnostic logs under `diagnostics/`

Continue to scan relevant checked-in source files and bundled assets, including the existing font and image assets where supported. Keep intentional exclusions for `.git`, `.gradle`, build output, report output, `.env`, keystores, APK/AAB files, and compiled outputs. Record intentional exclusions separately from unexpected read failures.

Status provenance must include repository, branch/ref, exact commit, effective application version name/code, UTC timestamp, Python version, ScanCode version/status, Gradle/AGP/Kotlin/KSP/JDK versions, expected and completed configurations, file counts, exclusions, raw output paths, diagnostic paths, and SHA-256 values for generated report files.

### 7.7 Completeness and CI policy

- `COMPLETE` only when ScanCode has completed successfully, every intended resolvable configuration has resolved, report parsing/output validation succeeded, and there were no unexpected file read failures.
- `INCOMPLETE` when ScanCode bootstrap/version/scan fails, an intended resolvable configuration fails, report validation fails, or an unexpected file read failure occurs.
- Non-created Release test configurations do not block completeness when correctly classified as not applicable.
- `UNKNOWN_METADATA` and `NOT_DETECTED` findings do not alone make a technically complete scan incomplete; they remain visible as data-quality findings.
- The licensing workflow must remain informational and non-blocking for Android build/release. Preserve `continue-on-error: true` and artifact upload with `if: always()`.
- Publish a clear warning and report summary to the GitHub Job Summary when the inventory is incomplete. Do not make an incomplete licence scan a reason to silently stop the Android APK build.

### 7.8 Licensing acceptance tests

- The exact Python 3.12 executable is available before ScanCode `configure` runs.
- The existing pinned archive checksum still matches.
- `scancode --version` and `--help` both return success.
- A real `scancode-raw.json` is produced and parses as JSON.
- `scancodeFilesMergedCount` is greater than zero when ScanCode reports files; the merged count is traceable to raw JSON paths.
- Missing Release unit-test configurations are classified as `NOT_APPLICABLE`; they do not appear as resolution failures.
- Actual existing configuration resolution failures remain visible and mark the inventory incomplete.
- Exact POM evidence is preserved; no group-prefix guessing appears.
- CycloneDX JSON and XML parse and contain the same application identity/version and corresponding component set.
- Summary, HTML, CSV, status JSON, diagnostics, and artifact manifest agree on counts and completion state.
- A successful scan reports `COMPLETE`; if a deliberate scanner failure is simulated, the report says `INCOMPLETE` while the Android build workflow remains independent.

## 8. V18.2 requirement E — Release lint and quality gate

### 8.1 Supplied lint result

The newly supplied `lint-results-release.txt` states `No issues found.` The XML report is from Lint 9.1.1 and has:

- `issue` elements: 0
- `error` elements: 0
- `warning` elements: 0

This is a clean Release lint baseline. The earlier V18.1-era count of 19 warnings is superseded by this current report; do not carry those 19 warnings forward as if they remain open.

### 8.2 Requirements

1. Preserve all current clean Release lint findings; do not add broad detector suppressions, `lint.xml` exclusions, or a lint baseline to manufacture a clean result.
2. Run Release lint after all V18.2 changes.
3. If new warnings arise, fix the underlying source issue and retain the same visual, gesture, animation, and command semantics.
4. Keep the lint report as a CI artifact and publish the result in the job summary.
5. Build/test/lint failures must be separate from the informational licensing job status.

### 8.3 Acceptance

- `lintRelease` succeeds.
- XML has zero errors and zero warnings.
- No new detector suppression is added solely to hide V18.2 regressions.

## 9. Test architecture and regression matrix

The coding agent must add or update deterministic tests for the new state transitions. Prefer unit tests for state machines and Compose UI tests for actual click/gesture/layout behaviour.

### 9.1 Command Send/Cancel deck

- Idle timer begins only when its deck is visible and eligible.
- Hidden time, Activity pause time, and time spent under a blocking dialog do not count.
- Direct interaction restarts only the command deck's timer.
- Rear card is fully concealed in rest state and only peeks after the complete idle interval.
- Animation is non-functional visually: it cannot submit/cancel a command.
- One foreground action tap causes one callback.

### 9.2 Release Catalogue deck

- Exactly one action card is presented in the footer at a time.
- The two actions are stacked, not horizontally adjacent.
- Exchange tap animates front/rear order but does not execute an action.
- Foreground action tap invokes the correct existing callback once.
- Cancel remains reachable and functional while a download is active.
- Download/Install availability follows the actual updater state.
- Closing the dialog stops its timer and prevents delayed animation or callbacks.

### 9.3 Home and workspace

- `hasActiveWorkspaceSession` is false at first launch, true after new/open/import, and remains true while Home is visible.
- Return to Active Drawing appears only for a valid active session.
- Returning never creates, imports, reloads, or resets the drawing.
- Document revision and explicit-save revision remain correct across save, undo, redo, import, and Home navigation.
- Destructive New/Open/Import operations prompt only when necessary and honour Save/Discard/Stay.
- Save failure never replaces the current project.
- Page transition is reversible, smooth, and preserves local UI state.
- Device Back from Home returns to active workspace only when one exists.

### 9.4 Existing product regression

Run the existing test suite and add targeted regression coverage for:

- geometry kernel operations and double-precision numeric stability;
- command processing, command cancellation, and multi-step command progress;
- undo/redo;
- viewport state, pan/zoom, fit drawing, and stable canvas sizing;
- Grid Snap, ORTHO, Polar Tracking, PolarSnap, and OSNAP independence;
- QDIM selectors, selection removal, and help-callout timing;
- keyboard/IME and controller-dock positioning;
- `.pcad` save/load and existing DXF/PDF/SVG import/export;
- verified update download, SHA-256 gate, install-source permission and PackageInstaller handoff;
- adaptive launcher icon asset integrity.

Do not weaken existing assertions to make V18.2 pass.

## 10. CI workflow changes and validation sequence

### 10.1 Workflow scope

- `.github/workflows/licensing-inventory.yml`: Python 3.12 setup, strict ScanCode bootstrap, dynamic configuration inventory classification, improved summary/status.
- `.github/workflows/lint.yml`: preserve clean lint behaviour; only adjust if a genuine V18.2 requirement arises.
- `.github/workflows/build-signed-apk.yml`: preserve the existing fail-closed production-signing guard. The confirmed failure is secret provisioning, so do not modify the workflow merely to bypass it. Change the workflow only if a separate defect is demonstrated by a later run.
- `gradle/wrapper/gradle-wrapper.properties`, Wrapper scripts/JAR: update and validate the Wrapper at Gradle `9.8.2` as specified in §3.7. This is a required maintenance task prompted by the successful lint run's outdated-version notice; it is not a workaround for the signing failure.
- `app/build.gradle.kts`, `gradle/libs.versions.toml`: keep AGP `9.1.1`, Kotlin `2.4.21`, and KSP `2.3.13` initially. Change plugin versions only if the Gradle `9.8.2` compatibility test demonstrates a specific incompatibility and official vendor guidance supports the adjustment.

### 10.2 Recommended execution order

1. Confirm repository/branch/commit and correlate the supplied Build & Sign and successful lint screenshots with the live workflow runs; the current signing-guard error is already established.
2. Read the current code around the command deck, release dialog, Home navigation, ViewModel document lifecycle, persistence and test coverage.
3. Add or repair tests for timer state and document/session navigation before changing UI implementation.
4. Repair Send/Cancel timer binding and concealed-at-rest state.
5. Replace Release Catalogue side-by-side action row with the single stacked action deck and tap-to-exchange interaction.
6. Add active-session return navigation, animated page switch, saveable page-state retention and unsaved-document protection.
7. Update the Gradle Wrapper to `9.8.2` using §3.7, capture `--warning-mode=all`, and run the toolchain compatibility/test matrix before finalising the change.
8. Repair ScanCode Python bootstrap and dynamic Gradle configuration reporting.
9. Verify signing-key continuity, securely provision all five repository Actions secrets, and rerun the guarded Release path. Do not make a source or workflow-code change for the confirmed secret-availability failure.
10. Bump patch version to 1.18.2 and preserve the existing monotonic version-code process.
11. Run unit tests, Compose/UI tests, lint, debug build and exact signed Release build using Gradle `9.8.2`.
12. Validate licensing inventory independently and confirm its non-blocking policy.
13. Inspect the resulting APK identity, version and signing fingerprint; publish only after the release gates pass.

### 10.3 CI gates

**Blocking for application release:** source compilation, relevant unit/UI tests, Release lint, APK assembly, release signing continuity, APK signature verification, package/version verification, artifact integrity and successful release publication when publication is requested.

**Informational/non-blocking:** licence metadata completeness. The status and diagnostics must still be accurate and available as retained artifacts.

## 11. Documentation changes

Update the existing documentation files rather than creating redundant manuals:

- `README.md`: V18.2 release summary and return-to-active-drawing behaviour.
- `docs/ARCHITECTURE.md`: document the Home/workspace navigation host and active-session state ownership.
- `docs/BUILD_AND_RELEASE.md`: document the confirmed missing/inaccessible five-secret production-signing preflight, the repository-level secret requirements, the secure provisioning procedure, the Gradle `9.8.2` Wrapper pin and checksum-verification process, and the successful guarded Release validation once completed. Retain the signing identity and fail-closed secret-handling rules.
- `docs/LICENSING_INVENTORY.md`: record the Python 3.12 ScanCode runtime requirement for the pinned archive, the actual configurations discovered, the not-applicable classification, and the revised report-completeness criteria.
- `docs/STORAGE_AND_FORMATS.md`: document session quick-save/checkpoint and explicit-save/dirty-state distinction if the storage flow changes.

Documentation must reflect implemented behaviour, not planned-but-unfinished functionality.

## 12. Non-regression constraints

The following must remain unchanged unless an independently demonstrated defect requires a tightly scoped correction:

- `applicationId` and app namespace identity as currently configured;
- `minSdk=24`, `targetSdk=37`, compile SDK API 37.2 and Java 11 bytecode target;
- existing production app signing certificate and fail-closed release-signing process;
- current drawing file formats, document schema compatibility and database identity;
- exact geometry kernel and entity model;
- viewport rendering, camera transforms, input coordinate conversion and controller calibration;
- V18.1 card animation rules outside the two identified action decks;
- QDIM, Grid Snap, ORTHO, Polar Tracking, PolarSnap, OSNAP and Paper Space behaviours;
- existing home/release catalogue changelog content and actual release source;
- bundled icon assets and font assets;
- no advertising, telemetry, remote drawing upload, or new network service.

## 13. Final acceptance checklist

V18.2 is complete only when all applicable items below are evidenced in the final agent report.

### Application behaviour

- [ ] Send/Cancel uses the shared lifecycle-aware idle timer; no un-driven timer instance remains.
- [ ] Send/Cancel rear card is fully concealed at rest and peeks only after 75 seconds of eligible inactivity.
- [ ] Command action semantics remain correct and are invoked once.
- [ ] Release Catalogue actions are stacked in one card position, never side by side.
- [ ] Tap-to-exchange visibly animates the front/rear Release Catalogue cards.
- [ ] Download, Install and Cancel callbacks remain correct across updater states.
- [ ] Home includes a conditional Return to Active Drawing control.
- [ ] Home ↔ Workspace transition is animated and reversible.
- [ ] Returning restores the same unsaved active drawing and editor state.
- [ ] New/Open/Import cannot silently replace a dirty active drawing.
- [ ] Save failure, Discard, and Stay behaviour are tested.

### Build and quality

- [ ] The five canonical repository Actions secrets are present by name and available to the workflow; no secret values were exposed during verification.
- [ ] The guarded Release signing step successfully decoded the established keystore, validated the configured alias and matched the trusted certificate SHA-256 fingerprint.
- [ ] The signed Release APK builds and its signature, package identity and version are verified.
- [ ] No speculative signing-key or release identity changes were made.
- [ ] Unit/UI tests pass.
- [ ] Gradle Wrapper and checked-in wrapper scripts/JAR are updated to stable Gradle `9.8.2`.
- [ ] `distributionSha256Sum` matches the official SHA-256 for `gradle-9.8.2-bin.zip`.
- [ ] `./gradlew --version` reports Gradle `9.8.2` on JDK 21.
- [ ] `./gradlew help --warning-mode=all` is reviewed; no actionable compatibility warning is hidden or suppressed.
- [ ] Lint and build workflows run on Gradle `9.8.2`; the outdated-version notice is gone.
- [ ] Release lint passes with zero warnings and zero errors.
- [ ] Debug APK builds.
- [ ] Signed Release APK builds with the existing production identity.
- [ ] APK signature and certificate fingerprint are verified.
- [ ] Version name and version code are verified monotonic.

### Licensing inventory

- [ ] Python 3.12 is installed before the pinned ScanCode standalone archive is configured.
- [ ] ScanCode version and help preflight pass.
- [ ] Genuine ScanCode JSON is generated and merged.
- [ ] Missing variant configurations are reported as not applicable, not failed.
- [ ] Exact POM evidence remains exact; no group-prefix licence guesses are introduced.
- [ ] SBOMs and consolidated reports validate and agree on counts.
- [ ] Licensing status is accurate and remains informational/non-blocking.

### Delivery

- [ ] `version.properties` reflects `1.18.2` patch line.
- [ ] Existing source and docs updated without duplicate manuals.
- [ ] No secrets, keystore, local `.env`, APK, or private credential have been committed.
- [ ] Final change summary, test results, build details, signing fingerprint, licensing completeness and known blockers are reported.

## 14. Required final report from the coding agent

The coding agent must return a structured implementation report with:

1. **Repository identity:** repository, branch, exact commit before and after changes.
2. **Version:** version name, version code and release tag.
3. **Send/Cancel deck:** root cause fixed, shared timer integration, timer interval/peek duration and test evidence.
4. **Release Catalogue:** final stacked-card interaction, state mapping, callback tests and screenshot evidence.
5. **Home/workspace:** navigation motion, state-preservation design, active-session handling, dirty-state prompt and test evidence.
6. **Build issue:** record the confirmed missing/inaccessible five-secret signing-preflight failure, the repository secret-name verification result (names only), the guarded rerun outcome, and any later failure separately. Never report the confirmed signing-preflight issue as a compile error.
7. **Gradle toolchain:** Wrapper version and distribution checksum verification, JDK, AGP, Kotlin, KSP versions, `--warning-mode=all` findings, compatibility resolution and whether the outdated-version notice is cleared.
8. **Lint:** task outcome and warning/error counts.
9. **Tests:** exact Gradle tasks and pass/fail results.
10. **Signing:** certificate SHA-256 and verification result only; never disclose key material or passwords.
11. **Licensing:** ScanCode version, Python version, configuration-resolution table, file scan counts, completeness status and artifact paths.
12. **Files changed:** source files, tests, workflows and documentation.
13. **Known limitations:** every incomplete item and the reason it remains incomplete.

---

## 15. Evidence references

### Supplied repository and diagnostics

- Source snapshot: `PrecisionCAD-Studio-main.zip` (SHA-256 listed in §1.1).
- Lint artifact: `android-lint-release-reports (1).zip` (SHA-256 listed in §1.1).
- Licensing artifact: `precisioncad-licensing-inventory-1363111ef36baefc45b2262b6fb69a77b2426bdc.zip` (SHA-256 listed in §1.1).
- Build failure screenshot: `6432.jpg` (SHA-256 listed in §1.1), showing the failed production-signing guard and skipped downstream steps.
- Successful lint screenshot: `6433.jpg` (SHA-256 listed in §1.1), showing the separate successful `lintRelease` run and informational Gradle-version notice.
- ScanCode bootstrap diagnostic: `diagnostics/scancode-install.log` in the licensing artifact.
- ScanCode preflight diagnostic: `diagnostics/scancode-version.log` in the licensing artifact.
- Missing Release unit-test configuration diagnostics: `diagnostics/gradle-config-releaseUnitTestCompileClasspath.log` and `diagnostics/gradle-config-releaseUnitTestRuntimeClasspath.log` in the licensing artifact.

### External implementation references

- `actions/setup-python` release listing (reviewed for the stable v7.1.0 release): https://github.com/actions/setup-python/releases
- GitHub Marketplace description of setup-python and its Python-on-PATH behaviour: https://github.com/marketplace/actions/setup-python
- Official ScanCode Toolkit installation prerequisites: https://github.com/aboutcode-org/scancode-toolkit/blob/develop/INSTALL.rst
- ScanCode Toolkit v32.5.0 pinned standalone archive used by the current workflow: https://github.com/aboutcode-org/scancode-toolkit/releases/tag/v32.5.0
- Gradle 9.8.2 release notes and Wrapper upgrade instructions: https://docs.gradle.org/9.8.2/release-notes.html
- Gradle 9.8.2 official release/distribution list and checksums: https://gradle.org/releases/
- Android Gradle Plugin minimum Gradle compatibility table: https://developer.android.com/build/releases/about-agp
- Kotlin Gradle Plugin fully supported Gradle/AGP compatibility matrix: https://kotlinlang.org/docs/gradle-configure-project.html

**End of V18.2 Master Implementation Plan.**
