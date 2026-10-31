# PrecisionCAD Studio
## Revised Product Specification and Implementation Plan for Android

**Document status:** Consolidated implementation specification  
**Product focus:** Desktop-class 2D CAD drafting on Android  
**Primary platform:** Android  
**Application technology:** Kotlin and Jetpack Compose  
**Rendering scope:** Technical CAD viewport only  
**Development philosophy:** 2D-first, offline-first, precision-oriented

---

# 1. Product Identity and Vision

PrecisionCAD Studio is a desktop-class 2D CAD drafting and technical drawing application designed specifically for Android.

Its purpose is to bring the broad drafting, editing, annotation, dimensioning, geometric construction, document-management, and file-interchange capabilities of desktop CAD applications such as AutoCAD, BricsCAD, and LibreCAD into the Android ecosystem.

The application must not be treated as a simplified mobile CAD viewer or a conventional touchscreen drawing application. It should provide a serious CAD workspace with precise geometry, command-driven operations, configurable drafting standards, and an interaction model adapted to touch devices.

## 1.1 Core principles

- Desktop-class 2D CAD functionality.
- Native Android application using Kotlin and Jetpack Compose.
- Precision drafting rather than freehand drawing.
- A simple, efficient technical CAD viewport.
- A dedicated velocity-sensitive virtual cursor controller.
- Command-based and tool-based workflows.
- Adaptive portrait and landscape layouts.
- Configurable drawing units and sheet settings.
- Automatic dimension generation and intelligent annotation placement.
- Offline-first project operation.
- Robust DXF interoperability.

## 1.2 Product scope

The primary product is a comprehensive 2D CAD drafting environment.

Three-dimensional modelling, assemblies, photorealistic rendering, PBR materials, studio lighting, and product-presentation rendering are outside the current scope. The viewport exists for technical CAD representation and engineering drafting only.

---

# 2. Rendering Architecture — CAD Viewport Only

The application must use a deliberately simple technical CAD rendering system. It must not allocate development resources to photorealistic or presentation-oriented rendering.

## 2.1 Supported viewport features

- Wireframe geometry.
- 2D geometry with configurable line weights.
- Grid and coordinate visualization.
- Selection highlighting.
- Object-snap indicators.
- Construction and reference geometry.
- Dimension and annotation rendering.
- Continuous, dashed, dotted, and other supported line types.
- Hatch patterns and solid fills.
- Centre lines and centre marks.
- Technical drawing presentation.
- Selection windows and crossing windows.
- Viewport pan, zoom, and fit-to-drawing operations.

## 2.2 Rendering objectives

- High performance on ordinary Android hardware.
- Accurate geometric representation.
- Consistent line weights and line types.
- Smooth zooming and panning.
- Efficient rendering of large drawings.
- Accurate selection and highlighting.
- Antialiasing for clean geometric edges.
- Hardware-accelerated rendering where beneficial.

## 2.3 Rendering technology

Use a lightweight 2D rendering pipeline based on Android Canvas or an appropriate OpenGL ES implementation. The final choice should be established through a performance prototype rather than assumed in advance.

The rendering architecture must separate document geometry from viewport presentation:

- Geometry is stored in model coordinates.
- Screen coordinates are calculated through a viewport transformation.
- Zoom and pan affect the view transformation, not the underlying geometry.
- Selection and snapping calculations use well-defined coordinate transformations.
- Annotation rendering is handled separately from geometric entity storage.

No PBR renderer, material library, studio-lighting system, environmental reflections, or dedicated rendering studio is required.

---

# 3. Drawing Documents, Sheets, Units, and Coordinate Systems

Users must be able to choose and change the measurement system and sheet configuration of each drawing.

This is a core document feature, not merely a display preference.

## 3.1 Configurable drawing units

The application must support, at minimum:

- Millimetres (mm).
- Centimetres (cm).
- Metres (m).
- Inches (in).
- Feet (ft).

Additional unit systems may be added later.

The user must be able to choose the drawing's working unit when creating a project and change it later through drawing settings.

## 3.2 Unit-change behaviour

Changing the drawing unit must offer an explicit choice between two distinct operations:

**A. Change displayed units without changing physical geometry**

