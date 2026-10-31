# PrecisionCAD Studio — Workspace Redesign, Navigation Performance, Fit-Drawing and Export Reliability
## Repository-Aware Engineering Brief for the Coding Agent

**Parent application:** PrecisionCAD Studio  
**Target:** Android-native, strictly 2D CAD  
**Primary stack:** Kotlin, Jetpack Compose, Android Canvas / existing rendering implementation  
**Document purpose:** Standalone implementation instruction based on the supplied source archive and the user's recorded interaction requirements  
**Scope:** Workspace UX, transient notifications, touch navigation, drawing fit, PDF export, DXF export and regression validation  
**Do not:** Replace or merge this document into the existing master implementation plan unless explicitly instructed.

---

# 0. Instructions and source baseline

The user supplied:
- Git repository URL: `https://github.com/AnimeXplayXD/PrecisionCAD-Studio.git`
- Source archive: `PrecisionCAD-Studio-main.zip`
- Screenshots of the current workspace and the incorrect PDF export.

The uploaded ZIP was extracted and inspected. The source paths and findings below refer to that uploaded snapshot. Before editing, compare the working checkout with the current repository branch and report any differences. Do not assume the ZIP and remote `HEAD` are identical.

## 0.1 Existing implementation points to inspect first

| Concern | Existing source file | Relevant existing implementation |
|---|---|---|
| Main workspace layout | `app/src/main/java/com/example/ui/CadWorkspaceScreen.kt` | `CadWorkspaceScreen`, `TopEngineeringHeaderBar`, `ViewportFloatingStatusHud`, `CommandRibbonAndInputBar`, `CompactCommandLineBar`, `LandscapeLeftToolDock` |
| Workspace state and actions | `app/src/main/java/com/example/ui/CadWorkspaceViewModel.kt` | `CadWorkspaceUiState`, `updateViewport`, `updateViewportDimensions`, `zoomExtents`, command prompt state |
| Viewport gestures and rendering | `app/src/main/java/com/example/ui/components/CadViewportCanvas.kt` | `CadViewportCanvas`, pointer-input handlers, Canvas entity loop, adaptive grid |
| Viewport transforms | `app/src/main/java/com/example/cad/kernel/CadSnapAndCoordEngine.kt` | `ViewportTransform.modelToScreen`, `screenToModel`, `zoomAroundScreenPoint`, `fitToBounds` |
| Virtual cursor | `app/src/main/java/com/example/ui/components/VirtualCursorController.kt` | `VirtualCursorControllerDeck`, touchpad and joystick interaction |
| Dimension candidate generation | `app/src/main/java/com/example/cad/dimension/QuickDimensionEngine.kt` | QDIM candidate creation and feature references |
| Dimension arrangement | `app/src/main/java/com/example/cad/dimension/DimensionAlignmentEngine.kt` | dimension grouping, arrangement and annotation placement |
| Entity model and bounds | `app/src/main/java/com/example/cad/model/CadEntities.kt` | entity types, `boundingBox`, `DimensionEntity`, polyline bulges |
| PDF and DXF export/import | `app/src/main/java/com/example/cad/io/CadDxfInterchange.kt` | `exportToPdfFile`, `exportToDxf`, `appendEntityDxf`, `importFromDxf` |
| Application entry point | `app/src/main/java/com/example/MainActivity.kt` | activity/window integration |
| Theme surfaces | `app/src/main/java/com/example/ui/theme/Color.kt` | `CadPanelSurface`, `CadHudSurface`, `CadPanelElevated` |

Do not create parallel workspace, viewport, command, export, or controller systems if the existing abstractions can be extended safely.

## 0.2 Confirmed source observations

The following are directly visible in the uploaded source snapshot:

1. `CadWorkspaceScreen.kt` currently renders separate portrait and landscape arrangements. Portrait stacks the top header, viewport with floating HUD, command ribbon/input, and a large virtual-controller deck. Landscape adds a left tool dock and a fixed-width 310 dp right inspector/controller panel.
2. `ViewportFloatingStatusHud` displays `uiState.commandPromptBanner` as a persistent banner. The same state field is used for command prompts and operation feedback such as zoom-extents completion. There is no separate transient-notification state in the inspected screen/view-model path.
3. The top header uses `CadPanelSurface`, which is opaque in `Color.kt`. The floating HUD uses `CadHudSurface = Color(0xE816202E)`, which is partially transparent, and active HUD badges use a 20% alpha fill. Do not incorrectly rewrite already-opaque surfaces; make all workspace chrome surfaces consistently opaque where they overlay drawing content.
4. In `CadViewportCanvas.kt`, the transform, tap, and drag `pointerInput` handlers are keyed by `viewport`. The transform callback changes the viewport on gesture updates. This means the pointer-input coroutine keys can change during an active gesture, cancelling/restarting gesture handling.
5. `CadViewportCanvas` attaches `detectTransformGestures`, `detectTapGestures`, and `detectDragGestures` in separate pointer-input modifiers on the same viewport node. Their arbitration and consumption behaviour must be made explicit.
6. The Canvas draw block iterates the full `entities` list on each draw. It filters layer visibility while drawing but does not perform viewport-bounds culling or use a spatial query in this path. The grid renderer also iterates grid lines on every draw, with guard limits.
7. `CadWorkspaceViewModel.zoomExtents()` unions the bounding boxes of all `st.entities`, without filtering hidden/frozen layers. It calls `ViewportTransform.fitToBounds()` with the current canvas width and height and writes the result into `commandPromptBanner`.
8. `ViewportTransform.fitToBounds()` uses a fixed `paddingFactor = 0.80f`, clamps each model extent to at least 10 units, clamps viewport dimensions to at least 200 px, and centres the result using the entire supplied viewport rectangle. It does not know about internal overlay-safe areas.
9. `CadEntities.kt` computes `PolylineEntity.boundingBox()` from vertex positions only. It does not include bulge-arc extrema. `DimensionEntity.boundingBox()` includes anchor points, dimension-line point and optional angular vertex, but not the complete rendered text, arrows, leader, or extension-line extents.
10. `CadDxfInterchange.exportToPdfFile()` renders polyline vertices with `lineTo()` and ignores each vertex's bulge. It does not render standalone `ArcEntity`, `EllipseEntity`, `TextEntity`, or `LeaderEntity` in the shown entity switch.
11. The PDF dimension branch draws two lines from `anchor1` and `anchor2` to `dimensionLinePoint`. It does not construct conventional dimension geometry by projecting the two extension origins onto the dimension line and drawing the dimension line between the projected points. This produces the observed spoke/converging-line appearance.
12. `CadDxfInterchange.exportToDxf()` declares AC1015 and writes a minimal TABLES section containing LTYPE and LAYER, but no DIMSTYLE table is emitted in the inspected code.
13. DXF dimension serialization writes a simplified DIMENSION record. It does not emit the usual dimension-style reference and anonymous dimension-block representation required for robust display interoperability. Its group-code 70 mapping assigns separate values 0 through 7 to the application's dimension types; this must be checked against the actual DXF dimension-type bit flags. Horizontal and vertical rotated dimensions should not be represented as arbitrary invented dimension subtype values.
14. DXF serialization emits application-specific entity names `CENTERMARK` and `DONUT`. These are not standard DXF entity types in the form currently written. It also emits `MLEADER` while declaring AC1015; verify the target-version compatibility of every entity emitted.
15. Existing tests in the archive are mostly example tests. Add real unit, serialization, rendering, gesture and instrumentation regression tests.

These observations are a starting diagnosis, not permission to make unverified assumptions. Confirm each behaviour against the current checkout and add tests that reproduce it.

---

# 1. Overall product objective

Redesign the current CAD workspace so that it behaves like a focused technical drafting environment rather than a dashboard containing every available control simultaneously.

The viewport must become the primary work surface. Controls should appear according to the user's current task, while essential drafting state remains visible and accessible.

The redesign must preserve:
- Strictly 2D CAD scope.
- Existing command-based workflows and command aliases.
- Accurate model-space coordinates and precision.
- Grid, grid snap, object snap, orthographic mode, active layer and selection feedback.
- Dedicated virtual cursor controller with relative touchpad and velocity-sensitive joystick modes.
- Direct coordinate entry, numeric command parameters, undo/redo and QDIM.
- Adaptive portrait and landscape operation.
- Current document, geometry, dimension and unit state.
- Active command, construction points, selection, snap state, cursor model coordinates and viewport transform through layout changes.

Do not solve clutter by shrinking every control. Use hierarchy, contextual access, collapsible docks and progressive disclosure.

# 2. Workspace information architecture

## 2.1 Persistent workspace chrome

Keep only the controls needed for orientation and frequent work permanently visible:

**Top app/workspace bar**
- Project/document name, with unsaved-state indicator.
- One compact document/menu entry for New, Open, Save, Export and document settings.
- Current model/paper-space indicator.
- Undo and redo.
- One Fit Drawing action.
- Overflow menu for less frequent actions.
- Do not permanently expose the command matrix, every document setting, every QDIM setting, and every tool category in the header.

