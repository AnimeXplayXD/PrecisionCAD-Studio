# PrecisionCAD Studio — Version 18 Master Implementation Plan

**Document status:** Final implementation specification for Version 18  
**Baseline source:** `PrecisionCAD-Studio-main.zip` supplied for the V18 review  
**Baseline archive integrity:** ZIP test passed; 126 entries; SHA-256 recorded in the handoff metadata below  
**Scope:** Release-quality remediation, Android 17/API 37 compilation and target migration, Paper Space rendering, zoom-adaptive snapping, polar acquisition correctness, QDIM/OSNAP/card interaction refinements, controller/IME motion stability, launcher artwork integration, and GitHub-backed in-app updating.  
**Authority:** This document is the complete V18 implementation brief. Treat the supplied source as the implementation baseline, not as permission to recreate V17 features.

---

## 0. Instructions to the coding agent

### 0.1 Execute this plan without guessing

Implement the requirements in this document in the stated order. Where a requirement specifies a value, state, colour, gesture, timing rule, file format, API or acceptance condition, treat it as intentional.

Do not substitute a visually similar control with different behaviour. Do not implement only the appearance while leaving the underlying state or geometry behaviour unchanged. Do not suppress lint warnings to make the report appear clean. Do not introduce unrelated features or rewrite the CAD engine to make an isolated UI correction.

Before changing a source file:

1. Inspect its current implementation and callers.
2. Confirm that the uploaded source snapshot is still the branch's actual baseline. If the working repository has commits newer than the supplied archive, preserve those commits and reconcile the differences; never overwrite newer work with the archive.
3. Identify existing state owners, rendering order, coordinate transformations, persistence rules and tests.
4. Make the smallest architectural change that satisfies the specified contract.
5. Add or update focused tests before proceeding to the next work package.

After each work package, run the relevant tests and build tasks. If a task cannot be completed because a required external value is absent (for example, the public GitHub release repository owner/name or the three supplied image assets), do not invent the value. Implement the surrounding configurable mechanism, record the blocker, and report exactly what is needed.

### 0.2 V17 is the completed baseline

V17 was reported as implemented successfully. Do not reimplement the V17 feature list as if it were new work. Preserve its approved behaviour, especially:

- shared live-preview input preservation;
- the final command-bar engagement rule;
- automatic private project saving and explicit `Documents/PrecisionCAD` saving;
- native `.pcad` project format and DXF/PDF/SVG interchange/export roles;
- PDF title-block filename consistency;
- QDIM selective removal semantics;
- independent visual Polar Tracking and PolarSnap configuration;
- the global enable/disable card language;
- the Touchpad/Joystick presentation and coordinate controls;
- the package identity `com.precisioncad.studio`;
- the established Home screen and dark engineering visual identity.

The V18 archive still contains some of the specific defects now being reported. The supplied source snapshot remains the code baseline to inspect and correct. Do not assume a requirement is implemented merely because it appears in V17 documentation; verify its current source behaviour.

### 0.3 Product identity and non-negotiable invariants

- Product name: **PrecisionCAD Studio**.
- Android application ID: `com.precisioncad.studio`. Preserve it; do not rename it.
- Application scope: native Android, offline-first, strictly 2D CAD.
- Geometry remains double-precision model-space data.
- The canvas is the stable drawing viewport. UI animations, keyboard movement, dialogs, card transitions, snapping feedback and panel expansion must not alter model geometry, model-to-screen transform, camera centre, zoom or pan unless the user explicitly performs a viewport operation.
- Existing geometry kernel, document model, Canvas renderer, ViewModel ownership and file-format implementations must be reused and improved. Do not create a second drawing engine or duplicate document state.
- Existing PDF, DXF and SVG functionality must remain available. Native project persistence remains `.pcad` as established in V17.
- Release signing identity must remain stable across all public updates.

### 0.4 Required engineering discipline

- Keep source changes local to the relevant subsystem.
- Keep user preferences, transient UI state, document data and viewport state separate.
- Keep visual previews separate from committed document entities.
- Keep Grid Snap, ORTHO, Visual Polar Tracking, PolarSnap and OSNAP as distinct mechanisms.
- Keep card appearance shared but provide per-deck gesture and activation policies.
- Do not give every card deck the same gesture recogniser.
- Do not introduce broad storage permissions where Android scoped APIs suffice.
- Do not embed GitHub personal access tokens, release keystore passwords or signing keys in the APK, source code, README or public workflow output.
- Do not claim a build is successful unless the actual build task completed successfully.

---

## 1. Source baseline and verified repository observations

### 1.1 Supplied source snapshot

The supplied archive is `PrecisionCAD-Studio-main.zip`. It contains 126 entries and passed ZIP integrity validation. It includes the Android application, version catalog, Gradle build files, two GitHub Actions workflows, root README and six existing subsystem documents.

The archive does not contain the complete Gradle Wrapper launch files and wrapper JAR. It contains `gradle/wrapper/gradle-wrapper.properties` only. Restore the complete official wrapper as described in Section 4.8.

No `.git` history is supplied inside the archive. Do not invent a commit SHA or claim that this archive represents a particular GitHub commit. The live repository's checked-out commit must be recorded by the coding agent when it begins work.

### 1.2 Current build configuration observed

The supplied `app/build.gradle.kts` currently declares:

```kotlin
namespace = "com.example"
compileSdk { version = release(36) { minorApiLevel = 1 } }

defaultConfig {
    applicationId = "com.precisioncad.studio"
    minSdk = 24
    targetSdk = 36
}
```

The current version catalog declares AGP `9.1.1`, Kotlin Compose plugin `2.2.10`, KSP `2.3.5`, Compose BOM `2024.09.00`, Room `2.7.0`, Lifecycle `2.8.7`, Core KTX `1.18.0`, Activity Compose `1.10.1`, and Coroutines `1.10.2`.

The CI workflows currently install Android platform 36 and Build Tools 36.0.0, use Gradle 9.3.1 directly, and use older Actions major versions. The source uses JDK 21 in CI and Java 11 source/target compatibility in the Android module. Preserve the Java language compatibility level unless a tested requirement justifies changing it; the JDK that runs Gradle is a separate setting.

### 1.3 Important current source locations

| Subsystem | Current source file(s) | Verified responsibility / issue |
|---|---|---|
| Snap and polar calculations | `app/src/main/java/com/example/cad/kernel/CadSnapAndCoordEngine.kt` | Fixed model-space Grid Snap; visual bearing can fall back to nearest angle; PolarSnap angle builder mixes visual custom angles into snap angles. |
| Viewport and keyboard layout | `app/src/main/java/com/example/ui/CadWorkspaceScreen.kt` | Command/controller overlay composition, IME insets, viewport safe-area measurement, selective-dimension dialog placement. |
| Canvas and Paper Space | `app/src/main/java/com/example/ui/components/CadViewportCanvas.kt` | Paper sheet, grid, title-block and polar-guide rendering. |
| QDIM and configuration dialogs | `app/src/main/java/com/example/ui/components/CadDialogsAndPanels.kt` | QDIM selectors, selection list, cursor/Grid/OSNAP configuration, locale-dependent formatting, OSNAP labels. |
| Shared binary cards | `app/src/main/java/com/example/ui/components/CadBinaryToggleCard.kt` | Shared binary toggle visual state; supplied status-label arguments are currently not forwarded to the inner component. |
| Controller dock | `app/src/main/java/com/example/ui/components/VirtualCursorController.kt` | Touchpad/Joystick controller, drag handle, immediate conditional collapse/expand. |
| Workspace state | `app/src/main/java/com/example/ui/CadWorkspaceViewModel.kt` | Cursor updates, snap calls, controller state, command and viewport state. |
| Database | `app/src/main/java/com/example/cad/data/CadRoomDatabase.kt` | Room-backed project metadata and related persistence. |
| Root build | `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml` | Plugin and dependency alignment. |
| CI | `.github/workflows/lint.yml`, `.github/workflows/build-signed-apk.yml` | Lint/build/release signing and publication. |
| Icons | `app/src/main/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml`, density `mipmap-*` folders | Existing generic/vector and legacy launcher icon resources. |
| Manifest | `app/src/main/AndroidManifest.xml` | `applicationId`-derived FileProvider, activity declaration, `adjustResize`, duplicate activity label. |

### 1.4 Existing documentation

The source already contains:

- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/BUILD_AND_RELEASE.md`
- `docs/COMMANDS_AND_PREVIEWS.md`
- `docs/DIMENSION_SYSTEM.md`
- `docs/GEOMETRY_AND_COORDINATES.md`
- `docs/STORAGE_AND_FORMATS.md`

Do not create a second README or duplicate architecture documents. Update these existing files to accurately describe the V18 implementation and actual build/release commands. Add another document only if a genuinely separate subsystem needs one and the existing structure cannot reasonably contain it.

---

## 2. V18 delivery workstreams and implementation order

Implement in this order unless a dependency requires a narrowly justified adjustment:

| Phase | Workstream | Primary outcome |
|---:|---|---|
| 0 | Baseline, branches and regression protection | Establish current commit, record source status, create tests and preserve V17 behaviour. |
| 1 | Release lint remediation | Resolve all 73 reported release lint findings without suppressing detectors. |
| 2 | Android 17/API 37 and build toolchain | Compile against the newest verified stable API 37 minor level; target API 37; align Gradle, AGP, Kotlin, Compose, KSP and CI. |
| 3 | Launcher icon integration | Integrate the three separately supplied icon masters and generate all Android resources. |
| 4 | Paper Space title block | Remove grid contamination and make title-block typography zoom-independent. |
| 5 | Zoom-adaptive Grid Snap | Scale effective model-space snap interval with viewport zoom and guard against large screen-space jumps. |
| 6 | Visual Polar Tracking / PolarSnap correction | Acquire visual guides only when actually aligned and strictly separate visual and cursor-constraining snap angle sets. |
| 7 | QDIM selectors and dialogue layout | Replace vertical gesture selectors with stable multi-card navigation and eliminate gap/scroll conflicts. |
| 8 | Selective dimension removal | Make selection notices transient, keep Select All accessible and preserve scroll position. |
| 9 | Shared card motion system | Implement transient rear reveal, full concealment at rest and section-local periodic idle peeks. |
| 10 | OSNAP card labels and stability | Fixed mode abbreviations, moving full labels, correct ENABLE/DISABLE text and stable scroll position. |
| 11 | Floating command dock / IME / controller | Translucent command region, one-source IME positioning and animated controller collapse/expand. |
| 12 | GitHub release catalogue and in-app updater | Home update indicator, release history, download, integrity validation and Android installer confirmation. |
| 13 | Integrated validation and documentation | Full regression matrix, clean Release lint, signed APK verification and documentation update. |

Do not publish a production Release from an intermediate development commit merely because a build succeeds. Use the repository's agreed release branch and publication gate.

---

# PART A — RELEASE QUALITY, SDK AND BUILD SYSTEM

## 3. Android lint remediation — all 73 reported warnings

### 3.1 Required baseline and outcome

The supplied `android-lint-release-reports.zip` contains the HTML, text and XML Release lint reports. The report records **73 warnings and zero lint errors**.

| Detector | Count | Required treatment |
|---|---:|---|
| `GradleDependency` | 22 | Update only active dependency declarations; remove or document inactive aliases rather than activating them. |
| `UnusedResources` | 16 | Remove truly unused resources or migrate still-visible hard-coded text into resources. |
| `UseKtx` | 11 | Apply scoped KTX helpers without behavioural change. |
| `NewerVersionAvailable` | 9 | Update active Kotlin/coroutine/testing dependencies after compatibility validation. |
| `DefaultLocale` | 6 | Standardise engineering numerical formatting with `Locale.ROOT`. |
| `IconLauncherShape` | 5 | Replace legacy launcher artwork generated from the supplied icon masters. |
| `AndroidGradlePluginVersion` | 2 | Align AGP and Gradle Wrapper using a formally supported combination. |
| `OldTargetApi` | 1 | Migrate target SDK to API 37. |
| `RedundantLabel` | 1 | Remove redundant activity-level label if no distinct activity label is intended. |
| **Total** | **73** | **Zero actionable Release lint warnings after remediation.** |

Do not use a lint baseline, blanket `@Suppress`, disabled detectors, or wildcard exclusions to make the report green. A suppression is allowed only for a demonstrably unavoidable false positive, with a narrow scope and a source comment explaining why. Any such exception must be listed in the final agent report.

### 3.2 Locale-independent CAD numerical formatting — six findings

**Source:** `app/src/main/java/com/example/ui/components/CadDialogsAndPanels.kt`, around the line diagnostic formatting block at line 3036 in the supplied snapshot.

Six `String.format()` calls format four point coordinates, one segment length and one angle without an explicit locale. This can produce locale-dependent decimal separators, which is unsuitable for engineering coordinate readouts and copied coordinate values.

Required correction:

- Use `Locale.ROOT` for invariant CAD numerical formatting.
- Coordinate and length values in the affected diagnostic remain three decimal places.
- Angle remains two decimal places and retains the degree symbol.
- Do not change the underlying double-precision value, units, rounding policy or geometry.
- Apply the same invariant formatting rule to other CAD numeric readouts where output is intended to be a machine-consistent engineering value.
- Do not force `Locale.ROOT` on natural-language dates or translated text.

Create or extend one shared `CadNumberFormatter` (or the repository's equivalent utility) so components do not invent independent formatting rules. Preserve existing formatting APIs where callers rely on them.

Tests must run the same formatting cases under at least `Locale.US`, `Locale.GERMANY`, `Locale.forLanguageTag("bn-IN")`, and `Locale.forLanguageTag("tr-TR")`. The output must keep `.` as decimal separator and the specified decimal count.

### 3.3 Dependency update audit — distinguish active from inactive aliases

The lint report contains 31 dependency-version findings (22 `GradleDependency` plus nine `NewerVersionAvailable`). Source inspection shows that several candidate dependencies remain cataloged but their `implementation` declarations are commented out in `app/build.gradle.kts`.

**Do not activate optional dependencies merely to silence warnings.** In particular, do not introduce CameraX, Retrofit, OkHttp, Firebase, Navigation, DataStore, Credentials, Play Services Location or Coil functionality if it is not used by the current app.

The active dependency candidates identified from the supplied source include:

| Active component | Existing source version | Report candidate / review target |
|---|---:|---:|
| Core KTX | 1.18.0 | 1.19.1 |
| Lifecycle Runtime KTX | 2.8.7 | 2.11.0 |
| Lifecycle ViewModel Compose | 2.8.7 | 2.11.0 |
| Lifecycle Runtime Compose | 2.8.7 | 2.11.0 |
| Activity Compose | 1.10.1 | 1.13.0 |
| Compose BOM | 2024.09.00 | 2026.09.00 |
| Room Runtime | 2.7.0 | 2.8.5 |
| Room KTX | 2.7.0 | 2.8.5 |
| Room Compiler | 2.7.0 | 2.8.5 |
| AndroidX Test Core | 1.6.1 | 1.7.0 |
| AndroidX Test Runner | 1.6.2 | 1.7.0 |
| Kotlin Compose plugin | 2.2.10 | 2.4.21 |
| Coroutines Test | 1.10.2 | 1.11.0 |
| Coroutines Android | 1.10.2 | 1.11.0 |
| Coroutines Core | 1.10.2 | 1.11.0 |
| Robolectric | 4.16.1 | 4.17 |

These are review candidates taken from the uploaded lint report, not unconditional instructions to use stale exact versions. Before editing the catalog, check the stable release currently available and verify compatibility with the selected AGP/Kotlin/Gradle combination. Never use dynamic `+` versions.

Required grouping:

1. Compose BOM and active Compose consumers.
2. Lifecycle and Activity Compose.
3. Room runtime, KTX and compiler as one coordinated set.
4. Coroutines runtime and test dependencies as one set.
5. AndroidX test libraries and Robolectric.
6. Kotlin Compose plugin and KSP only after the build-tool compatibility gate in Section 4.

For every group, run compilation and tests before moving to the next group. Room updates must include database migration tests and persistent-project read/write tests. Do not change schemas or migrations just because Room was upgraded.

For inactive version-catalog aliases, either remove genuinely abandoned aliases or retain them with a clear `# optional / currently unused` comment and do not claim that the app upgraded them. The final lint target may still flag unused version-catalog entries depending on the detector; resolve by deleting unused declarations only after confirming no build logic, test or documentation references them.