The underlying geometry retains its physical size. Coordinates, dimensions, and measurements are converted for display.

Example: a line measuring 100 mm is displayed as 10 cm after changing the display unit.

**B. Rescale the drawing**

The actual model-space coordinates are converted by a scale factor so that their numerical values are re-expressed in the new unit while preserving the same physical size.

The application must explain the distinction before applying a potentially destructive conversion. It must not silently reinterpret existing coordinates.

## 3.3 Precision and numeric formats

Provide configurable:

- Linear precision.
- Angular precision.
- Decimal places.
- Decimal separator where appropriate.
- Rounding and display rules.
- Unit suffix visibility.
- Leading and trailing zero suppression.
- Architectural or engineering display formats where supported.

The displayed precision must not alter the stored geometric precision.

## 3.4 Sheet and drawing setup

Users must be able to configure the drawing sheet and its intended output format.

Provide:

- Sheet width and height.
- Standard paper presets, including A-series sizes and common ANSI sizes.
- Portrait and landscape sheet orientation.
- Custom sheet dimensions.
- Drawing scale.
- Drawing limits.
- Model-space and paper-space settings.
- Layout creation and management.
- Title-block configuration.
- Printable area and margins.
- Units for sheet dimensions.
- Page setup for PDF and print output.

Changing the sheet size must not unexpectedly resize the model geometry. Sheet dimensions, model-space units, and plotted scale must remain separate concepts.

## 3.5 Coordinate systems

- Cartesian X/Y coordinates.
- Absolute coordinate entry.
- Relative coordinate entry.
- Polar coordinate entry.
- Relative polar input.
- User-configurable origin where supported.
- Coordinate HUD.
- Dynamic coordinate input.
- Orthographic direction restrictions.

The coordinate display must clearly identify the active unit.

---

# 4. Dedicated Velocity-Sensitive Virtual Cursor Controller

A defining feature of PrecisionCAD Studio is an independent cursor-control mechanism.

Conventional mobile CAD applications often require users to touch the drawing canvas directly and drag a finger across the geometry. PrecisionCAD Studio must instead provide a dedicated control area that moves the CAD crosshair without requiring direct contact with the drawing.

## 4.1 Controller modes

Provide two modes sharing the same underlying cursor-positioning engine.

### A. Relative touchpad

- Finger displacement moves the CAD cursor.
- Cursor movement is relative, not tied to the finger's absolute screen position.
- Lifting and repositioning the finger does not relocate the CAD cursor.
- Sensitivity is configurable.
- Short movements support fine positioning.
- Repeated strokes allow movement across larger drawing distances.
- The control area remains separate from the drawing viewport.

### B. Velocity-sensitive virtual joystick

- A movable control indicator responds to finger movement.
- Direction determines cursor movement direction.
- Displacement from the joystick's neutral position determines cursor velocity.
- Small displacement produces slow, precise movement.
- Greater displacement produces faster movement.
- Returning towards the centre reduces velocity.
- Releasing the joystick stops cursor movement.
- The joystick must not continue moving the cursor after release.

The velocity curve should be configurable and should support a fine-control region near the centre. Cursor movement must remain predictable and controllable.

## 4.2 Cursor coordinate display

Display current model-space coordinates in a persistent or readily accessible HUD.

Example:

```text
X: 125.000 mm
Y: 85.000 mm
```

The coordinates must update as the cursor moves.

Provide direct numeric coordinate entry. For example, entering X = 25 and Y = 30 should position the cursor at that exact model-space location, subject to the user's chosen coordinate-entry mode.

## 4.3 Movement increments

Provide configurable movement increments, with sensible defaults such as:

- 10 units.
- 5 units.
- 1 unit.
- 0.5 units.
- 0.1 units.
- 0.01 units.

The user must be able to define custom increments.

Movement sensitivity, cursor step size, grid interval, and coordinate precision are separate settings and must not be conflated.

## 4.4 Controller configuration

- Select relative touchpad or velocity joystick.
- Adjust sensitivity.
- Adjust acceleration or velocity response.
- Configure fine-control behaviour.
- Configure movement increments.
- Enable or disable controller assistance.
- Provide a convenient way to reposition or collapse the controller panel.