**Viewport**
- Dominant central region.
- Opaque or properly surface-backed floating status surfaces.
- Minimal persistent HUD: active command prompt, current layer, and compact snap-state indicators.
- Secondary toggles should be available through a compact status control or expandable drafting-status panel.

**Command interface**
- A compact command line that can expand when the user types, invokes a command, or needs command history.
- Preserve command aliases and coordinate input.
- Contextual command options should appear only for the active command.
- Do not display every command in the active category at once on narrow screens.

**Controller**
- Preserve the dedicated virtual cursor controller.
- Make its dock collapsible and user-repositionable where practical.
- In collapsed mode, show a compact controller handle/toolbar with cursor coordinates and the active input mode.
- Expand the full touchpad/joystick and nudge controls only when the user requests them or when the current interaction requires them.
- Do not remove the controller or silently change its input semantics.

**Inspector and layers**
- Open as a contextual side panel, bottom sheet, or compact drawer.
- Show properties when an entity is selected; show layer controls when requested.
- Avoid permanently reserving large screen area for an empty inspector on phones.

## 2.2 Portrait layout

Recommended anatomy:

1. Compact opaque top bar.
2. Large CAD viewport.
3. Small, opaque floating drafting-status surface at the viewport edge.
4. Compact contextual command strip, not a two-row permanent catalogue.
5. Command line in a collapsible bottom panel.
6. Collapsed controller dock by default, expandable into the existing full controller.

When a command is active:
- Show its prompt and relevant options.
- Expose only options relevant to that command.
- Keep coordinate input and confirm/cancel actions reachable.
- Do not display unrelated category tabs or QDIM settings.

When no command is active:
- Prioritise the viewport.
- Show a small command/search launcher and a concise set of recent/favourite tools.
- Keep the command line available without consuming the full lower screen.

## 2.3 Landscape layout

Recommended anatomy:

- Compact opaque top bar.
- Central viewport occupying the majority of width and height.
- Collapsible left tool dock with grouped Draw, Modify, Annotate, Select and Inspect tools.
- Collapsible right inspector/layer panel; do not force a fixed 310 dp controller-plus-inspector panel on every landscape device.
- Compact command line at the bottom of the viewport or in a collapsible lower dock.
- Controller dock may sit at a lower corner or be expanded as a dedicated panel.

On short landscape displays, the viewport must not be compressed by simultaneously expanded left, right, bottom-command and controller panels. Allow one or more panels to collapse and restore them without losing state.

## 2.4 Tool discovery and progressive disclosure

Use a hierarchy:

- Primary: select, draw, modify, annotate, command input, undo/redo, fit.
- Secondary: category-specific tools and current-command options.
- Tertiary: dimension style, QDIM density/arrangement, advanced snapping, constraints, import/export settings, command matrix.

The user must be able to reach every existing command. Progressive disclosure is not feature removal.

Provide:
- Searchable command launcher.
- Recent commands.
- Optional favourites.
- Context actions based on selection type.
- A command-specific options strip.
- Overflow menus for less frequent operations.

Do not invent new command semantics or remove existing command aliases.

## 2.5 Orientation and responsive transitions

When changing portrait/landscape or expanding/collapsing panels, preserve:
- `activeCommand`.
- `commandStepPoints`.
- Command sub-option and parameter value.
- Selection and isolated entity set.
- Active layer.
- Grid/snap/OSNAP/ORTHO configuration.
- Cursor model coordinate and raw cursor coordinate.
- Controller mode and step increment.
- Undo/redo state.
- Model/paper-space selection.
- Viewport scale and visual anchor.

A layout reflow must not silently call Fit Drawing or reset the viewport.

---

# 3. Surface opacity, clipping and panel layering

## 3.1 Current issue and correction

The top header's `CadPanelSurface` is already opaque. The floating HUD is not fully opaque (`CadHudSurface` is `0xE816202E`) and active status badges use an alpha fill. Inspect all overlay surfaces rather than blindly replacing the whole theme.

Requirements:
- Any header, toolbar, command ribbon, inspector, controller panel, dropdown or HUD that sits above the drawing must have an opaque surface fill or a fully opaque backing container.
- Do not allow grid lines, geometry, sheet borders or axes to visually bleed through text or controls.
- Use consistent surface elevation/border separation.
- Clip each panel to its shape.
- Respect status/navigation bar insets and display cut-outs.
- Keep intentional alpha only for drawing-space selection highlights, previews, snap markers or geometry overlays where transparency is semantically useful.
- Do not use alpha transparency as a substitute for panel hierarchy.

## 3.2 Visual hierarchy

Use the existing dark technical palette as a starting point:
- Base viewport surface.
- Opaque workspace surface.
- Slightly elevated surface for active panels.
- Hairline border or tonal elevation for separation.
- High-contrast active state for selected tools.
- Avoid excessive bright outlines around every control.

Do not redesign the CAD canvas as a consumer dashboard. Maintain a restrained technical aesthetic.

---

# 4. Transient notification system

## 4.1 Separate notification state from command prompt state

Do not continue using `commandPromptBanner` as both:
- A persistent command prompt; and
- A temporary operation-completion notification.

Introduce a dedicated transient notification state/host. Keep the active command prompt persistent for the duration of the command.

Examples of transient notifications:
- `ZOOM EXTENTS • Fitted 20 entities`
- `FILLET • Radius 10 mm applied`
- `DXF EXPORT • File created`
- `UNDO • Previous operation restored`

## 4.2 Required animation behaviour

For ordinary non-critical status messages:

1. New notification slides down into its slot with a small fade-in.
2. It remains visible for approximately 2.0–3.0 seconds; use 2.5 seconds as the default.
3. It fades and slides upward as it disappears.
4. Its occupied layout height collapses smoothly.
5. Content below the notification moves upward into the reclaimed space.
6. A subsequent notification enters from the same upper edge.

Suggested animation:
- Enter: 180–240 ms, slide vertically from `-8.dp` to `0.dp` plus alpha `0f` to `1f`.
- Hold: 2,500 ms default.
- Exit: 180–240 ms, slide from `0.dp` to `-8.dp` plus alpha `1f` to `0f`.
- Use Compose animation primitives such as `AnimatedVisibility`, `slideInVertically`, `slideOutVertically`, `fadeIn`, `fadeOut`, and `shrinkVertically`/`expandVertically` where suitable.
- Respect system animation scale and reduced-motion/accessibility settings.

## 4.3 Layout and viewport invariants

The notification's appearance or disappearance may change UI layout height, but must not:
- Change model geometry.
- Change model-space cursor coordinates.
- Reset the active command or its construction points.
- Change selection.
- Change snap settings.
- Trigger Fit Drawing.
- Alter the stored measurement values.

If the viewport canvas height changes as the notification slot expands/collapses, preserve the model-space point at the previous viewport centre (or another explicitly chosen stable visual anchor) by rebasing the viewport pan. Do not let the drawing jump merely because a notification occupied or released vertical space.

Do not treat notification layout animation as a zoom operation.

## 4.4 Queueing and coalescing

- Keep at most one visible ordinary notification.
- Coalesce repeated identical notifications, refreshing the hold timer or updating a count where useful.
- For rapid different notifications, queue a small bounded number (e.g. 3); discard or coalesce stale low-priority messages rather than creating a stack of banners.
- Errors requiring user action must not disappear after 2.5 seconds; use a persistent, dismissible error panel or dialog according to severity.
- Do not interrupt text entry or steal focus.
- Announce notifications accessibly without repeatedly announcing every animation frame.

## 4.5 Implementation guidance

Use a lifecycle-aware state holder or a dedicated notification host with a cancellable coroutine/timer. Do not create unmanaged timers inside recomposition. Cancel or replace the previous dismissal job when a new message arrives. Keep notifications independent from the CAD document model.

---

# 5. High-performance touch navigation

## 5.1 Correct the existing gesture lifetime bug

In `CadViewportCanvas.kt`, the current `pointerInput(viewport)` keys change whenever `onViewportChanged` updates the viewport. The viewport changes during `detectTransformGestures`, so the gesture coroutine can be cancelled and restarted during an active pinch/pan.

Correct this first.

Requirements:
- Gesture handlers must have stable lifetimes across ordinary viewport updates.
- Use `rememberUpdatedState(viewport)` or an equivalent current-state holder so callbacks read the latest viewport without changing the `pointerInput` key on every frame.
- Avoid stale viewport captures in tap and drag handlers.
- Do not simply change all keys to `Unit` while retaining stale captured values.
- Add a regression test or instrumentation test that performs a continuous pinch and pan and verifies that the gesture is not cancelled mid-stream.

## 5.2 Gesture arbitration

The current viewport node attaches transform, tap and drag detectors in separate pointer-input modifiers. Their competition must be resolved deliberately.

