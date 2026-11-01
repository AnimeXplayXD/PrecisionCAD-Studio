# PrecisionCAD Studio — Version 17 Master Instructions

### Revised v17 delta-only specification — 1 November 2026

**Purpose:** Define only the incremental work approved for Version 17. Version 16 has already been implemented and is the working application baseline. Its completed implementation requirements are deliberately **not reproduced as Version 17 tasks** in this document.

**Application:** PrecisionCAD Studio  
**Product scope:** Android-native, strictly 2D CAD  
**Primary stack:** Kotlin, Jetpack Compose, the existing geometry/document model, and the existing rendering and command systems.

---

# 1. Version 17 scope, authority and source baseline

## 1.1 Delta-only authority

This file is the Version 17 implementation brief. It contains only new or amended requirements approved after the Version 16 implementation.

- Treat Version 16 as the completed product baseline. Do not copy its completed task list into this file and do not reimplement those features merely because they existed in earlier briefs.
- Preserve the working Version 16 behaviour unless a specific Version 17 section below explicitly changes it.
- A regression in an already completed Version 16 feature is not automatically permission to redesign or rebuild that feature. Report the regression, identify its cause and propose the smallest correction. Implement only the correction authorised by the user or clearly required by a Version 17 change below.
- Do not recreate the geometry kernel, document model, command parser, renderer, controller movement engine, dimension engine or file exporters. Extend the existing authoritative implementation.
- Before editing, compare the actual checkout and current branch with the source archive. Record the actual commit and identify local changes. Do not assume that an uploaded ZIP represents remote `HEAD`.
- This is an implementation instruction, not proof that source code has been modified. Report actual edits, build outcomes and test evidence separately.

## 1.2 Source snapshot observations

The supplied source snapshot `PrecisionCAD-Studio-main.zip` was inspected for this refinement. The archive metadata is not proof of a remote commit.

Relevant source observations from that snapshot:

- `app/src/main/java/com/example/ui/components/CadDialogsAndPanels.kt` contains `DirectCoordinateInputDialog()` and `ControllerAndSnapSettingsDialog()`.
- `DirectCoordinateInputDialog()` already provides Cartesian X/Y input, absolute/relative Cartesian selection, a Polar/Expression mode and use of the existing `CadSnapAndCoordEngine.parseCoordinateInput()` parser. Its expression examples include `50<45`, `@30<90`, `@25,-10` and a scalar distance along the current cursor direction.
- `VirtualCursorController.kt` currently shows X and Y in the controller coordinate HUD. Tapping the X/Y readout opens the direct-coordinate dialog.
- The controller currently presents Touchpad and Joystick as adjacent mode choices. The coordinate readout and settings control occupy the same header region.
- `ControllerAndSnapSettingsDialog()` currently creates its vertical scroll state inside the `AlertDialog` text content and updates `enabledOsnaps` locally when an OSNAP mode tile is tapped. The OSNAP choices are rendered in a two-column grid using `OsnapMode.entries.chunked(2)`.
- The supplied screenshot shows the reported OSNAP configuration-window scroll jump and the existing touchpad/joystick controller arrangement.
- The Gradle build snapshot declares `namespace = "com.example"` and `applicationId = "com.aistudio.precisioncad.kxpqzm"`.
- The supplied workflow makes duplicate differently named copies of a single Release APK and a single Debug APK before uploading them.
- The archive does not contain a conventional root `README.md`, a dedicated `docs/` architecture guide or a complete Gradle Wrapper launch-script/JAR set.

These are observations from the uploaded snapshot only. Recheck the live working branch before changing source files.

## 1.3 Change safety

- Keep document geometry in model space and preserve double-precision values.
- Separate committed document state from transient preview, cursor, input and dialog state.
- UI animations, dialog scrolling, card transitions and keyboard changes must not alter the CAD camera, viewport transform or model coordinates.
- Keep settings state authoritative in the existing ViewModel/configuration model; UI card animation must never become a second source of truth.
- Use stable keys and stable state ownership for lists, dialogs and animated controls.
- Add tests alongside each state or interaction change. Do not claim completion from visual appearance alone.

---

# 2. Shared live-preview preservation and contextual panel exit

## 2.1 Scope — every command that already has a live preview

This requirement applies to **every drawing, construction, annotation or modification command that currently displays live preview geometry**. It is not limited to Rectangle.

Examples include, where the existing implementation provides such previews: LINE, RECTANGLE, CIRCLE, ARC, POLYLINE, polygon tools, fillet/chamfer, offset, trim and other multi-step or parameterised commands.

Do not invent preview support for a command simply because it is listed here as an example. Inspect the actual command implementation, identify its current preview contract and apply the shared preservation rule to every command that already has one.

## 2.2 Preserve accepted command state

Keep these concepts distinct:

1. Accepted input points and accepted command operands.
2. The provisional live cursor candidate or next input.
3. The selected command mode and sub-option.
4. Current numeric parameters and geometric settings.
5. Derived temporary preview geometry.

Changing a mode, sub-option, geometric style or numeric parameter must not erase accepted points or operands merely to refresh the preview.

On an option change:

- retain every accepted point and valid operand;
- update only the relevant mode/parameter state;
- invalidate and recalculate temporary preview geometry immediately;
- render the new preview from the retained inputs and the newly selected options;
- keep the preview provisional until the command's ordinary acceptance action;
- do not force the user to pick a previously accepted point again.

If an option makes an accepted operand mathematically incompatible, retain the data where possible, explain the incompatibility through the existing contextual guidance, and request only the additional input genuinely required. Never silently clear the command history.

## 2.3 Preview/commit consistency

- Use the same authoritative geometry rules, coordinate conventions and units for preview and committed geometry.
- Do not create a second approximate geometry engine for previews.
- Preview recalculation must not append committed entities, dimensions, layers or undo records.
- Cancelling a command removes its temporary preview only.
- Accepting a command commits the geometry matching the latest valid preview, not a stale cached version.
- A preview-option change must not move the viewport or mutate unrelated command state.
- State ownership and Compose keys must not discard accepted input during recomposition.

## 2.4 Contextual option-panel exit animation

Any contextual command-option panel that has an entrance transition must also have a deliberate, visible exit transition.

- Dismiss the panel by animating it back towards and behind its invoking/anchor surface.
- Retain the outgoing panel in composition until its exit transition finishes.
- Do not let a parent immediately remove the panel before the exit can be rendered.
- Inspect ancestor clipping and layout constraints so the exit is not clipped or reduced to zero size prematurely.
- Replacing one panel with another must use a coherent keyed transition.
- The transition must not reflow the workspace or change viewport scale, pan, cursor coordinates or controller position.

## 2.5 Acceptance tests

- For every command with an existing live preview, accept its first required point and change every available preview option.
- Confirm accepted points/operands remain intact and the preview recalculates immediately.
- Confirm this for drawing and modification commands, not only Rectangle.
- Confirm changing an option does not itself commit the next point or entity.
- Confirm the final committed geometry matches the latest preview.
- Confirm cancellation removes temporary preview geometry without modifying committed geometry.
- Confirm panels animate out on completion, cancellation, command replacement and contextual dismissal.
- Confirm no viewport or controller transform/state changes during preview or panel transitions.

