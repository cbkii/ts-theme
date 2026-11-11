#!/usr/bin/env python3
"""Validate the standalone TS18 launcher repository and audit APK interoperability evidence."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import struct
import subprocess
import sys
import zipfile
from pathlib import Path, PurePosixPath
from typing import Any, Iterable
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[1]
LAUNCHER_ROOT = ROOT / "launcher"
LAUNCHER_PACKAGE = "com.cbkii.ts18launcher"
# Identity of the retired DoFun/RePlugin theme. It must never be built from this repository again.
RETIRED_THEME_PACKAGE = "launcher.variety.theme.plugin.sfp_cbk_black"
MAX_APK_SIZE = 64 * 1024 * 1024
MAX_ENTRY_SIZE = 32 * 1024 * 1024
MAX_EXPANDED_SIZE = 128 * 1024 * 1024
MAX_ENTRIES = 10_000

class ThemeError(RuntimeError):
    pass
class ValidationError(ThemeError):
    pass
class SafetyStop(ThemeError):
    pass

def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

def safe_member_name(name: str) -> bool:
    path = PurePosixPath(name)
    return bool(name) and not name.startswith(("/", "\\")) and "\\" not in name and not any(part in ("", ".", "..") for part in path.parts) and not (path.parts and ":" in path.parts[0])

def inspect_zip(apk: Path) -> list[zipfile.ZipInfo]:
    if not apk.is_file():
        raise ValidationError(f"APK not found: {apk}")
    size = apk.stat().st_size
    if size <= 0 or size > MAX_APK_SIZE:
        raise SafetyStop(f"APK size outside audited bounds: {size} bytes")
    try:
        with zipfile.ZipFile(apk) as archive:
            infos = archive.infolist()
            if not infos or len(infos) > MAX_ENTRIES:
                raise SafetyStop(f"APK entry count outside bounds: {len(infos)}")
            seen: set[str] = set(); expanded = 0
            for info in infos:
                if not safe_member_name(info.filename):
                    raise SafetyStop(f"Unsafe APK entry: {info.filename!r}")
                if info.filename in seen:
                    raise SafetyStop(f"Duplicate APK entry: {info.filename}")
                seen.add(info.filename); expanded += info.file_size
                if info.file_size > MAX_ENTRY_SIZE:
                    raise SafetyStop(f"Oversized APK entry: {info.filename}")
                if expanded > MAX_EXPANDED_SIZE:
                    raise SafetyStop("APK expanded size exceeds audited bound")
            if "AndroidManifest.xml" not in seen or not any(re.fullmatch(r"classes(?:[2-9][0-9]*)?\.dex", n) for n in seen):
                raise ValidationError("APK is missing AndroidManifest.xml or DEX code")
            return infos
    except zipfile.BadZipFile as exc:
        raise ValidationError(f"Invalid APK ZIP: {apk}") from exc

def _u16(data: bytes, offset: int) -> int:
    return struct.unpack_from("<H", data, offset)[0]
def _u32(data: bytes, offset: int) -> int:
    return struct.unpack_from("<I", data, offset)[0]
def _length8(data: bytes, offset: int) -> tuple[int, int]:
    value = data[offset]; offset += 1
    if value & 0x80:
        value = ((value & 0x7F) << 8) | data[offset]; offset += 1
    return value, offset
def _length16(data: bytes, offset: int) -> tuple[int, int]:
    value = _u16(data, offset); offset += 2
    if value & 0x8000:
        value = ((value & 0x7FFF) << 16) | _u16(data, offset); offset += 2
    return value, offset

def parse_binary_manifest(data: bytes) -> list[dict[str, Any]]:
    if len(data) < 8 or _u16(data, 0) != 0x0003:
        raise ValidationError("Manifest is not Android binary XML")
    offset = 8; strings: list[str] = []; elements: list[dict[str, Any]] = []
    while offset < len(data):
        if offset + 8 > len(data):
            raise ValidationError("Truncated binary XML chunk")
        chunk_type = _u16(data, offset); header_size = _u16(data, offset + 2); chunk_size = _u32(data, offset + 4)
        if header_size < 8 or chunk_size < header_size or offset + chunk_size > len(data):
            raise ValidationError("Invalid binary XML chunk bounds")
        if chunk_type == 0x0001:
            count = _u32(data, offset + 8); flags = _u32(data, offset + 16); strings_start = _u32(data, offset + 20); utf8 = bool(flags & 0x100)
            if count > 100_000:
                raise SafetyStop("Manifest string pool is unreasonably large")
            strings = []
            for index in range(count):
                relative = _u32(data, offset + header_size + index * 4); cursor = offset + strings_start + relative
                if utf8:
                    _, cursor = _length8(data, cursor); byte_length, cursor = _length8(data, cursor); value = data[cursor:cursor + byte_length].decode("utf-8", "strict")
                else:
                    char_length, cursor = _length16(data, cursor); value = data[cursor:cursor + char_length * 2].decode("utf-16le", "strict")
                strings.append(value)
        elif chunk_type == 0x0102:
            if not strings:
                raise ValidationError("Manifest element precedes string pool")
            name_index = _u32(data, offset + 20); attribute_start = _u16(data, offset + 24); attribute_size = _u16(data, offset + 26); attribute_count = _u16(data, offset + 28)
            if attribute_size < 20 or attribute_count > 4096:
                raise ValidationError("Invalid binary XML attribute table")
            attributes: dict[str, Any] = {}; base = offset + 16 + attribute_start
            for index in range(attribute_count):
                cursor = base + index * attribute_size
                if cursor + 20 > offset + chunk_size:
                    raise ValidationError("Manifest attribute exceeds chunk")
                attr_name_index = _u32(data, cursor + 4); raw_index = _u32(data, cursor + 8); data_type = data[cursor + 15]; typed_data = _u32(data, cursor + 16); attr_name = strings[attr_name_index]
                if raw_index != 0xFFFFFFFF: value: Any = strings[raw_index]
                elif data_type == 0x03: value = strings[typed_data]
                elif data_type == 0x12: value = bool(typed_data)
                elif data_type in (0x10, 0x11): value = typed_data
                elif data_type == 0x01: value = f"@0x{typed_data:08x}"
                else: value = {"type": data_type, "data": typed_data}
                attributes[attr_name] = value
            elements.append({"name": strings[name_index], "attributes": attributes})
        offset += chunk_size
    if not elements or elements[0]["name"] != "manifest":
        raise ValidationError("No manifest start element found")
    return elements

def manifest_summary(elements: list[dict[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}; permissions: list[str] = []; metadata: list[dict[str, Any]] = []
    for element in elements:
        name = element["name"]; attrs = element["attributes"]
        if name == "manifest": result.update(package=attrs.get("package"), version_code=attrs.get("versionCode"), version_name=attrs.get("versionName"), compile_sdk=attrs.get("compileSdkVersion"))
        elif name == "uses-sdk": result["min_sdk"] = attrs.get("minSdkVersion"); result["target_sdk"] = attrs.get("targetSdkVersion")
        elif name == "uses-permission": permissions.append(str(attrs.get("name")))
        elif name == "meta-data": metadata.append({"name": attrs.get("name"), "value": attrs.get("value")})
    result["permissions"] = permissions; result["metadata"] = metadata; return result

def _uleb128(data: bytes, offset: int) -> tuple[int, int]:
    value = 0; shift = 0
    for _ in range(5):
        current = data[offset]; offset += 1; value |= (current & 0x7F) << shift
        if current < 0x80: return value, offset
        shift += 7
    raise ValidationError("Invalid DEX ULEB128")
def dex_classes(data: bytes) -> list[str]:
    if len(data) < 0x70 or not data.startswith(b"dex\n"):
        raise ValidationError("classes.dex has an invalid header")
    string_count, string_offset = _u32(data, 0x38), _u32(data, 0x3C); type_count, type_offset = _u32(data, 0x40), _u32(data, 0x44); class_count, class_offset = _u32(data, 0x60), _u32(data, 0x64)
    if max(string_count, type_count, class_count) > 1_000_000: raise SafetyStop("DEX table count exceeds audited bound")
    strings: list[str] = []
    for index in range(string_count):
        value_offset = _u32(data, string_offset + index * 4); _, value_offset = _uleb128(data, value_offset); end = data.find(b"\0", value_offset)
        if end < 0: raise ValidationError("Unterminated DEX string")
        strings.append(data[value_offset:end].decode("utf-8", "replace"))
    types = [strings[_u32(data, type_offset + index * 4)] for index in range(type_count)]
    return [types[_u32(data, class_offset + index * 32)] for index in range(class_count)]
def signer_fingerprint(apk: Path) -> str | None:
    keytool = shutil.which("keytool")
    if keytool is None: return None
    try:
        completed = subprocess.run([keytool, "-printcert", "-jarfile", str(apk)], check=False, capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.TimeoutExpired): return None
    match = re.search(r"SHA256:\s*([0-9A-F:]+)", completed.stdout + completed.stderr)
    return match.group(1).replace(":", "") if match else None

def load_json(path: Path) -> Any:
    try: return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc: raise ValidationError(f"Invalid JSON: {path.relative_to(ROOT)}: {exc}") from exc
def png_dimensions(path: Path) -> tuple[int, int]:
    try: header = path.read_bytes()[:24]
    except OSError as exc: raise ValidationError(f"Unable to read PNG {path}: {exc}") from exc
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR": raise ValidationError(f"Invalid PNG header: {path}")
    return struct.unpack(">II", header[16:24])
def require(condition: bool, message: str, errors: list[str]) -> None:
    if not condition: errors.append(message)
def validate_project() -> list[str]:
    errors: list[str] = []
    # The launcher is the only product. The retired DoFun theme module and its tooling stay gone.
    settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
    included = re.findall(r'include\("(:[^"]+)"\)', settings)
    # The optional N3 display host is a separate experimental APK; it is never the released product.
    require(included == [":launcher", ":n3host"], f"Gradle must build :launcher and the optional :n3host only, found {included}", errors)
    host_build = (ROOT / "n3host/build.gradle.kts").read_text(encoding="utf-8")
    require('prop("n3host.enabled") == "true"' in host_build, "n3host must stay disabled unless explicitly enabled at build time", errors)
    for retired in ("theme", "tools/fetch_replugin.py", "tools/fetch_leaflet.py", "launcher/src/main/assets/map"):
        require(not (ROOT / retired).exists(), f"Retired product path is present: {retired}", errors)
    try:
        config = load_json(ROOT / "release-config.json")
        build = (LAUNCHER_ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        require(config.get("application_id") == LAUNCHER_PACKAGE, "Release identity must be the launcher package", errors)
        require(config.get("module") == "launcher" and config.get("gradle_task") == ":launcher:assembleRelease",
                "Release build must be :launcher:assembleRelease", errors)
        require(config.get("apk_path") == "launcher/build/outputs/apk/release/launcher-release.apk",
                "Release APK path must be the launcher release output", errors)
        require("plugin_id" not in config, "Release configuration must not carry a DoFun plug-in id", errors)
        require(f'applicationId = "{LAUNCHER_PACKAGE}"' in build, "Launcher applicationId changed", errors)
        sdk = config.get("sdk", {})
        for key, gradle in (("min", "minSdk"), ("target", "targetSdk"), ("compile", "compileSdk")):
            match = re.search(rf"\b{gradle} = (\d+)", build)
            require(match is not None and int(match.group(1)) == sdk.get(key),
                    f"release-config sdk.{key} disagrees with launcher {gradle}", errors)
        for relative in config.get("install_tools", []):
            require((ROOT / relative).is_file(), f"Release install tool is missing: {relative}", errors)
    except (OSError, ValidationError) as exc:
        errors.append(f"Invalid release configuration: {exc}")
    try:
        root = ElementTree.parse(LAUNCHER_ROOT / "src" / "main" / "AndroidManifest.xml").getroot()
        android = "{http://schemas.android.com/apk/res/android}"
        require(root.get("package") in (None, LAUNCHER_PACKAGE), "Launcher manifest package changed", errors)
        names = {item.get(f"{android}name") for item in root.iter("meta-data")}
        require("launcher.variety.theme.plugin" not in names, "Launcher must not declare DoFun plug-in metadata", errors)
    except (OSError, ElementTree.ParseError) as exc:
        errors.append(f"Invalid launcher manifest: {exc}")
    forbidden_suffixes = {".apk", ".aab", ".aar", ".jks", ".keystore", ".p12", ".pem"}; ignored = {".git", ".local", "build", "dist", "__pycache__"}
    for path in ROOT.rglob("*"):
        rel = path.relative_to(ROOT)
        if any(part in ignored for part in rel.parts): continue
        if path.is_file() and path.suffix.lower() in forbidden_suffixes: errors.append(f"Forbidden binary/key in repository: {rel}")
    return errors

def audit_apk(apk: Path) -> dict[str, Any]:
    infos = inspect_zip(apk)
    with zipfile.ZipFile(apk) as archive:
        manifest = manifest_summary(parse_binary_manifest(archive.read("AndroidManifest.xml")))
        dex_names = sorted(info.filename for info in infos if re.fullmatch(r"classes(?:[2-9][0-9]*)?\.dex", info.filename))
        classes = sorted({item for name in dex_names for item in dex_classes(archive.read(name))})
        json_entries = sorted(info.filename for info in infos if info.filename.startswith("assets/") and info.filename.endswith(".json")); invalid_json: list[str] = []
        for entry in json_entries:
            try: json.loads(archive.read(entry).decode("utf-8"))
            except (UnicodeError, json.JSONDecodeError): invalid_json.append(entry)
    return {"file":str(apk),"sha256":sha256_file(apk),"size_bytes":apk.stat().st_size,"manifest":manifest,"entry_count":len(infos),"json_entry_count":len(json_entries),"invalid_json_entries":invalid_json,"dex_class_count":len(classes),"dex_file_count":len(dex_names),"dex_classes":classes,"signer_certificate_sha256":signer_fingerprint(apk)}
def write_json(value: Any) -> None: sys.stdout.write(json.dumps(value, indent=2, sort_keys=True) + "\n")
def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__); subs = parser.add_subparsers(dest="command", required=True); subs.add_parser("validate"); audit = subs.add_parser("audit"); audit.add_argument("--apk", type=Path, required=True); return parser
def main(argv: Iterable[str] | None = None) -> int:
    parser = build_parser(); args = parser.parse_args(list(argv) if argv is not None else None)
    try:
        if args.command == "validate":
            errors = validate_project()
            if errors:
                for error in errors: print(f"ERROR: {error}", file=sys.stderr)
                print(f"FAILED: {len(errors)} validation error(s)", file=sys.stderr); return 1
            print("SUCCESS: repository validation passed"); return 0
        write_json(audit_apk(args.apk.resolve())); return 0
    except SafetyStop as exc: print(f"STOP: {exc}\nSTOPPED FOR SAFETY", file=sys.stderr); return 2
    except ThemeError as exc: print(f"ERROR: {exc}\nFAILED", file=sys.stderr); return 1
    except KeyboardInterrupt: print("INTERRUPTED", file=sys.stderr); return 130
if __name__ == "__main__": raise SystemExit(main())
