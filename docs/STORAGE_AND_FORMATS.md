# Native `.pcad` Persistence, Interchange Formats & Updater Staging

## 1. Native `.pcad` Project Format (`schemaVersion = 2`)

PrecisionCAD Studio uses `.pcad` as its lossless, human-inspectable JSON native project format (`CadPcadPersistence.kt`), distinct from `.dxf` CAD interchange files.

A `.pcad` document preserves:
- `schemaVersion = 2`, `projectId`, `projectName`, `savedAtEpochMs`, and `explicitSavePath`
- `storageUnitName`, `displayUnitName`, `unitChangeModeName`, and `precision` (`linearDecimals`, `angularDecimals`, `trailingZeroSuppression`)
- `workspaceSpaceName`, `sheetSetup`, and `titleBlockMetadata` (`title`, `drawingNumber`, `scaleLabel`, `unitLabel`, `author`, `revision`, `dateString`)
- Complete `layers`, `blocks`, `entities` (with exact IEEE-754 `Double` coordinates and full `DimensionEntity` ownership/tier metadata), `constraints`, `gridSnapConfig` (including `polarSnapPresetAnglesDeg` and `polarSnapCustomAnglesDeg`), `controllerConfig`, and `quickDimensionConfig`.

---

## 2. Two Distinct Project Save Operations

### A. Automatic Quick Save (App-Private Crash-Safe Storage)
- Writes to `<context.filesDir>/pcad_autosave/quick_save_working.pcad` using an atomic `.tmp` -> `.bak` -> rename swap protocol.

### B. Explicit Save (`Documents/PrecisionCAD/<basename>.pcad`)
- Writes directly to `Documents/PrecisionCAD/<basename>.pcad` (via `MediaStore.Files` on Android 10+ and direct directory fallback).
- Synchronizes the canonical basename (`Geo.pcad` -> `TITLE: Geo` in title block and `Geo.pdf` on plotted PDF export).

---

## 3. In-App Updater Staging Storage (Separate from Project Storage)

- Downloaded release APKs are staged exclusively under app-private internal storage at `<context.filesDir>/updates/` (`.part` temporary file during HTTPS streaming, promoted to `PrecisionCAD-Studio-v<versionName>-b<versionCode>-release.apk` only after passing all 10 `CadApkVerifier` checks).
- Retaining or cleaning up staged update APKs never touches `<context.filesDir>/pcad_autosave/`, the Room project database (`precision_cad_studio.db`), SharedPreferences, or `Documents/PrecisionCAD/`.
