#!/usr/bin/env python3
"""Verify the isolated legacy-D8 DoFun compatibility APK."""
from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from pathlib import Path

from ts18_theme import (
    ThemeError,
    dex_classes,
    inspect_zip,
    manifest_summary,
    parse_binary_manifest,
)

APPLICATION_ID = "launcher.variety.theme.plugin.sfp_cbk_black"
PLUGIN_ID = "sfp_cbk_black"
EXPECTED_D8 = "1.4.77"
EXPECTED_LIBRARY_CLASSES = {
    "Llibrary/a;",
    "Llibrary/a$a;",
    "Llibrary/b;",
    "Llibrary/c;",
    "Llibrary/d;",
    "Llibrary/d$1;",
    "Llibrary/e;",
    "Llibrary/f;",
}
FORBIDDEN_MODERN_LIBRARY_MARKERS = (
    "$$ExternalSyntheticApiModelOutline",
    "Llibrary/e$1;",
    "Llibrary/g;",
)
EXPECTED_PERMISSIONS = {
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.READ_PHONE_STATE",
    "android.permission.READ_EXTERNAL_STORAGE",
}


def fail(message: str) -> None:
    raise ThemeError(message)


def d8_metadata(dex: bytes) -> dict[str, object]:
    match = re.search(rb"~~D8\{[^}\x00]{1,1024}\}", dex)
    if not match:
        fail("DEX does not expose a D8 metadata marker")
    try:
        return json.loads(match.group(0)[4:].decode("ascii"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise ThemeError(f"Invalid D8 metadata marker: {exc}") from exc


def verify(apk: Path, expected_version_code: int, expected_timestamp: str) -> dict[str, object]:
    infos = inspect_zip(apk)
    with zipfile.ZipFile(apk) as archive:
        names = {item.filename for item in infos}
        dex_names = sorted(name for name in names if re.fullmatch(r"classes(?:[2-9][0-9]*)?\.dex", name))
        if dex_names != ["classes.dex"]:
            fail(f"Compatibility APK must contain exactly classes.dex, found {dex_names}")
        if any(name.startswith("lib/") for name in names):
            fail("Compatibility APK unexpectedly contains native libraries")
        if "assets/.gen/c.json" in names or any(name.startswith("assets/layout-") for name in names):
            fail("Minimal Fyd18-style carrier must not contain .gen or layout-* compatibility mirrors")

        elements = parse_binary_manifest(archive.read("AndroidManifest.xml"))
        summary = manifest_summary(elements)
        if summary.get("package") != APPLICATION_ID:
            fail(f"Unexpected package: {summary.get('package')}")
        if summary.get("version_code") != expected_version_code:
            fail(f"Unexpected versionCode: {summary.get('version_code')}")
        expected_version_name = f"{expected_version_code}_{expected_timestamp}.land"
        if summary.get("version_name") != expected_version_name:
            fail(f"Unexpected versionName: {summary.get('version_name')!r}; expected {expected_version_name!r}")
        if (summary.get("min_sdk"), summary.get("target_sdk"), summary.get("compile_sdk")) != (16, 26, 29):
            fail(f"Unexpected SDK envelope: {summary}")
        if set(summary.get("permissions", [])) != EXPECTED_PERMISSIONS:
            fail(f"Unexpected permission set: {summary.get('permissions')}")
        metadata = {item.get("name"): item.get("value") for item in summary.get("metadata", [])}
        if metadata.get("launcher.variety.theme.plugin") != PLUGIN_ID:
            fail(f"Unexpected plug-in metadata: {metadata}")
        if metadata.get("support_systemui") is not False or metadata.get("support_systemui_night") is not False:
            fail(f"Compatibility manifest must not claim SystemUI ownership: {metadata}")
        if metadata.get("support_night") is not True:
            fail(f"Compatibility manifest lost night support: {metadata}")

        app = next((item["attributes"] for item in elements if item["name"] == "application"), {})
        if app.get("icon") != "@0x01080013":
            fail(f"Compatibility manifest does not use the framework default app icon: {app.get('icon')}")
        if app.get("supportsRtl") is not True:
            fail("Compatibility manifest must set supportsRtl=true")

        import_info = json.loads(archive.read("assets/import_theme_info_config.json").decode("utf-8"))
        if import_info.get("themeId") != "20260822000100" or import_info.get("oemId") != "1132":
            fail(f"Unexpected import identity metadata: {import_info}")
        if not re.fullmatch(r"[0-9]{14}", str(import_info.get("themeId", ""))):
            fail("Compatibility themeId must remain a stable 14-digit timestamp-form identifier")

        dex = archive.read("classes.dex")
        classes = set(dex_classes(dex))
        if "Lcom/qihoo360/replugin/Entry;" not in classes:
            fail("RePlugin Entry class is missing")
        if "Llauncher/variety/theme/plugin/BuildConfig;" not in classes:
            fail("Legacy carrier must contain the land BuildConfig class")
        missing_library = sorted(EXPECTED_LIBRARY_CLASSES - classes)
        if missing_library:
            fail("Legacy RePlugin library topology is incomplete: " + ", ".join(missing_library))
        forbidden = sorted(
            cls for cls in classes if any(marker in cls for marker in FORBIDDEN_MODERN_LIBRARY_MARKERS)
        )
        if forbidden:
            fail("Modern D8 synthetic/library topology leaked into compatibility APK: " + ", ".join(forbidden))
        if len(classes) > 64:
            fail(f"Compatibility APK exceeds the 64-class envelope: {len(classes)}")

        marker = d8_metadata(dex)
        if marker.get("version") != EXPECTED_D8:
            fail(f"Compatibility D8 version is {marker.get('version')!r}; expected {EXPECTED_D8}")
        if marker.get("compilation-mode") != "release" or marker.get("min-api") != 16:
            fail(f"Unexpected D8 compilation metadata: {marker}")

    return {
        "apk": str(apk),
        "version_code": expected_version_code,
        "version_name": f"{expected_version_code}_{expected_timestamp}.land",
        "dex_files": 1,
        "dex_classes": len(classes),
        "d8": marker,
        "theme_id": import_info["themeId"],
        "oem_id": import_info["oemId"],
        "legacy_library_classes": sorted(EXPECTED_LIBRARY_CLASSES),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--expected-version-code", required=True, type=int)
    parser.add_argument("--expected-timestamp", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9]{12}", args.expected_timestamp):
        print("ERROR: --expected-timestamp must be YYMMDDHHMMSS", file=sys.stderr)
        return 2
    try:
        result = verify(args.apk.resolve(), args.expected_version_code, args.expected_timestamp)
    except (ThemeError, OSError, KeyError, json.JSONDecodeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
