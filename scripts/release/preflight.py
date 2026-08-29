#!/usr/bin/env python3
"""Inventory and preserve existing remote assets before any publication mutation."""
from __future__ import annotations
import argparse, json, os, sys
from pathlib import Path
from publication_manifest import load_publication_manifest
from release_lib import GitHubClient, ReleaseError, classify_assets, safe_asset_name, validate_remote_for_plan, write_json

def load_plan(path: Path) -> dict[str, object]:
    try: return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc: raise ReleaseError(f"Invalid release plan: {exc}") from exc

def main() -> int:
    parser=argparse.ArgumentParser(description=__doc__); parser.add_argument("--manifest",type=Path,required=True); parser.add_argument("--plan",type=Path,required=True); parser.add_argument("--output-dir",type=Path,required=True); parser.add_argument("--repository",default=os.environ.get("GITHUB_REPOSITORY","")); args=parser.parse_args()
    try:
        manifest_path=args.manifest.resolve(); manifest=load_publication_manifest(manifest_path); plan=load_plan(args.plan.resolve())
        for key in ("tag","source_sha","mode","release_state","replace_existing_assets"):
            manifest_key={"mode":"release_mode","release_state":"release_state","replace_existing_assets":"replace_existing_assets"}.get(key,key)
            if manifest.get(manifest_key)!=plan.get(key): raise ReleaseError(f"Release plan and publication manifest disagree on {key}")
        output_dir=args.output_dir.resolve()
        if output_dir.exists() and any(output_dir.iterdir()): raise ReleaseError(f"Preflight output directory is not empty: {output_dir}")
        output_dir.mkdir(parents=True,exist_ok=True); client=GitHubClient(os.environ.get("GITHUB_TOKEN",""),args.repository); _,release=validate_remote_for_plan(plan,client.snapshot()); remote=[]
        if release is not None:
            expected={item["name"] for item in manifest["assets"]}
            for item in client.list_assets(release.release_id):
                record={"id":int(item["id"]),"name":item["name"],"state":item.get("state"),"size":item.get("size"),"digest":item.get("digest"),"updated_at":item.get("updated_at"),"download_sha256":None}
                if item["name"] in expected:
                    if not safe_asset_name(item["name"]): raise ReleaseError(f"Unsafe remote asset name: {item['name']!r}")
                    recovery=output_dir/"recovery"/f"{item['id']}-{item['name']}"; record["download_sha256"]=client.download_asset(int(item["id"]),recovery); record["recovery_file"]=recovery.relative_to(output_dir).as_posix()
                remote.append(record)
        decision=classify_assets(expected_assets=manifest["assets"],remote_assets=remote,mode=plan["mode"],release_published=release.published if release else False,replace_existing=bool(plan["replace_existing_assets"]))
        write_json(output_dir/"publication-preflight.json",{"schema_version":1,"tag":plan["tag"],"source_sha":plan["source_sha"],"release_id":release.release_id if release else None,"release_state":release.state if release else "absent","remote_assets":remote,"decision":decision})
        print(f"SUCCESS: publication preflight reuse={len(decision['reuse'])} upload={len(decision['upload'])} replace={len(decision['replace'])} preserve={len(decision['preserve'])}"); return 0
    except ReleaseError as exc: print(f"ERROR: {exc}",file=sys.stderr); return 1
if __name__=="__main__": raise SystemExit(main())