### 3.4 KTX recommendations — eleven findings

The report identifies these locations in the supplied source:

| File | Location | Suggested idiom |
|---|---:|---|
| `CadDxfInterchange.kt` | 1125 | KTX bitmap creation helper where equivalent. |
| `CadDxfInterchange.kt` | 1387 | `Canvas.withClip`. |
| `CadDxfInterchange.kt` | 1627 | `Canvas.withSave`. |
| `CadRoomDatabase.kt` | 125 | `SharedPreferences.edit` extension. |
| `CadViewportCanvas.kt` | 868 | `Canvas.withSave`. |
| `CadViewportCanvas.kt` | 893 | `Canvas.withSave`. |
| `CadViewportCanvas.kt` | 1163 | `Canvas.withSave`. |
| `CadViewportCanvas.kt` | 1200 | `Canvas.withSave`. |
| `CadViewportCanvas.kt` | 1249 | `Canvas.withSave`. |
| `CadWorkspaceViewModel.kt` | 1020 | `SharedPreferences.edit` extension. |
| `CadWorkspaceViewModel.kt` | 3382 | `SharedPreferences.edit` extension. |

Reconfirm exact current line positions before modifying because preceding edits may shift lines.

KTX changes must preserve:

- exact Canvas save/restore nesting;
- existing clipping rectangles and drawing order;
- transform state after nested draw calls;
- preference keys, defaults and stored values;
- `apply()` versus `commit()` behaviour;
- exception-safe restoration.

Do not use this lint pass to refactor the DXF exporter, database layer or renderer wholesale.

### 3.5 Unused resources — sixteen findings

The supplied source has seven unused colour resources in `app/src/main/res/values/colors.xml`:

- `purple_200`
- `purple_500`
- `purple_700`
- `teal_200`
- `teal_700`
- `black`
- `white`

It has nine unused string resources in `app/src/main/res/values/strings.xml`:

- `workspace_subtitle`
- `mode_model_space`
- `mode_paper_space`
- `controller_touchpad`
- `controller_joystick`
- `qdim_button_label`
- `cmd_matrix_title`
- `unit_conversion_title`
- `export_share_title`

Search Kotlin, XML, manifest, generated-resource references and string-resource usages before deletion. If the associated wording is still displayed in Compose as a hard-coded user-facing label, migrate it to the resource file using `stringResource` rather than deleting it. Delete only resources with no remaining functional purpose. Keep `app_name`.

### 3.6 Redundant activity label

The supplied manifest declares `android:label="@string/app_name"` on both `<application>` and `.MainActivity`. Remove the activity-level duplicate only if the activity does not intentionally use a different title. Retain the application label.

### 3.7 Lint acceptance

Run `./gradlew :app:lintRelease` after the wrapper is restored. Save and inspect the HTML/XML/text output. Final result must report zero errors and zero actionable warnings. If a warning cannot be eliminated without changing intended behaviour, document the exact detector, line, reason and narrowly scoped suppression; do not silently ignore it.

---

## 4. Android 17, API 37.2 compilation and build-toolchain alignment

### 4.1 Required SDK levels

The V18 target is:

- `minSdk = 24` — preserve the existing minimum.
- `targetSdk = 37` — Android 17 platform behaviour.
- Compile SDK: API 37, preferred minor API level 2 (`37.2`) if the stable SDK package and selected AGP support it.

The Android platform's target SDK is an integer API level. Use `targetSdk = 37`, not `37.2`.

The Gradle compile DSL supports a `minorApiLevel` integer. Preferred form:

```kotlin
android {
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        minSdk = 24
        targetSdk = 37
    }
}
```

Before committing this exact setting, the agent must verify that the **stable** SDK platform package for API 37 minor 2 is available in the configured SDK repository and that the selected AGP can consume it. Use `sdkmanager --list --channel=0` and install the exact available stable platform package. If API 37.2 is not offered as a stable SDK package or the selected AGP cannot compile it, use API 37.1 (or the latest stable API 37 minor package actually offered) and record the reason. Do not use a preview or alpha package merely to display a higher number.

`37.2a` is an incremental API-difference specification, not a `minorApiLevel` integer. Do not write `minorApiLevel = "2a"` or invent an SDK package name. The latest numbered API specification verified for this plan is 37.2; the official 37.2a incremental diff does not make `2a` a valid `minorApiLevel`.

### 4.2 Toolchain compatibility gate

The supplied source currently uses AGP 9.1.1, Gradle 9.3.1, Kotlin Compose plugin 2.2.10, KSP 2.3.5 and CI JDK 21.

Use a fully supported stable combination, not the newest independent number from every tool. The compatibility candidate verified during this plan's preparation is:

| Component | Compatibility-first candidate |
|---|---:|
| AGP | 9.3.1 |
| Gradle Wrapper | 9.7.0 |
| Kotlin Compose plugin | 2.4.21 |
| KSP | 2.3.11, subject to processor/Gradle build verification |
| CI JDK | 21 |
| Java source/target compatibility | Preserve 11 unless a tested reason requires change |

The Kotlin compatibility table lists Kotlin Gradle Plugin 2.4.20–2.4.21 as fully supported through Gradle 9.7.0 and AGP 9.3.1. AGP 9.3 requires Gradle 9.5.0 or newer, JDK 17 or newer, and supports Android API 37. This candidate is therefore a conservative combination for this source.

AGP 9.5.0 is a newer stable AGP release at the time of preparing this plan and requires Gradle 9.6.0 or newer, JDK 17 or newer and supports API 37. However, the Kotlin compatibility table cited above does not list Kotlin 2.4.21 as fully supported with AGP 9.5.0. Do not combine AGP 9.5.0 and Kotlin Compose plugin 2.4.21 merely because each is individually stable. Upgrade to AGP 9.5 only if the official Kotlin/Compose compatibility information at implementation time confirms a supported stable pairing and KSP/Room/Moshi processors pass the full test suite. Otherwise, use the compatibility-first candidate.

For KSP, verify the selected KSP plugin with both active processors (Room compiler and Moshi Kotlin codegen). The candidate 2.3.11 is a current KSP release in the source review, but the final acceptance is a clean KSP compile and generated-source test, not the version string alone.

Do not change Java source/target compatibility from 11 to 21 merely because JDK 21 runs Gradle. Keep these roles distinct.

### 4.3 Gradle Wrapper restoration

The archive contains `gradle/wrapper/gradle-wrapper.properties` but lacks `gradlew`, `gradlew.bat` and `gradle-wrapper.jar`.

Restore the official wrapper for the selected Gradle version. Commit all three wrapper components and ensure the wrapper distribution URL and checksum are correct. Generate/upgrade the wrapper with the supported Gradle wrapper task and verify the result using both `./gradlew --version` and the Android build.

Both GitHub Actions workflows must use `./gradlew`, not a separately installed unpinned `gradle` binary. No CI step should silently choose a different Gradle version from the repository wrapper.

### 4.4 Android SDK installation in CI

Both `.github/workflows/lint.yml` and `.github/workflows/build-signed-apk.yml` currently install `platforms;android-36` and `build-tools;36.0.0`.

Update those installations to the exact stable API 37/minor 2 platform package supported by the selected AGP, plus the Build Tools version required by that AGP. Do not use a dynamic `latest` package for reproducible builds; choose the verified stable version and keep local and CI SDK expectations aligned.

### 4.5 Android 17 runtime migration

Target API 37 and test its behaviour changes. In particular, test adaptive/resizable large-screen layouts at smallest width 600 dp and above. The CAD application uses a full-screen canvas with overlay controls; it must not assume fixed portrait dimensions or fixed bottom-dock placement.

Test:

- phone portrait and landscape;
- tablet and large-screen window resize;
- multi-window / freeform resize if supported by device;
- status/navigation bar and immersive sticky behaviour;
- edge-to-edge and IME insets;
- Paper Space sheet and title block;
- modal dialogs, QDIM and configuration window scrolling;
- controller docking and collapse/expand;
- process recreation and activity configuration change;
- app update installer flow on supported Android versions.

Preserve `minSdk = 24` and guard API-specific calls. Do not add direct calls to API 37 without version guards if the app still supports API 24.

### 4.6 Existing action versions and Node runtime

The current workflows use older action majors (`checkout@v4`, `setup-java@v4`, `setup-android@v4`, `gradle/actions/setup-gradle@v4`, `upload-artifact@v4`, `softprops/action-gh-release@v2`). Update them to maintained stable action releases that run on the supported Node 24 runtime. At the time this plan is being prepared, examples include:

- `actions/checkout@v7.0.1`;
- `actions/setup-java@v6`;
- `android-actions/setup-android@v4.0.4`;
- `gradle/actions/setup-gradle@v6.4.1`;
- `actions/upload-artifact@v7.0.2`;
- `softprops/action-gh-release@v3.0.3` if that action is retained.

Before implementation, verify current release tags, `runs.using` runtime and hosted-runner requirements from each action's official repository. Do not assume every action with a newer tag uses Node 24. Use exact pinned commit SHAs if repository policy requires immutable dependencies; otherwise use maintained major tags and enable Dependabot updates for GitHub Actions. Hosted runners must satisfy the action runtime minimum. Do not use deprecated Node 20 or Node 24 compatibility shims.

### 4.7 Release signing — fail closed

The current workflow creates a newly generated fallback release keystore when secrets are absent. A fixed password and alias do not make a newly generated cryptographic key deterministic. That fallback can produce an APK that cannot update the user's installed build if its signing certificate differs.

Required correction:

- Public Release APK signing must use the persistent release keystore already used for published PrecisionCAD releases.
- Store keystore bytes/base64 and passwords only in protected GitHub Secrets or an appropriately protected environment.
- Require all expected secrets; fail the Release job before `assembleRelease` if missing.
- Verify the resulting certificate fingerprint against the known trusted release certificate.
- Never generate a replacement production key automatically.
- Never commit or print keystore contents, passwords or private key data.
- Debug uses the standard debug signing identity and is never attached to a public production Release.
- The lint job must not create or use a fake production signing key. Configure lint so signing is not required; if AGP configuration forces a keystore file during lint task configuration, use a disposable throwaway lint-only key that is never used to build or publish a Release APK and is clearly named as non-production.

Do not alter the existing Android application ID. A public Release update must keep `com.precisioncad.studio` and the trusted release signing identity.

### 4.8 Exactly one APK per build variant and direct artifact upload

The current workflow makes duplicate copies of the same Release APK under multiple names and does the same for Debug. It then uploads one named copy. Remove all duplicate-copy output logic.

Produce at most:

- one signed Release APK, named `PrecisionCAD-Studio-v<versionName>-b<versionCode>-release.apk`;
- one Debug APK, named `PrecisionCAD-Studio-v<versionName>-b<versionCode>-debug.apk`.

Use `actions/upload-artifact@v7` with `archive: false` for each individual APK. The direct-upload mode supports one file per artifact. Upload Release and Debug separately with clear identifiers. Do not include a duplicate APK, ZIP containing APK copies, or unrelated files in the APK artifact. If a checksum file is retained, upload it as a separate small artifact or include its value in the workflow summary/release description.

The public GitHub Release must contain exactly one production Release APK. Do not attach the Debug APK to the public production release. The release asset must be a raw `.apk` file, not an Actions artifact archive.

### 4.9 Automated public GitHub Release publication

Preserve the V17 requirement: a successful qualifying commit build publishes the Release APK automatically, with the triggering commit subject used as the release title.

Define the canonical primary branch in one workflow/environment variable or repository configuration. On a successful push to that branch:

1. Check out the exact triggering commit.
2. Run lint and tests.
3. Build one signed Release APK.
4. Verify package ID, version code/name, signing certificate and APK signature.
5. Ensure its version code is strictly greater than the latest previously published stable Release version code. Fail if it is not.
6. Generate a unique release tag from run number and short commit SHA; never overwrite an unrelated prior release.
7. Use the first line of the triggering commit subject as GitHub Release title. If blank, use a deterministic `PrecisionCAD Studio v<versionName> (build <versionCode>)` fallback.
8. Generate release notes in structured headings: `New`, `Fixes`, `Optimisations`, `Security`, `Other` (omit empty groups).
9. Publish only after all checks succeed.
10. Attach exactly one raw Release APK.
11. Include version name/code, commit SHA, publication timestamp and SHA-256 digest in the release description/metadata.

Pull-request builds must not publish. A manual workflow-dispatch build may build Release, Debug or both variants, but must not publish a production Release unless an explicit, protected `publish_release` input is selected and the job passes all checks. A Debug-only run must never publish.

If a version tag push is also supported, ensure it does not cause a duplicate release for a commit already published by the primary-branch workflow. Use idempotent release/tag logic.

