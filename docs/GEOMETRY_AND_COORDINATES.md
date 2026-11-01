# Geometry, Coordinate Systems, OSNAP & Polar Tracking

## 1. Exact Double-Precision Coordinate Spaces

PrecisionCAD Studio maintains a strict distinction between three coordinate spaces:

1. **Model Space (`Point2D` in `storageUnit`)**:
   - Every geometric entity is stored in exact IEEE-754 `Double` coordinates in the project's `storageUnit`.
   - Switching `displayUnit` under **Option A (`CONVERT_DISPLAY_UNITS`)** scales displayed values without mutating stored coordinates.
   - Switching `displayUnit` under **Option B (`REINTERPRET_UNITS`)** keeps numeric coordinate values unchanged while updating the unit symbol.
2. **Display / Working Space (`displayUnit`)**:
   - Used in the Controller Coordinate HUD, Direct Coordinate Input Window, command-line coordinate parser, and dimension text formatting.
3. **Screen Space (`Offset` in pixels)**:
   - Derived exclusively during rendering and hit-testing via `ViewportTransform.modelToScreen` and `ViewportTransform.screenToModel`.

---

## 2. Bearing Convention & Direct Coordinate Window (`θ (°)`)

- **Engineering Angle Convention**:
  - `+X` (East / Right) = `0°`
  - Counter-clockwise (CCW) positive in `[0°, 360°)` (`+Y = 90°`, `-X = 180°`, `-Y = 270°`).
- **Reference Origin Selection**:
  - When a command has accepted at least one step point (`commandStepPoints.lastOrNull() != null`), radial distance `R` and bearing `θ (°)` are measured relative to the active reference point `(x₀, y₀)`.
  - When no command reference point is active, `R` and `θ (°)` are measured relative to the drawing origin `(0, 0)`.
- **Deterministic Synchronization in `DirectCoordinateInputDialog`**:
  - Editing `X` or `Y` (`EditSource.CARTESIAN_XY`) recalculates `R = hypot(dx, dy)` and `θ = normalizeDegrees0To360(atan2(dy, dx))`.
  - Editing `θ (°)` (`EditSource.BEARING_THETA`) preserves the current radial distance `R` (defaulting to `1.0` if `R < 1e-9`) and updates:
    - `X = x₀ + R * cos(rad(θ))`
    - `Y = y₀ + R * sin(rad(θ))`

---

## 3. Separated Visual Polar Tracking vs. PolarSnap

- **Visual Polar Tracking (`polarTrackingEnabled`)**:
  - Evaluates all whole-number bearings `0°..359°` (`wholeNumberVisualBearingsDeg`) plus user-configured custom fractional angles (`customPolarAnglesDeg`, e.g., `22.5°`, `126.5°`).
  - Displays a guide ray and HUD readout (`R`, Cursor Bearing `θ`, Track Bearing `θₚ`) when the cursor is within `polarSnapToleranceDeg`, without constraining cursor movement.
- **PolarSnap (`polarSnapEnabled`)**:
  - Uses an independent multi-select preset collection (`polarSnapPresetAnglesDeg`, default `45°` and `90°`, selectable across `15°`, `30°`, `45°`, `60°`, `90°`) plus custom snap angles (`polarSnapCustomAnglesDeg`, default `126°`).
  - Projects the cursor onto the nearest enabled PolarSnap bearing ray at exact radial distance `R` only when `polarSnapEnabled == true` and Ortho is not overriding.
