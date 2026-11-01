# Interactive CAD Commands & Shared Live-Preview Preservation

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

### Supported Live-Preview Commands

| Command | Retained Step Points | Live Sub-Option / Parameter Preview |
| :--- | :--- | :--- |
| `RECTANGLE` | First corner `p1` | Switches live between `Sharp`, `Fillet` (corner arcs of radius `VAL`), `Chamfer` (beveled corners of distance `VAL`), and `Square` |
| `CIRCLE` | Center or `2P`/`3P` points | Switches live between `Radius`, `Diameter`, `2P`, `3P`, and `TTR` |
| `ARC` | Picked points (`p1`, `p2`) | Switches live between `3P` (three-point circumscribed arc) and `SCE` (Start-Center-End arc) |
| `POLYLINE` | Accepted vertices `p1..pn` | Rubber-band segment updates live between straight `Line` and bulged `Arc` (`bulge = 0.45`) |
| `POLYGON` | Center `p1` | Recomputes regular `N`-gon preview when switching `Inscribed` / `Circumscribed` or changing `VAL` (`3..64` sides) |
| `LINE`, `RAY`, `XLINE`, `SPLINE` | Accepted points | Live rubber-band / infinite construction line / spline preview |
| `ELLIPSE`, `DONUT` | Center & axis points | Live ellipse / concentric ring preview using `VAL` |
| `OFFSET`, `FILLET`, `CHAMFER` | Selected/hovered target | Live offset curve or corner fillet/chamfer preview using `VAL` |