The release repository must be public for anonymous in-app update queries/downloads. If source control remains private, use a designated public distribution repository and a narrowly scoped protected workflow credential for publishing. Never embed a GitHub token in the APK.

### 4.10 README and build documentation updates

Update `README.md` and `docs/BUILD_AND_RELEASE.md` to reflect the actual selected API, Gradle Wrapper commands, tool versions, signing secrets, one-APK-per-variant behaviour and public release publication. Remove inaccurate claims that the fallback key is deterministic. Add the updater's public distribution repository configuration once it is supplied.

---

## 5. Launcher icon integration — three image assets supplied separately

### 5.1 User-supplied image-source contract

The three icon images will be supplied to the coding agent **as three separate files**, not embedded in this implementation plan and not as a contact sheet. The agent must wait until those separate source files are present in the task/workspace before completing icon integration.

The required source roles are:

1. **Adaptive foreground** — transparent square image containing only the PrecisionCAD emblem, with slate-blue/gold artwork and no background.
2. **Adaptive background** — separate full-bleed dark charcoal/deep-blue background artwork, without the emblem.
3. **Monochrome** — transparent single-colour white silhouette of the exact same emblem geometry as the foreground.

Expected master dimensions are 1024 × 1024 pixels. Validate the actual files. Do not generate substitute artwork, use the previous XML vector as the new logo, crop a screenshot, or fabricate a missing layer. If any file is absent or misidentified, stop only the icon subtask and report the missing role.

The source filenames may differ. Identify each file by its actual appearance and alpha channel; do not rely only on filename. Record the original supplied filenames and map them to canonical internal asset names such as:

- `precisioncad_foreground.png`
- `precisioncad_background.png`
- `precisioncad_monochrome.png`

Keep unmodified masters under a clear input/design-asset directory or an external source-asset folder, and place generated Android resources under `app/src/main/res`. Do not overwrite the masters with resized outputs.

### 5.2 Android adaptive icon construction

Android adaptive icons require separate foreground and background layers; themed icons require a separate monochrome layer. Use the 108 × 108 dp adaptive layer canvas. The important emblem must sit within the central 66 × 66 dp safe region, with no meaningful detail depending on the outer 18 dp edges. The artwork must have clean edges and no baked-in rounded-square, circle, squircle mask or external shadow.

Required resource changes:

- Replace current `ic_launcher_foreground` artwork with the supplied foreground master, correctly scaled into the adaptive icon canvas without distorting aspect ratio.
- Replace current `ic_launcher_background` with the supplied background master.
- Add a genuinely separate monochrome drawable/resource from the supplied monochrome image. Do not point `<monochrome>` to the multicoloured foreground.
- Update both `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` to reference the correct foreground, background and monochrome resources.
- Preserve `android:icon` and `android:roundIcon` manifest references unless there is a verified reason to simplify them.

### 5.3 Legacy launcher icons

Regenerate the five standard density launcher PNGs for `mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`. If the `roundIcon` manifest/resource is retained for supported launcher fallback, regenerate the corresponding round fallback resources from the same design system.

Do not create five independent logo designs. All density outputs derive from the same supplied masters. Preserve the safe-zone composition and clean silhouette; do not bake a single OEM mask into the master image. Inspect raster output for edge aliasing, transparency, scaling and crop behaviour.

### 5.4 Icon verification

Verify:

- foreground and monochrome files have genuine alpha transparency;
- background is fully opaque unless the source intentionally specifies transparency;
- foreground and monochrome geometry match exactly;
- no checkerboard is baked into transparency;
- no text, watermark, sample-board or phone mockup is present;
- symbol remains legible at 48 × 48 dp;
- adaptive icon remains recognisable under circular, squircle and rounded-square masks;
- themed icon rendering uses the dedicated monochrome layer;
- five legacy densities have correct dimensions and appearance.

The user supplies **three master files**, not one file per launcher shape or density. Android resources are generated from those three assets.

---

# PART B — CAD RENDERING AND GEOMETRIC CORRECTNESS

## 6. Paper Space title block: opaque grid isolation and stable text

### 6.1 Verified defect

**Source:** `app/src/main/java/com/example/ui/components/CadViewportCanvas.kt`.

The current Canvas draw order calls `drawPaperSpaceSheet(...)` before `drawAdaptiveGrid(...)`. The grid is drawn later and therefore overlays the Paper Space title-block background. The title-block fill colour itself is not the problem; the later grid pass is.

The title-block text size is also currently calculated from the title-block screen-space rectangle, for example:

```kotlin
textPaint.textSize = (tbSize.height * 0.20f).coerceIn(9f, 18f)
textPaint.textSize = (tbSize.height * 0.16f).coerceIn(8f, 14f)
```

Because `tbSize.height` changes with zoom, the lettering becomes smaller when the sheet is zoomed out and larger/clamped when zoomed in. This contradicts the requested stable title-block typography.

### 6.2 Correct render-pass order

Refactor the Paper Space draw pipeline into distinct visual passes. Required conceptual order:

1. Canvas background.
2. Paper sheet fill.
3. Paper sheet border and printable margin.
4. Adaptive grid and coordinate axes.
5. Title-block background fill, drawn **after** the grid so it completely covers the grid below it.
6. Title-block outline and separators.
7. Title-block text and metadata.
8. Committed drawing entities and any remaining overlays in their intended layering order.

If entity rendering or selection overlays currently occur in a different order for correct CAD semantics, preserve those semantics while ensuring no grid pass can draw over the opaque title-block background or title-block text. The title-block pass must be an opaque visual island over the grid.

Keep the current background colour unless a separate contrast test demonstrates that it fails. The user explicitly said the existing colour is not the issue.

### 6.3 Zoom-independent typography

The title-block rectangle and its anchor remain in paper/model geometry and follow the sheet viewport transform. The text must use stable screen-space sizing independent of `pixelsPerModelUnit`.

Implementation requirements:

- Convert desired dp text sizes to physical pixels using the current Compose density.
- Do not derive text size from `tbSize.height` or model-space scale.
- Main title, drawing number/revision and metadata lines keep stable screen-space sizes.
- The user-visible title, drawing number, units, paper size, scale and standard retain their existing content.
- Preserve the existing bottom-right title-block anchoring to the paper sheet.
- Use deterministic clipping/truncation/line omission if the title-block region becomes too small to fit stable text. Do not shrink the font continuously to illegibility.
- At useful zoom levels, keep the main title more prominent than secondary metadata.
- Save and restore Paint state or use dedicated Paint instances so title-block font size, colour, typeface and anti-aliasing cannot leak into ordinary CAD text rendering.
- Keep dimensions and model-space text on their existing geometry-scaled paths; this fixed-screen-size rule applies specifically to Paper Space title-block typography and similar fixed UI metadata, not to all drawing text.

Suggested initial screen-size range for calibration: main title 14–16 sp/dp equivalent, metadata 10–12 sp/dp equivalent. These are initial values to test, not reasons to alter drawing scale. Use the existing CAD typography system if it already supplies a more consistent screen-space scale.

### 6.4 Acceptance tests

- Grid visible elsewhere, never visible inside title-block background.
- Zoom in and out repeatedly; title text keeps readable, stable screen-space size.
- Paper sheet zoom/pan keeps title block anchored to the same sheet corner.
- Grid spacing and document coordinates remain unchanged.
- Title-block drawing number, revision, units, scale and standard remain correct.
- No Paint-state leakage into model text or dimensions.
- Exported PDF remains visually correct and has no grid contamination in the title block unless the chosen plot configuration explicitly includes a grid (default title-block background must still mask it).

---

## 7. Zoom-adaptive Grid Snap and cursor alignment

### 7.1 Verified defect

**Source:** `app/src/main/java/com/example/cad/kernel/CadSnapAndCoordEngine.kt`.

The current implementation applies a fixed model-space interval:

```kotlin
fun applyGridSnap(rawPoint: Point2D, interval: Double): Point2D {
    val safeInterval = interval.coerceAtLeast(1e-4)
    val snappedX = (rawPoint.x / safeInterval).roundToLong() * safeInterval
    val snappedY = (rawPoint.y / safeInterval).roundToLong() * safeInterval
    return Point2D(snappedX, snappedY)
}
```

The ViewModel calls this with `gridSnapConfig.gridSnapInterval`. The calculation does not consider the viewport's current `pixelsPerModelUnit`. At extreme magnification, a fixed model-space interval can correspond to an excessively large screen-space jump and the snapped candidate can map outside the visible canvas.

### 7.2 Required behaviour

Grid Snap must preserve the user's configured base interval as the reference preference, but calculate an effective model-space interval based on the current zoom factor.

Define:

- `S0`: configured base Grid Snap interval (for example, 5.0 mm).
- `Z0`: reference `pixelsPerModelUnit` captured when the drawing first enters its normal initial fitted view for the current document session.
- `Z`: current `pixelsPerModelUnit`.
- `Seff`: effective interval for the current viewport.

Use:

\[
S_{eff} = S_0 \times \frac{Z_0}{Z}
\]

Then quantise `Seff` to a clean engineering progression (1–2–5 × powers of ten) where this does not create a larger-than-configured screen-space jump. Clamp it to a positive finite minimum supported by the document's numerical precision.

This gives:

- initial reference zoom: effective interval equals configured interval;
- zoom in by 2×: effective model-space interval halves;
- zoom in by 10×: effective model-space interval becomes one tenth;
- zoom out: effective interval grows proportionally.

Keep `gridSnapInterval` as the user's configured base preference. Do not overwrite it with the temporary effective interval.

### 7.3 Reference scale lifecycle

Add a clearly owned reference-scale value to viewport/snap-session state. Capture it once when the document's initial normal/Fit viewport is established. Preserve it through pan, pinch and ordinary zoom changes. Reset it only when a new drawing/document is opened or a new document view session begins.

An explicit later Fit operation may change the camera scale, but must not silently mutate the user's base interval. Define whether Fit recaptures the reference scale only if product testing proves the original baseline is no longer useful; default behaviour is to preserve the session reference so zoom adaptation remains predictable.

If there is no valid reference scale yet (startup before viewport measurement), do not snap using NaN, infinity or a guessed scale. Use the configured base interval until the first valid reference is captured.

### 7.4 Coordinate and viewport safety

- Calculate every snap from the current raw cursor candidate, never from a previously snapped candidate.
- Keep coordinates double precision.
- Do not round geometry or alter the stored entity coordinates beyond the actual snap operation.
- Ensure effective step is finite and greater than zero.
- Ensure `roundToLong` conversion cannot overflow for large coordinate magnitudes; validate/clamp safely before conversion.
- If the snapped candidate maps outside the usable viewport or requires an unreasonable screen-space displacement, do not force it. Preserve the raw candidate until a valid nearby snap target is available.
- Grid Snap must not itself cause the viewport to pan merely because the snapped point is off-screen. Auto-pan/edge-pan should be driven by the intended cursor-navigation rule, not by a large snap displacement.
- Use a maximum acceptable screen-space snap displacement and derive it from a stable dp threshold; a starting guard of approximately 24 dp is reasonable and must be tested on phone and tablet densities.
- Prevent interval flicker at quantisation boundaries with hysteresis or stable step selection.
- Display the currently effective interval in the GSNAP indicator while preserving the base preference in the configuration UI.

### 7.5 Keep snapping mechanisms separate

- Grid Snap rounds model X/Y to an adaptive grid lattice.
- ORTHO constrains direction to horizontal/vertical axes; it does not apply a fixed integer-coordinate grid.
- OSNAP resolves geometric entity features.
- Visual Polar Tracking displays acquired angular guides but does not itself constrain candidate coordinates.
- PolarSnap projects the candidate onto its own selected angular rays only when enabled.

Do not combine these into one general rounding function.

### 7.6 Acceptance tests

Use identical raw cursor points at several viewport zoom factors, including extreme magnification where coordinate text changes by decimal fractions. Verify effective snap interval scales inversely with zoom, visible screen-space step stays controlled, and the cursor never jumps outside the canvas as a result of Grid Snap. Verify ORTHO, OSNAP and PolarSnap remain independent. Test very large/small coordinates and non-finite input defensively.

---

## 8. Visual Polar Tracking and PolarSnap: independent acquisition and rendering

### 8.1 Verified defects

**Source:** `app/src/main/java/com/example/cad/kernel/CadSnapAndCoordEngine.kt`.

The current `buildPolarSnapAngles()` appends both `polarSnapCustomAnglesDeg` and `polarAdditionalAnglesDeg`. The latter is the custom Visual Polar Tracking collection. This mixes visual guide angles into the cursor-constraining PolarSnap angle set.

The current `computePolarTrackingPreview()` also:

- uses a broad shared acquisition tolerance clamped between 0.5° and 45°;
- gives an acquired PolarSnap direction priority when selecting the Visual Polar Tracking angle;
- otherwise always falls back to the nearest whole-degree angle even when no genuine acquisition test passes.

As a result, a guide ray/arc can remain visible because a PolarSnap ray is near the cursor, or because a nearest whole-degree candidate is assigned unconditionally. This is the blue guide line persisting when the cursor is no longer aligned with that visual direction.

### 8.2 Three independent angle concepts

Maintain separate state values and meanings:

- **R — Radial Distance:** Euclidean distance from the accepted base point to the raw cursor candidate in model units.
- **θ — Cursor Bearing:** actual, unrounded candidate direction relative to the configured reference basis.
- **θₚ — Track Bearing:** the Visual Polar Tracking angle that has genuinely been acquired within visual tolerance.
- **PolarSnap bearing:** an independently acquired selected PolarSnap ray used only for candidate projection when PolarSnap is enabled.

The visual `θₚ` state must never be assigned from an acquired PolarSnap angle. The actual cursor bearing θ remains read-only and unrounded.

### 8.3 Visual angle collection

When Visual Polar Tracking is enabled, the visual guide angle set consists of:

- every whole-degree bearing from 0° through 359°;
- custom fractional Visual Polar Tracking angles supplied through the visual custom-angle input.

Normalise angles into [0, 360), reject non-finite values, remove duplicates with a documented epsilon, and sort deterministically.

All whole-degree values are available candidates. Their availability does **not** imply that a guide is currently acquired.

### 8.4 Visual acquisition rule

Only acquire `θₚ` when the raw cursor bearing is genuinely close to a candidate in the visual guide set.

Use a dedicated visual-acquisition tolerance, separate from PolarSnap's snap tolerance. Start with:

- acquire when angular difference is at or below 0.35°;
- release an acquired guide only after the difference exceeds 0.55° (hysteresis).

The values are intended to prevent guide flicker while ensuring the nearest integer direction is not unconditionally acquired. Keep them in one named configuration constant and test with actual cursor movement. Do not clamp this visual tolerance to the PolarSnap tolerance.