## 4.5 Alternative input devices

The controller must not be mandatory. Support conventional input methods where available:

- Bluetooth or USB mouse.
- Stylus.
- External keyboard.
- Touch interaction for selection and direct operations.

The virtual controller remains the principal touch-first cursor method.

---

# 5. Grid, Snap, and Precision Alignment

The application must provide automatic coordinate alignment while preserving the ability to position geometry at arbitrary coordinates.

## 5.1 Grid display

- Configurable major and minor grid intervals.
- Adaptive grid density at different zoom levels.
- Grid visibility toggle.
- Configurable grid appearance.
- Grid origin configuration where supported.

The visible grid spacing and active snap interval must be independent. A coarse visible grid may coexist with a finer snap interval.

## 5.2 Grid Snap

When Grid Snap is enabled, the cursor aligns with the nearest valid grid intersection.

For example, with a grid interval of one unit, a cursor position near (2.17, 2.12) may resolve to (2, 2).

Support configurable intervals, including examples such as:

- 1 unit.
- 0.5 units.
- 0.25 units.
- 0.1 units.
- 0.01 units.

These are examples, not fixed application-wide values. The active drawing unit determines their physical interpretation.

## 5.3 Object Snap (OSNAP)

Provide conventional object-snapping modes:

- Endpoint.
- Midpoint.
- Centre.
- Intersection.
- Apparent intersection where supported.
- Quadrant.
- Perpendicular.
- Tangent.
- Nearest.
- Parallel.
- Extension.
- Insertion point.

The cursor must identify a valid snapping target and show an appropriate visual marker.

## 5.4 Snap priority and overrides

- Grid Snap and Object Snap operate independently.
- Individual object-snap modes can be enabled or disabled.
- Snap target selection uses screen-space proximity while preserving model-space accuracy.
- Temporary overrides can force a particular snap mode.
- Users can temporarily disable snapping.
- Exact coordinate entry takes precedence over automatic alignment.
- Horizontal and vertical movement locks are available.

Grid snapping must never force all geometry onto integer coordinates. Arbitrary coordinates must remain available through numeric entry and unrestricted cursor movement.

---

# 6. Adaptive Portrait and Landscape Interface

The application must support portrait and landscape orientations through purpose-designed adaptive layouts.

Changing orientation must not interrupt an active drafting operation.

## 6.1 Portrait mode

Recommended arrangement:

- Main drawing viewport occupies the upper majority of the display.
- Compact coordinate HUD remains visible.
- Virtual cursor controller occupies a dedicated lower section.
- Drawing tools are available through a compact toolbar or expandable command ribbon.
- Command input is accessible without permanently covering the drawing.
- Properties and layer controls appear in expandable panels.

## 6.2 Landscape mode

Recommended arrangement:

- Central drawing viewport.
- Left-side drawing and modification toolbar.
- Right-side properties and layer inspector, collapsible where necessary.
- Bottom command line and coordinate information.
- Dedicated virtual cursor controller in a lower control panel.
- Optional collapsible feature and properties panels.

## 6.3 State preservation during rotation

Preserve:

- Current drawing state.
- Cursor coordinates.
- Active command.
- Selected geometry.
- Zoom level.
- Viewport position.
- Active snap configuration.
- Undo/redo history.
- Current unit and sheet settings.
- Dimension style and annotation settings.

The layout must adapt without changing the drawing's underlying geometry or active command state.

---

# 7. Desktop CAD Feature Coverage

The objective is to progressively implement the drafting and editing capabilities available in desktop AutoCAD, BricsCAD, and LibreCAD, adapted to Android.

The following is a target capability catalogue, not a claim that all features are already implemented.

## 7.1 Drawing commands

- LINE.
- POLYLINE.
- CIRCLE.
- ARC.
- RECTANGLE.
- POLYGON.
- ELLIPSE.
- SPLINE.
- POINT.
- RAY.
- XLINE.
- DONUT.
- HATCH.
- BOUNDARY.

## 7.2 Modification commands

- MOVE.
- COPY.
- ROTATE.
- SCALE.
- MIRROR.
- OFFSET.
- TRIM.
- EXTEND.
- FILLET.
- CHAMFER.
- STRETCH.
- BREAK.
- JOIN.
- EXPLODE.
- ARRAY.
- ALIGN.
- LENGTHEN.
- PEDIT.

