# Geometry, Invariant Formatting, Zoom-Adaptive Grid Snap & Polar Tracking

## 1. Locale-Independent Engineering Formatting (`CadNumberFormatter`)

- All CAD coordinate readouts, dimension values, command-line diagnostics, and serialized numbers use `CadNumberFormatter` (`Locale.ROOT`).
- Guarantees `.` as the decimal separator and ASCII digits across all system locales (`en-US`, `de-DE`, `bn-IN`, `tr-TR`), preventing comma-decimal ambiguity with `X,Y` coordinate entry.

---

## 2. Zoom-Adaptive Grid Snap (`CadSnapAndCoordEngine`)

- **Preserved User Base Interval (`S_base`)**: `gridSnapSpacing` remains the user-configured nominal interval in `GridAndSnapConfig`.
- **Zoom Ratio & Quantized Ladder (`computeEffectiveGridSnapInterval`)**:
  - Given `referencePixelsPerModelUnit` (`P_ref`, captured when a drawing is fitted/initialized or when base grid spacing changes) and current `pixelsPerModelUnit` (`P_cur`), defines `zoomRatio = P_cur / P_ref` and `rawStep = S_base / zoomRatio`.
  - Quantizes `rawStep` to the nearest engineering `1–2–2.5–5 × 10^n` step within `[1e-4, 1e6]`, applying a **12% hysteresis band** around step transitions so minor pinch-zoom jitter does not oscillate the active grid step.
- **Safe Nearest-Grid Rounding & Viewport Guards (`applyZoomAdaptiveGridSnap`)**:
  - Snaps axes via `round(raw / step) * step` with a `Long` index range check (`abs(raw / step) <= 1e12`).
  - **Screen-Space Jump Guard**: If snapping an axis would jump the cursor by more than `min(96px, 0.35 * usableSpanPx)` on screen, or push a visible cursor outside `UsableViewportRect`, the raw coordinate on that axis is retained.
  - **ORTHO Independence**: When `orthoEnabled` is active with a reference point, the dominant axis is locked to the reference point and only the free axis snaps to the effective grid interval.

---

## 3. Strictly Separated Visual Polar Tracking vs. PolarSnap

- **Visual Polar Tracking (`polarTrackingEnabled`)**:
  - Evaluates whole-number bearings `0°..359°` plus user-configured custom fractional visual bearings (`polarAdditionalAnglesDeg`, e.g., `22.5°`).
  - Uses a narrow angular acquisition window (`VISUAL_POLAR_ACQUIRE_TOLERANCE_DEG = 0.35°`) and release hysteresis (`VISUAL_POLAR_RELEASE_TOLERANCE_DEG = 0.55°`).
  - Strictly informational: displays `R`, Cursor Bearing `θ`, and Track Bearing `θₚ` (when acquired), and never moves or projects the cursor.
- **PolarSnap (`polarSnapEnabled`)**:
  - Uses an independent multi-select preset collection (`polarSnapPresetAnglesDeg`, default `45°` and `90°`, selectable across `15°`, `30°`, `45°`, `60°`, `90°`) plus custom snap angles (`polarSnapCustomAnglesDeg`, default `126°`), built via `buildPolarSnapAngles(snapConfig)` without mixing in `polarAdditionalAnglesDeg`.
  - Projects the candidate point onto the nearest enabled PolarSnap ray within `polarAcquisitionToleranceDeg` only when `polarSnapEnabled == true` and neither OSNAP nor ORTHO overrides.
