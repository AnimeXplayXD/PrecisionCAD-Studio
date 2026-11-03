# Interactive CAD Commands, Previews & Temporary Polar Guides

## 1. Command State Machine

All drawing, modification, annotation, and inquiry commands are driven by `CadWorkspaceViewModel` through `CadWorkspaceUiState`:

- `activeCommand: CadCommandId`: Current command (`SELECT` when idle).
- `commandStepPoints: List<Point2D>`: Accepted model-space reference points for the current command session.
- `commandSubOption: String`: Active geometric mode or sub-option (e.g., `Sharp` / `Fillet` / `Chamfer` for `RECTANGLE`, `Radius` / `Diameter` / `2P` / `3P` / `TTR` for `CIRCLE`, `3P` / `SCE` for `ARC`, `Line` / `Arc` / `Close` for `POLYLINE`, `Inscribed` / `Circumscribed` for `POLYGON`).
- `commandParameterValue: Double`: Active numeric parameter (`VAL=`) in working units (or side count for `POLYGON`).

---

## 2. Shared Live-Preview Preservation Invariant

Changing a command sub-option (`setCommandSubOption`) or numeric parameter (`setCommandParameterValue`) while a command is in progress:

1. **Never clears `commandStepPoints`** (unless the sub-option is an explicit terminal action such as `POLYLINE -> Close` with `>= 2` vertices or `SELECT -> SelectAll / Clear / Previous`).
2. **Never resets or pans/zooms `ViewportTransform`**.
3. **Never commits an entity prematurely**.
4. **Immediately recomputes temporary preview geometry (`computePreviewEntities()`)** from the retained `commandStepPoints`, current `snappedCursorModelPos`, updated `commandSubOption`, and updated `commandParameterValue`.

---

## 3. Temporary Polar Guide Rendering Contract

- Temporary Visual Polar Tracking guides (`R`, Cursor Bearing `θ`, Track Bearing `θₚ`, dashed guide ray, and angular arc) are rendered exclusively in the transient HUD overlay pass of `CadViewportCanvas`.
- The blue visual tracking guide ray, angular arc, and `θₚ` label are drawn **only** when `polarTrackingEnabled == true` and `PolarTrackingPreviewState.isPolarGuideAcquired == true` (acquired within `0.35°`, released beyond `0.55°`).
- When the cursor moves away from an acquired visual bearing, the stale guide disappears immediately.
- Temporary polar guides and snap markers never enter `CadWorkspaceUiState.entities`, bounding-box extents, undo/redo history, `.pcad` documents, or DXF/PDF/SVG exports.
