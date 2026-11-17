# PrecisionCAD Studio: app-specific file-import implementation plan

Reviewed 16 November 2026. Scope: repair PrecisionCAD Studio so Lumina Gallery can open a real CAD document in it. This agent works only in the `AnimeXplayXD/PrecisionCAD-Studio` repository. Lumina's sender changes are separately specified in `Lumina_Gallery_App_Implementation_Plan.md`.

This is a source review and implementation specification; no installed-app test or code change has been performed. Telegram, gallery animations and video playback are outside this repository's task.

## 1. Confirmed receiver gap and reviewed source

The uploaded Lumina source hard-codes `com.precisioncad.studio` in `domain/Models.kt:637–665`. Its manifest already declares that package under `<queries>` and a correctly scoped FileProvider. `PrecisionCadHandoff` (`media/SensorAndCadEngine.kt:1186 onward`) creates a read-granted content URI and sends package-targeted `ACTION_VIEW`, first with the mapped MIME and then `application/octet-stream`. It only calls `startActivity` if a handler resolves.

I also read the current default-branch files in `AnimeXplayXD/PrecisionCAD-Studio` on GitHub:

- `app/build.gradle.kts`: `applicationId="com.precisioncad.studio"`, matching Lumina. Observed blob SHA `9c11cb700b08d36b16b4779789931b5b47a4cc4c`.
- `app/src/main/AndroidManifest.xml`: MainActivity is exported but has only MAIN/LAUNCHER; no ACTION_VIEW CAD import filter. Observed blob SHA `14bc63659cb9cfa824c1e0731a0c2211e5359c1c`.
- `app/src/main/java/com/precisioncad/studio/MainActivity.kt`: opens `CadWorkspaceScreen()` and manages immersive mode, but does not process `intent.data` or `onNewIntent`. Observed blob SHA `91f0dcfd57c21903dcae081f2351d3526b7f96b1`.

These are blob identities, not a claim about the installed APK's commit/version. Against this source, package installation succeeds but Lumina's ACTION_VIEW resolution has no matching receiver. Simply launching PrecisionCAD's main screen would still not import the file because incoming data is ignored. This is a concrete cross-project contract gap, not an application-name mismatch. Verify the installed build uses this manifest before attributing every device failure to it.

## 2. File-import contract

### Shared integration contract

| Field | Agreed behavior |
| --- | --- |
| Target app | `com.precisioncad.studio` (verify installed application ID/build) |
| Action | `android.intent.action.VIEW` |
| Receiver filter | Exported import Activity with `android.intent.category.DEFAULT` and supported MIME/content-URI filters |
| Data | Readable `content://` URI in Intent data, also supplied in ClipData |
| Grant | `FLAG_GRANT_READ_URI_PERMISSION`; no write access required |
| MIME | Lumina currently maps STEP/STP → `application/step`, IGES/IGS → `model/iges`, DWG → `image/vnd.dwg`, DXF → `image/vnd.dxf`, STL → `model/stl`, OBJ → `model/obj`, GLB → `model/gltf-binary`, GLTF → `model/gltf+json`. Advertise only formats PrecisionCAD really imports; use octet-stream fallback only when agreed and validated. |
| File lifetime | Sender retains staging bytes until import completion or a conservative cleanup policy; receiver copies into project storage for durable access |
| Success | Launch acceptance and successful model import are distinct; do not report import success from `startActivity` alone |
| Optional extensions | A versioned result/acknowledgement protocol must be implemented by both apps before relying on it |

Neither agent should silently change this contract. Coordinate any MIME, URI lifetime, result or version changes with the other app's plan. Do not broaden file permissions or filters merely to make resolution succeed.


## 3. Receiver implementation

1. Add an exported file-import entry Activity or extend MainActivity intentionally. Declare ACTION_VIEW plus CATEGORY_DEFAULT and the supported CAD MIME/content-URI combinations. Only advertise formats the importer actually supports. Keep the existing launcher filter separate. ACTION_SEND is optional and must be implemented explicitly if offered; it is not required for Lumina's current ACTION_VIEW flow.
2. Handle incoming URI/MIME in cold-launch `onCreate` and warm-launch `onNewIntent` when the selected launch mode/flags reuse an Activity. Update stored intent as appropriate and dispatch a typed import request to the workspace/ViewModel. Also handle a newly created Activity correctly; don't depend on one launch mode.
3. Read `content://` using ContentResolver, not `File(uri.path)`. Query safe display name/size, validate format/content, and do heavy copying/parsing off-main. Never assume a temporary FileProvider grant is persistable. If the workspace needs durable access after the grant ends, copy into controlled project storage while access is valid.
4. Expose loading/import progress and clear unsupported/corrupt/access-denied errors. Do not overwrite the user's active project silently; offer the established new-project/import decision when needed. Preserve the request across workspace initialization and consume it once; recomposition must not re-import it.
5. Treat incoming files as untrusted parser input: validate size, resource budgets and names, avoid path traversal, release streams and cancel safely. Narrow intent filters rather than relying on an unrestricted `*/*` handler.

### Connect the incoming request to the real CAD importer

Locate the existing workspace ViewModel, document parser/importer and project persistence APIs before choosing exact integration points. Only the manifest, application configuration and MainActivity were inspected in the receiver audit; do not invent existing importer symbols or assume every listed format is supported.

Define a typed incoming document request (request ID, URI, MIME and safe display metadata), an observable import state (pending, reading, importing, ready, cancelled, failed), and an adapter into the established importer. Route both cold and warm starts to the same adapter. Preserve requests until the workspace is initialized, and record consumption so recreation/recomposition cannot import twice. A new incoming request while another import runs must have an explicit queue/replace policy; never silently overwrite an unsaved project.