---

# 3. Final IME layout and command-bar engagement

## 3.1 Separate the keyboard-aware command region from the controller dock

The workspace must retain two separately anchored overlay regions over the stable drawing viewport.

**Upper command region:** active Overview/session guidance, function/action toolbar and persistent command-entry row. This region moves upward as one coordinated group so the command input and its controls remain immediately above the Android keyboard.

**Lower controller region:** touchpad/joystick surface, coordinate HUD, increment controls, settings, PICK PT and controller-side confirm/cancel actions. This region remains anchored at its normal screen-bottom position. It must not receive the IME bottom inset or be lifted/resized when the keyboard appears. The keyboard may cover some or all of this dock; that is intentional.

Do not apply `imePadding()`, an IME offset or a keyboard-height translation to a parent that also contains the controller. Apply keyboard-aware movement only to the upper command region. Preserve the behaviour in both portrait and landscape.

No keyboard transition may change the CAD viewport's measured model-space bounds, camera transform, zoom, pan or cursor model coordinates.

## 3.2 Command bar engagement — final Boolean rule

The Send/Cancel action-card compartment is visible when **either** the command-entry keyboard is visible **or** the command text is non-empty.

`commandBarEngaged = commandImeVisible || commandText.isNotEmpty()`

The command bar returns to its complete idle appearance only when both conditions are false:

`commandBarIdle = !commandImeVisible && commandText.isEmpty()`

Required behaviour:

| Keyboard | Command text | Action cards |
|---|---|---|
| Open | Empty | Visible |
| Open | Non-empty | Visible |
| Closed | Non-empty | Visible |
| Closed | Empty | Hidden; full idle state |

- Tapping the command field opens the keyboard and reveals the cards.
- Closing the keyboard does not erase typed text. If text remains, the cards remain visible.
- Clearing the field while the keyboard remains open does not hide the cards.
- Only a closed keyboard and an empty input return the bar to idle.
- A running CAD command by itself must not keep the command-entry compartment engaged when the keyboard is hidden and the text field is empty. The running command remains active through its existing command/session state and appropriate workspace controls.
- Do not conflate keyboard visibility, field focus, command lifecycle and command text into one persistent `isEngaged` flag.
- Observe IME visibility from the actual window/inset state, not focus alone.

## 3.3 Acceptance tests

Exercise all four rows in the table. Close the keyboard using Android Back, the keyboard dismissal affordance and an outside interaction. Confirm text persistence, action-card state and controller anchoring. Also test active drawing commands: keyboard dismissal must not cancel the operation, but it must not keep the command-entry cards visible when the text is empty.

---

# 4. Native project persistence, explicit device saving and PDF title consistency

## 4.1 Two distinct save operations

### A. Automatic Quick Save — private application storage

- Automatically persist the current native working document inside PrecisionCAD's private application storage as committed document edits occur.
- Do not show a permission prompt, folder picker or Save dialog for automatic Quick Save.
- Use debounced or transaction-aware writes so committed operations are protected without excessive storage activity.
- Use atomic replacement or an equivalent crash-safe strategy. A failed write must not destroy the last valid document.
- Maintain sufficient recovery information to restore the last known-good document after an interrupted write.
- Persist the editable document, not a rendered bitmap or DXF-only surrogate.
- Explain accurately that private app data may be removed when the application is uninstalled.

### B. Explicit Save — fixed user-visible device folder

When the user deliberately invokes Save, write the current native project to:

`Documents/PrecisionCAD/`

- Create the directory automatically when it does not exist.
- The user must not have to browse for, choose or navigate to the folder.
- Use the actual project basename and `.pcad` extension.
- Define a safe overwrite/conflict policy. Never silently create ambiguous duplicate names.
- Save the latest committed document, including entities, layers, dimensions, text, annotations, units, sheet settings and native metadata.
- Retain the URI/ownership reference needed to update a previously created user-visible file.
- The user-visible file must remain accessible outside the app-private directory and survive ordinary uninstall unless the user deletes it.

## 4.2 Android storage API constraints

Do not implement this as an unconditional request for unrestricted read/write access to all shared storage.

- On Android 10/API 29 and later, use an appropriate scoped-storage route for app-created Documents files, such as `MediaStore.Files` with relative path `Documents/PrecisionCAD/`, and retain the file URI/ownership information needed for subsequent updates.
- Do not use a Storage Access Framework folder picker for this fixed app-managed destination; the user explicitly requires automatic directory creation and no folder navigation.
- On Android 9/API 28 and earlier, request legacy external-storage permission only when the user first invokes explicit Save and only if the actual implementation requires it.
- Do not request `MANAGE_EXTERNAL_STORAGE` merely to create/update PrecisionCAD project files.
- Do not claim that current Android versions require the old unrestricted `WRITE_EXTERNAL_STORAGE` permission for this use case.
- Denial of a legacy permission must not disable private Quick Save.
- Verify behaviour across the supported API range and the configured `minSdk`/`targetSdk`.

## 4.3 Versioned native `.pcad` document format

Use `.pcad` as the authoritative editable PrecisionCAD project format for automatic private saving and explicit Documents saving.

Preserve, as supported by the actual document model:

- geometric entities and exact double-precision model coordinates;
- entity properties, layers, visibility, colours, linetypes and ordering;
- manual and generated dimensions, their type, placement and ownership metadata;
- ordinary text, leaders, annotations and supported blocks;
- units, sheet/page setup, drafting standard and project metadata;
- supported constraints/relationships that exist in the application;
- schema version and migration metadata.

Do not serialise transient keyboard state, open dialogs, cursor animation frames or temporary preview geometry as committed drawing data. Validate documents on load. Migrate known older schemas deliberately; reject unsupported future schemas safely rather than silently discarding content.

## 4.4 Format responsibilities

| Format | Responsibility |
|---|---|
| `.pcad` | Full editable native project; source of truth for PrecisionCAD saving. |
| `.dxf` | CAD interchange with supported external CAD applications. |
| `.pdf` | Fixed-page technical sheet for printing, review and sharing. |
| `.svg` | Scalable vector interchange/artwork with supported geometry and annotations. |

Do not disguise DXF as `.pcad` or merely change a file extension.

## 4.5 Home, project catalogue and migration

- Keep the already implemented Version 16 Home/startup behaviour; do not recreate it as Version 17 work.
- Automatic private Quick Save must not generate built-in starter/template projects.
- The saved-project catalogue must distinguish native `.pcad` projects from files merely viewed/imported as DXF, PDF or SVG.
- Importing an external drawing into the editable document model may create a native `.pcad` working copy when the user saves it.
- Migrate any existing internal project records safely, preserving user geometry and metadata. Never delete the projects directory or database wholesale.

## 4.6 PDF basename/title-block rule

The actual saved project basename is authoritative for both the exported PDF filename and the title-block `TITLE:` field.

