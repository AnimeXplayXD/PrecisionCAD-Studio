# PrecisionCAD Studio

**PrecisionCAD Studio** (`com.precisioncad.studio`) is a desktop-class 2D engineering CAD drafting, automatic dimensioning, and technical documentation application for Android built with Kotlin and Jetpack Compose.

---

## Core Engineering Capabilities

- **Exact Double-Precision 2D Geometric Kernel (`CadMath2D` & `CadSnapAndCoordEngine`)**:
  - True analytical geometry (`Point2D`, `LineEntity`, `CircleEntity`, `ArcEntity`, `LwPolylineEntity` with bulge factors, `EllipseEntity`, `SplineEntity`, `RayEntity`, `XLineEntity`, `DonutEntity`, `HatchEntity`, `BlockRefEntity`, `MTextEntity`, `DimensionEntity`).
  - Independent storage unit (`storageUnit`) and working/display unit (`displayUnit`) with explicit conversion modes (**Option A: Convert Display Units** vs. **Option B: Reinterpret Coordinates**).
  - Full coordinate entry grammar: Absolute Cartesian (`X,Y`), Relative Cartesian (`@dX,dY`), Absolute Polar (`R<θ`), Relative Polar (`@R<θ`), Direct Distance Entry (`R`), and interactive **Direct Coordinate Window** with synchronized `X`, `Y`, and editable Polar Bearing `θ (°)`.
- **Velocity-Sensitive Virtual Cursor Controller**:
  - **Vertical-Peek Two-Card Mode Selector**: Blue foreground/peeking **Touchpad** mode and Gold foreground/peeking **Joystick** mode with tap-only switching and animation-flight lock.
  - **Live Controller Coordinate HUD**: Displays real-time `X`, `Y`, and current bearing `θ (°)` (`+X = 0°`, counter-clockwise positive in `[0°, 360°)`).
  - **Discrete Step Nudge Pad**: Configurable engineering step increments (`10`, `5`, `1`, `0.5`, `0.1`, `0.01`).
- **Separated Visual Polar Tracking & PolarSnap**:
  - **Visual Polar Tracking (`0°–359°`)**: Informational whole-number degree bearings (`0°..359°`) plus custom fractional angles (`e.g. 22.5°, 126.5°`). Never constrains or moves the cursor by itself.
  - **PolarSnap (Independent Multi-Select Collection)**: Independently toggled angular projection using a multi-select preset collection (`15°`, `30°`, `45°`, `60°`, `90°` — default `45° + 90°`) and custom angles (`126°`).
- **Quick Dimension (`QDIM`) & Selective Dimension Removal**:
  - Deterministic two-pass feature extraction, 1D interval-graph lane allocation, and 2D AABB text-placement collision avoidance.
  - Continuous dark engineering modal with scroll-responsive header/footer, rotary wheel selectors with 2-second delayed speech-bubble callout, two-card footer action deck (**Enable & Generate** / **Clear All**), and **Category-First Selective Dimension Removal**.
- **Native `.pcad` Persistence & Interoperability**:
  - **Automatic Quick Save**: Atomic crash-safe write (`.tmp` + `.bak` + rename) in app-private storage (`pcad_autosave/quick_save_working.pcad`).
  - **Explicit Save**: User-visible project persistence in `Documents/PrecisionCAD/<basename>.pcad`.
  - **Authoritative Basename Synchronization**: Saving `Geo.pcad` synchronizes the title-block `TITLE: Geo` field and exports plotted sheets as `Geo.pdf`.
  - **Full ASCII DXF (`R2000 / AC1015`), Plotted PDF, and SVG Export/Import**.

---

## Documentation Index

| Document | Scope |
| :--- | :--- |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Clean MVVM package topology, state flow, and IME/overlay invariance |
| [`docs/GEOMETRY_AND_COORDINATES.md`](docs/GEOMETRY_AND_COORDINATES.md) | Double-precision coordinate spaces, OSNAP, Visual Polar Tracking vs. PolarSnap, and editable bearing `θ (°)` |
| [`docs/COMMANDS_AND_PREVIEWS.md`](docs/COMMANDS_AND_PREVIEWS.md) | Interactive CAD command state machine and shared live-preview preservation across sub-option/parameter edits |
| [`docs/DIMENSION_SYSTEM.md`](docs/DIMENSION_SYSTEM.md) | Quick Dimension (`QDIM`) solver, lane allocation, and Category-First Selective Dimension Removal |
| [`docs/STORAGE_AND_FORMATS.md`](docs/STORAGE_AND_FORMATS.md) | Native `.pcad` v2 JSON schema, atomic quick-save, `Documents/PrecisionCAD/` storage, and DXF/PDF/SVG interchange |
| [`docs/BUILD_AND_RELEASE.md`](docs/BUILD_AND_RELEASE.md) | Automated versioning, single-APK Gradle configuration, and GitHub Actions release signing |
