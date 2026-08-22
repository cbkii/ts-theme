#!/usr/bin/env python3
"""Resolve one create/repair transaction without mutating GitHub."""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

from release_lib import (
    GitHubClient,
    ReleaseError,
    load_config,
    resolve_plan,
    source_version,
    write_json,
)


def parse_bool(value: str) -> bool:
    lowered = value.lower()
    if lowered not in {"true", "false"}:
        raise argparse.ArgumentTypeError("expected true or false")
    return lowered == "true"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--mode", required=True)
    parser.add_argument("--requested-tag", required=True)
    parser.add_argument("--state", required=True)
    parser.add_argument("--replace-existing", required=True, type=parse_bool)
    parser.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY", ""))
    parser.add_argument("--source-sha", default=os.environ.get("GITHUB_SHA", ""))
    parser.add_argument("--output", type=Path, default=Path("release-plan.json"))
    args = parser.parse_args()
    try:
        root = args.root.resolve()
        config = load_config(root)
        version_name, _ = source_version(root, config)
        client = GitHubClient(os.environ.get("GITHUB_TOKEN", ""), args.repository)
        plan = resolve_plan(
            mode=args.mode,
            requested_tag=args.requested_tag,
            requested_state=args.state,
            replace_existing=args.replace_existing,
            current_source_sha=args.source_sha,
            current_version_name=version_name,
            snapshot=client.snapshot(),
        )
        write_json(args.output.resolve(), plan)
        print(
            f"SUCCESS: {plan['mode']} {plan['tag']} at {plan['source_sha']} "
            f"from {plan['observed_remote_state']} ({plan['version_source']})"
        )
        return 0
    except ReleaseError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
