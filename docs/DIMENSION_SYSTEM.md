# Quick Dimension (`QDIM`), Card Selectors & Selective Dimension Removal

## 1. Shape-Aware Quick Dimension & Alignment Solver (`AutoDimensionEngine` & `DimensionAlignmentEngine`)

1. **Pass 1 — Shape Classification, Geometric Feature & Alignment Extraction**:
   - Classifies target geometry into `QDimShapeFamily` (`STANDALONE_CIRCLE`, `CONCENTRIC_CIRCLES`, `HOLE_PATTERN_WITHOUT_PLATE`, `STANDALONE_ARC`, `PLATE_WITH_HOLES`, `TRIANGLE_POLYGON`, `RECTANGLE_BOX`, `OPEN_POLYLINE_CONTOUR`, `MIXED_COMPOSITE`) and builds an explicit `QDimDimensionPlan`.
   - Suppresses redundant horizontal/vertical bounding-box and radius/half-span linear callouts on standalone circles, concentric circles, hole patterns without a plate, and standalone partial arcs; emits both `⌀` and `R` in Detailed mode for standalone circles; caps triangle interior angles at 2 in Detailed mode.
   - Evaluates symmetric margins, equal hole patterns, and collinear alignments within engineering tolerance to produce a `DimensionAlignmentReport`.
2. **Pass 2 — 1D Interval-Graph Lane Allocation & Full 2D Collision Avoidance**:
   - Assigns each linear dimension to an outward offset tier (`tierIndex`) using interval-graph coloring so parallel extension/dimension lines never overlap.
   - Evaluates 2D Axis-Aligned Bounding Boxes (`AABB`) and segment intersections across text labels, arrowheads, witness lines, and model contours so callouts avoid colliding with holes, arrows, and geometry.

---

## 2. QDIM Dialog & Horizontal Card-Based Option Selectors

- **Bounded Scroll Surface**: Uses a single hoisted `rememberScrollState()` with threshold-pinned header (`qdim_scroll_header`, including a compact rotating header refresh icon `qdim_header_refresh_icon_button`) and footer (`qdim_scroll_footer`).
- **Three Horizontal Swipeable Card Decks (`QDimHorizontalCardSelector` / `CadHorizontalOptionCardDeck`)**:
  1. **Operating Scope Mode** (`qdim_wheel_mode`, stage badge `01 / 03`)
  2. **Alignment & Arrangement Mode** (`qdim_wheel_arrangement`, stage badge `02 / 03`)
  3. **Dimension Density Policy** (`qdim_wheel_density`, stage badge `03 / 03`)
  - Horizontal left/right swipes advance or rewind one option at a time with a swipe-direction-aware chevron trail (`«  «  «` or `»  »  »`), while vertical drags pass cleanly to the parent dialog scroll container.
- **Parent-Owned Single-Active Callout Overlay (`5.0s` Auto-Dismiss)**:
  - Never opens automatically on initial dialog launch. Only the most recently interacted selector displays its floating callout (`Popup`) for `5,000ms`, immediately replacing any earlier selector's callout and dismissing on vertical scroll.
- **Compact Footer Action Row (`qdim_footer_action_deck` + `qdim_open_selective_clear_button`)**:
  - Green foreground **Generate / Update** (`execute_qdim_confirm_button`) and red **Clear All** (`clear_all_dimensions_button`) two-card deck paired on the same horizontal row with an adjacent icon-first **Selective Clear** button (`qdim_open_selective_clear_button`).

---

## 3. Selective Dimension Removal: Stable Scroll & Transient Marquee Notice

- Preserves the V17 category-first hierarchy (all 6 categories checked and locked by default; unchecking a category unlocks individual child dimensions; supports checked, unchecked, and indeterminate category states).
- **Header Select All / Deselect All Action (`selective_clear_master_toggle_button`)**: Located in the fixed dialog header outside the scrolling list so toggling never resizes the list viewport.
- **Transient Single-Line Marquee Notification (`selective_clear_transient_marquee`)**:
  - Appears as a floating overlay only after an actual user selection change (e.g., `5 of 7 dimensions selected for removal`), completes one readable marquee cycle before smooth retraction, and never shifts or resets `LazyListState` scroll position.
