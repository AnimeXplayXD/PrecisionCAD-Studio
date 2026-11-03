# PrecisionCAD Studio

**PrecisionCAD Studio** (`com.precisioncad.studio`) is a strictly 2D engineering CAD drafting, automatic dimensioning, and technical documentation application for Android built with Kotlin and Jetpack Compose.

---

## Build, SDK & Toolchain Summary

- **Android SDK Levels**:
  - `compileSdk`: Android API `37` (`minorApiLevel = 2`, `platforms;android-37.2`, `build-tools;37.0.0`)
  - `targetSdk`: `37` (Android 17)
  - `minSdk`: `24` (Android 7.0+)
- **Toolchain Versions**:
  - Gradle Wrapper: `9.3.1` (`./gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) with pinned `distributionSha256Sum` (`b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06`)
  - Android Gradle Plugin (AGP): `9.1.1`
  - Kotlin Compose Compiler Plugin: `2.2.10`
  - KSP (Room & Moshi codegen): `2.3.5`
  - Java Bytecode Target: `11` (CI runner JDK `21` on `ubuntu-26.04`)
- **Gradle Wrapper Commands**:
  ```bash
  ./gradlew --version
  ./gradlew :app:lintRelease
  ./gradlew :app:testDebugUnitTest
  ./gradlew :app:assembleDebug
  ./gradlew :app:assembleRelease
  ```
- **Launcher Icon Master Assets**:
  - Unmodified 1024×1024 source masters live in `design-assets/icons/` (`precisioncad_foreground.png`, `precisioncad_background.png`, `precisioncad_monochrome.png`).
  - Adaptive icon layers (`ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `ic_launcher_monochrome.xml`) and legacy density PNGs (`mipmap-mdpi` through `mipmap-xxxhdpi`) are generated into `app/src/main/res/`.

---

## Core 2D Engineering Capabilities

- **Exact Double-Precision 2D Geometric Kernel (`CadGeometryKernel` & `CadSnapAndCoordEngine`)**:
  - True analytical 2D geometry (`Point2D`, `LineEntity`, `CircleEntity`, `ArcEntity`, `PolylineEntity` with bulge factors, `EllipseEntity`, `SplineEntity`, `RayEntity`, `XLineEntity`, `DonutEntity`, `HatchEntity`, `BlockInsertEntity`, `TextEntity`, `LeaderEntity`, `CenterMarkEntity`, `DimensionEntity`).
  - Invariant engineering number formatting (`CadNumberFormatter` using `Locale.ROOT`) so coordinates, lengths, and bearings always use `.` as decimal separator across all device locales.
  - Independent storage unit (`storageUnit`) and working/display unit (`displayUnit`) with explicit conversion modes (**Convert Display Units** vs. **Reinterpret Coordinates**).
  - Full coordinate entry grammar (`X,Y`, `@dX,dY`, `R<θ`, `@R<θ`, direct distance `R`) and **Direct Coordinate Window** with synchronized `X`, `Y`, and editable Polar Bearing `θ (°)`.
- **Zoom-Adaptive Grid Snap & Separated Visual Polar Tracking vs. PolarSnap**:
  - **Zoom-Adaptive Grid Snap**: Scales effective grid snap interval inversely with zoom (`effectiveStep = rawStep` quantized to a `1–2–2.5–5 × 10^n` ladder with 12% hysteresis, `Long` overflow guard, and screen-space jump/viewport guards) while keeping user-configured `gridSnapSpacing` intact.
  - **Visual Polar Tracking (`0°–359°`)**: Acquires whole-number bearings (`0°..359°`) and custom fractional visual bearings (`e.g. 22.5°`) only within a narrow angular corridor (`0.35°` acquire / `0.55°` release hysteresis). Never constrains or projects cursor coordinates.
  - **PolarSnap (Independent Multi-Select Collection)**: Independently toggled angular projection using its own preset collection (`15°`, `30°`, `45°`, `60°`, `90°` — default `45° + 90°`) and custom snap angles (`126°`).