Example: `Geo.pcad` must export as `Geo.pdf`, and the title block must say `TITLE: Geo`.

- Do not substitute a template title, stale drawing title, hard-coded project name or unrelated metadata title.
- Keep drawing number, revision, units, scale and standard as independent title-block fields.
- Renaming the project changes the basename/title used by subsequent exports.
- If there is no authoritative saved basename, obtain a valid drawing name rather than inventing a saved filename.
- Test the PDF filename and rendered title block together.

## 4.7 Acceptance tests

Test automatic private persistence without permissions; recovery after force-stop/relaunch; first explicit Save to the automatically created Documents folder; legacy permission handling; scoped-storage behaviour; `.pcad` round-trip and migration; conflict/overwrite handling; and `Geo.pcad` → `Geo.pdf` with `TITLE: Geo`.

---

# 5. Quick Dimension and Alignment dialog refinement

## 5.1 Dialog surface and continuous scroll body

Rebuild only the QDIM dialog presentation described here; retain the existing dimension-generation engine, alignment calculations, entity ownership and undo behaviour.

- Use one coherent dark dialog surface and a visually continuous scrolling body.
- Remove the conspicuous inner black rectangle/heavy border that makes the body appear trapped in a second panel.
- Keep separators, spacing and subtle fade edges restrained.
- The body scrolls behind the header/footer docking regions without abrupt content cut-offs.
- Reserve scroll space so the fixed footer never covers the last setting.
- Respect portrait, landscape, safe areas and the available window height.

## 5.2 Scroll-responsive header

The title begins as part of the scrollable content. As the user scrolls down, it transitions smoothly from its in-flow title position into the pinned header at the top of the dialog.

- This is one logical header transitioning between positions, not two independently visible copies.
- Use a stable scroll threshold and animated placement/size/opacity changes.
- Scrolling back to the top returns the title to its original in-flow position.
- No jump, duplicate title or blank title area is permitted.

## 5.3 Scroll-responsive footer

The footer action deck begins at the bottom of the scrollable content. As the user scrolls towards the footer docking threshold, it transitions into a permanently anchored position at the bottom of the dialog.

- Treat the footer as one logical component moving from in-flow to pinned placement.
- The body reserves the necessary bottom inset while the footer is pinned.
- Scrolling back to the top returns the footer to its original in-flow position.
- No content may become inaccessible behind the pinned footer.

## 5.4 Rotary/wheel selectors and delayed explanations

Replace repeated always-visible option cards for Operating Mode, Alignment/Arrangement and Dimension Density Policy with compact single-button rotary/wheel selectors.

- Tapping a selector opens its wheel/pop-up choice interface.
- Preserve all existing valid enum values and meanings. This is a visual/input redesign, not permission to invent new QDIM calculation modes.
- Display the current selection on the button.
- Keep explanatory descriptions hidden by default.
- After the user rests on a selection for approximately two seconds, reveal a small speech-bubble/callout with a subtle pointer to the selected item and a description of that choice.
- Dismiss the callout on selection change, dismissal or loss of relevant focus.
- Do not allow delayed callouts to cover the footer or obscure other selectable choices.

## 5.5 Remove redundant controls

- Remove the separate Quick Alignment ON/OFF switch row; the footer's Enable & Generate action is the activation/generation control.
- Remove the large text-labelled Refresh control and its redundant explanatory segment.
- If a distinct refresh operation remains necessary, expose it as a compact icon-only control. Rotate the icon while the operation is running and return it to rest on completion.
- Do not keep a second independent enabled Boolean in the dialog. The existing authoritative QDIM ViewModel state remains the single source of truth.

## 5.6 Footer two-card action deck

The footer contains two equal-footprint, square or subtly filleted/chamfered cards:

- Green foreground action: **Enable & Generate**.
- Red rear action: **Clear All**.

The footer deck has its own interactions:

1. Tap the foreground card to execute the action currently in front.
2. Swipe down within the deck to bring the rear card forward.
3. A swipe changes card position only; it never executes an action.
4. A short tap on Clear All opens the ordinary destructive confirmation.
5. A deliberate long press on Clear All opens the selective dimension-removal interface below. Long press must not also trigger the short-tap confirmation.

Keep tap, long-press and swipe recognition distinct and lock action dispatch during the card exchange animation. This QDIM deck is an action deck, not a binary setting toggle; its destructive Clear All card remains red.

## 5.7 Long-press Clear All — category-first selective dimension removal

The selection interface is a two-level hierarchy: dimension categories first, then the actual dimension entities within each category.

### Category behaviour

- Every category is checked by default, meaning all dimensions in the category are selected for removal.
- While a category is checked, all of its child dimensions are selected but their controls are greyed out/disabled. Users cannot alter children while the parent category remains checked.
- Unchecking a category unlocks its individual dimension entries.
- When unlocked, child entries initially remain selected. Users uncheck individual entries they wish to preserve.
- Rechecking the category selects all children and locks them again.
- A category with mixed child selection displays an indeterminate/partial state.
- Provide a master Select All action that checks and locks every category.

### Dimension entries

- Display actual dimension entities grouped by their dimension type/alignment family.
- Each individual dimension has a left-side checkbox, a readable measured value and enough geometric context to distinguish repeated or similar dimensions.
- Include an exact count of selected dimension entities.
- Do not show generic category toggles as though they were the individual dimension entities.
- The selection list must support all actual supported dimension types; do not silently omit angular, linear, radius, diameter, ordinate or other implemented types.

### Apply and confirmation

- After selection, show a confirmation describing the exact number of dimension entities to be removed.
- Cancel at either selection or confirmation stage must leave the document unchanged.
- On confirmation, remove only selected dimension entities. Preserve geometry, ordinary text, leaders and non-dimensional annotations.
- Perform removal as one undoable/redoable document transaction.
- Clear stale dimension-render/cache entries for the removed IDs.
- Do not trigger QDIM regeneration merely because the dialog is reopened.

## 5.8 Integrity and viewport constraints

Preserve the existing QDIM engine's authoritative state, regeneration idempotency and manual/generated dimension separation. A refresh must replace the previous generated set rather than append duplicates. Ordinary regeneration must protect manually created dimensions. The selective Clear All action is the explicit operation that may remove both manual and generated dimensions selected by the user.

Dialog opening, scrolling, wheel selection, callout display, card exchange and deletion selection must never pan, zoom, fit, recenter or rebase the CAD viewport.

## 5.9 Acceptance tests

Test header/footer transitions in both directions; continuous scrolling; wheel values; two-second descriptions; icon-only refresh; footer tap/swipe/long-press arbitration; all-selected/locked category state; unlock and per-dimension preservation; indeterminate state; exact deletion count; cancel; commit; undo/redo; manual/generated mixtures; and no duplicate generation.

---

# 6. Cursor Control, Grid and OSNAP configuration

## 6.1 Repair scroll-position jump when selecting Active OSNAP modes

A defect is reported in the Cursor Controller, Grid & OSNAP Configuration dialog: selecting an Active Object Snap (OSNAP) mode causes the entire scrollable content region to jump upward by several positions.