## 7.3 Selection and inspection

- Single-object selection.
- Window selection.
- Crossing selection.
- Select All.
- Add/remove from selection.
- Selection filters.
- Object properties inspection.
- Object isolation and visibility controls.
- Group management.
- Drawing comparison where feasible.

## 7.4 Annotation and dimensioning

- Linear dimensions.
- Aligned dimensions.
- Angular dimensions.
- Radius dimensions.
- Diameter dimensions.
- Ordinate dimensions.
- Leaders and multileaders.
- Single-line and multiline text.
- Centre marks.
- Tolerance annotations.
- Dimension styles.
- Associative dimensions.

## 7.5 Drafting and document management

- Layer management.
- Layer visibility, locking, and freezing where supported.
- Colour management.
- Line types.
- Line weights.
- Object properties.
- Blocks and block insertion.
- Block attributes.
- External references where supported.
- Drawing templates.
- Model space.
- Paper space.
- Layout management.
- Viewports.
- Drawing units.
- Coordinate systems.
- Drawing limits.
- Grid and snap configuration.
- Reusable block libraries.

## 7.6 Advanced drafting

- Parametric geometric constraints.
- Dimensional constraints.
- Geometric construction tools.
- Associative dimensions.
- Boundary detection.
- Hatch pattern libraries.
- Construction geometry.
- Advanced selection.
- Drawing recovery and validation.

Feature parity must be evaluated command by command. A desktop feature should not be advertised as supported until its behaviour, edge cases, persistence, and file interchange have been tested.

---

# 8. Quick Dimension — Automatic Dimension Generation

Quick Dimension is a core feature, comparable in purpose to AutoCAD's QDIM command.

Its purpose is to analyse selected drawing geometry and automatically generate useful dimensions without requiring the user to select individual edges or manually position each dimension.

## 8.1 Expected workflow

1. User selects the entire drawing using Select All, or selects a particular region.
2. User invokes Quick Dimension from the toolbar or command line.
3. The application analyses the selected geometry.
4. It identifies meaningful dimensions and geometric relationships.
5. It calculates dimension positions and annotation spacing.
6. It generates the dimensions automatically.
7. The completed dimensions remain editable and associative where possible.

No individual edge selection, manual dimension placement, or repeated confirmation should be required for the normal automatic workflow.

## 8.2 Quick Dimension operating modes

Provide:

- `QDIM ALL` — analyse the complete drawing.
- `QDIM SELECT` — analyse selected geometry.
- `QDIM REGION` — analyse a defined drawing region, if implemented.

All modes should use the same automatic analysis and layout engine.

## 8.3 Geometric analysis

Identify relevant:

- Overall width and height.
- Horizontal and vertical distances.
- Distances between parallel features.
- Hole diameters.
- Hole centre distances.
- Arc radii.
- Angular relationships.
- Relevant gaps and clearances.
- Repeated geometric patterns.
- Symmetrical features.
- Aligned features.
- Major profile dimensions.

## 8.4 Dimension selection rules

The engine must prefer useful engineering and manufacturing dimensions over exhaustive measurement of every possible point pair.

For example, a rectangular plate with six equally spaced holes should normally receive:

- Overall plate width and height.
- Hole diameter.
- Relevant edge-to-hole distances.
- Hole-to-hole spacing.
- Useful pattern information.

It should not generate every possible pairwise distance between all holes.

Provide an automatic dimension-density setting:

- **Sparse:** essential dimensions only.
- **Balanced:** overall and important subordinate dimensions.
- **Detailed:** most meaningful geometric distances.

Balanced should be the default.

## 8.5 Dimension hierarchy

Generated dimensions should follow a hierarchy:

1. Primary dimensions: overall width, height, and major external measurements.
2. Secondary dimensions: distances between holes, edges, shoulders, and major features.
3. Tertiary dimensions: small offsets, clearances, and local distances.
4. Geometric annotations: diameters, radii, angles, centre marks, and specialised measurements.

The layout engine should optimise the complete annotation arrangement rather than placing every dimension independently.

## 8.6 Dimension layout engine

