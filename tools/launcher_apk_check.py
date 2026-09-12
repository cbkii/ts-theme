#!/usr/bin/env python3
"""Static runtime-envelope check for the standalone TS18 launcher APK."""

from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path

MAX_APK_BYTES = 2_500_000
FORBIDDEN_DEX_MARKERS = (
    b"Lkotlin/",
    b"Lkotlinx/",
    b"Landroidx/",
    b"Lcom/qihoo360/",
)
REQUIRED_DEX_MARKERS = (
    b"Lcom/cbkii/ts18launcher/LauncherActivity;",
    b"Lcom/cbkii/ts18launcher/AppDrawerActivity;",
    b"Lcom/cbkii/ts18launcher/SettingsActivity;",
    b"Lcom/cbkii/ts18launcher/MediaListenerService;",
    b"Lcom/cbkii/ts18launcher/platform/TopwayDesktopWindowMarkerService;",
    b"Lcom/cbkii/ts18launcher/platform/TopwayDesktopWindowProvider;",
)
REQUIRED_FILES = {
    "AndroidManifest.xml",
    "classes.dex",
    "assets/map/map.html",
    "assets/map/vendor/leaflet.js",
    "assets/map/vendor/leaflet.css",
    "assets/map/vendor/LEAFLET-LICENSE.txt",
    "assets/licenses/MATERIAL_SYMBOLS_NOTICE.txt",
    "assets/nav/nav-window.sh",
}


def fail(message: str) -> None:
    raise SystemExit(f"launcher envelope: FAIL: {message}")


def inspect(apk: Path) -> None:
    if not apk.is_file():
        fail(f"APK not found: {apk}")
    size = apk.stat().st_size
    if size <= 0:
        fail("APK is empty")
    if size > MAX_APK_BYTES:
        fail(f"APK grew beyond {MAX_APK_BYTES} bytes: {size}")

    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        name_set = set(names)
        missing = REQUIRED_FILES - name_set
        if missing:
            fail(f"missing required files: {', '.join(sorted(missing))}")

        dex_files = sorted(
            name for name in names
            if name.startswith("classes") and name.endswith(".dex")
        )
        if dex_files != ["classes.dex"]:
            fail(f"expected exactly one primary DEX, found: {dex_files}")

        native = sorted(
            name for name in names
            if name.startswith("lib/") and name.endswith(".so")
        )
        if native:
            fail(f"native libraries are not allowed: {native[:8]}")

        dex = archive.read("classes.dex")
        for marker in FORBIDDEN_DEX_MARKERS:
            if marker in dex:
                fail(f"forbidden runtime marker present in DEX: {marker!r}")
        for marker in REQUIRED_DEX_MARKERS:
            if marker not in dex:
                fail(f"required manifest component marker missing: {marker!r}")

    print(
        "launcher envelope: PASS "
        f"apk_bytes={size} dex=1 native=0 "
        "kotlin=0 androidx=0 replugin=0 leaflet_map_assets=present "
        "navigation_task_helper=present topway_home_compat=present "
        "material_symbols_notice=present"
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()
    inspect(args.apk)
    return 0


if __name__ == "__main__":
    sys.exit(main())
