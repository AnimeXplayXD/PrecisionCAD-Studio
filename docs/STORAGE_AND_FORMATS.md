# Native `.pcad` Persistence & DXF / PDF / SVG Formats

## 1. Native `.pcad` Project Format (`schemaVersion = 2`)

PrecisionCAD Studio uses `.pcad` as its lossless, human-inspectable JSON native project format (`CadPcadPersistence.kt`), distinct from `.dxf` CAD interchange files.

A `.pcad` document preserves:
- `schemaVersion = 2`, `projectId`, `projectName`, `savedAtEpochMs`, and `explicitSavePath`
- `storageUnitName`, `displayUnitName`, `unitChangeModeName`, and `precision` (`linearDecimals`, `angularDecimals`, `trailingZeroSuppression`)
- `workspaceSpaceName`, `sheetSetup`, and `titleBlockMetadata` (`title`, `drawingNumber`, `scaleLabel`, `unitLabel`, `author`, `revision`, `dateString`)
- Complete `layers`, `blocks`, `entities` (with exact IEEE-754 `Double` coordinates and full `DimensionEntity` ownership/tier metadata), `constraints`, `gridSnapConfig` (including `polarSnapPresetAnglesDeg` and `polarSnapCustomAnglesDeg`), `controllerConfig`, and `quickDimensionConfig`.

---

## 2. Two Distinct Save Operations

### A. Automatic Quick Save (App-Private Crash-Safe Storage)
- Writes to `<context.filesDir>/pcad_autosave/quick_save_working.pcad`.
- Uses an atomic write-and-swap protocol:
  1. Serializes JSON to `quick_save_working.pcad.tmp` and flushes (`fd.sync()`).
  2. Copies the previous valid `quick_save_working.pcad` to `quick_save_working.pcad.bak`.
  3. Atomically renames `.tmp` to `quick_save_working.pcad`.
- Triggered automatically on debounced drawing mutations so work is never lost on unexpected process termination.

### B. Explicit Save (`Documents/PrecisionCAD/<basename>.pcad`)
- Writes directly to `Documents/PrecisionCAD/<basename>.pcad` (via `MediaStore.Files` with `RELATIVE_PATH = "Documents/PrecisionCAD"` on Android 10+ and direct directory fallback).
- Retains the resolved URI/file handle (`lastExplicitSaveUri`) so subsequent explicit saves update the same `.pcad` file cleanly without prompting a folder picker.

---

## 3. Authoritative Project Basename & Plotted PDF Title Rule

The saved project basename (`CadPcadPersistence.canonicalBasename(name)`) is the single source of truth across `.pcad` persistence, title-block rendering, and plotted PDF export:
- Saving project `Geo` writes `Documents/PrecisionCAD/Geo.pcad`.
- Exporting a plotted PDF from `Geo.pcad` generates `Geo.pdf` and renders `TITLE: Geo` inside the sheet title block (`CadDxfInterchange.exportToPdfFile`).