If no visual angle is within acquisition tolerance:

- `activePolarAngleDeg`/`θₚ` is null;
- `worldPolarAngleDeg` is null;
- `isPolarGuideAcquired` is false;
- do not draw the blue visual guide ray, associated angular arc or visual guide label.

When a guide is acquired, derive world angle from the current reference basis plus relative bearing. Relative-to-last-segment must keep using the previous segment reference when enabled; otherwise use +X.

### 8.5 PolarSnap angle collection

PolarSnap is configured independently through:

- a multi-select set of standard presets;
- a separate custom PolarSnap angle collection.

`buildPolarSnapAngles()` must include **only** selected PolarSnap presets and `polarSnapCustomAnglesDeg`. It must not append `polarAdditionalAnglesDeg` or the all-degree Visual Polar Tracking collection.

No implicit fallback should silently turn all whole-degree directions into PolarSnap directions. If no PolarSnap preset is selected, follow the explicit persisted default preset defined by the configuration model; do not derive a hidden all-degree set.

The user may choose multiple presets simultaneously. PolarSnap's selected collection is independent from the all-degree Visual Polar Tracking set.

PolarSnap has its own acquisition tolerance (the existing user-configurable snap tolerance may remain if correctly scoped). Its acquired ray and projected point may constrain the accepted candidate only when `polarSnapEnabled` is true. Visual Tracking alone never moves the cursor or projected candidate.

### 8.6 Rendering contract

The Canvas should draw:

- R and θ readout when a valid base point and non-degenerate direction exist;
- θₚ, visual ray, angular arc and visual label only when Visual Polar Tracking has acquired a guide;
- any PolarSnap-specific projection indication only when PolarSnap is independently acquired/enabled, visually distinguished from the informational visual guide if rendered;
- no permanent blue ray when no current visual acquisition exists.

When Visual Polar Tracking is disabled, do not draw its guide ray, arc or θₚ label. PolarSnap must not cause the Visual Polar Tracking ray to appear. Temporary guide geometry must never enter the document entity list, extents, undo history, DXF, PDF or SVG output.

### 8.7 PrecisionCAD readout identity

Use clear fixed labels and a compact technical presentation:

- `R` — Radial Distance;
- `θ` — Cursor Bearing;
- `θₚ` — Track Bearing.

Keep the reference basis explicit (`+X` or `Last Segment`). Keep the visual and snap settings clearly separated in the configuration panel. Numeric values use invariant engineering formatting. All readout values are read-only; coordinate editing remains in the Direct Coordinate input/command line.

### 8.8 Acceptance tests

- At raw angle 135.1°, the 135° visual guide may be acquired within tolerance.
- At a raw angle materially away from 135° (for example 127°), a stale 135° blue guide must disappear.
- A PolarSnap preset at 126° must not force Visual Polar Tracking to display a 126° guide when visual bearing is not aligned.
- With visual tracking disabled, no visual ray or arc appears.
- With PolarSnap disabled, the candidate remains at the raw/grid/OSNAP result and is not projected by PolarSnap.
- Custom fractional visual angle 22.5° can acquire visually without being inserted into PolarSnap.
- Custom PolarSnap angle 126° can constrain the candidate only when PolarSnap is enabled and 126° is in the snap collection.
- Relative-to-last-segment uses the prior segment as reference and labels it accordingly.
- Temporary guide geometry never enters saved/exported geometry.

---

# PART C — DIALOGS, CARD SYSTEM AND INTERACTION

## 9. Shared card component and global card-motion system

### 9.1 Design contract

Use one reusable card-stack visual component with per-instance interaction policies. The foreground card is the true, stable hit target. The rear card is visual context only; it must not intercept taps while concealed or peeking.

At rest:

- rear card is completely concealed behind the foreground card;
- the foreground card is centred and visually dominant;
- no visible side/top sliver remains indefinitely.

On an explicit card change:

1. incoming card emerges from behind the outgoing foreground card;
2. outgoing card moves behind it;
3. card positions/scale/elevation/opacity animate as one coherent transition;
4. the new rear card may remain slightly visible for approximately 2–3 seconds;
5. it then smoothly retracts and becomes completely concealed behind the foreground card.

Do not abruptly set rear-card alpha to zero. The rear card must move/scale/clip behind the front card through an animated transition.

### 9.2 Shape-aware reveal direction

Each deck declares its peek orientation; do not infer direction from incidental measured pixels if a clear deck type exists.

- Square or compact cards: reveal a small rear-card edge from the side.
- Wide rectangular or pill-shaped cards: reveal from above the foreground card.
- Touchpad/Joystick mode selector: rear card peeks from above.
- Other specific decks may declare their orientation explicitly while preserving the user's previously approved orientation.

The visual reveal must not reflow parent layout or change component size.

### 9.3 Colours and labels

- Positive action cards such as ENABLE, DOWNLOAD, UPDATE, INSTALL and SEND use the established green family where applicable.
- Binary toggle **ENABLE** state is green.
- Binary toggle **DISABLE** state is neutral grey, not red.
- Destructive actions such as CANCEL and CLEAR ALL use the established red family where those cards represent a destructive/negative action.
- Touchpad mode card uses the established blue accent.
- Joystick mode card uses the established engineering-gold accent.

Card labels are concise and direct. Do not append a description to Download/Install/Cancel actions. Do not replace `ENABLE`/`DISABLE` with abbreviations such as `POLAR`, `PSNAP`, `ON` or `OFF` as the visible state label. Technical identifiers may remain as separate fixed labels where requested (for example OSNAP `[END]`).

### 9.4 Per-deck interaction policies

The shared component must accept distinct policies:

| Deck | Allowed interaction |
|---|---|
| Command Send/Cancel | Preserve established tap and downward-swipe exchange behaviour. Rear card never receives taps. |
| QDIM Enable & Generate / Clear All | Tap foreground action; downward swipe exchanges the two cards; long-press Clear All opens selective removal. |
| QDIM multi-option selectors | Horizontal forward/backward swipe changes one option. Vertical drag remains for parent scrolling. |
| Touchpad/Joystick | Single tap exchanges the mode cards; no swipe. Rear card emerges from above. |
| Binary enable/disable cards | Single tap toggles state; no swipe. |
| OSNAP individual cards | Single tap toggles that mode; no swipe. |
| Release Download/Cancel and Install/Cancel | Single tap only; no swipe. |

The rear card is not an independent touch target. Lock the deck during exchange animation so taps/swipes cannot double-execute or trigger the outgoing card.

### 9.5 Section-local inactivity timers — strict isolation

Every card deck owns its own inactivity timer, timestamp, animation state and eligibility. There must be no application-wide timer and no global interaction listener that resets every deck.

**An event outside a specific deck does not reset that deck's timer.** Examples:

- typing in the command text field does not reset the Send/Cancel deck timer;
- opening/focusing the command input does not reset it;
- zooming/panning the canvas does not reset it;
- selecting a toolbar command does not reset another deck's timer;
- scrolling the QDIM body does not reset a selector deck timer unless the pointer actually interacts with that deck surface;
- changing an OSNAP mode does not reset the QDIM deck timer;
- touching the Touchpad surface does not reset an unrelated card deck timer.

A direct tap/long press/swipe on the deck's own interactive surface resets that deck's timer. Only gestures supported by that deck count as its direct interaction.

### 9.6 Visibility/activation eligibility — no hidden background calculations

A deck's timer and idle animation are active only when all are true:

- parent screen/dialog/section is open;
- deck is visible in the composed viewport;
- deck is enabled and interactive;
- containing window/activity is in a state where its UI is visible;
- no blocking transition is currently running.

When any condition becomes false, cancel its pending timer and pending idle animation. Do not keep a hidden QDIM timer running while QDIM is closed. Do not keep OSNAP card timers running while the configuration dialog is closed. Do not run a card timer for a hidden/disabled deck.

When the deck becomes eligible again, start a fresh inactivity interval. Do not count time spent hidden or suspended.

### 9.7 Idle-peek timing

Use a default per-deck idle interval of **75 seconds** after its last direct interaction or after activation. This sits within the requested roughly one-to-one-and-a-half-minute period.

When the interval expires:

1. reveal a subtle portion of the rear card from the configured direction;
2. hold the preview briefly (approximately 0.8–1.2 seconds);
3. smoothly retract it fully behind the foreground card;
4. restart that deck's own 75-second idle interval if still eligible.

This is a visual hint only. It must not change selected card, toggle value, command text, scroll position, focus, document state or execute an action. It must not trigger haptics or sound unless separately requested (not requested here).

The transition after a user-initiated card exchange uses the separate short preview period (approximately 2–3 seconds) before concealment. Idle peeks use the 75-second inactivity period. Do not confuse these two timers.

Pause timers during an active gesture, card transition, parent scrolling that obscures the card, window not-resumed state or reduced-motion accessibility mode. In reduced-motion mode, omit periodic peeks or use a minimal non-spatial indication; never force motion against the platform setting.

### 9.8 Command Send/Cancel activation exception

The command card deck exists visually only when:

\[
\text{CommandDeckVisible} = \text{KeyboardVisible} \lor \text{CommandTextNotEmpty}
\]

The command card inactivity timer is eligible only while `CommandDeckVisible` is true and the deck is actually interactive.

| Keyboard | Command text | Deck | Timer |
|---|---|---|---|
| Open | Empty | Visible | Active |
| Open | Nonempty | Visible | Active |
| Closed | Nonempty | Visible | Active |
| Closed | Empty | Hidden / idle | Inactive; no timer work |

Typing or keyboard visibility alone does not count as direct interaction with the Send/Cancel cards. The deck timer resets only when the card deck itself is tapped or its supported gesture is performed.

When keyboard closes and command text is empty, hide the card compartment and cancel its timer/animation immediately. When keyboard closes but text remains, keep cards visible and keep their timer eligible. Do not discard command text merely because the keyboard closes.

### 9.9 Acceptance tests

Test two simultaneously visible decks. Interacting with Deck A must not reset Deck B's timer. Hide a dialog and verify its timer cancels. Reopen it and verify a fresh interval. Verify command text/keyboard states from Section 9.8. Verify idle peeks never execute actions and never cause parent layout remeasurement.

---

## 10. QDIM dialog and card-based option selectors

### 10.1 Existing issue

**Source:** `CadDialogsAndPanels.kt`.

The current `QDimRotaryWheelSelector` uses vertical drag recognition while the entire dialog body also uses vertical scrolling. The same gesture can alter a selector or scroll the parent, producing mis-touches. Delayed help callouts are inserted into normal layout flow and can shift content, creating large gaps and remeasurement.

### 10.2 Dialog structure

Retain the QDIM dialog as one bounded surface with:

- a header that transitions from its in-content position into a pinned header as scrolling reaches its threshold;
- one continuous central vertical scroll region;
- a footer that begins in content flow and transitions into a pinned bottom footer at its threshold;
- enough reserved bottom space that pinned footer never obscures the final content;
- stable dialog width/height and no nested opaque black rectangle that visually interrupts the scroll area;
- no large unexplained vertical gaps.

The header/footer must each be one logical element whose position changes through a matched animation. Do not compose duplicate fixed and scrolling copies that can visually overlap.

### 10.3 Three multi-option card selectors

Replace the current vertical rotary/wheel selector UI with three card-based selectors:

1. Operating Scope Mode.
2. Alignment & Arrangement Mode.
3. Dimension Density Policy.

Retain all existing enum values, defaults and engine semantics. Do not rename or remove modes just because the presentation changes.

For a section with more than two options:

- current option appears as the centred foreground card;
- adjacent previous/next card is concealed behind it at rest;
- swipe left moves forward one option;
- swipe right moves backward one option;
- one recognised gesture changes exactly one option;
- a tap on the foreground selection follows the existing selection contract (if tap opens a chooser, preserve this; otherwise choose the active option and do not invent a new action);
- the card-exchange animation reveals the incoming/outgoing cards using the shared card system;
- lock interaction while transition runs;
- no vertical swipe changes a selector.

Vertical movement over the selector belongs to the parent dialog scroll. Use gesture arbitration/slop so horizontal card navigation and vertical page scrolling are not both dispatched for the same gesture.

### 10.4 Remove layout-shifting descriptions

The selected mode's explanatory description is hidden by default. After the user rests on one option for the existing approved delay (approximately two seconds), show a small callout/speech bubble anchored to that card.

The callout must be an overlay, not a new element in the vertical layout. It must not increase selector or dialog height, push later sections, or change the scroll offset. Hide it when the selected option changes, the selector moves, the page scrolls, or the dialog closes. Allow the complete description to be read; if lengthy, size the callout within available window bounds and scroll internally if required.

### 10.5 Stable spacing and scrolling

- The main dialog scroll state is hoisted and remains stable for the dialog lifetime.
- Do not create a new `rememberScrollState()` on each selection.
- Selector card height and width are stable for each layout class.
- No `weight(fill=false)` or unconstrained spacer may generate unexplained empty vertical regions; use measured available height and explicit section spacing.
- Help overlays do not participate in layout measurement.
- Changing an option updates state but must not move the parent scroll position.
- Header and footer transitions do not resize the middle content unexpectedly.

### 10.6 QDIM footer action deck

Retain the two square/subtly chamfered action cards:

- green **ENABLE & GENERATE**;
- red **CLEAR ALL**.

The `Dimensions` suffix is omitted from the primary card label. No long description is placed inside either card.

Interactions:

- tap foreground Enable & Generate: enable QDIM and generate/regenerate according to existing QDIM state contract;
- downward swipe on the deck: exchange foreground and rear card;
- tap foreground Clear All: open destructive confirmation for the existing all-dimension operation;
- long press Clear All: open the category-first selective dimension-removal window;
- swipe alone never executes a destructive action;
- no duplicate independent QDIM enabled state is introduced.

Retain QDIM's single authoritative ViewModel enabled state and idempotent regeneration semantics. Do not append duplicate generated dimensions. Manual dimensions must be preserved by ordinary regeneration. Clear All/selective removal retains the prior V17 undo transaction rules.

### 10.7 Refresh icon

The large textual Refresh row/button is not restored. If a refresh/regeneration affordance remains necessary, show a compact icon-only control in the dialog header. It rotates while an actual refresh/regeneration operation is running and returns to rest on completion/failure. It must not occupy a separate bordered card or create another enabled-state switch.

### 10.8 QDIM acceptance tests