The engine must:

- Group related dimensions.
- Create suitable dimension chains and baselines.
- Calculate extension-line positions.
- Avoid text overlap.
- Avoid collisions with geometry.
- Avoid overlapping dimension lines.
- Maintain readable spacing.
- Prioritise overall dimensions.
- Relocate annotations when the preferred position is occupied.
- Use external text or arrows when internal placement is unsuitable.

Generated dimensions must be editable after creation.

The engine must distinguish automatically generated dimensions from manually authored dimensions. It must not overwrite or duplicate existing manual annotations without permission.

---

# 9. Adaptive Dimension and Annotation Scaling

Dimension text, arrowheads, extension lines, offsets, and spacing must be configurable and automatically adapted to the drawing's scale and available space.

The system must account for both the drawing's physical dimensions and the current viewport zoom.

## 9.1 Model-aware annotation sizing

Initial annotation sizes should be determined by:

- Drawing units.
- Intended sheet size.
- Plot scale.
- Selected drafting standard.
- Dimension style.
- Overall drawing scale.

Small mechanical components should receive appropriately proportioned annotation sizes. Large industrial drawings may require larger annotation sizes.

The user must be able to choose ISO, ANSI, or custom dimension styles where supported.

## 9.2 Viewport-aware annotation presentation

The renderer must distinguish between:

- Model-space annotation properties.
- Screen-space presentation.

Zooming must not change the actual measured value or corrupt stored dimension geometry.

The renderer must prevent annotations from visually overwhelming the geometry.

Required behaviours:

- Preserve readable dimension text.
- Prevent arrowheads from overwhelming short dimension lines.
- Move arrows outside the extension lines when necessary.
- Move text outside short measurements when necessary.
- Reduce annotation density in crowded areas when appropriate.
- Reposition labels to avoid obscuring important geometry.
- Support temporary annotation decluttering at extreme zoom levels.

## 9.3 Arrow adjustment

Arrow size is determined by the active dimension style, subject to available dimension-line space.

For short measurements:

- Move arrows outside when internal placement is unsuitable.
- Reduce arrow size within configured limits.
- Place text externally when there is insufficient room.

For longer measurements:

- Keep arrows inside where appropriate.
- Centre dimension text when suitable.

Arrowheads must never consume the entire visible dimension line.

## 9.4 Text adjustment

- Use compact placement for short dimensions.
- Centre text on longer dimensions where appropriate.
- Move text outside when internal space is insufficient.
- Use leaders or suitable extension arrangements when required.
- Relocate annotations in crowded regions.

## 9.5 Model-space and viewport rules

| Property | Stored/document behaviour | Viewport behaviour |
|---|---|---|
| Measured distance | Fixed geometric value | Never changes |
| Dimension text value | Derived from geometry and units | Never changes because of zoom |
| Arrow size | Defined by dimension style | Rendered without overwhelming geometry |
| Text height | Defined by dimension style and plot requirements | Displayed proportionally and legibly |
| Extension-line offset | Defined by dimension style | Remains consistent with the style |
| Dimension placement | Stored in model coordinates | May be adjusted by layout rules |
| Annotation density | Controlled by dimensioning rules | May be decluttered temporarily |

The application must not arbitrarily alter the actual dimension geometry simply because the user zooms.

Exported DXF and PDF annotations must retain sizes consistent with the selected drafting standard and intended sheet scale.

---

# 10. Dimensioning Components and Data Requirements

Introduce three dedicated components:

## 10.1 `AutoDimensionEngine`

Responsibilities:

- Analyse selected entities.
- Detect geometric relationships.
- Generate candidate dimensions.
- Identify overall, linear, radial, angular, and ordinate measurements.
- Avoid obvious redundant dimensions.
- Apply the selected dimension-density policy.

## 10.2 `DimensionLayoutEngine`

Responsibilities:

- Group related dimensions.
- Establish dimension chains and baselines.
- Calculate annotation positions.
- Detect collisions.
- Resolve text and geometry overlap.
- Choose internal or external arrow placement.
- Reposition annotations to improve readability.

## 10.3 `AdaptiveAnnotationRenderer`

Responsibilities:

