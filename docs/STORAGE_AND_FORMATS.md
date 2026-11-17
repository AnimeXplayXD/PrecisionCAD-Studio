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
- Flushed automatically after workspace edits (debounced) and immediately whenever the user navigates from the active Workspace to the Home screen (`openHomeScreen()`), without clearing `isDocumentDirty` (`documentRevision != lastExplicitSaveRevision`).

### B. Explicit Save (`Documents/PrecisionCAD/<basename>.pcad`)
- Writes directly to `Documents/PrecisionCAD/<basename>.pcad` (via `MediaStore.Files` on Android 10+ and direct directory fallback) and updates `lastExplicitSaveRevision = documentRevision` so `isDocumentDirty` becomes `false`.
- Synchronizes the canonical basename (`Geo.pcad` -> `TITLE: Geo` in title block and `Geo.pdf` on plotted PDF export).

---

## 3. In-App Updater Staging Storage (Separate from Project Storage)

- Downloaded release APKs are staged exclusively under app-private internal storage at `<context.filesDir>/updates/` (`.part` temporary file during HTTPS streaming, promoted to `PrecisionCAD-Studio-v<versionName>-b<versionCode>-release.apk` only after passing all 10 `CadApkVerifier` checks).
- Retaining or cleaning up staged update APKs never touches `<context.filesDir>/pcad_autosave/`, the Room project database (`precision_cad_studio.db`), SharedPreferences, or `Documents/PrecisionCAD/`.

---

## 4. External `ACTION_VIEW` CAD Import Contract (1.18.4 / Build 32+)

PrecisionCAD Studio (`.MainActivity`, `launchMode="singleTop"`) accepts external CAD documents via Android `Intent.ACTION_VIEW` (`CATEGORY_DEFAULT` / `CATEGORY_BROWSABLE`) with `content://` or `file://` URIs and `FLAG_GRANT_READ_URI_PERMISSION`.

### Supported Formats and Intent Filters
- **2D ASCII DXF (`.dxf`):** `image/vnd.dxf`, `application/dxf`, `application/x-dxf`, `drawing/x-dxf`, `image/x-dxf`, `application/x-autocad`, and extension fallback (`*.dxf` / `*.DXF`).
- **Native `.pcad` (`schemaVersion` 1 & 2):** `application/vnd.precisioncad.pcad`, `application/x-pcad`, and extension fallback (`*.pcad` / `*.PCAD`).
- **Validated Generic Binary Fallback (`application/octet-stream`):** Accepted for callers whose `FileProvider` does not advertise CAD-specific MIME types, then strictly validated inside `CadIncomingDocumentRouter` before parsing.
- **Explicitly Unsupported Formats (Not Advertised):** Binary DXF, AutoCAD `.dwg` / `.dwf`, `.step` / `.stp`, `.iges` / `.igs`, `.stl`, `.obj`, `.glb`, `.gltf`, and `*/*`.

### Security, Validation, and Unsaved Session Protection
- Streams bytes via `ContentResolver.openInputStream` into `<context.filesDir>/incoming_imports/` while checking `OpenableColumns`, enforcing a `25 MiB` import cap and `4 MiB` free-space margin (`StatFs`), sanitizing untrusted display names against path traversal, computing an incremental SHA-256 digest, and rejecting empty, binary, 3D, or malformed files with explicit `CadIncomingImportState.Failed` dialogs.
- If the active workspace session has unsaved modifications (`isDocumentDirty == true`), the incoming document remains staged safely in app-private storage while `UnsavedDrawingConfirmationDialog` lets the user choose **Save and Continue**, **Discard and Continue**, or **Stay Here**, surviving screen rotation and temporary external URI grant expiration.

