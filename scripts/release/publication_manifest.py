#!/usr/bin/env python3
"""Validation for the four-asset TS18 publication manifest."""
from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from release_lib import ReleaseError, SHA1, SHA256, SemVer, safe_asset_name, sha256_file

EXPECTED_ROLES = {"installable_apk", "sha256_sidecar", "metadata_sidecar", "installer_tools"}


def load_publication_manifest(path: Path, verify_files: bool = True) -> dict[str, Any]:
    try:
        manifest = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ReleaseError(f"Invalid publication manifest: {exc}") from exc
    required = {"schema_version", "product_name", "tag", "version_name", "version_code", "source_sha", "package_id", "plugin_id", "signer_sha256", "release_mode", "release_state", "replace_existing_assets", "assets"}
    missing = sorted(required - set(manifest))
    if missing:
        raise ReleaseError(f"Publication manifest is missing: {', '.join(missing)}")
    parsed = SemVer.from_tag(str(manifest["tag"]))
    if manifest["schema_version"] != 1 or parsed is None:
        raise ReleaseError("Publication manifest schema or tag is invalid")
    if manifest["version_name"] != parsed.version_name or manifest["version_code"] != parsed.android_version_code:
        raise ReleaseError("Publication manifest version authority disagrees with its tag")
    if not SHA1.fullmatch(str(manifest["source_sha"])):
        raise ReleaseError("Publication manifest source SHA is invalid")
    if not isinstance(manifest["replace_existing_assets"], bool):
        raise ReleaseError("Publication manifest replacement authority is invalid")
    if not isinstance(manifest["assets"], list) or not manifest["assets"]:
        raise ReleaseError("Publication manifest assets are missing")
    names: set[str] = set(); roles: set[str] = set()
    for item in manifest["assets"]:
        name = item.get("name"); role = item.get("role")
        if not isinstance(name, str) or not safe_asset_name(name) or name in names:
            raise ReleaseError(f"Unsafe or duplicate publication asset name: {name!r}")
        if role not in EXPECTED_ROLES or role in roles:
            raise ReleaseError(f"Unsupported or duplicate publication asset role: {role!r}")
        if item.get("destination") != "release":
            raise ReleaseError(f"Unsupported asset destination for {name}")
        if not isinstance(item.get("size"), int) or item["size"] < 0:
            raise ReleaseError(f"Invalid asset size for {name}")
        if not isinstance(item.get("sha256"), str) or not SHA256.fullmatch(item["sha256"]):
            raise ReleaseError(f"Invalid asset SHA-256 for {name}")
        names.add(name); roles.add(role)
        if verify_files:
            file_path = path.parent / name
            if not file_path.is_file() or file_path.stat().st_size != item["size"] or sha256_file(file_path) != item["sha256"]:
                raise ReleaseError(f"Publication asset changed or is missing: {name}")
    if roles != EXPECTED_ROLES:
        raise ReleaseError("Publication manifest does not contain the exact four maintained asset roles")
    if verify_files:
        allowed = names | {path.name, "release-notes.md", "release-plan.json", "install-manifest.json"}
        actual = {item.name for item in path.parent.iterdir() if item.is_file()}
        unexpected = sorted(actual - allowed)
        if unexpected:
            raise ReleaseError(f"Unplanned files in qualified bundle: {', '.join(unexpected)}")
    return manifest
