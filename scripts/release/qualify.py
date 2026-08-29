#!/usr/bin/env python3
"""Qualify one exact signed APK and create its deterministic publication bundle."""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

SCRIPT_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(SCRIPT_ROOT))
from tools.ts18_theme import audit_apk  # noqa: E402
from release_lib import ALLOWED_VERSION_SOURCES, ReleaseError, SemVer, load_config, sha256_file, write_json  # noqa: E402
from publication_manifest import load_publication_manifest  # noqa: E402

HEX_64 = re.compile(r"(?i)(?<![0-9a-f])([0-9a-f](?:[: ]?[0-9a-f]){63})(?![0-9a-f])")

def run_checked(command: list[str], *, env: dict[str, str] | None = None) -> str:
    try:
        completed = subprocess.run(command, check=False, capture_output=True, text=True, timeout=120, env=env)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise ReleaseError(f"Command failed to execute: {command[0]}: {exc}") from exc
    output = completed.stdout + completed.stderr
    if completed.returncode != 0:
        raise ReleaseError(f"Command failed ({completed.returncode}): {command[0]}\n{output[-4000:]}")
    return output

def parse_aapt_badging(output: str) -> dict[str, int | str]:
    package = re.search(r"^package:\s+name='([^']+)'\s+versionCode='([^']+)'\s+versionName='([^']+)'", output, re.MULTILINE)
    min_sdk = re.search(r"^sdkVersion:'([0-9]+)'$", output, re.MULTILINE)
    target_sdk = re.search(r"^targetSdkVersion:'([0-9]+)'$", output, re.MULTILINE)
    compile_sdk = re.search(r"compileSdkVersion='([0-9]+)'", output)
    if not all((package, min_sdk, target_sdk, compile_sdk)):
        raise ReleaseError("aapt badging output omitted required package/SDK fields")
    try:
        return {"package": package.group(1), "version_code": int(package.group(2)), "version_name": package.group(3), "min_sdk": int(min_sdk.group(1)), "target_sdk": int(target_sdk.group(1)), "compile_sdk": int(compile_sdk.group(1))}
    except ValueError as exc:
        raise ReleaseError("aapt badging returned a non-numeric version/SDK field") from exc

def certificate_digests(output: str, *, marker: str) -> set[str]:
    result: set[str] = set()
    for line in output.splitlines():
        if marker.lower() not in line.lower(): continue
        for match in HEX_64.finditer(line): result.add(re.sub(r"[^0-9A-Fa-f]", "", match.group(1)).upper())
    return result

def one_certificate_digest(output: str, *, marker: str, source: str) -> str:
    digests = certificate_digests(output, marker=marker)
    if len(digests) != 1: raise ReleaseError(f"Expected one unambiguous SHA-256 certificate in {source}; found {len(digests)}")
    return next(iter(digests))
def require_signing_schemes(output: str) -> None:
    required = (r"(?im)^Verified using v1 scheme \(JAR signing\): true\s*$", r"(?im)^Verified using v2 scheme \(APK Signature Scheme v2\): true\s*$")
    if any(re.search(pattern, output) is None for pattern in required): raise ReleaseError("APK must verify with both v1 and v2 signing schemes")

def changelog_notes(source_root: Path, version_name: str, product_name: str) -> str:
    try: text = (source_root / "CHANGELOG.md").read_text(encoding="utf-8")
    except OSError as exc: raise ReleaseError(f"Unable to read CHANGELOG.md: {exc}") from exc
    heading = re.compile(rf"^##\s+(?:\[)?{re.escape(version_name)}(?:\])?(?:\s+-[^\n]*)?\s*$", re.MULTILINE)
    match = heading.search(text)
    if match:
        next_heading = re.search(r"^##\s+", text[match.end():], re.MULTILINE); end = match.end() + next_heading.start() if next_heading else len(text); body = text[match.end():end].strip()
    else:
        unreleased = re.search(r"^##\s+(?:\[)?Unreleased(?:\])?\s*$", text, re.MULTILINE)
        if unreleased:
            next_heading = re.search(r"^##\s+", text[unreleased.end():], re.MULTILINE); end = unreleased.end() + next_heading.start() if next_heading else len(text); body = text[unreleased.end():end].strip()
        else: body = "- Built and qualified from the selected source commit."
    if not body: body = "- Built and qualified from the selected source commit."
    return f"# {product_name} {version_name}\n\n{body}\n\n## Validation boundary\n\nThis APK was built once, signed, structurally inspected and checksum-qualified by the release workflow. DoFun discovery, theme application and runtime behaviour still require installation on the target TS18.\n"