Implement one coordinated pointer-input state machine, or a clearly defined set of cooperating handlers, for:
- One-finger tap.
- One-finger drag for selection-window/crossing selection or the active drawing operation.
- Two-finger pinch-to-zoom.
- Two-finger pan.
- Optional two-finger pan without scale change.
- Stylus and external pointing-device input where supported.

Rules:
- Two-finger transform has priority when two pointers are active.
- A pinch must not accidentally start a selection rectangle.
- A drawing tap must not be consumed as a pan.
- Active drawing commands must have an explicit gesture policy.
- The virtual touchpad/joystick is a separate control surface and must not receive viewport gestures.
- Use pointer consumption consistently.
- Handle pointer count changes, cancellation, pointer-up and gesture re-entry without jumps.

## 5.3 Pinch-to-zoom focal-point correctness

Zoom around the gesture centroid.

The model-space point under the fingers before a scale change must remain under the centroid after the scale change, unless simultaneous pan intentionally moves it.

For each gesture update:
1. Resolve the current centroid in viewport-local coordinates.
2. Resolve the model-space point under that centroid using the pre-update transform.
3. Apply scale within documented minimum/maximum limits.
4. Recompute pan so that the same model point maps to the centroid.
5. Apply centroid movement/pan in a consistent order.
6. Commit the transient viewport transform for rendering.

Do not zoom around the screen centre when the user's fingers are elsewhere.

## 5.4 Rendering and state-update architecture

The current Canvas loops through every entity during drawing. Improve the path without changing CAD geometry.

- Keep model geometry immutable during pan/zoom.
- Keep viewport transformation separate from document geometry.
- Avoid rebuilding dimension candidates, snap indexes, entity topology or document metadata on every gesture frame.
- Precompute/cache static geometry paths and style information when document geometry changes, not on every pan/zoom.
- Use viewport-space culling: draw only entities whose model-space bounds intersect the visible model-space viewport, plus a small overscan margin.
- Introduce or reuse a spatial index for larger drawings. If no index exists, design a safe incremental index keyed by entity ID and update it only when geometry changes.
- Ensure entity bounds are accurate for arcs, bulged polylines, text, dimensions, leaders and blocks before using them for culling.
- Keep grid generation bounded by visible extents and pixel spacing.
- Avoid per-frame allocations of large lists, paths, paints, maps or formatted strings.
- Keep Paint and reusable path objects cached where safe.
- Use `drawWithCache` or a model-space render cache where appropriate.
- Do not perform file I/O, database access, blocking locks or heavy geometry calculations on the UI thread.
- Do not run the full Quick Dimension engine during gestures.
- Do not call `zoomExtents()` as a side effect of ordinary viewport-size changes except for a deliberate initial-fit policy.

A possible architecture is:
- Document geometry state: changes only on CAD edit transactions.
- Static render cache/spatial index: rebuilt incrementally on document edits.
- Transient viewport transform: updated during gestures.
- Frame-coalesced render invalidation: redraw at display frame cadence.
- Committed viewport transform: persisted when the gesture ends or at a safe throttled cadence.

Do not over-engineer before profiling. First remove the unstable gesture keys and measure again.

## 5.5 Performance targets

Use measurable goals rather than the untestable promise of perfect smoothness:

- Target 60 frames per second on supported 60 Hz devices during ordinary pinch/pan.
- On 90/120 Hz displays, render at the display's available cadence when feasible.
- Keep p95 input-to-visible-frame latency at or below 32 ms on the agreed reference device.
- Avoid sustained frame times above 33 ms during the representative drawing benchmark.
- No gesture cancellation, jump, scale inversion, or cursor discontinuity during a 10-second continuous pinch/pan test.
- No main-thread disk or database work during gesture processing.
- Track jank percentage, frame time, input latency, entity count, and memory allocations.

If a device cannot sustain the target on a very large drawing, degrade gracefully through culling, cached geometry and reduced non-essential overlays. Do not reduce geometric accuracy.

---

# 6. Fit Drawing / Zoom Extents redesign

## 6.1 Product behaviour

The existing top-level fit action should be presented as **Fit Drawing** or **Zoom to Drawing**, not an ambiguous centre-zoom action.

Its purpose is to centre and scale the relevant drawing content inside the actual usable viewport.

The fit calculation must include, by default:
- Visible geometric entities.
- Lines, polylines, arcs, circles, ellipses and splines.
- Visible dimensions and their complete annotation extents.
- Dimension text, arrows and extension lines.
- Leaders and multileaders, including their text.
- Text and MText.
- Centre marks and centre lines.
- Hatches and boundaries.
- Block inserts, accounting for block contents, scale and rotation.
- Other visible annotation objects that are part of the current space.

It must exclude:
- Hidden layers.
- Frozen layers.
- Entities excluded by the current isolate state.
- Entities in a different workspace space unless the user explicitly requests an all-space fit.
- Deleted or invalid entities.

Offer optional fit policies where useful:
- Fit all visible content (default).
- Fit selected objects.
- Fit geometry only (exclude annotation extents).
- Fit current space.
- Fit all visible spaces, only if the application explicitly supports that workflow.

## 6.2 Correct bounds

Do not rely blindly on current `CadEntity.boundingBox()` implementations.

Audit and correct:
- `PolylineEntity.boundingBox()`: include bulge-arc extrema, not only vertex coordinates.
- `ArcEntity.boundingBox()`: include only the actual swept arc extrema and endpoints.
- `DimensionEntity.boundingBox()`: include the full rendered annotation, including text, arrowheads, extension lines and leaders.
- `TextEntity.boundingBox()`: account for rotation, alignment and rendered width/height; replace crude estimates where practical.
- `LeaderEntity.boundingBox()`: include text layout, arrowhead and all leader vertices.
- `BlockInsertEntity.boundingBox()`: include transformed block contents, not an arbitrary fixed 10-unit envelope.
- Hatches and splines: include actual visible geometry bounds.
- Construction geometry: include or exclude according to an explicit fit policy.

The bounds engine should return a structured result with:
- Valid/empty state.
- Model-space bounding box.
- Included entity count.
- Excluded entity count and reasons where diagnostic mode is enabled.
- Whether annotations were included.

## 6.3 Usable viewport rectangle

Do not fit to the full screen. Fit to the actual drawable CAD viewport after accounting for:
- System bars and display cut-outs.
- Top workspace header.
- Persistent viewport HUD.
- Expanded command panel if it overlaps or reduces the canvas.
- Controller dock if it overlaps the viewport.
- Side panels in landscape.
- Any reserved viewport controls.

Prefer to calculate the actual Canvas bounds after layout has been resolved. If floating overlays occupy part of the Canvas, define safe insets or a safe viewport rectangle.

The drawing should be centred in this usable rectangle, not behind toolbars or under the controller.

## 6.4 Fit algorithm

For valid model bounds:
1. Calculate complete visible extents.
2. Calculate usable viewport width and height.
3. Apply configurable margins in screen dp/px, not arbitrary model units.
4. Handle near-zero width or height with a scale-safe epsilon and aspect-aware fallback.
5. Calculate the minimum scale required to fit both axes while preserving aspect ratio.
6. Clamp to documented min/max zoom.
7. Compute pan so the model-space bounds centre maps to the usable viewport centre.
8. Apply the new transform atomically.
9. Preserve cursor model coordinates and active command state.
10. Show a transient notification such as `FIT DRAWING • 20 visible entities`.

Do not use `coerceAtLeast(10.0)` as a generic replacement for degenerate geometry. A tiny point or line drawing should still fit sensibly without being treated as a 10-unit-wide object.

## 6.5 Edge cases

- Empty drawing: show a stable default origin/grid view and a brief “Nothing to fit” notification.
- Single point: use a documented default visible span around the point.
- Horizontal line or vertical line: expand only the zero-size axis by a controlled visual margin.
- Very small and very large coordinates: avoid overflow and precision loss.
- Hidden/frozen layers: do not affect Fit Visible Drawing.
- Outlier geometry: do not silently discard it. Offer Fit All Visible and Fit Selection or report the outlier if a diagnostic mode is enabled.
- Paper space: fit the current sheet/layout and its visible annotations, respecting printable area.
- Model space: fit current visible model-space content.
- Annotations: include them by default so all relevant dimensions remain visible.
- Zoom level: preserve aspect ratio; never stretch the drawing.
- UI panel changes: re-evaluate only the usable viewport rectangle and preserve the visual anchor; do not unexpectedly refit the document.

## 6.6 `ViewportTransform.fitToBounds()` correction

The current implementation:
- Uses fixed 80% padding.
- Forces width/height to at least 10 model units.
- Forces viewport dimensions to at least 200 px.
- Centres using the entire supplied viewport.

Replace with a testable fit service that accepts:
- Validated model bounds.
- Actual usable viewport rectangle.
- Screen-space margin.
- Min/max zoom.
- Degenerate-bounds policy.

Keep `ViewportTransform` as the model/screen transform abstraction. Do not embed UI-specific knowledge of the header/controller inside the geometry kernel.