- Vertical drag scrolls the dialog and never changes selector values.
- Horizontal left/right gesture changes one selector option only.
- No unexplained empty region remains between sections.
- Help callout appears after delay without changing page height/scroll position.
- Header and footer smoothly dock and undock without duplicates.
- Enable & Generate is idempotent and does not duplicate generated dimensions.
- Downward swipe changes active footer card without execution.
- Long press Clear All opens selective removal; ordinary tap opens the established confirmation.

---

## 11. Selective Dimension Removal: stable list and transient master-selection notice

### 11.1 Preserve V17 selection semantics

The V17 selection hierarchy remains authoritative:

- all dimension categories are selected by default;
- a checked category selects all its children and locks individual child toggles;
- unchecking a category unlocks its individual dimensions;
- individual dimensions may then be selected/deselected;
- category supports checked, unchecked and indeterminate states;
- final confirmation reports the exact selected entity count;
- only selected dimension entities are removed;
- drawing geometry, ordinary text, leaders, non-dimension annotations and non-selected dimensions are preserved;
- operation is one undoable transaction.

### 11.2 Master Selection is not a permanent box

Remove the permanent master-selection count/action row from the list layout. Replace the count display with a transient notification matching the main workspace notification motion language.

The notification must:

- appear only after an actual selection change;
- display selected count (for example, `5 of 7 selected` or `All 7 selected`);
- contain no Select All/Deselect All button;
- remain a fixed, single-line-height notification area or a floating overlay that does not alter list measurement;
- use a marquee for longer text;
- complete one entire readable marquee cycle before dismissal;
- fade/retract with controlled motion, never vanish abruptly;
- use a stable width and height regardless of whether the selection is partial or complete.

The notification is not a modal and must not block user interaction with the list.

### 11.3 Select All / Deselect All placement

Place Select All/Deselect All as a separate, clearly accessible action in a stable location within the selection dialog header or immediately below it, outside the transient count notification. Its label must remain fully visible and must not wrap into a tall box. It must not change the scrollable list height when toggled.

### 11.4 Scroll stability

Current selection changes must not jump the list up or down. Keep stable category/dimension keys and a retained list scroll state. Ensure category header and child row measured heights do not change when enabled/locked/unlocked status text changes. Use fixed or bounded row dimensions, stable alignment and overlays for transient notices.

On category or item state changes, preserve:

- first visible list item key;
- its pixel offset from the viewport top;
- expanded/collapsed category state;
- the user's current scroll position.

Do not reset to top when master selection changes. If row content changes in a way that requires remeasurement, restore the visible key and offset after the update.

### 11.5 Acceptance tests

- Toggle individual dimension; scroll offset remains unchanged.
- Unlock/relock category; list position remains unchanged.
- Select All/Deselect All; notification area does not grow/shrink the dialog.
- Long count label completes one marquee cycle before retraction.
- The count is exact and changes immediately.
- Remove selected dimensions and verify undo restores exact selected entities.

---

## 12. Cursor Control, Grid and OSNAP configuration

### 12.1 Binary controls use shared cards

Every binary toggle in the Cursor Control, Grid & OSNAP configuration window uses the shared card visual design:

- foreground **ENABLE** card is green;
- foreground **DISABLE** card is neutral grey;
- tap exchanges the two card states with the established animation;
- no swipe gesture is available;
- no conventional Android switch remains for these binary settings;
- state is owned by the existing configuration/ViewModel and persisted through the existing preferences/document config as appropriate.

This includes Visual Polar Tracking, relative-to-last-segment, PolarSnap projection and every other binary option in this configuration window. Do not convert sliders, numeric inputs, multi-select presets or checkboxes into binary cards.

It is also the application-wide binary-toggle rule for other equivalent binary enable/disable settings. The specific V18 card state label is `ENABLE` or `DISABLE`; do not show `POLAR`, `PSNAP`, `REL SEG`, `ON` or `OFF` as the state label. Technical setting identity may remain as separate text.

### 12.2 OSNAP cards

Keep every existing OSNAP mode, mode selection meaning, fixed row dimensions and stable key. Replace the single combined label that currently gets ellipsised:

```kotlin
label = "[${mode.badgeCode}] ${mode.label}"
```

with a three-region card layout:

1. fixed short identifier on the left, e.g. `[END]`;
2. full descriptive name in a dedicated middle text region;
3. fixed ENABLE/DISABLE state on the right.

The identifier and status never move. The descriptive name uses a horizontal marquee when it does not fit, with an initial pause, a readable movement cycle and a pause before repeating. Do not use ellipsis as the final label presentation. Expose the full label through accessibility semantics/content description.

The card keeps fixed measured height/width. Marquee animation cannot increase row height or reposition sibling cards.

### 12.3 OSNAP scrolling stability

The current source already hoists `rememberScrollState()`, keys OSNAP cards by mode name and uses 54 dp fixed-height rows. Do not simply add another scroll state and assume that fixes the reported movement.

Verify the actual cause through measurement and recomposition tracing. Ensure:

- dialog body has one bounded vertical scroll viewport;
- header/footer remain outside that scrolling body;
- OSNAP rows remain fixed height;
- card status/state animation changes only internal visuals;
- content above OSNAP has stable measured heights;
- selection changes never recreate the scroll state;
- first visible item key and scroll offset are retained through configuration updates.

Add a UI test that scrolls to OSNAP and toggles each mode individually. The scroll viewport must not jump.

### 12.4 Polar controls remain separated

Present visual whole-degree guide settings and PolarSnap preset controls in distinct subsections with clear labels. Visual custom fractional angles are not PolarSnap custom angles. PolarSnap permits multiple independent preset selections. Do not make Visual Tracking enable/disable automatically change the PolarSnap preset collection, except for the already established ORTHO mutual-exclusion rule where applicable.

### 12.5 Acceptance tests

- Every applicable binary setting uses the reusable card and correct ENABLE/DISABLE label/colour.
- No unsupported swipe toggles a setting.
- `[END]` remains fixed while `Endpoint` scrolls horizontally.
- OSNAP status label remains fixed and legible.
- Scroll position remains stable after toggling every OSNAP mode.
- Reopening configuration restores the actual saved state.

---

## 13. Command dock, keyboard dismissal and controller motion

### 13.1 Floating command/function dock

**Source:** `CadWorkspaceScreen.kt`.

The current `ContextualCommandAndOptionsDock` is wrapped by an outer rectangular `Surface` with a full-width border and opaque panel fill, unlike the distinct rounded controller dock.

Required appearance:

- remove the dedicated full-width rectangular enclosing border;
- remove the opaque outer parent fill;
- allow the drawing canvas to remain visible behind the surrounding command/function region;
- retain the individual function-toolbar control surfaces and individual command input surface;
- make the command input background translucent/dark enough for legible text;
- preserve focus indication, cursor, placeholder, action cards and tap target sizes;
- do not change the controller dock's rounded border/surface styling;
- do not let transparent overlays intercept canvas gestures outside their actual controls.

The Overview prompt/card retains its own independent visual rules. This requirement removes the large enclosing command-dock rectangle, not all individual control borders.

### 13.2 Final command deck visibility

Use the already established V17 rule:

`CommandDeckVisible = KeyboardVisible OR CommandTextNotEmpty`.

- keyboard open + empty text: Send/Cancel visible;
- keyboard open + text: visible;
- keyboard closed + text: visible;
- keyboard closed + empty text: hidden and command bar returns completely to idle.

Closing the keyboard does not clear text. Typing is not direct interaction with the Send/Cancel card deck and does not reset that deck's local idle-peek timer. Only a direct interaction with the Send/Cancel deck resets its timer.

An active CAD command alone must not force the command-entry Send/Cancel compartment to remain visible when the keyboard is closed and command input is empty. Preserve the active command's independent completion/cancellation controls in their proper command context.

### 13.3 IME positioning — one source of truth

The current `CadWorkspaceScreen.kt` has a manually calculated bottom offset and then applies `.imePadding()` to the same upper command region. The controller dock is already intended to remain stationary/no-IME-padding. Multiple inset calculations and measured overlay heights can create keyboard dismissal jitter.

Refactor to exactly one authoritative IME positioning strategy for the upper command region. Do not subtract the keyboard inset manually and also apply the same inset via `.imePadding()`.

Required layout:

- lower controller dock remains anchored to the normal bottom position and receives no IME inset;
- upper Overview/function toolbar/command input region moves together above the keyboard;
- controller must not be lifted or resized by keyboard appearance;
- when keyboard disappears, upper region returns smoothly to its normal position above the controller;
- same positioning logic works portrait/landscape and with the right-side controller/inspector variant where applicable.

Choose one Compose WindowInsets or manual-inset approach after tracing actual edge-to-edge/`adjustResize` delivery on the target SDK. Do not mix mechanisms. The keyboard's reported bottom inset must be consumed once only.

### 13.4 Keep viewport camera and safe-area calculation independent

The canvas viewport usable area must not be derived from a measurement that includes animated keyboard padding. Keyboard appearance/dismissal must not change `pixelsPerModelUnit`, pan, camera centre, model cursor coordinates, model bounds or drawing geometry.

If the canvas safe region must reserve overlay space, calculate from the actual non-IME overlay dimensions and stable dock height, not from a command-region measurement that includes IME padding. Animate overlay translation without repeatedly refitting/recentering the drawing.

### 13.5 Controller collapse/expand animation

**Source:** `VirtualCursorController.kt`.

The current source removes the expanded content using an immediate `if (!config.isCollapsed)` conditional. This creates an instantaneous collapse/expand.

Replace with an animated transition around the expanded controller body:

- vertical expand/collapse;
- restrained fade;
- smooth panel-height/upper-corner boundary change;
- coordinated movement of step controls, touchpad/joystick surface, Pick Point and confirm/cancel controls;
- chevron rotates or changes smoothly;
- clipped content never escapes panel boundaries;
- bottom edge remains anchored in the same position;
- the controller's logical expanded/collapsed state remains the existing ViewModel/config state;
- swipe handle and chevron produce the same animation;
- block interaction with controls while their content is entering/exiting;
- no canvas transform/coordinate changes.

Use Compose animation primitives such as `AnimatedVisibility` with vertical expand/shrink and fade, and `animateContentSize()` only where it does not cause duplicate measurement. Do not use an abrupt conditional removal as the transition mechanism.

### 13.6 Acceptance tests

- command dock has no outer rectangle/opaque slab;
- canvas is visible around translucent floating controls;
- keyboard open/dismiss with Back and keyboard hide control produces no jitter;
- controller does not rise when keyboard opens;
- command deck states match Section 13.2;
- controller collapse/expand is smooth and bottom-anchored;
- no viewport camera/cursor/model changes during any animation;
- repeat in portrait, landscape and large-screen configuration.

---

# PART D — GITHUB RELEASE CATALOGUE AND IN-APP UPDATER

## 14. Purpose and distribution model

Implement a GitHub-backed updater for the public production Release APK. The application must:

- detect available updates from GitHub Releases;
- display a Home-screen update icon;
- show how many eligible stable releases the installed build is behind;
- display a chronological release/changelog catalogue;
- download the newest eligible stable Release APK inside PrecisionCAD without opening a browser;
- verify the APK before install;
- invoke Android's standard package-installation confirmation directly;
- retain a successfully downloaded and verified APK if the user cancels installation;
- preserve the existing app and user drawings if any download/verification/install step fails.

Do not route users to a GitHub page or browser for downloading. GitHub is the backend source, not a visible catalogue button.

### 14.1 Public repository configuration

The release repository owner/name must be configured explicitly. Inspect the checked-out repository's remote and current workflow configuration. If no public distribution repository is identified, add a clear build-time configuration for owner/name and mark it as a required deployment value; do not invent a repository. If source repository is private, the updater must point to a separate public distribution repository for anonymous access.

Do not embed a personal GitHub token or any private credential in the APK. Public Release endpoints/assets must be accessible anonymously. Keep the configured repository owner/name centralised in one source (for example a typed `UpdateRepositoryConfig`/BuildConfig value) rather than duplicating literals across UI, repository and downloader.

### 14.2 GitHub API data source

Use GitHub REST Releases API:

- List releases for complete history and update count: `GET /repos/{owner}/{repo}/releases?per_page=100&page=N`.
- The latest release endpoint may be used as a fast latest-stable check, but the full history list is required for the catalogue and skipped-release count.

Follow pagination until the required history is retrieved. Prefer `per_page=100`. Cache results and use conditional requests/ETags if available to minimise rate-limit usage. Use public unauthenticated API access. Handle rate-limit response, network errors and pagination correctly.

Use `published_at` for the release publication date displayed in the catalogue. Do not use `created_at` as the publication date; GitHub documents it as the date of the commit used for the release.

Each release record should retain at least:

- release ID;
- tag name;
- release name/title;
- release body/changelog;
- `draft` and `prerelease` flags;
- `published_at` timestamp;
- target commit SHA/target commitish if present;
- asset list, exact APK name, asset ID/URL, content type, size and GitHub digest;
- parsed application version name, version code and package ID from the structured release metadata.

Reject drafts from public catalogue/update selection. Include published prereleases in the history catalogue with a `Pre-release` status, but do not count or automatically install them in the ordinary stable channel.

### 14.3 Stable release metadata format

The GitHub Release body must contain human-readable changelog sections plus machine-readable build metadata. Use predictable headings:

```markdown
## New
- ...

## Fixes
- ...

## Optimisations
- ...

## Security
- ...

## Other
- ...

---

<!-- PRECISIONCAD-RELEASE-METADATA
packageId: com.precisioncad.studio
versionName: <actual versionName>
versionCode: <actual versionCode>
assetName: <exact release APK filename>
commitSha: <full commit SHA>
sha256: <actual APK SHA-256>
channel: stable
-->
```

Empty changelog groups are omitted. Do not invent changelog items. Generate from commit/release notes supplied by CI or author. The values must be extracted from the actual assembled APK/build metadata, not typed independently by hand. Version code must be numeric, monotonically increasing and strictly greater than the currently installed/previous stable release.

Use GitHub Release asset `digest` (`sha256:<hex>`) when present and also include the same SHA-256 in metadata/release description for compatibility and audit. The updater must verify asset digest before installation.

### 14.4 Version comparison and update count

Use the APK's numeric Android `versionCode` as the authoritative ordering. Do not compare `versionName` lexicographically. Parse versionName for display only.

Define eligible stable update releases as unique published, non-draft, non-prerelease releases that:

- contain exactly one valid production Release APK asset;
- identify package ID `com.precisioncad.studio`;
- supply a valid numeric version code greater than the installed app's version code;
- pass the configured release channel policy.

Deduplicate duplicate tags/assets/version codes. An invalid or debug-only release does not count.

