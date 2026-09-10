#!/usr/bin/env python3
"""Fetch the pinned Leaflet runtime assets used by the standalone launcher map."""

from __future__ import annotations

import hashlib
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "launcher" / "src" / "main" / "assets" / "map" / "vendor"
USER_AGENT = "cbkii-ts-theme-build/1 (+https://github.com/cbkii/ts-theme)"
VERSION = "1.9.4"
ASSETS = {
    "leaflet.js": {
        "sha256": "db49d009c841f5ca34a888c96511ae936fd9f5533e90d8b2c4d57596f4e5641a",
        "urls": (
            f"https://unpkg.com/leaflet@{VERSION}/dist/leaflet.js",
            f"https://cdn.jsdelivr.net/npm/leaflet@{VERSION}/dist/leaflet.js",
        ),
    },
    "leaflet.css": {
        "sha256": "a7837102824184820dfa198d1ebcd109ff6d0ff9a2672a074b9a1b4d147d04c6",
        "urls": (
            f"https://unpkg.com/leaflet@{VERSION}/dist/leaflet.css",
            f"https://cdn.jsdelivr.net/npm/leaflet@{VERSION}/dist/leaflet.css",
        ),
    },
}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def fetch_one(name: str, spec: dict[str, object]) -> None:
    expected = str(spec["sha256"])
    destination = DEST / name
    if destination.is_file():
        existing = destination.read_bytes()
        if digest(existing) == expected:
            print(f"Leaflet {VERSION}: {name} already verified ({len(existing)} bytes)")
            return
        destination.unlink()

    last_error = ""
    data: bytes | None = None
    for url in spec["urls"]:
        try:
            request = urllib.request.Request(
                str(url),
                headers={"User-Agent": USER_AGENT, "Accept": "*/*"},
            )
            with urllib.request.urlopen(request, timeout=30) as response:
                candidate = response.read(750_000)
            if not candidate or len(candidate) >= 750_000:
                raise RuntimeError("unexpected asset size")
            actual = digest(candidate)
            if actual != expected:
                raise RuntimeError(
                    f"SHA-256 mismatch: expected {expected}, got {actual}"
                )
            data = candidate
            break
        except (OSError, RuntimeError, urllib.error.URLError) as exc:
            last_error = f"{type(exc).__name__}: {exc}"

    if data is None:
        raise SystemExit(f"Leaflet {VERSION}: failed to fetch {name}: {last_error}")

    DEST.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + ".tmp")
    temporary.write_bytes(data)
    temporary.replace(destination)
    print(f"Leaflet {VERSION}: fetched {name} ({len(data)} bytes, sha256={expected})")


def main() -> int:
    for name, spec in ASSETS.items():
        fetch_one(name, spec)
    return 0


if __name__ == "__main__":
    sys.exit(main())
