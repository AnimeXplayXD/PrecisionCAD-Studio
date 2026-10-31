# PrecisionCAD Studio — Desktop-Class 2D CAD Drafting & Technical Documentation for Android

**PrecisionCAD Studio** is a desktop-class, offline-first **2D CAD drafting and technical documentation environment** for Android built with Kotlin and Jetpack Compose. It brings the precision geometry creation, command-driven modification, layer/block management, intelligent automatic dimensioning (`QDIM`), and DXF interoperability of desktop systems (AutoCAD, BricsCAD, LibreCAD) to touch devices through a **dedicated velocity-sensitive virtual cursor controller**, a **pure 2D technical CAD viewport**, and a **verified CAD Command Compatibility Matrix**.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> This revised implementation plan incorporates your consolidated 2D CAD specification plus the **Comprehensive CAD Command Compatibility Matrix** documenting every command's supported options, input methods, expected output, limitations, and test cases (Section 5). Click **Proceed** when ready to build.

- **Confirmed Scope — Pure 2D Technical CAD Viewport (No 3D or Photorealistic Rendering)**: Development is 100% focused on 2D technical drafting, geometric precision, line weights/types, hatch patterns, selection windows, and engineering annotations. 3D solid modeling, 3D assemblies, PBR materials, and studio lighting are strictly excluded.
- **Confirmed Core Input — Dedicated Velocity-Sensitive Virtual Cursor Controller**: Provides a dedicated lower control deck featuring both a **Relative Touchpad** and a **Velocity-Sensitive Virtual Joystick** (with non-linear fine-control center response and zero post-release drift), paired with discrete step increments (`10`, `5`, `1`, `0.5`, `0.1`, `0.01`, custom) and direct numeric coordinate entry (`Absolute X,Y`, `Relative @X,Y`, `Polar L<θ`, `Relative Polar @L<θ`).
- **Confirmed Core Intelligence — Quick Dimension (`QDIM`) & 3-Stage Annotation Pipeline**: Implements `AutoDimensionEngine`, `DimensionLayoutEngine`, and `AdaptiveAnnotationRenderer` to automatically analyze selected geometry or entire drawings, synthesize hierarchical engineering dimensions (`Sparse`, `Balanced`, `Detailed`), resolve text/arrow collisions, and adapt arrow/text placement without altering stored model-space values.
- **Confirmed Document & Interoperability Standard — Multi-Unit Conversion, Paper/Model Space & DXF/PDF/SVG**: Supports `mm`, `cm`, `m`, `in`, and `ft` with an explicit user choice between **Display-Only Unit Conversion** vs. **Physical Geometry Rescaling**, independent ISO/ANSI sheet setup, Room metadata indexing with versioned document storage, and full DXF (import/export), PDF (plotted sheet output), and SVG export.
- **Confirmed Command Parity & Verification — Comprehensive CAD Command Compatibility Matrix**: Every supported CAD command is documented and verifiable against explicit options, input modalities, deterministic geometric outputs, 2D-scope limitations, and automated test cases—and is also browsable in-app via an interactive **Command Reference & Compatibility Inspector**.

---

## 1. Overview & Core Concept

- **What It Does**: Provides a complete, command- and tool-driven 2D CAD workstation on Android capable of creating, modifying, constraining, dimensioning, laying out, and exchanging complex mechanical, industrial, and architectural technical drawings.
- **Target Audience / Persona**: Mechanical and industrial engineers, CAD drafters, CNC machinists, and field technicians who need uncompromising desktop 2D CAD precision on Android phones and tablets without finger-occlusion errors.
- **Key Value**: Solves the two biggest bottlenecks of mobile CAD—imprecise touch input and tedious manual annotation—through a dedicated **Velocity-Sensitive Cursor Controller**, a one-tap **Quick Dimension (`QDIM`)** analysis and layout engine, and deterministic desktop command parity.

---

## 2. User Experience, Adaptive Workspace & Visual Design

### Key User Flows

1. **Project Hub, Unit System & Sheet Setup**:
   - Create or open drawings with explicit working units (`mm`, `cm`, `m`, `in`, `ft`), linear/angular precision (`0` to `6` decimal places, trailing/leading zero suppression), drafting standard (`ISO` or `ANSI`), and sheet presets (`ISO A0–A4`, `ANSI A–E`, or custom width/height in Portrait or Landscape with independent plot scale).
   - Changing a drawing's unit later prompts a clear two-option dialog:
     - **Option A — Convert Displayed Units Only**: Keeps physical size identical and converts displayed coordinates/dimensions (e.g., a `100 mm` entity displays as `10.0 cm`).
     - **Option B — Rescale Model Geometry**: Applies a physical scale factor to model-space coordinates so numerical values are re-expressed in the new unit while preserving physical proportions.
2. **Precision Cursor Control & Coordinate Entry Flow**:
   - Operate the CAD crosshair from the dedicated **Virtual Cursor Controller Panel** without touching the drawing canvas:
     - **Relative Touchpad Mode**: Dragging moves the crosshair relative to its current position; lifting and repositioning the finger never jumps the cursor.
     - **Velocity-Sensitive Joystick Mode**: Displacement direction steers the crosshair; displacement magnitude controls velocity along a configurable response curve with a fine-control zone near the center. Releasing the joystick immediately halts movement.
     - **Step Nudge & Direct Coordinate Bar**: Step the cursor by exact increments (`10`, `5`, `1`, `0.5`, `0.1`, `0.01`, or custom units) or type exact coordinates (`125,85`, `@25,-10`, `50<45`, `@30<90`) in the live Coordinate & Command HUD.
   - Seamless coexistence with hardware Bluetooth/USB mice, styluses, and external keyboards.
3. **Desktop 2D Drafting, Modification & Layer Workflow**:
   - **Draw Tools**: `LINE`, `POLYLINE`, `CIRCLE` (Center-Radius, Diameter, 2P, 3P, TTR), `ARC` (3P, SCE, CSA), `RECTANGLE`, `POLYGON`, `ELLIPSE`, `SPLINE`, `POINT`, `RAY`, `XLINE`, `DONUT`, `HATCH`, and `BOUNDARY`.
   - **Modify Tools**: `MOVE`, `COPY`, `ROTATE`, `SCALE`, `MIRROR`, `OFFSET`, `TRIM`, `EXTEND`, `FILLET`, `CHAMFER`, `STRETCH`, `BREAK`, `JOIN`, `EXPLODE`, `ARRAY` (Rectangular/Polar), `ALIGN`, `LENGTHEN`, and `PEDIT`.
   - **Selection & Layers**: Single pick, left-to-right enclosed **Window Selection** (solid blue frame), right-to-left intersecting **Crossing Selection** (dashed emerald frame), `Select All`, selection filters, and full **Layer & Block Management** (visibility, lock, freeze, color, linetype, lineweight).