`updatesBehindCount` equals the number of eligible stable release versions whose versionCode is greater than the installed versionCode. If the latest stable release is the same as installed, count is zero.

Installation always targets the single highest eligible stable versionCode, never an intermediate release. The count is informational/history depth, not a sequence of installs.

### 14.5 Home-screen update icon

Place the update/download icon at the top-right of the Home screen, separate from New Drawing/Open Drawing/Saved Drawings.

Visual rules:

| State | Appearance |
|---|---|
| No eligible update | White icon; no number or dot. |
| Exactly one eligible stable update | Icon changes to application gold/yellow accent; no dot and no number. |
| More than one eligible stable update | Gold/yellow icon with the integer count immediately beside the icon. |
| Check in progress | Subtle loading/rotation state without a false update count. |
| Check failure with cached update data | Preserve the last known badge, show an unobtrusive offline/stale state when opened. |
| Check failure without cached data | Return to neutral visual state and show a retryable error. |

No notification dot. The icon itself changes colour. For multiple releases, the number appears beside the icon (not overlaid on the icon and not above it).

Tapping the icon when no badge is present starts a manual check. A manual check must not be blocked by the periodic automatic-check throttle.

### 14.6 Automatic and manual check cadence

On entering Home, check the release repository if the last successful check is older than a configurable interval; use six hours as the default interval. Do not contact GitHub on every recomposition or every Home state change. Check on a deliberate manual tap regardless of the automatic throttle.

Use a single repository layer to prevent overlapping identical requests. Cancel duplicate in-flight requests or share the same result. Cache the last successful release metadata and timestamp locally. Offline Home remains usable.

A check must not block New Drawing, Open Drawing, Saved Drawings or the CAD workspace.

---

## 15. Release catalogue interface

### 15.1 Visual reference and structure

Use the user's supplied changelog screenshots (Morphp-style vertical release timeline) as the visual reference. The catalogue should be a vertically scrollable, dark technical page/dialog with:

**Header:**
- actual PrecisionCAD Studio adaptive app icon;
- title `View changelogs`;
- subtitle `PrecisionCAD Studio · v<latestVersionName>`;
- latest release identity.

**Timeline:**
- one vertical timeline rail with release markers;
- newest release at the top, older releases below;
- each release header shows version, status badge, exact publication date, relative age if useful, counts of New/Features, Fixes, Optimisations and other categories;
- expand/collapse chevron;
- independent collapsible category cards within the release;
- category header contains icon, category name and item count;
- category body shows individual changelog bullet rows with separators;
- long text wraps naturally; no truncated changelog content.

Use category identity and restrained colours:

- New/Features: blue;
- Fixes: engineering gold;
- Optimisations: green;
- Security: restrained amber/red;
- Other: neutral.

Do not fabricate release entries, dates, counts or changelog descriptions. Parse them from published release metadata.

### 15.2 Release status labels

Every release entry may carry one or more clear statuses:

- `INSTALLED` on the release whose numeric versionCode matches the currently installed package;
- `LATEST` on the highest eligible stable release;
- `PRE-RELEASE` on a published prerelease;
- optional `PREVIOUS RELEASE`/no badge for earlier stable entries.

If installed version is latest, the same release may show both Installed and Latest. If the installed build cannot be found in the queried release history, show a separate installed-version summary at the header and do not falsely mark a release entry Installed.

### 15.3 Catalogue content and ordering

- Show the complete published release history available through the public Releases API, paginating as needed.
- Show every intermediate stable release between the installed build and latest stable release.
- Show published prereleases as clearly labelled history entries, but exclude them from the default stable update count/install selection.
- Sort by actual published timestamp descending; where timestamps are identical, use versionCode and stable ordering as deterministic tie-breakers.
- Use exact `published_at` date and optionally a relative age label.
- Installed and latest releases must be unmistakable.
- The catalogue may initially expand the installed and latest entries; older releases start collapsed to avoid a very long first view.
- User may expand/collapse each release and category independently.
- Cache the catalogue data and render cached results while a refresh is in progress; update with new data after success.

### 15.4 Fixed bottom action cards — no GitHub button

There is **no GitHub button** in the catalogue. GitHub is only the release data/download source.

The fixed bottom footer contains exactly two cards:

Before download:
- green `DOWNLOAD`;
- red `CANCEL`.

After successful download and verification:
- green `INSTALL`;
- red `CANCEL`.

Labels are exactly `DOWNLOAD`, `CANCEL`, `INSTALL`, `CANCEL`. Do not append descriptions, subtitles or explanatory sentences inside the cards. Do not include a visible GitHub action.

The action footer remains anchored while the timeline scrolls and reserves enough bottom content padding that the last release remains reachable. The cards use the shared shape-aware card animation and section-local inactivity policy. These update/catalogue actions are tap-only; no swipe gesture.

### 15.5 Catalogue acceptance tests

- Header displays actual icon and newest available stable version.
- Installed version and latest stable version are distinguishable.
- Dates match GitHub `published_at` values.
- Counts match actual parsed changelog entries.
- Published prerelease is visibly labelled but not counted as stable update.
- All intervening releases are visible in chronological order.
- No GitHub button is present.
- Before download footer is DOWNLOAD/CANCEL; after verification it becomes INSTALL/CANCEL.
- Fixed footer does not obscure the final release card.

---

## 16. In-app APK download, verification and Android installation

### 16.1 Download behaviour

When the user taps DOWNLOAD:

- download the highest eligible stable release APK directly inside PrecisionCAD;
- do not open a browser, GitHub webpage, external download manager UI or unrelated application;
- stream the HTTPS response into a temporary file under app-private internal storage (for example `filesDir/updates/`);
- expose actual byte progress using `Content-Length` when available, otherwise show indeterminate progress and downloaded byte count;
- support cancellation using the visible CANCEL action while downloading;
- apply connection/read timeouts and handle redirects securely;
- follow only HTTPS redirects to expected GitHub asset/content delivery hosts; reject downgrade to HTTP or unknown hosts;
- verify the result before promoting `.part`/temporary file to the verified APK path.

Do not add a third-party networking library solely for this task if `HttpsURLConnection`/existing dependencies and coroutines are sufficient. If an HTTP library is introduced, justify it and keep versions in the version catalog.

### 16.2 APK validation before install

Before the INSTALL card becomes available, validate all of the following:

1. Download completed without truncation.
2. SHA-256 exactly matches the GitHub release asset digest or the trusted release checksum metadata.
3. APK parses successfully.
4. APK package ID is exactly `com.precisioncad.studio`.
5. APK versionCode is strictly greater than currently installed versionCode.
6. APK versionName matches the release metadata.
7. APK is the production Release APK, not Debug.
8. APK signer matches the currently installed application's trusted release signer or a valid Android signing-certificate rotation lineage accepted by PackageManager.
9. Release asset name/content type corresponds to the one expected production APK.
10. No unexpected split-APK set or multiple APK asset is required; this updater's contract is one universal APK output.

Use Android package parsing/signing APIs (for example `PackageManager.getPackageArchiveInfo` with signing certificate flags and `SigningInfo`) and compare signer identity safely. Do not rely only on filename, SHA-256 or `versionName`. A matching checksum does not prove that the APK was signed by the trusted publisher.

Reject invalid artifacts and keep the existing installed application untouched. Never install a mismatched package or signer. Do not silently fall back to a Debug APK.

### 16.3 Installer action labels

Before the APK has been downloaded and verified, footer actions are DOWNLOAD and CANCEL.

After successful download and verification, change the primary card to INSTALL; retain CANCEL. Do not leave UPDATE as a label. Do not show a custom confirmation dialog before opening Android's installer. The card contains only its label.

When user cancels the catalogue after the download is complete, retain the verified APK in private staging storage so the user can open the catalogue later and tap INSTALL without downloading again. Remove stale/incomplete `.part` files. Retain the verified APK until installed, replaced by a newer verified APK, or explicitly cleared by the user/system cleanup policy.

### 16.4 Permission to install unknown apps

The application is distributed directly through GitHub APK releases. Add `android.permission.REQUEST_INSTALL_PACKAGES` to the manifest if the direct-sideload distribution policy permits it. Do not request `MANAGE_EXTERNAL_STORAGE`.

Before starting the install session, call `PackageManager.canRequestPackageInstalls()` on API 26+. If false:

1. Keep the update catalogue and downloaded verified APK state intact.
2. When user taps INSTALL, open `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` for this app's package.
3. Explain outside the card or through a concise one-time permission rationale that Android requires the user to allow PrecisionCAD as an install source.
4. On return/resume, re-check `canRequestPackageInstalls()`.
5. If granted, retain the INSTALL/CANCEL footer. Do not silently install on return; user taps INSTALL again to explicitly invoke the system installer.
6. If denied or user returns without enabling, keep the APK and show an actionable status; do not repeatedly reopen settings automatically.

The updater must not request unrestricted file-system permission. This updater is a direct GitHub distribution feature. If Google Play distribution is later added, reassess `REQUEST_INSTALL_PACKAGES` against Play policy; do not assume Google Play permits self-update installation from outside the store.

### 16.5 Android PackageInstaller — explicit system confirmation

The requested product behaviour is to open Android's standard confirmation window after the user taps INSTALL. Implement this intentionally. Do not try to bypass the system approval.

Use `PackageInstaller` session APIs:

- create a full-install session for the validated APK;
- write the APK bytes to the session;
- fsync/close correctly;
- commit using a correctly configured explicit PendingIntent/receiver;
- handle `STATUS_PENDING_USER_ACTION` by launching the supplied system confirmation intent;
- handle `STATUS_SUCCESS`, `STATUS_FAILURE`, `STATUS_FAILURE_ABORTED`, storage/version/signature failures and all other relevant statuses;
- persist enough session/download state to recover after activity recreation or process death.

Set/handle user-action requirements so the system's user confirmation remains required for this consumer updater. Do not request silent installation or attempt a device-owner privilege path. `PackageInstaller.SessionParams` provides `USER_ACTION_REQUIRED`; use it to match the requested explicit confirmation flow and always be prepared to receive `STATUS_PENDING_USER_ACTION`.

The Android system may choose the exact copy, layout and controls of its install confirmation screen. PrecisionCAD cannot remove or redesign system-owned controls. It must launch the system-provided confirmation UI and wait for its result.

### 16.6 Installer flow after permission

Expected sequence:

1. User taps INSTALL in PrecisionCAD.
2. Permission check passes.
3. App creates/commits PackageInstaller session.
4. System installation confirmation UI appears.
5. User approves installation.
6. System installs/replaces package.
7. App observes installer result or next process start and refreshes installed version.
8. Verified APK staging is cleaned after successful install.
9. Update icon becomes white/no count if now latest; otherwise count is recalculated.

If the user cancels the system confirmation, the installed app remains unchanged and verified APK remains staged. Reopening catalogue shows INSTALL/CANCEL.

### 16.7 Failure handling

Handle explicitly:

- no internet;
- GitHub API rate limit;
- release repository not public/not found;
- no eligible stable release;
- no matching production APK asset;
- multiple ambiguous APK assets;
- incomplete download;
- HTTP error/redirect host mismatch;
- SHA-256 mismatch;
- invalid/corrupt APK;
- wrong package ID;
- wrong or non-increasing version code;
- signer/certificate mismatch;
- insufficient internal storage;
- missing install-source permission;
- user returns from settings without granting permission;
- system installer cancelled;
- system installation failure;
- process death during download/session;
- update already installed from another source before user taps INSTALL.

No error path may delete or corrupt existing projects, Room data, private `.pcad` files or user preferences. The updater must not overwrite the currently installed APK or any project files.

### 16.8 Update state model

Use one explicit updater state model, for example:

- `Idle`
- `Checking`
- `UpToDate`
- `UpdateAvailable(releasesBehind, latestRelease, cachedHistory)`
- `CatalogLoading`
- `Downloading(release, bytesDownloaded, totalBytes?)`
- `DownloadFailed(reason, retryAvailable)`
- `DownloadedVerified(release, privateApkPath)`
- `InstallSourcePermissionRequired(release)`
- `InstallerLaunching(release, sessionId)`
- `AwaitingSystemConfirmation(release, sessionId)`
- `InstallSucceeded(versionCode)`
- `InstallCancelled(release)`
- `InstallFailed(reason)`

Do not encode this as unrelated Booleans that can contradict each other. Persist the release identity, verified file path, checksum and installer session state where required.

### 16.9 Release-count badge exact rule

The Home update icon follows these final rules:

- zero eligible stable updates: white download/update icon, no count;
- exactly one eligible stable update: gold/yellow icon, no dot and no number;
- more than one eligible stable update: gold/yellow icon plus the integer count immediately beside it;
- count is the number of unique eligible stable releases with versionCode greater than installed versionCode;
- prereleases, drafts, invalid assets, duplicate version codes and Debug builds are excluded;
- all updates are installed by downloading only the latest stable version, not by installing each skipped release sequentially.

### 16.10 Acceptance tests

- Manual check works without a badge.
- Automatic Home check respects six-hour throttle.
- Update count and icon colour match valid release history.
- Catalogue lists all available releases and exact dates.
- Download never opens browser.
- Download progress and cancel work.
- `INSTALL` appears only after full APK verification.
- Wrong digest, package, version or signing certificate blocks install.
- Install-source settings flow returns to same verified update state.
- Android confirmation appears after INSTALL.
- System cancellation retains verified APK.
- Successful update preserves `.pcad` drawings, Room database, settings and user-visible saved projects.
- Newly installed version removes the badge or recalculates it correctly.

---

# PART E — DOCUMENTATION, TESTING AND ACCEPTANCE

## 17. Source documentation and README maintenance

The latest source already has a root README and subsystem documents. Update them; do not create duplicates.

### 17.1 README.md

Update the existing README to accurately cover:

- PrecisionCAD Studio purpose and strictly 2D scope;
- current Android SDK targets and minimum Android version;
- current language/framework/build-tool versions;
- how to restore/sync and use the Gradle Wrapper;
- exact Debug and Release build commands;
- test and Release lint commands;
- native `.pcad` storage and DXF/PDF/SVG export distinction;
- current Paper Space and dimension features;
- GitHub release distribution and in-app updater;
- where icon master files are expected during build integration (if appropriate for developer docs);
- current limitations, not aspirational features.

### 17.2 Existing docs to update