---

# 7. PDF export correction

## 7.1 Confirmed defects in current source

In `CadDxfInterchange.exportToPdfFile()`:

- `PolylineEntity` is drawn by connecting each vertex with `lineTo()`. Bulge values are ignored, so curved polyline segments become straight chords.
- Standalone `ArcEntity` is not handled in the shown `when` statement.
- `EllipseEntity`, `TextEntity`, and `LeaderEntity` are not handled in the shown entity switch.
- The `DimensionEntity` branch draws two lines from `anchor1` and `anchor2` to `dimensionLinePoint`. This creates a converging/spoke-like construction instead of a conventional dimension line with correctly projected extension lines.
- The geometry fit uses a fixed fraction of the page (`drawW` 78%, `drawH` 72%) and a fixed centre offset. It does not derive a safe printable rectangle from the title block and all annotation extents.
- The page background and border are deliberately dark; preserve the chosen visual style unless the user changes it.

## 7.2 Correct PDF geometry renderer

Implement or refactor a dedicated PDF drawing backend that consumes canonical CAD entities and a single model-to-paper transform.

Required entity handling:
- LINE: correct endpoints.
- LWPOLYLINE: render each straight segment and each bulge arc. Do not approximate all arcs with straight chords.
- ARC: render exact centre, radius, start angle and sweep/orientation.
- CIRCLE: render exact centre and radius.
- ELLIPSE: render using its major/minor axes and rotation.
- SPLINE: render using a validated path approximation or exact supported curve representation.
- TEXT/MTEXT: render text, rotation, alignment, height and line breaks.
- LEADER/MLEADER: render leader vertices, arrowhead and text.
- CENTERMARK: render as standard centreline strokes.
- HATCH: render supported pattern or solid fill with clipping/boundary handling.
- BLOCK INSERT: render transformed block contents.
- DIMENSION: render complete dimension geometry using the shared dimension-render plan.

For bulged polylines, convert each bulge segment into its exact circular arc representation or a sufficiently accurate vector path derived from the bulge formula. Preserve clockwise/counter-clockwise orientation.

## 7.3 Correct dimension rendering

Do not draw both anchors to the dimension-line point.

For linear horizontal/vertical dimensions:
- Determine the measured points.
- Project them onto the dimension-line direction.
- Draw extension lines from the source points to the extension-line start/end locations.
- Draw the dimension line between the projected points.
- Place arrowheads at the measured ends.
- Place dimension text according to the active style and text-placement policy.
- Support external arrows/text when the measured span is too short.

For aligned dimensions:
- Use the actual measurement direction and its perpendicular offset.
For angular, radius and diameter dimensions:
- Use the appropriate arc, centre, leader and text geometry.
- Do not treat them as linear dimensions.

Use the same semantic dimension data and adaptive annotation render plan as the viewport wherever possible. PDF should have a backend renderer, not a separate interpretation of the dimension model.

## 7.4 Paper-space transform and layout

- Define one model-to-paper transformation and apply it exactly once.
- Respect sheet size, orientation, margins, printable area, title-block reservation and configured plot scale.
- Include dimension text, arrows and leaders in fit/layout calculations.
- Avoid title-block overlap.
- Keep physical paper dimensions correct.
- Preserve model geometry values; export scaling must not modify the document.
- Validate PDF page size and vector geometry programmatically.

---

# 8. DXF export correction and AutoCAD Mobile compatibility

## 8.1 Confirmed source concerns

In `CadDxfInterchange.exportToDxf()`:
- `$ACADVER` is set to `AC1015`.
- TABLES currently emits LTYPE and LAYER only; no DIMSTYLE table is emitted.
- `DimensionEntity` serialization is a simplified record and omits the usual dimension style reference and anonymous dimension-block representation.
- Group code 70 maps the application's eight dimension categories to integer values 0–7. This must be replaced with standards-correct DXF dimension type and flag handling.
- `CENTERMARK` and `DONUT` are written as entity type names, although these are not standard DXF entity types in this form.
- `MLEADER` is emitted despite the AC1015 declaration; verify entity-version compatibility and either raise the target DXF version or use a compatible primitive fallback.
- HATCH, INSERT, block references, linetypes, text, and other entity types require format-conformance tests, not just string generation.

## 8.2 DXF dimension records

Use the DXF specification for the declared target version.

At minimum:
- Define a valid `DIMSTYLE` table and a `STANDARD` style or the intended named style.
- Emit a valid dimension style reference.
- Emit the required dimension block reference and the graphical dimension block contents where required by the target format.
- Use correct group-code 70 dimension type and flag bit semantics.
- Horizontal and vertical linear dimensions should be represented using the correct rotated/linear dimension subtype with the appropriate rotation angle, not arbitrary dimension subtype values.
- Aligned dimensions must use the aligned subtype.
- Diameter, radius, angular and ordinate dimensions must use their correct DXF dimension semantics.
- Ensure extension-line origins and dimension-line placement are semantically correct.
- Keep actual measured values separate from display text overrides.
- If a native dimension cannot be represented reliably, offer a standards-based primitive export mode using LINE, ARC, SOLID/filled arrow geometry where appropriate, and TEXT/MTEXT. Label the mode accurately; do not emit malformed DIMENSION entities.

Do not assume a DXF file is valid because it has the `.dxf` extension or contains a final EOF marker.

## 8.3 Standard entity fallback

Do not emit application-specific entity names as though they were standard DXF records.

Examples:
- CENTERMARK: export as standard LINE entities using the correct centreline style.
- DONUT: export as concentric circles or a properly defined solid/hatch representation, according to the actual entity semantics.
- MLEADER: use a compatible DXF version or a standards-compatible LEADER plus MTEXT fallback.
- Unsupported hatch or block features: preserve what can be represented and explicitly report unsupported content; never silently write invalid entity structures.

## 8.4 Structural conformance

Validate:
- Header variables and declared version.
- Section ordering and terminators.
- TABLES records and entry counts.
- LTYPE, LAYER, STYLE, DIMSTYLE and other required tables.
- BLOCKS, BLOCK, ENDBLK and INSERT consistency.
- Entity group codes and required coordinate pairs.
- Handles and owner/reference relationships where required by the target version.
- Numeric finiteness; reject NaN and infinity.
- Units and coordinate scaling.
- Text encoding.
- EOF and file encoding.
- Entity support for the declared DXF version.

Do not hand-roll complex DXF structures without a conformance test suite. If a vetted DXF library can be adopted without unacceptable licensing, footprint or maintenance cost, evaluate it; otherwise isolate the writer and validate against authoritative DXF specifications and independent parsers.

## 8.5 AutoCAD Mobile validation

The export is not complete until:
- The DXF opens in AutoCAD Mobile on a real target device, or the agent clearly reports that this exact test could not be performed.
- The same file opens in at least one independent desktop CAD/DXF reader.
- The drawing's units, bounds, layers, arcs, circles, polylines and annotations are inspected after opening.
- The agent records the tested application/version and any unsupported features.

Do not claim AutoCAD Mobile compatibility solely because an independent parser accepts the file.

---

# 9. Cross-format consistency contract

All three representations must derive from the same canonical document state:

1. Native viewport.
2. PDF export.
3. DXF export.

Shared semantics:
- Entity geometry.
- Layer visibility and style.
- Model units and coordinate values.
- Dimension anchors and measurement semantics.
- Dimension line placement and annotation style.
- Text, leader and centre-mark references.
- Block transforms.

Backend-specific responsibilities:
- Viewport: screen rendering and interaction overlays.
- PDF: paper-space vector rendering and plot scale.
- DXF: standards-compliant entity serialization.

Never independently infer dimension anchors in each exporter. Never change geometry to compensate for a renderer defect.

---

# 10. Accessibility and touch ergonomics

- Keep touch targets comfortably reachable; aim for at least 48 dp for primary touch actions where practical.
- Avoid dense rows of tiny pill controls.
- Preserve accessible labels for icon-only actions.
- Communicate active snap modes and controller modes through text or semantic descriptions, not colour alone.
- Support TalkBack focus order through the workspace.
- Keep command input accessible when panels collapse.
- Ensure the controller can be operated one-handed where practical without obscuring the active drawing.
- Respect system font scaling without allowing the workspace to overflow.
- Respect reduced-motion settings for notification and panel transitions.

---

# 11. Implementation phases

## Phase 0 — Repository verification and baseline

1. Compare the uploaded archive with the current Git branch.
2. Identify the active app entry point and all callers of the affected methods.
3. Record current build/test status.
4. Capture screenshots and benchmark traces before changing code.
5. Create reproducible test drawings for the reported failures.

**Exit condition:** Baseline behaviour and exact source revision are documented.

## Phase 1 — Critical gesture fix

1. Remove viewport-dependent pointer-input key restarts.
2. Introduce stable gesture handler lifetimes.
3. Resolve transform/tap/drag arbitration.
4. Preserve focal-point zoom.
5. Add gesture regression tests.
6. Benchmark before and after.

