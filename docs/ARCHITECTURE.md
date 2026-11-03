# PrecisionCAD Studio — System Architecture

## 1. Layered Package Structure

PrecisionCAD Studio is organized into five deterministic layers under `app/src/main/java/com/example/`:

1. **`cad/model/` — Domain Entities, Invariant Formatting & Immutable Value Objects**:
   - `CadEntities.kt`: Sealed 2D hierarchy (`CadEntity`) with exact IEEE-754 `Double` coordinates (`LineEntity`, `PolylineEntity`, `CircleEntity`, `ArcEntity`, `EllipseEntity`, `SplineEntity`, `RayEntity`, `XLineEntity`, `DonutEntity`, `HatchEntity`, `BlockInsertEntity`, `TextEntity`, `LeaderEntity`, `CenterMarkEntity`, `DimensionEntity`), `CadLayer`, `BlockDefinition`, and `GeometricConstraintRecord`.
   - `CadUnitsAndSheet.kt`: `CadNumberFormatter` (`Locale.ROOT` invariant formatter), `CadUnit`, `UnitChangeMode`, `PrecisionFormatConfig`, `SheetSetupConfig`, `GridAndSnapConfig`, and `VirtualControllerConfig`.
   - `CadCommandSpec.kt` & `CADCommandMatrix.kt`: `CadCommandId`, `CadCommandCategory`, and `CadCommandCompatibilityCatalog`.
2. **`cad/kernel/` — Analytical 2D CAD Kernel**:
   - `CadGeometryKernel.kt` & `CadModificationEngine.kt`: Intersection solvers, fillet/chamfer arc trimmers, offset curve generators, polygon/rectangle builders, bounding-box evaluators, and geometric constraint solvers.
   - `CadSnapAndCoordEngine.kt`: Coordinate expression parser, OSNAP candidate evaluator, zoom-adaptive Grid Snap (`computeEffectiveGridSnapInterval` and `applyZoomAdaptiveGridSnap`), strictly separated Visual Polar Tracking (`0.35°` acquire / `0.55°` release hysteresis) vs. PolarSnap (`buildPolarSnapAngles`), and viewport camera transforms (`ViewportTransform`, `UsableViewportRect`).
3. **`cad/dimension/` — Quick Dimension & Alignment Engine**:
   - `AutoDimensionEngine.kt`, `DimensionAlignmentEngine.kt`, & `AdaptiveAnnotationRenderer.kt`: Two-pass feature graph extractor, 1D interval-graph lane allocator, 2D AABB text-collision resolver, and ISO/ASME dimension render planner.
4. **`cad/io/`, `cad/data/`, & `cad/update/` — Persistence, Interchange & GitHub Release Updater**:
   - `CadPcadPersistence.kt`: Native `.pcad` (`schemaVersion = 2`) serializer/deserializer, atomic app-private quick-save (`pcad_autosave/quick_save_working.pcad`), and explicit device saving (`Documents/PrecisionCAD/<basename>.pcad`).
   - `CadDxfInterchange.kt`: ASCII DXF (`AC1015`), plotted vector PDF (`PdfDocument`), and SVG exporter/importer.
   - `CadRoomDatabase.kt`: Local Room catalogue synchronizing project metadata and `.pcad`/DXF payloads.
   - `CadAppUpdater.kt` & `CadPackageInstallerReceiver.kt`: Public GitHub Releases API client (`CadGitHubUpdateRepository`), structured metadata parser (`CadReleaseParser`), 10-point APK verifier (`CadApkVerifier`), private HTTPS staging downloader, and Android `PackageInstaller` session launcher.
5. **`ui/` — Jetpack Compose Workspace & Shared Card System**:
   - `CadWorkspaceViewModel.kt`: Single-source-of-truth `StateFlow<CadWorkspaceUiState>`, non-destructive Home/Workspace session retention (`hasActiveWorkspaceSession`, `documentRevision`, `lastExplicitSaveRevision`, `isDocumentDirty`, `PendingHomeReplacementAction`), undo/redo snapshots, zoom-adaptive grid reference scale tracking, command state machine, live preview synthesis, and update repository coordination.
   - `CadWorkspaceScreen.kt`: Overlay-invariant full-screen CAD viewport, `SaveableStateHolder` + `AnimatedContent` Home/Workspace transitions, unbordered translucent floating command dock with concealed-at-rest Send/Cancel stack (`postExchangeRevealMs = 0L`), single-source IME positioning, unsaved-drawing replacement confirmation dialog, and Home screen with top-right update indicator.
   - `ui/components/`:
     - `CadViewportCanvas.kt`: Multi-pass 2D technical canvas (Paper Space sheet base pass -> grid pass -> model geometry & dimensions -> Paper Space opaque title-block pass -> transient visual polar & snap overlays).
     - `CadBinaryToggleCard.kt`: Reusable card-stack system (`CadDeckIdleTimerState`, `CadBinaryToggleCard`, `CadOsnapModeToggleCard`, `CadHorizontalOptionCardDeck`, `CadExchangeableDualActionDeck`) with concealed rear card at rest, configurable post-exchange reveal (`0L` for command Send/Cancel stack; `2.4s` for dual-action decks), and isolated `75s` section-local idle peek.
     - `VirtualCursorController.kt`: Animated controller collapse/expand, vertical-peek mode selector, and 1:1 touchpad / velocity joystick motion engines.
     - `CadDialogsAndPanels.kt` & `CadReleaseCatalogueDialog.kt`: QDIM dialog, Selective Dimension Removal dialog, Cursor/Grid/OSNAP dialog, and Morphp-style GitHub Release Catalogue dialog with a single-position stacked Download/Install vs. Cancel action-card deck.

---

## 2. Renderer Pass Boundaries & Viewport Invariance

- **Paper Space Two-Pass Rendering**:
  1. `drawPaperSpaceSheetBase` renders the paper shadow, white sheet fill, and outer sheet margin frame *before* `drawAdaptiveGrid`.
  2. `drawPaperSpaceTitleBlockPass` renders the opaque title-block background fill (`Color(0xFF0A0F14)`), cell dividers, and zoom-independent screen-space typography (`dp.toPx()`) *after* `drawAdaptiveGrid` so grid lines never bleed through the title block.
- **Single-Source IME & Overlay Invariance**:
  - `CadViewportCanvas` never derives its usable viewport or camera transform (`pixelsPerModelUnit`, `panScreenX`, `panScreenY`) from animated IME insets, card idle peeks, dialog scroll states, or controller collapse/expand transitions.
  - `upper_command_ime_region` uses a single bottom offset (`maxOf(controllerDeckHeightPx, imeBottomPx)` without duplicate `.imePadding()`), keeping `lower_controller_stationary_region` anchored at the bottom of the screen.
