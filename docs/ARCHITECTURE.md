# PrecisionCAD Studio — System Architecture

## 1. Layered Package Structure

PrecisionCAD Studio is organized into four deterministic layers under `app/src/main/java/com/example/`:

1. **`cad/model/` — Domain Entities & Immutable Value Objects**:
   - `CadEntities.kt`: Sealed hierarchy (`CadEntity`) with exact `Double` coordinates (`LineEntity`, `LwPolylineEntity`, `CircleEntity`, `ArcEntity`, `EllipseEntity`, `SplineEntity`, `RayEntity`, `XLineEntity`, `DonutEntity`, `HatchEntity`, `BlockRefEntity`, `MTextEntity`, `DimensionEntity`), `CadLayer`, `CadBlockDefinition`, and `GeometricConstraint`.
   - `CadUnitsAndSheet.kt`: `CadUnit`, `UnitChangeMode`, `EngineeringPrecision`, `SheetSetup`, `TitleBlockMetadata`, `GridAndSnapConfig`, and `VirtualControllerConfig`.
   - `CadCommandCatalog.kt`: `CadCommandId`, `CadCommandCategory`, and `CadCommandCompatibilityCatalog`.
2. **`cad/kernel/` — Analytical 2D CAD Kernel**:
   - `CadMath2D.kt`: Intersection solvers, fillet/chamfer arc trimmers, offset curve generators, polygon/rectangle builders, and bounding-box evaluators.
   - `CadSnapAndCoordEngine.kt`: Coordinate expression parser, OSNAP candidate evaluator, separated Visual Polar Tracking (`0°..359°` + custom fractional) vs. PolarSnap (multi-select preset collection + custom angles), and viewport camera transforms (`ViewportTransform`, `FitDrawingEngine`).
   - `CadConstraintSolver.kt`: Iterative geometric constraint solver (`HORIZONTAL`, `VERTICAL`, `PARALLEL`, `PERPENDICULAR`, `TANGENT`, `CONCENTRIC`, `EQUAL`, `COINCIDENT`, `FIXED`).
3. **`cad/dimension/` — Quick Dimension & Alignment Engine**:
   - `QuickDimensionEngine.kt`: Two-pass feature graph extractor, 1D interval-graph lane allocator, 2D AABB text-collision resolver, and Quick Alignment solver.
4. **`cad/io/` & `cad/data/` — Native `.pcad` Persistence, Room Catalogue & DXF/PDF/SVG Interchange**:
   - `CadPcadPersistence.kt`: Native `.pcad` (`schemaVersion = 2`) serializer/deserializer, atomic app-private quick-save (`pcad_autosave/quick_save_working.pcad`), and explicit device saving (`Documents/PrecisionCAD/<basename>.pcad`).
   - `CadDxfInterchange.kt`: ASCII DXF (`AC1015`), plotted vector PDF (`PdfDocument`), and SVG exporter/importer.
   - `CadRoomDatabase.kt`: Local Room catalogue synchronizing project metadata, `.pcad` payloads, and DXF interchange snapshots.
5. **`ui/` — Jetpack Compose Workspace & Custom Controls**:
   - `CadWorkspaceViewModel.kt`: Single-source-of-truth `StateFlow<CadWorkspaceUiState>`, undo/redo snapshots, command state machine, and live preview synthesis.
   - `CadWorkspaceScreen.kt`: Overlay-invariant full-screen CAD viewport, separated IME command region vs. stationary controller region, and modal orchestration.
   - `ui/components/`: `CadViewportCanvas.kt`, `VirtualCursorController.kt`, `CadBinaryToggleCard.kt`, and `CadDialogsAndPanels.kt`.

---

## 2. Overlay-Invariant Viewport & Final IME Architecture

- **Viewport Stability**: `CadViewportCanvas` is mounted once at the root of the workspace `Box`. Opening or closing any overlay (`OverviewOverlayCard`, `RetractableSideToolDrawer`, `VirtualCursorControllerDeck`, or modal dialogs) never mutates `ViewportTransform` (`panScreenX`, `panScreenY`, `pixelsPerModelUnit`).
- **Separated IME Regions**:
  - **Upper Command Region (`upper_command_ime_region`)**: Contains `OverviewOverlayCard` and `ContextualCommandAndOptionsDock`. Uses `Modifier.imePadding()` to rise smoothly above the software keyboard as a single coordinated unit.
  - **Lower Stationary Controller Region (`lower_controller_stationary_region`)**: Contains `VirtualCursorControllerDeck`. Anchored at the bottom of the screen without `.imePadding()`, so opening the software keyboard never lifts, stretches, or resizes the virtual controller.
- **Command-Bar Engagement Rule**:
  - `commandBarEngaged = commandImeVisible || commandLineInput.isNotEmpty()`
  - Running an active CAD command (`LINE`, `RECTANGLE`, `CIRCLE`, etc.) never forces the Send/Cancel action-card compartment open while the keyboard is closed and `commandLineInput` is empty.