The supplied source snapshot shows:

- `ControllerAndSnapSettingsDialog()` owns local `enabledOsnaps` state;
- the dialog body uses `Column(...verticalScroll(rememberScrollState()))` inside the `AlertDialog` text slot;
- OSNAP entries are created from `OsnapMode.entries.chunked(2)` and each tile changes the local enabled-mode set on click.

The precise runtime cause must be verified on the current checkout. Do not assume the visual symptom is caused by the selection model alone.

### Required correction

- Give the dialog one stable scroll-state owner that survives recomposition of the AlertDialog content. Hoist it out of the text-slot lambda and use a saveable/stable scroll-state strategy appropriate to the dialog lifetime.
- Preserve the exact scroll offset when any OSNAP mode is selected or deselected.
- Keep the dialog and scroll container mounted. A mode selection must update only the selected-mode state and the selected visual treatment.
- Use stable keys/identity for OSNAP rows and entries.
- Do not recreate the scroll state, reset the dialog route, change the dialog key or rebuild the whole dialog when `enabledOsnaps` changes.
- Do not request keyboard focus, trigger `BringIntoViewRequester`, call `scrollTo`/`animateScrollTo`, or shift the content to reveal a selected OSNAP tile on selection.
- Keep selected and unselected tiles the same measured size; visual state changes must not change row height or dialog content geometry.
- Keep OSNAP selections local until the existing Save Configuration action commits them, unless the current product state model explicitly requires immediate persistence. Do not dismiss the dialog on a selection.
- Preserve multiple simultaneous OSNAP selections.

If the jump is ultimately caused by AlertDialog remeasurement or platform window relayout, correct the dialog's sizing/scroll container so its viewport remains stable. Do not mask the problem by scrolling back to a hard-coded offset after every click; the user's current position must be preserved.

### Acceptance tests

- Scroll to the OSNAP section and select an entry near the top, middle and bottom of the grid.
- Repeat selection/deselection for every OSNAP mode.
- Verify the scroll offset remains unchanged within a small pixel tolerance after each click.
- Verify the dialog does not move, resize, reopen or lose other unsaved settings.
- Verify multiple modes can remain selected and Save Configuration persists the selected set.
- Repeat on small/large portrait screens and landscape.

## 6.2 Professional configuration hierarchy

Refine the Cursor Controller, Grid & OSNAP Configuration dialog while retaining its supported settings and calculation semantics.

- Group controller sensitivity/speed, numeric increments, grid spacing, polar tracking, PolarSnap and OSNAP modes into clearly named sections.
- Align labels, controls, values and section spacing consistently.
- Use responsive two-column fields only where labels and values remain readable; otherwise stack them.
- Avoid cropped labels, hidden values, accidental horizontal clipping and controls intersecting the fixed header/footer.
- Keep one continuous, stable scrollable body and a fixed, reachable action area.
- Preserve current settings until Save Configuration; Cancel discards the unsaved dialog edits.
- Do not alter joystick response curves, movement integration or touchpad movement semantics as part of this visual refinement.

---

# 7. Direct Coordinate window — editable polar bearing and larger layout

## 7.1 Current implementation to extend

The inspected `DirectCoordinateInputDialog()` already has:

- Cartesian X and Y fields;
- absolute/relative Cartesian mode;
- a Polar/Expression mode using the existing coordinate parser;
- resolved coordinate preview for parsed expressions;
- validation before submitting the coordinate expression.

Do not discard this working parser or replace it with a second coordinate engine. Extend the current dialog and the existing cursor/coordinate pipeline.

## 7.2 Add a dedicated editable angle/bearing field

Add a clear, directly editable **Angle / Bearing θ (°)** control to the direct-coordinate window. It must represent the actual cursor bearing (θ), not the acquired visual tracking direction (θₚ).

The user must be able to enter a specific angle numerically through the coordinate window just as they can currently enter X and Y. The angle uses degrees and must display its reference direction explicitly.

### Consistent position/bearing semantics

X, Y and θ cannot be treated as three unrelated absolute values, because a point relative to a base point determines one bearing. Define one deterministic last-edited-input rule:

- When the user edits Cartesian X or Y, the resulting candidate coordinate is authoritative; recalculate the radial distance R and bearing θ from the active reference point and update the displayed θ accordingly.
- When the user edits θ directly, preserve the current radial distance R from the active reference point and recalculate X/Y by projecting that same R onto the newly entered bearing. Display the resolved X/Y values as the resulting position.
- If the user edits X/Y after editing θ, the Cartesian coordinate becomes authoritative again and θ is recalculated from it.
- Maintain an explicit edit-source/state field so recomposition cannot create a feedback loop between X/Y and θ.
- If R is zero or there is no meaningful candidate point, do not fabricate a direction; show a neutral/unavailable bearing until a valid position exists.
- Validate finite numeric values and normalise bearings according to the application's established 0°/360° convention.

### Reference direction

- Show the reference point used to compute R and θ.
- When measuring relative to the last accepted segment is enabled, use that segment's direction as the bearing reference and label it accordingly.
- Otherwise use the established absolute drawing reference, normally positive X.
- Do not silently switch the reference direction when the user enters a value.

### Existing Polar/Expression input

- Retain the existing expression parser and syntax (`50<45`, `@30<90`, `@25,-10`, and supported scalar-distance input).
- An expression submission is authoritative for the resolved candidate point; update X, Y, R and θ from the parsed result.
- Do not leave stale X/Y/θ values from a previous mode.
- Keep parser validation and coordinate snapping integration in the existing pipeline.
- Entering or previewing a coordinate must not commit a drawing point until the explicit Move Cursor & Pick/accept action is invoked.

## 7.3 Larger, more polished dialog

Increase the coordinate dialog's usable size so X, Y and θ are clearly presented without crowding.

- Use a responsive width of approximately 92–95% of the available safe window width, with a sensible tablet maximum width.
- Allow a taller content region than the current dialog, bounded by the safe window height; keep buttons reachable and the body scrollable when necessary.
- Use a strong hierarchy: coordinate mode, reference point, primary input fields, resolved R/position preview, advanced expression input, validation and actions.
- Avoid cramped X/Y fields and a narrow angle field. Give θ a full readable numeric input with an explicit degree suffix/label.
- Provide a live preview of resolved X/Y, R and θ as the user edits values.
- Keep the dialog above the keyboard when the IME opens, without moving the controller dock or changing the viewport.
- Preserve Cancel and explicit submission semantics.

## 7.4 Controller coordinate HUD

Extend the existing X/Y coordinate readout that opens this dialog.

- Show X, Y and the current bearing θ in a polished, compact coordinate instrument/readout.
- Include unit notation for X/Y and degrees for θ.
- Ensure the readout can fit narrow portrait layouts without clipping; use responsive layout, compact typography or a deliberate horizontal overflow strategy rather than truncating the values unpredictably.
- Tapping the coordinate readout opens the larger Direct Coordinate window.
- The HUD is read-only; editing occurs in the dialog.
- Keep the settings affordance distinct from the coordinate readout.