**Exit condition:** Continuous pinch/pan works without cancellation, jumps or selection interference.

## Phase 2 — Fit Drawing correctness

1. Correct entity bounding boxes, including bulge arcs and annotation extents.
2. Filter hidden/frozen/isolate states.
3. Compute the actual usable viewport rectangle.
4. Replace the simplistic fit calculation with a testable fit service.
5. Preserve visual anchor when panels or notification slots change size.

**Exit condition:** Fit Drawing includes visible geometry and dimensions without clipping or covering controls.

## Phase 3 — Notification and workspace redesign

1. Separate transient notification state from command prompt state.
2. Implement timed slide/fade/expand-collapse behaviour.
3. Make all workspace overlay surfaces opaque.
4. Reorganise portrait and landscape workspace around the viewport.
5. Add contextual command panels and collapsible controller/inspector docks.
6. Preserve command and viewport state during all layout transitions.

**Exit condition:** The workspace is uncluttered, drafting remains accessible, and notifications disappear without changing CAD state.

## Phase 4 — PDF export correction

1. Implement exact bulge-arc rendering.
2. Add missing entity renderers.
3. Correct dimension-line construction.
4. Correct page transform and title-block-safe fit.
5. Add PDF vector regression tests.

**Exit condition:** PDF geometry and annotations match the native document at the intended scale.

## Phase 5 — DXF export correction

1. Correct dimension subtype/flag mapping.
2. Add valid dimension styles and dimension block representation.
3. Replace non-standard entities with standard fallback geometry.
4. Verify target DXF version compatibility.
5. Add structural validation and independent parser tests.
6. Test in AutoCAD Mobile when available.

**Exit condition:** DXF opens in verified readers and preserves supported geometry and annotations.

## Phase 6 — Full regression and performance validation

Run the full test matrix, inspect portrait/landscape screenshots, and compare native/PDF/DXF results. Record remaining limitations.

---

# 12. Mandatory acceptance tests

## Workspace and notification tests

1. Portrait workspace displays a dominant viewport without permanently exposing every command.
2. Landscape workspace remains usable on short-height screens.
3. Top/header/HUD/command/controller surfaces do not show grid or geometry through their backgrounds.
4. `ZOOM EXTENTS` feedback appears as a transient notification.
5. Notification disappears after approximately 2.5 seconds.
6. Notification exits upward with fade; content below reclaims the space smoothly.
7. A new notification enters by sliding down.
8. Repeated notifications coalesce; error notifications remain until acknowledged.
9. Notification animation does not change geometry, active command, cursor model coordinate, selection, snap state or zoom scale.
10. Orientation changes and panel collapse preserve command and viewport state.

## Gesture tests

11. Pinch zoom keeps the model point under the pinch centroid stable.
12. Two-finger pan works without accidental selection.
13. One-finger drawing/tap/selection behaviour remains correct.
14. Gesture coroutine does not restart during a viewport update.
15. Rapid zoom in/out does not create jitter, scale jumps or input loss.
16. Ten-second continuous pinch/pan test has no gesture cancellation.
17. Touchpad and joystick interaction remain independent of viewport gestures.
18. Large drawings use culling/cache and do not require a full expensive document recomputation per frame.

## Fit Drawing tests

19. Fit includes visible geometry and dimensions.
20. Fit excludes hidden and frozen layers.
21. Fit respects isolate state.
22. Fit includes bulged-polyline arc extents.
23. Fit includes dimension text, arrows, leaders and rotated text.
24. Fit handles a point, horizontal line, vertical line, empty drawing and degenerate bounds.
25. Fit centres the drawing in the usable viewport, not behind controls.
26. Fit preserves model geometry and cursor coordinates.
27. Fit in paper space respects sheet and title-block/printable-area boundaries.

## PDF tests

28. A rounded rectangle with bulge fillets exports with true curves, not straight chords.
29. Standalone arcs and all supported entity types render.
30. Linear dimensions have two extension lines and one correctly projected dimension line.
31. Diameter/radius/leader annotations render with correct references.
32. Text and leaders are not omitted.
33. PDF page size, orientation and plot scale are correct.
34. Title block does not overlap drawing content.
35. PDF vector bounds and geometry match canonical model-space geometry after applying the export transform.

## DXF tests

36. DXF section structure and tables pass an independent parser.
37. Dimension styles and dimension block references are valid for the declared DXF version.
38. Horizontal/vertical/aligned/angular/radius/diameter/ordinate dimensions use correct DXF semantics.
39. No unsupported `CENTERMARK` or `DONUT` custom entity record is emitted in the standard profile.
40. All emitted entity types are supported by the declared DXF version.
41. Fillet arcs, circles and polylines retain correct coordinates and radii.
42. DXF opens in an independent CAD reader.
43. AutoCAD Mobile opening is tested and recorded where device access is available.

---

# 13. Required agent report

Before broad changes, report:
- Exact source revision inspected.
- Files and methods responsible for each defect.
- Which findings are confirmed and which remain hypotheses.
- Proposed minimal changes and any schema/API changes.

After implementation, report:
- Files changed.
- Root cause fixed for each issue.
- Tests added and exact test results.
- Before/after gesture performance metrics.
- Before/after workspace screenshots in portrait and landscape.
- PDF and DXF validation results.
- Actual AutoCAD Mobile test result, if performed.
- Known limitations and unsupported DXF entities.

Do not claim a fix based only on code compilation. Do not claim performance improvement without measurement. Do not claim file compatibility without opening or independently validating the output.

**Definition of done:** The workspace is focused and responsive; transient notifications behave as specified; pinch/pan is stable and measured; Fit Drawing includes visible geometry and annotations in the usable viewport; PDF exports preserve curves and correctly construct dimensions; DXF exports use valid, version-compatible standard entities and open in the stated target readers.


---

# 14. Authoritative Interaction Contract — Navigation-Only Drawing Surface and Contextual Controls

**This section is a user-confirmed refinement and takes precedence over any earlier wording in this brief that could be interpreted as allowing direct drawing, tapping, dragging, or selection on the CAD viewport.**

The CAD sheet/viewport is to become a **navigation-only touch surface**. CAD operations must be initiated and controlled through the command line, the plus (`+`) action launcher, the command-specific option strip, and the independent virtual cursor controller.

This is a deliberate product interaction decision, not a temporary workaround.

## 14.1 Absolute rule: no direct CAD operation on the sheet

The drawing viewport must accept only these touch navigation gestures:

1. **Pinch with two fingers:** zoom in and zoom out.
2. **Move with two fingers:** pan/reposition the visible drawing.
3. **Combined two-finger pinch and movement:** zoom around the two-finger centroid while panning.

All other direct input on the sheet is forbidden:
- No single-finger tap to select an entity.
- No single-finger tap to pick a point.
- No single-finger drag to draw a line, move geometry, create a selection window, or pan.
- No long press or context menu on geometry.
- No tap-to-start a command.
- No drag handles or direct geometry manipulation.
- No one-finger selection, crossing selection, window selection, or grip editing.
- No direct OSNAP target acquisition by touching the sheet.
- No double-tap shortcut on the drawing.
- No stylus tap/drag operation on the sheet.
- No mouse click/drag operation routed as a CAD editing action from the sheet.

Do not silently preserve legacy viewport selection or drawing gestures. The viewport gesture layer must explicitly consume or ignore disallowed pointer sequences without forwarding them to CAD command handlers.

Hardware keyboards may continue to enter commands and coordinates through the command line. The ban applies to direct interaction with drawing content, not to the separate command input controls.

### Allowed viewport gesture recogniser

Use a single, coordinated navigation recogniser or equivalent explicit state machine:
- `Idle`
- `OnePointerSuppressed`
- `TwoPointerTransform`
- `Cancelled`

A one-pointer sequence must never enter a CAD editing state. It may be ignored or consumed solely to prevent parent gesture handlers from misinterpreting it.

When the second pointer arrives:
- Begin a two-pointer navigation gesture.
- Record the initial centroid, pointer separation, and current viewport transform.
- Use centroid movement for pan.
- Use separation ratio for zoom.
- Keep the model-space point under the gesture centroid stable during scaling.
- Apply pan and scale as one coherent transform update.
- Handle pointer-up, pointer-count changes and cancellation without jumps.

If one pointer remains after a two-pointer gesture, do not automatically reinterpret the remaining finger as a drawing or selection gesture. End the transform and wait for all pointers to lift before accepting a new two-pointer gesture.

No command, selection or point-pick code may subscribe directly to viewport taps or drags after this change.

### Point acquisition without touching the sheet

A command that requires a coordinate must use the dedicated cursor workflow:
1. Start the command through the command line or `+` action launcher.
2. The active command displays a prompt, such as `LINE • Specify first point`.
3. The virtual cursor controller moves the CAD crosshair in model space.
4. The coordinate HUD displays the current cursor coordinates.
5. The user presses **PICK PT** to submit the current coordinate to the active command.
6. The command advances to its next prompt.
7. Repeat until the user confirms, finishes or cancels the command.

