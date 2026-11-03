#!/usr/bin/env python3
"""
PrecisionCAD Studio — Fail-Closed APK Signer Certificate SHA-256 Verifier (V18.3).

Parses `apksigner verify --verbose --print-certs` output, validates that:
1. Exactly one `Number of signers:` line is present and equals `1`.
2. Exactly one `certificate SHA-256 digest:` line is present.
3. The substring AFTER the literal `certificate SHA-256 digest:` marker (regardless of how many
   preceding colons appear in the signer prefix such as `V2 Signer: certificate SHA-256 digest: ...`
   or `Signer #1 certificate SHA-256 digest: ...`), with colons and whitespace removed and normalized
   to lowercase, is an exact 64-character hexadecimal SHA-256 digest (`^[0-9a-f]{64}$`).
4. The trusted expected fingerprint (`RELEASE_CERT_SHA256`) independently normalizes to an exact
   64-character lowercase hexadecimal SHA-256 digest (`^[0-9a-f]{64}$`).
5. The extracted APK signer digest and the normalized trusted fingerprint match character-for-character.

Standard library only; no third-party dependencies.
"""

import argparse
import re
import sys
from typing import Tuple

HEX64_RE = re.compile(r"^[0-9a-f]{64}$")
SIGNER_COUNT_LINE_RE = re.compile(r"^\s*Number of signers:\s*(.+?)\s*$", re.IGNORECASE)
DIGEST_MARKER_RE = re.compile(r"^.*certificate\s+SHA-256\s+digest:\s*(.*)$", re.IGNORECASE)


class CertificateVerificationError(ValueError):
    """Raised when apksigner output or expected certificate fingerprint fails validation."""


def normalize_sha256_fingerprint(raw_value: str, label: str = "fingerprint") -> str:
    """
    Removes colons and whitespace, normalizes to lowercase, and validates exact 64-hex format.
    """
    if raw_value is None:
        raise CertificateVerificationError(f"{label} is missing (None).")
    cleaned = re.sub(r"[:\s]+", "", raw_value).lower()
    if not cleaned:
        raise CertificateVerificationError(f"{label} is empty after removing separators.")
    if len(cleaned) != 64:
        raise CertificateVerificationError(
            f"{label} length is {len(cleaned)} characters; expected exactly 64 hexadecimal characters."
        )
    if not HEX64_RE.match(cleaned):
        raise CertificateVerificationError(
            f"{label} contains non-hexadecimal characters (expected ^[0-9a-f]{{64}}$)."
        )
    return cleaned


def parse_apksigner_output(signer_output: str) -> Tuple[int, str]:
    """
    Parses `apksigner verify --verbose --print-certs` text output.
    Returns `(signer_count, normalized_64_hex_sha256_digest)` or raises `CertificateVerificationError`.
    """
    if not signer_output or not signer_output.strip():
        raise CertificateVerificationError("apksigner output is empty.")

    signer_counts = []
    digest_suffixes = []

    for raw_line in signer_output.splitlines():
        m_count = SIGNER_COUNT_LINE_RE.match(raw_line)
        if m_count:
            raw_count_token = m_count.group(1).strip()
            if not re.match(r"^\d+$", raw_count_token):
                raise CertificateVerificationError(
                    f"Malformed 'Number of signers:' value: {raw_count_token!r}"
                )
            signer_counts.append(int(raw_count_token))

        if "certificate sha-256 digest:" in raw_line.lower():
            m_digest = DIGEST_MARKER_RE.match(raw_line)
            if not m_digest:
                raise CertificateVerificationError(
                    "Found 'certificate SHA-256 digest:' line that could not be parsed."
                )
            digest_suffixes.append(m_digest.group(1))

    if len(signer_counts) != 1:
        raise CertificateVerificationError(
            f"Expected exactly 1 'Number of signers:' line; found {len(signer_counts)}."
        )

    signer_count = signer_counts[0]
    if signer_count != 1:
        raise CertificateVerificationError(
            f"Expected exactly 1 APK signer (Number of signers: 1); found {signer_count}."
        )

    if len(digest_suffixes) != 1:
        raise CertificateVerificationError(
            f"Expected! exactly 1 'certificate SHA-256 digest:' line; found {len(digest_suffixes)}."
        )

    normalized_digest = normalize_sha256_fingerprint(
        digest_suffixes[0], label="APK signer certificate SHA-256 digest"
    )
    return signer_count, normalized_digest