- **Paper Space Sheet & Opaque Title Block Pass**:
  - Splits Paper Space rendering into a sheet base pass (drawn before the grid) and an opaque title-block pass (drawn after the grid) so grid lines never bleed through the title block, with zoom-independent screen-space title-block typography.
- **Shared Card Component & Section-Local Idle Motion System**:
  - Concealed rear card at rest, `2.4s` post-exchange reveal, and strictly isolated `75s` section-local idle peek (`CadDeckIdleTimerState`).
  - Layout-phase lambda `Modifier.offset { IntOffset(...) }` animations across all card decks so animated cards do not trigger unnecessary Compose recomposition.
  - Green `ENABLE` and neutral-grey `DISABLE` binary cards across Cursor Control, Grid & OSNAP settings, with 3-region OSNAP cards (`[END]` badge left, horizontal marquee full label middle, `ENABLE`/`DISABLE` right).
  - Unbordered translucent floating command dock, single-source IME positioning, and animated controller collapse/expand.
- **Quick Dimension (`QDIM`) & Selective Dimension Removal**:
  - Horizontal swipeable multi-option card decks for Scope, Arrangement, and Density Policy (decoupled from vertical dialog scrolling) with non-layout-shifting delayed callout overlays driven by `scrollState.isScrollInProgress`.
  - Category-First Selective Dimension Removal with stable scroll-offset preservation, header `SELECT ALL` / `DESELECT ALL` action, and non-layout-shifting transient marquee count notification.
- **Native `.pcad` Persistence & DXF / PDF / SVG Interchange**:
  - Atomic crash-safe Quick Save (`pcad_autosave/quick_save_working.pcad`) and Explicit Save (`Documents/PrecisionCAD/<basename>.pcad`), distinct from ASCII DXF (`AC1015`), plotted vector PDF, and SVG export.
- **GitHub Release Catalogue & In-App Updater**:
  - Queries public GitHub Releases (`AnimeXplayXD/PrecisionCAD-Studio`), displays the Home top-right update status icon (white when current, gold for 1 update, gold + count beside icon for >1 updates), renders a Morphp-style chronological changelog timeline with `DOWNLOAD`/`CANCEL` and `INSTALL`/`CANCEL` footer cards, verifies SHA-256 digest + package ID + versionCode + signer certificate, and invokes Android's `PackageInstaller` confirmation directly in-app.

---

## Current Scope Limitations

- Strictly 2D drafting and annotation (no 3D solid modeling, 3D meshes, or Z-axis extrusion).
- Production Release APK signing in CI fails closed unless all five canonical repository signing secrets (`KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`, `RELEASE_CERT_SHA256`) are configured and pass SHA-256 certificate verification.

---

## Documentation Index

| Document | Scope |
| :--- | :--- |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Clean MVVM package topology, renderer passes, shared card system, updater layer, and IME invariance |
| [`docs/GEOMETRY_AND_COORDINATES.md`](docs/GEOMETRY_AND_COORDINATES.md) | Locale-invariant formatting, zoom-adaptive Grid Snap, OSNAP, Visual Polar Tracking vs. PolarSnap |
| [`docs/COMMANDS_AND_PREVIEWS.md`](docs/COMMANDS_AND_PREVIEWS.md) | Interactive CAD command state machine, live-preview preservation, and temporary polar guide rendering |
| [`docs/DIMENSION_SYSTEM.md`](docs/DIMENSION_SYSTEM.md) | QDIM horizontal card selectors, non-shifting callout overlays, and Selective Dimension Removal scroll stability |
| [`docs/STORAGE_AND_FORMATS.md`](docs/STORAGE_AND_FORMATS.md) | Native `.pcad` v2 JSON schema, atomic quick-save, `Documents/PrecisionCAD/` storage, and updater APK staging |
| [`docs/BUILD_AND_RELEASE.md`](docs/BUILD_AND_RELEASE.md) | API 37.2 toolchain, Gradle Wrapper, fail-closed 5-secret signing, Node 24 Actions, single-APK artifacts, and release metadata |
| [`docs/LICENSING_INVENTORY.md`](docs/LICENSING_INVENTORY.md) | Non-blocking informational source and dependency licensing inventory workflow (ScanCode v32.5.0 & per-GAV POM metadata) |