The cursor crosshair may be drawn over the viewport as a visual indicator, but its location must be changed only by the virtual controller, direct coordinate entry, command expressions, or other explicit non-canvas input. Touching the crosshair on the sheet must do nothing.

Use the existing `VirtualCursorController` and command-input infrastructure where possible. Do not create a second, competing point-acquisition system.

### Selection and enquiry without sheet taps

- Provide Select commands and selection filters through the `+` launcher and command line.
- Provide `SELECT`, `SELECT ALL`, window/crossing selection commands, filters, layer-based selection and entity-type selection through command-driven workflows.
- Since direct viewport window/crossing gestures are prohibited, implement geometric selection regions by numeric coordinate entry or by controller cursor positioning followed by **PICK PT** at the required corners.
- Provide entity inquiry through commands, selected-object lists, layer lists and the properties inspector.
- A selected entity must be identifiable through its highlight in the viewport and its properties panel; the user must not need to tap it to inspect it.
- Provide explicit Clear Selection and Select Previous options.

If a future product decision reintroduces direct sheet selection, it must be an explicit, separately configurable interaction mode. It must not be enabled by default and must not weaken the navigation-only contract in this implementation.

## 14.2 Contextual command line and suggestions

The command line, suggestion strip and command-specific option strip must no longer remain permanently expanded.

### Idle state

When no command is being typed or executed:
- Hide the full command-line input panel.
- Hide the suggestion strip.
- Hide command-specific options.
- Do not display a permanent row of unrelated command buttons above the command line.
- Retain a compact, accessible command-entry affordance, such as a `CMD` handle/button or the existing `+` launcher.
- Do not steal viewport height for empty command controls.

The user can open command entry from the compact command affordance, use the `+` launcher, or focus the command line with a keyboard shortcut where supported.

### Typing/autocomplete state

When the user opens command entry and types a prefix:
- Show only matching command suggestions.
- Resolve aliases through the existing command registry (`CADCommandMatrix`, `CadCommandSpec`, and existing command alias definitions).
- Rank an exact alias above partial-name matches.
- Do not show every command in the application.
- Keep suggestions directly related to the current query.

Example:
- Query: `L`
- Exact alias resolution: `LINE`, if `L` is registered as its alias in the current command registry.
- Suggestions: show the exact `LINE` command and only relevant prefix/alias matches.
- Do not show unrelated `MOVE`, `CIRCLE`, `TRIM`, QDIM or dimension options.

Typing a prefix must not execute a command by accident. Execute only after an explicit action, such as selecting a suggestion or pressing Enter according to the command-line convention.

### Active-command state

After `LINE` is invoked:
- Replace general suggestions with the active LINE prompt.
- Show only LINE-specific options.
- Examples: first-point/next-point prompt, coordinate input, `PICK PT`, ORTHO override if relevant, polar/tracking override if relevant, `Undo`, `Close`, `Finish`, `Cancel`.
- Do not show Modify, Select, QDIM or circle-specific controls while LINE is active.
- When the command asks for a point, expose the coordinate HUD and point-acquisition controls.
- When the user enters a command option, show only the options valid for the current command step.

When another command is invoked, replace the contextual controls with that command's controls. When the command completes or is cancelled, return to the idle collapsed state and hide its temporary option strip.

### Command option metadata

Use the existing command specification/registry as the source of truth. Extend command metadata where necessary to describe:
- Command category.
- Prompt and current command step.
- Accepted aliases.
- Valid command options.
- Required point/number/text parameters.
- Whether point acquisition is required.
- Whether the virtual controller should be offered.
- Contextual action labels and keyboard shortcuts.

Do not hardcode a separate UI command catalogue for LINE, CIRCLE, MOVE, TRIM and other commands. The UI should render controls from the active command specification.

### Suggestion strip behaviour

- Hidden when command entry is idle.
- Visible only while typing or when the active command has context-sensitive suggestions/options.
- Changes immediately when the query changes.
- Disappears when the query is cleared or the command is accepted, unless the active command requires a prompt/options row.
- Must not continue displaying stale suggestions from a previous command.
- Must not overlap or cover the command input or virtual controller.

## 14.3 Retractable command-category ribbon

The current lower category ribbon exposes Draw, Modify, Select/Inquiry and Annotate controls as a long, crowded row. Replace its permanently expanded presentation with a retractable tool drawer.

### Collapsed state

- Show a narrow side-mounted handle/rail with a clear chevron/arrow.
- The handle indicates whether the tool drawer is collapsed or expanded.
- Keep the majority of the viewport unobstructed.
- Do not retain the full category row as a transparent strip behind the handle.
- The collapsed handle must have a reliable touch target and accessible label, such as `Expand tools`.

### Expanded state

When the user activates the arrow:
- Reveal the existing category line and its available options in a fully opaque, properly layered panel.
- Preserve the current categories and commands; this is a presentation change, not feature removal.
- Allow the user to select a category such as Draw, Modify, Select & Inquiry, or Annotate.
- Show tools relevant to the selected category.
- Collapse the drawer when the user selects a command if that improves viewport space; use a predictable preference or policy and do not interrupt command execution.
- Provide a visible collapse arrow/handle.

### Position and animation

- Portrait: use a side-edge drawer/rail that does not consume a full permanent horizontal row. The expanded panel may overlay the viewport with an opaque surface, or occupy a defined dock region, but it must not create an unstable viewport jump.
- Landscape: use a retractable side dock where practical.
- Use a short, restrained slide animation, approximately 180–240 ms.
- Clip the drawer to its surface.
- Preserve selected category and scroll position while opening/closing.
- Keep the drawer separate from the command-specific option strip.

The drawer arrow is a navigation control, not a CAD geometry input.

## 14.4 Collapsible virtual cursor controller dock

The full virtual controller must not remain permanently expanded at the bottom of the screen.

Implement two primary states:

1. `COMPACT_DOCK`
2. `EXPANDED_CONTROLLER`

Retain the user's last state where practical, but allow explicit show/hide control.

### Compact dock appearance

When collapsed, show one compact horizontal bar containing:
- Touchpad mode selector.
- Joystick mode selector.
- Current X coordinate.
- Current Y coordinate.
- Controller configuration/settings control.
- A dedicated drag handle or chevron indicating expand/collapse.

This is the only persistent remnant of the controller. Hide the large touchpad/joystick active area, nudge arrows, step-size buttons, PICK PT, accept and cancel controls while collapsed.

The coordinate display must remain live and accurate even while the detailed controller is collapsed. The mode selector and configuration controls remain available from the compact bar.

### Swipe-down collapse

The user can swipe downward on the controller's dedicated header/drag handle to collapse the full controller:
- Start the drag only from the handle/header region, not from the touchpad/joystick interaction surface.
- Use a vertical drag threshold and velocity to decide whether to collapse.
- Animate the full controller into the compact bar at the bottom.
- Do not interpret a downward drag inside the touchpad as a panel-collapse gesture.
- Preserve controller mode, cursor coordinates, nudge increment and configuration.

### Swipe-up expansion

The user can swipe upward from the compact bar/handle to expand:
- Animate the panel from the bottom.
- Restore the currently selected input mode, Touchpad or Joystick.
- Restore the previous nudge increment and cursor configuration.
- Show the full control surface and associated point-acquisition controls.

The compact bar should also support an explicit chevron/button so expansion is discoverable and accessible without relying exclusively on a swipe gesture.

### Expanded controller contents

The expanded controller can show:
- Touchpad/Joystick mode selector.
- Current X/Y coordinate HUD.
- Configuration/settings.
- Step-size increments.
- Nudge controls.
- Relative touchpad or velocity joystick surface.
- **PICK PT** action.
- Confirm/accept and cancel actions when valid for the active command.

Do not show accept/cancel actions when there is no active operation that uses them.

### Point-acquisition integration

When a command requires a point:
- Display the current point prompt near the command line.
- Display the coordinate readout in the compact dock and expanded panel.
- Allow **PICK PT** only when the active command is awaiting a point.
- On PICK PT, submit the current virtual cursor model coordinate to the command.
- Disable or relabel PICK PT when no point input is expected.
- If the controller is collapsed during point acquisition, the command remains active and the cursor remains at its current coordinates. The user can expand it when ready.
- Do not force the controller to expand unexpectedly unless a user-configurable `Auto-expand controller for point prompts` setting is deliberately enabled.

### Gesture arbitration in the controller

The controller is a separate interaction surface and is exempt from the navigation-only sheet restriction.

- Touchpad movement may move the virtual cursor according to its existing relative-touchpad semantics.
- Joystick deflection may move the virtual cursor according to its existing velocity-sensitive semantics.
- Controller header vertical swipe controls dock expansion/collapse.
- Controller body movement must never be mistaken for a panel swipe.
- PICK PT, configuration, mode switching and nudge buttons operate only inside the controller UI.

