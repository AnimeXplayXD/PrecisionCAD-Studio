# Quick Dimension (`QDIM`), Card Selectors & Selective Dimension Removal

## 1. Two-Pass Quick Dimension & Alignment Solver (`AutoDimensionEngine` & `DimensionAlignmentEngine`)

1. **Pass 1 — Geometric Feature & Alignment Extraction**:
   - Scans target entities (selected entities or all visible geometry) to extract horizontal/vertical spans, circle diameters (`⌀`), arc/fillet radii (`R`), chamfer bevel notes (`C`), and angular vertex relationships (`°`).
   - Evaluates symmetric margins, equal hole patterns, and collinear alignments within engineering tolerance to produce a `DimensionAlignmentReport`.
2. **Pass 2 — 1D Interval-Graph Lane Allocation & 2D AABB Collision Avoidance**:
   - Assigns each linear dimension to an outward offset tier (`tierIndex`) using interval-graph coloring so parallel extension/dimension lines never overlap.
   - Evaluates 2D Axis-Aligned Bounding Boxes (`AABB`) for dimension text labels and leader rays so callouts avoid colliding with holes and geometry.

---

## 2. QDIM Dialog & Horizontal Card-Based Option Selectors

- **Bounded Scroll Surface**: Uses a single hoisted `rememberScrollState()` with threshold-pinned header (`qdim_scroll_header`, including a compact rotating header refresh icon `qdim_header_refresh_icon_button`) and footer (`qdim_scroll_footer`).
- **Three Horizontal Swipeable Card Decks (`QDimHorizontalCardSelector` / `CadHorizontalOptionCardDeck`)**:
  1. **Operating Scope Mode** (`qdim_wheel_mode`)
  2. **Alignment & Arrangement Mode** (`qdim_wheel_arrangement`)
  3. **Dimension Density Policy** (`qdim_wheel_density`)
  - Horizontal left/right swipes advance or rewind one option at a time using the shared card-stack transition, while vertical drags pass cleanly to the parent dialog scroll container.
- **Non-Layout-Shifting Delayed Callout Overlay**:
  - After resting on an option for 2 seconds, a floating speech-bubble callout overlay (`Popup`) explains the selected option without altering card height, pushing sibling sections, or changing the dialog scroll position.
- **Two-Card Footer Action Deck (`qdim_footer_action_deck`)**:
  - Green foreground **ENABLE & GENERATE** (`execute_qdim_confirm_button`) and red **CLEAR ALL** (`clear_all_dimensions_button`), governed by an isolated `CadDeckIdleTimerState`.
  - Downward swipe exchanges the front and rear cards without executing; tapping *Clear All* opens destructive confirmation; long-pressing *Clear All* opens Selective Dimension Removal.

---

## 3. Selective Dimension Removal: Stable Scroll & Transient Marquee Notice

- Preserves the V17 category-first hierarchy (all 6 categories checked and locked by default; unchecking a category unlocks individual child dimensions; supports checked, unchecked, and indeterminate category states).
- **Header Select All / Deselect All Action (`selective_clear_master_toggle_button`)**: Located in the fixed dialog header outside the scrolling list so toggling never resizes the list viewport.
- **Transient Single-Line Marquee Notification (`selective_clear_transient_marquee`)**:
  - Appears as a floating overlay only after an actual user selection change (e.g., `5 of 7 dimensions selected for removal`), completes one readable marquee cycle before smooth retraction, and never shifts or resets `LazyListState` scroll position.