4. **Quick Dimension (`QDIM`) & Technical Annotation Flow**:
   - Select geometry (`Select All`, window selection, or `QDIM REGION`) and trigger **Quick Dimension (`QDIM`)** with selectable density (**Sparse**, **Balanced**, **Detailed**).
   - `AutoDimensionEngine` detects overall bounding width/height, hole diameters, center-to-center pitches, edge-to-hole offsets, arc radii, and angles, avoiding redundant point-pair clutter.
   - `DimensionLayoutEngine` arranges primary, secondary, and tertiary dimensions into clean baselines and chains, automatically resolving text/line overlaps and preserving any manual dimensions.
   - Associative dimensions update automatically whenever underlying geometry is moved, stretched, or scaled.
5. **Model Space / Paper Space, Command Compatibility Inspector & DXF/PDF/SVG Exchange**:
   - Toggle between **Model Space** (1:1 geometry construction) and **Paper Space Sheet Layouts** (configured paper size, printable margins, viewport scale, and ISO/ANSI title block).
   - Open the built-in **CAD Command Compatibility Matrix & Reference** from the command bar to inspect command syntax, aliases, options, limitations, and live test verification status.
   - Import and export standard `.DXF` files preserving layers, colors, linetypes, blocks, hatches, and dimensions, or export plotted sheets to `.PDF`, `.SVG`, and `.PNG`.

### Adaptive Portrait & Landscape Interface

- **Portrait Workspace**:
  - **Upper Majority**: High-contrast 2D Technical Viewport with floating top Coordinate & Snap Status HUD (`X`, `Y`, `ΔL`, `∠θ`, active unit, active command prompt).
  - **Middle Dock**: Compact scrollable Command & Tool Ribbon + collapsible Layer/Properties/Quick-Dimension bottom sheets.
  - **Lower Dedicated Control Deck**: Velocity-Sensitive Virtual Joystick / Relative Touchpad switcher, discrete step-increment selector, Ortho/Grid/OSNAP quick toggles, and direct numeric/command input bar.
- **Landscape Workspace**:
  - **Left Dock**: Vertical Drawing & Modification command palette.
  - **Center**: Full-height 2D Technical Viewport with top Coordinate/Snap HUD and bottom Command Line.
  - **Right Dock**: Collapsible Properties Inspector, Layer Manager, and Quick Dimension / Constraint panel.
  - **Lower-Right Ergonomic Thumb Zone**: Dedicated Virtual Cursor Controller (Touchpad / Velocity Joystick) positioned for effortless thumb precision without covering the viewport.
- **Zero-Loss Orientation Transitions**:
  - Rotating between Portrait and Landscape preserves the active command, in-progress construction points, cursor coordinates, selection set, viewport zoom/pan matrix, snap state, and full undo/redo stack.

### Visual Identity & Technical Viewport Styling

- **Aesthetic Direction**: **Utilitarian Desktop CAD Dark Blueprint** — crisp, zero-distraction engineering workspace built for prolonged drafting accuracy and immediate visual parsing of line types, weights, and snap markers.
- **Color Palette**:
  - **Viewport Background**: Deep CAD Charcoal (`#0A0E14`) in Model Space; High-Contrast Technical Sheet (`#111722` with crisp paper boundary and margin guides) in Paper Space.
  - **Grid & Axes**: Minor grid (`#182232`), Major grid (`#26354D`), Origin X-Axis (`#EF4444`), Origin Y-Axis (`#22C55E`).
  - **Geometry & Selection**: Default Layer 0 (`#F8FAFC`), Selected Geometry highlight (`#00E5FF` dashed glow), Window Selection fill (`#3B82F6` at 18% alpha), Crossing Selection fill (`#10B981` at 18% alpha), Construction/XLine geometry (`#64748B` dashed).
  - **OSNAP & Controller Accents**: Active OSNAP marker (`#FFB300` Amber geometric badge: square for Endpoint, triangle for Midpoint, circle for Center, X for Intersection, perpendicular/tangent glyphs), Dimension & Annotation layer (`#38BDF8`).
- **Typography**:
  - **UI Headers & Command Labels**: **Space Grotesk** (bundled locally in `res/font/`).
  - **Coordinates, Command Line, Dimensions & Tables**: **JetBrains Mono** (bundled locally in `res/font/`) for strict tabular alignment of numeric readouts.

---

## 3. Key Architectural Decisions & Trade-Offs

- **Decision 1: Strict Separation of Model-Space Geometry and Screen-Space Viewport Transform**
  - *Chosen Approach*: All geometric entities and dimensions are stored in immutable double-precision (`Double`) model-space coordinates. An affine `ViewportTransform` (`panX`, `panY`, `zoomScale`, `sheetScale`) maps between model space and screen pixels for rendering, hit-testing, and OSNAP proximity queries.
  - *Why*: Ensures zooming, panning, or rotating the device never alters stored geometry or measured dimension values, while maintaining sub-millimeter precision across large drawings.
- **Decision 2: Three-Stage Dimensioning Pipeline (`AutoDimensionEngine` -> `DimensionLayoutEngine` -> `AdaptiveAnnotationRenderer`)**
  - *Chosen Approach*: Decouple feature recognition (`AutoDimensionEngine`), spatial collision/chain arrangement (`DimensionLayoutEngine`), and zoom-aware arrow/text rendering (`AdaptiveAnnotationRenderer`).
  - *Why*: Guarantees that `QDIM` produces manufacturing-grade drawings (overall bounds, hole patterns, non-overlapping baseline stacks) while short dimensions automatically flip arrows/text outside when zoomed or tightly spaced.