## 7.5 Acceptance tests

- Edit X and verify θ updates from the resulting candidate point.
- Edit Y and verify θ updates.
- Edit θ and verify R is preserved while X/Y update.
- Edit X/Y again and verify Cartesian position becomes authoritative.
- Test positive, negative, fractional and wraparound angles (including 0°, 359.9° and 360°).
- Test absolute and relative references and relative-to-last-segment behaviour.
- Test all existing expression syntaxes and verify resolved X/Y/R/θ values.
- Confirm no point is committed until explicit acceptance.
- Confirm validation rejects malformed, non-finite or impossible input without moving the cursor.
- Test portrait, landscape, keyboard-open and keyboard-closed layouts.

---

# 8. Touchpad/Joystick mode selector and controller-dock redesign

## 8.1 Replace adjacent mode tabs with a vertical-peek two-card selector

The current side-by-side Touchpad and Joystick mode choices must become a single two-card mode selector.

- The active mode card is in the foreground.
- The alternate mode card peeks from **above** the foreground card. This vertical stack orientation is specific to Touchpad/Joystick and differs from the side-peek action decks used elsewhere.
- A single tap animates the rear card moving down/out to the foreground while the previous foreground card moves upward/back into the rear position.
- There is no swipe gesture for this selector.
- Lock the selector during the exchange animation to prevent double dispatch.
- Do not use the QDIM deck's swipe-down gesture or command/export deck gesture recognisers here.

### Mode-specific colour identity

- **Touchpad:** restrained PrecisionCAD blue.
- **Joystick:** restrained PrecisionCAD gold.
- Preserve each mode's accent identity when it is the foreground card and as a subdued accent when it is peeking from behind.
- Do not use red/green enabled/disabled semantics for this mutually exclusive mode selector; blue and gold identify two different input modes, not ON/OFF states.

## 8.2 Controller-dock hierarchy and layout refinement

Reorganise the controller dock into a more polished, deliberate structure while preserving the existing controller functions and input behaviour.

Recommended hierarchy:

1. **Controller header:** vertical-peek Touchpad/Joystick card selector, coordinate instrument showing X/Y/θ, and a distinct settings affordance.
2. **Increment strip:** compact, consistently sized step-increment choices, with the active increment clearly highlighted.
3. **Primary control area:** the active mode's large input surface, with point-pick and command-side actions aligned in a distinct secondary cluster.
4. **Dock handle/state:** retain the existing controller expansion/collapse behaviour and clear visual separation from the drawing viewport.

- Make the active touchpad/joystick input surface large enough for precise use.
- Keep PICK PT, accept and cancel actions reachable and visually distinct wherever they exist in the current mode's interaction contract.
- Align edges, spacing, typography, border radii and control sizes. Prevent coordinate values, step labels or action labels from being clipped.
- Use responsive portrait/landscape arrangements. Do not force the portrait layout into landscape.
- Keep the settings control outside the coordinate value hit target so tapping settings does not open the coordinate editor.
- Maintain pointer ownership: controller interaction must not leak into sheet pan/zoom or drawing/edit input.
- Mode changes must not move the camera, change model coordinates or commit a point.

## 8.3 Preserve controller algorithms

This is a presentation and mode-selector interaction change, not a request to replace controller movement algorithms.

- Preserve established relative-touchpad displacement semantics.
- Preserve joystick response curve, frame integration, speed scaling and stop-on-release behaviour.
- Do not introduce fling, acceleration or a new movement curve.
- Do not change PICK PT/accept/cancel command semantics.
- Keep controller position stable when the keyboard appears; the separate IME rule applies.

## 8.4 Acceptance tests

- Verify the alternate mode card peeks from above, not from the side.
- Tap the mode selector repeatedly and verify the incoming card animates down/out to the foreground.
- Verify no swipe action is recognised.
- Verify Touchpad uses blue and Joystick uses gold.
- Verify movement algorithms and point-pick semantics remain unchanged.
- Verify coordinate HUD and settings controls do not overlap or steal one another's taps.
- Verify no touch sequence leaks into viewport navigation or CAD editing.
- Verify the layout on compact portrait, large portrait and landscape screens.

---

# 9. Application-wide binary toggle-card system and polar tracking semantics

## 9.1 One reusable toggle-card component across the application

Replace **every user-facing binary enable/disable toggle** in the application with one reusable two-card state control. This is a global design rule, not a Polar Tracking-specific or configuration-dialog-only rule.

Inventory the whole source tree for binary state controls, including conventional `Switch` controls, explicit ON/OFF controls and independent mode buttons whose semantic purpose is enabling/disabling a feature. Apply the same component consistently to workspace controls, settings dialogs, controller/grid/OSNAP settings and other relevant application surfaces.

### State colours — final rule

- **Enabled:** green foreground card.
- **Disabled:** grey foreground card.
- The opposite state card remains partially visible behind the foreground card.
- When enabled, the grey Disabled card is the rear card.
- When disabled, the green Enabled card is the rear card.
- A single tap animates the two cards exchanging positions and changes the authoritative Boolean state exactly once.
- There is no swipe gesture anywhere on these binary toggle cards.
- The rear card is decorative/non-actionable while covered; tapping the visible foreground control toggles the state.
- Use restrained offset, scale, rotation and opacity consistent with the established PrecisionCAD card language.
- Keep the hit target and semantics stable during animation; lock input while the transition runs.
- Do not use red for the Disabled state. Red remains reserved for destructive actions or explicit danger/cancel semantics.

Use the existing design tokens where suitable: a muted green such as the existing emerald accent for Enabled and a neutral grey/near-black treatment for Disabled. Avoid neon colours or large saturated fills.

## 9.2 Scope and exclusions

The global rule covers actual user-facing Boolean feature states and independent binary ON/OFF choices, including individual OSNAP mode entries where each mode can be enabled or disabled independently.

It does **not** convert every selection control into a binary toggle card. Keep these control types distinct:

- checkboxes used to select individual dimensions in the Clear All selection window;
- master/child category selection and indeterminate states;
- radio groups and mutually exclusive enumerations;
- wheel selectors, numeric fields and sliders;
- custom multi-select angle chips where they represent membership in a set rather than a feature's overall enabled state;
- command Send/Cancel, QDIM Enable/Clear All and Project Hub export-format action decks;
- the Touchpad/Joystick mutually exclusive mode selector, which has its own blue/gold vertical-peek card design in Section 8.

For a group of independent Boolean entries, each entry has its own enabled/disabled card state. The group remains multi-select; converting an OSNAP mode grid must not turn it into a single-choice group.

## 9.3 State ownership and accessibility

- Each card binds to the existing authoritative setting state; do not duplicate the Boolean in animation-local state.
- A tap changes only that setting.
- Rapid taps during animation are safely ignored or coalesced; never dispatch twice.
- Expose accessible label, current state and click action to TalkBack.
- Provide a sufficiently large touch target and readable Enabled/Disabled text/iconography.
- Respect reduced-motion settings where supported while preserving the same state semantics.
- Keep compact variants for dense toolbars and OSNAP grids, without abandoning the two-card visual identity.

