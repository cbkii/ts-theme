#!/usr/bin/env python3
"""Fetch the exact open-source RePlugin compatibility AAR with fail-closed integrity checks."""

from __future__ import annotations

import argparse
import hashlib
import os
import tempfile
import urllib.request
from pathlib import Path


VERSION = "2.3.4"
FILENAME = f"replugin-plugin-lib-{VERSION}.aar"
URL = (
    "http://maven.geelib.360.cn/nexus/repository/replugin/"
    f"com/qihoo360/replugin/replugin-plugin-lib/{VERSION}/{FILENAME}"
)
SHA256 = "0c3132e90dc372056bd9601788ee67a1c97fb64d15f6074826825addadf6a89f"
MAX_BYTES = 2 * 1024 * 1024


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fetch(destination: Path) -> str:
    if destination.is_file() and sha256_file(destination) == SHA256:
        return "cached"

    destination.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(URL, headers={"User-Agent": "ts-theme-build/1"})
    fd, temporary_name = tempfile.mkstemp(
        prefix=f".{destination.name}.", suffix=".tmp", dir=destination.parent
    )
    os.close(fd)
    temporary = Path(temporary_name)
    try:
        size = 0
        with urllib.request.urlopen(request, timeout=30) as response, temporary.open("wb") as output:
            while chunk := response.read(64 * 1024):
                size += len(chunk)
                if size > MAX_BYTES:
                    raise RuntimeError("RePlugin response exceeded the 2 MiB safety limit")
                output.write(chunk)
        actual = sha256_file(temporary)
        if actual != SHA256:
            raise RuntimeError(f"RePlugin SHA-256 mismatch: expected {SHA256}, got {actual}")
        os.replace(temporary, destination)
        return "downloaded"
    finally:
        temporary.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("build/dependencies") / FILENAME,
    )
    args = parser.parse_args()
    result = fetch(args.output.resolve())
    print(f"SUCCESS: RePlugin {VERSION} {result}; SHA-256 {SHA256}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
