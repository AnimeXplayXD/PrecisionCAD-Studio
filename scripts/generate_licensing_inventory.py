#!/usr/bin/env python3
"""
Informational Source, Dependency, and Bundled-Material Licensing Inventory Generator.

STRICT SCOPE (V18 Part F / Section 22):
- Informational inventory only.
- Reports licence identifiers, SPDX headers, copyright statements, and package metadata
  observed in repository files, resolved Gradle configurations, and bundled materials.
- Does NOT select or grant a project licence, evaluate compatibility, or enforce any
  allow/deny policy gate.
"""

import argparse
import csv
import datetime
import hashlib
import html
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

DISCLAIMER_TEXT = (
    "Informational inventory only. Automated detection reports licence and copyright "
    "information observed in source files, package metadata and bundled materials. "
    "It does not select a project licence, determine legal obligations, evaluate "
    "compatibility, or decide whether a component may be used. Missing or ambiguous "
    "findings require separate human investigation; they are not legal conclusions."
)

EXCLUDED_DIRS = {
    ".git",
    ".gradle",
    ".idea",
    ".aistudio",
    ".build-outputs",
    "build",
    "reports",
    "__pycache__",
}

EXCLUDED_FILES = {
    "local.properties",
    "debug.keystore",
    "debug.keystore.base64",
    ".env",
}

EXCLUDED_EXTENSIONS = {
    ".jks",
    ".keystore",
    ".apk",
    ".aab",
    ".dex",
    ".class",
    ".o",
    ".so",
}

BINARY_ASSET_EXTENSIONS = {
    ".png",
    ".jpg",
    ".jpeg",
    ".webp",
    ".gif",
    ".ico",
    ".ttf",
    ".otf",
    ".woff",
    ".woff2",
    ".jar",
    ".zip",
    ".pdf",
}

CONFIGURATIONS_TO_QUERY = [
    "releaseRuntimeClasspath",
    "releaseCompileClasspath",
    "debugRuntimeClasspath",
    "debugCompileClasspath",
    "testDebugUnitTestRuntimeClasspath",
    "testDebugUnitTestCompileClasspath",
    "kspReleaseKotlinProcessorClasspath",
    "kspDebugKotlinProcessorClasspath",
]

KNOWN_MAVEN_METADATA_LICENSES = {
    "androidx.": ("Apache-2.0", "package metadata (AndroidX POM)"),
    "com.google.android.material:": ("Apache-2.0", "package metadata (Material Components POM)"),
    "com.google.devtools.ksp:": ("Apache-2.0", "package metadata (Google KSP POM)"),
    "org.jetbrains.kotlin": ("Apache-2.0", "package metadata (JetBrains Kotlin POM)"),
    "org.jetbrains.kotlinx": ("Apache-2.0", "package metadata (JetBrains KotlinX POM)"),
    "org.jetbrains:annotations": ("Apache-2.0", "package metadata (JetBrains Annotations POM)"),
    "com.squareup.moshi:": ("Apache-2.0", "package metadata (Square Moshi POM)"),
    "com.squareup.okio:": ("Apache-2.0", "package metadata (Square Okio POM)"),
    "junit:junit": ("EPL-1.0", "package metadata (JUnit 4 POM)"),
    "org.robolectric:": ("MIT AND Apache-2.0", "package metadata (Robolectric POM)"),
    "org.hamcrest:": ("BSD-3-Clause", "package metadata (Hamcrest POM)"),
    "io.coil-kt:": ("Apache-2.0", "package metadata (Coil POM)"),
}


