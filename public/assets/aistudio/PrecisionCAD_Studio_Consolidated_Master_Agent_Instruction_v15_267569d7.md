# PrecisionCAD Studio — Consolidated Master Agent Instruction
## Workspace, CAD interaction, controller, rendering, export and validation requirements


**Parent application:** PrecisionCAD Studio  
**Target:** Android-native, strictly 2D CAD  
**Primary stack:** Kotlin, Jetpack Compose, Android Canvas / existing rendering implementation  
**Document purpose:** One authoritative implementation instruction consolidating the former v5/v6 requirements, touchpad safety, Overview and notification motion, QDIM completeness/alignment, professional pure-black visual identity, the QDIM state-synchronisation/idempotency correction, and the document-wide Clear All Dimensions action, and the latest controller, notification, tool-browser and viewport-gesture refinements.  
**Scope:** Workspace UX, immersive full-screen mode, tool discovery, persistent command workflow, controller interaction, viewport navigation and tracking, rendering performance, drawing fit, QDIM feature completeness and annotation alignment, Overview/notification motion, professional pure-black visual identity, PDF/DXF interoperability and regression validation.  
**Document authority:** This file supersedes the separate v5 and v6 briefs. Use the precedence rules in Section 0 whenever older descriptive wording conflicts with a later user-confirmed requirement. Sections 30–33 are authoritative for Overview/status presentation, QDIM local-feature dimensions, collision-aware dimension alignment and the application theme. Sections 35 and 37 are authoritative for QDIM state, duplicate prevention and the document-wide Clear All Dimensions action.

---

# 0. Instructions and source baseline

The user supplied:
- Git repository URL: `https://github.com/AnimeXplayXD/PrecisionCAD-Studio.git`
- Latest source archive: `PrecisionCAD-Studio-main (1).zip` (the source snapshot reviewed for the v6 refinements)
- Earlier source archive: `PrecisionCAD-Studio-main.zip` (historical baseline only)
- Screenshots of the current workspace and the incorrect PDF export.

The supplied ZIP snapshots were extracted and inspected. The source paths and findings below include observations from the latest `PrecisionCAD-Studio-main (1).zip` snapshot. The separate uploaded `proj_mounting_plate.dxf` is the exact DXF fixture analysed in Section 28. Before editing, compare the working checkout with the current repository branch and report any differences. Do not assume the ZIP and remote `HEAD` are identical.


## 0.0 Master precedence and conflict resolution

This is one consolidated instruction. The contents of the former v5 brief are retained in full, and the v6 additions (the supplied DXF inspection and controller-specific refinements) are incorporated below. The following rules are the final authority whenever an earlier section uses a broader or older description:

1. **Sheet touch input:** one-finger drag pans; two-finger pinch changes zoom only. No tap, double-tap, long press, stylus click, mouse click, selection or CAD edit is accepted on the sheet. See Sections 5, 14, 18 and 21.
2. **Tool drawer:** five category tiles, independently expandable sections, invisible left-edge swipe activation, and no visible arrow/chevron in either state. See Sections 14, 18 and 19.
3. **Bottom workspace:** all controls are overlays over one stable, full-size viewport. The controller is the lowest tier, the always-visible command tier is above it, and the function/action tier is above the command tier. See Sections 19, 20 and 22.
4. **Controller modes:** touchpad mode shows only its relative touchpad as the main input surface; it does not show PICK PT, Enter, Escape, nudge arrows or step-size controls. Joystick mode retains its existing layout and behaviour. See Sections 14, 24 and 27.
5. **Touchpad safety:** a pointer-down is not movement. The cursor does not move until a deliberate drag crosses the system touch-slop threshold; the threshold-crossing movement is discarded and becomes a new baseline. A tap or double-tap that does not become a drag therefore produces exactly zero cursor displacement. See Section 27.
6. **Joystick:** do not alter its existing motion algorithm, response curve, frame integration or stop-on-release behaviour.
7. **Viewport invariance:** panel, controller, command-field, notification and drawer animation must never resize, re-centre, refit or rebase the drawing. See Sections 18 and 22.
8. **DXF sample:** the exact uploaded `proj_mounting_plate.dxf` findings and tests in Section 28 take precedence over generic speculation about why AutoCAD Mobile rejected it.
9. **PDF:** preserve the user's already-working PDF export. Do not regress it while fixing DXF.
10. **Overview and transient feedback:** the trailing guidance text must leave the function toolbar and become a separate floating Overview overlay above it. The command prompt required to complete an active command remains persistent in the command tier. All short-lived operation notifications must animate, including when one notification replaces another while the notification area remains visible.
11. **QDIM completeness:** local straight edges of closed/open polylines must be considered for dimensioning in Detailed mode, including horizontal and vertical edges, not only inclined edges. A candidate dimension must not be silently omitted merely because its orientation is orthogonal.
12. **QDIM collision resolution:** relocate the complete dimension assembly (dimension line, extension lines, arrows and text) as a coherent unit. Do not move only the text anchor and leave the dimension geometry behind.
13. **Visual theme:** use a professional, restrained interface palette with a pure-black model-space background and near-black panels. Do not revert to grey surfaces or neon-filled controls. Preserve the CAD entity/layer colour system independently of UI theme colours.
14. **Clear All Dimensions is a separate master action inside the QDIM controls:** it removes every dimension entity in the current drawing, whether automatic or manual, across drawing spaces and all layers, while preserving ordinary geometry and non-dimensional annotations. It requires explicit confirmation, clears stale QDIM overlays, sets QDIM OFF, and is one undoable transaction. See Section 37.
15. **Touchpad behaviour is reverted:** restore the prior relative-touchpad movement semantics and layout. In touchpad mode, remove only the directional nudge arrows/NUDGE label and reclaim that space for the touchpad; keep all other previously available controller controls unchanged. Do not apply the later touch-slop-gated movement design or a new velocity-acceleration curve. See Section 39.1.
16. **Notification layers:** an active command/session guidance panel belongs in a floating bottom Overview and persists until acceptance/completion or cancellation. Ordinary messages use the bottom Overview with fade-in/fade-out when it is free. A separate top transient notification is used only while the bottom Overview is occupied by an active session. See Section 39.2.
17. **Tool browser:** retain the five-category side rail, but display only the selected category's commands in the right content pane. Do not show all five category accordions in one content list. Restore a subtle translucent arrow handle in the closed state; it must not obstruct the drawing. See Section 39.3.
18. **Viewport gestures:** one-finger pan remains; two-finger pan is re-enabled and operates together with pinch zoom. Two-pointer centroid movement pans, pointer separation changes zoom, and both may occur simultaneously. See Section 39.4.

Where an older descriptive section says that a shared controller may display point-pick/nudge/accept/cancel controls in general, interpret those controls as joystick-mode controls unless the user separately approves their appearance in touchpad mode.

24. **Dynamic Input and Polar Tracking:** implement both as distinct, coordinated drafting aids. Dynamic Input displays command prompts, coordinates and distance/angle values near the virtual cursor and accepts numeric input; Polar Tracking displays temporary angular alignment paths and angle guidance from the current command base point. Neither feature may permit direct sheet taps to draw/select/pick. See Section 43.
25. **Polar Tracking is visual guidance only:** implement the temporary polar guide line/ray, angular arc and read-only angle label before point acceptance. Do not add editable coordinate, distance or angle entry fields beside the cursor. Numeric input remains in the persistent command line. See Section 45, which supersedes the Dynamic Input entry-field requirements in Section 43.
26. **Polar preview includes all visual measurements, read-only:** show live radial distance R from the accepted base point to the cursor, the actual current cursor bearing θ, and the active configured polar angle θp, alongside the guide ray and angular arc. These are labels, never editable fields. See Section 47.

21. **Latest source baseline:** use the newly uploaded `PrecisionCAD-Studio-main.zip` as the current source snapshot for implementation planning. Its ZIP comment contains `52d16959c99fc35ac7a6fb60bc148213652ba7be`; this is archive metadata, not independently verified Git history. The private GitHub repository was not visible through the current connector, so do not claim the ZIP is confirmed remote HEAD. See Section 41.
22. **Do not redo already-implemented features:** the latest source already contains immersive system-bar hiding, a pure-black palette, a stable full-screen Canvas layer, persistent command input, a shared/idempotent QDIM state, Clear All Dimensions, horizontal/vertical QDIM edge candidates, whole-dimension relocation, transparent dimension text, and core LINE/CIRCLE/ARC creation. Audit and extend these implementations instead of creating parallel systems. See Section 42.
23. **Remaining current-source UX divergences:** revert the touchpad to the previously accepted direct relative movement and hide only its nudge arrows; change the tool browser to a single selected-category pane and restore a translucent closed-state arrow; re-enable simultaneous two-finger pan plus pinch; and route notifications according to the active-session rules in Section 39. See Section 42.

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

## 5.2 Gesture arbitration — latest sheet navigation contract

The sheet is navigation-only. The current user-confirmed mapping is:

- **One finger drag:** pan the viewport.
- **Two-finger pinch:** zoom the viewport in or out.
- **One-finger tap, long press, double tap:** no CAD action.
- **Two-finger translation without a meaningful change in separation:** do not pan; two-finger movement is not the pan gesture.
- **Drawing, entity selection, inquiry, OSNAP acquisition and point picking:** never originate from a sheet touch.

This mapping supersedes earlier wording in this brief that suppressed all one-finger drags or used two-finger movement for panning.

Use one coordinated pointer recogniser or a clearly defined cooperating set of handlers. Its conceptual states must include:

- `IDLE`
- `ONE_POINTER_PAN`
- `TWO_POINTER_PINCH_ZOOM`
- `POST_PINCH_POINTER_SUPPRESSED`
- `LEFT_EDGE_DRAWER_REVEAL`
- `CANCELLED`

Rules:

1. A one-pointer drag that begins outside the reserved drawer edge zone pans the viewport. It changes only the viewport transform; it must never move the model-space CAD cursor or document geometry.
2. A one-pointer tap has no effect on CAD content.
3. When a second pointer arrives, end one-pointer pan and transition to pinch-zoom. Do not treat the two pointers' centroid translation as pan.
4. On pinch start, record the initial centroid, initial separation and viewport transform. Use separation change for scale. Use a stable focal anchor so centroid drift does not inadvertently pan the drawing.
5. When a pinch loses one pointer, suppress the remaining pointer until every pointer in that gesture is lifted. Do not reinterpret it as a new one-finger pan mid-gesture.
6. Three or more simultaneous pointers must be handled deterministically: cancel or suppress the gesture; never route it to CAD editing.
7. A horizontal swipe beginning in the reserved left-edge drawer zone is handled by the drawer recogniser, not by viewport panning.
8. All viewport pointer changes must be consumed or otherwise isolated so no tap, drag, long press or pointer-up leaks to CAD command, selection or point-pick handlers.
9. Keep pointer-input coroutine lifetimes stable across viewport updates. Use the latest transform via an updated state holder; do not key `pointerInput` to the changing viewport transform.
10. Do not add stylus, mouse or external-pointer CAD editing on the sheet. Those devices may operate explicit UI controls, but the sheet remains restricted to the two navigation gestures above.

The gesture layer must define arbitration between the left-edge drawer reveal, ordinary one-finger pan and two-finger pinch. Use movement slop, directional dominance and a reserved edge strip. Do not suppress Android's protected system-back gesture with unsupported gesture-exclusion workarounds.

## 5.3 Pinch-to-zoom focal-point correctness

The two-finger gesture is **zoom-only**. It must not translate the viewport as a side effect of the fingers' centroid moving.

- Record a stable focal anchor when the second pointer joins.
- Derive zoom from the change in distance between the two pointers.
- Apply each scale change around the stable focal anchor, or use an equivalent transform that demonstrably prevents centroid drift from producing pan.
- Clamp scale to the existing configured minimum and maximum.
- Do not change model-space cursor coordinates, drawing geometry, selection, active command or snap state.
- If the two-finger separation changes negligibly, do not apply a pan operation.
- Test pinch-in, pinch-out, small centroid drift, rapid pinch, pointer-up and cancellation on physical devices.

**One-finger pan** is implemented separately under Section 18.4. A continuous one-finger pan must not be cancelled by ordinary viewport updates.

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

## 14.1 Absolute rule: navigation only, never CAD editing on the sheet

The sheet accepts only two touch-navigation gestures:

1. **One-finger drag:** pan/reposition the viewport.
2. **Two-finger pinch:** zoom in or out around a stable focal anchor.

The following are forbidden on the sheet:
- One-finger tap to select, inspect or pick a point.
- Long press or double tap on geometry.
- Direct drawing, moving, modifying or erasing geometry.
- Selection-window or crossing-window gestures.
- Direct OSNAP target acquisition by touching geometry.
- Grip editing.
- Stylus or mouse click/drag routed as a CAD operation.

Two-finger translation alone is not a pan gesture. The viewport must not translate merely because the pinch centroid drifts. A one-finger drag is navigation only; it does not move the virtual CAD cursor.

### Allowed viewport gesture state machine

Use a coordinated recogniser with at least:
- `Idle`
- `OnePointerPan`
- `TwoPointerPinchZoom`
- `PostPinchPointerSuppressed`
- `LeftEdgeDrawerReveal`
- `Cancelled`

A gesture that begins as one-finger pan may transition to pinch when a second pointer arrives. If the pinch later drops to one pointer, suppress the remaining pointer until all pointers lift; do not resume pan mid-gesture.

Reserve a narrow, configurable left-edge strip for the tool-drawer reveal gesture. Swipes beginning in that strip are not viewport pans. Keep the drawer gesture outside Android's protected system-edge gesture region as far as practical, and do not disable Android system navigation.

### Point acquisition without touching the sheet

A command that requires a coordinate must use the explicit cursor workflow:

1. Start the command through the command line or `+` action launcher.
2. Show the active command prompt, such as `LINE • Specify first point`.
3. Move the virtual CAD cursor using the separate touchpad/joystick controller or numeric coordinate entry.
4. Display the model-space X/Y coordinate readout.
5. The user presses **PICK PT** to submit the current model-space coordinate.
6. Advance to the next prompt.
7. Finish or cancel explicitly.

The cursor crosshair may be drawn over the viewport, but touching it on the sheet must never acquire or submit its point. Use the existing `VirtualCursorController` and command-input infrastructure.

### Selection and inquiry without sheet taps

- Provide Select, window/crossing selection, filters and inquiry through the command registry, command line and `+` launcher.
- Drive selection corners through virtual-cursor positioning plus PICK PT or numeric coordinates.
- Provide entity inquiry through command-driven selection, lists, layer filters and the properties inspector.
- Keep all selection and model operations deliberate and command-mediated.

## 14.2 Persistent command line and contextual command options

The command entry bar must remain **continuously visible**. Do not make the user press a separate `CMD` affordance to reveal the input field.

### Persistent idle state

- Keep a compact command text field rendered at all times.
- Give it a clear prompt such as `Command or X,Y coordinates`.
- Do not focus it automatically on launch; the Android keyboard must not appear until the user deliberately focuses the field.
- The field remains available while the controller is expanded or collapsed.
- Do not show unrelated command buttons or an all-command list in the idle state.
- The `+` launcher remains a separate graphical command entry point.

### Typing/autocomplete state

- When the user types a prefix, show only matches from the existing command registry.
- Rank exact command aliases ahead of partial matches.
- Example: typing `L` must rank LINE as the exact alias and show only relevant matches.
- Coordinate expressions such as `125,85`, `@25,-10` and `50<45` must not be misclassified as command names.
- Typing alone must never execute a command. Require Enter or an explicit suggestion selection.

### Active-command state

- Keep the command input field visible.
- Show the active command prompt and only options valid for the current command and current command step.
- Example: when LINE is active, show the first-point/next-point prompt and LINE-specific options; do not display unrelated Modify, Select, QDIM or Circle actions.
- Expose PICK PT only when the command is awaiting a point.
- When the command finishes or is cancelled, return to the idle prompt while keeping the command field visible.

The `+` launcher and typed command entry must invoke the same command registry and same command execution pipeline. Never implement a separate graphical command engine.

## 14.3 Complete CAD tool-browser redesign

Replace the current single-active-category popup with a redesigned, opaque, retractable CAD tool browser. Preserve all commands and capabilities; this is an information-architecture and interaction redesign.

### Five category tiles

When the drawer is expanded, display five compact vertical category tiles on its side. They correspond to the five existing `CadCommandCategory` groups:

1. **Draw / 2D Geometry** — prominently include LINE and POLYGON; retain the remaining supported drawing tools.
2. **Modify & Edit** — MOVE, COPY, ROTATE, SCALE, MIRROR, OFFSET, TRIM, EXTEND, FILLET, CHAMFER, STRETCH, BREAK, JOIN, EXPLODE, ARRAY, ALIGN, LENGTHEN, PEDIT and other registered editing commands.
3. **Select & Inquiry** — SELECT, DIST/MEASURE, PROPERTIES, ISOLATE and registered enquiry/selection functions.
4. **Annotate 2D & QDIM** — QDIM, linear/aligned/angular/radial/diametric/ordinate dimensions, LEADER, TEXT and CENTERMARK.
5. **Document / Units / I/O** — LAYER, UNITS, PAGESETUP, BLOCK, CONSTRAINT, DXF/PDF, UNDO/REDO and document-level operations.

The user specifically requires LINE and POLYGON to be present in Draw. Do not remove existing drawing tools when adding or reordering these entries.

The Modify & Edit area must make Edit, Select & Inquiry, and Annotate 2D/QDIM readily discoverable as distinct functional groups. Avoid flattening every command into one undifferentiated list.

### Independent expandable/collapsible headers

- Use accordion-style section headers such as `2D Drawings`, `Modify & Edit`, `Select & Inquiry`, `Annotate 2D & QDIM`, and `Document / Units / I/O`.
- Each header expands/collapses independently.
- Multiple headers may remain expanded simultaneously.
- Opening one section must never automatically close another section.
- Store expansion state as a set of expanded section IDs, not a single `activeCategory` value.
- Preserve each section's scroll position and expansion state while the drawer is open.
- The five side tiles focus, scroll to or expand their corresponding section. They must not collapse other open sections.
- Show clear expanded/collapsed styling without changing command state.

### Edge-swipe opening and closing — no visible arrow control

Remove the current right-side arrow/chevron handle completely.

- No visible arrow box, chevron button or clickable handle is to be shown when the drawer is closed.
- No visible arrow box or chevron handle is to remain when the drawer is open.
- When the drawer is open, show the five category tiles in the side rail in place of the old arrow handle.
- Open the drawer with a horizontal inward swipe starting in a narrow reserved region along the left side of the app content.
- Close it with a horizontal outward/leftward swipe on the drawer surface. A standard Android Back action may also close it.
- Do not require the device to have an OEM-specific sidebar gesture enabled; implement the app's own gesture recogniser.
- Do not intercept Android's protected system-back or emergency gestures. Place the app gesture region safely inside system edge areas and validate on the target Android devices.
- Use horizontal-direction dominance, touch slop and a deliberate distance/velocity threshold to reject incidental diagonal or vertical movement.
- When the drawer is closed, the left-edge swipe region must not interfere with ordinary viewport panning elsewhere.
- The gesture must work in portrait and landscape.

### Presentation and animation

- The expanded drawer overlays the viewport with a fully opaque panel.
- The five category tiles sit alongside the content panel, replacing the former arrow handle.
- Keep the open panel width and content readable on narrow portrait screens; use internal scrolling rather than clipping the final commands.
- Use a restrained slide animation, approximately 180–240 ms.
- Do not resize the viewport or change the viewport transform during opening, closing, scrolling or accordion expansion.
- Retain the selected command, current command prompt, selected entities and cursor state when the drawer is dismissed.

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

### Expanded controller contents — mode-specific

The shared controller header retains:
- Touchpad/Joystick mode selector.
- Current X/Y coordinate HUD.
- Configuration/settings.
- Dedicated collapse/expand handle.

The main control body is conditional:

**Relative Touchpad mode**
- Show the relative touchpad surface as the sole main input surface.
- Hide PICK PT, Enter, Escape, directional nudge controls and the step-size selector.
- Do not leave empty layout slots for hidden controls.
- Do not register tap, double-tap, long-press or click actions on the touchpad.

**Velocity Joystick mode**
- Preserve the existing joystick surface, StepNudgePad, step-size selector, PICK PT, Enter and Escape controls exactly as currently implemented.
- Do not change the joystick motion algorithm as part of touchpad work.

Do not show accept/cancel actions when there is no active operation that uses them. The command line and existing command-level point acceptance remain available independently of the controller mode.

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

- Touchpad movement moves the virtual cursor only after the touchpad's movement-intent gate has classified the gesture as a deliberate drag; taps and double-taps do not move the cursor.
- Joystick deflection retains its existing velocity-sensitive behaviour unchanged.
- Controller header vertical swipe controls dock expansion/collapse.
- Controller body movement must never be mistaken for a panel swipe.
- PICK PT and the existing nudge/action buttons operate in joystick mode; mode switching and configuration operate only through their explicit controller UI controls.

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

Represent presentation and gesture state independently from the CAD document model.

Suggested conceptual state:

```kotlin
enum class ControllerDockState {
    COMPACT_DOCK,
    EXPANDED_CONTROLLER
}

enum class ToolDrawerState {
    CLOSED,
    OPEN
}

enum class ViewportGestureState {
    IDLE,
    ONE_POINTER_PAN,
    TWO_POINTER_PINCH_ZOOM,
    POST_PINCH_POINTER_SUPPRESSED,
    LEFT_EDGE_DRAWER_REVEAL,
    CANCELLED
}

// Persist as a set so several accordion groups can remain open.
val expandedToolSections: Set<ToolSectionId>
```

The command-panel state should model an always-present input bar plus query and active-command presentation, rather than a hidden/visible command-field toggle.

Required state invariants:
- Only one-finger sheet drag pans the viewport.
- Two-finger separation changes zoom the viewport; centroid translation alone does not pan.
- One-finger tap, long press and double tap never dispatch CAD commands.
- A one-pointer remainder after pinch is suppressed until all pointers lift.
- Drawer edge-swipe recognition cannot dispatch CAD input.
- Command execution occurs only through the registry, persistent command line or plus launcher.
- A point is accepted only through PICK PT or explicit numeric input.
- Controller collapse/expansion does not cancel an active command or alter the viewport transform.
- Drawer expansion, accordion changes and category-tile navigation do not reset selection, cursor coordinates or viewport transform.
- Function bar and command bar remain visible.
- Notification state remains independent of command and document state.

## 14.7 Overlay-only workspace layout and viewport invariance

All tool panels, command controls, notifications and controller surfaces must be overlays above a stable CAD viewport. They must not participate in a layout arrangement that changes the viewport's measured width or height when shown, hidden, collapsed or expanded.

Use one stable root container:
- The CAD viewport occupies the stable app content surface.
- The top header and status/HUD controls are overlays.
- The category drawer is an overlay.
- The function/action bar is an overlay.
- The persistent command-entry bar is an overlay.
- The controller panel is an overlay anchored near the bottom edge.
- Transient notifications are overlays.

Do not put the viewport in a `Column` with `weight(1f)` while the command dock and controller are inserted as height-consuming siblings if changing their size changes the canvas bounds.

Maintain two distinct concepts:
1. **Viewport surface bounds:** stable bounds of the CAD drawing surface. These determine model-to-screen mapping and must not change when UI overlays appear/disappear.
2. **Interaction-safe bounds:** the unobstructed region used for controller cursor tracking, hit testing and placement decisions. These may change with overlays, but changing them must not rebase the drawing.

Do not call `rebaseForUsableRectChange()` merely because the controller, tool drawer, command bar, function bar or notification changed visibility/size. Those are overlays, not a new viewport.

A genuine device-size or orientation change may require a controlled viewport remeasurement. Preserve an explicitly chosen model-space anchor and scale policy for that case; do not confuse it with panel animation.

The required result is absolute: opening or closing the controller must not slide the drawing, change its centre, zoom, pan or model-space cursor. The same invariance applies to the drawer, command field, function bar, notification and accordion expansion.

Keep the overlay surfaces opaque where they are panels. Transparent annotation text is a separate rendering rule in Section 22.

## 14.8 Updated acceptance tests — latest navigation-only and persistent workspace

### Viewport touch contract

44. A one-finger tap on the sheet has no CAD effect.
45. A one-finger drag pans the viewport smoothly and changes no geometry or cursor model coordinate.
46. Long press and double tap on geometry have no CAD effect.
47. A two-finger pinch zooms in/out around a stable focal anchor.
48. Two-finger centroid translation alone does not pan the viewport.
49. A pinch that drops to one pointer suppresses the remaining pointer until all pointers lift.
50. A left-edge inward swipe in the reserved app region opens the tool drawer and is not interpreted as viewport pan.
51. No sheet gesture selects entities, acquires OSNAP, starts a command, draws or edits geometry.

### Persistent command and function bars

52. The command text field remains visible when idle.
53. The command field is not automatically focused and does not open the software keyboard on launch.
54. Typing `L` shows only registry matches and ranks LINE as its exact alias.
55. Invoking LINE shows only LINE prompts/options while the command field remains visible.
56. Clearing or completing a command returns the same persistent bar to its idle prompt.
57. The function/action bar remains visible and contains the plus launcher, drawer status/control and contextual status.
58. The `+` launcher and command line invoke the same registered command implementation.
59. All point prompts use the virtual cursor/PICK PT or numeric input.

### Tool drawer and accordions

