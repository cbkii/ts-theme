#!/usr/bin/env python3
"""Execute a draft-first, create-or-repair GitHub Release transaction."""
from __future__ import annotations
import argparse, json, os, sys, time
from pathlib import Path
from publication_manifest import load_publication_manifest
from release_lib import GitHubClient, ReleaseError, classify_assets, validate_remote_for_plan

def read_json(path: Path,label:str)->dict[str,object]:
    try: value=json.loads(path.read_text(encoding="utf-8"))
    except (OSError,UnicodeError,json.JSONDecodeError) as exc: raise ReleaseError(f"Invalid {label}: {exc}") from exc
    if not isinstance(value,dict): raise ReleaseError(f"{label} is not a JSON object")
    return value

def require_same_transaction(manifest:dict[str,object],plan:dict[str,object],preflight:dict[str,object])->None:
    pairs=((manifest.get("tag"),plan.get("tag"),"tag"),(manifest.get("source_sha"),plan.get("source_sha"),"source SHA"),(manifest.get("release_mode"),plan.get("mode"),"mode"),(manifest.get("release_state"),plan.get("release_state"),"release state"),(manifest.get("replace_existing_assets"),plan.get("replace_existing_assets"),"replacement authority"),(preflight.get("tag"),plan.get("tag"),"preflight tag"),(preflight.get("source_sha"),plan.get("source_sha"),"preflight source SHA"))
    for left,right,label in pairs:
        if left!=right: raise ReleaseError(f"Transaction documents disagree on {label}")
def attach_preflight_hashes(fresh:list[dict[str,object]],previous:list[dict[str,object]])->list[dict[str,object]]:
    prior={int(item["id"]):item for item in previous}; result=[]
    for item in fresh:
        record=dict(item); old=prior.get(int(item["id"]))
        if old is not None:
            for field in ("name","state","size","digest","updated_at"):
                if old.get(field)!=item.get(field): raise ReleaseError(f"Remote asset changed after preflight: {item.get('name')}")
            record["download_sha256"]=old.get("download_sha256")
        result.append(record)
    return result
def verified_remote_assets(client:GitHubClient,release_id:int,manifest:dict[str,object],mode:str,output_dir:Path)->list[dict[str,object]]:
    expected={item["name"]:item for item in manifest["assets"]}; inventory=[]
    for attempt in range(5):
        inventory=client.list_assets(release_id); by_name={item["name"]:item for item in inventory}; ready=all(name in by_name and by_name[name].get("state")=="uploaded" and by_name[name].get("size")==spec["size"] for name,spec in expected.items())
        if ready: break
        if attempt==4: raise ReleaseError("Remote assets did not reach the uploaded state with expected sizes")
        time.sleep(2)
    names=[item["name"] for item in inventory]
    if len(names)!=len(set(names)): raise ReleaseError("Remote Release contains duplicate asset names")
    unexpected=sorted(set(names)-set(expected))
    if mode=="create_new_release" and unexpected: raise ReleaseError(f"New Release contains unplanned assets: {', '.join(unexpected)}")
    by_name={item["name"]:item for item in inventory}; output_dir.mkdir(parents=True,exist_ok=True); verified=[]
    for name,spec in expected.items():
        item=by_name[name]; digest=item.get("digest")
        if digest and digest!=f"sha256:{spec['sha256']}": raise ReleaseError(f"GitHub-reported digest mismatch for {name}")
        downloaded=output_dir/f"{item['id']}-{name}"; actual=client.download_asset(int(item["id"]),downloaded)
        if actual!=spec["sha256"]: raise ReleaseError(f"Downloaded remote bytes differ for {name}")
        verified.append({"id":int(item["id"]),"name":name,"state":item.get("state"),"size":item.get("size"),"digest":digest,"download_sha256":actual})
    return verified
def publish_transaction(*,client:GitHubClient,manifest_path:Path,manifest:dict[str,object],plan:dict[str,object],preflight:dict[str,object],verify_dir:Path,notes:str)->None:
    require_same_transaction(manifest,plan,preflight); release_name=f"{manifest['product_name']} {manifest['tag']}"; _,release=validate_remote_for_plan(plan,client.snapshot())
    if (release.release_id if release else None)!=preflight.get("release_id"): raise ReleaseError("Remote Release identity changed after preflight")
    if (release.state if release else "absent")!=preflight.get("release_state"): raise ReleaseError("Remote Release state changed after preflight")
    if plan["mode"]=="create_new_release":
        client.create_tag(plan["tag"],plan["source_sha"]); created=client.create_draft_release(plan["tag"],plan["source_sha"],release_name,notes); release_id=int(created["id"]); release_published=False; fresh=[]
    else:
        if release is None:
            created=client.create_draft_release(plan["tag"],plan["source_sha"],release_name,notes); release_id=int(created["id"]); release_published=False; fresh=[]
        else:
            release_id=release.release_id; release_published=release.published
            if release.state=="stable" and plan["release_state"]!="stable": raise ReleaseError("A stable Release cannot be demoted")
            if release.state=="prerelease" and plan["release_state"]=="draft": raise ReleaseError("A published prerelease cannot be returned to draft")
            fresh=attach_preflight_hashes(client.list_assets(release_id),preflight.get("remote_assets",[]))
    decision=classify_assets(expected_assets=manifest["assets"],remote_assets=fresh,mode=plan["mode"],release_published=release_published,replace_existing=bool(plan["replace_existing_assets"])); remote_by_name={item["name"]:item for item in fresh}
    for name in decision["replace"]: client.delete_asset(int(remote_by_name[name]["id"]))
    for name in decision["upload"]+decision["replace"]: client.upload_asset(release_id,name,manifest_path.parent/name)
    verified_remote_assets(client,release_id,manifest,plan["mode"],verify_dir)
    if not release_published or (release is not None and release.state!=plan["release_state"]): client.update_release_state(release_id,plan["release_state"],name=release_name,body=notes)
def main()->int:
    parser=argparse.ArgumentParser(description=__doc__); parser.add_argument("--manifest",type=Path,required=True); parser.add_argument("--plan",type=Path,required=True); parser.add_argument("--preflight",type=Path,required=True); parser.add_argument("--verify-dir",type=Path,required=True); parser.add_argument("--repository",default=os.environ.get("GITHUB_REPOSITORY","")); args=parser.parse_args()
    try:
        manifest_path=args.manifest.resolve(); manifest=load_publication_manifest(manifest_path); plan=read_json(args.plan.resolve(),"release plan"); preflight=read_json(args.preflight.resolve(),"publication preflight"); notes=(manifest_path.parent/"release-notes.md").read_text(encoding="utf-8"); client=GitHubClient(os.environ.get("GITHUB_TOKEN",""),args.repository); publish_transaction(client=client,manifest_path=manifest_path,manifest=manifest,plan=plan,preflight=preflight,verify_dir=args.verify_dir.resolve(),notes=notes); print(f"SUCCESS: remotely verified {len(manifest['assets'])} assets for {manifest['tag']} with final state {plan['release_state']}"); return 0
    except (OSError,ReleaseError) as exc:
        print(f"ERROR: {exc}",file=sys.stderr); print("The valid tag is never moved or deleted. If a tag/draft now exists, rerun with repair_existing_release after inspecting the draft.",file=sys.stderr); return 1
if __name__=="__main__": raise SystemExit(main())