## 9.4 PrecisionCAD polar readout

Present the live tracking information with the symbol and explicit engineering label together:

- `R` — **Radial Distance:** distance from the last accepted base point to the raw cursor candidate.
- `θ` — **Cursor Bearing:** actual, unsnapped bearing of the raw cursor candidate from the active reference direction.
- `θₚ` — **Track Bearing:** acquired polar-tracking direction offered by the visual guide system.

Use an instrument-style readout consistent with PrecisionCAD: restrained blue for position/measurement information, muted gold for the acquired guide direction, high-contrast neutral values, current drawing units for R and degrees for θ/θₚ.

- Never label both θ and θₚ merely as “Angle”.
- θ remains the actual cursor bearing, including fractional degrees.
- θₚ is the acquired guide direction and may be an integer or custom fractional angle.
- Show the reference basis (normally +X or previous accepted segment).
- When a valid base point exists but no direction is acquired, R and θ may remain visible; do not fabricate θₚ.
- These cursor HUD values remain read-only. Numeric edits occur in the Direct Coordinate window.

## 9.5 Visual Polar Tracking angle collection

When Visual Polar Tracking is enabled:

- Every whole-number bearing from 0° through 359° is automatically available for visual guide acquisition.
- Do not require a user-selected global 45°/30°/90° increment to make integer bearings visible.
- Allow additional fractional visual bearings such as 22.5°, 15.6° or 126° where supported.
- Normalise angles to the established angular domain, remove duplicates within a defined numeric tolerance and handle 0°/360° wraparound deterministically.
- Use a defined acquisition tolerance with suitable hysteresis/deadband to avoid flickering.
- The all-degree visual collection is informational/directional guidance. Its existence must not itself move, project or constrain the raw cursor candidate.

## 9.6 PolarSnap separate multi-selection collection

PolarSnap must use its own separate collection of user-selected angle presets.

- Allow multiple PolarSnap presets simultaneously; do not limit the user to one selected increment.
- Retain supported custom PolarSnap angles where the configuration model supports them.
- Do not populate PolarSnap with all 360 whole-degree visual bearings.
- Do not derive the PolarSnap selection from the visual guide collection.
- Candidate projection is permitted only when PolarSnap is enabled and a selected PolarSnap direction is acquired under the existing tolerance rules.
- A visual guide that is not in the PolarSnap selection must not constrain the candidate.
- Enabling/disabling Visual Polar Tracking must not silently rewrite PolarSnap's stored angle collection.

## 9.7 Interaction with ORTHO, OSNAP and grid

Keep the mechanisms logically separate:

- GRID — visual grid display;
- GSNAP — grid-interval candidate snapping;
- OSNAP — geometric object-snap acquisition;
- ORTHO — orthogonal directional constraint;
- Visual Polar Tracking — visual angular guides/readout;
- PolarSnap — projection onto the separately selected polar bearings.

Preserve the application's established mutual-exclusion rule between ORTHO and Visual Polar Tracking, where currently specified. State changes must be deterministic and reflected by their own authoritative Boolean values. Visual whole-degree guides must not override OSNAP/grid priority or alter coordinate parsing.

Temporary guide lines, arcs and readouts must never become committed entities, affect model extents, appear in DXF/PDF/SVG exports or create undo records.

## 9.8 Acceptance tests

- Inventory all user-facing binary enable/disable controls and verify the card component is used consistently.
- Enabled foreground green; disabled foreground grey; opposite state peeks behind; no swipe handler.
- Verify one tap changes only its own state.
- Verify OSNAP mode entries remain independently multi-selectable.
- Verify category checkboxes, action decks, wheel selectors and Touchpad/Joystick cards retain their distinct semantics.
- Verify R, θ and θₚ meanings, values and units.
- Verify all whole-degree visual bearings and custom fractional directions.
- Verify visual tracking does not project the candidate.
- Verify PolarSnap uses only multiple user-selected presets.
- Verify no visual angle is silently added to PolarSnap.
- Verify ORTHO/OSNAP/GRID/GSNAP priority and saved/exported document integrity.

---

# 10. Android application identity, APK outputs and GitHub Actions

## 10.1 Final Android package identity

The intended final identity is:

```kotlin
namespace = "com.precisioncad.studio"
applicationId = "com.precisioncad.studio"
```

The Android launcher label must be `PrecisionCAD Studio`.

The supplied source snapshot currently uses `namespace = "com.example"` and `applicationId = "com.aistudio.precisioncad.kxpqzm"`. Migrate consistently:

- Kotlin source package declarations and imports;
- test package declarations/imports;
- manifest-relative activities/services/providers;
- FileProvider authority and any package-derived identifiers;
- generated `BuildConfig`/namespace references;
- any external service configuration that is actually present.

Do not leave unexplained old package references in the application source.

### Application-ID migration warning

A changed application ID is a different Android application identity. Existing installations under `com.aistudio.precisioncad.kxpqzm` will not be upgraded in place to `com.precisioncad.studio`. Their private application data is not automatically transferred.

Perform this migration before the first public release. Before changing it on existing test devices, export or migrate any important `.pcad` projects. Keep the same final application ID and signing certificate for all subsequent public updates.

## 10.2 Exactly one APK per build variant

The supplied workflow currently builds one Release APK and copies that same file under two different names. It similarly duplicates the Debug APK. These copies are not distinct build variants.

Remove all duplicate-copy packaging. Produce at most:

- one signed Release APK;
- one Debug APK.

A Release-only run produces one Release APK. A Debug-only run produces one Debug APK. A both-variant run produces exactly one of each.

Suggested filenames:

- `PrecisionCAD-Studio-v<versionName>-b<versionCode>-release.apk`
- `PrecisionCAD-Studio-v<versionName>-b<versionCode>-debug.apk`

Names and metadata must match the actual APK manifest/version. Never make a second file solely to create an alternate filename.

## 10.3 Actions artifact packaging

- Upload exactly one APK file per variant.
- Use a maintained upload-artifact action supporting the required non-archived/single-file mode where available (`archive: false` if the selected official action version supports it).
- If the chosen action does not support non-archived artifacts, upload only one APK file and describe the platform's packaging accurately. Do not create a ZIP containing duplicated APKs.
- Give Release and Debug artifacts different artifact names.
- The public GitHub Release asset must be the raw `.apk` file, not an Actions artifact ZIP.
- Do not attach Debug APK to the public production Release unless explicitly approved.

## 10.4 Automatic public Release after a successful commit build

On each successful push to the confirmed primary/release branch:

1. Check out the exact triggering commit.
2. Run required checks and tests.
3. Build the signed Release APK (and Debug only where the workflow selection requests it).
4. Verify the Release APK's application ID and signing certificate.
5. Record full/short commit SHA, workflow run number, version name and version code.
6. Use the triggering commit's first-line subject as the GitHub Release title. Use a deterministic version/hash fallback only if the commit subject is unavailable.
7. Create a unique tag such as `build-<run-number>-<short-sha>` so runs do not overwrite earlier Releases.
8. Publish only after successful build and signature verification.
9. Attach exactly one raw `.apk` Release asset.
10. Include commit SHA, version metadata, run/build number and APK SHA-256 checksum in the release description.