- **Decision 3: Room Metadata Index + Versioned Structured Document Files**
  - *Chosen Approach*: Use Room SQLite for project cataloguing, search, templates, and metadata while serializing the complete CAD document graph (entities, layers, blocks, dimension styles, layouts, undo snapshots) in a schema-versioned structured document store.
  - *Why*: Supports fast atomic saves, deterministic Undo/Redo command transactions, crash recovery, and lossless preservation of entity IDs and associative links.

---

## 4. Technical Architecture & System Design *(Technical Reference)*

### Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                 PrecisionCAD Studio Adaptive UI (Jetpack Compose)              │
├────────────────────────┬──────────────────────────────┬────────────────────────┤
│  Command & Tool Docks  │   2D Technical CAD Viewport  │  Dedicated Virtual     │
│ ┌────────────────────┐ │ ┌──────────────────────────┐ │  Cursor Controller     │
│ │ • Draw Commands    │ │ │ • Wireframe & Lineweight │ │ ┌────────────────────┐ │
│ │ • Modify Commands  │ │ │ • Linetypes & Hatches    │ │ │ • Relative Touchpad│ │
│ │ • Layer & Block Mgr│ │ │ • Grid & Origin Axes     │ │ │ • Velocity Joystick│ │
│ │ • Properties Panel │ │ │ • OSNAP Visual Markers   │ │ │ • Step Increments  │ │
│ │ • Unit/Sheet Setup │ │ │ • Window/Crossing Select │ │ │ • Coordinate Input │ │
│ │ • Command Matrix   │ │ │ • Model & Paper Space    │ │ │ • Ortho/Snap Locks │ │
│ └─────────┬──────────┘ │ └────────────┬─────────────┘ │ └─────────┬──────────┘ │
└───────────┼────────────┴──────────────┼───────────────┴───────────┼────────────┘
            │                           │                           │
            ▼                           ▼                           ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                   CadWorkspaceViewModel (Orientation-Safe StateFlow)           │
│  • Active Command State Machine (Prompt, Step, Preview Entities, Modifiers)   │
│  • Cursor Physics & Input Router (Touchpad, Joystick Velocity Loop, Mouse)     │
│  • Undo/Redo Transaction Stack & Selection Set Manager                         │
└───────────┬───────────────────────────┬───────────────────────────┬────────────┘
            │                           │                           │
            ▼                           ▼                           ▼