- Render arrowheads.
- Render dimension text.
- Render extension and dimension lines.
- Respect dimension styles and plot scale.
- Adapt presentation to viewport conditions.
- Apply temporary decluttering where required.

Generated dimensions should be associative whenever possible. If underlying geometry changes, the associated dimension values must update.

---

# 11. CAD File Interoperability

DXF must be treated as a primary drawing interchange format.

## 11.1 Required capabilities

- DXF import.
- DXF export.
- Preservation of supported entity types.
- Layer and colour preservation.
- Dimension and annotation preservation.
- Block and attribute handling.
- Unit preservation.
- Supported external-reference handling.
- Correct handling of supported line types and hatch patterns.

## 11.2 Additional export formats

- SVG export.
- PDF technical drawing export.
- Image export for drawing previews.

DWG compatibility must be investigated as a separate integration requirement. Implementing DXF does not automatically provide DWG compatibility.

The application should use an established CAD library or a carefully validated implementation for file interchange rather than assuming that a simple custom writer will provide complete compatibility.

---

# 12. Application Architecture and Technical Decisions

## 12.1 Application layer

- Kotlin.
- Jetpack Compose.
- MVVM with StateFlow.
- Android-native input handling.
- Adaptive layouts.
- Offline-first operation.

## 12.2 CAD geometry layer

Use a dedicated geometry module independent of the user interface.

Responsibilities:

- Geometric entities.
- Coordinate transformations.
- Intersections and measurements.
- Entity modification.
- Geometric validation.
- Selection calculations.
- Spatial indexing for large drawings.

A custom 2D geometry engine is reasonable, provided it is developed with explicit numerical tolerances, robust intersection handling, and automated tests.

## 12.3 Rendering layer

Use Android Canvas or an appropriate OpenGL ES implementation, selected after performance testing.

The rendering layer must remain separate from document storage and geometry operations.

## 12.4 Input and precision layer

Implement a dedicated input subsystem for:

- Relative touchpad.
- Velocity-sensitive joystick.
- Cursor coordinates.
- Movement sensitivity.
- Movement increments.
- Grid Snap.
- Object Snap.
- Orthographic restrictions.
- Numeric coordinate entry.
- Mouse, stylus, and keyboard input.

## 12.5 Drafting and annotation layer

Separate modules should manage:

- Drawing commands.
- Modification commands.
- Layers.
- Dimensions.
- Text and leaders.
- Hatches.
- Blocks.
- Layouts.
- Quick Dimension.
- Annotation placement.

## 12.6 Persistence

Use Room for project metadata, indexing, and application-level information.

Store the actual CAD document in a structured, versioned representation rather than treating the complete parametric document graph as an ordinary database record.

The document format should support:

- Schema versioning.
- Reliable save and recovery.
- Undo/redo transactions.
- Document migration.
- Backup and restoration.
- Preservation of entity identifiers and relationships.

---

# 13. Development Roadmap

Development should proceed in independently testable stages.

## Stage 1 — Core CAD Environment

- Android-native interface.
- Drawing viewport.
- Coordinate system.
- Grid.
- Zoom and pan.
- Virtual cursor controller.
- Velocity-sensitive joystick.
- Numeric coordinate entry.
- Grid snapping.
- Basic geometry creation.
- Undo and redo.
- Drawing units and sheet setup.

**Deliverable:** A usable basic 2D CAD environment.

## Stage 2 — Precision Drafting

- Object snapping.
- Dynamic coordinate input.
- Orthographic restrictions.
- Precision measurement.
- Line and angle restrictions.
- Advanced geometric construction.
- Configurable cursor sensitivity.

**Deliverable:** A precision-oriented drafting environment.

## Stage 3 — CAD Editing

- Modification commands.
- Selection engine.
- Properties inspector.
- Layer management.
- Line types and line weights.
- Object manipulation.
- Blocks and groups.

**Deliverable:** A functional general-purpose 2D CAD editor.

## Stage 4 — Dimensioning and Technical Documentation

- Linear, aligned, angular, radius, diameter, and ordinate dimensions.
- Dimension styles.
- Quick Dimension.
- Automatic dimension analysis.
- Dimension layout optimisation.
- Adaptive annotation rendering.
- Text and leaders.
- Hatching.
- Title blocks.
- Sheet layouts and paper space.