- `docs/ARCHITECTURE.md`: add updater repository/download/installer layer, shared card system ownership, UI/ViewModel boundaries, renderer pass boundaries.
- `docs/BUILD_AND_RELEASE.md`: update API/toolchain, Gradle Wrapper, Node24-compatible actions, signing requirements, one-APK artifacts, raw public release asset and release metadata.
- `docs/COMMANDS_AND_PREVIEWS.md`: explain preview-state preservation, polar visual acquisition and temporary guide rendering.
- `docs/DIMENSION_SYSTEM.md`: explain QDIM card selectors, selective removal notification and scroll-state contracts.
- `docs/GEOMETRY_AND_COORDINATES.md`: document zoom-adaptive Grid Snap reference scale/effective interval and distinct Grid/ORTHO/OSNAP/PolarSnap semantics.
- `docs/STORAGE_AND_FORMATS.md`: preserve current native storage contract and add updater staging path only if needed; do not confuse downloaded APK storage with project storage.

Add/update file-level KDoc and public/internal API KDoc for complex modified algorithms and state models. Document coordinate spaces, units, degrees, tolerances, persistence effects, thread/dispatcher expectations and whether a function returns temporary preview or committed geometry.

### 17.3 No documentation claims without implementation

A feature may be described as implemented only after source and tests prove it. If any feature is deferred or partially implemented, document its current status precisely.

---

## 18. Integrated test plan

### 18.1 Lint/build pipeline

Run at minimum:

