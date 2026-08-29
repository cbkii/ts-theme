#!/usr/bin/env python3
from __future__ import annotations
import sys
from pathlib import Path
from publication_manifest import load_publication_manifest
from release_lib import ReleaseError

if __name__ == "__main__":
    try:
        load_publication_manifest(Path(sys.argv[1]).resolve())
        print("SUCCESS: qualified publication manifest and four assets verified")
    except (IndexError, ReleaseError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