def require_single_apk(expected_apk: Path) -> None:
    candidates = sorted(expected_apk.parent.glob("*.apk")) if expected_apk.parent.is_dir() else []
    if candidates != [expected_apk]:
        rendered = ", ".join(str(item.name) for item in candidates) or "none"
        raise ReleaseError("Release output directory must contain exactly the maintained APK; found: " + rendered)

def deterministic_install_zip(source_root: Path, output_dir: Path, config: dict[str, object], install_manifest: dict[str, object], tag: str) -> Path | None:
    inputs = config.get("install_tools")
    if inputs is None: return None
    if not isinstance(inputs, list) or not inputs or any(not isinstance(item, str) or not item for item in inputs):
        raise ReleaseError("install_tools configuration is malformed")
    manifest_path = output_dir / "install-manifest.json"; write_json(manifest_path, install_manifest)
    zip_path = output_dir / f"{config['asset_stem']}-{tag}-install-tools.zip"
    entries: list[tuple[str, bytes, int]] = []
    for relative in inputs:
        source = source_root / relative
        if not source.is_file(): raise ReleaseError(f"Install-tools input is missing from exact release source: {relative}")
        mode = 0o755 if relative.endswith(".sh") else 0o644
        entries.append((relative, source.read_bytes(), mode))
    entries.append(("install-manifest.json", manifest_path.read_bytes(), 0o644))
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, payload, mode in sorted(entries):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED; info.create_system = 3; info.external_attr = mode << 16
            archive.writestr(info, payload)
    return zip_path