**Deliverable:** A technical drawing environment with automatic and manual dimensioning.

## Stage 5 — Interoperability

- DXF import/export.
- SVG export.
- PDF export.
- Drawing templates.
- Supported external-reference handling.
- File validation and recovery.

**Deliverable:** A practical CAD application capable of exchanging drawings with desktop software.

## Stage 6 — Advanced CAD Capabilities

- Parametric geometric constraints.
- Dimensional constraints.
- Associative dimensions.
- Advanced selection.
- Advanced drafting commands.
- Expanded block and layout support.
- Further desktop CAD feature coverage.

**Deliverable:** An expanded desktop-class 2D CAD environment for Android.

---

# 14. Acceptance Criteria

The application should be tested against explicit behavioural requirements.

## 14.1 Cursor and input

1. Sliding on the relative touchpad moves the CAD cursor without touching the drawing.
2. Lifting and repositioning the finger does not relocate the cursor.
3. Joystick displacement controls cursor velocity.
4. Releasing the joystick stops cursor movement.
5. Fine movements remain controllable.
6. Numeric coordinate entry positions the cursor accurately.
7. Grid and object snapping operate independently.
8. Mouse and stylus input do not interfere with the virtual controller.

## 14.2 Units and sheets

1. A drawing can be created in millimetres, centimetres, metres, inches, or feet.
2. Display-unit conversion preserves physical geometry.
3. Explicit rescaling applies the correct conversion factor.
4. Sheet dimensions can be changed independently of model geometry.
5. Portrait and landscape sheet orientations are supported.
6. Dimension values display the correct units and precision.
7. Exported drawings preserve supported unit and scale information.

## 14.3 Quick Dimension

1. Selecting a rectangular drawing and invoking Quick Dimension generates its overall width and height.
2. A plate with multiple holes receives useful hole diameters and spacing dimensions.
3. Redundant dimensions are avoided.
4. Generated text and arrows do not overlap important geometry.
5. Short dimension lines receive suitable external arrow and text placement.
6. Dimensions remain readable during zooming.
7. Dimension density can be changed.
8. Editing underlying geometry updates associative dimensions.
9. Existing manually authored dimensions are not overwritten without permission.
10. DXF export preserves supported dimension entities and their intended properties.

## 14.4 General CAD operation

1. Drawing commands produce mathematically valid geometry.
2. Modification commands preserve entity relationships where appropriate.
3. Undo and redo restore document state reliably.
4. Large drawings remain responsive.
5. Project saving and recovery are reliable.
6. Orientation changes preserve active drawing state.
7. Supported DXF files can be imported, edited, and exported without avoidable data loss.

---

# 15. Explicitly Excluded Functionality

The following are outside the scope of the current PrecisionCAD Studio plan:

- Photorealistic rendering.
- PBR materials.
- Studio lighting.
- Environmental reflections.
- Carbon-fibre and metallic presentation materials.
- Product presentation rendering.
- Dedicated rendering studios.
- High-fidelity product photography simulation.
- 3D assembly modelling.
- 3D solid modelling as part of the initial product scope.

The CAD viewport exists exclusively for technical drafting and geometric representation.

---

# 16. Final Product Principle

PrecisionCAD Studio should bring desktop CAD drafting capabilities to Android without reproducing the limitations of conventional touchscreen drawing applications.

Its defining characteristics are:

1. Comprehensive 2D CAD functionality.
2. A simple, high-performance technical viewport.
3. A velocity-sensitive virtual cursor controller.
4. Accurate grid and object snapping.
5. Configurable drawing units and sheet dimensions.
6. Automatic Quick Dimension generation.
7. Intelligent dimension placement and adaptive annotation.
8. Portrait and landscape workspace adaptation.
9. Reliable DXF interoperability.
10. Offline-first document management.

The central design principle is straightforward:

**The user should control the drafting process with desktop-like precision, while the application handles coordinate alignment, geometric calculations, dimension generation, annotation placement, and document management automatically wherever possible.**

PrecisionCAD Studio should prioritise mathematical correctness, drafting productivity, interoperability, and responsive interaction over decorative visual complexity.