┌────────────────────────┐ ┌──────────────────────────┐ ┌────────────────────────┐
│ 2D CAD Geometry Kernel │ │ Intelligent Dimensioning │ │ Interoperability &     │
│ • Double-Precision     │ │ • AutoDimensionEngine    │ │ Persistence Layer      │
│   Entities & Spatial   │ │   (QDIM All/Select/Reg)  │ │ • DXF Parser & Writer  │
│   Bounding Index       │ │ • DimensionLayoutEngine  │ │ • SVG & Plotted PDF    │
│ • Intersection, Trim,  │ │   (Chains, Baselines,    │ │   Sheet Exporter       │
│   Extend, Offset,      │ │    Collision Solver)     │ │ • Room Project Index   │
│   Fillet, Chamfer      │ │ • AdaptiveAnnotation     │ │ • Versioned Document   │
│ • OSNAP & Grid Solver  │ │   Renderer (ISO/ANSI)    │ │   Store & Recovery     │
│ • Constraint Solver    │ │ • Associative Watcher    │ │                        │
└────────────────────────┘ └──────────────────────────┘ └────────────────────────┘
```

---

## 5. Comprehensive CAD Command Compatibility Matrix

Every command in PrecisionCAD Studio is governed by this explicit compatibility specification. Input methods across commands use the following shorthand codes:
- **`VC`**: Dedicated Virtual Cursor Controller (Relative Touchpad / Velocity Joystick + Pick/Confirm button)
- **`NUM`**: Direct Numeric Coordinate Entry (`X,Y`, `@X,Y`, `L<θ`, `@L<θ`, or scalar distance/angle)
- **`CMD`**: Command Line text entry, alias invocation, and option keyword buttons
- **`OSNAP`**: Object Snap & Grid Snap automatic target acquisition
- **`PTR`**: Direct Touch / Mouse / Stylus pick or window/crossing drag

### 5.1 Drawing Commands (`DRAW`)

| Command (Alias) | Supported Options & Sub-Modes | Input Methods | Expected Output & Geometric Behavior | Known Scope & Limitations | Verification Test Cases |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`LINE`** (`L`) | `Chain` (continuous segments), `Close` (`C` to close loop to start point of chain), `Undo` (`U` last segment), `Angle/Length` lock | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Creates one or more independent 2D `LineEntity` segments on active layer with exact start/end `(x,y)` coordinates. | 2D `Z=0` plane only; segments are individual entities unless joined via `JOIN` or created via `POLYLINE`. | **TC-DRW-01**: Enter `0,0` -> `@100,0` -> `@0,50` -> `C`; verify 3 closed segments with exact lengths `100`, `50`, `111.8034`. |
| **`POLYLINE`** (`PL`) | `Line` mode, `Arc` mode (bulge calculation), `Close` (`C`), `Width` (constant global width), `Undo` (`U` vertex) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Creates a single connected `PolylineEntity` (`LWPOLYLINE` in DXF) storing ordered vertices `(x, y, bulge)` and closed flag. | Variable per-segment start/end taper width is normalized to uniform polyline lineweight on DXF R12 fallback. | **TC-DRW-02**: Create 4-vertex polyline with 1 arc segment (`bulge = 1.0` semicircle) and `Close`; verify closed perimeter and area calculation. |
| **`CIRCLE`** (`C`) | `Center-Radius` (default), `Center-Diameter` (`D`), `2-Point` (`2P`), `3-Point` (`3P`), `Tan-Tan-Radius` (`TTR`) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Creates a `CircleEntity` with exact `(centerX, centerY, radius)`. `3P` solves circumcircle of 3 non-collinear points; `TTR` solves analytical circle tangent to 2 entities. | `3P` rejects collinear points with clear command warning; `TTR` requires valid radius capable of bridging the two entities. | **TC-DRW-03**: Run `CIRCLE` `3P` on `(0,10)`, `(10,0)`, `(0,-10)`; verify center `(0,0)` and `R = 10.0`. Test `TTR` between two orthogonal lines (`X=0`, `Y=0`) with `R=5` -> center `(5,5)`. |
| **`ARC`** (`A`) | `3-Point` (`3P`), `Start-Center-End` (`SCE`), `Center-Start-Angle` (`CSA`), `Direction` (`CCW` / `CW` toggle) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Creates an `ArcEntity` defined by `(centerX, centerY, radius, startAngleDeg, sweepAngleDeg)` following right-hand coordinate conventions. | Elliptical arcs are represented via `ELLIPSE` or `SPLINE` rather than circular `ArcEntity`. | **TC-DRW-04**: Create `SCE` arc with Start `(10,0)`, Center `(0,0)`, End `(0,10)`; verify `R = 10.0`, `start = 0°`, `sweep = +90°`, arc length `15.7080`. |
| **`RECTANGLE`** (`REC`) | `Corner-to-Corner` (default), `Center-and-Corner` (`C`), `Dimensions` (`D` width & height), `Fillet` (`F`), `Chamfer` (`CH`), `Rotation` (`R`) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Creates a closed orthogonal or rotated `PolylineEntity` (4 or 8 vertices when filleted/chamfered) with optional horizontal/vertical constraints. | Editing individual sides after `EXPLODE` converts it into independent lines/arcs. | **TC-DRW-05**: Create `REC` at `(10,10)` with `Dimensions` `W=80, H=40` and `Fillet R=5`; verify closed 8-segment rounded rectangle and overall bounds `80 × 40`. |
| **`POLYGON`** (`POL`) | `Sides` (`3` to `64`), `Inscribed in Circle` (`I`), `Circumscribed about Circle` (`C`), `Edge` (`E` two endpoints of one edge) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Creates a regular closed `PolylineEntity` with `N` equal sides and equal interior angles. | Maximum 64 sides to preserve interactive snap performance. | **TC-DRW-06**: Create 6-sided `Circumscribed` hex at `(0,0)` with radius `10` (across flats `20`); verify vertex distance `11.5470` and 6 equal sides. |
| **`ELLIPSE`** (`EL`) | `Center-Axes` (`C`: center, major axis endpoint, minor radius), `Axis-End` (two endpoints of major axis + minor distance) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Creates an `EllipseEntity` storing `(centerX, centerY, majorRadius, minorRadius, rotationAngleDeg)`. | Full closed ellipses supported; partial elliptical arcs convert to high-density polylines on `TRIM`. | **TC-DRW-07**: Create `ELLIPSE` at `(0,0)`, major endpoint `(20,0)`, minor `10`; verify quadrant OSNAP points at `(±20,0)` and `(0,±10)`. |
| **`SPLINE`** (`SPL`) | `Fit Points` (Catmull-Rom / cubic interpolation through pick points), `Control Vertices` (`CV` B-spline), `Close` (`C`), `Degree` (`3`) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Creates a smooth `SplineEntity` passing through fit points or guided by control polygon, tessellated adaptively for zoom-independent smoothness. | Boolean/trimming operations intersect against high-precision subdivided spline segments (`tolerance <= 0.01`). | **TC-DRW-08**: Create 4-point fit spline `(0,0) -> (10,15) -> (25,-5) -> (40,10)`; verify curve passes through all 4 fit points and exports `SPLINE` DXF record. |
| **`POINT`** (`PO`) | `Single` point, `Multiple` point mode, configurable `PDMODE` display style (Dot, Cross `+`, X-mark `×`, Circle-Cross `⊕`) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Places `PointEntity` nodes in model space that act as valid `Node/Endpoint` OSNAP targets. | Point marker screen size is governed by document `PDSIZE` setting. | **TC-DRW-09**: Place point at `(33.25, 18.75)` and verify `OSNAP` locks onto exact coordinates `(33.25, 18.75)`. |
| **`RAY`** (`RAY`) | `Base Point` + `Through Point` or `Angle` (`∠θ`), multi-ray radial creation from same base point | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Creates semi-infinite reference construction line starting at `basePoint` and extending infinitely along direction vector `u`. | Clipped to viewport bounds during rendering and sheet plotting; converts to finite `LineEntity` when trimmed at outer end. | **TC-DRW-10**: Create `RAY` from `(0,0)` through `(10,10)` (`45°`); verify intersection with vertical line `X=250` resolves at `(250,250)`. |
| **`XLINE`** (`XL`) | `Two-Point` (default), `Horizontal` (`H`), `Vertical` (`V`), `Angle` (`A`), `Bisect` (`B` angle bisector), `Offset` (`O` parallel to line) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Creates an infinite construction line (`XLineEntity`) extending in both directions for projection and alignment layouts. | Trimming an `XLINE` once converts it to a `RAY`; trimming a `RAY` converts it to a finite `LineEntity`. | **TC-DRW-11**: Create `XLINE` `Bisect` between `(0,0)->(10,0)` and `(0,0)->(0,10)`; verify infinite line passes through `(0,0)` at `45.0°`. |
| **`DONUT`** (`DO`) | `Inside Diameter` (`ID`), `Outside Diameter` (`OD`), continuous placement loop (`ID = 0` produces filled circle) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Creates a `DonutEntity` (closed annular ring with configurable inner/outer radii and solid/hatch fill representation). | Requires `OD > ID >= 0`; rejects invalid `ID >= OD` with prompt message. | **TC-DRW-12**: Create `DONUT` with `ID=10`, `OD=20` at `(50,50)`; verify inner/outer circumference snaps and DXF closed wide-polyline export. |
| **`HATCH`** (`H`) | `Patterns`: `ANSI31` (45° section), `ANSI32` (steel), `ANSI37` (crosshatch), `SOLID`, `GRID`, `DOTS`; `Scale`, `Angle`, `Pick Internal Point` or `Select Boundary Objects` | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Generates a `HatchEntity` bound to a closed contour with clipped hatch vector lines at the specified angle and pitch. | Pick-point boundary detection requires a closed, non-self-intersecting loop within gap tolerance (`0.05` units). | **TC-DRW-13**: Hatch a `100 × 50` rectangle containing a `⌀20` inner hole with `ANSI31` (`Scale=1, Angle=45°`); verify hatch lines clip cleanly around the inner hole island. |
| **`BOUNDARY`** (`BO`) | `Pick Internal Point`, output type `Polyline` (closed `LWPOLYLINE` loop) or `Region` contour | `VC`, `CMD`, `PTR` | Traces the minimal enclosing closed loop of intersecting lines, arcs, and polylines around the picked interior seed point. | Open contours with gaps larger than gap tolerance report `"Closed boundary could not be determined"`. | **TC-DRW-14**: Draw 4 overlapping lines forming a `40 × 40` square with overhanging tails; pick `(20,20)` with `BOUNDARY` -> verify closed `40 × 40` `PolylineEntity` is created. |

---

### 5.2 Modification Commands (`MODIFY`)

| Command (Alias) | Supported Options & Sub-Modes | Input Methods | Expected Output & Geometric Behavior | Known Scope & Limitations | Verification Test Cases |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`MOVE`** (`M`) | `Base Point + Second Point`, `Displacement` (`@dx,dy` or `L<θ`) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Translates all selected entities by vector `(dx, dy)` and updates any associative dimensions referencing those entities. | Locked-layer entities are filtered out of the move set automatically. | **TC-MOD-01**: Move circle at `(10,20)` by `@15,-5`; verify new center `(25,15)` and radius unchanged. |
| **`COPY`** (`CO` / `CP`) | `Single` or `Multiple` (default continuous placement), `Array` (`A` count & spacing along displacement vector) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Clones selected entities with new unique UUIDs at each target displacement while preserving layer, linetype, and lineweight. | Copying an entity without copying its dimension creates an independent un-dimensioned entity. | **TC-MOD-02**: Copy a line with `Array` count `4` and spacing `@20,0`; verify 3 new lines placed at `+20`, `+40`, `+60`. |
| **`ROTATE`** (`RO`) | `Base Point + Rotation Angle` (`CCW` positive degrees), `Copy` (`C` keep original), `Reference` (`R` align reference angle to new angle) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Rotates selected entities around `basePoint` by `θ` using exact 2D rotation matrix. | Non-uniformly scaled ellipses rotate their major axis angle `rotationAngleDeg`. | **TC-MOD-03**: Rotate line `(10,0)-(20,0)` around `(0,0)` by `+90°`; verify new endpoints `(0,10)-(0,20)`. |
| **`SCALE`** (`SC`) | `Base Point + Scale Factor` (`s > 0`), `Copy` (`C`), `Reference` (`R` old reference length -> new target length) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Uniformly scales coordinates, radii, and lengths relative to `basePoint` by factor `s`. | Scale factor `s <= 0` is rejected; non-uniform X/Y scaling is only supported on Block insertions (`INSERT`). | **TC-MOD-04**: Scale a `50 × 30` rectangle from `(0,0)` using `Reference` (`50` -> `75`, factor `1.5`); verify resulting bounds `75 × 45`. |
| **`MIRROR`** (`MI`) | `First Point + Second Point of Mirror Line`, `Erase Source Objects` (`Yes` / `No`, default `No`) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Reflects selected entities across the specified axis line, reversing arc sweep directions appropriately. | Text entities remain right-reading (`MIRRTEXT = 0` behavior) so mirrored annotations are never upside-down. | **TC-MOD-05**: Mirror arc centered at `(20,10)` across Y-axis (`(0,0)-(0,100)`) with `Erase=No`; verify new arc at `(-20,10)` with mirrored start/end angles. |
| **`OFFSET`** (`O`) | `Offset Distance` (`d > 0`), `Through Point` (`T`), `Multiple` (`M`), `Layer` (`Current` vs `Source`) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Creates parallel lines, concentric circles/arcs, or inward/outward offset polylines with automatic corner miter/trimming. | Inward offset of a circle with `d >= radius` is rejected with `"Cannot offset that entity: radius would be <= 0"`. | **TC-MOD-06**: Offset a `100 × 60` closed rectangle inward by `5.0`; verify resulting closed rectangle measures `90 × 50`. |
| **`TRIM`** (`TR`) | `Quick Mode` (all visible entities act as cutting edges by default) or `Select Cutting Edges` first, `Fence` / `Crossing` selection | `VC`, `CMD`, `OSNAP`, `PTR` | Splits target entity at exact analytical intersections with cutting edges and removes the picked segment portion. | Cannot trim an entity that has zero intersections with any cutting edge. | **TC-MOD-07**: Draw line `(-50,0)-(50,0)` intersected by vertical lines at `X=-10` and `X=10`; pick middle segment -> verify 2 remaining outer segments `(-50,0)-(-10,0)` and `(10,0)-(50,0)`. |
| **`EXTEND`** (`EX`) | `Quick Mode` (all visible entities act as boundary edges) or `Select Boundary Edges` first | `VC`, `CMD`, `OSNAP`, `PTR` | Projects the nearer endpoint of a line, arc, or open polyline along its natural trajectory until it intersects the nearest boundary edge. | Closed circles, closed polylines, and rays extending away from the boundary cannot be extended. | **TC-MOD-08**: Extend line `(0,0)-(30,0)` toward vertical boundary line at `X=50`; verify endpoint updates to `(50,0)`. |
| **`FILLET`** (`F`) | `Radius` (`R >= 0`, where `R = 0` produces sharp corner trim/extend intersection), `Trim` (`Trim` vs `No Trim`), `Polyline` (`P` fillet all corners of polyline), `Multiple` (`M`) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Inserts a tangent circular arc of radius `R` between two lines/polyline segments and trims/extends them to the tangent points. | Rejects parallel lines or cases where `R` exceeds the available segment lengths when `Trim` is active. | **TC-MOD-09**: Fillet orthogonal lines `(0,0)-(50,0)` and `(0,0)-(0,50)` with `R=10`; verify lines trimmed to `(10,0)` and `(0,10)` connected by tangent `90°` arc centered at `(10,10)`. |
| **`CHAMFER`** (`CHA`) | `Distances` (`D1`, `D2`), `Angle` (`A` length + angle), `Trim` (`Trim` / `No Trim`), `Polyline` (`P` chamfer all vertices), `Multiple` (`M`) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Bevels the corner between two non-parallel line segments using setback distances `D1` and `D2` and inserts the connecting bevel segment. | Applicable to line segments and polyline straight segments; circular arcs require `FILLET`. | **TC-MOD-10**: Chamfer corner `(0,0)` of `(0,0)-(40,0)` and `(0,0)-(0,40)` with `D1=5, D2=5`; verify bevel line connects `(5,0)` to `(0,5)` with length `7.0711`. |
| **`STRETCH`** (`S`) | `Crossing Window` selection + `Base Point` + `Displacement` | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Moves vertices/endpoints inside the crossing selection while keeping endpoints outside anchored; translates fully enclosed entities. | Entities must be selected via crossing window (or vertex pick) to identify which endpoints stretch vs remain fixed. | **TC-MOD-11**: Crossing-select right edge of a `100 × 50` rectangle and stretch by `@25,0`; verify rectangle becomes `125 × 50` and associative horizontal dimension updates to `125.0`. |
| **`BREAK`** (`BR`) | `Two-Point Break` (remove segment between P1 and P2), `Break at Point` (`@` split into two contiguous entities at single point) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Splits a line, polyline, circle, or arc into two entities or removes a span between two points (converts a broken circle into an `ArcEntity`). | Breaking a circle requires two distinct angles; single-point break is for open curves/lines. | **TC-MOD-12**: Break line `(0,0)-(100,0)` between `(30,0)` and `(60,0)`; verify two lines `(0,0)-(30,0)` and `(60,0)-(100,0)`. |
| **`JOIN`** (`J`) | Collinear lines, contiguous lines/arcs sharing endpoints within tolerance (`1e-4`) | `VC`, `CMD`, `PTR` | Merges collinear overlapping/contiguous lines into a single `LineEntity`, or chained lines/arcs into a unified `PolylineEntity`, or closes an arc into a `CircleEntity`. | Non-coplanar or disjoint entities separated by more than endpoint tolerance cannot be joined. | **TC-MOD-13**: Select lines `(0,0)-(20,0)` and `(20,0)-(20,30)` and run `JOIN`; verify single 3-vertex `PolylineEntity` of total length `50.0`. |
| **`EXPLODE`** (`X`) | Polylines, Rectangles, Polygons, Blocks (`INSERT`), Associative Dimensions, Hatches | `VC`, `CMD`, `PTR` | Decomposes a compound entity into its constituent primitive `LineEntity`, `ArcEntity`, and `TextEntity` objects. | Primitive lines, circles, arcs, and points cannot be exploded further. | **TC-MOD-14**: Explode a 4-sided rectangle `PolylineEntity`; verify 4 independent `LineEntity` objects replace the polyline. |
| **`ARRAY`** (`AR`) | `Rectangular` (`Rows`, `Columns`, `Row Offset`, `Col Offset`, `Angle`) and `Polar` (`Center Point`, `Item Count`, `Fill Angle = 360°`, `Rotate Items` toggle) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Generates structured rectangular grids or circular bolt-hole/tooth patterns from selected source entities. | Row/Column count limited to `1..100` per operation to prevent accidental multi-thousand entity stalls. | **TC-MOD-15**: Run `ARRAY` `Polar` on a `⌀8` hole at `(30,0)` around center `(0,0)` with `Count = 6, Fill = 360°`; verify 6 holes spaced at `60°` intervals on `R=30` bolt circle. |
| **`ALIGN`** (`AL`) | `1st Source -> 1st Destination`, `2nd Source -> 2nd Destination`, optional `Scale Objects to Alignment Points` (`Yes` / `No`) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Simultaneously translates, rotates, and optionally scales selected objects so source reference points align with target points. | 2D alignment uses 2 point pairs (translation + planar rotation + optional uniform scale). | **TC-MOD-16**: Align a horizontal `50 mm` part to an inclined `45°` target edge; verify exact endpoint coincidence and `45°` orientation. |
| **`LENGTHEN`** (`LEN`) | `Delta` (`DE` add/subtract length), `Percent` (`P`), `Total` (`T` set absolute length/angle), `Dynamic` (`DY`) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Adjusts the total length of a `LineEntity` or sweep angle/length of an `ArcEntity` from the picked endpoint while preserving direction/curvature. | Does not apply to closed entities (`CIRCLE`, closed `POLYLINE`). | **TC-MOD-17**: Run `LENGTHEN` `Total = 120` on line `(0,0)-(80,0)` picking near `(80,0)`; verify endpoint moves to `(120,0)`. |
| **`PEDIT`** (`PE`) | `Close`/`Open`, `Join` (`J`), `Width` (`W`), `Edit Vertex` (Move/Insert/Delete vertex), `Reverse` (`R`), `Fit`/`Decurve` | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Modifies properties and vertex topology of existing `PolylineEntity` objects (and optionally converts a picked `LINE`/`ARC` into a polyline first). | Self-intersecting vertex edits warn the user if the polyline is used as a hatch boundary. | **TC-MOD-18**: Convert line `(0,0)-(50,0)` via `PEDIT`, `Join` `(50,0)-(50,40)` and `(50,40)-(0,40)`, then `Close`; verify closed 4-vertex rectangle. |

---

### 5.3 Selection, Inspection & Inquiry Commands (`SELECT / INQUIRY`)

| Command (Alias) | Supported Options & Sub-Modes | Input Methods | Expected Output & Geometric Behavior | Known Scope & Limitations | Verification Test Cases |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`SELECT`** / Window & Crossing | `Single Pick`, `Window` (Left->Right enclosed blue), `Crossing` (Right->Left intersecting green), `Select All` (`CTRL+A` / `ALL`), `Add` (`+`), `Remove` (`-`), `Filter` (by Type/Layer/Color) | `VC`, `CMD`, `PTR` | Updates active selection set and displays grip handles + object count summary in Properties Inspector. | Frozen or locked layer entities are excluded from modification selections. | **TC-SEL-01**: Drag Left-to-Right window partially over a line and fully over a circle -> verify only the circle is selected; drag Right-to-Left over both -> verify both are selected. |
| **`DIST`** / **`MEASUREGEOM`** (`DI`) | `Distance` (`ΔX`, `ΔY`, `Direct Distance`, `Angle in XY`), `Radius`, `Angle` between 2 lines, `Area` & `Perimeter` of closed loop or point list | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Displays exact geometric measurements in the active drawing unit and precision without modifying the document. | Area measurement on self-intersecting polygons reports signed shoelace area with warning. | **TC-INQ-01**: Measure distance between `(10,15)` and `(40,55)` -> verify `Distance = 50.000`, `ΔX = 30.000`, `ΔY = 40.000`, `Angle = 53.13°`. |
| **`PROPERTIES`** (`PR` / `CHPROP`) | Edit `Layer`, `Color` (`ByLayer` or custom), `Linetype` (`ByLayer`, `Continuous`, `Dashed`, `Hidden`, `Center`, `Phantom`, `Dotted`), `Lineweight`, and geometry coordinates/radii | `VC`, `NUM`, `CMD`, `PTR` | Live-updates selected entities' graphical and geometric properties with undo transaction recording. | When multiple mixed entity types are selected, common properties (`Layer`, `Color`, `Linetype`, `Lineweight`) remain editable. | **TC-INQ-02**: Select a circle, change radius from `15` to `22.5` and layer to `CENTERLINES` in Properties panel -> verify model and associative diameter dimension update to `⌀45.0`. |
| **`ISOLATEOBJECTS`** / **`UNISOLATE`** (`ISOLATE` / `HIDEOBJECTS`) | `Isolate Selected` (temporarily hide unselected), `Hide Selected`, `End Object Isolation` (`UNISOLATE`) | `CMD`, `PTR` | Temporarily filters viewport visibility for uncluttered drafting on complex mechanical layouts without altering persistent layer states. | Session-scoped visibility state; `UNISOLATE` restores all layer-visible entities immediately. | **TC-INQ-03**: Isolate 2 circles out of 20 entities, edit them, then run `UNISOLATE` -> verify all 20 entities are visible again. |

---

### 5.4 Annotation, Dimensioning & Quick Dimension Commands (`ANNOTATE`)

| Command (Alias) | Supported Options & Sub-Modes | Input Methods | Expected Output & Geometric Behavior | Known Scope & Limitations | Verification Test Cases |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`QDIM`** (`QDIM`) | Modes: `QDIM ALL`, `QDIM SELECT`, `QDIM REGION`; Density: `Sparse`, `Balanced` (default), `Detailed`; Standard: `ISO` / `ANSI` | `VC`, `CMD`, `PTR` | Executes `AutoDimensionEngine` -> `DimensionLayoutEngine` -> `AdaptiveAnnotationRenderer` to automatically generate overall width/height, hole diameters, center spacing, edge offsets, and radii in collision-free baseline/chain stacks. | Never overwrites or duplicates existing manual dimensions without explicit confirmation; focuses on manufacturing-relevant relationships rather than exhaustive point pairs. | **TC-DIM-01**: Draw a `120 × 80` plate with 4 `⌀12` mounting holes and run `QDIM ALL` (`Balanced`); verify overall `120` & `80` dimensions, hole diameter callout `4× ⌀12`, and non-overlapping hole pitch dimensions. |
| **`DIMLINEAR`** (`DLI`) | `Horizontal`, `Vertical`, `Pick 2 Extension Origins` or `Select Object` directly, `Baseline` / `Continue` chaining | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Creates an associative horizontal or vertical `DimensionEntity` measuring `|x2 - x1|` or `|y2 - y1|`. | Rotated linear measurements use `DIMALIGNED`. | **TC-DIM-02**: Create horizontal `DIMLINEAR` between `(0,0)` and `(75.5, 20)` -> verify dimension reads `75.50` and updates if `(75.5, 20)` is stretched. |
| **`DIMALIGNED`** (`DAL`) | `Pick 2 Extension Origins` or `Select Line/Segment`, offset distance for dimension line | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Creates an associative `DimensionEntity` parallel to the measured points showing true Euclidean distance. | Extension lines remain perpendicular to the measured vector. | **TC-DIM-03**: Dimension inclined line `(0,0)-(30,40)` with `DIMALIGNED` -> verify measured value is `50.00`. |
| **`DIMANGULAR`** (`DAN`) | `Pick 2 Non-Parallel Lines` or `Pick 3 Points (Vertex + 2 Endpoints)` or `Select Arc` | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Creates an angular `DimensionEntity` displaying degrees (`°`) or degrees-minutes-seconds (`DMS`) along a dimension arc. | Rejects parallel lines with clear message. | **TC-DIM-04**: Apply `DIMANGULAR` between `(0,0)-(50,0)` and `(0,0)-(50,50)` -> verify `45.0°` arc annotation. |
| **`DIMRADIUS`** (`DRA`) & **`DIMDIAMETER`** (`DDI`) | Pick `Circle` or `Arc`, leader angle/position placement, automatic `R` or `⌀` prefix, internal vs external arrow auto-flip | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Creates associative radial (`R`) or diametric (`⌀`) dimension linked to the target circle/arc center and radius. | `AdaptiveAnnotationRenderer` automatically moves arrows and text outside for small holes where internal span cannot fit arrows. | **TC-DIM-05**: Apply `DIMDIAMETER` to a small `⌀2.5 mm` hole and a large `⌀80 mm` bore -> verify `⌀2.5` uses external arrows/leader without obscuring the hole while `⌀80` fits cleanly. |
| **`DIMORDINATE`** (`DOR`) | `X-Datum` (horizontal coordinate from origin/datum) or `Y-Datum` (vertical coordinate), automatic jogged leader when crowded | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Creates datum ordinate callout (`X` or `Y` distance from current origin) commonly used on CNC hole tables and plates. | Values reference active drawing origin `(0,0)`. | **TC-DIM-06**: Place `X-Datum` and `Y-Datum` ordinate dimensions on hole center `(42.5, 18.0)` -> verify callouts `42.50` and `18.00`. |
| **`LEADER`** / **`MLEADER`** (`LE` / `MLD`) | `Arrowhead Point -> Landing Shoulder -> Text Note`, GD&T tolerance frame (`⌖`, `∥`, `⊥`, ` concentricity`, `±tol`) | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Creates a multi-segment technical leader with arrowhead, horizontal shoulder landing, and annotation/tolerance text. | Maximum 4 leader vertices per callout. | **TC-DIM-07**: Create `MLEADER` at hole quadrant `(30,20)` with note `"CHAMFER 1.0 × 45°"` -> verify arrow, shoulder, and non-overlapping text. |
| **`TEXT`** / **`MTEXT`** (`DT` / `T` / `MT`) | `Insertion Point`, `Text Height`, `Rotation Angle`, `Alignment` (`Left`, `Center`, `Right`, `Middle`), multi-line formatting | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Creates single-line or multi-line technical text entities scaled in model or paper units. | Uses bundled technical vector/TTF font for guaranteed offline fidelity. | **TC-DIM-08**: Insert `MTEXT` at `(10,10)` with height `3.5 mm` and 2 lines -> verify bounding box, grip selection, and DXF `MTEXT` export. |
| **`CENTERMARK`** & **`CENTERLINE`** (`CM` / `CL`) | Pick `Circle`/`Arc` for cross-hair center mark (with overshoot extensions), or pick `2 Parallel Lines` for bisecting centerline | `VC`, `CMD`, `OSNAP`, `PTR` | Generates associative center marks or alternating long-short dashed centerlines (`CENTER` linetype). | Center mark extensions scale proportionally to circle radius and active dimension style. | **TC-DIM-09**: Apply `CENTERMARK` to `⌀40` circle at `(50,50)` -> verify crosshairs centered at `(50,50)` with `CENTER` dash-dot extensions. |

---

### 5.5 Document, Layer, Block, Constraint & Interoperability Commands (`DOCUMENT / SYSTEM`)

| Command (Alias) | Supported Options & Sub-Modes | Input Methods | Expected Output & Geometric Behavior | Known Scope & Limitations | Verification Test Cases |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`LAYER`** (`LA`) | `New`, `Delete`, `Set Current`, `Visibility` (`On`/`Off`), `Freeze`/`Thaw`, `Lock`/`Unlock`, `Color`, `Linetype`, `Lineweight` | `CMD`, `PTR` | Manages document layer table; updates viewport rendering and selection eligibility immediately. | Layer `0` and `DEFPOINTS` cannot be deleted. | **TC-DOC-01**: Lock layer `CONSTRUCTION`, attempt to `DELETE` or `MOVE` entities on it -> verify entities remain protected and unmodified. |
| **`UNITS`** (`UN`) | Working unit (`mm`, `cm`, `m`, `in`, `ft`), Conversion Mode: **Mode A (Display Conversion Only)** vs **Mode B (Rescale Geometry)**, Linear/Angular Precision (`0–6` decimals) | `CMD`, `PTR` | Updates unit metadata; Mode A converts displayed measurements without changing physical size; Mode B rescales model-space coordinates by exact factor. | Prompts explicit confirmation explaining Mode A vs Mode B before committing changes. | **TC-DOC-02**: Draw `100 mm` line; switch to `cm` in Mode A -> coordinate HUD and dimension read `10.00 cm`; switch to `in` in Mode B -> verify rescaled geometry and accurate physical conversion. |
| **`PAGESETUP`** / **`LAYOUT`** (`PS` / `LO`) | Toggle `Model Space` vs `Paper Space`, Sheet Presets (`ISO A0–A4`, `ANSI A–E`, `Custom`), `Portrait`/`Landscape`, `Plot Scale` (`1:1`, `1:2`, `1:5`, `1:10`, `2:1`, custom), `Title Block` | `CMD`, `PTR` | Configures printable sheet dimensions, margins, title block fields, and viewport plot scale independently of model geometry. | Changing sheet size or orientation never alters model-space entity coordinates. | **TC-DOC-03**: Switch from `ISO A4 Landscape (297×210 mm)` to `ISO A3 Landscape (420×297 mm)` at `1:2` scale -> verify model geometry is untouched while paper frame updates. |
| **`BLOCK`** (`B`) & **`INSERT`** (`I`) | `Create Block` (Name, Base Point, Entities, Attributes), `Insert Block` (Position, Uniform Scale, Rotation), `Explode on Insert` | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Defines reusable block definitions in document block table and places `BlockInsertEntity` instances (`INSERT` in DXF). | Nested block depth capped at 5 levels to prevent circular reference recursion. | **TC-DOC-04**: Define block `M8_BOLT_HEAD` from hex + circle; insert 4 instances at different rotations -> verify all 4 render and export as DXF `BLOCK`/`INSERT`. |
| **`GEOMCONSTRAINT`** & **`DIMCONSTRAINT`** (`GC` / `DC`) | `Horizontal`, `Vertical`, `Parallel`, `Perpendicular`, `Tangent`, `Concentric`, `Coincident`, `Equal`, `Fix`, plus driving dimensional distance/radius locks | `VC`, `NUM`, `CMD`, `OSNAP`, `PTR` | Applies persistent 2D geometric or dimensional constraints and runs iterative relaxation solver to satisfy constraints and update associative dimensions. | Over-constrained conflicts are flagged in Crimson and prevented from corrupting existing valid geometry. | **TC-DOC-05**: Apply `Perpendicular` and `Equal` constraints to two lines of lengths `40` and `65` -> verify lines snap orthogonal with equal length. |
| **`DXFIN`** / **`DXFOUT`** / **`EXPORTPDF`** / **`EXPORTSVG`** | DXF ASCII R2000/R12 Import & Export (`HEADER`, `TABLES`, `BLOCKS`, `ENTITIES`), Plotted Sheet `.PDF` export, Vector `.SVG` export | `CMD`, `PTR` | Round-trips supported 2D entities, layers, colors, linetypes, blocks, hatches, and dimensions to/from `.DXF`, and exports scale-accurate `.PDF`/`.SVG` drawings. | Binary DWG files require conversion to DXF; unsupported 3D mesh entities (`3DSOLID`, `MESH`) in imported DXFs are safely ignored with a summary notice. | **TC-DOC-06**: Export a multi-layer dimensioned drawing to `.DXF`, re-import into a blank document -> verify 100% preservation of entity counts, coordinates, layers, units, and dimensions. |
| **`UNDO`** (`U`) & **`REDO`** (`REDO`) | Multi-step transactional Undo and Redo across geometry creation, modification, layer edits, unit changes, and `QDIM` operations | `CMD`, `PTR` | Restores exact prior or subsequent document snapshot from the versioned command transaction stack. | Clearing project history or closing the app commits the saved file state. | **TC-DOC-07**: Execute `LINE`, `FILLET`, `QDIM ALL`, then `UNDO` 3 times and `REDO` 3 times -> verify bit-for-bit restoration of entities and dimensions at every step. |