def verify_apksigner_against_expected(signer_output: str, expected_fingerprint: str) -> str:
    """
    Validates both the apksigner output and expected trusted fingerprint and requires exact equality.
    Returns the normalized 64-character lowercase SHA-256 digest on success.
    """
    _, actual_digest = parse_apksigner_output(signer_output)
    expected_digest = normalize_sha256_fingerprint(
        expected_fingerprint, label="Trusted RELEASE_CERT_SHA256"
    )
    if actual_digest != expected_digest:
        raise CertificateVerificationError(
            f"Signed Release APK certificate SHA-256 ({actual_digest}) does not match "
            f"trusted RELEASE_CERT_SHA256 ({expected_digest})."
        )
    return actual_digest


def run_self_tests() -> None:
    """
    Executes the 14 mandatory V18.3 §2.4 unit test fixtures.
    """
    trusted_hex = "7bbcda1924e95ca1758c7e0ac763b56ae9f7eafef4c6e7362bf2d63c63531e59"
    different_hex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    # 1. Exact user-supplied V2 Signer output (two colons on the digest line)
    exact_user_output = """\
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v3.2 scheme (APK Signature Scheme v3.2): false
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
V2 Signer: certificate SHA-256 digest: 7bbcda1924e95ca1758c7e0ac763b56ae9f7eafef4c6e7362bf2d63c63531e59
"""
    parsed = verify_apksigner_against_expected(exact_user_output, trusted_hex)
    assert parsed == trusted_hex, f"Expected {trusted_hex}, got {parsed}"
    assert parsed != "certificatesha-256digest"

    # 2. Signer #1 prefix form (single colon on the digest line)
    signer_1_output = f"""\
Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
Number of signers: 1
Signer #1 certificate DN: CN=PrecisionCAD Studio Release
Signer #1 certificate SHA-256 digest: {trusted_hex}
"""
    assert verify_apksigner_against_expected(signer_1_output, trusted_hex) == trusted_hex

    # 3. Uppercase digest in both apksigner output and expected secret
    upper_output = f"Number of signers: 1\nV2 Signer: certificate SHA-256 digest: {trusted_hex.upper()}\n"
    assert verify_apksigner_against_expected(upper_output, trusted_hex.upper()) == trusted_hex

    # 4. Colon-separated digest
    colon_hex = ":".join(trusted_hex[i : i + 2].upper() for i in range(0, 64, 2))
    colon_output = f"Number of signers: 1\nV2 Signer: certificate SHA-256 digest: {colon_hex}\n"
    assert verify_apksigner_against_expected(colon_output, colon_hex) == trusted_hex

    # 5. Exactly one signer and digest equal to trusted value -> PASS (verified above)

    # 6. Exactly one signer but a different well-formed digest -> FAIL as genuine mismatch
    try:
        verify_apksigner_against_expected(exact_user_output, different_hex)
        raise AssertionError("Expected mismatch failure")
    except CertificateVerificationError as exc:
        assert "does not match" in str(exc)

    # 7. Missing digest marker -> FAIL
    try:
        parse_apksigner_output("Number of signers: 1\nVerified using v2 scheme: true\n")
        raise AssertionError("Expected missing digest marker failure")
    except CertificateVerificationError:
        pass

    # 8. Empty digest -> FAIL
    try:
        parse_apksigner_output("Number of signers: 1\nV2 Signer: certificate SHA-256 digest:   \n")
        raise AssertionError("Expected empty digest failure")
    except CertificateVerificationError:
        pass

    # 9a. Digest with 63 hex characters -> FAIL
    try:
        parse_apksigner_output(
            f"Number of signers: 1\nV2 Signer: certificate SHA-256 digest: {trusted_hex[:-1]}\n"
        )
        raise AssertionError("Expected 63-hex failure")
    except CertificateVerificationError:
        pass

    # 9b. Digest with 65 hex characters -> FAIL
    try:
        parse_apksigner_output(
            f"Number of signers: 1\nV2 Signer: certificate SHA-256 digest: {trusted_hex}a\n"
        )
        raise AssertionError("Expected 65-hex failure")
    except CertificateVerificationError:
        pass

    # 10. Non-hex character in digest -> FAIL
    try:
        parse_apksigner_output(
            f"Number of signers: 1\nV2 Signer: certificate SHA-256 digest: {trusted_hex[:-1]}g\n"
        )
        raise AssertionError("Expected non-hex failure")
    except CertificateVerificationError:
        pass

    # 11. Number of signers: 0 -> FAIL
    try:
        parse_apksigner_output(
            f"Number of signers: 0\nV2 Signer: certificate SHA-256 digest: {trusted_hex}\n"
        )
        raise AssertionError("Expected 0-signer failure")
    except CertificateVerificationError:
        pass

    # 12. Number of signers: 2 -> FAIL
    try:
        parse_apksigner_output(
            f"Number of signers: 2\nV2 Signer: certificate SHA-256 digest: {trusted_hex}\n"
        )
        raise AssertionError("Expected 2-signer failure")
    except CertificateVerificationError:
        pass

    # 13. More than one certificate SHA-256 digest line -> FAIL closed
    try:
        parse_apksigner_output(
            f"Number of signers: 1\n"
            f"Signer #1 certificate SHA-256 digest: {trusted_hex}\n"
            f"V2 Signer: certificate SHA-256 digest: {trusted_hex}\n"
        )
        raise AssertionError("Expected multiple digest lines failure")
    except CertificateVerificationError:
        pass

    # 14. Malformed / missing signer-count line -> FAIL
    try:
        parse_apksigner_output(f"V2 Signer: certificate SHA-256 digest: {trusted_hex}\n")
        raise AssertionError("Expected missing signer-count failure")
    except CertificateVerificationError:
        pass

    try:
        parse_apksigner_output(
            f"Number of signers: one\nV2 Signer: certificate SHA-256 digest: {trusted_hex}\n"
        )
        raise AssertionError("Expected malformed signer-count failure")
    except CertificateVerificationError:
        pass


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify apksigner --verbose --print-certs output against expected SHA-256 fingerprint."
    )
    parser.add_argument(
        "--self-test",
        action="store_true",
        help="Run built-in unit test suite for apksigner certificate parser.",
    )
    parser.add_argument(
        "--expected",
        default="",
        help="Expected trusted 64-character hex SHA-256 certificate fingerprint.",
    )
    parser.add_argument(
        "--input-file",
        default="",
        help="Path to file containing apksigner output (reads stdin if omitted).",
    )
    parser.add_argument(
        "--quiet-digest-only",
        action="store_true",
        help="Print only the normalized 64-hex digest to stdout on success.",
    )
    args = parser.parse_args()

    if args.self_test:
        run_self_tests()
        print("SELF_TEST_OK: All 14 apksigner certificate parser fixtures passed.")
        return 0

    if args.input_file:
        with open(args.input_file, "r", encoding="utf-8", errors="replace") as fh:
            signer_output = fh.read()
    else:
        signer_output = sys.stdin.read()

    try:
        if args.expected:
            digest = verify_apksigner_against_expected(signer_output, args.expected)
            if args.quiet_digest_only:
                print(digest)
            else:
                print(
                    f"VERIFIED_OK: Single APK signer certificate SHA-256 matches trusted RELEASE_CERT_SHA256 ({digest})."
                )
        else:
            _, digest = parse_apksigner_output(signer_output)
            print(digest)
        return 0
    except CertificateVerificationError as exc:
        sys.stderr.write(f"ERROR: {exc}\n")
        return 1


if __name__ == "__main__":
    sys.exit(main())
