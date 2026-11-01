# Quick Dimension (`QDIM`), Alignment & Selective Dimension Removal

## 1. Two-Pass Quick Dimension & Alignment Solver (`QuickDimensionEngine`)

1. **Pass 1 — Geometric Feature & Alignment Extraction**:
   - Scans target entities (selected entities or all visible geometry) to extract horizontal/vertical spans, circle diameters (`⌀`), arc/fillet radii (`R`), chamfer bevel notes (`C`), and angular vertex relationships (`°`).
   - Detects near-collinear horizontal/vertical coordinates, near-equal radii, and concentric centers within engineering tolerance to produce a `QuickAlignmentReport`.
2. **Pass 2 — 1D Interval-Graph Lane Allocation & 2D AABB Collision Avoidance**:
   - Assigns each linear dimension to an outward offset tier (`tierIndex`) using interval-graph coloring so parallel extension/dimension lines never overlap.
   - Evaluates 2D Axis-Aligned Bounding Boxes (`AABB`) for dimension text labels and shifts `textOffsetAlongLine` or promotes the dimension tier when a collision with geometry or another label is detected.

---

## 2. QDIM Dialog Architecture

- **Continuous Dark Engineering Surface**: Eliminates nested inner black boxes in favor of a unified scrollable dark surface with scroll-responsive compacting header (`qdim_scroll_header`) and footer (`qdim_scroll_footer`).
- **Rotary / Wheel Selectors with 2-Second Delayed Callout**:
  - Compact single-button wheel selectors for **Dimension Mode** (`qdim_wheel_mode`), **Arrangement** (`qdim_wheel_arrangement`), and **Density Policy** (`qdim_wheel_density`).
  - Pressing and holding for 2 seconds reveals a contextual speech-bubble callout (`qdim_callout_bubble_*`) explaining the active engineering option.
- **Two-Card Footer Action Deck (`qdim_footer_action_deck`)**:
  - **Green Foreground Card (`execute_qdim_confirm_button`)**: *Enable & Generate*.
  - **Red Rear Card (`clear_all_dimensions_button`)**: *Clear All*.
  - Swiping down swaps the front and rear cards without executing; tapping the active front card triggers its action; short-tapping *Clear All* opens a confirmation dialog; **long-pressing *Clear All*** (or tapping `qdim_open_selective_clear_button`) opens the **Category-First Selective Dimension Removal** window.

---

## 3. Category-First Selective Dimension Removal

- Groups all drawing dimensions into six engineering categories:
  1. **Linear — Horizontal**
  2. **Linear — Vertical**
  3. **Linear — Aligned**
  4. **Radius (`R`)**
  5. **Diameter (`⌀`)**
  6. **Angular (`°`)**
- Supports category-level bulk selection, individual dimension inspection/toggling, and single-step undoable deletion (`removeSelectedDimensionsConfirmed(dimensionIdsToRemove)`).