For a temporary content grant, stage bytes into a unique app-private project/import location if needed. Use cancellation-aware background I/O, enforce resource/free-space limits, and validate imported content before committing the project. Keep temporary data cleanup separate from a successful durable project. Surface permission, unsupported format, malformed file and importer failures distinctly.

Retain normal launcher behavior when no file is provided. Do not automatically import sample geometry and report the incoming file as successful. Maintain the established rendering and editing workflows after real import succeeds.

## 4. File-by-file changes

| Project/file | Work |
| --- | --- |
| PrecisionCAD Manifest | Supported exported ACTION_VIEW import handler and DEFAULT filter. |
| PrecisionCAD `MainActivity.kt` or new `CadImportActivity.kt` | Dispatch incoming file requests on cold/warm start. |
| PrecisionCAD new `cad/import/CadIncomingDocumentRouter.kt` and workspace ViewModel | Validate/read URI, stage durable project input, import once and surface progress/errors. Exact adapter to existing importer must be located in that repository. |
| Existing workspace ViewModel and import adapter (locate in repository) | Queue/consume incoming request, invoke actual parser, preserve unsaved work, expose progress/errors and commit a real project. |
| `app/src/test/.../CadIncomingDocumentRouterTest.kt` (proposed) | Validate action/URI/type routing, duplicate requests, cancellation and error classification. |
| `app/src/androidTest/.../ExternalCadImportTest.kt` (proposed) | Verify exported filter resolution, real temporary read grants, cold/warm start, valid import and Back navigation. |
| Documentation/release notes | Describe import-capable release and supported MIME types; keep application ID and production signing identity stable. |

No change to application ID is required by the reviewed source. Keep `app/build.gradle.kts` signing settings intact. Add a separate import Activity only if it simplifies lifecycle and routing; an extended MainActivity is also valid when its cold/warm behavior is correct.

## 5. Test and acceptance matrix

Test both apps installed on one device/profile; package presence alone is not acceptance. Test cold receiver and already-open receiver with unsaved work, real valid STEP/STP and IGES plus each actually supported format, correct MIME and agreed fallback, revoked/expired URI grant, corrupt/empty/large file, repeated taps and same-named different files. Verify readable bytes/hash at the receiver and the imported model, not just that its window opens. Reuse small valid CAD fixtures; a generated catalog metadata stub is not proof that a real model imports.

Existing Lumina tests cover MIME mapping and the app-absent path, not a successful two-app import. Add a test receiver fixture for grant/Intent tests plus end-to-end tests using the real PrecisionCAD build. A shell-launched URI test alone may lack the real sender's temporary grant and is insufficient.

Release the compatible receiver and sender changes together or document the minimum import-capable receiver build. Check the actual installed APK manifest/package/version on-device; GitHub default-branch source may differ. Do not request a reinstall as the first fix when the installed app lacks the required receiver implementation.

Receiver-specific assertions:

- Its installed manifest resolves the agreed ACTION_VIEW/DEFAULT/content MIME combination.
- A sender fixture using FileProvider and a temporary read grant delivers the exact test bytes. Validate hash and actual model import, not merely Activity launch.
- Both cold launch and warm launch import once and preserve unsaved work decisions.
- Open a second document while the app is running; ensure the request isn't lost or reapplied on recomposition.
- Rotation, backgrounding and process recreation retain/recover imports without depending on an expired URI grant; if recovery is impossible, show a retryable error rather than success.
- Invalid MIME/content, missing grants, empty/corrupt files and insufficient storage fail cleanly and leave the previous project intact.
- Return navigation works with the real Lumina sender. Receiver tests alone cannot validate Lumina's file cleanup/grant lifetime.

## 6. Implementation sequence

1. Confirm the installed/application package and existing parser capabilities; add a failing receiver integration test.
2. Declare the exported import filter and implement typed cold/warm Intent routing.
3. Wire secure background URI reading and one-time request consumption into the real workspace importer.
4. Add progress/error states, durable staging and unsaved-project safeguards.
5. Run instrumentation with a sender fixture and then the actual updated Lumina app. Document the minimum import-capable receiver build.

Do not publish a release as a side effect of implementing this plan. Return reviewed code changes and real test outcomes; source-level intent resolution is not proof that a CAD model opens successfully on the user's installed build.

## 7. Copy-ready agent handoff

> Work only in PrecisionCAD Studio. Add the missing external CAD document receiving contract: supported ACTION_VIEW/DEFAULT/content-URI filters, cold/warm Intent processing, ContentResolver-based reading and one-time import into the actual workspace. Preserve unsaved projects, application ID and signing. Validate permissions, file content and resource limits; expose clear errors and import progress. Do not replace a real import with launching the home screen or sample geometry. Coordinate the shared MIME/grant/lifetime contract with the separate Lumina sender plan, and verify the two apps end-to-end. Report touched files, tests, supported formats and limitations.

## 8. References

- Receiver manifest reviewed: https://github.com/AnimeXplayXD/PrecisionCAD-Studio/blob/main/app/src/main/AndroidManifest.xml
- Receiver entry point reviewed: https://github.com/AnimeXplayXD/PrecisionCAD-Studio/blob/main/app/src/main/java/com/precisioncad/studio/MainActivity.kt
- Receiver application ID: https://github.com/AnimeXplayXD/PrecisionCAD-Studio/blob/main/app/build.gradle.kts
- Android intent resolution: https://developer.android.com/guide/components/intents-filters
- FileProvider URI grants: https://developer.android.com/reference/androidx/core/content/FileProvider
- Secure file handoff: https://developer.android.com/training/secure-file-sharing/share-file