```bash
./gradlew --version
./gradlew clean
./gradlew :app:lintRelease
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Run connected/instrumented tests when a device/emulator is available:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Run Android API 37 emulator/device tests and at least one minimum-supported API 24/representative older API device test where practical. Record unavailable environments rather than claiming they passed.

### 18.2 Lint and dependency

- Zero actionable Release lint warnings.
- No disabled detectors or baseline hiding unresolved issues.
- Dependency resolution contains no dynamic versions.
- Active dependencies match documented stable versions.
- Inactive optional dependencies have been removed or clearly documented without being activated.
- KSP Room and Moshi processors generate and compile successfully.

### 18.3 CAD numeric and geometry

- Number formatting identical across tested locales.
- Grid Snap effective step scales inversely with zoom.
- Cursor remains inside/near usable canvas; no huge off-screen jumps.
- ORTHO and other snaps remain independent.
- Visual guide appears only after actual acquisition.
- PolarSnap only projects on independently selected snap rays.
- Model geometry is unchanged by temporary guides/overlay animations.

### 18.4 Paper Space and rendering

- No grid appears through title-block fill.
- Title-block text keeps stable screen-space size.
- Sheet and title-block geometry retain correct positions/units.
- Normal model-space text and dimensions retain existing scale behaviour.
- DXF/PDF/SVG outputs remain correct.

### 18.5 UI interaction

- QDIM horizontal card navigation and vertical body scrolling do not conflict.
- Header/footer pinning works without duplicates or reflow jumps.
- QDIM regeneration does not duplicate generated dimensions.
- Selective removal preserves scroll offset and correct selected entities.
- OSNAP state toggles do not move the scroll viewport.
- OSNAP marquee keeps abbreviation and state stationary.
- Binary toggle uses green ENABLE and grey DISABLE; no swipe toggling.
- Rear card is hidden at rest, briefly visible after exchange, then retracts.
- Idle-peek timer is local to each deck and only runs while its owning deck is visible/interactive.
- Touchpad/Joystick controller collapse/expand is animated.
- Keyboard dismiss has no jitter and controller remains stationary.
- All overlay/card animations leave viewport transform unchanged.

### 18.6 App updater

Test with a controlled public test-release repository or a staging repository. Include release records for:

- installed version;
- one newer stable release;
- several newer stable releases;
- prerelease newer than latest stable;
- draft release;
- stable release missing APK;
- duplicate version code;
- bad digest;
- wrong package ID;
- wrong signer;
- version code not increasing.

Verify badge rules, timeline order/status/date/changelog counts, action-label transition, private download, signature/digest verification, settings permission flow, Android installer confirmation, user cancellation and successful installation.

Do not test by publishing fake releases to the real production repository unless explicitly authorised. Use a staging repository or locally mocked repository responses.

### 18.7 Installer and persistence

Before and after a successful update, verify:

- saved `.pcad` files still open;
- Room project list remains intact;
- explicit `Documents/PrecisionCAD` files remain present;
- settings/preferences remain intact;
- package ID remains `com.precisioncad.studio`;
- signature certificate remains trusted;
- application starts and Home page appears;
- versionCode increases;
- no temporary `.part` file or failed installer session remains unintentionally.

---

## 19. Implementation deliverables

The coding agent must deliver:

1. Modified Android source implementing all approved V18 workstreams.
2. Updated Gradle version catalog, build scripts and complete Gradle Wrapper.
3. Updated lint and signed-build GitHub Actions workflows.
4. Updated app launcher resources generated from the three separate user-supplied master images.
5. GitHub updater/release-catalogue implementation and its testable repository configuration.
6. Unit/UI/integration tests covering the acceptance matrix.
7. Updated README and existing `docs/` files.
8. Release lint report after remediation.
9. Debug and signed Release APKs from the verified build pipeline, when signing secrets are available.
10. A concise implementation report listing changed files, tests run, build outcomes, remaining warnings, skipped tests, signing fingerprint verification result, SDK/toolchain versions and any external configuration still required.
11. A non-blocking source and dependency licensing-inventory workflow, with downloadable scan reports and explicit scan-completeness status. This is identification and reporting only; it must not choose, grant, approve, reject or interpret a project licence.

### 19.1 Required completion report format

The agent's final implementation report must include:

- source commit before and after changes;
- full toolchain versions;
- compileSdk/minorApiLevel/targetSdk/minSdk;
- lint warning/error counts before and after;
- dependency updates actually applied (not all catalog aliases);
- icon source files received and generated resource outputs;
- Paper Space/viewport tests;
- snapping/polar unit tests;
- QDIM/OSNAP/controller/card UI tests;
- updater API/catalogue/download/installer test results;
- application ID and signing certificate continuity;
- APK filenames, versionName, versionCode and SHA-256;
- GitHub Release publication result and public asset URL (if publishing was enabled);
- all unresolved blockers;
- licensing-inventory workflow run status, scanner versions, scanned scope, generated report filenames, component/file counts, detected identifiers and undetected/ambiguous records;
- confirmation that the licensing inventory was informational only and did not gate build, merge or release.

Do not state “complete” for any item that was not built, tested or verified.

---

## 20. Final precedence and stop conditions

1. This document defines V18 scope. V17 is treated as the completed baseline; do not duplicate or regress its behaviour.
2. The latest checked-out source code takes precedence over an older code excerpt, but this V18 behaviour specification takes precedence over accidental current implementation defects.
3. If a current implementation conflicts with a stated V18 contract, correct it rather than preserving the defect.
4. Do not invent repository owner/name, signing secrets, asset files, release dates, changelog items, SDK package names or version codes.
5. If separate launcher source images are not present, leave the icon integration subtask pending and report it; do not generate substitutes.
6. If API 37.2 is not available as a stable SDK package or not supported by the selected toolchain, use the latest verified stable API 37 minor level and document why.
7. If the latest AGP release has no fully documented compatible Kotlin Compose/KSP combination, choose a formally supported stable combination rather than an unsupported mixture.
8. If public distribution repository is not identified or public, implement configurable repository support but do not expose private credentials or claim anonymous downloads will work.
9. If the release signing key/secrets are missing, fail the production Release signing/publication task. Never generate a replacement production key.
10. If an APK fails package/version/signature/digest validation, never pass it to Android's installer.
11. Do not implement silent/privileged installation. Use Android's standard confirmation flow as specified.
12. Do not disable lint checks, destroy user data, rewrite document formats, or modify application ID to avoid an implementation problem.
13. Do not modify the CAD viewport transform as a side effect of UI animation, keyboard movement, card idle peeks, QDIM dialog movement, list selection or controller expansion/collapse.
14. The licensing inventory is strictly descriptive. Do not select or grant a licence for PrecisionCAD Studio; do not create, replace or modify a root `LICENSE`/`COPYING` file, copyright notice or `SPDX-License-Identifier` header as part of this work. Do not classify any detected component as permitted, prohibited, compatible, incompatible, acceptable or unacceptable for use. Do not create allowlists, denylists, policy gates or automatic removal actions.
15. Unknown, missing, multiple or ambiguous licence findings are report data, not build failures and not legal conclusions. Preserve the evidence and label the record's detection status accurately. The scan job must not block application build, merge or public release.

---

## 21. Official technical references checked while preparing this plan

These references are provided to the coding agent for compatibility/security decisions. Re-check their current contents at implementation time; versions move over time.

### Android API and build configuration

- Android API 37.2 differences: https://developer.android.com/sdk/api_diff/37.2/changes/changes-summary
- Android API 37.2a incremental specification: https://developer.android.com/sdk/api_diff/37.2a-incr/changes/changes-summary
- Android `compileSdk` and `minorApiLevel`: https://developer.android.com/build
- Android 17 overview: https://developer.android.com/about/versions/17
- Android 17 release notes/behaviour changes: https://developer.android.com/about/versions/17/release-notes
- AGP 9.3 release notes: https://developer.android.com/build/releases/agp-9-3-0-release-notes
- AGP 9.5 release notes: https://developer.android.com/build/releases/agp-9-5-0-release-notes
- AGP/Gradle compatibility: https://developer.android.com/build/releases/about-agp
- Kotlin Gradle Plugin compatibility: https://kotlinlang.org/docs/gradle-configure-project.html
- KSP releases: https://github.com/google/ksp/releases

### Android launcher icons

- Adaptive icon foreground/background/monochrome and safe zone: https://developer.android.com/develop/ui/compose/system/icon_design_adaptive

### GitHub Actions

- `actions/checkout`: https://github.com/actions/checkout/releases
- `actions/setup-java`: https://github.com/actions/setup-java
- `android-actions/setup-android`: https://github.com/android-actions/setup-android/releases
- `gradle/actions`: https://github.com/gradle/actions/releases
- `actions/upload-artifact` including `archive: false`: https://github.com/actions/upload-artifact
- `softprops/action-gh-release` if retained: https://github.com/softprops/action-gh-release/releases

### GitHub Releases and Android installation

- GitHub Releases REST API: https://docs.github.com/en/rest/releases/releases
- Android `PackageManager.canRequestPackageInstalls()`: https://developer.android.com/reference/android/content/pm/PackageManager
- Android `PackageInstaller.SessionParams`: https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams

### Software composition and licensing inventory (identification only)

- CycloneDX Gradle plugin: https://github.com/CycloneDX/cyclonedx-gradle-plugin
- CycloneDX Gradle plugin on the Gradle Plugin Portal: https://plugins.gradle.org/plugin/org.cyclonedx.bom
- ScanCode Toolkit license detection: https://scancode-toolkit.readthedocs.io/en/stable/explanation/scancode-license-detection.html
- ScanCode Toolkit detection options: https://scancode-toolkit.readthedocs.io/en/stable/tutorials/configuring-scan-detection.html
- SPDX handling of licence information and identifiers: https://spdx.dev/learn/handling-license-info/
- SPDX license expressions: https://spdx.github.io/spdx-spec/v3.0.1/annexes/spdx-license-expressions/

# PART F — INFORMATIONAL LICENSING INVENTORY WORKFLOW (NO LICENCE DECISION OR GATE)

## 22. Source, dependency and bundled-material licence identification

### 22.1 Purpose and strict scope

Add an automated workflow that identifies and inventories licence-related information found in the PrecisionCAD Studio repository and in the software components resolved or bundled by its build.

**This workflow is an informational inventory only.** Its purpose is to answer questions such as:

- Which third-party packages and components are present?
- Which licence identifiers or licence expressions are declared in package metadata?
- Which licence texts, SPDX headers, copyright notices and attribution notices are detected in repository files?
- Which bundled assets or vendored files contain detectable licence or copyright information?
- Where was each result found, and what evidence supports that identification?
- Which records have no licence information detected, multiple findings, or an inconclusive detection?

It must not answer whether a component may or may not be used by PrecisionCAD Studio. It must not decide whether any licence is compatible with a future project licence, whether a component is approved, or whether the project is legally compliant.

The user has explicitly not selected or granted an open-source licence to PrecisionCAD Studio. This V18 task must not make that selection on the user's behalf.

### 22.2 Absolute non-goals

The workflow must not:

1. Select an open-source licence for the PrecisionCAD Studio project.
2. Create, replace, rewrite or add a root `LICENSE`, `LICENCE`, `COPYING` or `NOTICE` file as a licensing decision.
3. Add or alter copyright ownership notices.
4. Insert `SPDX-License-Identifier` headers into source files.
5. Remove, replace or automatically update a dependency because of its licence identifier.
6. Label a licence or component as allowed, denied, approved, rejected, compatible, incompatible, safe, unsafe, acceptable or unacceptable for use.
7. Compute or publish a licence-compatibility verdict between components or between a component and a proposed project licence.
8. Apply a licence allowlist, denylist, policy threshold, compatibility matrix or licence-based PR/release gate.
9. Fail or block compilation, tests, pull-request merging, APK signing or GitHub Release publication because a licence was detected, not detected, unknown, multiple or ambiguous.
10. Offer legal advice or present automated detection as a legal determination.

Do not use GitHub Dependency Review licence policy, FOSSA/ORT policy enforcement, or another allow/deny policy mode for this task. Tools may be used only in inventory/reporting mode.

### 22.3 Inventory coverage

The scan must cover the following repository and build material, where present:

**A. First-party repository files**

- Kotlin and Java source files.
- Gradle Kotlin DSL and settings files.
- Version catalogs and build logic.
- XML resources and Android manifests.
- Markdown, README and technical documentation.
- Shell scripts, workflow YAML and configuration files.
- Existing `LICENSE`, `LICENCE`, `COPYING`, `NOTICE`, `THIRD_PARTY_NOTICES`, `AUTHORS`, `COPYRIGHT`, `.reuse/dep5` and similar files.
- Existing file-level SPDX identifiers and existing copyright statements.

**B. Resolved dependencies**

- Direct dependencies used by the application.
- Transitive dependencies selected by Gradle resolution.
- Release and Debug dependency graphs where they differ.
- Relevant compile, runtime, annotation-processing/KSP and test configurations.
- Local JAR, AAR and other file-based dependencies.
- Gradle plugins and buildscript/build-logic dependencies, recorded separately from application runtime dependencies.
- Any generated or bundled third-party package metadata that forms part of the application build.

The inventory must preserve the configuration/scope in which a component was resolved. Do not collapse a test-only dependency into the application runtime scope or imply that every build-tool dependency is shipped in the APK.

**C. Vendored and bundled materials**

- Third-party source directories or copied source files.
- Embedded archives and package source bundles.
- Fonts, font families and font files.
- Images, icons, illustrations and other artwork stored in the repository.
- Native libraries, binary assets, templates, sample files and other redistributed material.
- The three user-supplied launcher artwork source files when they are added to the repository.

A binary or image for which no licence information is detectable must be recorded as `not detected` or `no evidence found`; do not infer a licence from its appearance, filename or origin.

### 22.4 Two complementary inventory sources

Use two complementary tools because dependency metadata and file-level licence evidence are different kinds of information.

**1. CycloneDX Gradle plugin — resolved dependency inventory**

Use the official CycloneDX Gradle plugin to generate a Software Bill of Materials from Gradle's resolved dependency graph. It records direct and transitive components selected by dependency resolution and produces machine-readable JSON and XML SBOM files. The currently published 3.x plugin line supports Gradle 8.4 or newer; the current Plugin Portal release observed while preparing this plan is 3.5.1. Verify the pinned version against the actual V18 Gradle/JDK/toolchain before applying it.

Generate the aggregate BOM and per-project/direct BOMs where supported by the selected plugin version. Preserve:

- component name;
- group and artifact coordinates;
- resolved version;
- package URL (PURL), when available;
- component type;
- direct/transitive relationship;
- dependency scope/configuration;
- supplier/publisher metadata, where present;
- licence identifier or expression supplied by package metadata;
- source repository or external reference, where present.

Do not invent licence values when Gradle or package metadata provides none. Preserve the source field as missing/unknown/not supplied rather than substituting a guess.

Use the plugin's documented task and output paths for the pinned release; do not assume paths from a different plugin version. The current aggregate task is normally `cyclonedxBom`; confirm it with `./gradlew tasks --all` after integration.

**2. ScanCode Toolkit — repository and file-level evidence**

Use ScanCode Toolkit in its licence/copyright/package detection mode to scan repository files, local bundled materials and staged third-party archives. The scan should collect:

- detected licence matches;
- existing SPDX identifiers and licence expressions;
- copyright statements;
- package/embedded package metadata;
- file path and type;
- line ranges for text-based evidence where available;
- scanner rule/reference information;
- detection origin (file text, file header, package metadata or archive/package record), where available.

Use the official ScanCode Toolkit release or container image, pin an exact stable version or immutable image digest, and record the scanner version in each report. Do not use a floating `latest` tag or unpinned branch.

The scan should enable licence, copyright, package and file-information detection, plus licence references. Use JSON as the canonical ScanCode output. Produce CSV and a human-readable HTML summary from that JSON if the pinned ScanCode release does not provide the exact combined view required. Retain the raw JSON as the evidence source.

Do not enable a policy or compliance enforcement mode. Detection scores, matched text and references may be shown as detector output, but must not be translated into a use/permission verdict.

### 22.5 Dependency configuration coverage

The agent must inspect the actual Gradle projects and configurations before configuring the SBOM task. At minimum, inventory the resolved dependency graphs for:

- `releaseRuntimeClasspath`;
- `releaseCompileClasspath`;
- `debugRuntimeClasspath`;
- `debugCompileClasspath`;
- relevant unit-test and instrumented-test runtime/compile configurations;
- KSP, annotation processor and code-generation dependencies;
- root and module buildscript/plugin dependencies.

Use the CycloneDX plugin's documented configuration inclusion/exclusion facilities for the pinned version. If a configuration is not supported by the plugin or cannot be resolved in the CI environment, record the exact omitted configuration and reason in the scan status report.

Capture Gradle plugin/buildscript dependencies separately using Gradle's dependency/build-environment reporting and the actual plugin declarations/resolution metadata. Do not silently assume that the application SBOM includes the Gradle plugin classpath.

Stage local JAR/AAR dependencies and other resolved package archives for ScanCode where practical. Keep their coordinates/configuration association in a manifest so file-level ScanCode findings can be related to the corresponding resolved component. Do not scan the entire developer Gradle cache indiscriminately; stage only artifacts resolved by the current project configurations.

### 22.6 Repository scan boundaries and exclusions

Scan tracked repository content and deliberate bundled/vendor material. Exclude generated or irrelevant build data such as:

- `.git/`;
- `.gradle/`;
- `**/build/`;
- local IDE caches and temporary files;
- generated lint reports and previously generated licensing reports;
- APKs/AABs produced during the current CI run unless a separate packaged-output inventory is explicitly generated;
- `local.properties`;
- signing keystores, signing property files, private keys, credentials, tokens and other secrets.

Do not exclude `libs/`, vendored sources, font/resource directories, bundled asset directories, existing notice files or checked-in archives merely because they are binary or large. Inspect them or record a clearly explained unsupported/unscanned status.

The repository scan must not print secret file contents. The scanner input should never include private signing material.

### 22.7 Required workflow

Create a dedicated GitHub Actions workflow, for example:

`.github/workflows/licensing-inventory.yml`

Use the following triggers:

- `pull_request`;
- `push` to the repository's configured primary/release branch;
- `workflow_dispatch` for a manual inventory run;
- optionally `release.published` if a fresh report is required for each published release.

Use least-privilege workflow permissions. The inventory job requires source read access only; it must not receive release-signing secrets, personal GitHub tokens or write permissions.

The workflow must:

1. Check out the exact commit being scanned.
2. Record repository, branch/ref, commit SHA and scan timestamp.
3. Set up the same verified JDK and Gradle Wrapper used by the V18 build.
4. Resolve the relevant Gradle configurations and generate CycloneDX SBOM output.
5. Capture Gradle plugin/buildscript dependency information separately.
6. Stage relevant local/resolved archives for file-level inspection.
7. Run ScanCode on repository source, existing notices, bundled resources and staged third-party archives.
8. Generate a consolidated human-readable inventory from the two evidence sources without merging away conflicting records.
9. Upload the raw and human-readable reports as a GitHub Actions artifact.
10. Publish a concise workflow summary identifying report locations, record counts and scan-completeness status.

The job must be independent of the Android build/release dependency chain. It may run in parallel with lint/build jobs. It must not be declared as a required licensing-policy gate for merge or release.

Use a separate non-blocking job or workflow status policy so a detected licence, missing licence metadata, unknown licence text, multiple matches or a particular SPDX identifier cannot block build or release. If a scanner crashes, the scan report must say `INCOMPLETE` and include the tool error; do not label the scan clean. Upload any reports already generated using an `always()`-conditioned upload step and an appropriate `if-no-files-found: warn` behaviour. A scanner execution problem should be visible as an inventory-tool problem, not misrepresented as a licence violation.

### 22.8 Required report files

Store generated output under a dedicated CI output directory, for example:

`reports/licensing/`

Produce at least:

1. `dependency-bom.cdx.json` — aggregate CycloneDX JSON SBOM.
2. `dependency-bom.cdx.xml` — aggregate CycloneDX XML SBOM.
3. `scancode-source-inventory.json` — raw ScanCode JSON.
4. `scancode-source-inventory.csv` — tabular file-level findings.
5. `licensing-inventory.html` — human-readable combined report.
6. `licensing-inventory-summary.md` — concise GitHub Actions/job summary.
7. `licensing-inventory-status.json` — scan provenance and completeness status.
8. `gradle-build-logic-dependencies.txt` or equivalent structured file — resolved Gradle plugin/buildscript inventory.

If the selected ScanCode release can produce a suitable SPDX document directly, an SPDX output may also be retained. Do not convert uncertain data into a fabricated SPDX assertion. Preserve raw scanner output regardless of any derived presentation format.

Upload the complete `reports/licensing/` directory as a separately named Actions artifact such as:

`precisioncad-licensing-inventory-<short-commit-sha>`

Keep this report artifact separate from APK artifacts. Do not attach it to the public application Release by default; this feature is an internal/repository inventory unless the owner later decides to publish it.

### 22.9 Consolidated inventory schema

The combined report should present one record per file-level finding or package/component finding. Where the same component occurs in several configurations, preserve the scope relationships rather than collapsing them into one misleading record.

Include these fields when available:

- record identifier;
- component or file name;
- component version;
- group/artifact coordinates;
- PURL;
- repository-relative file path;
- file hash (prefer SHA-256 in the derived inventory);
- dependency scope/configuration;
- direct or transitive relationship;
- detected or declared SPDX licence identifier/expression exactly as supplied;
- raw licence name/text label if no SPDX identifier is provided;
- evidence origin (package metadata, SPDX header, licence text/file, copyright notice, embedded package metadata or other scanner-reported source);
- matched line range, when available;
- copyright statement, when detected;
- scanner name/version;
- detection/reference information and score, if supplied by the scanner;
- status: `DETECTED`, `MULTIPLE_FINDINGS`, `NOT_DETECTED`, `UNKNOWN_METADATA`, `AMBIGUOUS`, `UNSCANNED` or `SCAN_INCOMPLETE`.

The status values describe what the tools observed. They are not approval or rejection states. In particular, `NOT_DETECTED` must never be written as `UNLICENSED`, and `UNKNOWN_METADATA` must never be treated as proof that no licence exists.

Where metadata and file-text detection disagree, show both records and flag `MULTIPLE_FINDINGS` or `AMBIGUOUS` for human inspection. Do not automatically choose which source is legally authoritative.

SPDX identifiers and expressions should be preserved as identifiers/expressions, including `AND`, `OR` and `WITH` relationships where declared. Do not simplify an expression or interpret its legal effect. Use the SPDX License List as a reference for identifier spelling only; matching an identifier is not a legal analysis.

### 22.10 Reporting presentation

The HTML report and GitHub Actions summary should contain:

- scan commit and timestamp;
- scanner/tool versions;
- total dependency-component records;
- number of direct and transitive components;
- counts by dependency configuration;
- number of repository files scanned;
- number of files with detected licence information;
- number of files with detected copyright statements;
- distinct identifier/expression strings observed;
- records with multiple findings or ambiguous evidence;
- records with no licence information detected;
- records skipped or not scanned;
- links/paths to raw JSON, CSV and CycloneDX output;
- scan completeness (`COMPLETE` or `INCOMPLETE`).

Display licence strings and observed counts descriptively. Do not use red/green compliance badges, “approved”, “blocked”, “safe”, “unsafe”, “compatible”, “incompatible” or wording that implies a permission decision.

The report must include a prominent statement:

> Informational inventory only. Automated detection reports licence and copyright information observed in source files, package metadata and bundled materials. It does not select a project licence, determine legal obligations, evaluate compatibility, or decide whether a component may be used. Missing or ambiguous findings require separate human investigation; they are not legal conclusions.

### 22.11 Project licence remains undecided

The user intends to make an open-source licensing decision separately. This V18 workflow must not make that decision.

If the repository currently contains a root `LICENSE`, `NOTICE` or copyright statement, the scan should report its existence, contents/signature and location as observed. It must not overwrite, replace, amend or infer that it is the user's final chosen project licence.

Do not add a root `LICENSE` file, `COPYING` file, `SPDX-License-Identifier` header or licence badge to the README as part of this task. Do not alter the package manifest to claim a project-wide licence. Any later project-licence selection is a separate owner-authorised task.

### 22.12 Acceptance tests

The implementation is accepted only when all of these are demonstrated:

1. A workflow run on a pull request produces a dependency SBOM and file-level ScanCode report.
2. A push to the configured primary branch produces the same report set for the pushed commit.
3. Manual dispatch is available.
4. The direct and transitive Gradle dependencies are represented, with the relevant configuration/scope recorded.
5. Gradle plugin/buildscript dependencies are represented separately or explicitly reported as unscanned with a reason.
6. Local JAR/AAR dependencies and checked-in vendored archives are included in the scan scope or explicitly recorded as not scanned.
7. Existing licence files, SPDX headers and copyright notices are identified with paths and line numbers where available.
8. Bundled fonts, artwork and other repository assets are included in scan scope; absence of detectable metadata is reported as `NOT_DETECTED`, not as an inferred licence.
9. Multiple or conflicting metadata/text findings are preserved rather than silently resolved.
10. The raw scanner outputs and consolidated HTML/CSV/Markdown summaries are uploaded as an Actions artifact.
11. The scan status clearly identifies `COMPLETE` or `INCOMPLETE` and lists omitted inputs.
12. A particular licence identifier, a missing identifier, an ambiguous result or a third-party component never blocks the Android build, merge or GitHub Release.
13. The workflow does not create or modify the project's root licence file, copyright notices or SPDX headers.
14. No report describes a component as permitted, prohibited, compatible, incompatible, approved or rejected.
15. Re-running the scan on the same commit with the same pinned scanner/tool versions produces materially reproducible inventory records; timestamps and run metadata may differ.

### 22.13 Implementation notes and tool references

Recommended implementation:

- Use the CycloneDX Gradle plugin for the resolved Gradle dependency graph. The observed current plugin release is 3.5.1, but pin only after testing compatibility with the V18 Gradle/JDK selection.
- Use ScanCode Toolkit for file-level licence, copyright and package detection. Pin an exact stable release or immutable image digest.
- Use SPDX identifiers/expressions only as standardised recorded identifiers; do not apply the expressions as a policy.
- Keep the scanner job isolated from signing secrets and release-write permissions.
- Store the generated reports as CI artifacts; do not commit generated reports to the source tree on every build unless a separate owner-approved retention policy is later requested.
- Add a short explanation of this inventory-only workflow to the existing `docs/BUILD_AND_RELEASE.md` or a new `docs/LICENSING_INVENTORY.md`. The documentation must explicitly state that it is not a licence grant, compliance review or legal decision.

Official references:

- CycloneDX Gradle plugin overview and resolved direct/transitive SBOM generation: https://github.com/CycloneDX/cyclonedx-gradle-plugin
- CycloneDX Gradle plugin versions: https://plugins.gradle.org/plugin/org.cyclonedx.bom
- ScanCode licence and copyright detection, file locations and line evidence: https://scancode-toolkit.readthedocs.io/en/stable/explanation/scancode-license-detection.html
- ScanCode detection options: https://scancode-toolkit.readthedocs.io/en/stable/tutorials/configuring-scan-detection.html
- SPDX licence identifiers and expressions: https://spdx.dev/learn/handling-license-info/


---

# Handoff metadata

- Baseline archive: `PrecisionCAD-Studio-main.zip`
- Baseline archive SHA-256: `d907ec40bd216760ec4386461e2cc95d1abe72b0979d18b2202c56f67e90cc06`
- Archive entry count: `126`
- ZIP integrity test: `passed`
- Prepared on: 3 November 2026
- This document is an implementation plan; it does not claim any source changes have already been performed.