## 14.5 `+` action launcher and command workflow

The plus (`+`) option is the primary graphical entry point for CAD actions when the user does not want to type a command.

It should open a focused action launcher, not a permanently expanded list of all tools.

Organise it around:
- Draw.
- Modify.
- Select.
- Inquiry/Properties.
- Annotate/QDIM.
- Document/Export, where appropriate.

Within a category:
- Search commands.
- Show recently used/favourite commands.
- Select a command to activate its prompt.
- Render only that command's contextual controls.
- For point-based commands, route every point through the virtual cursor and PICK PT flow.
- For numeric/text commands, collect values through command input fields.
- For entity selection, use command-driven selection filters/lists/coordinate windows, not direct sheet taps.

The `+` launcher and command line must invoke the same command registry and execute the same command workflow. They must not implement separate command behaviour.

## 14.6 Workspace UI state model

Represent the presentation state explicitly rather than inferring it from unrelated booleans.

Suggested conceptual state:

```kotlin
enum class ControllerDockState {
    COMPACT_DOCK,
    EXPANDED_CONTROLLER
}

enum class ToolDrawerState {
    COLLAPSED,
    EXPANDED
}

sealed interface CommandPanelState {
    data object Hidden : CommandPanelState
    data class Editing(val query: String) : CommandPanelState
    data class Running(
        val commandId: String,
        val promptId: String,
        val options: List<CommandOption>
    ) : CommandPanelState
}

enum class ViewportGestureState {
    IDLE,
    ONE_POINTER_SUPPRESSED,
    TWO_POINTER_TRANSFORM,
    CANCELLED
}
```

Keep these UI states separate from the CAD document model.

Required state invariants:
- `ViewportGestureState.ONE_POINTER_SUPPRESSED` cannot dispatch a CAD command or selection action.
- Only `TWO_POINTER_TRANSFORM` changes the viewport through direct sheet gestures.
- Command execution occurs through the command registry, command line or plus launcher.
- A point is accepted only through PICK PT, numeric coordinate input, or another explicit non-sheet command input.
- Controller collapse/expansion does not cancel an active command.
- Tool drawer collapse/expansion does not clear selection or reset the viewport.
- Command panel visibility does not change the CAD document.
- Notification state remains independent of command-panel state.

## 14.7 Layout and viewport stability during panel animation

Expanding and collapsing the tool drawer, command line, suggestion strip, notification, or controller can change the available viewport rectangle. Prevent the drawing from appearing to jump.

For each layout transition:
1. Capture a stable model-space anchor under a chosen screen-space anchor (preferably the previous usable viewport centre).
2. Allow layout to resolve the new viewport bounds.
3. Recompute the viewport pan needed to keep that model-space anchor at the chosen visual location.
4. Apply the rebase without changing model geometry or scale unless the user explicitly performs zoom.
5. Do not call Fit Drawing automatically.
6. Preserve cursor model coordinates and active command state.

If a panel overlays the viewport rather than resizing it, maintain an explicit usable viewport rectangle for Fit Drawing and UI collision avoidance. Keep overlay panels opaque.

Avoid animating the drawing transform and panel size independently in ways that create a visible drift. Define one transition coordinator for panel/viewport layout changes.

## 14.8 Updated acceptance tests — navigation-only and contextual operation

Add these tests to the existing acceptance suite:

### Viewport input restriction

44. A one-finger tap anywhere on the sheet does not select, inspect, draw, or modify geometry.
45. A one-finger drag anywhere on the sheet does not pan, draw, move geometry, create a selection region, or trigger a command.
46. Long press and double tap on geometry have no CAD effect.
47. A two-finger pinch zooms about the pinch centroid.
48. A two-finger translation pans/repositions the drawing.
49. Combined two-finger pinch and translation performs stable simultaneous zoom and pan.
50. When a two-finger gesture loses one pointer, the remaining pointer does not become a drawing/selection drag.
51. Touch input on the controller and command UI continues to work normally.

### Command line and contextual options

52. In the idle state, the full command line and suggestion strip are hidden.
53. Opening command entry shows the input field without exposing unrelated command categories.
54. Typing `L` shows only command-registry matches for that prefix/alias, with LINE ranked according to the registered alias.
55. Invoking LINE displays the LINE prompt and only LINE-relevant options.
56. No unrelated Modify, QDIM, Circle or selection buttons remain visible during the LINE command unless they are global cancel/undo controls required by the command framework.
57. Completing or cancelling LINE returns the interface to its idle collapsed state.
58. The `+` launcher and typed command entry invoke the same command implementation.
59. All point prompts accept coordinates through the virtual cursor/PICK PT or numeric input; sheet taps do nothing.

### Retractable category ribbon

60. The category ribbon is collapsed to a side handle in its idle state.
61. Activating the arrow expands the opaque category drawer.
62. Selecting a command exposes only the selected command's relevant options.
63. Collapsing the drawer does not clear the command, selection or viewport state.
64. Portrait and landscape layouts both provide reachable expand/collapse controls.

### Controller dock

65. The controller can be collapsed by swiping down on its header/drag handle.
66. A downward swipe inside the touchpad surface moves the cursor and does not collapse the dock.
67. The compact bar displays Touchpad, Joystick, X, Y and configuration controls.
68. Swiping upward from the compact handle expands the controller.
69. Expansion restores the previous input mode, step increment and configuration.
70. PICK PT is visible/enabled only when appropriate for the active command.
71. Collapsing the controller does not cancel a point-acquisition command or reset cursor coordinates.
72. The controller's collapsed/expanded state survives ordinary recomposition and orientation changes according to the documented policy.

### Layout stability

73. Opening and closing the command line, category drawer, controller and notification does not change model geometry or active command.
74. Panel transitions preserve a stable viewport visual anchor and do not produce an unintended pan/zoom jump.
75. No panel background permits drawing geometry or grid lines to bleed through.

## 14.9 Updated agent implementation order

The revised priority order is:

1. Establish and test the navigation-only sheet input contract.
2. Correct the gesture handler lifetime and two-pointer gesture state machine.
3. Implement the compact/expanded controller dock and PICK PT command integration.
4. Implement the hidden/active command-line state and command-registry-backed suggestions.
5. Implement the retractable tool-category drawer.
6. Implement transient notifications and opaque panel surfaces.
7. Implement stable viewport rebasing during panel transitions.
8. Correct Fit Drawing and all entity/annotation extents.
9. Correct PDF rendering and dimension construction.
10. Correct DXF entity and dimension serialization.
11. Run the complete regression matrix and report performance/compatibility evidence.

The agent must not implement the old direct sheet-selection interaction alongside the new navigation-only policy. The new contract supersedes the older assumption that the canvas can receive drawing taps, drags or selection gestures.

---

# 15. Final interaction principle

PrecisionCAD Studio must behave as follows:

- **The sheet is for viewing and navigation only.** Two-finger pinch zooms; two-finger movement pans. No direct CAD editing or selection occurs on the sheet.
- **The command line is contextual.** It appears when needed, and its suggestions/options correspond only to the typed or active command.
- **The tool categories are retractable.** A small side handle reveals the full tool ribbon when requested.
- **The virtual controller is collapsible.** Its compact bar retains mode, coordinates and configuration; swiping upward reveals the complete controller and PICK PT workflow.
- **All point acquisition is explicit.** The virtual cursor, numeric coordinates and PICK PT submit points to the active command.
- **The viewport remains the primary work area.** Panels are opaque, contextual and retractable, and their transitions do not corrupt the viewport transform or CAD state.

The user should never need to touch drawing geometry to operate the CAD system. All geometry operations must be deliberate, command-driven and mediated through the dedicated controls.


---

# 16. Full-screen immersive operating mode

## 16.1 Mandatory application-wide behaviour

PrecisionCAD Studio must operate in **full-screen immersive mode** throughout the application. The Android status bar and navigation bar must remain hidden during normal use so that the CAD workspace occupies the available display area without persistent system-bar intrusion.

This is a product requirement, not an optional user preference or a per-screen presentation choice.

The implementation must:

- Hide both the status bar and navigation bar using Android's supported immersive system-bar APIs.
- Use immersive-sticky behaviour so that ordinary interaction with the CAD workspace does not leave the system bars exposed.
- Keep the immersive policy active across every application screen, including the drawing workspace, file/document screens, settings and other in-app destinations.
- Reapply the hidden-system-bar state when the activity resumes, regains window focus, or is recreated after a configuration change.
- Ensure Compose content, the CAD viewport, the virtual controller and retractable panels use the full available display area without reserving permanent space for hidden system bars.
- Handle display cut-outs, camera holes and edge-to-edge insets deliberately. Do not place essential controls beneath cut-outs or unsafe display regions.
- Prevent a transient system-bar appearance from causing the CAD viewport to resize, refit, jump, or alter its model-to-screen transform. The viewport anchor, zoom, pan, cursor coordinates, active command and selection state must remain unchanged.
- Verify that transient system bars return to the hidden state automatically after the system's supported transient-reveal interval.

