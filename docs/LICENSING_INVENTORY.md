# Informational Source & Dependency Licensing Inventory

> **Informational inventory only.** Automated detection reports licence and copyright information observed in source files, package metadata and bundled materials. It does not select a project licence, determine legal obligations, evaluate compatibility, or decide whether a component may be used. Missing or ambiguous findings require separate human investigation; they are not legal conclusions.

---

## 1. Purpose & Non-Goals

The workflow `.github/workflows/licensing-inventory.yml` and generator script `scripts/generate_licensing_inventory.py` provide a strictly descriptive inventory of:

1. **First-Party & Bundled Repository Files**:
   - Scans source files, Gradle scripts, XML resources, documentation, and bundled binary assets (including `design-assets/icons/`).
   - Provisions Python `3.12.x` via `actions/setup-python@v7.1.0` on `ubuntu-26.04` before bootstrapping the official ScanCode Toolkit `v32.5.0` standalone Python 3.12 Linux archive (`scancode-toolkit-v32.5.0_py3.12-linux.tar.gz`, verified against SHA-256 `638adcd0af576d1f4d5b64dde228724b3ca4fdee2c4de20d88e4356be353f027`) in CI alongside the built-in deterministic repository file inspector (`repository-file-inspector.json`).
   - Distinguishes archive SHA-256 verification from CLI execution (`ARCHIVE_VERIFIED`, `BOOTSTRAP_FAILED_PYTHON_MISSING`, `VERSION_CHECK_FAILED`, `SCAN_FAILED`, `SCAN_PARTIAL`, `SCAN_COMPLETE`).
   - Records observed `SPDX-License-Identifier` headers, copyright notices, file SHA-256 digests, and line ranges.
   - Binary/image assets without embedded licence text are recorded as `NOT_DETECTED` (never inferred from appearance or filename).
2. **Resolved Gradle Dependency Graphs & Exact POM Metadata**:
   - Captures `releaseRuntimeClasspath`, `releaseCompileClasspath`, `debugRuntimeClasspath`, `debugCompileClasspath`, `debugUnitTestRuntimeClasspath`, `debugUnitTestCompileClasspath`, `releaseUnitTestRuntimeClasspath`, `releaseUnitTestCompileClasspath`, `debugAndroidTestRuntimeClasspath`, `debugAndroidTestCompileClasspath`, `kspReleaseKotlinProcessorClasspath`, `kspDebugKotlinProcessorClasspath`, and `buildEnvironment` (Gradle plugins/buildscript classpath).
   - Dynamically discovers resolvable configurations via `:app:dependencies` so Android configurations that are not created by the project's variant setup (such as `releaseUnitTestRuntimeClasspath` and `releaseUnitTestCompileClasspath` when release unit tests are not enabled) are classified as `NOT_APPLICABLE` rather than `FAILED`.
   - Extracts declared `<licenses>` directly from each component's exact resolved Maven `.pom` (and parent `.pom` chain when inherited) in the local Gradle cache (`files-2.1`).
   - Group-prefix or vendor-prefix heuristics are **never** used; if a component's POM is absent or lacks `<licenses>`, `licenseEvidenceSource` is recorded as `NONE` and `observedLicenseExpression` is `UNKNOWN`.

**Explicit Non-Goals**:
- Does **not** select, grant, or modify a root `LICENSE` file for PrecisionCAD Studio.
- Does **not** classify any component or licence as permitted, prohibited, compatible, incompatible, approved, or rejected.
- Never blocks compilation, tests, pull requests, APK signing, or GitHub Release publication (`continue-on-error: true`, read-only permissions).

---

## 2. Generated Reports (`reports/licensing/`)

Running `python3 scripts/generate_licensing_inventory.py --output-dir reports/licensing` produces:

1. `dependency-bom.cdx.json` — Aggregate CycloneDX 1.5 JSON SBOM with per-GAV POM license evidence and dynamic application `versionName (b<versionCode>)`.
2. `dependency-bom.cdx.xml` — Aggregate CycloneDX 1.5 XML SBOM.
3. `scancode-raw.json` — Raw ScanCode Toolkit JSON output when ScanCode CLI is invoked.
4. `repository-file-inspector.json` — Deterministic repository file header/copyright/SHA-256 inventory.
5. `scancode-source-inventory.json` — Combined file-level JSON inventory linking both `repository-file-inspector.json` and `scancode-raw.json`.
6. `scancode-source-inventory.csv` — Combined tabular CSV inventory of dependencies and repository files.
7. `licensing-inventory.html` — Human-readable combined HTML report.
8. `licensing-inventory-summary.md` — Concise Markdown summary published to `$GITHUB_STEP_SUMMARY`.
9. `licensing-inventory-status.json` — Scan provenance, tool versions, configuration resolution status, POM coverage metrics, and completeness status (`COMPLETE` or `INCOMPLETE`).
10. `gradle-build-logic-dependencies.txt` — Resolved Gradle plugin/buildscript dependency tree (`buildEnvironment`).
11. `diagnostics/` — Full untruncated stdout/stderr logs for every Gradle configuration query and ScanCode invocation.