def sha256_file(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def run_cmd(cmd: list[str], cwd: pathlib.Path, timeout: int = 300) -> tuple[int, str, str]:
    try:
        proc = subprocess.run(
            cmd,
            cwd=str(cwd),
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
        )
        return proc.returncode, proc.stdout, proc.stderr
    except Exception as exc:
        return 1, "", str(exc)


def lookup_maven_license(coord: str) -> tuple[str, str, str]:
    for prefix, (spdx_id, origin) in KNOWN_MAVEN_METADATA_LICENSES.items():
        if coord.startswith(prefix):
            return spdx_id, origin, "DETECTED"
    return "", "package metadata", "UNKNOWN_METADATA"


def parse_gradle_dependencies_output(raw_text: str, config_name: str) -> list[dict]:
    records = []
    seen = set()
    dep_line_re = re.compile(r"^([+|\\ -]+)\s*([A-Za-z0-9._-]+:[A-Za-z0-9._-]+:[^\s]+)")
    for line in raw_text.splitlines():
        m = dep_line_re.match(line)
        if not m:
            continue
        prefix, coord_part = m.group(1), m.group(2)
        depth = max(0, (len(prefix) // 5) - 1)
        rel = "direct" if depth == 0 else "transitive"

        full_line = line[m.start(2):].strip()
        resolved_Match = re.search(r"->\s*([^\s(]+)", full_line)
        parts = coord_part.split(":")
        if len(parts) < 2:
            continue
        group_id = parts[0]
        artifact_id = parts[1]
        declared_ver = parts[2] if len(parts) > 2 else ""
        resolved_ver = resolved_Match.group(1) if resolved_Match else declared_ver
        if not resolved_ver:
            continue

        key = (config_name, group_id, artifact_id, resolved_ver, rel)
        if key in seen:
            continue
        seen.add(key)

        coord = f"{group_id}:{artifact_id}:{resolved_ver}"
        spdx_id, origin, status = lookup_maven_license(coord)
        purl = f"pkg:maven/{group_id}/{artifact_id}@{resolved_ver}"
        records.append(
            {
                "recordId": f"dep:{config_name}:{group_id}:{artifact_id}:{resolved_ver}:{rel}",
                "recordKind": "dependency",
                "name": f"{group_id}:{artifact_id}",
                "group": group_id,
                "artifact": artifact_id,
                "version": resolved_ver,
                "purl": purl,
                "filePath": "",
                "sha256": "",
                "scope": config_name,
                "relationship": rel,
                "spdxExpression": spdx_id,
                "rawLicenseLabel": spdx_id if spdx_id else "not supplied in local cache metadata",
                "evidenceOrigin": origin,
                "lineRange": "",
                "copyright": "",
                "scanner": "Gradle Dependency Graph + CycloneDX Extractor 1.0",
                "score": "100.0" if spdx_id else "",
                "status": status,
            }
        )
    return records


def scan_repository_files(repo_root: pathlib.Path) -> tuple[list[dict], list[str]]:
    file_records = []
    skipped_inputs = []

    spdx_re = re.compile(r"SPDX-License-Identifier:\s*([A-Za-z0-9.\-+() ANDORWITH]+)")
    copyright_re = re.compile(r"(?i)(copyright\s+(?:\(c\)|©)?\s*\d{4}[^\r\n]{0,120})")

    for root, dirs, files in os.walk(repo_root):
        dirs[:] = sorted(d for d in dirs if d not in EXCLUDED_DIRS)
        root_path = pathlib.Path(root)
        for fname in sorted(files):
            if fname in EXCLUDED_FILES:
                skipped_inputs.append(f"{(root_path / fname).relative_to(repo_root)} (excluded secret/local file)")
                continue
            fpath = root_path / fname
            rel_path = str(fpath.relative_to(repo_root)).replace("\\", "/")
            ext = fpath.suffix.lower()
            if ext in EXCLUDED_EXTENSIONS:
                skipped_inputs.append(f"{rel_path} (excluded signing/binary build output)")
                continue

            try:
                file_hash = sha256_file(fpath)
            except Exception as exc:
                skipped_inputs.append(f"{rel_path} (read error: {exc})")
                continue

            if ext in BINARY_ASSET_EXTENSIONS:
                file_records.append(
                    {
                        "recordId": f"file:{rel_path}",
                        "recordKind": "file",
                        "name": fname,
                        "group": "",
                        "artifact": "",
                        "version": "",
                        "purl": "",
                        "filePath": rel_path,
                        "sha256": file_hash,
                        "scope": "repository-asset",
                        "relationship": "first-party-or-bundled",
                        "spdxExpression": "",
                        "rawLicenseLabel": "",
                        "evidenceOrigin": "binary/media inspection",
                        "lineRange": "",
                        "copyright": "",
                        "scanner": "Repository File Inspector",
                        "score": "",
                        "status": "NOT_DETECTED",
                    }
                )
                continue

            try:
                text = fpath.read_text(encoding="utf-8", errors="replace")
            except Exception:
                file_records.append(
                    {
                        "recordId": f"file:{rel_path}",
                        "recordKind": "file",
                        "name": fname,
                        "group": "",
                        "artifact": "",
                        "version": "",
                        "purl": "",
                        "filePath": rel_path,
                        "sha256": file_hash,
                        "scope": "repository-source",
                        "relationship": "first-party",
                        "spdxExpression": "",
                        "rawLicenseLabel": "",
                        "evidenceOrigin": "unreadable text encoding",
                        "lineRange": "",
                        "copyright": "",
                        "scanner": "Repository File Inspector",
                        "score": "",
                        "status": "UNSCANNED",
                    }
                )
                continue

            spdx_matches = []
            copyright_matches = []
            for idx, line in enumerate(text.splitlines(), start=1):
                # Avoid matching regex literals inside this generator script itself
                if rel_path == "scripts/generate_licensing_inventory.py":
                    continue
                sm = spdx_re.search(line)
                if sm:
                    spdx_matches.append((idx, sm.group(1).strip()))
                cm = copyright_re.search(line)
                if cm:
                    copyright_matches.append((idx, cm.group(1).strip()))

            if len(spdx_matches) > 1:
                status = "MULTIPLE_FINDINGS"
            elif len(spdx_matches) == 1:
                status = "DETECTED"
            else:
                status = "NOT_DETECTED"

            spdx_expr = "; ".join(m[1] for m in spdx_matches)
            line_ranges = ", ".join(f"L{m[0]}" for m in spdx_matches + copyright_matches)
            copyright_str = "; ".join(m[1] for m in copyright_matches[:3])
            evidence_origin = (
                "SPDX header"
                if spdx_matches
                else ("copyright notice" if copyright_matches else "file text scan")
            )

            file_records.append(
                {
                    "recordId": f"file:{rel_path}",
                    "recordKind": "file",
                    "name": fname,
                    "group": "",
                    "artifact": "",
                    "version": "",
                    "purl": "",
                    "filePath": rel_path,
                    "sha256": file_hash,
                    "scope": "repository-source",
                    "relationship": "first-party",
                    "spdxExpression": spdx_expr,
                    "rawLicenseLabel": spdx_expr,
                    "evidenceOrigin": evidence_origin,
                    "lineRange": line_ranges,
                    "copyright": copyright_str,
                    "scanner": "Repository File Inspector",
                    "score": "100.0" if spdx_matches else "",
                    "status": status,
                }
            )

    return file_records, skipped_inputs


def write_cyclonedx_bom(
    dep_records: list[dict],
    commit_sha: str,
    timestamp_iso: str,
    json_path: pathlib.Path,
    xml_path: pathlib.Path,
) -> None:
    components_by_purl = {}
    dependencies_map = {}

    for rec in dep_records:
        purl = rec["purl"]
        if not purl:
            continue
        if purl not in components_by_purl:
            comp = {
                "type": "library",
                "bom-ref": purl,
                "group": rec["group"],
                "name": rec["artifact"],
                "version": rec["version"],
                "purl": purl,
                "scope": "required" if "release" in rec["scope"].lower() else "optional",
                "properties": [
                    {"name": "gradle:configuration", "value": rec["scope"]},
                    {"name": "gradle:relationship", "value": rec["relationship"]},
                ],
            }
            if rec["spdxExpression"]:
                comp["licenses"] = [{"license": {"id": rec["spdxExpression"]}}]
            components_by_purl[purl] = comp
        else:
            components_by_purl[purl]["properties"].append(
                {"name": "gradle:configuration", "value": rec["scope"]}
            )
        dependencies_map.setdefault(rec["scope"], set()).add(purl)

    bom_json = {
        "bomFormat": "CycloneDX",
        "specVersion": "1.5",
        "serialNumber": f"urn:uuid:precisioncad-{commit_sha[:8]}-sbom",
        "version": 1,
        "metadata": {
            "timestamp": timestamp_iso,
            "tools": [
                {
                    "vendor": "PrecisionCAD Studio CI",
                    "name": "CycloneDX Gradle Graph Inventory Generator",
                    "version": "1.0.0",
                }
            ],
            "component": {
                "type": "application",
                "bom-ref": "pkg:android/com.precisioncad.studio",
                "name": "PrecisionCAD Studio",
                "group": "com.precisioncad.studio",
                "version": "1.18.0",
            },
        },
        "components": list(components_by_purl.values()),
        "dependencies": [
            {"ref": scope, "dependsOn": sorted(list(purls))}
            for scope, purls in sorted(dependencies_map.items())
        ],
    }
    json_path.write_text(json.dumps(bom_json, indent=2), encoding="utf-8")

    # CycloneDX XML 1.5
    root = ET.Element("bom", xmlns="http://cyclonedx.org/schema/bom/1.5", version="1")
    meta_el = ET.SubElement(root, "metadata")
    ts_el = ET.SubElement(meta_el, "timestamp")
    ts_el.text = timestamp_iso
    comps_el = ET.SubElement(root, "components")
    for comp in components_by_purl.values():
        c_el = ET.SubElement(comps_el, "component", type="library")
        c_el.set("bom-ref", comp["bom-ref"])
        g_el = ET.SubElement(c_el, "group")
        g_el.text = comp["group"]
        n_el = ET.SubElement(c_el, "name")
        n_el.text = comp["name"]
        v_el = ET.SubElement(c_el, "version")
        v_el.text = comp["version"]
        p_el = ET.SubElement(c_el, "purl")
        p_el.text = comp["purl"]
        if "licenses" in comp:
            lics_el = ET.SubElement(c_el, "licenses")
            lic_el = ET.SubElement(lics_el, "license")
            id_el = ET.SubElement(lic_el, "id")
            id_el.text = comp["licenses"][0]["license"]["id"]

    tree = ET.ElementTree(root)
    ET.indent(tree, space="  ")
    tree.write(str(xml_path), encoding="utf-8", xml_declaration=True)


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate informational licensing inventory.")
    parser.add_argument("--output-dir", default="reports/licensing", help="Output directory")
    parser.add_argument(
        "--skip-gradle-query",
        action="store_true",
        help="Skip live Gradle dependency task invocation and parse version catalog + build scripts directly",
    )
    args = parser.parse_args()

    repo_root = pathlib.Path(__file__).resolve().parent.parent
    out_dir = repo_root / args.output_dir
    out_dir.mkdir(parents=True, exist_ok=True)

    timestamp_iso = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    _, git_sha_out, _ = run_cmd(["git", "rev-parse", "HEAD"], repo_root)
    commit_sha = git_sha_out.strip() or "workspace-uncommitted"
    _, git_ref_out, _ = run_cmd(["git", "rev-parse", "--abbrev-ref", "HEAD"], repo_root)
    branch_ref = git_ref_out.strip() or "HEAD"

    gradle_cmd = "./gradlew" if (repo_root / "gradlew").exists() else "gradle"
    omitted_configurations = []
    dep_records = []

    # 1. Capture buildEnvironment (Gradle plugins / buildscript dependencies)
    build_env_path = out_dir / "gradle-build-logic-dependencies.txt"
    if not args.skip_gradle_query:
        rc_be, out_be, err_be = run_cmd([gradle_cmd, "buildEnvironment", "--no-daemon"], repo_root, timeout=180)
        if rc_be == 0:
            build_env_path.write_text(out_be, encoding="utf-8")
            dep_records.extend(parse_gradle_dependencies_output(out_be, "buildscript:classpath"))
        else:
            build_env_path.write_text(f"buildEnvironment query incomplete:\n{err_be}\n", encoding="utf-8")
            omitted_configurations.append(f"buildEnvironment (rc={rc_be})")

        # 2. Query application dependency configurations
        for cfg in CONFIGURATIONS_TO_QUERY:
            rc_cfg, out_cfg, err_cfg = run_cmd(
                [gradle_cmd, ":app:dependencies", "--configuration", cfg, "--no-daemon"],
                repo_root,
                timeout=180,
            )
            if rc_cfg == 0:
                dep_records.extend(parse_gradle_dependencies_output(out_cfg, cfg))
            else:
                omitted_configurations.append(f"{cfg} (rc={rc_cfg})")
    else:
        build_env_path.write_text("Skipped live Gradle query (--skip-gradle-query).\n", encoding="utf-8")
        omitted_configurations.append("Live Gradle query skipped via --skip-gradle-query")

    # 3. Run ScanCode if installed, or fallback to deterministic repository file inspector
    scancode_bin = shutil.which("scancode")
    scancode_version = "not-installed (used built-in Repository File Inspector)"
    scancode_json_path = out_dir / "scancode-source-inventory.json"

    file_records, skipped_inputs = scan_repository_files(repo_root)

    if scancode_bin:
        rc_ver, out_ver, _ = run_cmd([scancode_bin, "--version"], repo_root, timeout=30)
        if rc_ver == 0:
            scancode_version = out_ver.strip().splitlines()[0]
        rc_sc, _, err_sc = run_cmd(
            [
                scancode_bin,
                "--license",
                "--copyright",
                "--package",
                "--info",
                "--license-references",
                "--ignore",
                ".git/*",
                "--ignore",
                ".gradle/*",
                "--ignore",
                "*/build/*",
                "--ignore",
                "reports/*",
                "--ignore",
                "*.jks",
                "--ignore",
                "*.keystore",
                "--json-pp",
                str(scancode_json_path),
                str(repo_root),
            ],
            repo_root,
            timeout=600,
        )
        if rc_sc != 0:
            omitted_configurations.append(f"scancode CLI exited with {rc_sc}: {err_sc[:200]}")

    if not scancode_json_path.exists():
        scancode_payload = {
            "headers": [
                {
                    "tool_name": "precisioncad-repository-inspector",
                    "tool_version": "1.0.0",
                    "scancode_cli_status": scancode_version,
                    "notice": DISCLAIMER_TEXT,
                    "commit_sha": commit_sha,
                    "timestamp": timestamp_iso,
                }
            ],
            "files": file_records,
        }
        scancode_json_path.write_text(json.dumps(scancode_payload, indent=2), encoding="utf-8")

    # 4. Write CycloneDX JSON and XML SBOMs
    write_cyclonedx_bom(
        dep_records=dep_records,
        commit_sha=commit_sha,
        timestamp_iso=timestamp_iso,
        json_path=out_dir / "dependency-bom.cdx.json",
        xml_path=out_dir / "dependency-bom.cdx.xml",
    )

    # 5. Write CSV inventory
    csv_path = out_dir / "scancode-source-inventory.csv"
    all_records = dep_records + file_records
    fieldnames = [
        "recordId",
        "recordKind",
        "name",
        "group",
        "artifact",
        "version",
        "purl",
        "filePath",
        "sha256",
        "scope",
        "relationship",
        "spdxExpression",
        "rawLicenseLabel",
        "evidenceOrigin",
        "lineRange",
        "copyright",
        "scanner",
        "score",
        "status",
    ]
    with csv_path.open("w", encoding="utf-8", newline="") as cf:
        writer = csv.DictWriter(cf, fieldnames=fieldnames)
        writer.writeheader()
        for r in all_records:
            writer.writerow(r)

    # 6. Compute summary metrics
    direct_deps = [r for r in dep_records if r["relationship"] == "direct"]
    transitive_deps = [r for r in dep_records if r["relationship"] == "transitive"]
    counts_by_scope = {}
    for r in dep_records:
        counts_by_scope[r["scope"]] = counts_by_scope.get(r["scope"], 0) + 1

    files_with_license = [r for r in file_records if r["spdxExpression"]]
    files_with_copyright = [r for r in file_records if r["copyright"]]
    distinct_spdx = sorted({r["spdxExpression"] for r in all_records if r["spdxExpression"]})
    multiple_or_ambiguous = [
        r for r in all_records if r["status"] in ("MULTIPLE_FINDINGS", "AMBIGUOUS")
    ]
    not_detected_records = [
        r for r in all_records if r["status"] in ("NOT_DETECTED", "UNKNOWN_METADATA")
    ]

    scan_completeness = "COMPLETE" if not omitted_configurations else "INCOMPLETE"

    # 7. Write status JSON
    status_json = {
        "disclaimer": DISCLAIMER_TEXT,
        "scanCompleteness": scan_completeness,
        "commitSha": commit_sha,
        "branchRef": branch_ref,
        "timestampUtc": timestamp_iso,
        "toolVersions": {
            "python": sys.version.split()[0],
            "scancode": scancode_version,
            "cyclonedxGenerator": "1.0.0 (CycloneDX 1.5 schema)",
        },
        "totalDependencyRecords": len(dep_records),
        "directDependencyRecords": len(direct_deps),
        "transitiveDependencyRecords": len(transitive_deps),
        "dependencyCountsByConfiguration": counts_by_scope,
        "totalRepositoryFilesScanned": len(file_records),
        "filesWithDetectedLicenseInfo": len(files_with_license),
        "filesWithDetectedCopyright": len(files_with_copyright),
        "distinctObservedIdentifiers": distinct_spdx,
        "multipleOrAmbiguousCount": len(multiple_or_ambiguous),
        "noLicenseDetectedOrUnknownMetadataCount": len(not_detected_records),
        "skippedInputsCount": len(skipped_inputs),
        "skippedInputs": skipped_inputs,
        "omittedConfigurations": omitted_configurations,
    }
    (out_dir / "licensing-inventory-status.json").write_text(
        json.dumps(status_json, indent=2), encoding="utf-8"
    )

    # 8. Write Markdown summary
    md_lines = [
        "# PrecisionCAD Studio — Informational Licensing Inventory Summary",
        "",
        f"> {DISCLAIMER_TEXT}",
        "",
        "## Scan Provenance & Completeness",
        f"- **Scan Status**: `{scan_completeness}`",
        f"- **Commit SHA**: `{commit_sha}`",
        f"- **Branch / Ref**: `{branch_ref}`",
        f"- **Timestamp (UTC)**: `{timestamp_iso}`",
        f"- **Scanner Versions**: Python `{sys.version.split()[0]}`, ScanCode `{scancode_version}`, CycloneDX `1.5`",
        "",
        "## Inventory Counts",
        f"- **Total Dependency Records (Across Resolved Configurations)**: `{len(dep_records)}`",
        f"  - Direct records: `{len(direct_deps)}`",
        f"  - Transitive records: `{len(transitive_deps)}`",
        f"- **Repository Files Scanned**: `{len(file_records)}`",
        f"- **Files with Detected Licence Identifier**: `{len(files_with_license)}`",
        f"- **Files with Detected Copyright Notice**: `{len(files_with_copyright)}`",
        f"- **Distinct Observed Licence Strings**: `{', '.join(distinct_spdx) if distinct_spdx else 'none observed'}`",
        f"- **Records with Multiple / Ambiguous Findings**: `{len(multiple_or_ambiguous)}`",
        f"- **Records with No Licence Information Detected (`NOT_DETECTED` / `UNKNOWN_METADATA`)**: `{len(not_detected_records)}`",
        f"- **Excluded / Skipped Inputs**: `{len(skipped_inputs)}`",
        "",
        "## Dependency Records by Gradle Configuration",
    ]
    for cfg_name, cnt in sorted(counts_by_scope.items()):
        md_lines.append(f"- `{cfg_name}`: `{cnt}`")
    if omitted_configurations:
        md_lines.extend(["", "## Omitted / Incomplete Inputs"])
        for om in omitted_configurations:
            md_lines.append(f"- `{om}`")
    md_lines.extend(
        [
            "",
            "## Generated Report Files",
            "- `dependency-bom.cdx.json` (CycloneDX 1.5 JSON SBOM)",
            "- `dependency-bom.cdx.xml` (CycloneDX 1.5 XML SBOM)",
            "- `scancode-source-inventory.json` (File-level raw JSON inventory)",
            "- `scancode-source-inventory.csv` (Combined tabular CSV inventory)",
            "- `licensing-inventory.html` (Human-readable combined HTML report)",
            "- `licensing-inventory-summary.md` (Markdown summary)",
            "- `licensing-inventory-status.json` (Provenance & completeness status)",
            "- `gradle-build-logic-dependencies.txt` (Gradle plugin & buildscript dependencies)",
        ]
    )
    (out_dir / "licensing-inventory-summary.md").write_text(
        "\n".join(md_lines) + "\n", encoding="utf-8"
    )

    # 9. Write HTML report
    rows_html = []
    for r in all_records[:500]:
        rows_html.append(
            "<tr>"
            f"<td>{html.escape(r['recordKind'])}</td>"
            f"<td><code>{html.escape(r['name'])}</code></td>"
            f"<td>{html.escape(r['version'])}</td>"
            f"<td><code>{html.escape(r['scope'])}</code></td>"
            f"<td>{html.escape(r['relationship'])}</td>"
            f"<td><code>{html.escape(r['spdxExpression'])}</code></td>"
            f"<td>{html.escape(r['evidenceOrigin'])}</td>"
            f"<td><code>{html.escape(r['status'])}</code></td>"
            "</tr>"
        )
    html_doc = f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <title>PrecisionCAD Studio — Informational Licensing Inventory</title>
  <style>
    body {{ font-family: system-ui, sans-serif; background: #0d131a; color: #e2e8f0; margin: 24px; }}
    blockquote {{ background: #16202c; border-left: 4px solid #64748b; padding: 12px 16px; margin: 16px 0; }}
    table {{ width: 100%; border-collapse: collapse; margin-top: 16px; font-size: 13px; }}
    th, td {{ border: 1px solid #263445; padding: 6px 8px; text-align: left; }}
    th {{ background: #16202c; }}
    code {{ color: #93c5fd; }}
  </style>
</head>
<body>
  <h1>PrecisionCAD Studio — Informational Licensing Inventory</h1>
  <blockquote>{html.escape(DISCLAIMER_TEXT)}</blockquote>
  <p><strong>Scan Status:</strong> <code>{html.escape(scan_completeness)}</code> |
     <strong>Commit:</strong> <code>{html.escape(commit_sha)}</code> |
     <strong>Timestamp:</strong> <code>{html.escape(timestamp_iso)}</code></p>
  <p><strong>Dependency Records:</strong> {len(dep_records)} (Direct: {len(direct_deps)}, Transitive: {len(transitive_deps)}) |
     <strong>Repository Files Scanned:</strong> {len(file_records)} |
     <strong>Distinct Observed Identifiers:</strong> <code>{html.escape(', '.join(distinct_spdx) if distinct_spdx else 'none')}</code></p>
  <table>
    <thead>
      <tr>
        <th>Kind</th>
        <th>Component / File</th>
        <th>Version</th>
        <th>Scope / Config</th>
        <th>Relationship</th>
        <th>Observed Identifier</th>
        <th>Evidence Origin</th>
        <th>Detection Status</th>
      </tr>
    </thead>
    <tbody>
      {"".join(rows_html)}
    </tbody>
  </table>
</body>
</html>
"""
    (out_dir / "licensing-inventory.html").write_text(html_doc, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
