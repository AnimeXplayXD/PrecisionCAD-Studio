#!/usr/bin/env python3
"""
Informational Source, Dependency, and Bundled-Material Licensing Inventory Generator (V18.3).

STRICT SCOPE (V18.3 §6):
- Informational inventory only.
- Reports licence identifiers, SPDX headers, copyright statements, and exact GAV POM package metadata
  observed in repository files, resolved Gradle configurations, CycloneDX SBOMs, and bundled materials.
- Never assigns licences from broad Maven group prefixes.
- Separates genuine ScanCode JSON output (`scancode-raw.json`) from the built-in lightweight
  inspector (`repository-file-inspector.json`), and merges real ScanCode findings when present.
- Preserves full, untruncated diagnostic logs under `<output-dir>/diagnostics/`.
- Does NOT select or grant a project licence, evaluate compatibility, or enforce any allow/deny gate.
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
import time
import xml.etree.ElementTree as ET

SCHEMA_VERSION = "18.3.0"

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
    ".build-outputs",
    "build",
    "public",
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
    ".p12",
    ".b64",
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

# Expected Android & KSP resolvable configurations in :app
EXPECTED_APP_CONFIGURATIONS = [
    ("releaseRuntimeClasspath", "Release runtime classpath"),
    ("releaseCompileClasspath", "Release compile classpath"),
    ("debugRuntimeClasspath", "Debug runtime classpath"),
    ("debugCompileClasspath", "Debug compile classpath"),
    ("debugUnitTestRuntimeClasspath", "Debug unit-test runtime classpath"),
    ("debugUnitTestCompileClasspath", "Debug unit-test compile classpath"),
    ("releaseUnitTestRuntimeClasspath", "Release unit-test runtime classpath"),
    ("releaseUnitTestCompileClasspath", "Release unit-test compile classpath"),
    ("debugAndroidTestRuntimeClasspath", "Debug instrumentation-test runtime classpath"),
    ("debugAndroidTestCompileClasspath", "Debug instrumentation-test compile classpath"),
    ("kspReleaseKotlinProcessorClasspath", "KSP Release processor classpath"),
    ("kspDebugKotlinProcessorClasspath", "KSP Debug processor classpath"),
]

# Legacy V18 configuration names that failed because AGP names unit-test configurations
# `<variant>UnitTestRuntimeClasspath` rather than `test<Variant>UnitTestRuntimeClasspath`.
LEGACY_NON_EXISTENT_CONFIG_NOTES = {
    "testDebugUnitTestRuntimeClasspath": (
        "NON_APPLICABLE_LEGACY_NAME: AGP unit-test runtime configuration is named "
        "'debugUnitTestRuntimeClasspath' (the Gradle task is 'testDebugUnitTest')."
    ),
    "testDebugUnitTestCompileClasspath": (
        "NON_APPLICABLE_LEGACY_NAME: AGP unit-test compile configuration is named "
        "'debugUnitTestCompileClasspath' (the Gradle task is 'testDebugUnitTest')."
    ),
}

# Exact, unambiguous license name/URL -> SPDX identifier mappings (applied ONLY to exact POM declarations)
EXACT_LICENSE_NAME_TO_SPDX = {
    "the apache software license, version 2.0": "Apache-2.0",
    "apache license, version 2.0": "Apache-2.0",
    "apache license 2.0": "Apache-2.0",
    "apache-2.0": "Apache-2.0",
    "apache 2.0": "Apache-2.0",
    "asf 2.0": "Apache-2.0",
    "mit license": "MIT",
    "the mit license": "MIT",
    "mit": "MIT",
    "bsd-3-clause": "BSD-3-Clause",
    "the 3-clause bsd license": "BSD-3-Clause",
    "3-clause bsd license": "BSD-3-Clause",
    "new bsd license": "BSD-3-Clause",
    "bsd 3-clause": "BSD-3-Clause",
    "bsd-2-clause": "BSD-2-Clause",
    "the 2-clause bsd license": "BSD-2-Clause",
    "simplified bsd license": "BSD-2-Clause",
    "eclipse public license 1.0": "EPL-1.0",
    "eclipse public license - v 1.0": "EPL-1.0",
    "epl-1.0": "EPL-1.0",
    "eclipse public license v2.0": "EPL-2.0",
    "eclipse public license 2.0": "EPL-2.0",
    "epl-2.0": "EPL-2.0",
    "universal permissive license, version 1.0": "UPL-1.0",
    "upl-1.0": "UPL-1.0",
    "cdll + gplv2 with classpath exception": "CDDL-1.1 OR GPL-2.0-only WITH Classpath-exception-2.0",
    "gpl2 w/ cpe": "GPL-2.0-only WITH Classpath-exception-2.0",
    "gnu general public license, version 2 with the classpath exception": "GPL-2.0-only WITH Classpath-exception-2.0",
    "gnu lesser general public license v2.1 or later": "LGPL-2.1-or-later",
    "lgpl-2.1-or-later": "LGPL-2.1-or-later",
    "mozilla public license 2.0": "MPL-2.0",
    "mpl 2.0": "MPL-2.0",
    "mpl-2.0": "MPL-2.0",
    "cc0 1.0 universal": "CC0-1.0",
    "cc0-1.0": "CC0-1.0",
    "public domain": "LicenseRef-Public-Domain",
    "android software development kit license": "LicenseRef-Android-SDK-License",
}

EXACT_LICENSE_URL_TO_SPDX = {
    "http://www.apache.org/licenses/license-2.0.txt": "Apache-2.0",
    "https://www.apache.org/licenses/license-2.0.txt": "Apache-2.0",
    "http://www.apache.org/licenses/license-2.0": "Apache-2.0",
    "https://www.apache.org/licenses/license-2.0": "Apache-2.0",
    "https://opensource.org/licenses/apache-2.0": "Apache-2.0",
    "http://opensource.org/licenses/mit-license.php": "MIT",
    "https://opensource.org/licenses/mit-license.php": "MIT",
    "http://opensource.org/licenses/mit": "MIT",
    "https://opensource.org/licenses/mit": "MIT",
    "https://opensource.org/license/mit/": "MIT",
    "http://www.eclipse.org/legal/epl-v10.html": "EPL-1.0",
    "https://www.eclipse.org/legal/epl-v10.html": "EPL-1.0",
    "http://www.eclipse.org/legal/epl-v20.html": "EPL-2.0",
    "https://www.eclipse.org/legal/epl-v20.html": "EPL-2.0",
    "https://www.eclipse.org/org/documents/epl-2.0/epl-2.0.txt": "EPL-2.0",
    "http://opensource.org/licenses/bsd-3-clause": "BSD-3-Clause",
    "https://opensource.org/licenses/bsd-3-clause": "BSD-3-Clause",
    "http://opensource.org/licenses/bsd-2-clause": "BSD-2-Clause",
    "https://opensource.org/licenses/bsd-2-clause": "BSD-2-Clause",
    "http://www.mozilla.org/mpl/2.0/": "MPL-2.0",
    "https://www.mozilla.org/en-us/mpl/2.0/": "MPL-2.0",
}


def sha256_file(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def sanitize_log_text(text: str) -> str:
    """Redact any sensitive environment variable values from diagnostic logs."""
    if not text:
        return ""
    out = text
    for key, val in os.environ.items():
        if any(s in key.upper() for s in ("TOKEN", "PASSWORD", "SECRET", "KEYSTORE_BASE64", "PRIVATE_KEY")):
            if val and len(val) >= 4:
                out = out.replace(val, "[REDACTED]")
    return out


def run_cmd(cmd: list[str], cwd: pathlib.Path, timeout: int = 300) -> tuple[int, str, str, float, bool]:
    start = time.monotonic()
    try:
        proc = subprocess.run(
            cmd,
            cwd=str(cwd),
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
        )
        elapsed = round(time.monotonic() - start, 3)
        return proc.returncode, sanitize_log_text(proc.stdout), sanitize_log_text(proc.stderr), elapsed, False
    except subprocess.TimeoutExpired as exc:
        elapsed = round(time.monotonic() - start, 3)
        so = sanitize_log_text(exc.stdout.decode("utf-8", errors="replace") if isinstance(exc.stdout, bytes) else (exc.stdout or ""))
        se = sanitize_log_text(exc.stderr.decode("utf-8", errors="replace") if isinstance(exc.stderr, bytes) else (exc.stderr or ""))
        return 124, so, f"Command timed out after {timeout}s.\n{se}", elapsed, True
    except Exception as exc:
        elapsed = round(time.monotonic() - start, 3)
        return 1, "", sanitize_log_text(str(exc)), elapsed, False


def resolve_effective_app_version(repo_root: pathlib.Path) -> tuple[str, int]:
    """
    Compute the effective application versionName and versionCode using the exact same
    rules as `app/build.gradle.kts` (`version.properties` + CI environment variables).
    Never hard-codes `1.18.0`.
    """
    props = {}
    vp_path = repo_root / "version.properties"
    if vp_path.is_file():
        for raw_line in vp_path.read_text(encoding="utf-8", errors="replace").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            props[k.strip()] = v.strip()

    def to_int(val: str | None, default: int) -> int:
        try:
            return int(val) if val is not None else default
        except ValueError:
            return default

    major = to_int(props.get("VERSION_MAJOR"), 1)
    minor = to_int(props.get("VERSION_MINOR"), 0)
    patch = to_int(props.get("VERSION_PATCH"), 0)
    stored_code = to_int(props.get("VERSION_CODE"), 1)

    explicit_code = to_int(os.environ.get("VERSION_CODE"), 0)
    ci_run_num = to_int(os.environ.get("GITHUB_RUN_NUMBER"), 0)

    if explicit_code > 0:
        version_code = explicit_code
    elif ci_run_num > 0:
        version_code = stored_code + ci_run_num
    else:
        version_code = stored_code

    explicit_name = (os.environ.get("VERSION_NAME") or "").strip()
    git_ref_name = (os.environ.get("GITHUB_REF_NAME") or "").strip()
    git_tag_ver = ""
    if git_ref_name.startswith("v") and len(git_ref_name) > 1:
        git_tag_ver = git_ref_name[1:].split("-b")[0]

    if explicit_name:
        version_name = explicit_name
    elif git_tag_ver:
        version_name = git_tag_ver
    elif ci_run_num > 0:
        version_name = f"{major}.{minor}.{patch}.{ci_run_num}"
    else:
        version_name = f"{major}.{minor}.{patch}"

    return version_name, version_code


def read_toolchain_metadata(repo_root: pathlib.Path) -> dict:
    """Extract configured Gradle Wrapper, AGP, Kotlin, KSP, and CycloneDX versions from repo config."""
    info = {
        "gradleWrapper": "unknown",
        "agp": "unknown",
        "kotlin": "unknown",
        "ksp": "unknown",
        "cyclonedxGradlePlugin": "unknown",
        "jdk": "unknown",
    }
    gw_props = repo_root / "gradle" / "wrapper" / "gradle-wrapper.properties"
    if gw_props.is_file():
        m = re.search(r"gradle-([0-9.]+)-(?:bin|all)\.zip", gw_props.read_text(encoding="utf-8", errors="replace"))
        if m:
            info["gradleWrapper"] = m.group(1)

    toml_path = repo_root / "gradle" / "libs.versions.toml"
    if toml_path.is_file():
        toml_text = toml_path.read_text(encoding="utf-8", errors="replace")
        for key, field in [
            ("agp", "agp"),
            ("kotlin", "kotlin"),
            ("googleDevtoolsKsp", "ksp"),
            ("cyclonedxBom", "cyclonedxGradlePlugin"),
        ]:
            m = re.search(rf'^{key}\s*=\s*"([^"]+)"', toml_text, re.MULTILINE)
            if m:
                info[field] = m.group(1)

    rc, out, err, _, _ = run_cmd(["java", "-version"], repo_root, timeout=15)
    combined = (out + "\n" + err).strip()
    if combined:
        info["jdk"] = combined.splitlines()[0].strip()

    return info


def build_local_pom_index() -> dict[tuple[str, str, str], pathlib.Path]:
    """
    Build an index mapping exact (groupId, artifactId, version) -> POM path from local
    Gradle and Maven caches. Never guesses by prefix.
    """
    index: dict[tuple[str, str, str], pathlib.Path] = {}
    search_roots = []

    gradle_user_home = os.environ.get("GRADLE_USER_HOME")
    if gradle_user_home:
        search_roots.append(pathlib.Path(gradle_user_home) / "caches" / "modules-2" / "files-2.1")
    search_roots.extend(
        [
            pathlib.Path("/opt/gradle/.gradle/caches/modules-2/files-2.1"),
            pathlib.Path.home() / ".gradle" / "caches" / "modules-2" / "files-2.1",
            pathlib.Path.home() / ".m2" / "repository",
        ]
    )

    seen_roots = set()
    for root in search_roots:
        try:
            resolved_root = root.resolve()
        except Exception:
            continue
        if resolved_root in seen_roots or not resolved_root.is_dir():
            continue
        seen_roots.add(resolved_root)

        if "files-2.1" in str(resolved_root):
            # Layout: files-2.1/<group>/<artifact>/<version>/<hash>/<artifact>-<version>.pom
            for group_dir in resolved_root.iterdir():
                if not group_dir.is_dir():
                    continue
                group_id = group_dir.name
                for artifact_dir in group_dir.iterdir():
                    if not artifact_dir.is_dir():
                        continue
                    artifact_id = artifact_dir.name
                    for ver_dir in artifact_dir.iterdir():
                        if not ver_dir.is_dir():
                            continue
                        version = ver_dir.name
                        for pom_path in ver_dir.rglob(f"{artifact_id}-{version}.pom"):
                            index.setdefault((group_id, artifact_id, version), pom_path)
        else:
            # Standard Maven layout: <group/path>/<artifact>/<version>/<artifact>-<version>.pom
            for pom_path in resolved_root.rglob("*.pom"):
                try:
                    rel_parts = pom_path.relative_to(resolved_root).parts
                    if len(rel_parts) >= 4:
                        version = rel_parts[-2]
                        artifact_id = rel_parts[-3]
                        group_id = ".".join(rel_parts[:-3])
                        if pom_path.name == f"{artifact_id}-{version}.pom":
                            index.setdefault((group_id, artifact_id, version), pom_path)
                except Exception:
                    continue

    return index


def _strip_xml_ns(tag: str) -> str:
    return tag.split("}", 1)[-1] if "}" in tag else tag


def _parse_pom_xml_licenses_and_parent(
    pom_path: pathlib.Path,
) -> tuple[list[dict[str, str]], tuple[str, str, str] | None, dict[str, str]]:
    """
    Parse `<licenses>`, `<parent>`, and `<properties>` from an exact POM XML file.
    Returns (licenses_list, parent_gav_or_none, properties_dict).
    """
    text = pom_path.read_text(encoding="utf-8", errors="replace")
    root = ET.fromstring(text)

    props: dict[str, str] = {}
    licenses: list[dict[str, str]] = []
    parent_gav: tuple[str, str, str] | None = None

    for child in root:
        cname = _strip_xml_ns(child.tag)
        if cname == "properties":
            for prop_el in child:
                pname = _strip_xml_ns(prop_el.tag)
                props[pname] = (prop_el.text or "").strip()
        elif cname == "parent":
            pg, pa, pv = "", "", ""
            for pchild in child:
                ptag = _strip_xml_ns(pchild.tag)
                if ptag == "groupId":
                    pg = (pchild.text or "").strip()
                elif ptag == "artifactId":
                    pa = (pchild.text or "").strip()
                elif ptag == "version":
                    pv = (pchild.text or "").strip()
            if pg and pa and pv:
                parent_gav = (pg, pa, pv)
        elif cname == "licenses":
            for lic_el in child:
                if _strip_xml_ns(lic_el.tag) != "license":
                    continue
                name_val = ""
                url_val = ""
                dist_val = ""
                for f_el in lic_el:
                    ftag = _strip_xml_ns(f_el.tag)
                    fval = (f_el.text or "").strip()
                    if ftag == "name":
                        name_val = fval
                    elif ftag == "url":
                        url_val = fval
                    elif ftag == "distribution":
                        dist_val = fval
                if name_val or url_val:
                    licenses.append({"name": name_val, "url": url_val, "distribution": dist_val})

    # Substitute simple ${prop} placeholders inside license name/url if defined in <properties>
    def resolve_props(val: str) -> str:
        def repl(m: re.Match) -> str:
            key = m.group(1)
            return props.get(key, m.group(0))
        return re.sub(r"\$\{([^}]+)\}", repl, val)

    for lic in licenses:
        lic["name"] = resolve_props(lic["name"])
        lic["url"] = resolve_props(lic["url"])

    return licenses, parent_gav, props


def map_exact_pom_license_to_spdx(name: str, url: str) -> tuple[str, bool]:
    """
    Convert an exact POM `<license>` declaration (`name`, `url`) to an SPDX identifier ONLY
    when the exact name or URL is unambiguous. Never guesses from group prefix.
    Returns (spdx_id_or_empty, is_unambiguous).
    """
    norm_name = re.sub(r"\s+", " ", (name or "").strip()).lower()
    norm_url = (url or "").strip().lower().rstrip("/")

    if norm_name in EXACT_LICENSE_NAME_TO_SPDX:
        return EXACT_LICENSE_NAME_TO_SPDX[norm_name], True
    if norm_url in EXACT_LICENSE_URL_TO_SPDX:
        return EXACT_LICENSE_URL_TO_SPDX[norm_url], True
    if (norm_url + "/") in EXACT_LICENSE_URL_TO_SPDX:
        return EXACT_LICENSE_URL_TO_SPDX[norm_url + "/"], True
    return "", False


def resolve_exact_gav_pom_license(
    group_id: str,
    artifact_id: str,
    version: str,
    pom_index: dict[tuple[str, str, str], pathlib.Path],
    cache_memo: dict[tuple[str, str, str], dict],
) -> dict:
    """
    Resolve licence evidence for an exact `(group_id, artifact_id, version)` from its POM in
    the local Gradle/Maven cache (walking `<parent>` POMs only if present in the local cache).
    Never uses group-prefix inference.
    """
    gav = (group_id, artifact_id, version)
    if gav in cache_memo:
        return cache_memo[gav]

    pom_path = pom_index.get(gav)
    if pom_path is None or not pom_path.is_file():
        res = {
            "spdxExpression": "",
            "rawLicenseLabel": "artifact POM not present in local cache",
            "evidenceOrigin": f"unresolved POM for {group_id}:{artifact_id}:{version}",
            "status": "UNKNOWN_METADATA",
            "pomSha256": "",
        }
        cache_memo[gav] = res
        return res

    try:
        pom_hash = sha256_file(pom_path)[:16]
    except Exception:
        pom_hash = "unknown"

    visited = set()
    cur_gav: tuple[str, str, str] | None = gav
    cur_pom: pathlib.Path | None = pom_path
    inherited_from: str | None = None
    observed_licenses: list[dict[str, str]] = []

    while cur_gav is not None and cur_pom is not None and cur_gav not in visited:
        visited.add(cur_gav)
        try:
            lics, parent_gav, _ = _parse_pom_xml_licenses_and_parent(cur_pom)
        except Exception as exc:
            res = {
                "spdxExpression": "",
                "rawLicenseLabel": f"POM XML parse error: {exc}",
                "evidenceOrigin": f"exact artifact POM ({cur_pom.name}, sha256:{pom_hash})",
                "status": "UNKNOWN_METADATA",
                "pomSha256": pom_hash,
            }
            cache_memo[gav] = res
            return res

        if lics:
            observed_licenses = lics
            if cur_gav != gav:
                inherited_from = f"{cur_gav[0]}:{cur_gav[1]}:{cur_gav[2]}"
            break

        if parent_gav is not None and parent_gav in pom_index:
            cur_gav = parent_gav
            cur_pom = pom_index[parent_gav]
        else:
            break

    if not observed_licenses:
        res = {
            "spdxExpression": "",
            "rawLicenseLabel": "no <licenses> declaration in artifact POM",
            "evidenceOrigin": f"exact artifact POM ({pom_path.name}, sha256:{pom_hash})",
            "status": "NOT_DETECTED",
            "pomSha256": pom_hash,
        }
        cache_memo[gav] = res
        return res

    raw_labels = []
    mapped_spdx_list = []
    any_unmapped = False

    for lic in observed_licenses:
        lname = lic.get("name", "")
        lurl = lic.get("url", "")
        if lname and lurl:
            raw_labels.append(f"{lname} ({lurl})")
        elif lname:
            raw_labels.append(lname)
        else:
            raw_labels.append(lurl)

        spdx_id, ok = map_exact_pom_license_to_spdx(lname, lurl)
        if ok and spdx_id:
            if spdx_id not in mapped_spdx_list:
                mapped_spdx_list.append(spdx_id)
        else:
            any_unmapped = True

    origin_str = (
        f"exact artifact POM ({pom_path.name}, sha256:{pom_hash})"
        if not inherited_from
        else f"exact parent POM {inherited_from} for {pom_path.name} (sha256:{pom_hash})"
    )
    raw_combined = "; ".join(raw_labels)

    if len(mapped_spdx_list) == 1 and not any_unmapped and len(observed_licenses) == 1:
        status = "DETECTED"
        spdx_expr = mapped_spdx_list[0]
    elif len(mapped_spdx_list) == 1 and not any_unmapped and len(observed_licenses) > 1:
        # Multiple POM license entries that all map to the identical SPDX identifier
        status = "DETECTED"
        spdx_expr = mapped_spdx_list[0]
    elif len(mapped_spdx_list) > 1:
        status = "MULTIPLE_FINDINGS"
        spdx_expr = " ; ".join(mapped_spdx_list)
    else:
        status = "AMBIGUOUS"
        spdx_expr = " ; ".join(mapped_spdx_list) if mapped_spdx_list else ""

    res = {
        "spdxExpression": spdx_expr,
        "rawLicenseLabel": raw_combined,
        "evidenceOrigin": origin_str,
        "status": status,
        "pomSha256": pom_hash,
    }
    cache_memo[gav] = res
    return res


def parse_gradle_dependencies_output(
    raw_text: str,
    config_name: str,
    pom_index: dict[tuple[str, str, str], pathlib.Path],
    pom_memo: dict[tuple[str, str, str], dict],
) -> list[dict]:
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
        resolved_match = re.search(r"->\s*([^\s(]+)", full_line)
        parts = coord_part.split(":")
        if len(parts) < 2:
            continue
        group_id = parts[0]
        artifact_id = parts[1]
        declared_ver = parts[2] if len(parts) > 2 else ""
        resolved_ver = resolved_match.group(1) if resolved_match else declared_ver
        if not resolved_ver or resolved_ver == "FAILED":
            continue

        key = (config_name, group_id, artifact_id, resolved_ver, rel)
        if key in seen:
            continue
        seen.add(key)

        lic_info = resolve_exact_gav_pom_license(group_id, artifact_id, resolved_ver, pom_index, pom_memo)
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
                "sha256": lic_info["pomSha256"],
                "scope": config_name,
                "relationship": rel,
                "spdxExpression": lic_info["spdxExpression"],
                "rawLicenseLabel": lic_info["rawLicenseLabel"],
                "evidenceOrigin": lic_info["evidenceOrigin"],
                "lineRange": "",
                "copyright": "",
                "scanner": f"Gradle Dependency Graph + Exact POM Inspector {SCHEMA_VERSION}",
                "score": "100.0" if lic_info["spdxExpression"] else "",
                "status": lic_info["status"],
            }
        )
    return records


def scan_repository_files_builtin(repo_root: pathlib.Path) -> tuple[list[dict], list[str], list[str]]:
    """
    Lightweight built-in repository file hash and SPDX/copyright header inspector.
    Distinguishes intentional policy exclusions (`excluded_by_policy`) from unexpected
    read/scan failures (`unexpected_failures`).
    """
    file_records = []
    excluded_by_policy = []
    unexpected_failures = []

    spdx_re = re.compile(r"SPDX-License-Identifier:\s*([A-Za-z0-9.\-+() ANDORWITH]+)")
    copyright_re = re.compile(r"(?i)(copyright\s+(?:\(c\)|©)?\s*\d{4}[^\r\n]{0,120})")

    for root, dirs, files in os.walk(repo_root):
        for d in sorted(dirs):
            if d in EXCLUDED_DIRS or (d.startswith(".") and d != ".github"):
                rel_d = str((pathlib.Path(root) / d).relative_to(repo_root)).replace("\\", "/")
                if d in EXCLUDED_DIRS:
                    excluded_by_policy.append(f"{rel_d}/ (excluded directory by policy)")
        dirs[:] = sorted(d for d in dirs if d not in EXCLUDED_DIRS and not (d.startswith(".") and d != ".github"))
        root_path = pathlib.Path(root)
        for fname in sorted(files):
            fpath = root_path / fname
            rel_path = str(fpath.relative_to(repo_root)).replace("\\", "/")
            if fname in EXCLUDED_FILES:
                excluded_by_policy.append(f"{rel_path} (excluded local/secret file by policy)")
                continue
            ext = fpath.suffix.lower()
            if ext in EXCLUDED_EXTENSIONS:
                excluded_by_policy.append(f"{rel_path} (excluded signing/binary build artifact by policy)")
                continue

            try:
                file_hash = sha256_file(fpath)
            except Exception as exc:
                unexpected_failures.append(f"{rel_path} (read error: {exc})")
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
                        "evidenceOrigin": f"binary/media asset ({ext})",
                        "lineRange": "",
                        "copyright": "",
                        "scanner": f"Repository File Inspector {SCHEMA_VERSION}",
                        "score": "",
                        "status": "NOT_DETECTED",
                    }
                )
                continue

            try:
                text = fpath.read_text(encoding="utf-8", errors="replace")
            except Exception as exc:
                unexpected_failures.append(f"{rel_path} (text decode error: {exc})")
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
                        "scanner": f"Repository File Inspector {SCHEMA_VERSION}",
                        "score": "",
                        "status": "NOT_SCANNED",
                    }
                )
                continue

            spdx_matches = []
            copyright_matches = []
            for idx, line in enumerate(text.splitlines(), start=1):
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
                    "scanner": f"Repository File Inspector {SCHEMA_VERSION}",
                    "score": "100.0" if spdx_matches else "",
                    "status": status,
                }
            )

    return file_records, excluded_by_policy, unexpected_failures


def merge_scancode_raw_into_file_records(
    builtin_records: list[dict],
    scancode_raw_path: pathlib.Path,
    scancode_version_str: str,
) -> tuple[list[dict], int]:
    """
    Parse genuine ScanCode JSON (`scancode-raw.json`) and merge its file-level license,
    copyright, package, score, and line-range findings into the consolidated file records.
    Returns (merged_records, scancode_file_count).
    """
    payload = json.loads(scancode_raw_path.read_text(encoding="utf-8", errors="replace"))
    sc_files = payload.get("files", [])
    if not isinstance(sc_files, list):
        return builtin_records, 0

    by_rel_path: dict[str, dict] = {r["filePath"]: dict(r) for r in builtin_records if r.get("filePath")}
    sc_file_count = 0

    for entry in sc_files:
        if not isinstance(entry, dict) or entry.get("type") == "directory":
            continue
        raw_p = (entry.get("path") or "").strip().replace("\\", "/")
        if not raw_p:
            continue
        # Strip leading repo folder name if ScanCode prefixed it
        parts = raw_p.split("/")
        if raw_p not in by_rel_path and len(parts) > 1:
            candidate = "/".join(parts[1:])
            if candidate in by_rel_path:
                raw_p = candidate
        if raw_p in EXCLUDED_FILES or any(raw_p.startswith(ed + "/") for ed in EXCLUDED_DIRS):
            continue

        sc_file_count += 1
        rec = by_rel_path.get(raw_p)
        if rec is None:
            rec = {
                "recordId": f"file:{raw_p}",
                "recordKind": "file",
                "name": pathlib.Path(raw_p).name,
                "group": "",
                "artifact": "",
                "version": "",
                "purl": "",
                "filePath": raw_p,
                "sha256": entry.get("sha256") or "",
                "scope": "repository-source",
                "relationship": "first-party",
                "spdxExpression": "",
                "rawLicenseLabel": "",
                "evidenceOrigin": "ScanCode file scan",
                "lineRange": "",
                "copyright": "",
                "scanner": f"ScanCode Toolkit ({scancode_version_str})",
                "score": "",
                "status": "NOT_DETECTED",
            }
            by_rel_path[raw_p] = rec

        spdx_expr = (entry.get("detected_license_expression_spdx") or entry.get("detected_license_expression") or "").strip()
        detections = entry.get("license_detections") or entry.get("licenses") or []
        copyrights_list = entry.get("copyrights") or []

        line_ranges = []
        scores = []
        raw_labels = []
        for det in detections:
            if not isinstance(det, dict):
                continue
            lexp = (det.get("license_expression_spdx") or det.get("license_expression") or det.get("key") or "").strip()
            if lexp and lexp not in raw_labels:
                raw_labels.append(lexp)
            matches = det.get("matches") or [det]
            for m in matches:
                if not isinstance(m, dict):
                    continue
                sl = m.get("start_line")
                el = m.get("end_line")
                if sl is not None and el is not None:
                    line_ranges.append(f"L{sl}-L{el}" if sl != el else f"L{sl}")
                sc_val = m.get("score")
                if sc_val is not None:
                    try:
                        scores.append(float(sc_val))
                    except Exception:
                        pass

        c_holders = []
        for c_item in copyrights_list:
            if isinstance(c_item, dict):
                cval = (c_item.get("copyright") or c_item.get("value") or "").strip()
                if cval and cval not in c_holders:
                    c_holders.append(cval)
                sl = c_item.get("start_line")
                if sl is not None:
                    line_ranges.append(f"L{sl}")

        if spdx_expr:
            rec["spdxExpression"] = spdx_expr
            rec["rawLicenseLabel"] = "; ".join(raw_labels) if raw_labels else spdx_expr
            rec["evidenceOrigin"] = "ScanCode license detection"
            rec["status"] = "MULTIPLE_FINDINGS" if len(raw_labels) > 1 else "DETECTED"
        if c_holders:
            rec["copyright"] = "; ".join(c_holders[:5])
            if not rec["spdxExpression"]:
                rec["evidenceOrigin"] = "ScanCode copyright detection"
        if line_ranges:
            rec["lineRange"] = ", ".join(dict.fromkeys(line_ranges))
        if scores:
            rec["score"] = f"{max(scores):.1f}"
        rec["scanner"] = f"ScanCode Toolkit ({scancode_version_str})"

    merged = [by_rel_path[k] for k in sorted(by_rel_path.keys())]
    return merged, sc_file_count


def write_cyclonedx_bom(
    dep_records: list[dict],
    app_version_name: str,
    app_version_code: int,
    commit_sha: str,
    timestamp_iso: str,
    json_path: pathlib.Path,
    xml_path: pathlib.Path,
) -> int:
    components_by_purl: dict[str, dict] = {}
    dependencies_map: dict[str, set[str]] = {}

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
                    {"name": "inventory:evidenceOrigin", "value": rec["evidenceOrigin"]},
                    {"name": "inventory:detectionStatus", "value": rec["status"]},
                ],
            }
            if rec["spdxExpression"]:
                comp["licenses"] = [{"license": {"id": rec["spdxExpression"]}}]
            elif rec["rawLicenseLabel"] and rec["status"] in ("DETECTED", "AMBIGUOUS", "MULTIPLE_FINDINGS"):
                comp["licenses"] = [{"license": {"name": rec["rawLicenseLabel"]}}]
            components_by_purl[purl] = comp
        else:
            existing_props = components_by_purl[purl]["properties"]
            prop_entry = {"name": "gradle:configuration", "value": rec["scope"]}
            if prop_entry not in existing_props:
                existing_props.append(prop_entry)
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
                    "name": "CycloneDX Gradle Graph & Exact POM Inventory Generator",
                    "version": SCHEMA_VERSION,
                }
            ],
            "component": {
                "type": "application",
                "bom-ref": f"pkg:android/com.precisioncad.studio@{app_version_name}",
                "name": "PrecisionCAD Studio",
                "group": "com.precisioncad.studio",
                "version": app_version_name,
                "properties": [
                    {"name": "android:applicationId", "value": "com.precisioncad.studio"},
                    {"name": "android:versionCode", "value": str(app_version_code)},
                    {"name": "git:commitSha", "value": commit_sha},
                ],
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
    app_comp_el = ET.SubElement(meta_el, "component", type="application")
    app_comp_el.set("bom-ref", f"pkg:android/com.precisioncad.studio@{app_version_name}")
    ag_el = ET.SubElement(app_comp_el, "group")
    ag_el.text = "com.precisioncad.studio"
    an_el = ET.SubElement(app_comp_el, "name")
    an_el.text = "PrecisionCAD Studio"
    av_el = ET.SubElement(app_comp_el, "version")
    av_el.text = app_version_name

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
            lic_obj = comp["licenses"][0]["license"]
            if "id" in lic_obj:
                id_el = ET.SubElement(lic_el, "id")
                id_el.text = lic_obj["id"]
            elif "name" in lic_obj:
                nm_el = ET.SubElement(lic_el, "name")
                nm_el.text = lic_obj["name"]

    tree = ET.ElementTree(root)
    ET.indent(tree, space="  ")
    tree.write(str(xml_path), encoding="utf-8", xml_declaration=True)
    return len(components_by_purl)


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate V18.3 informational licensing inventory.")
    parser.add_argument("--output-dir", default="reports/licensing", help="Output directory")
    parser.add_argument("--scancode-bin", default="", help="Optional path to ScanCode CLI executable")
    parser.add_argument(
        "--skip-gradle-query",
        action="store_true",
        help="Skip live Gradle dependency task invocation",
    )
    parser.add_argument(
        "--require-scancode",
        action="store_true",
        help="Treat absence of ScanCode CLI as making scanCompleteness INCOMPLETE (default in CI)",
    )
    args = parser.parse_args()

    repo_root = pathlib.Path(__file__).resolve().parent.parent
    out_dir = repo_root / args.output_dir
    diag_dir = out_dir / "diagnostics"
    out_dir.mkdir(parents=True, exist_ok=True)
    diag_dir.mkdir(parents=True, exist_ok=True)

    timestamp_iso = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    _, git_sha_out, _, _, _ = run_cmd(["git", "rev-parse", "HEAD"], repo_root, timeout=15)
    commit_sha = (git_sha_out.strip() or os.environ.get("GITHUB_SHA") or "workspace-uncommitted").strip()
    _, git_ref_out, _, _, _ = run_cmd(["git", "rev-parse", "--abbrev-ref", "HEAD"], repo_root, timeout=15)
    branch_ref = (git_ref_out.strip() or os.environ.get("GITHUB_REF_NAME") or "main").strip()
    run_id = (os.environ.get("GITHUB_RUN_ID") or "local").strip()

    app_version_name, app_version_code = resolve_effective_app_version(repo_root)
    toolchain_info = read_toolchain_metadata(repo_root)

    # Index all local POM files for exact GAV license resolution
    pom_index = build_local_pom_index()
    pom_memo: dict[tuple[str, str, str], dict] = {}

    gradle_cmd = "./gradlew" if (repo_root / "gradlew").is_file() and os.access(repo_root / "gradlew", os.X_OK) else "gradle"
    configuration_results: dict[str, dict] = {}
    unexpected_omitted_configs: list[str] = []
    dep_records: list[dict] = []
    cyclonedx_plugin_output_path = ""

    build_env_path = out_dir / "gradle-build-logic-dependencies.txt"
    if not args.skip_gradle_query:
        # 1. Capture root buildEnvironment (Gradle plugins / buildscript classpath)
        rc_be, out_be, err_be, dur_be, to_be = run_cmd(
            [gradle_cmd, "buildEnvironment", "--no-daemon"], repo_root, timeout=240
        )
        (diag_dir / "gradle-buildEnvironment.log").write_text(
            f"cmd: {gradle_cmd} buildEnvironment --no-daemon\nrc: {rc_be}\nduration_s: {dur_be}\ntimeout: {to_be}\n"
            f"--- STDOUT ---\n{out_be}\n--- STDERR ---\n{err_be}\n",
            encoding="utf-8",
        )
        if rc_be == 0:
            build_env_path.write_text(out_be, encoding="utf-8")
            be_recs = parse_gradle_dependencies_output(out_be, "buildscript:classpath", pom_index, pom_memo)
            dep_records.extend(be_recs)
            configuration_results["buildscript:classpath"] = {
                "exists": True,
                "resolvable": True,
                "exitCode": 0,
                "durationSeconds": dur_be,
                "recordCount": len(be_recs),
                "status": "RESOLVED",
                "diagnosticLog": "diagnostics/gradle-buildEnvironment.log",
            }
        else:
            build_env_path.write_text(f"buildEnvironment failed (rc={rc_be}):\n{err_be}\n", encoding="utf-8")
            unexpected_omitted_configs.append(f"buildscript:classpath (rc={rc_be})")
            configuration_results["buildscript:classpath"] = {
                "exists": True,
                "resolvable": False,
                "exitCode": rc_be,
                "durationSeconds": dur_be,
                "recordCount": 0,
                "status": "RESOLUTION_FAILED",
                "error": err_be,
                "diagnosticLog": "diagnostics/gradle-buildEnvironment.log",
            }

        # 2. Query all :app configurations in a single `:app:dependencies` pass for speed and completeness
        rc_all, out_all, err_all, dur_all, to_all = run_cmd(
            [gradle_cmd, ":app:dependencies", "--no-daemon"], repo_root, timeout=300
        )
        (diag_dir / "gradle-app-dependencies.log").write_text(
            f"cmd: {gradle_cmd} :app:dependencies --no-daemon\nrc: {rc_all}\nduration_s: {dur_all}\ntimeout: {to_all}\n"
            f"--- STDOUT ---\n{out_all}\n--- STDERR ---\n{err_all}\n",
            encoding="utf-8",
        )

        # Split `:app:dependencies` output into per-configuration blocks
        config_blocks: dict[str, str] = {}
        if rc_all == 0:
            current_cfg = None
            current_lines: list[str] = []
            cfg_header_re = re.compile(r"^([A-Za-z0-9_]+)(?:\s+-\s+.*)?$")
            for line in out_all.splitlines():
                hm = cfg_header_re.match(line)
                if hm:
                    if current_cfg is not None:
                        config_blocks[current_cfg] = "\n".join(current_lines)
                    current_cfg = hm.group(1)
                    current_lines = [line]
                elif current_cfg is not None:
                    current_lines.append(line)
            if current_cfg is not None:
                config_blocks[current_cfg] = "\n".join(current_lines)

        # Refresh POM index in case Gradle downloaded any new POMs
        pom_index = build_local_pom_index()

        for cfg_name, cfg_desc in EXPECTED_APP_CONFIGURATIONS:
            if rc_all == 0 and cfg_name in config_blocks:
                block_text = config_blocks[cfg_name]
                cfg_recs = parse_gradle_dependencies_output(block_text, cfg_name, pom_index, pom_memo)
                dep_records.extend(cfg_recs)
                configuration_results[cfg_name] = {
                    "description": cfg_desc,
                    "exists": True,
                    "resolvable": "(n)" not in block_text.splitlines()[0],
                    "exitCode": 0,
                    "recordCount": len(cfg_recs),
                    "status": "RESOLVED",
                    "diagnosticLog": "diagnostics/gradle-app-dependencies.log",
                }
            elif rc_all == 0 and cfg_name not in config_blocks:
                # Dynamic configuration discovery (§7.3): `:app:dependencies` succeeded (rc=0) and
                # enumerated all configurations in the `:app` container. Configurations not created
                # by AGP in the active variant model (such as `releaseUnitTestRuntimeClasspath` and
                # `releaseUnitTestCompileClasspath` when only Debug unit tests are enabled) are
                # classified as `NOT_APPLICABLE` with an explicit reason rather than failed/missing.
                not_applicable_reason = (
                    "AGP creates unit-test configurations only for enabled unit-test variants; "
                    f"'{cfg_name}' is not created in the ':app' variant model (unit tests run on the "
                    "Debug variant via 'debugUnitTestCompileClasspath' and 'debugUnitTestRuntimeClasspath')."
                )
                log_rel = f"diagnostics/gradle-config-{cfg_name}.log"
                (out_dir / log_rel).write_text(
                    f"Dynamic configuration discovery from ':app:dependencies' (rc=0):\n"
                    f"Configuration '{cfg_name}' is NOT_APPLICABLE in ':app'.\n"
                    f"Reason: {not_applicable_reason}\n",
                    encoding="utf-8",
                )
                configuration_results[cfg_name] = {
                    "description": cfg_desc,
                    "exists": False,
                    "resolvable": False,
                    "exitCode": None,
                    "recordCount": 0,
                    "status": "NOT_APPLICABLE",
                    "notApplicableReason": not_applicable_reason,
                    "diagnosticLog": log_rel,
                }
            else:
                # Fallback individual query when `:app:dependencies` itself did not succeed
                rc_c, out_c, err_c, dur_c, to_c = run_cmd(
                    [gradle_cmd, ":app:dependencies", "--configuration", cfg_name, "--no-daemon"],
                    repo_root,
                    timeout=180,
                )
                log_rel = f"diagnostics/gradle-config-{cfg_name}.log"
                (out_dir / log_rel).write_text(
                    f"cmd: {gradle_cmd} :app:dependencies --configuration {cfg_name} --no-daemon\n"
                    f"rc: {rc_c}\nduration_s: {dur_c}\ntimeout: {to_c}\n"
                    f"--- STDOUT ---\n{out_c}\n--- STDERR ---\n{err_c}\n",
                    encoding="utf-8",
                )
                if rc_c == 0:
                    cfg_recs = parse_gradle_dependencies_output(out_c, cfg_name, pom_index, pom_memo)
                    dep_records.extend(cfg_recs)
                    configuration_results[cfg_name] = {
                        "description": cfg_desc,
                        "exists": True,
                        "resolvable": True,
                        "exitCode": 0,
                        "durationSeconds": dur_c,
                        "recordCount": len(cfg_recs),
                        "status": "RESOLVED",
                        "diagnosticLog": log_rel,
                    }
                else:
                    not_found = "not found in configuration container" in (out_c + err_c)
                    status_label = "NOT_APPLICABLE" if not_found else "RESOLUTION_FAILED"
                    if not not_found:
                        unexpected_omitted_configs.append(f"{cfg_name} (rc={rc_c})")
                    configuration_results[cfg_name] = {
                        "description": cfg_desc,
                        "exists": not not_found,
                        "resolvable": False,
                        "exitCode": None if not_found else rc_c,
                        "durationSeconds": dur_c,
                        "recordCount": 0,
                        "status": status_label,
                        "notApplicableReason": (
                            "Configuration is not created in the active AGP variant model."
                            if not_found
                            else ""
                        ),
                        "error": "" if not_found else (err_c or out_c).strip(),
                        "diagnosticLog": log_rel,
                    }

        for legacy_cfg, note in LEGACY_NON_EXISTENT_CONFIG_NOTES.items():
            configuration_results[legacy_cfg] = {
                "exists": False,
                "resolvable": False,
                "exitCode": None,
                "recordCount": 0,
                "status": "NOT_APPLICABLE",
                "notApplicableReason": note,
                "note": note,
            }
    else:
        build_env_path.write_text("Skipped live Gradle query (--skip-gradle-query).\n", encoding="utf-8")
        unexpected_omitted_configs.append("Live Gradle query skipped via --skip-gradle-query")

    # 3. Run built-in Repository File Inspector and write `repository-file-inspector.json`
    builtin_file_records, excluded_by_policy, unexpected_file_failures = scan_repository_files_builtin(repo_root)
    inspector_json_path = out_dir / "repository-file-inspector.json"
    inspector_payload = {
        "schemaVersion": SCHEMA_VERSION,
        "toolName": "precisioncad-repository-file-inspector",
        "notice": DISCLAIMER_TEXT,
        "commitSha": commit_sha,
        "timestampUtc": timestamp_iso,
        "files": builtin_file_records,
        "excludedByPolicy": excluded_by_policy,
        "unexpectedReadFailures": unexpected_file_failures,
    }
    inspector_json_path.write_text(json.dumps(inspector_payload, indent=2), encoding="utf-8")

    # 4. Discover and run ScanCode CLI (`scancode-raw.json` is written ONLY when ScanCode produces it)
    scancode_raw_path = out_dir / "scancode-raw.json"
    if scancode_raw_path.exists():
        scancode_raw_path.unlink()

    env_install_status = (os.environ.get("SCANCODE_INSTALL_STATUS") or "").strip()
    scancode_bin = (
        args.scancode_bin.strip()
        or (os.environ.get("SCANCODE_BIN") or "").strip()
        or shutil.which("scancode")
        or ""
    )

    scancode_status = "NOT_ATTEMPTED"
    scancode_version = "not-installed"
    scancode_exit_code: int | None = None
    scancode_duration_s: float = 0.0
    scancode_Diagnostic_log = ""

    if env_install_status in (
        "INSTALL_FAILED",
        "ARCHIVE_VERIFIED",
        "BOOTSTRAP_FAILED_PYTHON_MISSING",
        "VERSION_CHECK_FAILED",
    ):
        scancode_status = env_install_status
        scancode_Diagnostic_log = "diagnostics/scancode-install.log"
    elif scancode_bin:
        scancode_status = "ARCHIVE_VERIFIED"
        rc_ver, out_ver, err_ver, dur_ver, _ = run_cmd([scancode_bin, "--version"], repo_root, timeout=30)
        (diag_dir / "scancode-version.log").write_text(
            f"cmd: {scancode_bin} --version\nrc: {rc_ver}\nduration_s: {dur_ver}\n"
            f"--- STDOUT ---\n{out_ver}\n--- STDERR ---\n{err_ver}\n",
            encoding="utf-8",
        )
        rc_help, out_help, err_help, dur_help, _ = run_cmd([scancode_bin, "--help"], repo_root, timeout=30)
        (diag_dir / "scancode-help.log").write_text(
            f"cmd: {scancode_bin} --help\nrc: {rc_help}\nduration_s: {dur_help}\n"
            f"--- STDOUT ---\n{out_help}\n--- STDERR ---\n{err_help}\n",
            encoding="utf-8",
        )
        combined_ver_err = (out_ver + "\n" + err_ver + "\n" + out_help + "\n" + err_help)
        if "Unable to find an installation of Python" in combined_ver_err:
            scancode_status = "BOOTSTRAP_FAILED_PYTHON_MISSING"
            scancode_exit_code = rc_ver if rc_ver != 0 else rc_help
            scancode_Diagnostic_log = "diagnostics/scancode-version.log"
        elif rc_ver != 0 or rc_help != 0:
            scancode_status = "VERSION_CHECK_FAILED"
            scancode_exit_code = rc_ver if rc_ver != 0 else rc_help
            scancode_Diagnostic_log = "diagnostics/scancode-version.log"
        else:
            scancode_version = out_ver.strip().splitlines()[0] if out_ver.strip() else "installed"
            sc_cmd = [
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
                "--ignore",
                "*.apk",
                "--ignore",
                "*.aab",
                "--ignore",
                ".env",
                "--json-pp",
                str(scancode_raw_path),
                str(repo_root),
            ]
            rc_sc, out_sc, err_sc, dur_sc, to_sc = run_cmd(sc_cmd, repo_root, timeout=600)
            scancode_exit_code = rc_sc
            scancode_duration_s = dur_sc
            scancode_Diagnostic_log = "diagnostics/scancode-scan.log"
            (diag_dir / "scancode-scan.log").write_text(
                f"cmd: {' '.join(sc_cmd)}\nrc: {rc_sc}\nduration_s: {dur_sc}\ntimeout: {to_sc}\n"
                f"--- STDOUT ---\n{out_sc}\n--- STDERR ---\n{err_sc}\n",
                encoding="utf-8",
            )
            if rc_sc == 0 and scancode_raw_path.is_file():
                scancode_status = "SCAN_COMPLETE"
            elif scancode_raw_path.is_file():
                scancode_status = "SCAN_PARTIAL"
            else:
                scancode_status = "SCAN_FAILED"

    if scancode_raw_path.is_file() and scancode_status in ("SCAN_COMPLETE", "SCAN_PARTIAL"):
        consolidated_file_records, scancode_files_count = merge_scancode_raw_into_file_records(
            builtin_file_records, scancode_raw_path, scancode_version
        )
    else:
        consolidated_file_records = builtin_file_records
        scancode_files_count = 0

    # 5. Write CycloneDX 1.5 JSON and XML SBOMs with dynamic application version and exact POM evidence
    unique_component_count = write_cyclonedx_bom(
        dep_records=dep_records,
        app_version_name=app_version_name,
        app_version_code=app_version_code,
        commit_sha=commit_sha,
        timestamp_iso=timestamp_iso,
        json_path=out_dir / "dependency-bom.cdx.json",
        xml_path=out_dir / "dependency-bom.cdx.xml",
    )

    # 6. Write consolidated CSV (`licensing-inventory.csv` + backward-compatible `scancode-source-inventory.csv`)
    all_records = dep_records + consolidated_file_records
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
    for csv_filename in ("licensing-inventory.csv", "scancode-source-inventory.csv"):
        csv_path = out_dir / csv_filename
        with csv_path.open("w", encoding="utf-8", newline="") as cf:
            writer = csv.DictWriter(cf, fieldnames=fieldnames)
            writer.writeheader()
            for r in all_records:
                writer.writerow(r)

    # 7. Compute distinct, non-misleading summary counts
    direct_dep_records = [r for r in dep_records if r["relationship"] == "direct"]
    transitive_dep_records = [r for r in dep_records if r["relationship"] == "transitive"]
    unique_direct_purls = {r["purl"] for r in direct_dep_records if r["purl"]}
    unique_transitive_purls = {r["purl"] for r in transitive_dep_records if r["purl"]} - unique_direct_purls
    build_logic_purls = {r["purl"] for r in dep_records if r["scope"] == "buildscript:classpath" and r["purl"]}
    test_only_purls = {
        r["purl"]
        for r in dep_records
        if ("test" in r["scope"].lower()) and r["purl"]
    } - {
        r["purl"]
        for r in dep_records
        if ("release" in r["scope"].lower() or "debug" in r["scope"].lower())
        and "test" not in r["scope"].lower()
        and r["purl"]
    }

    counts_by_scope: dict[str, int] = {}
    for r in dep_records:
        counts_by_scope[r["scope"]] = counts_by_scope.get(r["scope"], 0) + 1

    files_with_license = [r for r in consolidated_file_records if r["spdxExpression"]]
    files_with_copyright = [r for r in consolidated_file_records if r["copyright"]]
    deps_with_detected_license = [r for r in dep_records if r["status"] == "DETECTED"]
    unique_purls_with_license = {r["purl"] for r in deps_with_detected_license if r["purl"]}

    distinct_spdx = sorted({r["spdxExpression"] for r in all_records if r["spdxExpression"]})
    multiple_or_ambiguous = [
        r for r in all_records if r["status"] in ("MULTIPLE_FINDINGS", "AMBIGUOUS")
    ]
    not_detected_records = [
        r for r in all_records if r["status"] in ("NOT_DETECTED", "UNKNOWN_METADATA")
    ]

    # Section 11.4 Completeness definition:
    # COMPLETE only when ScanCode completed (`SCAN_COMPLETE`), all expected resolvable configurations succeeded,
    # and no unexpected file read failures occurred.
    is_complete = (
        scancode_status == "SCAN_COMPLETE"
        and not unexpected_omitted_configs
        and not unexpected_file_failures
    )
    scan_completeness = "COMPLETE" if is_complete else "INCOMPLETE"

    # 8. Write Markdown summary and HTML report before hashing generated artifacts
    md_lines = [
        "# PrecisionCAD Studio — Informational Licensing Inventory Summary",
        "",
        f"> {DISCLAIMER_TEXT}",
        "",
        "## Scan Provenance & Completeness",
        f"- **Scan Completeness**: `{scan_completeness}`",
        f"- **ScanCode Status**: `{scancode_status}` (version: `{scancode_version}`, exitCode: `{scancode_exit_code}`)",
        f"- **Application ID**: `com.precisioncad.studio`",
        f"- **Effective Application Version**: `{app_version_name}` (`versionCode={app_version_code}`)",
        f"- **Commit SHA**: `{commit_sha}`",
        f"- **Branch / Ref**: `{branch_ref}`",
        f"- **Timestamp (UTC)**: `{timestamp_iso}`",
        f"- **Toolchain**: Gradle `{toolchain_info['gradleWrapper']}`, AGP `{toolchain_info['agp']}`, Kotlin `{toolchain_info['kotlin']}`, KSP `{toolchain_info['ksp']}`, JDK `{toolchain_info['jdk']}`",
        "",
        "## Dependency & Component Counts",
        f"- **Unique Resolved Maven Components (Deduplicated by PURL)**: `{unique_component_count}`",
        f"  - Unique direct components: `{len(unique_direct_purls)}`",
        f"  - Unique transitive-only components: `{len(unique_transitive_purls)}`",
        f"  - Buildscript / plugin components: `{len(build_logic_purls)}`",
        f"  - Test-only components: `{len(test_only_purls)}`",
        f"  - Unique components with exact POM licence declaration: `{len(unique_purls_with_license)}`",
        f"- **Total Configuration-Level Dependency Records**: `{len(dep_records)}` (Direct: `{len(direct_dep_records)}`, Transitive: `{len(transitive_dep_records)}`)",
        "",
        "## Repository Source & Bundled Asset Counts",
        f"- **Repository Files Scanned (In-Scope)**: `{len(consolidated_file_records)}` (ScanCode files merged: `{scancode_files_count}`)",
        f"- **Files with Detected Licence Identifier**: `{len(files_with_license)}`",
        f"- **Files with Detected Copyright Notice**: `{len(files_with_copyright)}`",
        f"- **Distinct Observed Licence Identifiers (Exact POM + Source Headers)**: `{', '.join(distinct_spdx) if distinct_spdx else 'none observed'}`",
        f"- **Records with Multiple / Ambiguous Findings**: `{len(multiple_or_ambiguous)}`",
        f"- **Records with No Licence / Unknown Metadata (`NOT_DETECTED` / `UNKNOWN_METADATA`)**: `{len(not_detected_records)}`",
        f"- **Intentional Policy Exclusions**: `{len(excluded_by_policy)}`",
        f"- **Unexpected Read / Configuration Failures**: `{len(unexpected_omitted_configs) + len(unexpected_file_failures)}`",
        "",
        "## Dependency Records by Resolved Gradle Configuration",
    ]
    for cfg_name, cnt in sorted(counts_by_scope.items()):
        md_lines.append(f"- `{cfg_name}`: `{cnt}`")

    if unexpected_omitted_configs or scancode_status != "SCAN_COMPLETE":
        md_lines.extend(["", "## Incomplete Stages & Diagnostics"])
        if scancode_status != "SCAN_COMPLETE":
            md_lines.append(
                f"- **ScanCode CLI**: status=`{scancode_status}`, version=`{scancode_version}`, "
                f"diagnostic=`{scancode_Diagnostic_log or 'not-installed in environment'}`"
            )
        for om in unexpected_omitted_configs:
            md_lines.append(f"- **Gradle Configuration**: `{om}`")
        for uf in unexpected_file_failures:
            md_lines.append(f"- **File Read Error**: `{uf}`")

    md_lines.extend(
        [
            "",
            "## Generated Report Files",
            "- `dependency-bom.cdx.json` (CycloneDX 1.5 JSON SBOM with dynamic app version and exact POM evidence)",
            "- `dependency-bom.cdx.xml` (CycloneDX 1.5 XML SBOM)",
            "- `scancode-raw.json` (Genuine unmodified ScanCode JSON; present only when ScanCode CLI executes)",
            "- `repository-file-inspector.json` (Lightweight built-in SHA-256 and SPDX/copyright header inspector output)",
            "- `licensing-inventory.csv` (Consolidated dependency and file-level CSV inventory)",
            "- `licensing-inventory.html` (Human-readable combined HTML report)",
            "- `licensing-inventory-summary.md` (Concise Markdown summary)",
            "- `licensing-inventory-status.json` (Machine-readable provenance and completeness status)",
            "- `gradle-build-logic-dependencies.txt` (Gradle plugin & buildscript dependencies)",
            "- `diagnostics/` (Full untruncated Gradle and ScanCode stdout/stderr logs)",
        ]
    )
    (out_dir / "licensing-inventory-summary.md").write_text("\n".join(md_lines) + "\n", encoding="utf-8")

    rows_html = []
    for r in all_records[:1000]:
        rows_html.append(
            "<tr>"
            f"<td>{html.escape(r['recordKind'])}</td>"
            f"<td><code>{html.escape(r['name'])}</code></td>"
            f"<td>{html.escape(r['version'])}</td>"
            f"<td><code>{html.escape(r['scope'])}</code></td>"
            f"<td>{html.escape(r['relationship'])}</td>"
            f"<td><code>{html.escape(r['spdxExpression'])}</code></td>"
            f"<td>{html.escape(r['rawLicenseLabel'])}</td>"
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
    table {{ width: 100%; border-collapse: collapse; margin-top: 16px; font-size: 12px; }}
    th, td {{ border: 1px solid #263445; padding: 6px 8px; text-align: left; vertical-align: top; }}
    th {{ background: #16202c; }}
    code {{ color: #93c5fd; }}
  </style>
</head>
<body>
  <h1>PrecisionCAD Studio — Informational Licensing Inventory</h1>
  <blockquote>{html.escape(DISCLAIMER_TEXT)}</blockquote>
  <p><strong>Scan Completeness:</strong> <code>{html.escape(scan_completeness)}</code> |
     <strong>ScanCode Status:</strong> <code>{html.escape(scancode_status)}</code> |
     <strong>App Version:</strong> <code>{html.escape(app_version_name)} (b{app_version_code})</code> |
     <strong>Commit:</strong> <code>{html.escape(commit_sha)}</code> |
     <strong>Timestamp:</strong> <code>{html.escape(timestamp_iso)}</code></p>
  <p><strong>Unique Maven Components:</strong> {unique_component_count} |
     <strong>Configuration Dependency Records:</strong> {len(dep_records)} (Direct: {len(direct_dep_records)}, Transitive: {len(transitive_dep_records)}) |
     <strong>Repository Files Scanned:</strong> {len(consolidated_file_records)} |
     <strong>Distinct Observed Identifiers:</strong> <code>{html.escape(', '.join(distinct_spdx) if distinct_spdx else 'none')}</code></p>
  <table>
    <thead>
      <tr>
        <th>Kind</th>
        <th>Component / File</th>
        <th>Version</th>
        <th>Scope / Config</th>
        <th>Relationship</th>
        <th>Observed SPDX Identifier</th>
        <th>Raw Declaration</th>
        <th>Evidence Origin</th>
        <th>Inventory Status</th>
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

    # 9. Compute SHA-256 digests of all generated report files and write `licensing-inventory-status.json`
    generated_artifacts = []
    for fpath in sorted(out_dir.rglob("*")):
        if fpath.is_file() and fpath.name != "licensing-inventory-status.json":
            rel_f = str(fpath.relative_to(out_dir)).replace("\\", "/")
            generated_artifacts.append({"path": rel_f, "sha256": sha256_file(fpath)})

    status_json = {
        "disclaimer": DISCLAIMER_TEXT,
        "informationalOnly": True,
        "schemaVersion": SCHEMA_VERSION,
        "scanCompleteness": scan_completeness,
        "scancodeStatus": scancode_status,
        "repository": "AnimeXplayXD/PrecisionCAD-Studio",
        "applicationId": "com.precisioncad.studio",
        "effectiveVersionName": app_version_name,
        "effectiveVersionCode": app_version_code,
        "commitSha": commit_sha,
        "branchRef": branch_ref,
        "runId": run_id,
        "timestampUtc": timestamp_iso,
        "toolVersions": {
            "python": sys.version.split()[0],
            "scancode": scancode_version,
            "scancodeExitCode": scancode_exit_code,
            "scancodeDurationSeconds": scancode_duration_s,
            "scancodeDiagnosticLog": scancode_Diagnostic_log,
            "cyclonedxGenerator": f"{SCHEMA_VERSION} (CycloneDX 1.5 schema)",
            "cyclonedxGradlePlugin": toolchain_info["cyclonedxGradlePlugin"],
            "gradleWrapper": toolchain_info["gradleWrapper"],
            "agp": toolchain_info["agp"],
            "kotlin": toolchain_info["kotlin"],
            "ksp": toolchain_info["ksp"],
            "jdk": toolchain_info["jdk"],
        },
        "expectedConfigurations": [c[0] for c in EXPECTED_APP_CONFIGURATIONS] + ["buildscript:classpath"],
        "configurationResolutionResults": configuration_results,
        "uniqueComponentCount": unique_component_count,
        "uniqueDirectComponentCount": len(unique_direct_purls),
        "uniqueTransitiveComponentCount": len(unique_transitive_purls),
        "buildPluginComponentCount": len(build_logic_purls),
        "testOnlyComponentCount": len(test_only_purls),
        "uniqueComponentsWithDeclaredPomLicenseCount": len(unique_purls_with_license),
        "totalDependencyRecords": len(dep_records),
        "directDependencyRecords": len(direct_dep_records),
        "transitiveDependencyRecords": len(transitive_dep_records),
        "dependencyCountsByConfiguration": counts_by_scope,
        "totalRepositoryFilesScanned": len(consolidated_file_records),
        "scancodeFilesMergedCount": scancode_files_count,
        "filesWithDetectedLicenseInfo": len(files_with_license),
        "filesWithDetectedCopyright": len(files_with_copyright),
        "distinctObservedIdentifiers": distinct_spdx,
        "multipleOrAmbiguousCount": len(multiple_or_ambiguous),
        "noLicenseDetectedOrUnknownMetadataCount": len(not_detected_records),
        "excludedByPolicyCount": len(excluded_by_policy),
        "excludedByPolicy": excluded_by_policy,
        "unexpectedReadFailuresCount": len(unexpected_file_failures),
        "unexpectedReadFailures": unexpected_file_failures,
        "omittedConfigurations": unexpected_omitted_configs,
        "generatedArtifacts": generated_artifacts,
    }
    (out_dir / "licensing-inventory-status.json").write_text(
        json.dumps(status_json, indent=2), encoding="utf-8"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