def create_bundle(*, orchestration_root: Path, source_root: Path, apk: Path, plan_path: Path, output_dir: Path, build_tools: Path, keystore: Path, key_alias: str) -> dict[str, object]:
    config = load_config(source_root)
    try: plan = json.loads(plan_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc: raise ReleaseError(f"Invalid release plan: {exc}") from exc
    required_plan = {"mode", "tag", "version_name", "source_sha", "release_state", "replace_existing_assets", "version_code", "version_source"}
    if required_plan - set(plan): raise ReleaseError("Release plan is incomplete")
    parsed_tag = SemVer.from_tag(plan["tag"])
    if parsed_tag is None or plan["version_name"] != parsed_tag.version_name or plan["version_code"] != parsed_tag.android_version_code: raise ReleaseError("Tagged version and release plan disagree")
    if plan["version_source"] not in ALLOWED_VERSION_SOURCES: raise ReleaseError("Release plan version source is invalid")
    expected_apk = source_root / config["apk_path"]
    if apk.resolve() != expected_apk.resolve(): raise ReleaseError(f"APK path is not the maintained output: {apk}")
    require_single_apk(expected_apk)
    if output_dir.exists() and any(output_dir.iterdir()): raise ReleaseError(f"Qualified output directory is not empty: {output_dir}")
    output_dir.mkdir(parents=True, exist_ok=True)
    try:
        with zipfile.ZipFile(apk) as archive:
            if archive.testzip() is not None: raise ReleaseError("APK ZIP integrity test failed")
            names = set(archive.namelist())
    except zipfile.BadZipFile as exc: raise ReleaseError("Release output is not a valid APK ZIP") from exc
    missing_entries = sorted(set(config["required_apk_entries"]) - names)
    if missing_entries: raise ReleaseError(f"APK is missing required theme entries: {', '.join(missing_entries)}")
    native_abis = sorted({name.split("/",2)[1] for name in names if re.fullmatch(r"lib/[^/]+/[^/]+\.so", name)})
    if native_abis != sorted(config["native_abis"]): raise ReleaseError(f"APK native ABI set mismatch: expected {config['native_abis']}, got {native_abis}")
    aapt, apksigner, zipalign = build_tools / "aapt", build_tools / "apksigner", build_tools / "zipalign"
    for tool in (aapt, apksigner, zipalign):
        if not tool.is_file(): raise ReleaseError(f"Android Build Tools executable is missing: {tool}")
    badging = parse_aapt_badging(run_checked([str(aapt), "dump", "badging", str(apk)]))
    expected_badging = {"package":config["application_id"],"version_code":plan["version_code"],"version_name":plan["version_name"],"min_sdk":config["sdk"]["min"],"target_sdk":config["sdk"]["target"],"compile_sdk":config["sdk"]["compile"]}
    if badging != expected_badging: raise ReleaseError(f"APK package/version/SDK mismatch: expected {expected_badging}, got {badging}")
    signer_output = run_checked([str(apksigner), "verify", "--verbose", "--print-certs", str(apk)]); require_signing_schemes(signer_output)
    apk_signer = one_certificate_digest(signer_output, marker="certificate SHA-256 digest", source="apksigner output")
    keytool_env = os.environ.copy(); keytool_env["LC_ALL"] = "C"
    keystore_output = run_checked(["keytool","-list","-v","-keystore",str(keystore),"-alias",key_alias,"-storepass:env","KEYSTORE_PASSWORD"], env=keytool_env)
    expected_signer = one_certificate_digest(keystore_output, marker="SHA256:", source="keytool output")
    if apk_signer != expected_signer: raise ReleaseError("APK signer does not match the selected keystore alias")
    run_checked([str(zipalign), "-c", "-p", "4", str(apk)])
    audit = audit_apk(apk); metadata = {item["name"]: item["value"] for item in audit["manifest"]["metadata"]}
    if metadata.get("launcher.variety.theme.plugin") != config["plugin_id"]: raise ReleaseError("APK DoFun plug-in metadata does not match release configuration")
    missing_classes = sorted(set(config["required_dex_classes"]) - set(audit["dex_classes"]))
    if missing_classes: raise ReleaseError(f"APK is missing required RePlugin classes: {', '.join(missing_classes)}")
    public_name = f"{config['asset_stem']}-{plan['tag']}.apk"; public_apk = output_dir / public_name; shutil.copyfile(apk, public_apk); digest = sha256_file(public_apk); size = public_apk.stat().st_size
    checksum_name = f"{public_name}.sha256"; metadata_name = f"{public_name}.metadata.txt"
    (output_dir / checksum_name).write_text(f"{digest}  {public_name}\n", encoding="utf-8")
    (output_dir / metadata_name).write_text("\n".join((f"product={config['product_name']}",f"tag={plan['tag']}",f"version_name={plan['version_name']}",f"version_code={plan['version_code']}",f"package_id={config['application_id']}",f"plugin_id={config['plugin_id']}",f"source_sha={plan['source_sha']}",f"signer_sha256={apk_signer}",f"size_bytes={size}",f"sha256={digest}","")), encoding="utf-8")
    (output_dir / "release-notes.md").write_text(changelog_notes(source_root, plan["version_name"], config["product_name"]), encoding="utf-8"); shutil.copyfile(plan_path, output_dir / "release-plan.json")
    install_manifest = {"schema_version":1,"tag":plan["tag"],"version_name":plan["version_name"],"source_sha":plan["source_sha"],"application_id":config["application_id"],"plugin_id":config["plugin_id"],"apk_name":public_name,"apk_sha256":digest,"safe_area": load_json_file(source_root / config["layout_profile"]) if config.get("layout_profile") else None}
    installer_zip = deterministic_install_zip(source_root, output_dir, config, install_manifest, plan["tag"])
    roles = [(public_name,"installable_apk"),(checksum_name,"sha256_sidecar"),(metadata_name,"metadata_sidecar")]
    if installer_zip is not None: roles.append((installer_zip.name,"installer_tools"))
    assets = []
    for name, role in roles:
        path = output_dir / name; assets.append({"name":name,"role":role,"destination":"release","size":path.stat().st_size,"sha256":sha256_file(path)})
    manifest = {"schema_version":1,"product_name":config["product_name"],"tag":plan["tag"],"version_name":plan["version_name"],"version_code":plan["version_code"],"source_sha":plan["source_sha"],"package_id":config["application_id"],"plugin_id":config["plugin_id"],"signer_sha256":apk_signer,"release_mode":plan["mode"],"release_state":plan["release_state"],"replace_existing_assets":plan["replace_existing_assets"],"assets":assets}
    manifest_path = output_dir / "release-manifest.json"; write_json(manifest_path, manifest); load_publication_manifest(manifest_path); return manifest

def load_json_file(path: Path) -> object:
    try: return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc: raise ReleaseError(f"Invalid install layout profile: {path}: {exc}") from exc

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("--orchestration-root", type=Path, default=SCRIPT_ROOT); parser.add_argument("--source-root", type=Path, required=True); parser.add_argument("--apk", type=Path, required=True); parser.add_argument("--plan", type=Path, required=True); parser.add_argument("--output-dir", type=Path, required=True); parser.add_argument("--build-tools", type=Path, required=True); parser.add_argument("--keystore", type=Path, required=True); parser.add_argument("--key-alias", required=True); args = parser.parse_args()
    try:
        manifest = create_bundle(orchestration_root=args.orchestration_root.resolve(), source_root=args.source_root.resolve(), apk=args.apk.resolve(), plan_path=args.plan.resolve(), output_dir=args.output_dir.resolve(), build_tools=args.build_tools.resolve(), keystore=args.keystore.resolve(), key_alias=args.key_alias)
        print(f"SUCCESS: qualified {len(manifest['assets'])} immutable public assets for {manifest['tag']}"); return 0
    except ReleaseError as exc: print(f"ERROR: {exc}", file=sys.stderr); return 1
if __name__ == "__main__": raise SystemExit(main())