Do not publish production Releases from pull-request builds. Keep manual dispatch for controlled Release-only, Debug-only or both-variant runs; a Debug-only job must never publish production.

Anonymous downloads require the Release repository to be public. If the source repository remains private, use a designated public distribution repository with a narrowly scoped protected credential. Never claim a private repository's Release asset is anonymously downloadable.

## 10.5 Stable Release signing

Remove any fallback that generates a new release keystore on the runner or uses hard-coded release passwords.

- Require the persistent user-controlled release keystore and protected secrets.
- Fail closed before publication if signing credentials are absent or invalid.
- Keep the same keystore, alias and signing certificate for every update under `com.precisioncad.studio`.
- Verify the APK certificate fingerprint against a controlled expected value.
- Never expose passwords, keystore bytes or signing secrets in logs/artifacts/release notes.
- Use protected GitHub Environments or equivalent controls for publication.
- Grant `contents: write` only to the release-publishing job; keep build/test permissions minimal.
- Enforce monotonically increasing `versionCode` and workflow concurrency protection.

## 10.6 Workflow maintenance and build reproducibility

The supplied screenshot reports Node 20 runtime/deprecation warnings and an obsolete setup-java action line. Do not merely suppress the warnings.

- Inspect official release notes at implementation time and use maintained action releases compatible with the then-current runner runtime, including Node 24 where relevant.
- Prefer reviewed immutable commit-SHA pins for third-party actions, with version comments.
- Configure Dependabot or an equivalent process for GitHub Actions dependencies.
- Validate Java, Gradle, AGP, Kotlin and KSP compatibility as a set. Do not update one component blindly.
- Restore the complete official Gradle Wrapper: `gradlew`, `gradlew.bat`, wrapper JAR and wrapper properties.
- Verify the wrapper distribution checksum where supported and use `./gradlew` in CI.
- Avoid dynamic dependency versions and retain reproducible build metadata.
- Do not promise permanent future-proofing; create an explicit dependency-update and compatibility-validation process.

## 10.7 KSP / IntelliJ `ApplicationManager` diagnostics

The supplied workflow screenshot reports two annotation errors indicating `ApplicationManager.getApplication()` returned null during a service lookup, while Gradle build task outcomes are shown as successful. Investigate the diagnostic and build outcome separately.

- Obtain the complete stack trace and identify the emitting plugin, task, processor or IDE integration.
- Check the actual Kotlin/KSP/AGP/Gradle/JDK compatibility matrix.
- The uploaded snapshot lists Kotlin `2.2.10`, AGP `9.1.1` and KSP `2.3.5`; evaluate updates only after confirming compatibility with the actual checkout.
- Compare a clean wrapper-based command-line build with GitHub Actions and any IDE/AI Studio annotation pass.
- Fix or remove an incompatible integration if identified. If it is IDE-only and unrelated to compilation, classify it accurately rather than presenting it as a Gradle build failure.
- Do not hide or delete diagnostics without identifying their source.
- Require clean Release/Debug builds, tests and APK signature verification before public publication.

## 10.8 Acceptance tests

Verify package/namespace consistency, migration warning, one APK per variant, no duplicate copies, correct artifact packaging, public raw APK Release asset, commit-subject Release title, unique tags, anonymous download, stable signing fingerprint, monotonic version code, current actions, Gradle Wrapper usage and the resolution/classification of the KSP diagnostic.

---

# 11. README, source descriptions and architecture documentation

## 11.1 Root README

Add a conventional root `README.md` that serves as the primary project introduction and developer entry point.

It must include:

- product purpose and strictly 2D scope;
- actual implemented feature summary, separating current features from planned work;
- architecture overview and principal source-tree map;
- Kotlin/Compose/Android rendering and document-model responsibilities;
- requirements for Android Studio, JDK, Android SDK and Gradle;
- exact wrapper-based Debug and Release build commands;
- test commands and test structure;
- native `.pcad` saving, private Quick Save and `Documents/PrecisionCAD` behaviour;
- DXF/PDF/SVG export roles;
- GitHub Actions workflow, release signing requirements and public download location;
- current limitations and known issues.

All instructions must match the actual source and workflow. Do not advertise planned functionality as already implemented.

## 11.2 `docs/` architecture and subsystem guides

Add focused documentation files:

- `docs/ARCHITECTURE.md` — application layers, ownership boundaries, state flow and dependency direction.
- `docs/GEOMETRY_AND_COORDINATES.md` — model/screen spaces, transforms, units, numerical precision, angle convention, reference bearings and coordinate parsing.
- `docs/COMMANDS_AND_PREVIEWS.md` — command lifecycle, accepted input, provisional cursor candidate, preview recalculation, option changes, cancel and commit.
- `docs/DIMENSION_SYSTEM.md` — manual dimensions, QDIM, alignment, generated/manual ownership, collision placement, refresh and selective removal.
- `docs/STORAGE_AND_FORMATS.md` — native `.pcad` schema, private save, public Documents save, migration, recovery and DXF/PDF/SVG responsibilities.
- `docs/BUILD_AND_RELEASE.md` — JDK/Gradle/Android requirements, wrapper, signing secrets, application ID, CI variants, Release publication and troubleshooting.

Each document must identify the real source files responsible for the behaviour and mark incomplete work as planned.

## 11.3 Kotlin file-level and API documentation

Add meaningful file-level descriptions and Kotlin KDoc for important classes, interfaces, data structures and non-trivial public/internal methods.

Prioritise:

- CAD document/entity model;
- geometry and modification kernels;
- snapping, coordinate parsing and polar tracking;
- command lifecycle and accepted input state;
- preview generation and option recalculation;
- QDIM feature classification, alignment and collision placement;
- rendering and viewport transforms;
- `.pcad` serialisation and private/public saving;
- DXF/PDF/SVG import/export;
- ViewModel ownership and UI state transitions;
- keyboard/IME, scroll state and gesture ownership where they affect invariants.

Documentation must explain contracts rather than restate function names. For geometry, state coordinate space, units, angle convention, precision expectations, mutability and whether a method changes committed document state or temporary preview state.

Avoid noisy comments that simply paraphrase a line. Keep KDoc concise and accurate at architectural boundaries and complex algorithms.

## 11.4 Documentation maintenance and acceptance

Update README/docs/KDoc whenever the related architecture changes. Keep examples synchronized with working source/CI commands. Never include signing secrets or private credentials. A documentation change is incomplete if it materially misrepresents the code.

Acceptance:

- a new developer can understand purpose, architecture and build requirements from README;
- source-tree maps match the repository;
- wrapper build/test commands work;
- format/storage descriptions match actual behaviour;
- all required architecture guides exist and point to real source files;
- important classes and algorithms have useful KDoc;
- planned and implemented work are clearly distinguished;
- no credentials or unsupported feature claims appear.

