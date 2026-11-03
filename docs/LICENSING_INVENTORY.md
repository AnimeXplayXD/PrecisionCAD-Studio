# Informational Source & Dependency Licensing Inventory

> **Informational inventory only.** Automated detection reports licence and copyright information observed in source files, package metadata and bundled materials. It does not select a project licence, determine legal obligations, evaluate compatibility, or decide whether a component may be used. Missing or ambiguous findings require separate human investigation; they are not legal conclusions.

---

## 1. Purpose & Non-Goals

The workflow `.github/workflows/licensing-inventory.yml` and generator script `scripts/generate_licensing_inventory.py` provide a strictly descriptive inventory of:

1. **First-Party & Bundled Repository Files**:
   - Scans source files, Gradle scripts, XML resources, documentation, and bundled binary assets (including `design-assets/icons/`).
   - Records observed `SPDX-License-Identifier` headers, copyright notices, file SHA-256 digests, and line ranges.
   - Binary/image assets without embedded licence text are recorded as `NOT_DETECTED` (never inferred from appearance or filename).
2. **Resolved Gradle Dependency Graphs**:
   - Captures `releaseRuntimeClasspath`, `releaseCompileClasspath`, `debugRuntimeClasspath`, `debugCompileClasspath`, unit-test configurations, KSP processor configurations, and `buildEnvironment` (Gradle plugins/buildscript classpath).
   - Preserves direct vs. transitive relationship and configuration scope.

**Explicit Non-Goals**:
- Does **not** select, grant, or modify a root `LICENSE` file for PrecisionCAD Studio.
- Does **not** classify any component or licence as permitted, prohibited, compatible, incompatible, approved, or rejected.
- Never blocks compilation, tests, pull requests, APK signing, or GitHub Release publication (`continue-on-error: true`, read-only permissions).

---

## 2. Generated Reports (`reports/licensing/`)

Running `python3 scripts/generate_licensing_inventory.py --output-dir reports/licensing` produces:

1. `dependency-bom.cdx.json` — Aggregate CycloneDX 1.5 JSON SBOM.
2. `dependency-bom.cdx.xml` — Aggregate CycloneDX 1.5 XML SBOM.
3. `scancode-source-inventory.json` — Raw file-level JSON inventory.
4. `scancode-source-inventory.csv` — Combined tabular CSV inventory.
5. `licensing-inventory.html` — Human-readable combined HTML report.
6. `licensing-inventory-summary.md` — Concise Markdown summary published to `$GITHUB_STEP_SUMMARY`.
7. `licensing-inventory-status.json` — Scan provenance, tool versions, omitted inputs, and completeness status (`COMPLETE` or `INCOMPLETE`).
8. `gradle-build-logic-dependencies.txt` — Resolved Gradle plugin/buildscript dependency tree.
