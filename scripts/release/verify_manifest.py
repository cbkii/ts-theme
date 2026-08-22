#!/usr/bin/env python3
"""Verify that a qualified publication bundle is closed and byte-exact."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from release_lib import ReleaseError, load_manifest


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()
    try:
        manifest = load_manifest(args.manifest.resolve())
        print(f"SUCCESS: verified {len(manifest['assets'])} manifest-approved assets")
        return 0
    except ReleaseError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