60. No visible arrow/chevron handle exists when the drawer is closed.
61. A reserved left-edge inward swipe opens the drawer.
62. The open drawer contains five side tiles corresponding to the five current command categories.
63. The five tiles lead to Draw, Modify & Edit, Select & Inquiry, Annotate 2D & QDIM, and Document / Units / I/O.
64. LINE and POLYGON are present under Draw.
65. Each accordion header expands independently; opening a second header does not collapse the first.
66. The five tile rail replaces the previous right-side arrow handle while the drawer is open.
67. A leftward swipe on the open drawer or Android Back closes it without a visible arrow button.
68. Drawer gestures do not interfere with ordinary pan or Android system-back gestures.

### Controller and overlay invariance

69. The controller can still be collapsed/expanded using its existing dedicated header gesture.
70. A gesture inside the touchpad surface moves the virtual cursor; it does not collapse the controller.
71. The compact controller shows Touchpad, Joystick, X/Y and settings.
72. Expansion restores the prior mode, nudge increment and configuration.
73. PICK PT appears/enables only when the active command requires a point.
74. Collapsing the controller does not cancel commands or reset cursor coordinates.
75. Opening/closing the controller does not move, resize, re-centre, pan or zoom the CAD drawing.
76. Opening/closing the drawer, command bar, function bar or notifications does not move, resize, re-centre, pan or zoom the drawing.
77. Accordion expansion and category-tile selection do not alter the viewport transform.
78. The model-space cursor remains stable during all overlay transitions.

### Annotation text and exports

79. Dimension and leader labels have no filled background rectangle in the on-screen viewport renderer.
80. Geometry remains visible beneath dimension and leader text.
81. Annotation text remains legible at small and large zoom levels through appropriate screen-space text sizing and contrast, not an opaque label mask.
82. The accepted PDF export remains visually and functionally unchanged.
83. DXF output passes structural validation and opens in independent DXF readers and AutoCAD Mobile; any reader-specific limitation is recorded with evidence.

### Layout stability

84. Portrait and landscape use the same overlay-only viewport invariant.
85. Overlays remain inside display-safe regions and do not obscure essential controls.
86. No panel transition causes a model-space geometry or screen-position jump.

## 14.9 Updated agent implementation order

1. Reconcile the current source checkout with the remote GitHub repository and identify the exact baseline commit.
2. Establish a stable root viewport surface with overlay-only workspace controls.
3. Replace the old two-finger pan/pinch recogniser with one-finger pan and two-finger pinch-only zoom.
4. Implement the reserved left-edge tool-drawer gesture with Android system-edge arbitration.
5. Replace the single-active-category popup with five side tiles and independently expandable accordion sections.
6. Keep the command field and function/action bar permanently visible.
7. Verify that controller and drawer transitions never rebase or move the drawing.
8. Remove on-screen dimension/leader label background masks while preserving readable text.
9. Integrate Compose VelocityTracker-based cursor motion where it improves controller response; benchmark before and after.
10. Audit and repair DXF dimension serialization against the AC1015 specification.
11. Preserve the PDF export behaviour already accepted by the user.
12. Run UI, gesture, annotation, export and performance regression tests on physical Android devices.

Do not reintroduce direct drawing, selection, inquiry or point-picking from the sheet. Do not treat overlay size changes as viewport-size changes.

# 15. Final interaction principle

- **The sheet is navigation-only.** One finger pans; two fingers pinch-zoom. No direct drawing, selection, inquiry, point-picking or geometry editing occurs on the sheet.
- **The CAD cursor is controlled independently.** Use the virtual touchpad, joystick, nudge controls, numeric coordinates and PICK PT.
- **The tool browser opens by edge swipe.** It has five category tiles and independently expandable accordion headers. No visible arrow/chevron handle is shown.
- **The command field is always visible.** Suggestions and options remain contextual to the typed or active command.
- **The function/action bar is always visible.** It sits above the command field in the bottom control stack.
- **All workspace controls are overlays.** Their appearance, disappearance and animation must not resize or reposition the drawing viewport.
- **Annotation labels are transparent.** Dimension and leader text has no filled background; the geometry behind it remains visible.
- **The accepted PDF export is protected from regression.** DXF export is still under investigation and must be tested independently.
- **The virtual controller remains a first-class CAD input device.** Motion sampling and velocity estimation may refine its response, but must not introduce unintended cursor drift or inertia.

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
2. Routine taps, virtual-cursor operation, touchpad movement, joystick movement, one-finger viewport pan, two-finger pinch zoom, command entry and panel interaction do not intentionally reveal the system bars.
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
- One-finger drag pans the sheet; two-finger pinch changes zoom only.
- Drawing, selection, inquiry and point acquisition remain available through the command system, virtual cursor and `PICK PT` workflow.

Automatic viewport tracking is driven by movement of the virtual cursor controller or an explicit coordinate-entry operation. It must not reinterpret a prohibited one-finger sheet gesture as cursor movement.

The tracking system must also distinguish between:
1. **Cursor movement:** the model-space cursor position changes; the viewport follows when required.
2. **One-finger viewport pan:** the viewport moves beneath a stationary model-space cursor, so the cursor's model-space coordinates remain unchanged.
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
- Do not allow cursor tracking to fight one-finger pan or two-finger pinch. Define a deterministic priority/ownership policy and suspend automatic tracking while a manual viewport gesture is active.
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
- [ ] One-finger pan and two-finger pinch-only zoom retain their specified navigation semantics and do not corrupt cursor coordinates.
- [ ] The system does not oscillate, jitter or repeatedly reverse direction at viewport boundaries.
- [ ] Opaque panels and system display insets are excluded from the usable tracking region.
- [ ] The cursor remains visible after collapsing or expanding the controller and retractable tool drawer; layout changes must not reset its model-space position.


---

# 18. Source-aware workspace redesign — 31 October 2026 source snapshot

## 18.1 Source baseline and observed implementation

The latest user-provided archive is `PrecisionCAD-Studio-main.zip`, whose ZIP comment contains this identifier:

`e7865c08db8c1be6832db8437222ea3a3087ad45`

This is archive metadata, not verified Git commit metadata; do not treat it as proof of the current remote HEAD. Before editing, connect to the GitHub repository, fetch its current default branch, identify the branch and HEAD commit, and compare it with this archive.

Source paths reviewed in this snapshot:

- `app/src/main/java/com/example/ui/CadWorkspaceScreen.kt`
- `app/src/main/java/com/example/ui/CadWorkspaceViewModel.kt`
- `app/src/main/java/com/example/ui/components/CadViewportCanvas.kt`
- `app/src/main/java/com/example/ui/components/VirtualCursorController.kt`
- `app/src/main/java/com/example/cad/kernel/CadSnapAndCoordEngine.kt`
- `app/src/main/java/com/example/cad/io/CadDxfInterchange.kt`
- `app/src/main/java/com/example/cad/model/CadCommandSpec.kt`
- `app/src/main/java/com/example/cad/model/CADCommandMatrix.kt`

The observed current implementation explains several requested refinements:

1. `RetractableSideToolDrawer` iterates `CadCommandCategory.entries` but renders only the single `uiState.activeCategory` command group at a time. Its expansion model is a single active category, not a set of independently expanded accordions.
2. The drawer always composes a 26 dp clickable handle containing an arrow icon. It therefore remains visible in both expanded and collapsed states.
3. The portrait workspace uses a `Column` with a weighted viewport followed by command and controller sections. The viewport's measured height is consequently affected by the sizes of those sibling sections.
4. `updateUsableViewportRect()` calls `rebaseForUsableRectChange()` when the usable rectangle changes. That function deliberately preserves the model point at the old usable-rectangle centre by moving it to the new rectangle centre. This behaviour is unsuitable for overlays that should not move the drawing.
5. The current viewport recogniser implements two-pointer transform with both `calculateZoom()` and `calculatePan()`, while one-pointer movement is suppressed. This conflicts with the latest user-confirmed one-finger-pan / two-finger-pinch-only contract.
6. The command dock currently shows a compact `CMD / X,Y` trigger in its idle state and reveals the `OutlinedTextField` only while editing. This conflicts with the new always-visible command field requirement.
7. `CadViewportCanvas.kt` explicitly draws dark filled rectangles behind dimension labels and leader text. These are the on-screen masks to remove under Section 22.
8. The DXF writer emits AC1015 and has LTYPE, LAYER, STYLE and DIMSTYLE tables, but the dimension entity serialization requires further standards validation described in Section 23.

These are findings from the supplied archive only. Recheck the latest connected GitHub revision before making edits; do not assume the remote code is identical.

## 18.2 Tool browser information architecture

The five category tiles must map to the existing five command categories, not create a disconnected parallel registry.

| Tile | Label | Command category |
|---|---|---|
| 1 | Draw / 2D Geometry | `DRAW` |
| 2 | Modify & Edit | `MODIFY` |
| 3 | Select & Inquiry | `SELECT_INQUIRY` |
| 4 | Annotate 2D & QDIM | `ANNOTATE_DIM` |
| 5 | Document / Units / I/O | `DOCUMENT_SYSTEM` |

Use these category IDs as stable references for the five tiles. The tile rail is visible only while the drawer is open. Each tile navigates to the corresponding category content and expands that section without collapsing any other expanded accordion.

The command list must continue to be sourced from `CadCommandId` / `CadCommandSpec` and the existing compatibility catalog. Do not create a second set of command IDs or duplicate command definitions.

## 18.3 No visible arrow or chevron

The latest user instruction explicitly rejects a visible arrow icon or arrow box for drawer activation. This supersedes the earlier request that described showing an arrow box while closed.

Final rule:
- Closed drawer: no visible arrow, handle, chevron or clickable arrow box.
- Open drawer: five category tiles occupy the side rail; no arrow handle.
- Opening: left-edge inward swipe.
- Closing: leftward swipe on the drawer surface or Android Back.
- Do not add an alternate visible arrow toggle during implementation.

## 18.4 Revised sheet gesture model

The earlier two-finger pan + pinch model is superseded. Implement:

| Gesture | Required action |
|---|---|
| One-finger tap | No CAD action |
| One-finger drag outside drawer edge zone | Pan viewport |
| Two-finger pinch | Zoom only |
| Two-finger centroid translation without pinch | No viewport pan |
| Long press / double tap | No CAD action |
| Left-edge inward swipe in app drawer zone | Open tool drawer |
| Leftward swipe on open drawer | Close tool drawer |

The pinch must not accidentally pan due to centroid movement. A fixed gesture-start anchor or a mathematically equivalent zoom-only transform is required. Manual one-finger pan suspends automatic cursor tracking until the gesture finishes.

## 18.5 Implementation invariants for UI overlay transitions

The drawing's screen transform must be invariant under all of the following:
- Controller expanded -> compact.
- Controller compact -> expanded.
- Tool drawer closed -> open.
- Tool drawer open -> closed.
- Accordion collapsed -> expanded.
- Accordion expanded -> collapsed.
- Command input focus gained/lost.
- Suggestion strip appears/disappears.
- Active command prompt/options appear/disappear.
- Function/action bar content changes.
- Transient notification appears/disappears.

The CAD viewport must remain a stable, full-size rendering surface below these controls. It is not acceptable merely to rebase the drawing so that it appears centred again after every overlay transition: the transform itself must remain unchanged for UI-only state changes.

---

# 19. Persistent three-tier bottom control stack

The bottom workspace controls must always expose three distinct stacked zones, from top to bottom:

1. **Function/action bar** — the uppermost of the three bottom control zones.
2. **Command entry/prompt bar** — directly beneath the function/action bar and above the controller.
3. **Virtual controller area** — the lowest zone, expanded or compact according to the controller state.

These are overlays over the CAD viewport, not siblings that reduce its measured size.

## 19.1 Function/action bar

Keep the function bar visible continuously. It contains:
- The `+` action launcher.
- Tool-browser status and the current open/closed state (for example, `Tools` / `Tools open`); do not use a visible arrow icon to open the drawer.
- Existing relevant quick actions, such as selection-dependent actions, when contextually valid.
- A compact status/current-command description area.

Retain useful existing functions. Reorganise their presentation rather than deleting them.

## 19.2 Persistent command entry and prompt bar

- Render the command `TextField` continuously; never hide it behind a separate `CMD` button.
- It occupies the middle tier, directly below the function bar and above the touchpad/joystick controller.
- The placeholder explains that it accepts commands and coordinate expressions.
- It must not automatically request keyboard focus or summon the software keyboard on app launch.
- On deliberate focus, accept soft-keyboard and hardware-keyboard input.
- When typing, show only ranked suggestions for the current query.
- When a command is active, show its live prompt and only that command's valid options in a compact contextual region associated with the command bar.
- Keep PICK PT associated with the controller and current point prompt; do not make the sheet itself a point-input surface.

## 19.3 Controller tier

The bottom tier preserves the existing relative touchpad and velocity joystick functionality. It may be expanded or compact, but its change in height must only reveal/cover more of the viewport. It must not move the viewport or change its model-to-screen transform.

Retain:
- Touchpad/joystick mode selection.
- Live X/Y coordinates.
- Configuration.
- Nudge increments and directional nudge controls.
- PICK PT when the active command is awaiting a point.
- Confirm/cancel where valid.

---

# 20. Strict overlay architecture and viewport stability

## 20.1 Required layout architecture

Refactor the workspace around a stable root `Box` or equivalent overlay-capable root:

- Render `CadViewportCanvas` once at stable root content bounds.
- Layer header, HUD, drawer, function bar, command bar, controller and transient messages above it.
- Give each overlay its own measured bounds and hit-testing region.
- Ensure overlay animation changes only that overlay's translation, clipping, visibility or internal layout.
- Do not let overlays participate in the viewport's measurement constraints.

A possible Z-order from back to front:

1. CAD viewport / grid / drawing.
2. Non-interactive viewport HUD elements.
3. Header and function/action controls.
4. Command-entry and contextual command bar.
5. Tool drawer and five-tile rail.
6. Controller panel and its interactive controls.
7. Transient notification overlay.

Resolve actual z-order so that controls receive their own touches and cannot leak those touches to the sheet.

## 20.2 Separate viewport bounds from unobstructed interaction bounds

Do not overload `UsableViewportRect` with two different responsibilities.

- `ViewportSurfaceRect`: stable drawing viewport dimensions and origin. It defines the rendering coordinate system and is unaffected by overlays.
- `InteractionSafeRect`: a dynamic exclusion region for cursor-following decisions and UI collision avoidance. It may account for opaque overlays, but changing it must not rebase the drawing transform.

A change to `InteractionSafeRect` may affect where future automatic cursor tracking keeps the crosshair visible. It must not move the drawing merely because an overlay appeared.

## 20.3 Regression tests

Capture and compare the viewport transform before and after each overlay state transition. Require exact equality within floating-point tolerance for:
- `panScreenX`
- `panScreenY`
- `pixelsPerModelUnit`

Also verify:
- The same model-space test points project to the same screen positions.
- Cursor model coordinates remain unchanged.
- Active command and command step remain unchanged.
- The drawing does not jump when the controller is collapsed while the touchpad is being used.
- Rotation and true display-size changes remain separately handled.

---

# 21. Transparent dimension and leader text in the CAD viewport

## 21.1 Required visual result

Dimension labels, symbols and units must be drawn directly over the viewport with **no filled background, no translucent rectangle, no blurred plate and no background pill**.

Examples include:
- `136 mm`
- `⌀28 mm`
- `6× ⌀10 mm`
- `4× R6 mm`
- Angular, ordinate and linear dimension labels.
- Leader callout text.

The geometry, grid and lines behind the text must remain visible. The text itself must not erase, mask or conceal the structure behind it.

## 21.2 Source-level change

In the uploaded snapshot, `CadViewportCanvas.kt` creates dark `Paint.Style.FILL` rectangles in the rendering paths for:
- `LeaderEntity` label text.
- Radial/diametric dimension labels.
- Angular dimension labels.
- Standard linear/aligned/ordinate dimension labels.

Remove these background-rectangle draw operations from the on-screen viewport renderer. Do not replace them with another filled shape or blur effect.

Preserve:
- Existing text positions and annotation layout plans.
- Diameter/radius symbols and units.
- Leader geometry, dimension lines, extension lines and arrowheads.
- Dimension text rotation and alignment.
- Existing zoom-aware text sizing and a sensible minimum screen-space font size.
- High-contrast annotation text colours.

Do not compensate for the removed background by making all annotation text excessively large or by moving labels away from their established positions. The supplied screenshots show that the positions and general dimension layout are accepted; this is a targeted background-mask removal.

A text-only contrast treatment may be considered only if it does not create a filled area or materially hide drawing geometry. Prefer the current clear cyan/blue text colour over adding a heavy outline.

## 21.3 PDF non-regression

The user confirms that PDF export is now working correctly and supplies `proj_mounting_plate.pdf` as an accepted example. Do not change the PDF export pipeline as part of this on-screen label-background task.

Use the supplied one-page PDF as a visual regression reference for the plotted technical drawing:
- CNC 6-Hole Mounting Plate.
- 136 mm overall width and 96 mm overall height.
- Four R6 corner radii.
- Six Ø10 mounting holes.
- Ø28 central hole.
- Existing location dimensions and title block.

A PDF change is permissible only if a separate defect is demonstrated and the user authorises that scope.

---

# 22. One-finger pan and two-finger pinch-only zoom — implementation details

## 22.1 One-finger pan

- Track the one active pointer's movement in screen pixels.
- Convert its delta into viewport pan delta only.
- Do not change the CAD cursor's model-space position.
- Apply movement smoothly and proportionally; do not add uncontrolled inertial scrolling.
- Stop immediately when the finger is lifted.
- Do not allow a tap or short movement below touch slop to pan.
- Do not allow a left-edge drawer gesture to be consumed as pan.

## 22.2 Two-finger zoom

- Track the distance between the two active pointers.
- Convert the distance ratio into a scale multiplier.
- Zoom around a stable initial focal anchor.
- Do not apply pan from centroid displacement.
- Clamp to configured minimum/maximum scale.
- Do not mutate drawing geometry or cursor coordinates.
- If a pointer is added/removed or the event stream is cancelled, reset the recogniser safely.

## 22.3 State transitions

- `IDLE -> ONE_POINTER_PAN` when one finger begins a movement outside the drawer edge zone and exceeds touch slop.
- `ONE_POINTER_PAN -> TWO_POINTER_PINCH_ZOOM` when a second finger joins.
- `TWO_POINTER_PINCH_ZOOM -> POST_PINCH_POINTER_SUPPRESSED` when one finger lifts while another remains.
- `POST_PINCH_POINTER_SUPPRESSED -> IDLE` only after all fingers are lifted.
- An inward left-edge swipe from the reserved drawer region enters `LEFT_EDGE_DRAWER_REVEAL`, not `ONE_POINTER_PAN`.
- Cancellation returns to `CANCELLED`, then to `IDLE` only when the event sequence has fully ended.

Do not reuse `calculatePan()` from a combined transform detector for two-pointer gestures unless the pan component is explicitly disabled. A dedicated distance-based pinch implementation is preferable.

---

# 23. DXF export — standards audit and compatibility investigation

## 23.1 Current user-reported status

- PDF export is working and is accepted. Preserve it.
- DXF export is still not opening in AutoCAD Mobile on the user's device.
- The user has not yet established whether the failure is specific to AutoCAD Mobile or affects other DXF readers.
- No exported `.dxf` file or exact AutoCAD Mobile error message is included in this upload. Therefore, do not claim that the mobile app is definitively at fault or that the exporter is definitively the sole cause.

## 23.2 Concrete source-level compatibility risk

In the uploaded source, `CadDxfInterchange.kt` writes `DIMENSION` entities with group-code 70 flags containing bit 32 (for example, 32, 33, 34, 35, 36, 38 and 102). The code comments describe bit 32 as indicating a referenced dimension block.

However, the inspected `DIMENSION` writer does not appear to write group code 2 (the name of the block containing the graphical representation of that dimension), nor does the dimension writer generate a matching anonymous dimension picture block in the BLOCKS section.

Autodesk's DXF reference states:
- Group code 2 is the name of the block containing the entities that make up the dimension picture.
- Group-code 70 bit 32 indicates that the block referenced by group code 2 is referenced by that dimension only.

This mismatch is a concrete compatibility risk and must be investigated. It is not, by itself, proof that it explains every AutoCAD Mobile failure.

