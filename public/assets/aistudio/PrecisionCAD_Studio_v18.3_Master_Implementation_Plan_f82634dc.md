# PrecisionCAD Studio — Version 18.3 Master Implementation Plan

**Document type:** Standalone, source-aware implementation specification for the Google AI Studio coding agent  
**Application:** PrecisionCAD Studio — Android-native 2D CAD  
**Target application version:** `1.18.3` (subject to the repository's existing CI version-name rules)  
**Application ID:** `com.precisioncad.studio`  
**Target release line:** V18.3  
**Plan status:** Implementation specification only. No repository source, GitHub setting, signing secret, signing key, release, or workflow has been changed while preparing this document.

---

## 0. Execution authority and scope

This document is the complete V18.3 implementation brief. The coding agent must first inspect the live repository, identify its exact branch and commit, and compare it with the source snapshot described in §1. The live checkout is authoritative for implementation. The uploaded ZIP and reports are diagnostic evidence, not proof that they all came from one commit.

V18.3 is a focused release-pipeline correctness and interaction-timer maintenance release. Its principal confirmed defect is the APK certificate fingerprint parser in `.github/workflows/build-signed-apk.yml`. The production signing key and the fingerprint secret are already in agreement. The workflow is extracting the wrong part of the `apksigner` output and falsely reporting a mismatch.

A second code-level defect exists in the shared card timer architecture: `rememberCadDeckIdleTimerState()` already owns a lifecycle observer and a timer coroutine, but `BindCadDeckIdleTimerEffect()` adds another observer and another timer coroutine to the same state at the Send/Cancel and Release Catalogue call sites. V18.3 must ensure one and only one live timer driver per `CadDeckIdleTimerState` instance.

The uploaded source already contains substantial V18.2 functionality. Do not reimplement it indiscriminately. Preserve and regression-test the Home/Canvas transition, active-session retention, dirty-document protection, stacked Release Catalogue card, and Send/Cancel card. Make only the narrowly required corrections.

### Execution order

1. Verify repository identity, branch, exact commit, version metadata, and the source/report relationship.
2. Correct and unit-test the certificate SHA-256 parser.
3. Consolidate the shared idle-timer lifecycle and coroutine ownership.
4. Run Send/Cancel, Release Catalogue, Home/Canvas and document-retention regressions.
5. Verify the existing Gradle Wrapper 9.8.2 on the exact checked-out commit. Do not perform a redundant version upgrade.
6. Regenerate the licensing inventory on that same commit and verify ScanCode's Python runtime setup.
7. Run lint, tests, Debug assembly, and the guarded signed Release build.
8. Publish nothing unless separately authorised and all production release checks pass.

### Absolute stop conditions

- Never change, regenerate, replace, or bypass the permanent production signing key to fix this parser defect.
- Never change `RELEASE_CERT_SHA256` merely to make the broken parser pass.
- Never weaken or remove the fail-closed release-signing guard.
- Never accept an APK whose actual signer certificate differs from the trusted production certificate.
- Never create multiple timer drivers for the same card-deck timer state.
- Never silently discard a dirty document when navigating Home, opening another project, importing a file, or creating a new drawing.
- Never use stale lint/licensing metrics as proof of the current commit's status.
- Never update Gradle, AGP, Kotlin, KSP, Compose, SDK levels, or Java bytecode settings without a reproducible reason and compatibility evidence.
- Do not publish a Release or create a public tag unless the user has explicitly requested publication.

---

## 1. Audited source and report set

### 1.1 Files reviewed

| Supplied file | SHA-256 | What it establishes |
|---|---|---|
| `PrecisionCAD-Studio-main.zip` | `b91d0a5dda2e76651c5ebc48c4e74cc2a591d4a5c4ff8cac8454f695be1e2e24` | Current uploaded source snapshot. It contains no `.git` directory, so it does not independently establish its commit SHA. |
| `android-lint-release-reports (1).zip` | `1776a3c76056ae4220dfecb74db3e82104cff87288b830903536cae8729c4d82` | Release lint TXT, XML and HTML reports. TXT says `No issues found.`; XML has an empty `<issues>` element and identifies lint 9.1.1. |
| `precisioncad-licensing-inventory-1363111ef36baefc45b2262b6fb69a77b2426bdc.zip` | `adcc430a12226c05f2a517d7b3280ff8d42bebda237ac08e749cacf2214333e8` | Licensing CSV/HTML, CycloneDX outputs, status JSON, repository inspector output and scanner/Gradle diagnostics. Its status JSON names commit `1363111ef36baefc45b2262b6fb69a77b2426bdc`. |
| Previous V18.2 plan | Supplied as `PrecisionCAD_Studio_v18.2_Master_Implementation_Plan_Download.md` | Records the prior card, navigation, signing-secret, licensing, and Gradle work. It is superseded only where this V18.3 plan supplies newer source evidence. |

The latest source ZIP does not include Git metadata. The lint archive does not expose a commit identity in its three report files. The licensing archive records commit `1363111ef36baefc45b2262b6fb69a77b2426bdc`, but its recorded versions differ from the uploaded source snapshot. These artifacts must not be assumed to describe the same tree.

### 1.2 Current source facts observed in the uploaded ZIP

| Item | Observed source value |
|---|---|
| `applicationId` | `com.precisioncad.studio` |
| `minSdk` | 24 |
| `targetSdk` | 37 |
| Compile SDK | Android API 37.2 (`release(37)` with minor API level 2) |
| Java source/target compatibility | Java 11 |
| JDK configured by workflows | 21 |
| Gradle Wrapper | **9.8.2** |
| Wrapper checksum property | `distributionSha256Sum=21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607` |
| Android Gradle Plugin | 9.1.1 |
| Kotlin Compose plugin | 2.4.21 |
| KSP | 2.3.13 |
| CycloneDX Gradle plugin | 3.5.1 |
| `version.properties` | `VERSION_MAJOR=1`, `VERSION_MINOR=18`, `VERSION_PATCH=2`, `VERSION_CODE=20` |
| Release signing configuration | `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`; release build type explicitly uses the `release` signing config |

The uploaded source's `gradle/wrapper/gradle-wrapper.properties` already points to `gradle-9.8.2-bin.zip`, includes a distribution SHA-256, and enables URL validation. `docs/BUILD_AND_RELEASE.md` also identifies Gradle 9.8.2 and the same checksum. V18.3 must validate this existing state, not blindly repeat the V18.2 Wrapper upgrade.

### 1.3 Important report/source mismatch

The licensing status JSON records:

- Commit: `1363111ef36baefc45b2262b6fb69a77b2426bdc`
- Effective version name: `1.18.1.2`
- Effective version code: `21`
- Gradle Wrapper: `9.3.1`
- Python: `3.14.4`
- ScanCode status: `VERSION_CHECK_FAILED`

The newly uploaded source ZIP instead contains `version.properties` for base version `1.18.2`, base code `20`, and a Gradle Wrapper at `9.8.2`. The source ZIP has no `.git` directory, so its commit cannot be proved from the archive itself. This is a strong indication that the licensing report and current source archive are from different repository revisions or build states; the coding agent must confirm this against the live Git checkout.

**Required reconciliation:** record the live checkout SHA before making changes. Run every new lint, build and licensing job against that same SHA. Each uploaded artifact must state the exact commit and effective version that generated it. If an artifact commit differs from the current checkout, label it historical/stale and do not use its metrics as current acceptance evidence.

### 1.4 Latest lint report

The uploaded Release lint report contains:

- `lint-results-release.txt`: `No issues found.`
- `lint-results-release.xml`: `<issues format="6" by="lint 9.1.1"></issues>` with no reported issue entries.
- `lint-results-release.html`: matching report output.

This is a clean lint result for the report's own build, but the archive does not establish that it was generated from the exact source ZIP commit. V18.3 must run `:app:lintRelease` after the changes and retain the fresh report artifact. Acceptance target: zero errors and zero warnings on the final implementation commit.

---

## 2. V18.3 requirement A — Correct the Release APK certificate SHA-256 parser

### 2.1 Confirmed root cause

**File:** `.github/workflows/build-signed-apk.yml`  
**Step:** `Verify Single APK Output, Package Identity, Version & Signing Certificate`  
**Current parser location in the supplied source:** approximately line 349.

The current code is:

```bash
APK_CERT_SHA256=$(printf '%s\n' "$SIGNER_OUTPUT" | grep -i "certificate SHA-256 digest:" | head -n 1 | awk -F':' '{print $2}' | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')
```

This is incorrect because `apksigner` prints a signer line containing **two colons**. The supplied actual line is:

```text
V2 Signer: certificate SHA-256 digest: 7bbcda1924e95ca1758c7e0ac763b56ae9f7eafef4c6e7362bf2d63c63531e59
```

With `awk -F':' '{print $2}'`, the current workflow extracts the second field:

```text
 certificate SHA-256 digest
```

After the existing whitespace and colon removal, it becomes:

```text
certificatesha-256digest
```

That is exactly the malformed value shown in the workflow's failure message. The fingerprint is not being read from the output correctly. Changing the trusted fingerprint would conceal the parsing error rather than fix it.

### 2.2 Definitive certificate evidence supplied by the user

The user supplied both values from the same failed run:

| Check | SHA-256 certificate fingerprint |
|---|---|
| Permanent keystore preflight | `7bbcda1924e95ca1758c7e0ac763b56ae9f7eafef4c6e7362bf2d63c63531e59` |
| Generated Release APK V2 signer | `7bbcda1924e95ca1758c7e0ac763b56ae9f7eafef4c6e7362bf2d63c63531e59` |
| Trusted `RELEASE_CERT_SHA256` | `7bbcda1924e95ca1758c7e0ac763b56ae9f7eafef4c6e7362bf2d63c63531e59` |

The values are identical. The APK output also states:

```text
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v3.2 scheme (APK Signature Scheme v3.2): false
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
V2 Signer: certificate SHA-256 digest: 7bbcda1924e95ca1758c7e0ac763b56ae9f7eafef4c6e7362bf2d63c63531e59
```

`apksigner` reports one signer and a successful V2 signature. The project `minSdk` is 24. Do not fail the release solely because V1, V3, V3.1, V3.2, V4, or SourceStamp are reported false; the current release policy must validate the schemes required by the app's supported platform range. For this supplied APK, V2 is true. Do not add an unrelated signing-scheme requirement as part of the fingerprint parser correction.

### 2.3 Required parser correction

Replace the current `$2` colon-field extraction with robust extraction of the text **after the literal `certificate SHA-256 digest:` marker**. Do not simply assume every output line has a fixed number of colon-delimited fields.

The implementation must:

1. Retain and print the complete `apksigner verify --verbose --print-certs` output in the job log.
2. Require the command itself to exit successfully.
3. Parse `Number of signers:` and require exactly one signer for this app's current signing configuration.
4. Find the line(s) containing the exact marker `certificate SHA-256 digest:`.
5. Require exactly one certificate SHA-256 digest line for the single-signer output.
6. Remove the complete prefix through the marker, then remove only colons and whitespace from the remaining digest.
7. Normalize the digest to lowercase.
8. Validate that the parsed digest contains exactly 64 hexadecimal characters (`^[0-9a-f]{64}$`).
9. Normalize and independently validate the trusted expected fingerprint from `RELEASE_CERT_SHA256` using the same 64-hex rule.
10. Compare the two normalized 64-character values exactly.
11. Print the extracted certificate fingerprint and the comparison outcome. A certificate fingerprint is public certificate metadata, not a private key or password; never print signing secrets.
12. Fail closed on missing output, zero or multiple digest lines, zero or multiple signers, invalid hex, wrong length, empty expected fingerprint, or a genuine fingerprint mismatch.

A robust extraction pattern may use a fixed-string marker followed by prefix removal, for example:

```bash
DIGEST_LINES="$(printf '%s\n' "$SIGNER_OUTPUT" | grep -F 'certificate SHA-256 digest:' || true)"
DIGEST_COUNT="$(printf '%s\n' "$DIGEST_LINES" | sed '/^[[:space:]]*$/d' | wc -l | tr -d '[:space:]')"

if [ "$DIGEST_COUNT" -ne 1 ]; then
  echo "ERROR: Expected exactly one signer certificate SHA-256 digest line; found ${DIGEST_COUNT}."
  exit 1
fi

APK_CERT_SHA256="$(printf '%s\n' "$DIGEST_LINES" \
  | sed -E 's/^.*certificate SHA-256 digest:[[:space:]]*//' \
  | tr -d ':[:space:]' \
  | tr '[:upper:]' '[:lower:]')"

if [[ ! "$APK_CERT_SHA256" =~ ^[0-9a-f]{64}$ ]]; then
  echo "ERROR: Parsed APK signer certificate fingerprint is not exactly 64 hexadecimal characters."
  exit 1
fi
```

This is an illustrative implementation shape. The coding agent may use an equivalently robust parser, but it must be tested against the exact user-supplied output and must not return the label text. It must also parse and validate the `Number of signers: 1` line rather than inferring signer count from the number of digest lines alone.

### 2.4 Extract the parser into a testable unit

Prefer moving certificate-output parsing and normalisation into a small helper (for example, `scripts/verify_apksigner_certificate.py` or a source-controlled shell helper) so the parsing logic can be tested without creating or signing an APK. Keep the workflow responsible for running `apksigner`, providing the expected fingerprint, and failing the job if the helper returns non-zero.

If Python is used, use the standard library only; do not introduce a third-party package for a one-function parser. If Bash is retained, provide a source-controlled shell test harness or invoke the function against fixtures in CI.

Required test fixtures and cases:

| Case | Expected result |
|---|---|
| Exact user-supplied `V2 Signer: certificate SHA-256 digest: <64 hex>` line | Parse the final 64-hex fingerprint correctly |
| `Signer #1 certificate SHA-256 digest: <64 hex>` prefix form | Parse correctly |
| Uppercase digest | Normalise to lowercase and parse |
| Colon-separated digest | Remove separators and parse |
| Exactly one signer and digest equal to trusted value | PASS |
| Exactly one signer but a different well-formed digest | FAIL as a genuine mismatch |
| Missing digest marker | FAIL |
| Empty digest | FAIL |
| Digest with 63 or 65 hex characters | FAIL |
| Any non-hex character in the digest | FAIL |
| `Number of signers: 0` | FAIL |
| `Number of signers: 2` | FAIL |
| More than one certificate SHA-256 digest line | FAIL closed unless the signing policy is deliberately changed and reviewed |
| Malformed/missing signer-count line | FAIL |

The regression test for the exact supplied output must return:

```text
7bbcda1924e95ca1758c7e0ac763b56ae9f7eafef4c6e7362bf2d63c63531e59
```

It must not return `certificatesha-256digest`.

### 2.5 Preserve the existing signing design

Do not change the following five GitHub Actions secrets:

- `KEYSTORE_BASE64`
- `STORE_PASSWORD`
- `KEY_PASSWORD`
- `KEY_ALIAS`
- `RELEASE_CERT_SHA256`

Do not change the keystore, alias, store password, key password, trusted fingerprint, package identity, or signing config. The two certificate fingerprints supplied by the user already match. Do not ask the user to regenerate or re-provision these values for this parser defect.

Keep the current fail-closed keystore preflight. It must continue to:

- decode the permanent keystore into a unique runner-temporary file;
- set restrictive permissions on the temporary keystore;
- verify the configured alias;
- calculate the keystore certificate fingerprint;
- compare that fingerprint to the trusted `RELEASE_CERT_SHA256` value before assembly;
- pass signing values only as step-scoped environment values where needed;
- never persist passwords to `$GITHUB_ENV`;
- remove the staged keystore in an `if: always()` cleanup step.

### 2.6 Post-fix Release verification order

After the parser unit tests pass, the next Release run must proceed through all of these stages:

1. Signing-preparation step validates the established keystore and reports the public fingerprint.
2. Unit tests and `lintRelease` complete.
3. `assembleRelease` completes using the existing `release` signing config.
4. `apksigner verify --verbose --print-certs` succeeds.
5. The parser finds exactly one signer and obtains exactly one 64-character digest.
6. The extracted APK signer digest equals the normalised expected trusted certificate digest.
7. `aapt2 dump badging` confirms `com.precisioncad.studio`.
8. APK `versionCode` and `versionName` match the values computed by the workflow.
9. Exactly one Release APK is found and copied to the expected named artifact.
10. SHA-256 checksum generation and artifact upload succeed.
11. Any publication step remains subject to explicit user authorisation and all release gates.

The final report must distinguish the original false parser failure from a later failure, if any. A passing keystore preflight alone is not sufficient; the post-build certificate check must also pass with the corrected parser.

---

## 3. V18.3 requirement B — One timer driver per card deck

### 3.1 Source finding

**Shared timer implementation:** `app/src/main/java/com/example/ui/components/CadBinaryToggleCard.kt`

The supplied source contains two places that drive the same `CadDeckIdleTimerState`:

- `rememberCadDeckIdleTimerState()` already registers a lifecycle observer and starts a `LaunchedEffect` timer loop. It advances the post-exchange reveal and the 75-second idle interval / 1-second peek cycle.
- `BindCadDeckIdleTimerEffect()` separately registers another lifecycle observer, updates eligibility again, and starts another `LaunchedEffect` timer loop against that same state instance.

The separate `BindCadDeckIdleTimerEffect()` is attached to:

- `CadWorkspaceScreen.kt` for the `command_send_cancel` deck;
- `CadReleaseCatalogueDialog.kt` for the `release_catalogue_action_deck`.

The same timer state can therefore be advanced by two independent coroutine loops and managed by two lifecycle observers. Those loops can race and double-advance the virtual elapsed time. This makes a one-second idle peek timing unreliable and can cause the rear card to appear only briefly or retract at the wrong time. This is a code-level architecture defect even though the underlying timer state unit test passes.

### 3.2 Required ownership model

There must be exactly **one timer coroutine and one lifecycle observer per `CadDeckIdleTimerState` instance**.

Recommended minimal correction for the uploaded source:

1. Keep `rememberCadDeckIdleTimerState()` as the sole lifecycle-aware timer owner because it already performs lifecycle registration, eligibility updates, reduced-motion detection, and the timer loop.
2. Remove the redundant `BindCadDeckIdleTimerEffect()` invocations from `CadWorkspaceScreen.kt` and `CadReleaseCatalogueDialog.kt`.
3. Remove its import from `CadWorkspaceScreen.kt`.
4. Search the entire repository for every other call site. In the supplied snapshot there are only the two call sites listed above. Confirm this in the live checkout before removing the helper function.
5. If no call sites remain, remove `BindCadDeckIdleTimerEffect()` entirely so a future contributor cannot accidentally reintroduce a second driver. If a genuine separate use case remains in the live checkout, refactor the helper so it provides eligibility binding only and cannot start a second timer coroutine or lifecycle observer.
6. Retain `LaunchedEffect(isDeckAnimating)` in the Release Catalogue only for `updateEligibility(transitionRunning = isDeckAnimating)`. It must not create another clock driver.
7. Keep section-local direct-interaction resets. A touch or action on another deck, scrollable card, or unrelated part of the UI must not reset this deck's own idle timer.

Do not replace the shared timer architecture with independent ad hoc delays in each screen. Preserve testable deterministic state advancement.

### 3.3 Required timer state semantics

Preserve these current timer constants unless a test proves the source behaviour does not match the established interaction requirement:

| Behaviour | Required value |
|---|---:|
| Idle interval | 75,000 ms |
| Idle peek duration | 1,000 ms |
| Post-card-exchange reveal for decks that use it | 2,400 ms |
| Command Send/Cancel deck post-exchange reveal | 0 ms (rear card remains concealed after a manual exchange except for the defined idle peek) |

The timer must:

- count only while its own deck is visible, enabled, and the app window is resumed;
- pause/reset when its parent disappears, the screen is suspended, or an animated exchange temporarily marks the deck ineligible;
- begin a fresh interval when the deck becomes eligible again;
- reset only on direct interaction with that deck or a command-entry event explicitly defined as activity for the command deck;
- never be advanced twice per elapsed interval;
- never execute an action as a side effect of the idle animation;
- immediately conceal the rear card when the idle peek expires;
- obey reduced-motion preference without breaking card order or functionality.

### 3.4 Send/Cancel deck requirements

The supplied `CadWorkspaceScreen.kt` already contains the V18.2 stacked Send/Cancel implementation, including `command_action_card_stack`, the front/rear card z-order, and the swipe/tap order exchange. Preserve its geometry, colours, touch direction, and command callbacks except where the timer ownership correction requires changes.

Required observable state:

**Deck inactive / hidden:**
- When neither the command field/keyboard nor command text makes the action deck eligible, the Send/Cancel deck is not displayed and its idle timer is stopped.
- Do not leave invisible hit targets in the layout.

**Deck active / user interacting:**
- Only one card is the foreground actionable card.
- The rear card is fully concealed behind it: no visible edge, border, shadow, label, or tappable hit area.
- Send remains the default foreground card after the deck is re-opened, unless the existing state contract explicitly preserves the user's chosen front card while the same session remains active.
- The idle timer resets on command text editing and direct deck interactions as already specified.

**Idle threshold reached:**
- After 75,000 ms of eligible inactivity, the rear card peeks using the established slide motion.
- The peek lasts 1,000 ms and retracts smoothly.
- It must not change the foreground action or execute Send/Cancel.
- If the user touches/uses the deck during the peek, the timer restarts cleanly.

**Manual card order exchange:**
- Preserve the existing vertical swipe gesture and tap behaviour.
- The selected front card becomes the sole actionable card.
- Avoid duplicate command submission or cancellation.
- Keep the red Cancel card inaccessible while it is completely concealed.

### 3.5 Release Catalogue action deck requirements

The supplied `CadReleaseCatalogueDialog.kt` already renders the primary action and Cancel in one vertically stacked card position, with a rear card, front card, state-driven colours, and a tap affordance on the exposed rear card. Do not replace it with side-by-side buttons.

Required behaviour:

- Exactly one visible foreground action card at a time.
- The primary card label is derived from the actual updater state (`DOWNLOAD` or `INSTALL` as appropriate); Cancel remains the alternate/rear action.
- A tap on the exposed rear card exchanges the front/rear order with a clear animation.
- The user must not accidentally activate the rear card merely by tapping the foreground card's main action region.
- The foreground action executes its callback exactly once.
- Cancel must preserve the existing download cancellation/reset/dismiss semantics.
- When downloading/checking or another state disables the primary action, its disabled appearance and hit target must agree.
- The rear card is concealed at rest and can be revealed only by the existing post-exchange or idle-peek rules.
- Keep the deck transition lock so the action cannot be double-committed during animation.
- Keep `release_catalogue_action_deck`, primary, cancel, and rear-card test tags stable unless the tests are intentionally migrated.

The shared timer must be driven only by `rememberCadDeckIdleTimerState()` (or by one clearly identified replacement owner), not both that function and `BindCadDeckIdleTimerEffect()`.

### 3.6 Required timer tests

Retain and extend the existing tests in `ExampleUnitTest.kt` and `ExampleRobolectricTest.kt`.

Add or update tests for:

1. A single deterministic `advanceTimeBy(74_999)` does not reveal the rear card.
2. Advancing the remaining 1 ms starts the peek.
3. The rear card remains revealed only during the 1,000 ms interval and is concealed afterwards.
4. A direct interaction resets the elapsed idle interval and terminates an active peek.
5. A deck hidden or lifecycle-suspended does not accumulate active idle time.
6. Re-enabling the deck begins a fresh 75-second interval.
7. Two decks maintain independent clocks.
8. The Compose owner starts one driver only. Add an instrumentation/Robolectric test or a test seam that would fail if two loops advance the same state.
9. Send/Cancel's rear card has no semantics node/hit target while completely concealed.
10. The rear card appears after the expected idle interval and retracts after the peek window.
11. Release Catalogue tap-to-exchange works, and download/install/cancel callbacks each execute at most once.
12. Reduced-motion mode suppresses movement but leaves card exchange and all actions usable.

A passing pure state-machine test is not enough: include at least one UI-level test that exercises the actual Compose timer owner.

---

## 4. V18.3 requirement C — Preserve the V18.2 Home/Canvas navigation and unsaved session safeguards

### 4.1 Existing implementation found

The uploaded source already contains the substantial V18.2 Home/Canvas implementation:

- `CadWorkspaceScreen.kt` uses `AnimatedContent` for the Home/workspace route.
- A `rememberSaveableStateHolder()` retains the Home and workspace saveable state under separate keys.
- Home-to-workspace and workspace-to-Home transitions use coordinated slide/fade animations, with a reduced-motion fallback.
- The Home screen receives `hasActiveWorkspaceSession`, active drawing name, dirty state, and a `onReturnToActiveWorkspace` callback.
- The Home UI contains `home_return_to_active_drawing_button` and a banner for an active session.
- `CadWorkspaceViewModel.openHomeScreen()` triggers an immediate automatic quick-save/checkpoint for an active session before changing the visible route. It does not mark the document explicitly saved.
- `returnToActiveWorkspace()` returns to the retained active workspace rather than constructing a blank drawing.
- New/Open/Import requests from Home create pending replacement actions when the active document is dirty.
- Existing Robolectric tests already reference the Home return control and route behaviour.

### 4.2 V18.3 scope

Do not redesign the Home screen or rewrite the routing architecture without an independently reproduced failure. Preserve the existing V18.2 implementation and verify the following end-to-end:

1. User begins a drawing and makes an unsaved modification.
2. User navigates to Home.
3. The active workspace remains retained; quick-save/checkpoint does not mark it explicitly saved.
4. The Home page shows a return-to-active-drawing control.
5. Activating it animates back to the same canvas and restores entities, layer state, current command state where intended, view state, selection state where saveable, and other editor-local state.
6. Choosing New/Open/Import while the active document is dirty presents the existing Stay/Discard/Save decision and never silently replaces the drawing.
7. A successful explicit Save changes the dirty state; a subsequent replacement may proceed under the existing rules.
8. A failed save preserves the dirty document and leaves the user on a safe route.
9. Android system Back from Home returns to the active workspace when the existing BackHandler conditions are met.
10. The animated route remains coherent if the app is backgrounded/resumed or the window is recreated.

If these tests reveal a genuine defect, fix only that state transition or persistence edge. Do not reset the document or editor model when the Home screen becomes visible.

### 4.3 Acceptance

- Returning Home → Canvas restores the same active, unsaved document.
- The return animation remains visible and does not abruptly replace the page.
- No dirty document is lost through Home navigation, New, Open, import, or system Back.
- Existing V18.2 UI tests pass and new edge-case tests pass.

---

## 5. V18.3 requirement D — Verify the Gradle 9.8.2 toolchain; do not perform a redundant upgrade

### 5.1 Current source state

The uploaded source already has:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.8.2-bin.zip
distributionSha256Sum=21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607
validateDistributionUrl=true
```

The checked-in build workflow, lint workflow, and licensing workflow invoke `./gradlew` and include a Wrapper version check. The build documentation also identifies Gradle 9.8.2. Therefore, a further Wrapper bump is not a V18.3 task unless the live repository differs from the uploaded source.

### 5.2 Required verification

On the exact live checkout SHA:

1. Inspect the Wrapper properties, wrapper JAR, `gradlew`, and `gradlew.bat`.
2. Confirm that all checked-in Wrapper files are coherent and the distribution SHA-256 corresponds to the exact `gradle-9.8.2-bin.zip` distribution.
3. Run `./gradlew --version` in the build, lint, and licensing jobs and retain the output.
4. Require the output to report Gradle 9.8.2 and JDK 21.
5. Run `./gradlew help --warning-mode=all` and review the complete warning output.
6. Confirm that the Android lint run and signed build run actually use the checked-out Wrapper, not a system `gradle` binary or a stale report from a previous checkout.
7. If an outdated Gradle notice still appears, capture the complete warning and identify its emitting task, plugin, or workflow step. Do not assume it is a Gradle distribution warning until the source is known.
8. Reconcile the licensing artifact's recorded Gradle 9.3.1 with the current source's 9.8.2 Wrapper by comparing exact commit SHAs. The old report must not be used as evidence of the current Wrapper version if it belongs to a different commit.

### 5.3 Toolchain boundaries

Keep the current versions for the first V18.3 pass:

- Gradle Wrapper 9.8.2
- AGP 9.1.1
- Kotlin Compose plugin 2.4.21
- KSP 2.3.13
- CycloneDX Gradle plugin 3.5.1
- JDK 21
- Android API 37.2 / Build-Tools 37.0.0
- Java 11 source/target bytecode

The prior V18.2 plan noted that Gradle 9.8.2 is beyond the published fully-supported Gradle ceiling for the current Kotlin Gradle Plugin line. This is a compatibility qualification that must remain visible. Do not conceal it by suppressing warnings. Run the real build/test/lint matrix and inspect warning-mode output. If a reproducible failure points to a specific plugin, document the exact failing task and consult that plugin's official compatibility matrix before making the smallest possible adjustment. Do not upgrade several build plugins together without evidence.

### 5.4 Acceptance

- All CI jobs report Gradle 9.8.2 from `./gradlew --version` on the same commit.
- Wrapper checksum and validation settings are present and verified.
- No stale Gradle 9.3.1 report is presented as the current build state.
- `help --warning-mode=all`, tests, lint, and Debug assembly complete; any compatibility warnings are documented with their source and disposition.
- Do not count the old licensing report's recorded 9.3.1 as an unresolved need to bump the Wrapper again.

---

## 6. V18.3 requirement E — Licensing inventory: align the report with the current commit and verify Python 3.12 ScanCode setup

### 6.1 What the supplied inventory actually says

The supplied licensing artifact identifies itself as:

- Repository: `AnimeXplayXD/PrecisionCAD-Studio`
- Commit: `1363111ef36baefc45b2262b6fb69a77b2426bdc`
- Effective version: `1.18.1.2`, version code `21`
- Python: `3.14.4`
- ScanCode status: `VERSION_CHECK_FAILED` / `not-installed`
- Gradle Wrapper recorded by the report: `9.3.1`
- Unique dependency components: `379`
- Direct components: `105`
- Transitive components: `274`
- Dependency records: `1,436`
- Components with declared POM licence information: `208`
- Repository files scanned: `101`
- ScanCode files merged: `0`
- Files with detected licence information: `2`
- Files with detected copyright: `2`
- Multiple/ambiguous records: `8`
- No licence detected / unknown metadata records: `291`
- Unexpected repository file-read failures: `0`

These are facts about that report artifact only. They are not necessarily the counts for the newly uploaded source snapshot because the report commit/version/Wrapper metadata do not match the uploaded ZIP's visible version properties and Wrapper.

### 6.2 Exact ScanCode failure in the report

The diagnostics show that the ScanCode standalone archive downloaded successfully and its SHA-256 matched the workflow's expected checksum. The subsequent configuration step failed because the archive's configure script attempted to invoke `python3.12`, but the runner could not find that executable:

```text
Archive SHA-256: actual=638adcd0af576d1f4d5b64dde228724b3ca4fdee2c4de20d88e4356be353f027 expected=638adcd0af576d1f4d5b64dde228724b3ca4fdee2c4de20d88e4356be353f027
.../scancode-toolkit-v32.5.0/configure: line 237: python3.12: command not found
ERROR: scancode --version preflight failed
```

This is a runtime/bootstrap failure, not a bad archive checksum.

### 6.3 Current source workflow already contains a Python 3.12 setup step

The uploaded source's `.github/workflows/licensing-inventory.yml` already includes:

- `actions/setup-python@v7.1.0` with `python-version: '3.12.x'`;
- a preflight that runs `python3.12 --version`, `python --version`, and `python3.12 -m venv --help`;
- the ScanCode Toolkit v32.5.0 standalone archive name `scancode-toolkit-v32.5.0_py3.12-linux.tar.gz`;
- the expected archive SHA-256 `638adcd0af576d1f4d5b64dde228724b3ca4fdee2c4de20d88e4356be353f027`;
- archive checksum validation before extraction/configuration;
- a ScanCode `--version` and `--help` preflight before the report generator is invoked;
- genuine `scancode-raw.json` output merging into the consolidated file inventory when ScanCode succeeds.

Therefore, do not blindly add a second Python installation step or replace the pinned archive. First compare the live commit with the commit reported by the artifact. The inventory artifact may predate the current workflow's explicit Python 3.12 setup.

### 6.4 Required implementation and CI procedure

1. Confirm the live checked-out commit SHA and compare it with `1363111ef36baefc45b2262b6fb69a77b2426bdc`.
2. Inspect the exact `licensing-inventory.yml` from the live checkout. If its Python 3.12 setup and executable preflight are already present, preserve them and run the workflow on the current commit.
3. Ensure Python 3.12 setup runs before the ScanCode archive's `configure`, `--version`, `--help`, and actual scan commands.
4. Immediately before configuring ScanCode, log only safe runtime facts: `command -v python3.12`, `python3.12 --version`, Python's executable path, and the relevant PATH resolution. Do not dump environment variables or secrets.
5. Require `python3.12` to be available as an executable on PATH. Do not alias Python 3.14 to `python3.12`, do not modify the pinned archive to bypass the prerequisite, and do not claim the ScanCode CLI is installed if only its archive was downloaded.
6. Verify that the archive's exact SHA-256 still matches the official pinned value before extraction.
7. Run ScanCode `--version` and `--help`; both must exit zero. Capture complete, untruncated logs.
8. Run the actual ScanCode source scan and require a genuine `scancode-raw.json` file with valid JSON. A downloaded archive or a successful `--version` check alone is not a complete scan.
9. Merge ScanCode file-level findings into `licensing-inventory.csv` and `licensing-inventory.html`. Keep the unmodified ScanCode JSON as a separate retained artifact.
10. Keep the built-in file inspector output named `repository-file-inspector.json`; never present it as genuine ScanCode output.
11. Preserve the current exact GAV/POM licence-evidence rules. Do not reintroduce broad Maven group-prefix licensing guesses. Preserve raw POM name/URL evidence and map to SPDX only when unambiguous.
12. Discover actual resolvable Gradle configurations. The current artifact classifies `releaseUnitTestRuntimeClasspath` and `releaseUnitTestCompileClasspath` as `NOT_PRESENT_IN_VARIANT_MODEL`; treat these as non-applicable only after confirming the current AGP project model. Do not label them a scanner failure merely because those names are absent. Keep required and optional/not-applicable configuration states distinct in the report.
13. Report dependency occurrences by configuration separately from deduplicated components. Keep the actual CycloneDX Gradle plugin output and component evidence; do not create an independent inaccurate parallel BOM.
14. Derive effective application version and version code from the live build configuration/workflow. The status JSON must state the exact commit SHA, branch, run ID, effective version, Python version, ScanCode version/status, Gradle Wrapper, AGP, Kotlin, KSP and JDK.
15. Update the inventory generator's stale V18.1 description/help text to the V18.3 report identity where appropriate. Its current `SCHEMA_VERSION` is `18.2.0`; set the report schema identity to `18.3.0` if the V18.3 report format remains versioned with the application plan. Keep the JSON schema structure backward-readable or document any intentional field change.
16. The generator's current `scanCompleteness` rule correctly requires ScanCode to complete, required configurations to resolve, and no unexpected repository file-read failures. Preserve the distinction between an incomplete scan and an unknown licence result. `UNKNOWN_METADATA` and `NOT_DETECTED` are not themselves scanner failures.
17. Keep the licensing workflow informational and non-blocking. It may warn and upload diagnostics when incomplete, but must not approve/reject packages, assign legal conclusions, alter the project's licence, or gate Android APK release.
18. Upload the complete reports and diagnostics under a name containing the exact current commit SHA. Use `if: always()` for diagnostic artifact upload.

### 6.5 Required licensing result

A current-commit report is accepted only when:

- status JSON's commit equals the exact code commit that generated it;
- effective version/versionCode match the build configuration for that commit;
- Gradle Wrapper and toolchain metadata match `./gradlew --version` and the version catalog;
- Python 3.12 executable preflight passes;
- ScanCode's version/help checks pass;
- the actual ScanCode run completes and genuine JSON is retained;
- the merged inventory counts reconcile with raw findings;
- every required resolvable dependency configuration is accounted for;
- optional/non-applicable configurations are explicitly identified;
- the status is `COMPLETE` only when all required scan operations succeed;
- the workflow remains informational and non-blocking.

If ScanCode still fails on the current commit despite the explicit setup-python step, diagnose the live PATH, action execution, Python binary path, and archive configure output. Fix the specific discrepancy; do not assume the historical `python3.12: command not found` message still describes the latest workflow.

---

## 7. V18.3 requirement F — Version and source identity reconciliation

### 7.1 Current version metadata

The uploaded source contains:

```properties
VERSION_CODE=20
VERSION_MAJOR=1
VERSION_MINOR=18
VERSION_PATCH=2
```

The current build workflow calculates a CI version code from the configured base value plus `GITHUB_RUN_NUMBER`, then checks it against the paginated published stable GitHub Releases. It also supports a version-name override and tag-derived release logic.

The supplied licensing report records effective version `1.18.1.2` and code `21` from commit `1363111...`, while the current ZIP shows base `1.18.2` and code `20`. Because the source ZIP has no `.git` metadata, do not resolve this mismatch by guessing.

### 7.2 V18.3 version rules

1. Confirm the live branch and commit before changing `version.properties`.
2. Confirm whether the intended user-facing patch line is `1.18.3`; this is the natural V18.3 progression from the uploaded source's `1.18.2`, but the live repository's actual release policy and tags are authoritative.
3. If the live repository is still on patch `2`, set `VERSION_PATCH=3`. Do not rewrite major/minor values.
4. Preserve the existing monotonic CI version-code calculation and all-pages stable-release check.
5. Do not manually set a guessed version code. Determine the maximum published stable code and let the existing workflow calculate the next code; adjust the base only if the existing check demonstrates it is necessary.
6. Confirm version code is strictly greater than the highest published stable release. If the release endpoint cannot be read or the metadata is ambiguous, fail closed and report the obstacle.
7. Keep the effective version and commit identity identical across APK metadata, licensing status JSON, lint artifact labels, release artifact labels, checksums, and job summaries.
8. Do not publish a tag or GitHub Release unless the user requests it.

---

## 8. Build workflow hardening and complete verification matrix

### 8.1 Workflows in the supplied source

- `.github/workflows/build-signed-apk.yml`
- `.github/workflows/lint.yml`
- `.github/workflows/licensing-inventory.yml`

Use the existing JDK 21 / API 37.2 / Build-Tools 37.0.0 workflow environment and current pinned action versions unless a reproducible problem requires a change. The release signing workflow must continue to use the Gradle Wrapper from the checked-out commit.

### 8.2 Required pre-merge and CI tasks

Run the following against the exact implementation commit:

1. `./gradlew --version`
2. `./gradlew help --warning-mode=all`
3. `./gradlew :app:testDebugUnitTest --no-daemon --stacktrace`
4. `./gradlew :app:lintRelease --no-daemon --stacktrace`
5. `./gradlew :app:assembleDebug --no-daemon --stacktrace`
6. Compose/Robolectric UI tests covering Send/Cancel, Release Catalogue and Home/Canvas navigation.
7. The source-controlled certificate parser tests, including the exact actual `apksigner` line supplied by the user.
8. Licensing inventory workflow, confirming ScanCode runtime setup, complete source scan and same-commit status metadata.
9. Guarded `:app:assembleRelease` through the existing signed workflow using the established five signing secrets.
10. Post-build `apksigner` verification, certificate parser comparison, package ID/version checks, APK file count, artifact name, and SHA-256 checksums.

### 8.3 Release signing must remain guarded

- A Debug-only workflow_dispatch run is useful for an unsigned-of-production-secrets compile smoke test; it does not establish Release signing validity.
- The Release run must pass the production keystore preflight before Release assembly.
- The final APK signer fingerprint must equal the trusted `RELEASE_CERT_SHA256` after robust parsing.
- The package ID must remain `com.precisioncad.studio`.
- `versionCode` and `versionName` must match the workflow's computed values.
- Exactly one signed Release APK is expected under the current workflow design.
- Keep APK SHA-256 checksum artifact generation.
- The keystore cleanup action must run regardless of success/failure.

### 8.4 Failure classification

The final CI report must classify failures accurately:

| Failure | Correct classification |
|---|---|
| Original `certificatesha-256digest` mismatch with equal preflight/APK fingerprints | Workflow parser defect; this V18.3 plan fixes it |
| Genuine differing keystore/APK fingerprints after parser correction | Real signing identity mismatch; fail closed and investigate key source |
| Missing signing secrets | Secret-provisioning/preflight failure, not compilation |
| Gradle/Kotlin/AGP task exception | Build/toolchain failure, with task and stack trace |
| Lint issue | Lint/quality finding |
| ScanCode Python executable unavailable | Licensing scanner runtime/bootstrap failure |
| Licensing metadata unknown or ambiguous | Informational metadata finding; not a legal conclusion and not automatically a scanner failure |

---

## 9. Non-regression requirements

The following application and release properties must remain unchanged unless a separate, reproducible defect requires a minimal correction:

- `applicationId=com.precisioncad.studio` and current namespace/configuration;
- minSdk 24, targetSdk 37, compile API 37.2 and Java 11 bytecode settings;
- established permanent production signing certificate and fail-closed policy;
- version-code monotonicity and release tag semantics;
- `.pcad` format, DXF/PDF/SVG import/export, database identity and persisted-document compatibility;
- CAD geometry kernel, coordinate transforms, viewport renderer, dimension pipeline and drawing entity model;
- Paper Space, title-block isolation, Grid Snap, ORTHO, Polar Tracking, PolarSnap, OSNAP and QDIM;
- command dock, touch controller, cursor mapping and IME positioning;
- the three integrated launcher icon assets and other bundled art/font assets;
- Home/Canvas active-session semantics and dirty-document protection;
- Release Catalogue updater state and download/install/cancel callbacks;
- informational-only licensing policy.

No unrelated screen redesign, dependency sweep, renderer migration, file-format rewrite, analytics, telemetry, or remote drawing-storage service is included in V18.3.

---

## 10. Documentation updates

Update the existing documents rather than creating duplicate manuals:

- `README.md`: V18.3 maintenance summary and verified release status only after implementation succeeds.
- `docs/BUILD_AND_RELEASE.md`: explain the certificate parser correction, the signer-count and 64-hex validation, the trusted fingerprint comparison, the existing Gradle 9.8.2 Wrapper, and the exact guarded release sequence. Do not document an outdated Gradle 9.3.1 state as current.
- `docs/ARCHITECTURE.md`: document one lifecycle-aware timer owner per `CadDeckIdleTimerState`, if the timer ownership implementation changes.
- `docs/LICENSING_INVENTORY.md`: document Python 3.12 ScanCode bootstrap, report/commit identity fields, the latest schema identity and non-applicable configuration treatment.
- Update existing test documentation if new certificate parser fixtures or timer integration tests are added.

Documentation must distinguish completed implementation from planned work. Do not claim a successful signed Release build until the corrected workflow has actually passed.

---

## 11. V18.3 acceptance checklist

### A. Certificate verification

- [ ] Root cause in the previous workflow is documented: `awk -F':' '{print $2}'` extracted the label, not the SHA-256 digest.
- [ ] Exact user-supplied signer-output fixture parses to `7bbcda1924e95ca1758c7e0ac763b56ae9f7eafef4c6e7362bf2d63c63531e59`.
- [ ] Parser validates a single signer and exactly one 64-character hexadecimal certificate digest.
- [ ] Missing, malformed, multiple, or mismatching fingerprints fail closed.
- [ ] Trusted fingerprint remains sourced from `RELEASE_CERT_SHA256`; it is not hard-coded as a replacement.
- [ ] Production keystore, alias, passwords, and fingerprint secret remain unchanged.
- [ ] Existing fail-closed signing preflight remains enabled.
- [ ] Signed Release APK post-build certificate comparison passes.
- [ ] Package ID, version name/code, APK count, artifact name and checksum verification pass.

### B. Timer and action cards

- [ ] Exactly one coroutine timer driver exists per `CadDeckIdleTimerState` instance.
- [ ] Exactly one lifecycle observer exists per timer state.
- [ ] No duplicate Bind/remember timer loop remains on Send/Cancel or Release Catalogue.
- [ ] 75-second idle interval and 1-second peek are deterministic.
- [ ] Direct interaction resets the correct deck only.
- [ ] App pause/visibility/reduced-motion conditions are handled.
- [ ] Send/Cancel rear card is fully concealed at rest and cannot be tapped while concealed.
- [ ] Send/Cancel idle slide-peek appears at the specified time and retracts without running an action.
- [ ] Release Catalogue keeps one foreground card position; actions are not side by side.
- [ ] Release Catalogue tap-to-exchange animation and callback semantics pass.

### C. Home/Canvas

- [ ] V18.2 animated Home ↔ Canvas transition remains intact.
- [ ] Return to active drawing control is visible for an active session.
- [ ] Same unsaved document and relevant workspace state are restored.
- [ ] New/Open/Import cannot silently replace a dirty document.
- [ ] Save failure, Stay, and Discard paths are covered.
- [ ] System Back behaviour is correct.

### D. Gradle and lint

- [ ] The live repository's Wrapper is confirmed as 9.8.2, with matching scripts/JAR/checksum.
- [ ] Build, lint and licensing jobs report Gradle 9.8.2 on the same commit.
- [ ] Any displayed 9.3.1 notice is traced to a stale/different checkout or another named source; no unexplained notice remains.
- [ ] `help --warning-mode=all` reviewed; no actionable issue is silently suppressed.
- [ ] Unit tests pass.
- [ ] Release lint has zero errors and zero warnings on the final commit.
- [ ] Debug APK assembly succeeds.
- [ ] Signed Release assembly succeeds after the production signing guard passes.

### E. Licensing

- [ ] Live checkout SHA matches status JSON `commitSha`.
- [ ] Effective version name and code match the build configuration.
- [ ] The Python 3.12 setup step precedes ScanCode configure/version/help/scan.
- [ ] `python3.12` is present and reports the intended version.
- [ ] ScanCode archive checksum matches the pinned SHA-256.
- [ ] ScanCode `--version` and `--help` exit successfully.
- [ ] The actual ScanCode scan completes and genuine JSON is retained.
- [ ] ScanCode findings are merged into the consolidated file reports.
- [ ] Required dependency configurations are resolved; non-applicable variants are distinguished.
- [ ] Gradle Wrapper/toolchain metadata agrees with actual CI output.
- [ ] Status is `COMPLETE` only when all required scans are complete.
- [ ] Licensing remains informational/non-blocking.

### F. Delivery and security

- [ ] No private key, keystore, password, Base64 signing payload, `.env` secret, or PAT is committed or uploaded as a normal artifact.
- [ ] No new production key was generated.
- [ ] No signing secret value was printed or retrieved.
- [ ] Exact source commit before/after is reported.
- [ ] Only required files were changed; the CAD engine and unrelated features were not rewritten.
- [ ] No public GitHub Release/tag was created without explicit authorisation.

---

## 12. Required final report from the coding agent

The agent must return a structured report containing:

1. **Repository identity:** repository, branch, exact commit before and after implementation.
2. **Version metadata:** base version properties, computed effective `versionName`, `versionCode`, tag if applicable, and proof of monotonicity.
3. **Certificate parser:** old parser defect, corrected parsing method, exact test fixture outcome, malformed/multiple/mismatch test outcomes.
4. **Release signing:** keystore preflight result, number of APK signers, APK certificate SHA-256, trusted certificate comparison result, APK package/version verification result. Report public fingerprint only; never disclose secret values.
5. **Timer architecture:** number of lifecycle observers and timer drivers per state, removed call sites, and tests proving no duplicate advancement.
6. **Send/Cancel:** front/rear state behaviour, idle interval, peek duration, direct-interaction reset, reduced-motion behaviour, and test evidence.
7. **Release Catalogue:** stacked action layout, tap-to-exchange animation, action callback tests, and confirmation that Download/Install and Cancel are not side by side.
8. **Home/Canvas:** animation and return path, document-retention test outcomes, dirty-document protection, and save-failure behaviour.
9. **Gradle toolchain:** Wrapper version/checksum, `./gradlew --version`, JDK/AGP/Kotlin/KSP versions, warning-mode findings, and whether the old 9.3.1 notice was traced to a stale artifact.
10. **Lint/tests:** exact tasks, results, warning/error counts, and links or paths to fresh reports.
11. **Licensing:** exact source SHA, effective version, Python version, ScanCode version/status, archive checksum, configurations resolved/not applicable, file/component counts, completeness status, and artifact paths.
12. **Files changed:** workflows, helper scripts, source files, tests, and documentation.
13. **Security:** state explicitly that production credentials were not changed and no private signing material was committed or logged.
14. **Known limitations:** every incomplete test or CI action and the evidence preventing completion.

The report must not call the original fingerprint failure a wrong certificate: the user supplied identical preflight, APK signer, and trusted fingerprints. The correct root cause is the colon-field parsing defect in the workflow.

---

## 13. Evidence and implementation references

### Source snapshot and reports

- `PrecisionCAD-Studio-main.zip` — SHA-256 `b91d0a5dda2e76651c5ebc48c4e74cc2a591d4a5c4ff8cac8454f695be1e2e24`
- `android-lint-release-reports (1).zip` — SHA-256 `1776a3c76056ae4220dfecb74db3e82104cff87288b830903536cae8729c4d82`
- `precisioncad-licensing-inventory-1363111ef36baefc45b2262b6fb69a77b2426bdc.zip` — SHA-256 `adcc430a12226c05f2a517d7b3280ff8d42bebda237ac08e749cacf2214333e8`
- User-supplied build output states identical keystore and APK signer SHA-256 certificate fingerprints: `7bbcda1924e95ca1758c7e0ac763b56ae9f7eafef4c6e7362bf2d63c63531e59`.

### Source files to inspect first

- `.github/workflows/build-signed-apk.yml`
- `.github/workflows/lint.yml`
- `.github/workflows/licensing-inventory.yml`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `version.properties`
- `app/src/main/java/com/example/ui/components/CadBinaryToggleCard.kt`
- `app/src/main/java/com/example/ui/CadWorkspaceScreen.kt`
- `app/src/main/java/com/example/ui/CadWorkspaceViewModel.kt`
- `app/src/main/java/com/example/ui/components/CadReleaseCatalogueDialog.kt`
- `app/src/test/java/com/example/ExampleUnitTest.kt`
- `app/src/test/java/com/example/ExampleRobolectricTest.kt`
- `scripts/generate_licensing_inventory.py`
- `docs/BUILD_AND_RELEASE.md`
- `docs/LICENSING_INVENTORY.md`

### Official/vendor documentation

- Gradle 9.8.2 release notes: https://docs.gradle.org/9.8.2/release-notes.html
- Gradle distributions and checksums: https://gradle.org/releases/
- Android Gradle Plugin release/Gradle compatibility: https://developer.android.com/build/releases/about-agp
- Kotlin Gradle Plugin compatibility: https://kotlinlang.org/docs/gradle-configure-project.html
- Android `apksigner` reference: https://developer.android.com/tools/apksigner
- ScanCode Toolkit v32.5.0 release: https://github.com/aboutcode-org/scancode-toolkit/releases/tag/v32.5.0
- ScanCode Toolkit installation requirements: https://github.com/aboutcode-org/scancode-toolkit/blob/develop/INSTALL.rst
- GitHub `setup-python` action: https://github.com/actions/setup-python

---

**End of V18.3 Master Implementation Plan.**