---

# 12. Version 17 implementation order

Implement and test in this order to isolate state, layout and release risks:

1. **Establish baseline and branch safety.** Compare the actual checkout/commit with the supplied source archive. Preserve user changes. Do not reimplement completed Version 16 work.
2. **Resolve the OSNAP dialog scroll jump.** Stabilise scroll-state ownership and selection recomposition before redesigning the rest of the configuration dialog.
3. **Implement the application-wide binary toggle-card component.** Inventory all actual binary controls; apply green-enabled/grey-disabled tap-only behaviour while keeping selection controls and action decks distinct.
4. **Implement the Touchpad/Joystick vertical-peek card selector.** Use blue/gold mode identity and preserve controller algorithms.
5. **Extend the Direct Coordinate dialog.** Add editable θ bearing, deterministic X/Y/θ edit-source behaviour, larger responsive layout and HUD bearing display.
6. **Refine the configuration dialog.** Apply the global state cards to its binary controls; preserve OSNAP multi-selection and separate visual polar vs PolarSnap angle sets.
7. **Apply the shared live-preview preservation contract.** Audit every command with an existing preview; preserve accepted inputs and recalculate after option changes.
8. **Correct IME/controller separation and final command-bar engagement.** Move only the upper command region and apply the keyboard-visible OR non-empty text rule.
9. **Implement native `.pcad` persistence.** Private automatic Quick Save, explicit `Documents/PrecisionCAD` save, schema/migration and recovery.
10. **Correct PDF title/basename consistency.** Test a saved `Geo.pcad` producing `Geo.pdf` and `TITLE: Geo`.
11. **Rebuild the QDIM dialog.** Scroll-responsive header/footer, wheel selectors, delayed callouts, icon refresh and footer card deck.
12. **Implement selective dimension removal.** Parent category checkboxes, locked child entries, per-dimension selection, exact count, confirmation and one undoable transaction.
13. **Migrate Android identity.** Move to `com.precisioncad.studio` with project-data migration/export guidance before first public release.
14. **Restore reproducible build configuration.** Complete Gradle Wrapper, validate toolchain compatibility and resolve/classify KSP/IDE diagnostics.
15. **Correct APK packaging and publication.** One Release APK, one Debug APK maximum, stable signing and direct public Release asset.
16. **Add README, architecture guides and KDoc.** Ensure documentation reflects actual implementation.
17. **Run integrated acceptance tests and produce an evidence-based agent report.**

---

# 13. Integrated acceptance matrix and required agent report

## 13.1 Required integrated tests

At minimum, test:

- all existing live-preview command families when their options/sub-options change;
- contextual option-panel entrance and exit;
- keyboard open/closed with the controller expanded and collapsed;
- all four command-bar keyboard/text combinations;
- OSNAP selection from every position in the scrolled configuration dialog, confirming no scroll jump;
- every global Boolean toggle in enabled and disabled states, including rapid taps and accessibility semantics;
- independent multi-select OSNAP modes;
- Touchpad/Joystick card transition and distinct blue/gold identity;
- X/Y edits recalculating θ;
- θ edits preserving R and recalculating X/Y;
- absolute, relative and last-segment bearing references;
- existing Cartesian and Polar/Expression syntax;
- coordinate dialog under portrait, landscape and open keyboard;
- visual all-degree guides versus separately configured PolarSnap presets;
- QDIM header/footer docking, wheel selectors, explanations and footer gesture arbitration;
- category-locked and individually unlocked Clear All selections;
- selective dimension deletion, exact count and undo/redo;
- native `.pcad` round-trip, private crash recovery and explicit Documents save;
- PDF basename/title consistency;
- application ID, signing certificate and version metadata;
- one Release APK and one Debug APK with no duplicate copies;
- public Release asset and anonymous download;
- README and documented wrapper commands.

## 13.2 Required coding-agent completion report

The coding agent must provide:

1. **Repository baseline:** branch, commit SHA, dirty-worktree status and source archive comparison.
2. **Changed files:** path and concise explanation for each modification.
3. **State/architecture changes:** ViewModel/data model, scroll-state owner, coordinate model and toggle component changes.
4. **Tests:** exact commands, device/API configurations and pass/fail results.
5. **Visual evidence:** screenshots or recordings for OSNAP scroll stability, global toggle cards, Touchpad/Joystick card transition, coordinate θ input, QDIM transitions and keyboard/controller separation.
6. **Build evidence:** Gradle Wrapper version, successful Debug/Release build outputs, application ID, version name/code and signing-certificate fingerprint (fingerprint only; never disclose secrets).
7. **Release evidence:** artifact names, artifact file counts, public Release URL and anonymous-download verification.
8. **Documentation evidence:** README/docs/KDoc files created or updated.
9. **Remaining issues:** blockers, unimplemented items, warnings and their exact origin.

Do not say “implemented” when only an instruction or screenshot has been produced. Do not say “tested” without reporting the test or observable evidence.

---

# 14. Final Version 17 precedence

1. This is a **Version 17 delta-only specification**. Completed Version 16 requirements are not repeated as Version 17 tasks.
2. Section 2 applies live-preview state preservation to every command that already has a live preview, not only Rectangle.
3. Section 3 is authoritative for IME movement and command-bar engagement: only the upper command region moves; the controller remains bottom-anchored; action cards show when the keyboard is visible OR command text is non-empty; full idle requires keyboard hidden AND empty text.
4. Section 4 is authoritative for native `.pcad` saving, automatic private Quick Save, explicit `Documents/PrecisionCAD` saving and PDF filename/title-block basename consistency.
5. Section 5 is authoritative for the QDIM scroll-transition layout, rotary selectors, delayed explanations, two-card footer and category-first selective dimension removal.
6. Section 6 is authoritative for the OSNAP configuration dialog scroll-position defect and its correction.
7. Section 7 is authoritative for the Direct Coordinate window's editable bearing θ and deterministic X/Y/R/θ semantics.
8. Section 8 is authoritative for the vertical-peek Touchpad/Joystick mode selector and controller-dock visual reorganisation.
9. Section 9 is authoritative for the **application-wide** binary toggle-card rule: Enabled green, Disabled grey, opposite card peeking, single tap, no swipe. This supersedes any earlier v17 wording that limited the component to the Cursor Control/Grid/OSNAP window or used red for the Disabled state.
10. Section 9's global toggle rule does not alter the separately specified red destructive QDIM Clear All card, the command Send/Cancel deck, the Project Hub export deck, selection checkboxes or the blue/gold Touchpad/Joystick mode selector.
11. Section 10 is authoritative for `com.precisioncad.studio`, one APK per variant, stable signing and public Release publication.
12. Section 11 is authoritative for README, architecture documentation and source-level KDoc.
13. If an older implementation note conflicts with an explicit Version 17 rule above, this Version 17 rule governs. Preserve all completed Version 16 behaviour not explicitly changed here.

**This document contains implementation instructions only. It does not claim that the Android source, APKs or GitHub workflow have been modified or tested.**