References:
- [Autodesk — Common Dimension Group Codes](https://help.autodesk.com/cloudhelp/2021/ENU/AutoCAD-DXF/files/GUID-EDD54EAC-A339-4EBA-AEA6-EC8066505E2B.htm)
- [Autodesk — BLOCK group codes](https://help.autodesk.com/cloudhelp/2020/ENU/AutoCAD-DXF/files/GUID-66D32572-005A-4E23-8B8B-8726E8C14302.htm)

## 23.3 Required audit and implementation

The agent must:
1. Validate the complete AC1015 output structure, including required entity subclass markers and ownership/table records.
2. For every exported DIMENSION entity, correctly emit the group code 2 block name and matching anonymous dimension picture block if the selected DXF representation requires it.
3. Ensure the group-code 70 flags accurately match the emitted representation. Do not set the block-reference bit if the referenced block is absent.
4. Ensure dimension style table records and referenced text styles are standards-compliant, including required handles/references where applicable.
5. Check coordinate systems and required group codes for each dimension subtype.
6. Test RADIUS, DIAMETER, horizontal/vertical linear, aligned, angular and ordinate dimensions independently.
7. Check LEADER/MTEXT, center marks, bulged LWPOLYLINE, arcs, ellipses, splines, hatches, blocks and custom metadata for reader compatibility.
8. Ensure unsupported entities are exported using standards-compliant decompositions or are clearly reported; do not emit non-standard entity names and assume every reader accepts them.
9. Keep file encoding, line endings, section terminators, handles and table counts valid.
10. Add round-trip tests and an external DXF parser/preflight step where available.

## 23.4 Compatibility test matrix

Test the same exact exported `.dxf` file in:
- At least one independent DXF parser or validator.
- A desktop CAD reader (AutoCAD desktop if available).
- AutoCAD Web where available.
- AutoCAD Mobile on Android.
- One additional independent CAD application if available.

Record the application/version, Android version, DXF file size, `$ACADVER`, whether the file opens, whether dimensions render, and the exact error message.

Interpret results cautiously:
- Fails in all readers: likely exporter or file corruption.
- Opens in independent readers and desktop AutoCAD but fails only in AutoCAD Mobile: investigate mobile-specific format support, file limits or an AutoCAD Mobile defect.
- Opens but dimensions are missing or malformed: investigate dimension block/style records and per-type DXF semantics.
- Opens after resaving through a desktop CAD application: diff the original and resaved DXF to identify the compatibility repair.

Autodesk's own support article for drawings that do not open in AutoCAD Mobile lists file corruption and file-size limits as possible causes. For a file that has never opened/saved in Mobile, Autodesk recommends testing in desktop AutoCAD, using `RECOVER`, `AUDIT` and `-PURGE`, then retrying the repaired file:
- [Autodesk — Drawing cannot be opened in AutoCAD Mobile App](https://www.autodesk.com/support/technical/article/caas/sfdcarticles/sfdcarticles/Drawing-cannot-be-opened-in-AutoCAD-mobile.html)

Do not use this generic troubleshooting guidance as evidence that the current failure is Autodesk's fault. The actual exported DXF and error message are needed for a conclusive diagnosis.

---

# 24. Motion API and velocity-sensitive virtual cursor

## 24.1 Terminology and recommendation

The phrase “Motion API” is ambiguous. The agent must identify the exact Android or AndroidX API it proposes before adding dependencies.

For this Compose-native project, the recommended implementation is:

- Continue using Jetpack Compose `pointerInput` / `PointerInputChange` for the existing virtual controller surfaces.
- Use `androidx.compose.ui.input.pointer.util.VelocityTracker` to estimate pointer velocity from timestamped input samples where velocity-sensitive response is required.
- Implement the CAD-specific response curve, acceleration, dead zone, filtering and coordinate integration in the existing virtual-controller/controller-engine layer.

Do **not** introduce `MotionLayout` merely to improve pointer movement; it is an animation/layout system, not the velocity estimator needed here.

Do **not** replace Compose pointer input with raw `android.view.MotionEvent` handling unless the agent demonstrates a concrete requirement that Compose pointer input cannot satisfy.

Android's `MotionEvent` is the underlying framework event representation for finger, stylus and mouse movement. It exposes pointer positions, actions, axes and historical samples. It is not a one-call cursor-smoothing feature. The relevant velocity calculation is a separate concern.

Relevant official documentation:
- [Android — Track touch and pointer movements](https://developer.android.com/develop/ui/views/touch-and-input/gestures/movement)
- [Android — MotionEvent reference](https://developer.android.com/reference/android/view/MotionEvent)
- [Compose — VelocityTracker reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/input/pointer/util/VelocityTracker)
- [Compose — Understand gestures and pointerInput](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures)

## 24.2 Touchpad response

For relative touchpad mode:
- Measure finger displacement and event time.
- Estimate finger velocity using `VelocityTracker`.
- Map finger speed through a configurable, bounded sensitivity/acceleration curve.
- Preserve precise low-speed movement for drafting.
- Permit faster cursor travel during fast swipes.
- Avoid sudden sensitivity discontinuities near the acceleration thresholds.
- Do not add inertial drift after the finger is released unless the user explicitly enables a separately designed option. Default behaviour is immediate stop.
- Maintain exact coordinate accumulation in model-space double precision.

## 24.3 Joystick response — preserve current implementation

The existing joystick implementation is already accepted by the user and must remain unchanged during this refinement.

- Do not replace, rewrite, retune or refactor its frame-integrated velocity loop.
- Do not change joystick deflection mapping, response exponent, maximum speed, dead zone, zoom normalisation, acceleration, or stop-on-release behaviour.
- Do not change its currently functioning layout, nudge pad, step-size controls or point/action buttons.
- Touchpad work must be isolated to the `RELATIVE_TOUCHPAD` branch and shared infrastructure only where a change is proven not to alter joystick behaviour.
- Add regression tests that compare joystick response before and after the touchpad change using the same input traces.

## 24.4 Filtering and performance

- Prefer Compose `VelocityTracker` first; do not add a new third-party motion library without benchmark evidence.
- Add only modest filtering. Excessive smoothing will add perceived latency and make precision drafting feel sluggish.
- Use event timestamps and elapsed time, not a fixed number of model units per frame.
- Coalesce visual cursor updates to the display frame where appropriate, while retaining the latest input sample.
- Avoid allocations and expensive state/recomposition work inside the high-frequency input path.
- Call `calculateVelocity()` only when needed; velocity calculation is more expensive than adding samples.
- Preserve immediate release/cancel semantics and reset the tracker at the beginning of a new pointer sequence.
- Keep the touchpad/joystick controller's own pointer recogniser separate from the viewport navigation recogniser.

## 24.5 Recommendation

Implement velocity estimation as a measured enhancement, not as a wholesale migration to raw MotionEvent. Use the Compose `VelocityTracker` with a well-defined CAD response curve. Keep the current controller behaviour as a baseline, benchmark cursor travel time, low-speed precision, overshoot and input-to-visible-response latency, and retain the change only if it improves both fast traversal and fine drafting.

---

# 25. Final acceptance checklist for this refinement batch

- [ ] GitHub access is connected and the current remote branch/HEAD is identified before any source changes.
- [ ] The uploaded ZIP snapshot commit is compared against the current repository.
- [ ] Tool browser has five category tiles matching the existing five command categories.
- [ ] Draw includes LINE and POLYGON and preserves the other registered drawing tools.
- [ ] Modify/Edit, Select/Inquiry and Annotate 2D/QDIM are easy to locate and are not flattened into one long list.
- [ ] Multiple accordion headers can remain expanded simultaneously.
- [ ] There is no visible arrow/chevron control in either drawer state.
- [ ] Left-edge inward swipe opens the drawer; swipe-outward or Android Back closes it.
- [ ] Edge-swipe recognition does not break one-finger viewport pan or Android system navigation.
- [ ] Command entry is always visible, but does not automatically open the keyboard.
- [ ] Function/action bar is always visible above the command entry bar.
- [ ] The controller is the lowest of the three bottom overlay tiers.
- [ ] All workspace controls overlay the drawing; they never change viewport measurement or transform.
- [ ] Controller collapse/expansion leaves the drawing at exactly the same screen position and scale.
- [ ] One finger pans; two-finger pinch zooms only; no other direct sheet touch does anything to CAD content.
- [ ] Dimension and leader labels are transparent and do not mask drawing geometry.
- [ ] PDF output remains unchanged and continues to match the user's accepted PDF example.
- [ ] DXF dimension records and anonymous dimension blocks are standards-audited; output opens in independent readers and AutoCAD Mobile, or a precise reader-specific limitation is documented.
- [ ] Motion velocity tracking is based on Compose `VelocityTracker` unless a demonstrated requirement justifies lower-level MotionEvent access.
- [ ] Cursor stops immediately on controller release and remains precise at low movement speeds.
- [ ] Touchpad taps, double-taps and long presses produce exactly zero cursor displacement.
- [ ] Touchpad movement begins only after the intentional-drag threshold; the threshold-crossing motion is discarded.
- [ ] Touchpad mode contains no PICK PT, Enter, Escape, nudge arrows or step-size selector.
- [ ] Joystick layout, frame-integration loop, response curve, sensitivity and stop-on-release behaviour are unchanged.
- [ ] Touchpad pointer ownership suppresses sheet navigation until every pointer is released.
- [ ] Portrait and landscape layouts pass the same interaction and viewport-invariance tests.


---



---

# 26. Final touchpad design decision

The touchpad is not a button pad, tap surface or gesture shortcut region. It is a **relative cursor-movement surface**. Its only purpose is to move the virtual CAD cursor through intentional finger dragging.

The safest practical interaction is a **touch-slop-gated relative drag**:

- A finger-down only establishes the starting point; it never moves the cursor.
- Finger jitter and all motion below the platform's touch-slop threshold are quarantined.
- A tap, double-tap or long press ends without changing cursor coordinates.
- Crossing touch slop arms the drag, but the threshold-crossing displacement itself is discarded.
- Only subsequent movement deltas move the cursor.
- No tap or double-tap action is implemented.
- The controller consumes its complete pointer sequence and prevents it from reaching sheet navigation.
- A multi-pointer sequence cancels the touchpad movement and remains suppressed until every pointer is released.
- Velocity tracking begins only after the drag has been armed.

This is preferable to adding a double-tap detector. A double-tap detector would introduce a second gesture recogniser, timing ambiguity and the risk of moving the cursor during the first tap before the second tap is recognised. The movement-intent gate avoids that problem at its source.

The cost is a small initial dead zone at the start of each finger contact. This is intentional: it prevents accidental taps from becoming cursor motion. The agent must tune and test the system-scaled threshold on the target device, but must not reduce it so far that ordinary finger jitter causes cursor displacement.

The joystick is not part of this change. Preserve its currently accepted motion response and layout.

# 27. Touchpad-only controller mode, pointer isolation and velocity refinement

This section records the user's latest explicit controller requirements. It supersedes any earlier instruction that presents the same PICK PT / ENTER / ESC action column in both controller modes. **Do not change the joystick's existing behaviour or velocity implementation.**

## 27.1 Current source implementation

The inspected `VirtualCursorControllerDeck` is in:

`app/src/main/java/com/example/ui/components/VirtualCursorController.kt`

The current expanded controller uses one shared horizontal control row containing:

1. `StepNudgePad`
2. The relative touchpad or joystick surface
3. A right-side action column containing `PICK PT`, Enter and Escape

The current action column is rendered regardless of `config.mode`. The step-increment selector and nudge pad are also rendered in both modes.

The touchpad surface's `pointerInput` handler calls `awaitFirstDown(requireUnconsumed = false)`, but the initial down is not consumed immediately. The handler consumes changes later in the movement loop. This leaves an opportunity for a parent/sibling viewport recogniser to observe the down event before the controller has claimed the complete gesture. The latest requirement explicitly forbids that event from becoming a tap or double-tap on the sheet.

The current relative touchpad converts each pointer displacement directly into model displacement using `touchpadSensitivity / pixelsPerModelUnit`. It does not use a velocity estimator. The joystick is separately implemented through `LaunchedEffect`, `withFrameNanos`, `joystickDeflection`, `joystickVelocityExponent` and `joystickMaxSpeedUnitsPerSec`.

## 27.2 Mode-specific presentation

The two controller modes must now have distinct expanded layouts.

### Relative Touchpad mode

When `config.mode == VirtualControllerMode.RELATIVE_TOUCHPAD`:

- The expanded controller's main interactive area must show **only the relative touchpad surface**.
- The touchpad surface must occupy the available main-control width; do not leave blank columns where the nudge pad or action buttons used to be.
- Hide the `PICK PT` button from this mode.
- Hide the Enter and Escape buttons from the touchpad control row.
- Hide the directional `StepNudgePad` from the touchpad control row.
- Hide the step-increment selector while the touchpad mode is active, because it configures the hidden nudge pad.
- Do not show an alternative tap, double-tap, long-press, click or context-menu action on the touchpad.
- Keep the shared controller header: mode selector, X/Y coordinate readout, settings and collapse/expand control.
- Keep the touchpad's own visual surface, grid/reticle and movement instruction. It is a relative input surface, not a button.
- The user moves the CAD cursor by sliding a finger across this surface. A finger-down event, stationary touch, tap, double-tap, long press or finger-up event must not itself trigger any CAD action.

The touchpad mode is deliberately a clean cursor-navigation surface. Its main row must not contain other command-action controls.

### Velocity Joystick mode

When `config.mode == VirtualControllerMode.VELOCITY_JOYSTICK`:

- Preserve the existing joystick layout and behaviour.
- Keep the joystick surface, StepNudgePad, step-increment selector, `PICK PT`, Enter and Escape controls as they currently work.
- Keep the existing frame-integrated joystick velocity loop, dead zone, response exponent, zoom normalisation and immediate stop-on-release behaviour unchanged.
- Do not replace, retune, refactor or otherwise alter the joystick motion algorithm as part of this touchpad task.
- Do not remove or globally disable `onPickAtCursor`; the point-pick action remains available in joystick mode and through any existing command-level route outside the touchpad surface.

Use explicit mode branches in the composable rather than rendering all controls and merely setting unwanted controls to alpha zero or disabling them. Hidden controls must not retain layout space or pointer hit targets.

## 27.3 Touchpad pointer isolation and tap-safe movement gate

The touchpad is a **movement-only relative input surface**. It has no click, tap, double-tap, long-press, selection or point-pick action. Do not add a double-tap recogniser to it.

The user's central concern is that a tap or double-tap can contain tiny unintended finger movements. A purely relative pad that immediately applies every pointer delta can therefore move the CAD cursor even though the user intended only to tap. The touchpad must use an explicit movement-intent gate.

### Required state machine

Use a touchpad-local state machine:

- `IDLE`
- `PRESSED_UNDECIDED`
- `DRAG_ACTIVE`
- `MULTI_POINTER_CANCELLED`
- `CANCELLED`

### Gesture protocol

1. **DOWN:** immediately claim and consume the pointer-down. Record the initial pointer position and pointer ID. Do not move the CAD cursor. Reset all per-gesture state.
2. **PRESSED_UNDECIDED:** consume every pointer change. Measure displacement from the initial down position, but do not apply any movement to the virtual cursor yet.
3. **Below touch slop:** while cumulative displacement remains below the platform's configured touch-slop threshold, keep the cursor completely stationary.
4. **UP before touch slop:** classify the interaction as a tap/no-op. Discard all accumulated displacement. The virtual cursor coordinates must remain bit-for-bit unchanged (or unchanged within the coordinate representation's exact equality semantics).
5. **Second tap:** handle it as another independent tap/no-op. No double-tap recogniser is installed, and neither tap is allowed to move the cursor.
6. **Touch-slop crossing:** classify the interaction as an intentional drag. Discard the entire pre-activation displacement and set the current pointer location as the new movement baseline. Do not apply the threshold-crossing delta to the CAD cursor.
7. **DRAG_ACTIVE:** only subsequent pointer deltas move the virtual cursor. Apply the velocity-sensitive gain described in Section 27.4.
8. **UP/CANCEL:** stop immediately, clear the pointer owner, clear velocity/filter history and return to `IDLE`. No movement continues after release.

Use Compose's supported touch-slop gesture primitives where suitable (for example, `awaitTouchSlopOrCancellation` / `awaitDragOrCancellation`) or implement the equivalent explicitly. Use the platform-scaled touch-slop value; do not invent a hard-coded pixel threshold. If the Compose helper consumes the slop-crossing change, still ensure that the slop-crossing displacement is not forwarded to the CAD cursor.

This deliberately sacrifices the first small finger displacement of each drag in exchange for a strong accidental-input safeguard. After the drag is armed, small movements are fully usable for fine drafting. Validate the threshold on the actual phone so it filters finger jitter without making the touchpad feel unresponsive.

### Pointer ownership and sheet isolation

- On DOWN, consume the down change immediately.
- Consume every movement change while the touchpad owns the gesture.
- Consume pointer-up and cancellation changes.
- Do not register `clickable`, `combinedClickable`, `detectTapGestures`, double-tap, long-press, context-menu or stylus-click handlers on the touchpad.
- Do not pass an unconsumed down/up sequence to the viewport recogniser.
- A touchpad gesture owns the pointer until the complete sequence ends.
- The controller overlay must be above the viewport in z-order and win hit-testing within its visible bounds.
- If a second pointer appears while a touchpad gesture is undecided or active, immediately enter `MULTI_POINTER_CANCELLED`, freeze the cursor at its current coordinates, consume the extra pointer and all remaining changes until every pointer is up. Do not pass the second pointer to the viewport to initiate sheet pinch zoom.
- Coordinate with the root gesture arbiter so that a pointer sequence owned by the touchpad cannot leak into viewport one-finger pan or two-finger zoom, including when an additional pointer lands just outside the touchpad bounds.
- If necessary, expose an explicit controller-input ownership flag to the viewport recogniser. While the touchpad owns input, the viewport recogniser must suppress the entire multi-pointer sequence until all pointers are released.
- A pointer cancellation, focus loss or controller collapse must stop movement and clear ownership safely.

### What counts as a tap

A stationary press, a tap, a double-tap or a long press is always a no-op. A pointer sequence becomes cursor movement only after it crosses touch slop and is therefore classified as a drag. Once a drag is active, no tap action exists.

A normal tap that remains within the touch-slop envelope must never cause even a small cursor-coordinate change. A gesture that crosses touch slop is, by definition, treated as a drag rather than a tap.

## 27.4 Touchpad velocity-sensitive refinement

The user has accepted the recommendation to refine the touchpad without changing the joystick. Implement the refinement only in the `RELATIVE_TOUCHPAD` branch.

- Keep relative movement semantics: the finger's displacement moves the virtual CAD cursor; finger position is not an absolute model-space coordinate.
- Do not calculate or apply cursor velocity while the gesture is `PRESSED_UNDECIDED`.
- When touch slop is crossed, establish a fresh velocity baseline at the new drag origin; pre-activation tap/jitter samples must not create an initial velocity impulse.
- Track pointer positions and timestamps during `DRAG_ACTIVE` only.
- Use Jetpack Compose's `androidx.compose.ui.input.pointer.util.VelocityTracker` (or a verified equivalent already available in the project's Compose version) to estimate pointer speed. Use the active-drag samples for gain calculation; if the chosen API requires a tracked position history, seed it at drag activation rather than carrying the tap/jitter history into the first gain calculation.
- Filter the velocity estimate to avoid noise and sudden gain changes.
- Convert speed to a bounded, configurable, non-linear gain multiplier.
- Preserve a precise low-speed region for drafting and allow faster travel for deliberate rapid strokes.
- Apply the multiplier to the current relative displacement. Do not create an unbounded frame loop or a post-release fling.
- Retain `config.touchpadSensitivity` as the user's base sensitivity. The velocity response modifies the displacement gain; it must not silently overwrite the stored sensitivity.
- Normalise against `pixelsPerModelUnit` so the model-space displacement remains predictable across zoom levels.
- Clamp the maximum gain and maximum per-event model displacement. No high-speed swipe may teleport the cursor by an uncontrolled amount.
- Smooth changes in the gain using a small low-pass filter or equivalent stable response.
- Reset velocity samples and filter state at the start of each new touchpad gesture, at the transition into `DRAG_ACTIVE`, and on pointer cancellation/release.
- Do not use device accelerometer, gyroscope, MotionLayout or a new third-party gesture dependency.
- Do not alter `LaunchedEffect`, `withFrameNanos`, joystick deflection, joystick response curve or joystick stop-on-release code.

Compose documents `VelocityTracker` as the facility for calculating pointer velocity from tracked events or positions/timestamps. Its `addPointerInputChange` extension should receive the DOWN and MOVE samples; velocity calculation should be performed only when needed because it is more costly than adding samples:
`https://developer.android.com/reference/kotlin/androidx/compose/ui/input/pointer/util/package-summary`

## 27.5 Point-pick workflow in touchpad mode

Removing the touchpad's `PICK PT` button must not accidentally delete the underlying CAD point-acquisition capability.

- The touchpad itself only moves the virtual cursor.
- It does not commit the cursor as a command point.
- The joystick mode retains its existing `PICK PT` action.
- Existing command-line coordinate entry and any command-level point-acceptance action remain available outside the touchpad surface.
- Do not create a hidden or invisible button over the touchpad to preserve the old action.
- Do not reinterpret a tap or double-tap on the touchpad as `PICK PT`.

## 27.6 Touchpad acceptance tests

- [ ] Touchpad mode renders one full-width touchpad surface in the main control area.
- [ ] No PICK PT button is rendered in touchpad mode.
- [ ] No Enter or Escape buttons are rendered in the touchpad control row.
- [ ] No StepNudgePad or step-increment strip is rendered in touchpad mode.
- [ ] Shared controller header, coordinates, settings and collapse control remain available.
- [ ] Joystick mode retains its existing layout and functions.
- [ ] Joystick source and motion algorithm remain unchanged.
- [ ] Touchpad DOWN is consumed immediately.
- [ ] Touchpad MOVE, UP and CANCEL changes are consumed.
- [ ] A stationary touch leaves cursor X/Y exactly unchanged.
- [ ] A single tap with finger jitter below platform touch slop leaves cursor X/Y exactly unchanged.
- [ ] Two consecutive taps leave cursor X/Y exactly unchanged; no double-tap recogniser exists.
- [ ] A long press with no drag leaves cursor X/Y exactly unchanged.
- [ ] The slop-crossing delta is discarded and does not create a cursor jump.
- [ ] Subsequent drag deltas move the cursor smoothly and relatively.
- [ ] Single tap, double-tap and long press on the touchpad do not trigger a sheet event.
- [ ] No touchpad pointer sequence reaches the sheet's pan or zoom recogniser.
- [ ] A second finger inside the touchpad does not start sheet pinch zoom.
- [ ] A touchpad stroke moves the virtual cursor only by relative displacement.
- [ ] Slow touchpad movements remain precise.
- [ ] Faster movements increase cursor travel smoothly within a bounded gain.
- [ ] Releasing the touchpad stops cursor movement immediately.
- [ ] Re-touching begins with fresh velocity history and no coordinate jump.
- [ ] Zoom changes do not produce a sensitivity discontinuity.
- [ ] PICK PT remains available in joystick mode and any existing command-level point-acceptance route.

---

# 28. Inspection report: uploaded `proj_mounting_plate.dxf`

The user supplied `/mnt/data/proj_mounting_plate.dxf`. The file was inspected directly as a DXF text file and with the independent `ezdxf` parser version 1.4.4. These findings refer to this exact uploaded file, not to a hypothetical export.

## 28.1 Basic file facts

- File size: 2,414 bytes.
- Text structure: 550 lines, alternating DXF group-code and value lines.
- Declared DXF version: `AC1015` (AutoCAD 2000 format family).
- `$INSUNITS` is `4`, which declares millimetres.
- Present sections: `HEADER`, `TABLES`, `BLOCKS`, `ENTITIES`.
- Model space contains exactly 10 entities:
  - 1 `LWPOLYLINE`
  - 7 `CIRCLE`
  - 2 `LINE`
- There are no `DIMENSION`, `TEXT`, `MTEXT`, `LEADER`, `HATCH` or `INSERT` entities in model space.
- The `DIMENSIONS` and `HATCH` layers are defined but have no model-space entities.
- A block definition named `M8_HEX_SOCKET` exists in `BLOCKS`, containing a circle and a closed hexagonal `LWPOLYLINE`, but no model-space `INSERT` references that block.

## 28.2 Defect 1 — parser rejects the LWPOLYLINE records

An ordinary read with `ezdxf 1.4.4` fails with:

`DXFStructureError: missing 'AcDbPolyline' subclass in LWPOLYLINE(#None)`

The recovery reader fails at the same structural defect.

The file has two `LWPOLYLINE` records: the block's hexagonal outline and the model-space mounting-plate outline. Both omit the required group-code-100 `AcDbPolyline` subclass marker. They also need the complete entity subclass sequence, including `AcDbEntity`, in the correct order for the declared DXF version.

As a diagnostic experiment, inserting the missing `100 / AcDbEntity` and `100 / AcDbPolyline` subclass markers into both records allowed `ezdxf` to read the AC1015 drawing. Its subsequent audit reported zero remaining errors but applied four repairs to invalid table-head handles (LTYPE, LAYER, STYLE and DIMSTYLE). This experiment is evidence that the missing polyline subclass markers are a real parser-level defect; it is not a substitute for correcting the exporter.

**Required fix:** make the writer emit the proper subclass markers for every entity type and validate the whole file, not just this polyline. A file that can be opened only after a recovery parser inserts missing structure is not an acceptable production export.

Autodesk LWPOLYLINE reference:
`https://help.autodesk.com/cloudhelp/2017/ENU/AutoCAD-DXF/files/GUID-748FC305-F3F2-4F74-825A-61F04D757A50.htm`

## 28.3 Defect 2 — table headers have invalid/incomplete structure

The `TABLE` headers in the uploaded file are written as a minimal sequence equivalent to:

- group 0: `TABLE`
- group 2: table name
- group 70: entry count

They omit the table-head handle and the `AcDbSymbolTable` subclass marker. Autodesk's DXF reference specifies the table-header handle, subclass marker and entry count; the DIMSTYLE table is a special case using handle group code 105 rather than group code 5.

The independent audit repaired invalid table-head handles in four tables: LTYPE, LAYER, STYLE and DIMSTYLE.

**Required fix:** generate valid unique handles and complete table-head records, using group code 105 for the DIMSTYLE table-head handle where required. Emit the correct table subclass marker and validate table-record subclass markers, ownership and references.

Autodesk symbol-table group-code reference:
`https://help.autodesk.com/cloudhelp/2017/ENU/AutoCAD-DXF/files/GUID-5AB9300F-F0AC-4ADE-89EA-A9D1D152D8B8.htm`

## 28.4 Defect 3 — the actual DXF contains no dimensions or annotation text

Although the companion PDF visibly contains dimension annotations, the uploaded DXF model space contains no dimension or text entities at all.

The DXF has seven circles, two centreline `LINE` entities and one closed outline `LWPOLYLINE`. The dimension layer is empty. The drawing therefore cannot reproduce the PDF's 136 mm, 96 mm, 28/68/108 mm, 24/72 mm, 4×R6, 6×Ø10 and Ø28 annotation set from its model-space contents.

This is separate from the source-level concern previously identified in the DXF `DIMENSION` writer about group code 70 and anonymous dimension picture blocks: **the uploaded sample contains no DIMENSION record on which that code path can be tested.** The agent must first establish why dimensions are absent from this file, then repair and test the `DIMENSION` serialization path using a fixture that actually contains dimensions.

**Required fix:** export the actual dimension and annotation entities associated with the drawing. If semantic DXF dimensions are supported, serialize standards-compliant `DIMENSION` entities with valid DIMSTYLE and anonymous dimension blocks. If a compatibility mode deliberately decomposes dimensions into `LINE`, `ARC` and `TEXT` entities, that mode must still export all visible annotation geometry and must be documented as non-associative.

## 28.5 Defect 4 — outline geometry does not agree with the PDF's stated dimensions

The model-space outline is the closed polyline with these approximate extents:

- X: 40.0000 to 160.0000 mm — width **120 mm**
- Y: 110.0000 to 190.0000 mm — height **80 mm**

The successful companion PDF specifies an overall plate size of **136 mm × 96 mm**. Its locating dimensions place the six Ø10 hole centres 28, 68 and 108 mm from the left datum, and 24 and 72 mm from the bottom datum.

The DXF hole centres are:

- X = 60, 100 and 140 mm
- Y = 126 and 174 mm

Those hole centres agree with the PDF's locating dimensions when the intended plate datum/extents are X = 32..168 mm and Y = 102..198 mm. The DXF outline itself, however, is inset by 8 mm on all four sides: X = 40..160 and Y = 110..190. Thus the outline is 16 mm too narrow overall and 16 mm too short overall, even though the hole coordinates and header extents correspond to the larger intended plate.

The other primary geometry is consistent with the PDF callouts:

- Centre bore: centre (100, 150), radius 14 mm → Ø28 mm.
- Six mounting holes: radius 5 mm → Ø10 mm each.
- Corner bulges: approximately 0.4142, consistent with quarter-circle R6 corner arcs.

**Required fix:** reconcile the model-space outer profile with the documented 136 × 96 mm geometry while retaining the six hole centres, Ø28 bore and R6 corner treatment. Do not merely alter `$EXTMIN`/`$EXTMAX`; the actual outline vertices must be correct. Add a dimensional regression test that checks the profile extents and hole coordinates against the supplied PDF's stated dimensions.

## 28.6 Defect 5 — named non-continuous linetypes have no dash pattern

The uploaded LTYPE table defines CONTINUOUS, DASHED, HIDDEN, CENTER, PHANTOM and DOT, but each entry has group code 73 set to zero and group code 40 set to zero. Consequently, the named non-continuous patterns do not contain dash elements.

The centreline entities explicitly reference CENTER, but the CENTER pattern is empty and can therefore appear continuous in a reader.

**Required fix:** write valid dash element counts, total pattern lengths and group-code-49 elements for non-continuous linetypes. Verify the linetype reference in each layer and entity. If a pattern cannot yet be emitted correctly, use CONTINUOUS in the compatibility export rather than a misleading named pattern.

## 28.7 DXF test plan for this exact sample

The coding agent must preserve this uploaded file as a regression fixture and perform these checks after repairing the exporter:

1. The file opens with a strict independent parser without recovery.
2. The file passes the parser audit with zero errors and zero repairs.
3. Both LWPOLYLINE records contain the complete subclass structure required by AC1015.
4. All table heads and table records have valid handles, subclass markers and resolvable references.
5. Model-space outline extents are 136 × 96 mm, not 120 × 80 mm.
6. The six Ø10 hole centres, Ø28 bore and R6 corner arcs agree with the PDF reference.
7. Dimension annotations are present and correctly represented in the DXF.
8. The expected linetype patterns render distinctly.
9. The same exact exported file opens in an independent CAD/DXF reader and AutoCAD Mobile.
10. Record the AutoCAD Mobile version, Android version, import path and any error text if it still fails.

Do not attribute the current failure to AutoCAD Mobile alone: the supplied file demonstrably fails an independent parser before these corrections.

---

# 29. Latest implementation order and non-regression constraints

Add the following tasks to the end of the existing implementation sequence:

1. Repair DXF entity and table subclass/handle structure, starting with the exact defects in Section 28.
2. Correct model-space plate outline geometry to 136 × 96 mm and restore the missing annotation entities.
3. Implement the two distinct controller layouts: touchpad-only input surface versus the unchanged joystick control deck.
4. Implement the touch-slop-gated touchpad state machine; stationary taps/double-taps must cause zero cursor displacement.
5. Establish controller pointer ownership so no touchpad pointer sequence reaches sheet pan/zoom, including extra pointers.
6. Add bounded, speed-sensitive relative touchpad gain using Compose velocity tracking only after drag activation.
7. Run the controller and DXF regression tests in Sections 27 and 28.

Non-regression requirements:

- Do not change the existing joystick velocity algorithm or its feel.
- Do not reintroduce PICK PT, Enter/Escape or nudge buttons in touchpad mode.
- Do not let touchpad taps/double-taps reach the CAD sheet.
- Do not change the one-finger sheet-pan / two-finger sheet-zoom contract outside the controller overlay.
- Do not alter the working PDF export path while fixing DXF.
- Do not mark DXF as fixed until the exact sample opens in a strict independent parser without automatic repairs and in the intended target CAD viewer.


## Addendum to Sections 18 and 23: uploaded-file versus exporter findings

The uploaded `proj_mounting_plate.dxf` contains no `DIMENSION` records. The earlier source-level finding about the DIMENSION writer's group-code-70 / anonymous-block handling remains an exporter code-path concern, but it must not be described as the demonstrated cause of the parse failure in this particular file. The demonstrated parse failure is the missing `AcDbPolyline` subclass marker. The demonstrated missing-drawing-content issue is the absence of all dimension/text entities. Test these failure classes independently.


## Official Android/Compose references for the consolidated touchpad rule

- Compose gesture handling and touch-slop helpers: `https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures`
- Compose pointer velocity tracking: `https://developer.android.com/reference/kotlin/androidx/compose/ui/input/pointer/util/package-summary`

The Compose documentation describes `awaitTouchSlopOrCancellation` and drag helpers for distinguishing intentional movement from a tap, and requires custom gesture handlers to consume pointer changes when they own the interaction. `VelocityTracker` can estimate speed from timestamped positions; however, this application must not use pre-activation tap/jitter samples to move the cursor or create a first-drag velocity impulse. Seed or reset the tracker at the deliberate-drag baseline and use it only after the touchpad has committed to `DRAG_ACTIVE`.


---

# 30. Floating Overview panel and unified animated operation feedback

## 30.1 Replace the toolbar's trailing guidance/status text

The short guidance text currently displayed at the far right of the bottom function toolbar (for example, `READY • QDIM ...`) must no longer occupy that toolbar row.

In the inspected source snapshot, the current trailing text is rendered by `ContextualCommandAndOptionsDock` in `CadWorkspaceScreen.kt` from `uiState.commandPromptBanner` in the no-selection branch. The active command prompt is also rendered separately by `ActiveCommandContextBar`.

Replace the toolbar's trailing guidance text with a distinct **Overview** overlay:

- The Overview is a small, self-contained floating panel/card positioned immediately above or near the function toolbar.
- It is visually separate from the toolbar: no shared border, attached tab, joined background or combined container.
- It must not consume layout height or cause the toolbar, controller, command panel or viewport to move.
- It is displayed only in response to a meaningful user action or state transition, such as selecting a tool, starting a command, accepting a point, completing an operation, cancelling, erasing, selecting/deselecting, changing a mode or encountering a warning/error.
- It gives a concise explanation of the selected operation, what the user should do next, or what has just happened.
- Its heading is `OVERVIEW` (or `Overview`, matching the chosen typographic convention).
- It may contain a short command/action title and one concise explanatory sentence. Do not turn it into a large modal or persistent instruction panel.
- It must fade in smoothly, remain for a bounded period, then fade out smoothly. A slight vertical translation of a few dp may accompany the fade, but avoid a large slide or bounce.
- A standard information message should remain visible for approximately 2–3 seconds (2.5 seconds default), then exit.
- A new event arriving during the display interval must transition coherently; do not replace the visible text abruptly.
- Place the overlay so it does not obscure the active virtual cursor or cover essential command controls. On small screens it may use a narrower width and one or two lines.
- Give it a restrained near-black opaque or high-opacity surface, thin neutral border and a small subdued accent indicator. Do not use the previous bright neon badge treatment.

## 30.2 Separate ephemeral Overview from persistent command prompts

The Overview is explanatory feedback, not the only source of operational instructions.

- The always-visible command tier continues to show the exact current command prompt while a command is active, for example `Specify first point`, `Specify next point`, `Enter radius`, or the active command's valid option list.
- A required point/parameter prompt must remain visible until the user supplies the required input, changes option, completes or cancels the command. Never fade away a prompt that the user needs in order to proceed.
- The Overview may briefly summarise the step, but it must not replace the persistent active-command prompt.
- In idle state, the command tier retains its editable command input and concise READY state. The Overview is hidden unless a new action warrants it.
- The function toolbar contains actions and controls only; do not put a long-lived command prompt or event message at its trailing edge.

Example behaviour:

| Event | Overview content | Persistent command tier |
|---|---|---|
| User selects LINE | `LINE — Create connected line segments` | `Specify first point` |
| First point accepted | `First point recorded` | `Specify next point or Finish` |
| Circle completed | `Circle created` | Return to READY |
| Operation cancelled | `Operation cancelled` | Return to READY |
| Entities erased | `3 entities erased` | Return to READY |
| Selection changed | `2 entities selected` | Remains available for commands |

## 30.3 One consistent transient event system

All short-lived operational notifications must use a unified motion and lifecycle system. This includes:

- Tool selection and command activation.
- Operation started/completed/cancelled.
- Selection, deselection and selection counts.
- Erase/delete, copy, move, rotate and other modification results.
- QDIM generation/refresh and dimension counts.
- Export/import results.
- Validation feedback, non-fatal warnings and errors.
- Snap/grid/mode toggles where confirmation is useful.

No short-lived message should simply appear or disappear abruptly.

### Required motion

- Enter: fade from alpha 0 to 1 over approximately 180–240 ms.
- Optional movement: a subtle 4–8 dp rise/fall or slight scale settle; no elastic or spring bounce.
- Hold: approximately 2.5 seconds by default for ordinary status events.
- Exit: fade from alpha 1 to 0 over approximately 180–240 ms, optionally with the same restrained 4–8 dp translation.
- Respect the user's system animation/“remove animations” preference where the platform exposes it; when animations are reduced, use a short direct state change without delaying critical feedback.
- Avoid layout reflow: the Overview and notification overlay must be layered above the viewport and must not change viewport dimensions or transforms.

### Replacement and queue behaviour

The inspected source already uses `AnimatedVisibility` with `fadeIn`/`fadeOut` combined with vertical slide for the notification banner. However, `emitTransientNotification()` can replace one non-null `activeNotification` directly with another. Since visibility remains `true`, `AnimatedVisibility` alone does not guarantee that the new message receives a separate enter transition; the text can change abruptly while the container remains visible.

Correct this by using a keyed content transition or an explicit exit-then-enter sequence:

- Give each notification event a unique stable event ID/sequence number, even when two different events have identical text.
- Use `AnimatedContent` keyed by event ID, or sequence the old event's exit before the new event's entry.
- Do not rely only on `visible = notification != null` to animate changes between two non-null notification objects.
- Retain the existing duplicate-message coalescing where appropriate, but refresh its timeout without restarting an unnecessary animation for identical consecutive events.
- Queue events in order when they arrive faster than the display interval. Do not drop important operation outcomes or errors through immediate replacement.
- Bound the queue and coalesce repeated low-value events (for example, repeated cursor status). Preserve warnings and errors over routine informational events.
- A persistent/critical error may remain until acknowledged, but the way it appears must still use the agreed entrance animation.
- Ensure timer cancellation, lifecycle changes and rapid successive events cannot dismiss a newer message using an older event's timer.
- A new Overview event should not be hidden behind an unrelated queued notification; define a deterministic priority policy for critical errors, operation results and ordinary guidance.

## 30.4 Acceptance criteria

- [ ] No `commandPromptBanner` or explanatory guide text remains attached to the trailing end of the function toolbar.
- [ ] A separate floating Overview appears above/near the toolbar when an action needs explanation.
- [ ] The Overview is absent in idle state when no message is required.
- [ ] The Overview fades in and fades out smoothly.
- [ ] The Overview never changes viewport layout, scale, pan or cursor coordinates.
- [ ] Active command prompts remain visible until the required action is completed or cancelled.
- [ ] Selection, completion, cancellation, erase, import/export, warnings and errors all receive animated feedback.
- [ ] A notification replacing another notification transitions instead of changing text abruptly.
- [ ] Queue order and priority are deterministic.
- [ ] Critical errors are not silently discarded.
- [ ] Rapid notification sequences do not overlap, flicker or use stale timers to dismiss newer items.

---

# 31. QDIM diagnosis: missing profile dimensions and incomplete local-edge extraction

## 31.1 Source-derived root cause of the missing middle dimension

The inspected source is explicit about why the profile in the user's latest screenshot receives dimensions for its inclined edges but not for its internal horizontal/vertical edges.

File:
`app/src/main/java/com/example/cad/dimension/QuickDimensionEngine.kt`

The engine first decomposes each `PolylineEntity` into line and arc primitives through `CadGeometryKernel.explodePolylineToPrimitives()` (approximately lines 169–185 in the inspected snapshot). This is a sound starting point: the internal straight edges are available as `LineEntity` primitives in `allLines`.

However, the Tier 3 local-dimension pass (approximately lines 487–508) classifies each line and explicitly creates a dimension only when it is neither horizontal nor vertical:

```kotlin
if (!isHorizontal && !isVertical && line.length > 4.0) {
    // create DimensionType.ALIGNED
}
```

Therefore:

- Inclined edges receive `ALIGNED` dimensions.
- Horizontal local edges are discarded by this condition.
- Vertical local edges are discarded by this condition.
- The inner horizontal shelf/central segment in the user's profile is not dimensioned because the code never creates a candidate for it.
- The same omission applies to internal vertical shoulders or other orthogonal straight segments.

This is a candidate-generation omission, not merely a text-placement or screen-rendering defect. The aligner cannot arrange a dimension that the generator never creates.

## 31.2 Required local profile feature classification

Replace the inclined-only local dimension pass with a complete edge-feature classifier.

For every line primitive produced from a selected or analysed polyline:

1. Measure segment length and orientation from the actual endpoints.
2. Classify it as horizontal, vertical or inclined using a configurable angular tolerance.
3. Preserve the original endpoints and source polyline ID.
4. Determine whether the segment is an exterior boundary edge, an interior/re-entrant edge, a construction edge or an unrelated line.
5. Generate an appropriate dimension candidate for each eligible local edge according to the selected QDIM density policy.
6. Do not silently ignore an edge merely because it is horizontal or vertical.

Mapping:

- Horizontal segment → `LINEAR_HORIZONTAL` with endpoint coordinates retained.
- Vertical segment → `LINEAR_VERTICAL` with endpoint coordinates retained.
- Inclined segment → `ALIGNED` with endpoint coordinates retained.
- Arc/bulge segment → radius/diameter or arc-length candidate only when appropriate to the selected drafting policy. Do not dimension every fillet separately when a verified repeated-radius callout is more suitable.

## 31.3 Completeness and density policy

Keep the current three density modes, but define them by feature completeness rather than by a direction blind spot.

- **Sparse:** overall envelope dimensions and primary repeated-feature/radius callouts.
- **Balanced:** overall dimensions plus salient local shoulders, notch widths/depths, hole locations/pitches and important radii, while avoiding redundant dimensions.
- **Detailed:** every eligible unique straight profile segment (horizontal, vertical and inclined), required arc radii, feature locations and pattern relationships, with collision-aware arrangement.

For the user's concave multi-fillet profile, Detailed mode should consider every unique straight segment, including the inner horizontal shelf and the inner/outer vertical walls. The dimension-generation report must list any edge intentionally omitted and the reason (too short, duplicate, construction geometry, hidden/frozen layer, redundant under the chosen density policy, or geometrically ambiguous).

Do not use a fixed arbitrary minimum length of 4.0 model units as the sole gate for all drawings. Make the threshold relative to dimension style/text height and drawing scale, with an explicit user-configurable minimum feature length where useful. If a short segment is suppressed for legibility, report that it was omitted rather than silently pretending the profile is fully dimensioned.

## 31.4 Topology-aware grouping and redundancy control

A closed profile should be analysed as an ordered boundary, not merely as a bag of independent lines.

- Preserve polyline vertex order, closure state, bulge-to-edge association and source topology.
- Detect adjacent tangent arc/line boundaries and concave/convex corners.
- Identify horizontal/vertical chains and local steps.
- Group collinear adjacent edges only when doing so does not erase a meaningful vertex, notch, shoulder or manufacturing feature.
- For a re-entrant profile, dimension the notch width/depth and the relevant boundary segments explicitly.
- Avoid emitting the same measurement twice from both a segment dimension and an overall/chain dimension.
- Record candidate lineage: source entity ID, source segment index, endpoints, dimension type, and suppression/retention reason.

A complete dimension set is not necessarily one dimension for every primitive. The engine must distinguish meaningful design features from redundant edges, but every suppression must be explainable.

## 31.5 Dimension candidate diagnostics

Extend `DimensionAlignmentReport` so that it records more than successfully placed dimensions.

For every extracted dimensionable edge, report:

- Source entity and segment index.
- Orientation classification.
- Measured value.
- Candidate dimension type.
- Whether a candidate was generated.
- If not generated, a machine-readable suppression reason.
- If generated, its placement result and collision state.

This makes a missing dimension diagnosable: the agent can determine whether it was never generated, suppressed as redundant, rejected by a threshold, or lost during arrangement/rendering.

---

# 32. QDIM alignment diagnosis: text displaced from dimension geometry

## 32.1 Source-derived cause of scattered dimension labels

The inspected source contains a second concrete weakness in:

`app/src/main/java/com/example/cad/dimension/DimensionAlignmentEngine.kt`

Inside `buildLinearPlacementResult()` (approximately lines 798–881), collision handling changes `textAnchor` when an estimated text box intersects another occupied text box. The code shifts the text anchor to another position, but retains the original `plan` geometry.

The returned placement then contains:

- `dimLineStartModel` and `dimLineEndModel` from the original render plan.
- `ext1StartModel` and `ext1EndModel` from the original render plan.
- `ext2StartModel` and `ext2EndModel` from the original render plan.
- A modified `textAnchorModel` moved away from the original dimension line.

Consequently, the numeric label can be moved to avoid another label while its dimension line, extension lines and arrow locations remain where they were originally calculated. This can make the value appear detached, offset or arbitrarily placed in relation to its own dimension line.

This is a likely source-level explanation for the inconsistent label placement seen in the earlier hole-plate drawing. The exact visual severity depends on its candidate set, density, arrangement preference and current viewport scale; do not claim that every earlier screenshot defect has only this one cause.

## 32.2 Correct collision solution: relocate the complete dimension assembly

When a linear dimension collides with another annotation, relocate the whole dimension assembly as one coherent object:

- Dimension line.
- Both extension lines.
- Arrowheads.
- Text anchor and text rotation.
- Any required leader or witness geometry.

Preferred strategy:

1. Generate the complete dimension render plan.
2. Test its complete geometry and text bounds against existing annotations, profile edges and other occupied geometry.
3. Generate a set of candidate parallel tracks or offsets on the geometrically valid side of the feature.
4. Rebuild the entire render plan at each candidate location.
5. Recalculate both extension-line endpoints and dimension-line endpoints.
6. Recalculate arrow placement and text position using the same new plan.
7. Score candidate layouts by text overlap, line crossing, distance from feature, visual hierarchy and sheet-boundary constraints.
8. Select the lowest-cost valid complete placement.
9. If no candidate is clear, mark it as unresolved and report it. Do not silently move only its text.

A text-only relocation may be allowed only for a formally supported drafting convention in which the text is intentionally placed outside the dimension line with a clear leader or break. In that case, create the leader geometry explicitly; never leave a floating value with no visual association.

## 32.3 Improve arrangement for hole patterns and non-rectangular profiles

The current QDIM engine uses heuristic circle groupings for hole patterns:

- Circles are grouped into rows using rounded Y coordinates.
- Circles are grouped into columns using rounded X coordinates.
- It primarily dimensions the bottom row and rightmost column.
- Horizontal secondary dimensions are placed on a common bottom chain track.
- Vertical secondary dimensions are placed on a common right chain track.

These heuristics are efficient for a clean rectangular hole pattern, but can produce incomplete or poorly grouped dimensions when there are additional bores, non-rectangular outlines, asymmetric rows, imperfectly aligned centres or several feature clusters.

Required improvements:

- Use geometric clustering with an explicit tolerance in drawing units, not only quantisation via `(coordinate * 10).roundToLong()`.
- Derive candidate rows/columns from detected feature geometry and verify alignment residuals.
- Keep unrelated bores from changing the classification of a mounting-hole row.
- Detect more than only the bottom row and rightmost column when the selected geometry requires other dimensions.
- Support chain, baseline and ordinate layouts as deliberate alternatives.
- Assign each feature dimension to a logical group, datum and track.
- Do not force all local dimensions to the same bottom or right track if that causes excessive crowding.
- Use profile-side and feature-cluster awareness to choose the nearest appropriate annotation side.
- Avoid dimension-line/extension-line crossings with part geometry.
- Keep primary overall dimensions outside the secondary feature bands.
- Preserve the current repeated diameter/radius callout functionality where feature equivalence is actually verified.

## 32.4 Prevent false completeness claims

A high `layoutScore` must not imply that the drawing is fully dimensioned when some profile edges never became candidates.

- Report generation completeness separately from layout quality.
- `generationCompleteness` should account for eligible feature edges found, candidate dimensions created and candidates intentionally suppressed with reasons.
- `layoutQuality` should measure collisions, text overlap, line crossings and relocation cost among generated dimensions.
- A drawing may have excellent placement quality but incomplete feature coverage; the report must show both facts separately.
- Do not label the drawing `fully dimensioned` unless the eligible feature set has been accounted for.

---

# 33. Professional pure-black visual identity

## 33.1 Design direction

The current interface uses a bright neon visual language: intense cyan, amber, emerald and red outlines/fills, numerous accent-coloured chips and vivid status treatments. The user wants a more professional application suitable for technical education and engineering drafting, while retaining a genuinely black dark mode.

The new direction is **professional technical black**:

- Pure-black or near-absolute-black model-space background.
- Near-black panels, rails, command areas and overlays.
- Subtle separation through very dark tonal steps, fine borders and spacing—not broad grey cards.
- Neutral primary text and restrained secondary text.
- One subdued primary interaction accent, not multiple simultaneous neon accents.
- Status colours used only where semantically necessary.
- No decorative glow, bloom, luminous edges, neon outlines, gradients or bright filled chips.

Do not convert the application to a generic grey dark theme. The user's explicit requirement is to preserve black.

## 33.2 Suggested starting palette

The values below are a starting design system, not immutable final colours. Validate them on the actual phone's AMOLED/OLED display and check readability.

| Role | Suggested colour | Use |
|---|---|---|
| Model-space canvas | `#000000` | Pure-black drawing background |
| Primary panel | `#050608` | Header, bottom tiers, command panel |
| Elevated panel | `#090C10` | Drawers, dialogs, floating Overview |
| Secondary panel | `#0D1117` | Input wells and limited nested surfaces |
| Hairline border | `#202630` | Fine structural separation |
| Strong neutral border | `#303946` | Active panel boundary, focused input |
| Primary text | `#E6E9ED` | Main labels and command text |
| Secondary text | `#A4ADB8` | Secondary labels and controls |
| Muted text | `#737E8B` | Hints and non-essential status |
| Main accent | `#7892A8` | Focus, selected item and primary active state |
| Secondary accent | `#8C9BA8` | Secondary interactive affordance |
| Snap/precision state | `#B39A69` | OSNAP/precision indicator, used sparingly |
| Success | `#729783` | Success state only |
| Warning | `#B79A63` | Warning state only |
| Error | `#B87575` | Error state only |

Keep the model-space background exactly black if that remains the desired drafting surface. Slightly lifted panel tones are acceptable only if they remain visibly black rather than reading as grey cards.

## 33.3 Component styling rules

- Replace neon-filled action buttons with dark/black surfaces, quiet borders and neutral foreground icons/text.
- Selected controls may use the muted main accent as a thin border, small indicator or subdued fill; avoid saturated solid fills.
- Reduce the number of simultaneously coloured chips.
- Use one dominant accent for ordinary interactive focus. Reserve green, amber and red for success, warning and error meaning.
- Avoid making all controls appear equally important. Use hierarchy: primary action, secondary action, status/disabled.
- Use restrained corner radii and consistent line weights.
- Use subtle focus states rather than luminous glows.
- Preserve touch target size; professional styling must not reduce accessibility or precision.
- Do not render non-essential button borders around every word if spacing and typographic hierarchy communicate grouping more cleanly.
- Use a consistent icon family and line weight.
- Keep the toolbar and command region visually quiet so the technical drawing remains the focal point.

## 33.4 CAD drawing colours are not the UI theme

Do not indiscriminately recolour geometry, dimension layers, axes, snap markers or imported drawing entities when redesigning the application UI.

Keep these separate:

1. **Application chrome palette:** buttons, panels, toolbar, status indicators, Overview and dialogs.
2. **CAD entity/layer palette:** geometry and annotation colours controlled by layers, entity properties and drafting standards.
3. **Transient interaction overlays:** cursor, snap aperture, selection preview, grip and construction preview.

The UI may become monochromatic while drawing layers retain the colours needed to distinguish geometry and dimensions. Provide a coherent default technical drawing palette, but preserve user-defined layer colours and DXF colour mapping.

## 33.5 Pure-black and accessibility requirements

- Avoid broad grey background surfaces.
- Maintain sufficient foreground/background contrast. Small text should target at least a 4.5:1 contrast ratio; larger text and essential non-text UI indicators should meet appropriate accessibility contrast requirements.
- Check disabled, selected, focused and warning states independently; do not rely only on colour to communicate them.
- Do not allow Android dynamic colour/wallpaper theming to unexpectedly replace the application's fixed black product identity.
- Test on OLED at low brightness as well as on standard LCD displays.
- Check anti-aliased fine line text and thin borders; increase luminance contrast or stroke weight where necessary.
- Preserve the current black-background drawing readability.

Android accessibility guidance explains that foreground/background contrast affects readability and recommends at least 4.5:1 for small text and 3:1 for large text. Android's Compose animation documentation supports `AnimatedVisibility`, `fadeIn` and `fadeOut` for controlled appearance/disappearance transitions:
- `https://support.google.com/accessibility/android/answer/7158390`
- `https://developer.android.com/develop/ui/compose/animation/composables-modifiers`

## 33.6 Theme acceptance tests

- [ ] Model-space canvas is pure black (`#000000`) or the explicitly approved black value.
- [ ] Main UI panels read as black/near-black, not grey.
- [ ] Neon cyan/amber/green full-fill treatments are removed from ordinary controls.
- [ ] A restrained primary accent is used consistently.
- [ ] Success/warning/error colours are reserved for their semantic meanings.
- [ ] CAD layer/entity colours remain independent of UI chrome colours.
- [ ] Text, icons and control boundaries remain legible at low screen brightness.
- [ ] Small text contrast targets at least 4.5:1.
- [ ] No decorative glow, bloom or neon gradient remains.
- [ ] The drawing remains the visual focal point.

---

# 34. Updated implementation order — latest refinements

Add these tasks to the existing implementation order, preserving its previous priorities and non-regression constraints:

1. **Overview and event motion:** remove trailing guide text from the function toolbar; create the independent floating Overview; animate all notification entries, replacements and exits.
2. **QDIM generation completeness:** add horizontal and vertical local polyline segment candidates and profile-aware feature extraction.
3. **QDIM coherent collision relocation:** move/rebuild complete dimension assemblies rather than text labels alone.
4. **QDIM diagnostics:** report generated, retained and suppressed feature dimensions separately from layout quality.
5. **Professional black theme:** move the application chrome to the restrained pure-black visual system while preserving layer/entity colours.
6. **Regression:** re-run both the six-hole mounting plate dimension scenario and the user's multi-fillet concave profile. Verify local segment coverage, no missing middle shelf dimension, no detached text and no collisions.

Do not declare QDIM fixed merely because the current screenshot looks tidy. Test candidate-generation completeness and placement geometry independently.


---

# 35. QDIM state synchronisation and duplicate-generation correction

## 35.1 Observed behaviour to correct

The user reports this sequence:

1. Enable Quick Dimension / Quick Alignment from the QDIM control in the function/tool area or through the command line.
2. The generated dimensions appear, and the operation appears to succeed.
3. The QDIM status control in the top header still displays OFF.
4. The user then enables QDIM through the top-header QDIM option.
5. The automatic dimension overlay appears a second time, producing duplicated dimensions.

The required result is one consistent state and one generated automatic dimension set, regardless of which supported entry point is used.

## 35.2 What the inspected source actually contains

The reviewed source snapshot is `PrecisionCAD-Studio-main (1).zip` / `PrecisionCAD-Studio-main (1).zip` as supplied for the preceding source review. Relevant files:

- `app/src/main/java/com/example/ui/CadWorkspaceScreen.kt`
- `app/src/main/java/com/example/ui/CadWorkspaceViewModel.kt`
- `app/src/main/java/com/example/ui/components/CadDialogsAndPanels.kt`
- `app/src/main/java/com/example/cad/dimension/QuickDimensionEngine.kt`

The inspected implementation has a single QDIM status property:

`CadWorkspaceUiState.isQuickDimensionEnabled`

The relevant paths are:

| Entry point | Current source path | Expected state effect |
|---|---|---|
| Top header QDIM button | `TopEngineeringHeaderBar` → `onOpenQDim` → `QuickDimensionDialog` | Opens the QDIM dialog. Header text `QDIM: ON/OFF` is derived from the same `uiState.isQuickDimensionEnabled`. The header button itself is a dialog launcher, not a direct toggle in this source snapshot. |
| QDIM dialog switch | `QuickDimensionDialog` → `onToggleQuickDimension` → `viewModel.setQuickDimensionEnabled` | Mutates the shared ViewModel state and, when enabling, generates dimensions. |
| QDIM contextual toolbox toggle | `qdim_bar_toggle` → `viewModel.setQuickDimensionEnabled` | Mutates the same shared state. |
| Command line `QDIM` | `submitCommandOrCoordinateInput()` → `executeQuickDimension(..., replaceAutoDims = true)` | Generates dimensions and sets the shared enabled state to true. |
| Command registry QDIM selection | `activateCommand(CadCommandId.QDIM)` → `executeQuickDimension(..., replaceAutoDims = true)` | Generates dimensions and sets the shared enabled state to true. |
| QDIM refresh button | `refreshQuickDimensions()` → `executeQuickDimension(..., replaceAutoDims = true)` | Replaces/regenerates automatic dimensions for the drawing. |

The portrait and landscape header instances both receive `uiState.isQuickDimensionEnabled`. The QDIM dialog switch also receives that same value. The source therefore does **not** show three independent ON/OFF booleans. In this snapshot, a successful `executeQuickDimension()` sets `isQuickDimensionEnabled = true`; the header should consequently display ON on the next StateFlow recomposition.

Do not invent a second Boolean state to repair the symptom. Verify the actual runtime state and binding path. If the latest working checkout has diverged from the inspected archive, compare that checkout before implementing the fix.

## 35.3 Confirmed duplicate-generation path in the current source

The strongest directly demonstrated duplicate-generation risk is the optional `replaceAuto` parameter in `QuickDimensionDialog`.

In `CadDialogsAndPanels.kt`:

- The dialog declares `var replaceAuto by remember { mutableStateOf(true) }`.
- It displays a switch described as `Refresh previous auto-dimensions (Manual annotations are always preserved)`.
- It passes this value to `onExecuteQDim(mode, density, replaceAuto, arrangement)`.

In `CadWorkspaceViewModel.executeQuickDimension()`, that Boolean is forwarded to:

`AutoDimensionEngine.generateQuickDimensionsWithAlignmentReport(..., replaceExistingAutoDims = replaceAutoDims)`

In `QuickDimensionEngine.kt`, when `replaceExistingAutoDims` is false, the engine uses `allEntities` as `preservedEntities`, then appends the newly generated centre marks and resolved dimensions. That path can preserve the old generated dimension set and append another one.

When `replaceExistingAutoDims` is true, `removeAutoDimensions()` filters out `DimensionEntity` values with `isAutoGenerated == true` and auto-generated `CenterMarkEntity` instances with a non-null `sourceEntityId`, before adding the replacement set.

Therefore:

- `replaceAutoDims = false` is a real append path and can produce duplicated automatic dimension overlays.
- The QDIM dialog exposes this path through a switch.
- The normal toolbox toggle and command-line QDIM paths pass true in the inspected code.
- Re-enabling QDIM through the same ViewModel method while it is already ON currently reruns dimension generation and records another undo snapshot; it should instead be idempotent or explicitly routed to Refresh.

This is a confirmed implementation risk. It is not yet proven to be the only cause of the user's exact runtime sequence.

## 35.4 State architecture: one authoritative source of truth

Use exactly one authoritative QDIM feature state in `CadWorkspaceUiState` / `CadWorkspaceViewModel`.

- Do not add local `remember { mutableStateOf(...) }` ON/OFF flags in the header, toolbox, dialog or command component.
- Do not maintain separate `isHeaderQDimEnabled`, `isToolQDimEnabled`, `isCommandQDimEnabled` or `isDialogQDimEnabled` variables.
- Every UI presentation must derive its visible state directly from `uiState.isQuickDimensionEnabled`.
- Every entry point must invoke a common ViewModel intent method.
- All enabled/disabled state transitions must be committed in one state update after the relevant document operation succeeds.
- Keep density, arrangement preference, last report and generated-dimension provenance in the same shared state/document model where appropriate.
- The top header status label must never be driven by a locally remembered value or a stale parameter captured by a long-lived lambda.
- Keep one QDIM control state across portrait/landscape composition branches, drawer visibility, controller expansion and dialog lifecycle.

The top header QDIM control currently opens the QDIM settings dialog despite looking like an ON/OFF status button. The implementation may preserve that role, but its status must always reflect the shared state. Do not turn it into a second independent toggle. If a direct header toggle is desired, route it through the same central ViewModel intent and keep configuration in the dialog; do not create another state variable.

## 35.5 Separate Enable, Disable, Configure and Refresh intents

The current `setQuickDimensionEnabled(enabled)` method should become idempotent.

### Enable

- If QDIM is OFF, enable it and generate exactly one automatic dimension set.
- If QDIM is already ON, do not regenerate dimensions and do not append anything.
- If QDIM is already ON, return without creating an unnecessary undo snapshot or duplicate notification.
- Set the shared status to ON only after successful generation and document update.

Conceptual behaviour:

```kotlin
fun setQuickDimensionEnabled(enabled: Boolean) {
    val current = _uiState.value
    if (current.isQuickDimensionEnabled == enabled) return

    if (enabled) {
        enableAndGenerateOnce()
    } else {
        disableAndRemoveGeneratedDimensions()
    }
}
```

The exact implementation must preserve the current undo/redo and error-handling architecture; this pseudocode expresses the required state semantics.

### Disable

- Remove only the QDIM-owned generated dimension and centre-mark entities.
- Preserve manual dimensions, text, leaders, user-created centre marks and all geometric entities.
- Set the shared enabled flag to false in the same logical transaction.
- Do not leave the header or contextual toolbox control showing ON after the generated set has been removed.

### Configure

Changing density or arrangement while QDIM is enabled is a regeneration operation:

1. Snapshot the document once.
2. Remove the previous QDIM-owned generated set.
3. Generate the new set using the requested density/arrangement.
4. Commit the new set and configuration together.
5. Keep the feature ON.
6. Emit one operation result.

Changing density or arrangement while QDIM is OFF should update the preference only; it should not create dimension entities.

### Refresh

Refresh is the only ordinary path that regenerates an already-enabled QDIM set without changing ON/OFF state.

- Refresh always replaces the previous QDIM-generated set.
- Refresh never appends to the previous set.
- Refresh does not change manual dimensions.
- Refresh runs at most once for one user action.
- Refresh is distinct from enabling and should have a distinct method/intent such as `refreshQuickDimensions()`.

## 35.6 Remove the unsafe append switch from the standard QDIM dialog

For normal QDIM operation, remove the `replaceAuto` switch that permits `replaceAutoDims = false`.

The standard dialog should never offer a mode that silently appends another copy of the QDIM-generated dimension set. The UI should state plainly that **Refresh replaces existing automatic dimensions while preserving manual annotations**.

If there is a future legitimate need to retain an earlier automatic set as a separate drawing revision, implement an explicit, separately named archival/convert-to-manual operation. Do not represent that behaviour with an ambiguous "Refresh previous auto-dimensions" checkbox.

The standard QDIM enable, command and refresh paths must always use the replace-generated-set behaviour.

## 35.7 Generated-entity ownership and idempotent regeneration

Do not rely solely on visual equality of dimension values to detect duplicates.

Every automatic dimension and automatic centre mark must have explicit QDIM provenance:

- `isAutoGenerated = true` for every generated `DimensionEntity`.
- A generated-source identifier or owner tag linking it to the QDIM engine/document.
- Stable source geometry IDs and dimension-feature identity where feasible.
- Generated centre marks must carry a valid source entity ID and QDIM ownership.
- Manual user-created dimensions must remain `isAutoGenerated = false` and must never be deleted by a normal QDIM refresh.

Before regeneration:

1. Find and remove the complete previous QDIM-owned set.
2. Preserve all manual annotations.
3. Generate the new set once.
4. Verify that every generated entity has its QDIM provenance marker.
5. Verify uniqueness of the generated feature identity within the new set.
6. Commit the entity-list replacement atomically.

Where data migration or old document loading has lost the `isAutoGenerated` marker, use explicit provenance metadata or a safe migration strategy. Do not delete manual annotations merely because they visually resemble an auto dimension.

## 35.8 Rendering must have one source of truth

The current `CadViewportCanvas` draws stored `DimensionEntity` instances through the normal entity-rendering path. Keep this as the single authoritative rendering path for committed QDIM dimensions.

- Do not render the same QDIM result once from `entities` and a second time from `lastAlignmentReport`.
- The alignment report is diagnostic/placement metadata, not a second set of drawable entities.
- Do not maintain a separate local Compose list of dimension overlays in addition to the document entity list.
- Temporary in-progress dimension previews may use a separate preview layer, but must be clearly transient and must never duplicate committed entities.
- A completed QDIM refresh must replace committed entities, not leave old overlay primitives in a retained Canvas cache.
- Invalidate the renderer/cache by document revision and stable entity identity when the set is replaced.

## 35.9 Runtime diagnosis and instrumentation

Because the reported stale header status should not occur in the inspected source snapshot, instrument the exact runtime paths before declaring the issue fixed.

For each QDIM intent, log or expose in a debug-only diagnostic record:

- Trigger source: `HEADER_DIALOG`, `TOOLBAR_TOGGLE`, `COMMAND_LINE`, `COMMAND_REGISTRY`, `DIALOG_REFRESH`, `DENSITY_CHANGE` or `ARRANGEMENT_CHANGE`.
- `isQuickDimensionEnabled` before and after the operation.
- Whether the operation was Enable, Disable, Configure or Refresh.
- `replaceAutoDims` value (the standard user-facing paths must always be true).
- Number of existing QDIM-owned dimensions before generation.
- Number of old generated dimensions removed.
- Number of new generated dimensions produced.
- Number of manual dimensions preserved.
- Number of QDIM-owned dimensions after commit.
- Count of duplicate generated feature keys, which must be zero.
- Document revision and operation ID.

The header, toolbox and dialog should also expose the same state in test tags/semantics so automated UI tests can assert consistency. Remove or compile out verbose diagnostics for production builds after the defect is resolved.

## 35.10 QDIM acceptance and regression tests

### State synchronisation

- [ ] Enable QDIM from the toolbox; the top header changes to ON on the same committed state update.
- [ ] Enable QDIM from command-line `QDIM`; the top header and toolbox show ON.
- [ ] Enable QDIM from the top-header dialog; the toolbox and command context show ON.
- [ ] Disable QDIM from the dialog; the top header and toolbox show OFF.
- [ ] Disable QDIM from the toolbox; the top header and dialog show OFF.
- [ ] Change density while enabled; all controls remain ON and the current generated set is replaced once.
- [ ] Change arrangement while enabled; all controls remain ON and the current generated set is replaced once.
- [ ] Portrait and landscape header instances display identical status.
- [ ] Dismissing/reopening the dialog does not reset or fork the status state.

### Idempotency and duplication

- [ ] Calling Enable when already ON performs no regeneration.
- [ ] Calling Enable repeatedly leaves the QDIM-owned entity count unchanged.
- [ ] Invoking the same QDIM command twice with standard replacement semantics leaves one generated set, not two.
- [ ] Refresh replaces the old set and leaves no duplicate generated feature keys.
- [ ] Enabling from one entry point and then opening the top dialog does not generate another set merely by opening the dialog.
- [ ] Opening/dismissing the top dialog does not change the drawing.
- [ ] The standard UI contains no option that passes `replaceAutoDims = false`.
- [ ] Every generated dimension and centre mark is recognised as QDIM-owned.
- [ ] Manual dimensions and manual centre marks remain untouched.
- [ ] One user action produces one logical document transaction and one undo step.
- [ ] The Canvas renders the committed QDIM entity set once; diagnostic reports are not rendered as extra dimensions.

### State/overlay regression sequence

1. Start with QDIM OFF and no generated dimension entities.
2. Enable QDIM using the command/tool route.
3. Assert the header, toolbox and dialog all display ON.
4. Count QDIM-owned dimensions and centre marks.
5. Open the top QDIM dialog and dismiss it without changing settings; assert counts remain unchanged.
6. Open the dialog, change no replacement option (the option must no longer exist), and refresh; assert the old set was replaced and total QDIM-owned count remains stable for unchanged geometry/settings.
7. Disable QDIM from the dialog; assert all QDIM-owned entities are removed and manual annotations remain.
8. Enable from the top dialog; assert exactly one set is generated.
9. Invoke the command-line QDIM route; assert replacement, not append.
10. Verify one visible rendering of each generated dimension and no double-stroked/overprinted labels.

---

# 36. Updated implementation order — QDIM synchronisation

Add the following work after the existing Overview/QDIM generation/alignment work:

1. Audit the latest working checkout against the inspected source snapshot.
2. Consolidate every QDIM entry point into one ViewModel intent interface and shared `isQuickDimensionEnabled` state.
3. Make Enable/Disable idempotent and separate them from explicit Refresh.
4. Remove the standard dialog's `replaceAuto = false` append path.
5. Ensure all generated dimensions/centre marks carry durable QDIM ownership/provenance.
6. Make regeneration a single atomic replace transaction preserving manual annotations.
7. Verify that `CadViewportCanvas` draws QDIM dimensions only through the committed document entity list.
8. Add cross-entry-point state and entity-count regression tests.
9. Reproduce the user's exact sequence: enable through toolbox/command, inspect top header, then open the top QDIM dialog and refresh. Verify no duplicate dimension overlay is possible.

Do not mark this defect fixed merely because the header text changes to ON. The generated entity count and visible rendering must also prove that only one QDIM set exists.


---

# 37. QDIM master action: Clear All Dimensions

## 37.1 Purpose and placement

Add a clearly labelled **Clear All Dimensions** action to the Quick Dimension control surface opened from the top-header QDIM control. The same action must be reachable from the toolbox-launched QDIM control surface so that users do not encounter different QDIM capabilities depending on the entry point.

This is a document-wide master cleanup action. It is distinct from:

- Turning automatic QDIM generation OFF.
- Refreshing automatic dimensions.
- Deleting selected entities.
- Clearing only QDIM-generated dimensions.

The action must be easy to find but visually distinguished as destructive. Place it in a clearly separated cleanup section of the QDIM control surface, not beside the ordinary ON/OFF switch in a way that makes accidental activation likely.

Suggested action label: **CLEAR ALL DIMENSIONS**.

## 37.2 Exact deletion scope

After explicit confirmation, remove **every dimension object in the current drawing document**, regardless of:

- Whether it was generated automatically by QDIM or created manually.
- Whether `isAutoGenerated` is true or false.
- Whether it has QDIM provenance or belongs to another dimension command.
- Its dimension subtype: linear, horizontal, vertical, aligned, angular, radius, diameter, ordinate, baseline, continued/chain or any other supported dimension type.
- Its placement layer, including visible, hidden, frozen or otherwise non-displayed layers.
- Its drawing space: model space and every editable paper-space/layout space belonging to the current document.

Do not filter the master operation using `isAutoGenerated`. That filter is appropriate for ordinary QDIM disable/refresh, not for this all-dimensions reset.

The operation must clear the dimensional presentation in the viewport as well as the underlying document entities. If any QDIM dimension graphics are held in a separate preview/cache/report/overlay collection, invalidate and clear those dimension-only render artefacts too. Do not leave a duplicated dimension visible after its source entity has been removed.

### Preserve everything that is not a dimension

The master clear must preserve:

- All actual model geometry: lines, polylines, arcs, circles, ellipses, splines, points, rays, construction geometry and block references.
- Ordinary TEXT, MTEXT, leaders, notes and other annotations that are not dimension objects.
- Independent centre marks and centre lines.
- Layers, layer names, colours, visibility/frozen state and line types.
- Units, coordinate systems, constraints, blocks, sheet/page settings, title blocks and document metadata.
- Selection-independent object properties and geometry coordinates.

If the document internally represents a dimension as a parent `DimensionEntity` with generated child graphics, remove the parent dimension object and its dimension-owned child graphics as one unit. Do not delete unrelated ordinary lines or text merely because they are visually near the dimension.

If a legacy/imported document stores a dimension as decomposed primitive entities, delete those primitives only when durable metadata/provenance identifies them as belonging to a dimension object. Do not guess by proximity, colour, text content or layer name. If no reliable provenance exists, report that such legacy exploded annotations cannot be safely distinguished rather than deleting ordinary geometry.

If a drawing uses dimensions nested inside shared block definitions, do not mutate the reusable block definition silently. Count and report those separately; a future explicit block-definition cleanup can be added if safe block-editing support exists.

## 37.3 Explicit confirmation

Require confirmation before executing the operation.

At confirmation time:

1. Count every dimension object in scope.
2. Capture the current document revision.
3. Show the exact number of dimensions that will be removed.
4. State that both manually created and automatically generated dimensions will be cleared.
5. State that geometry and unrelated annotations will be preserved.
6. Provide explicit **Cancel** and **Clear Dimensions** actions.

Suggested confirmation wording:

**Clear all dimensions?**

`This will remove 18 dimensions from this drawing, including manual and automatically generated dimensions. Geometry and other annotations will remain. This action can be undone.`

The displayed count must be calculated from the live document model, not guessed from the currently visible viewport. Hidden/frozen-layer dimensions and dimensions outside the current zoom must be included.

If the document revision changes while the confirmation is open, re-count and revalidate immediately before committing. Update the confirmation if the count changed, or require the user to confirm the new count.

Dismissal, tapping outside, Back navigation or choosing Cancel must leave the document untouched.

## 37.4 Atomic deletion, undo and redo

Implement the clear as one document-level transaction:

1. Capture the current document revision.
2. Enumerate all in-scope dimension entity IDs across model space, paper space and all editable layouts.
3. Enumerate dimension-owned child graphics and QDIM-generated supporting artefacts whose ownership is explicitly recorded.
4. Create one undo record containing the exact entities and prior QDIM state.
5. Remove all dimension entities and their owned graphics in one atomic model update.
6. Preserve all non-dimensional entities and metadata.
7. Set `isQuickDimensionEnabled = false` in the same logical transaction. The top header, toolbox and QDIM dialog must all show OFF after commit.
8. Clear the current QDIM alignment report and all dimension-only preview/render caches. Invalidate spatial indexes, selection references and inspector state for deleted dimension IDs.
9. Commit one document revision.
10. Emit one animated success Overview, for example `18 dimensions cleared`.

Undo must restore every deleted manual and automatic dimension, the dimension-owned supporting graphics, and the previous QDIM enabled state. Redo must repeat the complete operation.

Do not create one undo entry per dimension. Do not publish a sequence of partially cleared frames while deleting entities one by one.

If no dimensions are present, do not create an undo record. Show a short animated Overview: `No dimensions found`.

## 37.5 Interaction with QDIM Enable, Disable, Configure and Refresh

Keep this master action separate from the idempotent QDIM state logic in Section 35.

| Operation | Automatic dimensions | Manual dimensions | QDIM enabled state |
|---|---|---|---|
| Disable QDIM | Remove QDIM-owned set | Preserve | OFF |
| Enable QDIM | Generate one set if currently OFF | Preserve | ON |
| Refresh QDIM | Replace QDIM-owned set | Preserve | ON |
| Change density/arrangement while ON | Replace QDIM-owned set once | Preserve | ON |
| **Clear All Dimensions** | **Remove all** | **Remove all** | **OFF** |

After Clear All Dimensions:

- Do not automatically regenerate dimensions because QDIM was ON before the clear.
- Merely opening or dismissing the header QDIM dialog must not regenerate dimensions.
- The user may explicitly enable QDIM again or issue the QDIM command when ready.
- Re-enabling QDIM creates one new automatic set and does not restore dimensions that were manually cleared.
- Manual dimension commands remain available while automatic QDIM is OFF.
- The header, dialog and toolbox all report OFF after the clear transaction commits.

## 37.6 Implementation guidance

Add a dedicated ViewModel action with explicit semantics, for example:

`clearAllDimensionsConfirmed()`

Do not implement this by calling `setQuickDimensionEnabled(false)`. That ordinary method must continue to remove only the QDIM-owned generated set and preserve manual dimensions.

The master clear must query the document model, not just the currently rendered/visible entity list. It must include hidden/frozen layers and off-screen dimensions.

Use stable entity IDs and a single document transaction. Ensure that:

- All affected model/paper-space collections are enumerated.
- Dimension-owned child graphics are removed with their parent.
- Independent centre marks and ordinary annotations survive.
- QDIM reports/previews/caches cannot redraw stale dimensions.
- Selection sets and inspectors no longer reference deleted dimension entities.
- PDF and DXF exports reflect the cleared document.
- The operation remains fully undoable and redoable.

Do not infer dimension ownership from colour, proximity, text value, layer name or visual similarity.

## 37.7 Acceptance tests

- [ ] The top-header QDIM control surface contains a clearly separated Clear All Dimensions action.
- [ ] The toolbox-launched QDIM control surface exposes the same action.
- [ ] Confirmation states that both manual and automatic dimensions will be removed.
- [ ] Confirmation displays the exact document-wide count.
- [ ] Cancel, dialog dismissal and Back leave the document unchanged.
- [ ] Confirm removes every supported dimension subtype.
- [ ] Both auto-generated and manually created dimensions are removed.
- [ ] Dimensions on hidden/frozen layers are removed.
- [ ] Dimensions in model space and every editable paper-space/layout space are removed.
- [ ] Dimensions outside the current viewport are removed.
- [ ] Dimension-only cached overlays and previews are cleared; no duplicate remains visible.
- [ ] Actual model geometry remains unchanged.
- [ ] Ordinary text, leaders, notes, independent centre marks and centre lines remain unchanged.
- [ ] Layer properties, units, blocks, constraints, page settings and document metadata remain unchanged.
- [ ] QDIM-owned dependent child graphics are removed without deleting unrelated primitives.
- [ ] QDIM status becomes OFF in header, dialog and toolbox in the same committed transaction.
- [ ] Reopening the QDIM dialog does not regenerate dimensions.
- [ ] Exactly one undo entry is created for the complete clear.
- [ ] Undo restores every dimension and the previous QDIM state.
- [ ] Redo clears them again.
- [ ] An empty document produces `No dimensions found` without an empty undo entry.
- [ ] PDF and DXF exports contain no cleared dimensions.
- [ ] Re-enabling QDIM after a clear produces exactly one new automatic set.
- [ ] The clear action never deletes ordinary geometry or unrelated annotations.

---

# 38. Updated implementation order — master dimension cleanup

Add the following after QDIM state synchronisation in Section 36:

1. Add Clear All Dimensions to the shared header-launched and toolbox-launched QDIM control surface.
2. Implement document-wide dimension enumeration across model space, paper layouts and all layers.
3. Add an explicit confirmation dialog with exact count and clear/cancel choices.
4. Implement one atomic deletion transaction that removes automatic and manual dimensions.
5. Clear associated dimension-owned child graphics and dimension-only render/report caches.
6. Set the shared QDIM state to OFF in the same transaction, without automatic regeneration.
7. Add complete undo/redo coverage.
8. Verify that geometry, ordinary text, leaders, independent centre marks, layers and document metadata are unchanged.
9. Test: mixed manual/automatic dimensions → Clear All → verify drawing preservation → Undo → Redo → explicitly enable QDIM → verify exactly one new automatic set.


---

# 39. Latest UX refinements: touchpad reversion, two-layer notifications, category browser and two-finger pan

This section captures the user's latest review of the supplied screenshots. It supersedes earlier conflicting requirements in Sections 19, 21, 27 and 30 wherever stated below.

## 39.1 Restore the prior relative touchpad; remove only the directional nudge arrows

The user has rejected the newer touchpad movement treatment and wants to return to the earlier behaviour that was familiar and predictable. Do not continue with the touch-slop-gated touchpad introduced in Sections 26–27, and do not add a new velocity-based acceleration curve to this reverted touchpad.

### Restore the prior movement semantics

- Restore the earlier relative touchpad behaviour: pointer displacement within the touchpad is translated directly into relative virtual-cursor displacement using the existing base `touchpadSensitivity` and current `pixelsPerModelUnit` conversion.
- Keep the existing touchpad movement feel and sensitivity settings. Do not introduce a new dead zone, touch-slop activation phase, discarded first movement, velocity curve, acceleration multiplier, cursor fling or inertial drift.
- A pointer-down establishes the initial sample; it does not itself move the virtual cursor. Subsequent measured pointer movement produces relative cursor movement. A stationary tap with no movement therefore produces no displacement.
- Do not add a single-tap, double-tap, long-press, click or context-menu action to the touchpad.
- Consume pointer sequences that begin within the controller overlay so they cannot become sheet taps, selections or command actions. This pointer ownership requirement remains in force even though the movement-intent gate is removed.
- A double-tap is not a command or point-accept action. Its actual measured pointer movement, if any, follows the ordinary relative movement mapping; do not introduce a special double-tap displacement or cursor jump.

### Touchpad layout: remove only the nudge arrows

In relative touchpad mode:

- Hide the four directional nudge arrows and the adjacent `NUDGE` label.
- Reclaim the width formerly occupied by the nudge cluster and give it to the touchpad surface.
- Keep the remaining controller UI as it existed in the previous accepted layout: mode selector, coordinates HUD, settings, step-size controls if they were already present, PICK PT, command-level accept/confirm and cancel controls.
- Do not remove PICK PT, Enter/confirm, Escape/cancel or other previously working touchpad-mode controls as part of this reversion.
- Do not create empty spacer columns where the nudge arrows were.
- Expand the touchpad proportionally into the freed space while retaining safe margins and sensible aspect ratio.
- Do not change the joystick mode: preserve its nudge arrows, step controls, PICK PT, Enter and Escape, as well as its existing movement algorithm.

**Important precedence:** This is an explicit reversal of the narrower touchpad-only panel requested in Section 27.2. The latest instruction is to restore the earlier layout and behaviour, with the directional nudge cluster as the sole controller element removed from touchpad mode.

### Acceptance tests

- [ ] The touchpad uses the earlier direct relative-delta mapping and existing sensitivity.
- [ ] There is no touch-slop-gated activation or discarded first drag movement.
- [ ] There is no new velocity-acceleration curve, fling or inertial drift.
- [ ] The four nudge arrows and `NUDGE` label are absent in touchpad mode.
- [ ] The touchpad expands into the space reclaimed from the nudge cluster.
- [ ] All other previously working touchpad controller controls remain available.
- [ ] A stationary touch causes no cursor displacement.
- [ ] No tap/double-tap/long-press action is registered by the touchpad.
- [ ] Touchpad pointers remain isolated from sheet pan/zoom and CAD selection.
- [ ] The joystick layout and motion algorithm remain unchanged.

---

## 39.2 Two-layer notification and active-session Overview behaviour

The screenshots establish two distinct message situations. They must not be collapsed into one generic toolbar status string.

### A. Bottom Overview — primary guidance and active-session area

The bottom Overview is a floating panel positioned above the function toolbar. It is not attached to the toolbar and must not consume layout space or shift the viewport.

It has two modes:

**Transient guidance mode**
- Used for ordinary descriptions, confirmations and short operation results when no command/session currently occupies the Overview.
- Example: `CLEAR SELECTION — 0 objects selected`.
- Appears in the bottom Overview with a smooth fade-in.
- Remains for the configured short display interval (approximately 2–3 seconds by default).
- Fades out smoothly.
- No duplicate top notification is created for this ordinary message.

**Active-session mode**
- Used when an operation requires a continuing sequence of user actions, such as FILLET, CHAMFER, LINE, ARC, CIRCLE or another multi-step command.
- Contains the active command title, the current instruction, the relevant coordinate/parameter guidance, and any session-specific choices or actions needed to complete the step.
- Example: `FILLET — Pick first intersection` plus the current prompt/parameter such as radius, and the appropriate confirm/accept and cancel actions.
- Appears as a floating Overview panel using the same restrained fade-in treatment.
- Remains visible for the entire active session and does not fade out merely because its ordinary notification timeout elapsed.
- Updates smoothly when the command advances to another step.
- Disappears with a fade-out only when the command completes, is accepted, or is cancelled.
- Must not remain as an inline bar inserted between the function toolbar and the persistent command-entry field.

The actual command-entry field remains persistent in its dedicated command tier. The Overview is the floating explanation/action surface for the current session, not a replacement for command entry.

### B. Top notification — overflow/status lane only while bottom Overview is occupied

A separate top transient notification lane is required, but it must be used conditionally.

- When no active session occupies the bottom Overview, ordinary messages appear only in the bottom Overview. The top notification lane remains hidden.
- When an active session is occupying the bottom Overview and another notification event occurs, display that additional event in the top notification lane.
- The top notification must fade in, remain briefly, and always fade out automatically.
- A top notification must never replace or dismiss the active-session Overview.
- The top lane may briefly show a status that is also summarised by the active Overview; this is intentional when required by the event model and must not be treated as accidental duplicate rendering.
- The top lane is not a second permanent status bar. Hide it when there is no active session and no overflow event.

### Message routing policy

| Bottom Overview state | New event | Presentation |
|---|---|---|
| Free / idle | Ordinary guidance or operation result | Bottom transient Overview only |
| Free / idle | Error or warning | Bottom transient Overview, using warning/error treatment and appropriate duration |
| Occupied by active command/session | Ordinary status/result event | Top transient notification |
| Occupied by active command/session | Warning/error | Top transient notification with semantic priority |
| Active command advances | New command prompt/step | Update bottom active-session Overview; do not create an unrelated top message unless an additional event occurred |
| Active command completes or cancels | Completion/cancel result | Fade out active-session Overview; after it is released, route the ordinary result into the bottom transient Overview if appropriate |

### Animation and layout requirements

- Both lanes use smooth fade-in/fade-out motion, not abrupt visibility changes.
- Use restrained vertical movement if desired, no more than a subtle few dp.
- Active-session Overview has an entrance animation and an exit animation when the session ends.
- A change in the active command step animates the content replacement without hiding the entire card between steps.
- The top toast fades out automatically after its own display interval.
- Do not animate height in the workspace layout; both elements are overlays.
- Neither lane may move, zoom, resize or rebase the CAD viewport.
- Keep the Overview opaque enough for readable text, with the professional black theme from Section 33.

### Acceptance tests

- [ ] An ordinary `0 objects selected` message appears only in the bottom Overview when no command is active.
- [ ] The top notification lane is hidden while the bottom Overview is free.
- [ ] Starting FILLET shows a floating active-session Overview with the relevant point prompt and options.
- [ ] The FILLET Overview remains visible until accept/completion or cancellation.
- [ ] The FILLET guidance is not placed as an inline strip behind/between the toolbar and command bar.
- [ ] A separate status event occurring during FILLET appears in the top notification lane.
- [ ] The top notification fades out without dismissing the FILLET Overview.
- [ ] When FILLET is cancelled/completed, its Overview fades away.
- [ ] The next ordinary guidance event returns to the bottom Overview only.
- [ ] All transitions are smooth and do not alter viewport geometry or layout.

---

## 39.3 Tool browser: five-tile category rail with one category content pane

The supplied screenshot shows the current CAD Tool Browser as a left category rail and a right content area. The user wants to retain the rail but change the content behaviour.

### Category rail

Keep five side tiles:

1. DRAW — Draw (2D Geometry)
2. MOD — Modify & Edit
3. SEL — Select & Inquiry
4. DIM — Annotate & QDIM
5. DOC — Document, Units & IO

The five tiles remain visible while the browser is open.

### Single selected-category content pane

- The right-side content pane displays commands and options belonging only to the currently selected category.
- Selecting DRAW displays only drawing commands such as LINE, POLYLINE, CIRCLE, ARC, RECTANGLE, POLYGON, ELLIPSE and other existing Draw commands.
- Selecting MOD replaces the right pane contents with only Modify & Edit commands.
- Selecting SEL displays only Select & Inquiry commands.
- Selecting DIM displays only annotation and QDIM commands.
- Selecting DOC displays only document, units, import/export and system commands.
- Do not show the five category headings as five accordion rows in the same content pane.
- Do not show commands from the other four categories in the selected category's content pane.
- Switching tiles changes the content pane while leaving the browser shell and category rail stable.
- Preserve the scroll position independently per category where practical.
- Show a clear selected tile state.
- Keep the command registry as the source of command definitions, descriptions, aliases and availability; the browser is a navigation/filtering presentation, not a duplicate command database.

### Restore a transparent arrow affordance

The previous invisible-only edge-swipe approach proved difficult to discover and operate.

- Restore a small arrow/chevron handle in its previous location at the exposed edge of the closed tool browser.
- The arrow must be translucent and low-contrast, using the neutral/primary-muted accent from the professional black theme.
- It must be visually discoverable without becoming a prominent button or obscuring important sheet geometry.
- No solid filled tab, bright outline, neon glow or large arrow background.
- The arrow should have a small, narrow hit area with a sufficiently generous actual touch target behind the translucent visual.
- Tapping the handle opens the browser.
- A deliberate inward swipe may also open it.
- When the browser is open, hide the closed-state arrow; the category rail itself is the visible navigation.
- Closing may be performed by a reverse swipe or a clearly accessible close affordance inside the browser shell; do not require a hard-to-discover invisible gesture as the sole close mechanism.
- Preserve Android system navigation/back gestures; do not intercept protected system-edge gestures.

### Acceptance tests

- [ ] The five category tiles are visible in the open browser.
- [ ] Exactly one category's command list is shown in the right-side pane at a time.
- [ ] Selecting a tile replaces the right pane content; it does not expand another list below it.
- [ ] No unrelated category commands are visible.
- [ ] Selected category state is visually clear.
- [ ] Command definitions still come from the shared registry.
- [ ] The closed-state arrow is present but translucent and unobtrusive.
- [ ] The arrow does not obscure important geometry or cursor visibility.
- [ ] Tapping the arrow opens the browser.
- [ ] Inward swipe also opens the browser.
- [ ] The arrow is hidden while the browser is open.
- [ ] Closing remains discoverable and does not interfere with Android system navigation.

---

## 39.4 Re-enable two-finger pan alongside one-finger pan and pinch zoom

This is the latest sheet-navigation contract and supersedes Section 21's earlier two-finger-zoom-only rule.

The sheet remains navigation-only; direct touch still cannot draw, select, enquire, snap, pick points or edit entities.

### Gesture mapping

- **One-finger drag:** pans the viewport.
- **Two-finger translation:** pans the viewport using the centroid movement of the two pointers.
- **Two-finger pinch/spread:** zooms the viewport according to the change in distance between the pointers.
- **Two-finger pan and pinch together:** if both centroid and inter-pointer distance change, apply pan and zoom simultaneously in one stable transform update.
- **Stationary two-finger contact:** no movement.
- **Two-finger rotation:** do not rotate the drawing.

### Transform mathematics

For a two-pointer gesture, track:

- Previous centroid \(C_{t-1}\) and current centroid \(C_t\).
- Previous pointer separation \(d_{t-1}\) and current separation \(d_t\).

Calculate:

\[
\Delta C = C_t - C_{t-1}
\]

\[
s = \frac{d_t}{d_{t-1}}
\]

Apply the centroid translation as viewport pan and the separation ratio as zoom. Preserve the zoom anchor at the appropriate current/previous centroid according to the viewport transform convention. Pan and zoom must be applied as one coherent transform update so that combined gestures do not jump.

### Pointer-state rules

- One pointer enters one-finger pan after the configured movement threshold.
- When a second pointer arrives, terminate/rebase the one-finger pan baseline and enter two-pointer transform mode without a jump.
- During two-pointer mode, process centroid translation and separation change simultaneously.
- If one pointer leaves, suppress the remaining pointer until all pointers are released, or perform a mathematically stable baseline reinitialisation that has been tested to cause no jump. Do not accidentally begin a fresh pan from the remaining pointer at its old location.
- Three or more pointers must be cancelled/ignored deterministically; they must not start CAD input.
- The tool browser and controller overlays retain their own pointer ownership. Touches beginning inside them must not reach the viewport gesture detector.
- Pointer-input coroutine lifetimes must remain stable while the viewport transform updates.

### Acceptance tests

- [ ] One-finger drag pans the drawing.
- [ ] Two-finger translation with constant separation pans the drawing.
- [ ] Pinching inward zooms out.
- [ ] Spreading outward zooms in.
- [ ] A combined pinch and centroid translation performs zoom and pan simultaneously.
- [ ] Two-finger gestures do not rotate the drawing.
- [ ] Two-pointer gesture start/end transitions do not jump.
- [ ] Multi-pointer gestures never invoke CAD operations.
- [ ] Touches on the tool browser and controller never leak into sheet navigation.
- [ ] Cursor model coordinates remain unchanged during pan/zoom.
- [ ] No sheet gesture creates, selects, snaps to, picks or modifies CAD entities.

---

# 40. Updated implementation order — latest interaction refinements

Add these tasks after the earlier v10 implementation sequence:

1. Restore the accepted pre-touch-slop relative touchpad movement and sensitivity behaviour; remove the touch-slop gate and new velocity acceleration.
2. Hide only the touchpad-mode directional nudge arrows and `NUDGE` label; expand the touchpad into the released space and retain the other established touchpad controls.
3. Implement the two-lane Overview/session feedback routing: bottom active-session guidance versus conditional top transient notifications.
4. Move the entire active command guidance/options panel out of the inline toolbar/command-row layout and into the floating bottom Overview.
5. Change the tool browser to a persistent five-tile rail and one selected-category command pane.
6. Restore the subtle translucent closed-state arrow affordance and preserve swipe opening.
7. Re-enable simultaneous two-finger centroid pan plus pinch zoom while preserving one-finger pan.
8. Re-run touchpad/controller isolation, notification lifecycle, tool-browser navigation, viewport transform and active-command regression tests.

Do not mark this revision complete solely because the controls look correct in a screenshot. Verify the actual pointer ownership, state transitions, overlay lifecycle and viewport transform with instrumented interaction tests.


---

# 41. Latest source snapshot record and source-of-truth policy

## 41.1 Newly supplied source archive

The latest source supplied by the user for this revision is:

`PrecisionCAD-Studio-main.zip`

Observed archive facts:

- Archive size: approximately 1.13 MB.
- Archive entries: 108.
- ZIP comment: `52d16959c99fc35ac7a6fb60bc148213652ba7be`.
- The ZIP does not contain a `.git` working directory.
- The ZIP does not contain a `gradlew` launcher script.
- The source tree contains the Android application, Kotlin/Compose source, Gradle build files, a project-side `.aistudio` implementation plan and only the sample unit/Robolectric/instrumented tests listed below.

The ZIP comment is retained as supplied archive metadata. Do not represent it as a verified Git commit, branch head or remote `HEAD` without checking it against repository history.

The GitHub connector did not expose any repositories to the current session when the private repository was queried/listed. A private repository is a plausible explanation, but the connector result does not establish the exact permission/configuration cause. The coding agent must treat this newly supplied ZIP as the current working source snapshot until a properly authorised private-repository connection is available.

**Do not request the user to make the repository public.** If live GitHub access is required, the repository must be included in the GitHub integration's authorised repository scope, with permission for the connected account/app to read it.

## 41.2 Comparison with the preceding uploaded snapshot

The new archive is materially different from the previous `PrecisionCAD-Studio-main (1).zip` snapshot. The following core files changed:

| File | Previous size | New size | Source review implication |
|---|---:|---:|---|
| `CadWorkspaceScreen.kt` | 114,478 bytes | 130,289 bytes | Workspace composition, tool browser and bottom overlays have evolved |
| `CadWorkspaceViewModel.kt` | 136,762 bytes | 141,547 bytes | QDIM state, clear action, notifications and workspace behaviour have evolved |
| `VirtualCursorController.kt` | 37,294 bytes | 47,423 bytes | New touchpad motion state machine and mode-specific controller layout |
| `CadViewportCanvas.kt` | 53,169 bytes | 57,354 bytes | Viewport gesture handling and renderer have evolved |
| `QuickDimensionEngine.kt` | 54,496 bytes | 68,279 bytes | Local profile edge classification and diagnostics have expanded |
| `DimensionAlignmentEngine.kt` | 46,336 bytes | 53,426 bytes | Whole-dimension relocation and report logic have expanded |
| `CadDxfInterchange.kt` | 76,921 bytes | 81,652 bytes | DXF table/entity serialization has evolved |
| `ui/theme/Color.kt` | 1,137 bytes | 1,301 bytes | The professional black visual system has been introduced |

The latest files were inspected directly. This section records source facts and known differences against the user-approved v11 behaviour; it is not a claim that the app has been built, deployed or tested on the user's phone.

## 41.3 Current source paths

The latest archive's primary source paths are:

| Concern | Current file |
|---|---|
| Application activity and immersive window policy | `app/src/main/java/com/example/MainActivity.kt` |
| Workspace root, top/bottom overlays, tool browser and command stack | `app/src/main/java/com/example/ui/CadWorkspaceScreen.kt` |
| Shared application/document state, command execution, notifications and QDIM | `app/src/main/java/com/example/ui/CadWorkspaceViewModel.kt` |
| Touchpad/joystick controller | `app/src/main/java/com/example/ui/components/VirtualCursorController.kt` |
| Sheet input and 2D Canvas drawing | `app/src/main/java/com/example/ui/components/CadViewportCanvas.kt` |
| QDIM candidate generation | `app/src/main/java/com/example/cad/dimension/QuickDimensionEngine.kt` |
| QDIM arrangement and collision relocation | `app/src/main/java/com/example/cad/dimension/DimensionAlignmentEngine.kt` |
| Geometry and modification operations | `app/src/main/java/com/example/cad/kernel/CadGeometryKernel.kt`, `CadModificationEngine.kt` |
| Coordinate/snap/viewport transforms | `app/src/main/java/com/example/cad/kernel/CadSnapAndCoordEngine.kt` |
| CAD entity/document model | `app/src/main/java/com/example/cad/model/CadEntities.kt` |
| Command registry and command metadata | `app/src/main/java/com/example/cad/model/CadCommandSpec.kt`, `CADCommandMatrix.kt` |
| DXF import/export | `app/src/main/java/com/example/cad/io/CadDxfInterchange.kt` |
| Application palette | `app/src/main/java/com/example/ui/theme/Color.kt`, `Theme.kt` |
| QDIM dialog and Clear All confirmation | `app/src/main/java/com/example/ui/components/CadDialogsAndPanels.kt` |

## 41.4 Test/build evidence available in this archive

The archive contains only these test source files:

- `app/src/test/java/com/example/ExampleUnitTest.kt`
- `app/src/test/java/com/example/ExampleRobolectricTest.kt`
- `app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt`

No dedicated regression suites were found in this archive for:

- Viewport pointer-state transitions and combined pan/zoom.
- Touchpad direct-relative behaviour or controller pointer isolation.
- Bottom Overview/top notification routing and command-session persistence.
- Tool-browser category navigation.
- QDIM candidate-generation completeness and dimension placement.
- QDIM enable/refresh/clear idempotency and undo/redo.
- DXF parser interoperability or dimension round trips.

The archive has no `gradlew` launcher and no `.git` directory. No Gradle build, emulator test or physical-device test is claimed by this source audit. The coding agent must add focused tests and report actual build/test output in its implementation result.

---

# 42. Source audit: implemented features versus remaining v11 requirements

This section is authoritative for deciding what must be changed in the latest source. It is deliberately divided into **already present**, **partially present**, and **still divergent**. The agent must not implement a second system alongside an existing correct one.

## 42.1 Already present in the latest source — preserve and extend

### A. Immersive full-screen Android window policy

`MainActivity.kt` already:

- Calls `enableEdgeToEdge()`.
- Calls `WindowCompat.setDecorFitsSystemWindows(window, false)`.
- Hides `WindowInsetsCompat.Type.systemBars()`.
- Uses `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`.
- Reapplies immersive policy in `onResume()` and when the activity regains window focus.

Preserve this central policy. Do not add duplicate per-screen fullscreen flags. Test actual system-bar transient reveal and return-to-immersive behaviour on the target Android device.

### B. Professional pure-black visual palette

`ui/theme/Color.kt` already implements the specified black palette:

| Role | Current source colour |
|---|---|
| Model-space background | `#000000` |
| Primary panel | `#050608` |
| Elevated panel/HUD | `#090C10` |
| Surface card | `#0D1117` |
| Hairline border | `#202630` |
| Strong border | `#303946` |
| Muted blue primary accent | `#7892A8` |
| Amber precision accent | `#B39A69` |
| Success accent | `#729783` |
| Warning accent | `#B79A63` |
| Error accent | `#B87575` |
| Primary text | `#E6E9ED` |
| Secondary text | `#A4ADB8` |
| Muted text | `#737E8B` |

The application uses `darkColorScheme` with this custom palette. Do not replace it with a grey theme or introduce another competing palette. Review any remaining neon-looking component fills/borders against Section 33 and consolidate them into the existing theme tokens.

### C. Stable full-screen Canvas with overlay panels

In `CadWorkspaceScreen.kt`:

- Portrait mode draws the CAD viewport once inside a stable full-size `Box`.
- Header/HUD, tool drawer, Overview, function/command region and controller are layered above the Canvas.
- The Canvas is not a weighted `Column` sibling whose height is reduced by the controller.
- The landscape branch follows the same principle of a full-size base viewport and overlaid workspace controls.

`CadWorkspaceViewModel.updateUsableViewportRect()` updates the interaction-safe rectangle without calling `rebaseForUsableRectChange()` on ordinary overlay changes. The initial default-rectangle transition may fit the current drawing once; subsequent tool/controller/Overview changes update the safe rectangle but do not directly change the viewport transform.

Preserve that separation. Do not restore any old layout-driven camera rebase. Verify the portrait and landscape paths independently.

### D. Persistent command input

`ContextualCommandAndOptionsDock` contains a continuously rendered command text field in idle, typing and active-command states. The field does not auto-focus on launch. Prefix suggestions are shown when text is entered.

Preserve the always-visible text input. The remaining discrepancy is the placement of the active-command context/options surface, described in Section 42.8.

### E. Shared QDIM state and idempotent ON/OFF setter

The latest ViewModel still has one shared `CadWorkspaceUiState.isQuickDimensionEnabled` property. Header and function-bar labels are derived from it.

`setQuickDimensionEnabled()` has an equality guard:

- If the requested state equals the current state, it returns.
- OFF removes QDIM-generated automatic dimensions while preserving manual dimensions and clears the alignment report.
- ON executes QDIM using replacement semantics.

The standard QDIM dialog's Execute button passes `replaceAutoDims = true`. The old UI path that offered an append/keep-previous-generated-set option is not visible in the latest dialog.

Do not create additional local QDIM Boolean states. Retain one authoritative ViewModel state.

### F. Clear All Dimensions action and confirmation

The latest QDIM dialog contains a destructive `CLEAR ALL DIMENSIONS` button and a confirmation dialog. The ViewModel has `clearAllDimensionsConfirmed()`.

Current behaviour in the inspected code:

- Counts `DimensionEntity` objects in `uiState.entities`.
- Removes every `DimensionEntity` from that list, regardless of `isAutoGenerated`.
- Removes every `CenterMarkEntity` whose `sourceEntityId` is non-null.
- Preserves remaining entities.
- Intersects the selected-ID set with the remaining entity IDs.
- Sets `isQuickDimensionEnabled = false`.
- Clears `lastAlignmentReport`.
- Records one undo snapshot before the non-empty removal.
- Emits one success notification.
- If no dimensions and no source-linked centre marks exist, it sets QDIM OFF without creating an empty undo entry.

Preserve this general transaction model, but review the scope/ownership limitations in Section 42.9 before describing it as fully document-wide.

### G. Horizontal, vertical and inclined QDIM edge candidate generation

The latest `QuickDimensionEngine.kt` no longer has the old inclined-only condition.

Its Tier 3 local profile feature pass:

- Classifies line primitives as `HORIZONTAL`, `VERTICAL` or `INCLINED`.
- Creates `LINEAR_HORIZONTAL` candidates for eligible horizontal edges.
- Creates `LINEAR_VERTICAL` candidates for eligible vertical edges.
- Creates `ALIGNED` candidates for eligible inclined edges.
- Uses `EdgeDimensionDiagnostic` records with status and reason.
- Distinguishes explicit dimensions, dimensions covered by an overall dimension, derivable fillet-trimmed outer spans, sparse-policy suppression and coverage by a manual dimension.

This directly addresses the earlier source-level omission of all horizontal and vertical local edges. Do not reintroduce the old condition that excludes orthogonal segments.

Remaining completeness caveats are in Section 42.10.

### H. Whole-dimension linear collision relocation

The latest `DimensionAlignmentEngine.buildLinearPlacementResult()` (approximately lines 904–1023) no longer moves only the text anchor.

When a collision is detected, it:

- Moves `dimensionLinePoint` to a parallel offset track.
- Recomputes `AdaptiveAnnotationRenderer.computeRenderPlan()` using the adjusted dimension.
- Uses the recomputed plan for dimension-line endpoints, extension-line endpoints, arrow positions and text anchor.
- Stores the updated `DimensionEntity` in the placement result.
- Marks unresolved collisions in `reviewFlags`.

Preserve this whole-assembly relocation. Do not revert to text-only displacement. Remaining collision-analysis limitations are recorded in Section 42.10.

### I. Transparent dimension and leader labels

The latest `CadViewportCanvas.kt` draws dimension labels directly using Canvas/native text drawing. In the inspected linear, angular and radial/diametric text branches, no filled rectangular label background is drawn. The source comment explicitly describes transparent dimension text rendered directly above sheet geometry.

Preserve this behaviour. Do not introduce a text mask, blur plate or opaque background rectangle when adjusting annotation layout. Continue to verify leader text paths separately.

### J. Core geometric drafting already exists

The application already has a geometry/document model and command execution for the fundamental 2D drawing entities:

- `LINE` creates `CadEntity.LineEntity` objects.
- `CIRCLE` creates `CadEntity.CircleEntity` objects through centre/radius, two-point, three-point and tangent construction paths where supported.
- `ARC` creates `CadEntity.ArcEntity` objects through the three-point geometry kernel or start/centre/end workflow.
- Preview geometry is computed separately through `computePreviewEntities()`.
- The active document entity list stores the actual geometry rather than a bitmap of drawn pixels.

The requested basic canvas-based drafting engine should therefore be understood as **completing and testing the existing command → geometry-kernel → document-entity → Canvas-preview/renderer pipeline**, not creating a second unrelated drawing engine or storing geometry in screen pixels.

Preserve model-space coordinates, existing `CadEntity` types, geometry-kernel validation, preview isolation and undoable commits.

## 42.2 Touchpad implementation currently conflicts with the latest user-approved reversion

The current `VirtualCursorController.kt` still contains:

- `TouchpadGestureState` with `PRESSED_UNDECIDED` and `DRAG_ACTIVE`.
- `TouchpadMotionEngine` with platform touch-slop gating.
- A threshold-crossing displacement that is discarded.
- `VelocityTracker` and `computeVelocityGain()`.
- A velocity gain from 0.72 to 2.60.
- A touchpad UI branch that renders only the touchpad surface; the step-size selector, PICK PT, Enter and Escape controls are hidden.

These reflect the older v7/v10 safety experiment, not the user's latest v11 reversal.

The latest requirement is:

1. Restore the earlier direct relative-delta mapping using the existing `touchpadSensitivity` and `pixelsPerModelUnit`.
2. Remove the `TouchpadMotionEngine` touch-slop activation gate from the normal touchpad movement path.
3. Remove the new velocity-acceleration multiplier and no longer use `VelocityTracker` for cursor gain.
4. Keep pointer-down as a starting sample only; a stationary tap with no measured movement must cause no cursor displacement.
5. Do not add a tap, double-tap, long-press, click or context-menu command to the touchpad.
6. Continue to consume/own pointer sequences inside the touchpad so that they cannot leak into sheet pan/zoom or selection.
7. In touchpad mode, remove only the four directional nudge arrows and the `NUDGE` label.
8. Expand the touchpad area into the width reclaimed from those arrows.
9. Restore all other controller elements that were present in the previously accepted touchpad layout: step-size controls, PICK PT, Enter/confirm, Escape/cancel, mode controls, coordinates and settings.
10. Keep the joystick layout, velocity response, frame integration and stop-on-release algorithm unchanged.

The direct-relative touchpad must not interpret a stationary tap as movement. It should process actual pointer displacement samples only. Do not restore the earlier direct-touch sheet gestures or add any touchpad click action.

## 42.3 Viewport input currently lacks the newly requested two-finger pan

The latest `CadViewportCanvas.kt` currently has:

- `ONE_POINTER_PAN`.
- `TWO_POINTER_PINCH_ZOOM`.
- `POST_PINCH_POINTER_SUPPRESSED`.
- `LEFT_EDGE_DRAWER_REVEAL`.
- `CANCELLED`.

Its two-pointer policy uses `calculateZoom()` and a locked focal anchor. The two-pointer branch calls `applyTwoFingerPinchZoomOnly()` and explicitly ignores centroid pan. The helper `applyTwoFingerTransform()` also delegates to zoom-only behaviour and ignores its pan argument.

This conflicts with the latest user requirement in Section 39.4.

Implement the final gesture mapping:

- One finger: pan.
- Two fingers: centroid translation pans while inter-pointer distance changes zoom.
- Combined two-finger movement: apply both pan and zoom coherently in one viewport transform update.
- No rotation.
- The sheet remains navigation-only; no touch gesture performs CAD selection, drawing, enquiry, OSNAP acquisition or point picking.
- Preserve pointer-state suppression and stable recogniser lifetimes through pointer-count transitions.
- Pointer sequences beginning inside the controller or tool browser must remain owned by those overlays.

Do not merely add centroid movement to the existing locked-focal zoom and create two separate transform writes that visibly jump. Compute a coherent simultaneous pan-and-zoom transform with a stable anchor.

## 42.4 Tool browser currently remains a multi-accordion with no arrow handle

`RetractableSideToolDrawer()` in `CadWorkspaceScreen.kt` currently:

- Shows the five vertical category tiles on the left.
- Tracks `activeCategory` and `expandedToolSections`.
- Renders all five category section headings in the right-side vertical list.
- Allows multiple expanded sections.
- Has no visible arrow/chevron in either state.
- Relies on a left-edge swipe, bottom Function Bar Tools Browser button, leftward close swipe and Android Back.

The latest user-approved requirement is different:

- Keep the five category tiles.
- Show commands for **only the selected category** in the right-side pane.
- Selecting a tile replaces the right pane list rather than opening another accordion below it.
- The five category command definitions must still come from the shared `CadCommandId`/command registry.
- Restore a small translucent closed-state arrow/chevron at the previous exposed edge position.
- The arrow is visually transparent/low contrast and must not obscure the drawing; the actual touch target may be slightly larger than the visible glyph.
- Tapping the arrow opens the browser; inward swipe remains an alternative.
- Hide the closed-state arrow while the browser is open.
- Provide a discoverable close action or reverse swipe; do not require an invisible gesture as the only close method.
- Preserve Android system navigation.

Do not keep the old multi-accordion pane while claiming this category-browser requirement is satisfied.

## 42.5 Notification and Overview implementation currently emits both lanes for every event

The latest ViewModel has separate `activeOverview` and `activeNotification` fields, and the screen renders both `OverviewOverlayCard` and `TransientNotificationBanner`.

However, `emitTransientNotification()` currently calls `emitOverview()` for every event before it handles `activeNotification`. `emitOverview()` sets a fixed 2.5-second dismissal job regardless of whether a command session is active. Consequently ordinary events can be displayed in both the bottom Overview and the top notification banner at the same time.

`ActiveCommandContextBar` is still rendered inline beneath the function toolbar when a command is active. It includes the current prompt and command-specific controls such as the FILLET radius field, sub-options, confirm/finish and cancel.

The latest user request in Section 39.2 requires conditional routing:

- **No active command/session:** ordinary guidance or operation result appears only in the bottom floating Overview, fades in, remains briefly and fades out. The top transient notification remains hidden.
- **Active command/session:** the bottom Overview becomes a persistent active-session guidance/actions panel and stays visible until the operation is accepted/completed or cancelled.
- **Additional event during an active session:** show that event in the separate top transient notification lane, which fades away independently without dismissing the active-session Overview.
- When the active session ends, its bottom Overview fades away. The next ordinary event goes back to the bottom transient Overview only.
- The persistent command-entry field remains visible in the command tier.
- Move the active command guidance/options surface out of the inline function/command-row strip and into the floating bottom active-session Overview, while keeping the command input field persistently available.
- Do not let the active-session guidance appear as a bar behind or between the function toolbar and command entry.
- Both lanes remain full-screen overlays and must not reflow the viewport.

Implement a distinct Overview event type or session state; do not infer session persistence merely by extending a 2.5-second notification timeout. Avoid calling `emitOverview()` unconditionally from every transient notification event.

The current keyed `AnimatedContent` transition for Overview is useful and should be preserved. The top banner's `AnimatedVisibility` currently animates only visibility of the container; add a keyed transition for replacement content if distinct top events can replace one another while the lane remains visible.

## 42.6 Full-screen viewport and panel layout: mostly implemented, preserve carefully

The latest portrait screen renders the Canvas inside a full-size root `Box`. The bottom stack is layered over it and contains Overview, function/command dock and controller. The top stack contains header, transient notification and viewport HUD.

`updateUsableViewportRect()` is used to update the interaction-safe rectangle. After the initial default rectangle transition, the ViewModel updates this rectangle without rebasing the viewport transform.

Preserve this implementation. The fact that `usableViewportRect` changes as overlays appear is not itself a camera rebase. Keep the viewport transform and model-space anchor stable when the tool drawer/controller/Overview changes. Do not remove initial fit-to-drawing behaviour on first actual viewport measurement.

The agent must test that overlay changes do not alter the Canvas's full-size measurement, model-to-screen scale or pan translation.

## 42.7 QDIM source state: shared state is present; validate idempotency and clear scope

The following is present:

- Shared `isQuickDimensionEnabled` in `CadWorkspaceUiState`.
- Header and function bar read the shared state.
- `setQuickDimensionEnabled()` returns immediately if the requested state equals the existing state.
- Standard QDIM dialog execution passes replacement semantics.
- `refreshQuickDimensions()` replaces existing auto dimensions.
- `clearAllDimensionsConfirmed()` removes manual and automatic `DimensionEntity` instances from the current `entities` collection, removes source-linked centre marks, resets QDIM OFF, clears the alignment report and records one undo snapshot for non-empty clears.
- The dialog shows a confirmation with a count and explicit Cancel/Clear buttons.

Remaining checks:

1. `executeQuickDimension()` itself records an undo snapshot and regenerates whenever invoked. Keep this deliberate for explicit QDIM/Refresh commands, but ensure merely opening the dialog or reapplying the already-current ON state never calls it.
2. Confirm there is no remaining UI path that passes `replaceAutoDims = false`.
3. Verify undo/redo restores the exact QDIM state and prior generated set.
4. The dialog count is currently `uiState.entities.count { it is DimensionEntity }`; ensure this is the full in-scope document count for the current document model. If model/paper/layout dimensions have separate storage in a later model, enumerate all relevant stores.
5. The ViewModel deletes every `CenterMarkEntity` with non-null `sourceEntityId`. Verify this is a reliable generated-mark ownership test. Prefer explicit QDIM ownership/provenance rather than assuming every source-linked mark belongs to QDIM.
6. Add tests proving the top header, toolbox and dialog always show the same state and that clearing dimensions removes no unrelated geometry or annotation.

Do not create a second QDIM status Boolean.

## 42.8 Basic drafting engine: extend current commands, do not duplicate them

The latest source already implements `LINE`, `CIRCLE` and `ARC` construction through `CadWorkspaceViewModel` and `CadGeometryKernel`.

- LINE accepts successive points and commits `CadEntity.LineEntity` segments.
- CIRCLE supports multiple construction options, including 3-point, 2-point, centre/radius or diameter, and tangent construction paths where supported.
- ARC uses the geometry kernel's three-point construction or an explicit start-centre-end calculation.
- Preview entities are computed separately from the committed document entity list.
- The main Canvas draws the model entities and preview entities through separate inputs.

The agent must not create a second Canvas bitmap drawing engine or second document model. Complete the existing command/geometry-kernel/document-entity path, verify preview cancellation and commit semantics, and ensure geometry can round-trip through PDF/DXF.

## 42.9 DXF source changes: some previous defects are addressed; HATCH remains a standards-review item

The current `CadDxfInterchange.kt` has improved since the previous archive:

- LWPOLYLINE writes `AcDbEntity` and `AcDbPolyline` subclass markers.
- LTYPE, LAYER, STYLE and DIMSTYLE table headers now include handles and table subclass markers.
- Table records include their expected subclass markers.
- DIMSTYLE table handle uses group code 105 for the record handle and includes `AcDbDimStyleTableRecord`.
- Non-continuous linetypes write dash counts, total pattern lengths and segment patterns.
- DIMENSION group code 70 deliberately clears bit 32 because the serializer does not reference an anonymous dimension picture block. Do not blindly reintroduce bit 32 or anonymous-block flags without implementing and validating the complete block structure.
- Dimension text labels are written through the DXF entity path; verify all dimension subtypes and target-reader behaviour using real exported drawings.

The current HATCH branch still writes group code 93 followed directly by vertex coordinate pairs. The inspected path does not visibly emit the expected HATCH boundary-path count/flags and edge/vertex path structure. This remains a likely standards-interoperability risk and must be checked against the selected DXF version's exact HATCH grammar.

Required validation:

- Export a minimal LINE/CIRCLE file.
- Export a polyline with bulges.
- Export one dimension of every supported subtype.
- Export a HATCH.
- Parse each exact output with a strict independent DXF parser without recovery/audit repairs.
- Open the same exact output in a desktop CAD reader and AutoCAD Mobile.
- Record file version, parser version, reader version and exact error messages.
- Keep PDF export regression tests separate and preserve the user's working PDF route.

Do not claim the latest DXF serializer has been fully validated merely because the table/entity code now contains subclass markers.

## 42.10 QDIM remaining completeness and alignment caveats

The latest Tier 3 QDIM generator classifies horizontal, vertical and inclined segments. It is an improvement, but it still contains length thresholds:

- The initial line pass skips a segment when its length is at or below 1.0 drawing unit.
- Horizontal and vertical explicit local candidates are generated only when the segment length exceeds 2.0 drawing units.
- Inclined local candidates are generated only when the segment length exceeds 3.0 drawing units.
- The current `EdgeDimensionStatus` set does not include a dedicated `TOO_SHORT`/`BELOW_MINIMUM_FEATURE_LENGTH` status.
- Some segments can therefore be skipped without an `EdgeDimensionDiagnostic`, despite the rest of the engine's completeness report.

The agent must:

1. Record every extracted edge, including those below the threshold.
2. Add an explicit diagnostic status/reason for too-short or intentionally suppressed edges.
3. Keep threshold policy related to dimension style/text height and current drawing units rather than relying only on fixed model-unit constants.
4. Preserve meaningful internal shoulders/notches. A segment must not be discarded simply because another equal-length segment exists elsewhere; suppression should be based on geometric coverage/topology rather than value alone.
5. Re-test the user's concave multi-fillet profile, including the inner horizontal shelf and vertical walls, and the six-hole mounting plate.
6. Keep generation completeness distinct from placement quality.

`buildLinearPlacementResult()` now relocates the entire dimension assembly and recomputes its render plan. However, its local collision function primarily tests occupied text boxes and the text's distance from obstacle lines; verify that complete dimension/extension lines are also assessed for intersections with the geometry and with previously placed dimension segments. The broader multi-primitive collision helper exists elsewhere in the alignment engine; ensure linear placements use a complete collision test before they are marked CLEAR.

## 42.11 Dimension-label backgrounds

The current renderer's main radial, angular and linear dimension-label branches use direct `drawText()` calls with no filled rectangular text mask. This already matches the requested transparent text presentation in the inspected paths.

Preserve the transparent style, but include a visual regression test for:

- Linear dimension text.
- Radius/diameter callouts.
- Angular dimension text.
- Leader labels.
- Zoomed-out screen-space text readability.
- No background plate or filled rectangle obscures model geometry.

## 42.12 Source audit completion criteria

Before changing any source file, the coding agent must:

- Read the exact supplied archive snapshot.
- Compare its current branch/checkout against this archive if a properly authorised private GitHub connection is available.
- Treat the supplied archive as the source of truth if no remote source can be accessed.
- Preserve already-implemented features in Sections 42.1 and 42.6–42.8.
- Implement the user-approved outstanding differences in Sections 42.2–42.5 and 42.9–42.10.
- Add focused unit/UI tests for the missing behaviours.
- Report what was changed, what remained untouched, what tests were run, and any limitation not verified on a physical Android device.

Do not claim a GitHub `HEAD` comparison, Gradle build, independent DXF parser result or physical-device interaction test unless that exact validation was actually performed.


---

# 43. Dynamic Input and Polar Tracking — cursor-centred drafting assistance

## 43.1 Reference interpretation

The three newly supplied AutoCAD photographs demonstrate two separate but cooperating functions:

1. **Dynamic Input (DYN):** live command prompts and numeric input fields positioned near the crosshair/cursor. The examples show a polar distance/angle readout such as `Polar: 18783.6196 < 180°`, a distance field, an angular field and a prompt such as `Specify next point or ...`.
2. **Polar Tracking (POLAR):** temporary dotted alignment paths/guide arcs with nearby angular labels, including 90°, 126° and 180°, generated as the cursor approaches configured angular directions from the active command's base point.

Implement both features in PrecisionCAD Studio. They must be independent controls but cooperate during point-based commands.

Autodesk describes Dynamic Input as three components—pointer input, dimension input and dynamic prompts—and documents Polar Tracking as temporary alignment paths based on configured angular increments:
- Dynamic Input settings: `https://help.autodesk.com/cloudhelp/2025/ENU/AutoCAD-Core/files/GUID-38EC86CF-D96A-455F-A5DE-2CDA23C28FC4.htm`
- Dynamic Input tooltips: `https://help.autodesk.com/cloudhelp/2022/ENU/AutoCAD-Core/files/GUID-3EBD4C17-F0A5-49FA-B131-4AABE2E727DB.htm`
- Polar Tracking and PolarSnap: `https://help.autodesk.com/view/ACD/2026/ENU/?caas=caas%2Fdocumentation%2FACDLT%2F2014%2FENU%2Ffiles%2FGUID-7EC3C63D-EA4E-4E65-A676-C3A3627E3F19-htm.html`

This is a 2D implementation. Do not introduce UCS 3D axes, 3D object tracking or 3D dynamic UCS.

## 43.2 Separate feature responsibilities

### Dynamic Input (DYN)

Dynamic Input is the virtual-cursor-adjacent prompt and numeric-input system.

It must support three independently configurable components:

1. **Pointer Input**
   - Displays the current virtual cursor's model-space X/Y coordinates next to the cursor.
   - Supports absolute coordinate entry and relative coordinate entry where accepted by the active command.
   - Uses the application's current drawing units and coordinate precision.
   - Updates continuously as the virtual cursor moves.

2. **Dimension Input**
   - During a point-based command, displays the live distance from the accepted base point to the current cursor position.
   - Displays the live angle when angular direction is meaningful.
   - For a second/subsequent LINE point, show distance and angle fields together, similar to the supplied AutoCAD photographs.
   - For CIRCLE, show the live radius/diameter value while selecting the circumference point.
   - For ARC, show only the parameters relevant to the current construction method (for example, radius, included angle, chord length or endpoint distance).
   - Do not display irrelevant values for a given command step.

3. **Dynamic Prompts**
   - Shows the active command name and current prompt near the virtual cursor.
   - Examples: `Specify first point`, `Specify next point`, `Specify radius`, `Specify end point`, `Specify angle`.
   - Shows only valid options for the current command step.
   - Does not show unrelated command options or another command's prompt.
   - Must stay synchronised with the persistent command tier; the cursor tooltip is a convenient local mirror/input surface, not a different command state.

Each of the three components can be toggled independently in Dynamic Input Settings, while a master DYN toggle enables/disables the whole near-cursor system.

### Polar Tracking (POLAR)

Polar Tracking is an angular guidance system based on the current command's active base point.

- Display temporary dotted/ray/arc alignment guides as the virtual cursor approaches configured polar angles.
- Display a small angular label at or near the relevant guide (for example, `90°`, `126°`, `180°`).
- Display the polar distance-and-angle tooltip when Dynamic Input's dimension input is enabled.
- Recalculate the guide and angle as the virtual cursor moves.
- Do not create permanent CAD geometry for tracking paths, arcs, angle labels or tooltip fields.
- Discard all tracking graphics when the command step changes, the base point changes, the cursor moves outside the acquisition tolerance, or the command is completed/cancelled.
- The guide must originate from the most recently accepted command base point or other explicitly designated tracking origin.
- For commands with no accepted base point yet, do not display a polar guide. After the first point is accepted, enable tracking for the next point.
- For a multi-segment LINE/POLYLINE command, the default tracking origin for the next segment is the most recently accepted vertex. Allow a configurable absolute-angle reference or relative-to-last-segment mode as described below.

## 43.3 Dynamic Input visual behaviour

The dynamic input fields should be positioned near the virtual CAD cursor, in a compact cluster that adapts to the command step.

For a LINE second point, the cluster should be able to show:

- Command prompt: `Specify next point`.
- Distance field: current/base-point-to-cursor length, for example `18783.6196`.
- Angle field: current direction, for example `180°`.
- Optional polar description: `Polar: 18783.6196 < 180°`.

For a first point, pointer input may show X/Y coordinates instead of a distance/angle pair.

Visual requirements:

- Follow the virtual cursor, not the physical finger position on the touchpad or joystick.
- Use compact, legible, near-black tooltip surfaces with subtle neutral borders and the professional black theme.
- Keep the field text high-contrast; the cursor readout must remain legible at low screen brightness.
- Use small rectangular tooltips/fields similar in density to the reference screenshots, but adapt them to phone display dimensions.
- Keep the field cluster from covering the cursor crosshair, snap marker, active preview geometry or polar guide intersection.
- Place fields in the least obstructive nearby quadrant. Flip or reposition the cluster when it approaches screen edges, panel boundaries, the Overview, header or controller.
- Clamp the tooltip cluster to the visible Canvas region and avoid placing it beneath opaque overlays.
- Use a subtle fade/position transition when the fields relocate; do not make them jump or flicker.
- The values update without recomposing the entire workspace or rebuilding the document geometry.
- Dynamic Input tooltips are temporary UI overlays and must not change the model-space viewport transform, cursor coordinates, geometry or zoom/pan.
- These tooltips are not the dimension labels discussed in Section 23. They may have a small high-opacity background for readability; do not apply the transparent dimension-text rule to these UI input fields.

## 43.4 Numeric entry and field focus

Dynamic Input must support precise numeric entry without undermining the always-visible command line.

- When a command is active and DYN is ON, allow the currently relevant numeric field to receive keyboard/numeric input.
- The active field must be visually indicated.
- Tab (or an accessible on-screen field-switch control) moves focus between distance and angle fields where both are available.
- Enter/accept commits the current numeric value for the active command step; it must not silently finish the entire command unless that is the command's next valid action.
- The user can still enter the same values through the persistent command line. Both surfaces submit into the same command input/point-acquisition state.
- Numeric entry must support the existing coordinate-expression grammar: absolute X,Y, relative @X,Y, polar L<θ and relative polar @L<θ, subject to the active prompt's accepted syntax.
- If a value is invalid, show validation next to the active field and preserve the current preview/base point; do not commit an invalid point.
- Do not automatically open the Android keyboard on every cursor movement. Open it only when the user deliberately focuses an editable dynamic field or explicitly invokes numeric entry.
- The virtual cursor remains controlled by the touchpad/joystick. Typing a value updates the candidate point in model space without moving the cursor control's stored position unless the command's established coordinate-entry semantics explicitly rebase it.
- Keep Dynamic Input optional. When DYN is OFF, the command tier remains fully functional and no cursor-adjacent prompt/value overlay is shown.

## 43.5 Polar angle settings

Provide a Polar Tracking settings surface with:

- Master POLAR on/off.
- Increment angle presets: 90°, 60°, 45°, 30°, 22.5°, 18°, 15°, 10° and 5°.
- Custom increment angle entry.
- Additional angles list, allowing one or more custom angular directions in addition to the primary increment.
- Absolute angle reference (relative to the drawing's configured zero-angle convention).
- Relative-to-last-segment reference for chained line/polyline construction.
- Clockwise/counter-clockwise angle direction according to the drawing's angle-unit convention.
- A configurable angular acquisition tolerance and visual guide length/range.
- Optional display of guide arcs/rays, angular labels and polar distance tooltip.

Default behaviour should be predictable for 2D drafting:
- 0° points along positive model-space X.
- Angles increase counter-clockwise.
- 90° is vertical upward in model space.
- The displayed angle obeys the drawing's configured angle units/zero direction.
- Use model-space geometry for calculations; convert to screen coordinates only for drawing the guide.

The common increment list is grounded in Autodesk's documented polar angle choices; do not claim full AutoCAD compatibility for every setting unless the implementation passes equivalent tests.

## 43.6 Tracking geometry and acquisition algorithm

Polar tracking must be calculated in model space and rendered in screen space.

For base point \(B=(B_x,B_y)\) and candidate cursor point \(P=(P_x,P_y)\):

\[
\Delta x = P_x-B_x,\qquad \Delta y=P_y-B_y
\]

\[
r=\sqrt{\Delta x^2+\Delta y^2}
\]

\[
\theta=\operatorname{atan2}(\Delta y,\Delta x)
\]

Normalise \(\theta\) according to the drawing angle convention. Find the nearest configured polar direction \(\theta_k\) and compute the angular difference. Show the guide when that difference falls inside the configured acquisition tolerance and the candidate distance is non-degenerate.

For an optional polar-snap/lock mode, project the candidate point onto the selected polar ray:

\[
P_{\text{polar}}=B+r
\begin{bmatrix}
\cos\theta_k\\
\sin\theta_k
\end{bmatrix}
\]

Keep the following concepts distinct:

- **Polar Tracking (guidance):** draws the alignment path and angle feedback. It does not silently rewrite the cursor's stored model-space coordinate.
- **Polar Snap/Lock (constraint):** when explicitly enabled, the point-acquisition candidate is constrained to the selected polar direction. The unprojected cursor coordinate remains available for display and the user can leave the acquisition zone to release the constraint.

Do not make the cursor teleport to the polar ray merely because an angular guide became visible. Show the guide first; apply coordinate projection only when the configured snap/lock behaviour says so.

The polar guide may be drawn as a long dotted alignment ray from the base point, with a subtle dotted arc/sector/indicator that communicates the angular relation as in the supplied references. Do not draw a full-radius semicircle for every frame if a simpler ray and angle label communicate the same direction more clearly on a phone.

## 43.7 Interaction with OSNAP, GRID, ORTHO and existing coordinate input

- Polar Tracking must work alongside OSNAP. An acquired endpoint/midpoint/intersection may be used as the base point or as a candidate intersection along a polar guide.
- Grid display remains independent of Dynamic Input and Polar Tracking.
- Grid snap remains a separate coordinate restriction.
- Ortho and Polar Tracking must have deterministic behaviour. For the initial implementation, make them mutually exclusive, matching the common AutoCAD drafting convention: enabling ORTHO disables POLAR; enabling POLAR disables ORTHO. Show a concise feedback message when this happens.
- Do not silently disable OSNAP when Polar Tracking is enabled.
- If grid snap and polar snap both attempt to constrain one candidate point, define and test a deterministic priority (recommended: explicit object snap > explicit numeric coordinate entry > active polar snap > grid snap > free cursor candidate). Do not permit two independent snap systems to alternately move the candidate.
- The command line remains authoritative and always available.
- Dynamic Input must mirror the command's current accepted prompt/parameter state rather than creating a second command engine.
- The virtual cursor continues to use the controller. The sheet remains navigation-only: one-finger pan and two-finger pan/pinch are still viewport navigation gestures, never drafting input.
- Controller overlay pointer ownership must prevent Dynamic Input fields from receiving a physical touch sequence that began inside the touchpad/joystick surface. Explicit taps on a Dynamic Input field are valid UI input; they are not sheet taps.

## 43.8 Command support matrix

Implement Dynamic Input and Polar Tracking incrementally, beginning with the commands that most benefit from point/distance/angle guidance.

| Command/step | Pointer input | Dimension input | Dynamic prompt | Polar tracking |
|---|---|---|---|---|
| LINE first point | X/Y | Not applicable | Specify first point | No base point yet |
| LINE next point | Optional X/Y | Distance + angle | Specify next point/Finish | Yes |
| POLYLINE first point | X/Y | Not applicable | Specify first point | No base point yet |
| POLYLINE next vertex | Optional X/Y | Distance + angle | Specify next vertex/Close/Finish | Yes, based on previous vertex |
| CIRCLE centre | X/Y | Not applicable | Specify centre | No base point until centre accepted |
| CIRCLE radius/diameter | Optional X/Y | Radius/diameter | Specify radius/diameter | Not normally needed unless construction mode requires a direction |
| ARC first/second/third point | X/Y | Step-dependent distance/angle | Current ARC prompt | Yes after a base point is accepted |
| MOVE/COPY base point and destination | X/Y | Displacement distance/angle | Current point prompt | Yes after base point |
| ROTATE base point/reference angle | X/Y | Angle | Current rotation prompt | Angular guidance as appropriate |
| FILLET/CHAMFER selection/point steps | Context-dependent | Radius/distance values where appropriate | Active prompt/options | Only when acquiring a geometric point |

Commands without a meaningful point or distance input should not show irrelevant dynamic fields.

## 43.9 Controls and discoverability

Provide separate status controls:

- `DYN` — master Dynamic Input ON/OFF.
- `POLAR` — master Polar Tracking ON/OFF.
- A small settings action for Dynamic Input components (Pointer, Dimension, Prompt).
- A Polar Tracking settings action for increment angle, additional angles, reference mode and tolerance.

Place these in the existing status/HUD or drafting settings area, using the professional black palette. Do not crowd the top header with multiple large neon buttons.

The controls must report their current state consistently throughout the header, function area and settings panel. There must be one authoritative ViewModel state for each feature.

If physical keyboard shortcuts are later added, F12 may toggle Dynamic Input and F10 may toggle Polar Tracking for familiarity with AutoCAD. However, mobile users must have visible touch controls; do not make physical function keys a requirement. Avoid intercepting Android system shortcuts or keyboard accessibility controls.

## 43.10 Rendering and performance requirements

- Render polar guides, tracking labels and Dynamic Input fields in a transient overlay layer separate from committed CAD entities.
- Do not add polar guide geometry or tooltips to the document entity collection.
- Do not include temporary tracking guides in PDF/DXF export or model-space extents.
- Cache reusable Paint/Path objects; update only the guide geometry and tooltip values that actually change.
- Do not rebuild the entire document draw list on each touchpad/joystick sample.
- Avoid allocating a large number of paths or text layouts for every pointer update.
- Invalidate only the viewport overlay region where practical.
- Keep the cursor/guide rendering frame-paced and responsive at 60 Hz and 120 Hz where supported.
- Hide all dynamic overlays when no command step requires them or when the corresponding mode is OFF.
- Do not let the Dynamic Input or Polar Tracking overlay alter the stable full-screen viewport transform.

## 43.11 Acceptance tests

### Dynamic Input

- [ ] DYN can be enabled and disabled independently of POLAR.
- [ ] Pointer Input displays precise model-space X/Y values near the virtual cursor.
- [ ] LINE's second-point prompt displays live distance and angle values.
- [ ] Dynamic values update as the virtual cursor moves.
- [ ] Dynamic prompt text matches the active command and command step.
- [ ] Invalid numeric input is rejected without corrupting the preview or geometry.
- [ ] Numeric entry from the cursor tooltip and command line updates the same command state.
- [ ] The Android keyboard does not open merely because the virtual cursor moves.
- [ ] Tooltip placement stays within the visible canvas and avoids opaque panels/essential geometry.
- [ ] Turning DYN OFF hides the cursor-adjacent input cluster while leaving the persistent command line fully functional.

### Polar Tracking

- [ ] POLAR can be enabled/disabled independently of DYN.
- [ ] Default 90° tracking displays a vertical alignment guide when appropriate.
- [ ] A 180° guide displays a horizontal alignment path as in the first reference photograph.
- [ ] A 126° or other custom/additional direction can be displayed when configured.
- [ ] The guide originates from the last accepted command base point.
- [ ] The angle label follows the current candidate direction.
- [ ] The guide disappears when the cursor leaves the acquisition tolerance or the command step/base point changes.
- [ ] Enabling ORTHO disables POLAR and vice versa, with feedback.
- [ ] OSNAP continues to work in conjunction with Polar Tracking.
- [ ] Polar Tracking alone does not silently rewrite the virtual cursor's stored model-space coordinates.
- [ ] Optional PolarSnap/lock projects only the point-acquisition candidate and can be released.
- [ ] Temporary guides never become document geometry or appear in exports/extents.

### Combined and regression

- [ ] Dynamic Input OFF + Polar Tracking ON displays polar guides without numeric tooltips.
- [ ] Dynamic Input ON + Polar Tracking OFF displays cursor input/prompt/dimension fields without polar guides.
- [ ] Both ON display the combined distance/angle tooltip and polar alignment path.
- [ ] Both OFF restore the normal command/controller workflow.
- [ ] LINE, POLYLINE, CIRCLE and ARC retain their existing command semantics.
- [ ] Touchpad/joystick move the virtual cursor; sheet gestures still only navigate.
- [ ] Dynamic Input fields do not consume touchpad/joystick pointer sequences.
- [ ] No temporary guide is saved, dimensioned, selected or exported.
- [ ] DYN/POLAR overlays do not cause viewport resize, rebase, pan or zoom.
- [ ] Rendering remains responsive during continuous cursor movement.

---

# 44. Updated implementation order — Dynamic Input and Polar Tracking

Add this subsystem after the existing geometry-command and viewport/controller work:

1. Add one authoritative Dynamic Input state with independent Pointer, Dimension and Prompt components.
2. Add one authoritative Polar Tracking state with configurable angular increments and reference conventions.
3. Implement model-space polar-angle candidate calculation from the last accepted command point.
4. Render temporary dotted polar alignment rays/arcs and angle labels in a dedicated viewport overlay.
5. Add cursor-adjacent Dynamic Input tooltip placement and live X/Y, distance/angle values.
6. Connect dynamic numeric-field entry to the existing command/coordinate input state; do not create a second command parser.
7. Add optional PolarSnap/lock as a distinct constraint mode; do not make visual tracking itself alter coordinates.
8. Add deterministic interaction rules with ORTHO, OSNAP and grid snap.
9. Implement LINE/POLYLINE first, then CIRCLE/ARC and supported modification commands.
10. Validate that dynamic overlays never become saved geometry, never enter DXF/PDF export, and do not affect drawing extents.
11. Benchmark continuous pointer movement and confirm that only transient overlay elements are updated.
12. Test the three reference scenarios: 180° horizontal tracking, arbitrary 126° tracking, and 90° vertical tracking.


---

# 45. Final Polar Tracking requirement — visual angular preview only

This section records the user's clarification after reviewing the proposed Dynamic Input feature. It **supersedes the editable numeric-entry and cursor-adjacent coordinate-field requirements in Section 43**.

## 45.1 What the user wants

The user does not want to type or insert a numerical value into editable fields positioned around the virtual cursor.

The required feature is the **visual Polar Tracking preview** shown in the supplied AutoCAD photographs:

- A temporary polar alignment line/ray appears from the accepted base point toward the cursor's current candidate direction.
- A temporary angular arc/sector illustrates the angle between the reference direction and the current candidate direction.
- A read-only numerical angle label (for example, `90°`, `126°` or `180°`) appears near the arc/guide.
- The guide line, arc and angle label update continuously as the virtual cursor moves.
- These elements appear while the user is positioning the next point, before the second point or subsequent point is accepted.
- The user can inspect the angle visually and decide when to accept the point.
- Nothing is committed to the drawing until the command's normal point-acceptance action occurs.

The intended behaviour is therefore **Polar Tracking with a live visual preview**, not full AutoCAD-style editable Dynamic Input fields.

## 45.2 Explicitly excluded features

Do not implement the following cursor-adjacent input elements:

- Editable X/Y coordinate fields.
- Editable distance fields.
- Editable angle fields.
- A numeric insertion box next to the cursor.
- A floating keyboard-entry panel attached to the polar guide.
- A requirement to tap a tooltip before entering the next point.
- Automatic opening of the Android keyboard during cursor movement.
- A second command-input system separate from the persistent command line.

Do not add the three Dynamic Input subcomponents (Pointer Input, Dimension Input and Dynamic Prompts) as editable near-cursor fields.

The persistent command line remains the sole general-purpose text/numeric command-entry surface. The ordinary command prompt and command-specific options remain in the existing command/Overview areas according to Sections 20 and 39.

## 45.3 Required visual preview

For a point-based command with an accepted base point \(B\) and a moving virtual cursor candidate \(P\):

1. Compute the candidate direction from \(B\) to \(P\).
2. Find the nearest configured Polar Tracking angle.
3. When the direction falls within the configured acquisition tolerance, display the polar guide.
4. Draw a temporary guide line/ray from the accepted base point in the selected angular direction.
5. Draw a temporary angle arc from the configured reference direction to the selected polar direction.
6. Display a small read-only angle label at a position near the arc, such as `90°`, `126°` or `180°`.
7. Continue to update the guide and label as the cursor moves.
8. Keep the normal live preview geometry (for example, the provisional LINE segment) visible at the same time.
9. When the user accepts the point, commit only the actual drawing geometry required by the active command. Do not commit the guide line, angular arc or angle label.
10. When the cursor leaves the configured angular acquisition tolerance, hide or release the guide according to the selected tracking policy.
11. When the command is cancelled or completed, remove all polar preview graphics.

### Visual hierarchy

- The actual candidate geometry preview is the primary preview.
- The polar guide line is a thin, dotted or dashed secondary construction guide.
- The angular arc is a restrained, temporary indicator.
- The angle value is a compact read-only label.
- Do not render large, filled, opaque tooltip boxes around the cursor.
- Do not obstruct the virtual cursor crosshair or snap marker.
- Use the professional black application palette; preserve the distinct CAD entity/layer colour system.
- Use a temporary overlay surface only if needed for angle-text legibility. The guide itself must remain visually lightweight.

## 45.4 Angle computation and reference behaviour

Use the accepted command base point and current candidate cursor position in model space:

\[
\Delta x=P_x-B_x,\qquad \Delta y=P_y-B_y
\]

\[
\theta=\operatorname{atan2}(\Delta y,\Delta x)
\]

Normalise the angle according to the drawing's configured angle convention.

The default 2D convention should remain:

- 0° along positive model-space X.
- Positive angles counter-clockwise.
- 90° upward in model space.
- Displayed angle follows the drawing's unit/zero-angle configuration.

Support the configured polar increments and additional angles described in Section 43.5, including arbitrary configured directions such as 126°.

For chained LINE/POLYLINE operations, allow the configured tracking reference to be either:

- Absolute: relative to the drawing's zero-angle direction.
- Relative: relative to the last accepted segment direction.

The selected reference must be visible in Polar Tracking settings. Do not silently change the convention between command steps.

## 45.5 Tracking does not automatically move or commit the cursor point

The visual guide must not cause a hidden coordinate jump.

- Polar Tracking guidance alone draws a visual reference; it does not change the virtual cursor's model-space coordinates.
- If PolarSnap/angle lock is separately enabled, it may project the command's **candidate point** onto the selected polar direction for point acquisition. Keep the raw cursor coordinate available internally and use an explicit snapped candidate for acceptance.
- Do not move the virtual cursor's stored coordinates merely because a guide appears.
- Do not commit any geometry until the existing PICK PT/acceptance workflow accepts the point.
- Do not add point acceptance to the sheet tap/double-tap path.
- The touchpad/joystick remains the cursor-movement device; the existing command-level acceptance mechanism remains responsible for accepting the candidate point.

## 45.6 Controls

Keep a dedicated `POLAR` ON/OFF control and the Polar Tracking settings surface.

Retain:
- Preset angular increments.
- Custom increment.
- Additional custom angles.
- Absolute or relative tracking reference.
- Angular acquisition tolerance.
- Optional visual guide/arc/label settings.
- Optional PolarSnap/angle lock as a separately named feature.

Remove or omit the proposed `DYN` master toggle and the Pointer/Dimension/Prompt component toggles if their only purpose is to control the excluded editable cursor-adjacent Dynamic Input fields.

Do not label the remaining visual feature `Dynamic Input`. Use **Polar Tracking** or **Polar Guide** so its behaviour is unambiguous.

## 45.7 Interaction with OSNAP, GRID and ORTHO

Retain the distinct behaviour described in Section 43.7, with these final clarifications:

- OSNAP remains available and may define the accepted base point.
- GRID display and GRID snap remain independent.
- ORTHO and POLAR use deterministic, mutually exclusive angular guidance; enabling one disables the other and gives concise feedback.
- When OSNAP and Polar Tracking are simultaneously active, show the polar guide only when it does not falsely imply that an incompatible snap candidate has been acquired.
- If explicit PolarSnap is enabled, define a deterministic point-acquisition priority. Recommended: explicit numeric coordinate entry from the command line > object snap > PolarSnap candidate > grid snap > free cursor candidate. Implement one final candidate point; never allow two independent snap systems to fight.
- The persistent command line remains the place for deliberate numeric entry.

## 45.8 Command support

Prioritise visual polar preview for:

- LINE after the first point has been accepted.
- POLYLINE after the first vertex has been accepted.
- ARC point-acquisition steps where a base point and direction are meaningful.
- MOVE/COPY destination after the base point is accepted.
- Other point-based commands only where polar direction is geometrically meaningful.

For CIRCLE, show a polar guide only for a construction step that has a meaningful direction from an accepted centre/base point. Do not add irrelevant angular guides to a simple radius-entry step.

## 45.9 Acceptance tests

- [ ] No editable X/Y, distance or angle fields appear beside the cursor.
- [ ] No floating numeric insertion box appears near the polar guide.
- [ ] No automatic keyboard opens during cursor movement.
- [ ] During LINE's second-point preview, a temporary polar guide line appears when the candidate direction matches a configured angle.
- [ ] A temporary angular arc appears between the reference direction and the selected polar direction.
- [ ] A read-only angle label such as 90°, 126° or 180° appears near the arc.
- [ ] The guide line, arc and angle label update continuously before point acceptance.
- [ ] The ordinary preview line remains visible while the polar guide is displayed.
- [ ] The user can move away from the guide and the guide is released/hidden.
- [ ] Accepting the point commits only the command geometry, not the guide/arc/label.
- [ ] Cancelling the command removes all polar preview graphics.
- [ ] Polar Tracking guidance alone does not move the stored cursor coordinate.
- [ ] Optional PolarSnap affects only the candidate point and is separately enabled.
- [ ] The feature works with the touchpad and joystick without changing their movement behaviour.
- [ ] The feature does not permit direct sheet tap, double-tap, drawing or point picking.
- [ ] No polar preview graphics appear in saved entities, model extents, PDF or DXF exports.
- [ ] The persistent command line remains available for exact numeric coordinate entry.

---

# 46. Updated implementation order — visual Polar Tracking only

Replace the Dynamic Input implementation sequence in Section 44 with this narrowed sequence:

1. Implement the shared Polar Tracking ON/OFF state and settings.
2. Calculate the nearest configured polar angle from the accepted command base point to the current candidate cursor position.
3. Render the temporary polar guide line/ray, angular arc and read-only angle label.
4. Connect this preview to LINE and POLYLINE first, then relevant ARC and modification-command point steps.
5. Keep the actual candidate geometry preview visible independently of the polar guide.
6. Keep cursor coordinates unchanged when merely showing polar guidance.
7. Implement optional PolarSnap as a separate candidate-point constraint, not as an automatic cursor movement.
8. Preserve the persistent command line as the only general-purpose numeric entry surface.
9. Verify that guide graphics are transient and excluded from document entities, extents, PDF and DXF export.
10. Do not implement editable cursor-adjacent Dynamic Input fields, a DYN toolbar control, or automatic keyboard activation.


---

# 47. Polar preview readouts — radial distance R, actual angle θ and active polar angle θp

This section clarifies the complete information visible in the user's AutoCAD reference photographs. It extends the visual-only Polar Tracking requirement in Section 45. It does **not** reintroduce editable Dynamic Input fields.

## 47.1 Required read-only information

While a point-based command is waiting for the next point, and after a base point has been accepted, the live cursor preview must present all of the following information where geometrically applicable:

1. **Radial distance R:** the live distance from the accepted base point to the current cursor candidate.
2. **Actual cursor angle θ:** the current unrounded direction from the accepted base point to the raw virtual cursor position.
3. **Active polar angle θp:** the configured polar direction currently being acquired or tracked (for example, 90°, 126° or 180°).
4. **Angular guidance:** a temporary angular arc/sector illustrating the active polar angle relative to the selected reference direction.
5. **Polar guide line/ray:** a temporary dotted/dashed alignment line extending from the accepted base point along the active polar direction.
6. **Current candidate geometry preview:** for example, the provisional line from the accepted point to the moving cursor candidate.

The user wants everything demonstrated in the photographs except the ability to insert/type a number into a cursor-adjacent field. Do not omit R or either angle merely because editable numeric input is excluded.

## 47.2 Meaning of each value — avoid ambiguity

Use three distinct semantic values:

- \(R\): raw radial distance from the accepted base point \(B\) to the live cursor candidate \(P\).
- \(\theta\): actual raw cursor bearing, calculated from \(B\) to \(P\), before any polar projection.
- \(\theta_p\): the active configured polar tracking direction currently selected/acquired by the tracking engine.

Calculate:

\[
\Delta x=P_x-B_x,\qquad \Delta y=P_y-B_y
\]

\[
R=\sqrt{(\Delta x)^2+(\Delta y)^2}
\]

\[
\theta=\operatorname{atan2}(\Delta y,\Delta x)
\]

Normalise \(\theta\) into the drawing's configured angular range.

For a configured polar angle set \(A=\{\alpha_1,\alpha_2,\dots,\alpha_n\}\), find the nearest configured direction using the smallest wrapped angular difference. When the candidate direction is within the configured acquisition tolerance of a configured direction, set \(\theta_p\) to that direction and display the polar guide.

Do not conflate:
- The measured current cursor direction \(\theta\).
- The nearest/acquired polar direction \(\theta_p\).

For example, if the cursor currently lies at 88.7° and the acquired polar direction is 90°, display both values distinctly:
- `R: 125.40 mm`
- `θ: 88.7°`
- `POLAR: 90°`

The angular arc and guide ray should identify the 90° tracking direction, while the raw cursor indicator/readout communicates the actual 88.7° direction. If no polar direction is currently acquired, hide the active polar angle/guide, but continue showing R and θ when the current command step supports point/distance guidance.

Do not display meaningless or unstable angle values when \(R\) is effectively zero. In that case show the distance as zero and suppress θ/θp until a valid direction exists.

## 47.3 Visual arrangement

The live drafting assistance must visually include:

- A line/ray from the accepted base point in the current polar direction.
- An angular arc/sector showing the angle from the selected reference axis or previous segment direction to the polar ray.
- A read-only angular label placed near the arc.
- A read-only radial distance value R.
- A read-only actual cursor bearing θ.
- A clear read-only label for the active polar direction θp.

Suggested compact presentation:

`R 125.40 mm   θ 88.7°   POLAR 90°`

This is an illustrative readout, not an editable input string. The actual layout may split the values into compact labels so the guide remains legible on a phone.

The visual mapping must be unambiguous:
- The radial measurement corresponds to the distance from the accepted base point to the current candidate.
- The actual cursor angle corresponds to the direct base-to-cursor vector.
- The polar angle corresponds to the angular guide ray and angular arc.
- The live preview segment corresponds to the current candidate point, not necessarily to the polar-projected point unless PolarSnap/lock is explicitly enabled.

Use a small, read-only overlay near the cursor/guide that adapts its position to avoid covering the cursor crosshair, snap marker, preview segment, arc, tool panels or other important geometry. Do not create input boxes or editable fields. A subdued high-opacity label background is allowed only as a visual text backing; it must not behave as an input control.

## 47.4 Visual guidance and PolarSnap remain separate

When only Polar Tracking is active:

- Show R, θ, θp, guide line and angular arc as read-only visual information.
- Keep the cursor's actual model-space coordinates unchanged.
- Do not project the cursor or candidate point onto the polar line merely because it is being displayed.
- Keep the preview segment attached to the raw cursor candidate.

When optional PolarSnap/angle lock is explicitly active:

- The command's accepted candidate may be projected onto the selected polar ray.
- Keep raw cursor data and constrained candidate data separate.
- R and θ must be clearly identified as either raw cursor measurements or constrained-candidate measurements. Recommended default: show raw cursor `R` and `θ`, and show `POLAR θp` as the selected guide. If a separate constrained distance is useful, label it explicitly as `R snap`; do not silently replace the raw R value.
- The live geometry preview should show both raw and constrained states only if the UI can distinguish them without clutter; otherwise show the constrained candidate with a subtle snap marker and preserve raw values in the internal state.
- Point acceptance uses the constrained candidate only when PolarSnap is enabled.

## 47.5 Command-step behaviour

- Before the first point of LINE/POLYLINE is accepted, there is no established base point. Do not show R, θ or a polar guide relative to a nonexistent base point. Show the normal command prompt only.
- After the first point is accepted, immediately start calculating R and θ from that accepted point to the live virtual cursor.
- As the cursor moves, update R and θ continuously.
- When a polar direction is acquired, display θp and the angular guide.
- If the raw cursor direction moves outside the acquisition tolerance, clear θp and the polar guide while continuing to show R and θ.
- When the next point is accepted in a chained LINE/POLYLINE command, that newly accepted vertex becomes the next base point. Recalculate all measurements relative to it.
- When the command completes or is cancelled, remove the complete temporary polar/distance/angle overlay.
- Use the current command's active geometric base point for ARC/MOVE/COPY and other supported operations; do not assume every command uses the same base-point transition.

## 47.6 Explicitly excluded input behaviour

The readouts must be strictly non-interactive:

- No text cursor/caret.
- No editable text field.
- No tap-to-edit.
- No keyboard activation.
- No long-press context menu.
- No numeric value insertion by tapping a displayed R or angle.
- No separate floating numeric input panel.

All deliberate numerical input remains in the persistent command line. The existing command parser and coordinate-expression syntax remain unchanged.

## 47.7 Acceptance tests

- [ ] After a LINE first point is accepted, live radial distance R appears as the cursor moves.
- [ ] Actual cursor angle θ appears and updates continuously.
- [ ] When the cursor acquires 90°, the active polar angle θp is shown as 90°.
- [ ] When the cursor acquires 126° (configured as an additional angle), θp shows 126°.
- [ ] When the cursor acquires 180°, θp shows 180°.
- [ ] R is measured from the accepted command base point to the live cursor candidate.
- [ ] θ is the raw cursor bearing, not silently rounded to the polar increment.
- [ ] θp is the active configured polar angle represented by the polar guide ray and angular arc.
- [ ] The angular arc and read-only angle label correspond to θp.
- [ ] The guide ray begins at the accepted base point.
- [ ] The ordinary preview geometry remains visible at the same time.
- [ ] Moving outside the acquisition tolerance hides θp and the guide but retains R and θ where meaningful.
- [ ] No R/θ/θp label can be focused, edited or tapped to enter a value.
- [ ] Tapping or long-pressing a value does not open the keyboard or trigger a command.
- [ ] No editable cursor-adjacent numeric fields or full Dynamic Input subsystem are introduced.
- [ ] All temporary overlays disappear on command completion/cancellation.
- [ ] Polar Tracking alone does not mutate cursor or document geometry.
- [ ] The feature works in LINE, POLYLINE and other point-based commands with a well-defined base point.
- [ ] No temporary guide/readout appears in saved entities, model extents, PDF or DXF export.

---

# 48. Updated implementation order — complete read-only polar measurements

Extend the implementation sequence in Section 46:

1. Retain the current visual Polar Tracking angle acquisition and guide rendering.
2. Add the live raw radial distance \(R\) from the accepted base point to the moving cursor candidate.
3. Add the live actual cursor bearing \(\theta\), without rounding it to the nearest tracking increment.
4. Add the active configured polar direction \(\theta_p\) as a separate read-only value.
5. Associate the angular arc and guide ray specifically with \(\theta_p\).
6. Keep R/θ readouts available even when no polar direction is acquired, provided a valid base point exists.
7. Ensure all values are display-only and cannot receive focus or launch the keyboard.
8. Keep PolarSnap/lock as a separate optional candidate constraint.
9. Verify the measurements against known 2D test vectors and the reference cases (90°, 126° and 180°).