## 16.2 Accidental-reveal prevention and Android system boundaries

The application must minimise accidental system-bar reveals during drafting and navigation. Use Android's immersive-sticky behaviour rather than a non-sticky hide operation, and test the experience with the device's actual edge-gesture navigation enabled.

Do not implement deceptive overlays, accessibility workarounds, or unsupported techniques intended to intercept or disable Android system gestures. Android retains control over protected system interactions. A deliberate system-edge gesture may still reveal transient system UI or invoke system navigation, depending on the Android version and device configuration. PrecisionCAD Studio must not claim that an ordinary application can permanently disable the notification shade, emergency functions, or all system gestures.

The intended behaviour is:

1. On entering the app, both system bars are hidden.
2. Routine taps, virtual-cursor operation, touchpad movement, joystick movement, two-finger viewport pan/zoom, command entry and panel interaction do not intentionally reveal the system bars.
3. If Android reveals transient system bars after a deliberate edge gesture, they disappear again according to immersive-sticky system behaviour.
4. Their transient appearance must not modify the drawing, trigger CAD input, change the viewport transform, or cause a layout jump.
5. Returning to the app after leaving it must restore immersive mode.

If the product later requires a genuinely locked-down device experience that prevents users from leaving the app, assess Android Lock Task Mode separately. Do not silently enable kiosk/device-owner behaviour as part of this full-screen requirement.

## 16.3 Implementation guidance

For a conventional Android application, use the supported `WindowInsetsController` / `WindowInsetsControllerCompat` APIs to hide `WindowInsetsCompat.Type.systemBars()` and select the appropriate immersive-sticky transient-bar behaviour. Keep this implementation in a central activity/window policy rather than duplicating unrelated system-bar logic across individual Compose screens.

The agent must verify API behaviour against the project's actual minimum and target SDK versions and use the compatible AndroidX implementation where appropriate. Do not assume that setting a legacy fullscreen flag alone satisfies this requirement.

## 16.4 Acceptance criteria

The implementation is complete only when all of the following pass:

- [ ] Status and navigation bars are hidden on initial launch.
- [ ] Both bars remain hidden while navigating between all in-app screens.
- [ ] Routine CAD interactions do not expose system bars.
- [ ] A deliberate edge gesture produces only the transient system behaviour permitted by Android; the bars subsequently hide again.
- [ ] Returning from another app restores immersive mode.
- [ ] Activity recreation, rotation and window-focus changes do not leave system bars permanently visible.
- [ ] Transient system-bar appearance does not resize or refit the drawing viewport.
- [ ] Model coordinates, cursor coordinates, zoom, pan, active command and drawing geometry remain unchanged when system bars appear or disappear.
- [ ] Display cut-outs and unsafe edge regions do not obscure essential controls.
- [ ] The implementation does not interfere with Android emergency functions or rely on unsupported system-gesture suppression.

This requirement applies in addition to—not instead of—the existing navigation-only sheet interaction contract and the opaque, retractable workspace-panel requirements.


---

# 17. Cursor-boundary visibility and automatic viewport tracking

## 17.1 Mandatory behaviour

The virtual CAD cursor must never disappear merely because its model-space position lies outside the currently visible sheet viewport. When the user moves the cursor beyond the visible viewport boundary, PrecisionCAD Studio must automatically adjust the viewport so that the cursor remains visible.

The cursor represents a model-space position, not a point that is permanently constrained to the visible screen rectangle. The application must preserve the cursor's intended model-space coordinates while changing the viewport transform to reveal that position.

This behaviour is commonly described as **automatic viewport panning**, **edge panning**, or **cursor-following viewport tracking**. It must be implemented as a deliberate navigation behaviour, not by clamping the cursor to the viewport edge or discarding its movement.

## 17.2 Required interaction

- When cursor movement approaches or crosses a viewport edge, initiate viewport tracking so that the cursor remains visible.
- Prefer smooth, continuous viewport translation while the cursor is moving, rather than an abrupt jump or an immediate zoom change.
- If the cursor's requested model-space position is substantially outside the visible region, progressively pan the viewport to bring it into view.
- Preserve the cursor's exact model-space coordinates throughout the operation. Do not replace them with the coordinates of the nearest visible edge.
- Keep the cursor at a useful on-screen position with a configurable or consistently chosen edge margin. Avoid pinning it directly against the viewport boundary.
- When necessary to reveal a distant cursor position, zoom out in controlled increments while keeping the cursor's model-space position stable. Do not zoom out without limit.
- Once the cursor is visible with sufficient working margin, stop automatic tracking and allow normal cursor movement to continue.
- When the user moves the cursor back toward the drawing, restore ordinary viewport behaviour without a sudden scale or position discontinuity.

## 17.3 Distinguish cursor tracking from viewport navigation

The existing sheet interaction contract remains unchanged:

- The sheet viewport is navigation-only.
- One-finger taps, drags, long presses and double taps on the sheet must not initiate CAD commands, select entities, acquire snap points, edit grips or pick geometry.
- Two-finger gestures remain the direct sheet-navigation method for pan and pinch zoom.
- Drawing, selection, inquiry and point acquisition remain available through the command system, virtual cursor and `PICK PT` workflow.

Automatic viewport tracking is driven by movement of the virtual cursor controller or an explicit coordinate-entry operation. It must not reinterpret a prohibited one-finger sheet gesture as cursor movement.

The tracking system must also distinguish between:
1. **Cursor movement:** the model-space cursor position changes; the viewport follows when required.
2. **Two-finger viewport pan:** the viewport moves beneath a stationary model-space cursor, so the cursor's model-space coordinates remain unchanged.
3. **Pinch zoom:** the viewport scale changes around the gesture's focal point; the cursor's model-space coordinates remain unchanged unless the user separately moves it.
4. **Coordinate entry or PICK PT:** the cursor is assigned the requested model-space point, and the viewport follows if that point is outside the visible region.

## 17.4 Tracking algorithm and safeguards

Implement tracking in model-space terms using the current viewport transform and the actual usable viewport rectangle.

1. Convert the virtual cursor's model-space coordinates to screen coordinates.
2. Compare the resulting screen point with an inner tracking rectangle inset from the viewport edges by a suitable margin.
3. If the cursor leaves that inner rectangle, calculate the minimum viewport translation needed to bring it back toward a preferred visible region.
4. Apply the translation smoothly, using frame-time-based motion rather than a fixed displacement per frame.
5. If translation alone cannot reveal the requested point within the permitted viewport range, reduce the zoom scale gradually, subject to the configured minimum zoom.
6. Recalculate the screen projection and repeat until the cursor is visible with the required margin or a defined zoom/pan limit is reached.
7. Stop tracking when the cursor is safely inside the inner rectangle.

Safeguards:

- Do not alter model-space geometry, entity coordinates, dimensions, constraints or annotations.
- Do not alter the cursor's model-space coordinates as a side effect of viewport movement.
- Do not change the active command, command prompt, selected entities, snap settings, nudge increment or controller mode.
- Do not trigger automatic zoom-to-extents. Tracking should reveal the cursor, not fit the entire drawing.
- Do not repeatedly oscillate between panning and zooming near the boundary. Use a dead zone, hysteresis and a stable target region.
- Do not allow tracking to fight simultaneous two-finger viewport navigation. Define and test a deterministic priority/ownership policy for concurrent input.
- Respect the usable viewport after subtracting panels, controller overlays, display cut-outs and other occlusions. Do not track toward a region hidden behind an opaque panel.
- Avoid unnecessary zoom changes. Translation is the preferred response; zoom-out is a secondary measure for distant points.
- If a coordinate lies beyond a configured model-space or navigation limit, stop at the limit and provide a clear, non-blocking indication rather than allowing unbounded scale or coordinate growth.

## 17.5 Acceptance criteria

- [ ] Moving the virtual cursor beyond any viewport edge causes the viewport to follow it.
- [ ] The cursor remains visible during continued movement and does not vanish beyond the viewport.
- [ ] Ordinary nearby movement does not cause unnecessary viewport motion.
- [ ] Viewport translation is smooth and independent of frame rate.
- [ ] A distant coordinate-entry or `PICK PT` target is brought into view without changing its model-space coordinates.
- [ ] Zoom-out occurs only when translation is insufficient, and never below the configured minimum zoom.
- [ ] The cursor is kept away from the viewport edge by a useful margin.
- [ ] Tracking does not modify drawing geometry, dimensions, constraints, selection, active command or snap configuration.
- [ ] Two-finger pan and pinch retain their specified navigation semantics and do not corrupt cursor coordinates.
- [ ] The system does not oscillate, jitter or repeatedly reverse direction at viewport boundaries.
- [ ] Opaque panels and system display insets are excluded from the usable tracking region.
- [ ] The cursor remains visible after collapsing or expanding the controller and retractable tool drawer; layout changes must not reset its model-space position.

