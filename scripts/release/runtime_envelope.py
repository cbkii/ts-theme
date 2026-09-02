#!/usr/bin/env python3
"""Validate the built APK's minimal RePlugin runtime envelope.

The TS18 theme has no executable feature implementation of its own. The signed
APK should therefore contain only the pinned RePlugin 2.3.4 compatibility
payload plus generated theme resource classes. This check runs against the
actual APK rather than inferring the result from Gradle source configuration.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from tools.ts18_theme import ThemeError, audit_apk  # noqa: E402


class RuntimeEnvelopeError(RuntimeError):
    pass


def load_config(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise RuntimeEnvelopeError(f"Unable to read runtime-envelope configuration: {exc}") from exc
    if not isinstance(value, dict):
        raise RuntimeEnvelopeError("Release configuration must be a JSON object")
    return value


def validate_runtime_envelope(audit: dict[str, Any], config: dict[str, Any]) -> dict[str, Any]:
    envelope = config.get("runtime_envelope")
    if not isinstance(envelope, dict):
        raise RuntimeEnvelopeError("release-config.json is missing runtime_envelope")

    expected_dex_files = envelope.get("expected_dex_files")
    max_dex_classes = envelope.get("max_dex_classes")
    allowed_prefixes = envelope.get("allowed_class_prefixes")
    required_classes = config.get("required_dex_classes")
    if not isinstance(expected_dex_files, int) or expected_dex_files < 1:
        raise RuntimeEnvelopeError("runtime_envelope.expected_dex_files must be a positive integer")
    if not isinstance(max_dex_classes, int) or max_dex_classes < 1:
        raise RuntimeEnvelopeError("runtime_envelope.max_dex_classes must be a positive integer")
    if (
        not isinstance(allowed_prefixes, list)
        or not allowed_prefixes
        or any(not isinstance(item, str) or not item.startswith("L") for item in allowed_prefixes)
    ):
        raise RuntimeEnvelopeError("runtime_envelope.allowed_class_prefixes is malformed")
    if (
        not isinstance(required_classes, list)
        or not required_classes
        or any(not isinstance(item, str) or not item for item in required_classes)
    ):
        raise RuntimeEnvelopeError("required_dex_classes is malformed")

    dex_file_count = audit.get("dex_file_count")
    dex_class_count = audit.get("dex_class_count")
    dex_classes = audit.get("dex_classes")
    if not isinstance(dex_file_count, int) or not isinstance(dex_class_count, int) or not isinstance(dex_classes, list):
        raise RuntimeEnvelopeError("APK audit omitted DEX runtime-envelope fields")
    if any(not isinstance(item, str) for item in dex_classes):
        raise RuntimeEnvelopeError("APK audit returned a malformed DEX class list")

    errors: list[str] = []
    if dex_file_count != expected_dex_files:
        errors.append(f"expected {expected_dex_files} DEX file(s), found {dex_file_count}")
    if dex_class_count > max_dex_classes:
        errors.append(f"expected at most {max_dex_classes} DEX classes, found {dex_class_count}")

    class_set = set(dex_classes)
    missing = sorted(set(required_classes) - class_set)
    if missing:
        errors.append("missing required RePlugin classes: " + ", ".join(missing))

    unexpected = sorted(
        item for item in dex_classes if not any(item.startswith(prefix) for prefix in allowed_prefixes)
    )
    if unexpected:
        sample = ", ".join(unexpected[:12])
        suffix = "" if len(unexpected) <= 12 else f" (+{len(unexpected) - 12} more)"
        errors.append("unexpected executable classes in theme APK: " + sample + suffix)

    if errors:
        raise RuntimeEnvelopeError("; ".join(errors))

    return {
        "dex_file_count": dex_file_count,
        "dex_class_count": dex_class_count,
        "allowed_class_prefixes": list(allowed_prefixes),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--config", type=Path, default=ROOT / "release-config.json")
    args = parser.parse_args()
    try:
        config = load_config(args.config.resolve())
        audit = audit_apk(args.apk.resolve())
        result = validate_runtime_envelope(audit, config)
    except (RuntimeEnvelopeError, ThemeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    print(
        "SUCCESS: minimal APK runtime envelope: "
        f"dex_files={result['dex_file_count']} dex_classes={result['dex_class_count']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
