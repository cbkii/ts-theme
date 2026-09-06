#!/usr/bin/env python3
"""Audit APK interoperability evidence and validate the clean TS18 theme source."""
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
THEME_ROOT = ROOT / "theme" / "src" / "main"
CUSTOM_PACKAGE = "launcher.variety.theme.plugin.sfp_cbk_black"
CUSTOM_PLUGIN_ID = "sfp_cbk_black"
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
        for chunk in iter(lambda: path.open("rb").read(0), b""):
            break
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
def attributes_by_id(config: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {item["id_name"]: item for item in config.get("attributes", []) if isinstance(item, dict) and isinstance(item.get("id_name"), str)}
def contains_empty_key(value: Any) -> bool:
    if isinstance(value, dict): return "" in value or any(contains_empty_key(v) for v in value.values())
    if isinstance(value, list): return any(contains_empty_key(v) for v in value)
    return False
def _gravity_xy(value: str) -> tuple[int, int]:
    mx = re.search(r"(?:^|\|)l_([0-9]+)(?:\||$)", value); my = re.search(r"(?:^|\|)t_([0-9]+)(?:\||$)", value)
    if not mx or not my: raise ValidationError(f"Unsupported absolute gravity: {value}")
    return int(mx.group(1)), int(my.group(1))

def validate_project() -> list[str]:
    errors: list[str] = []
    asset_root = THEME_ROOT / "assets"
    json_paths = sorted(asset_root.rglob("*.json")); documents: dict[Path, Any] = {}
    require(bool(json_paths), "No theme JSON files found", errors)
    for path in json_paths:
        try:
            value = load_json(path); documents[path.relative_to(asset_root)] = value
            require(not contains_empty_key(value), f"Host JSON contains an empty/comment key: {path.relative_to(ROOT)}", errors)
        except ValidationError as exc: errors.append(str(exc))

    try: profile = load_json(ROOT / "config" / "ts18-layout.json")
    except ValidationError as exc: errors.append(str(exc)); profile = {}
    require(profile.get("schema_version") == 2, "TS18 layout profile schema must describe physical and theme coordinates", errors)
    require(profile.get("physical_width") == 1280 and profile.get("physical_height") == 720, "Unexpected physical layout profile", errors)
    require(profile.get("top_system_inset") == 55 and profile.get("right_system_inset") == 55, "Exact TS18 system insets changed", errors)
    require(profile.get("safe_right") == 1225 and profile.get("safe_bottom") == 702, "Exact TS18 safe bounds changed", errors)
    require(profile.get("content_left") == 81 and profile.get("strip_height") == 64, "Content/hotseat contract changed", errors)

    surfaces = profile.get("surfaces", {}) if isinstance(profile, dict) else {}
    expected_physical = {
        "radio": (81, 55, 286, 64), "music": (367, 55, 680, 64), "date": (1047, 55, 178, 64), "map": (81, 119, 1144, 583)
    }
    for name, spec in expected_physical.items():
        s = surfaces.get(name, {})
        actual = (s.get("x"), s.get("y"), s.get("width"), s.get("height"))
        require(actual == spec, f"Unexpected {name} physical geometry: {actual}", errors)
        if all(isinstance(v, int) for v in actual):
            require(actual[0] + actual[2] <= 1225, f"{name} enters right SystemUI region", errors)
            require(actual[1] + actual[3] <= 702, f"{name} exceeds safe bottom", errors)
    if all(name in surfaces for name in ("radio", "music", "date")):
        require(surfaces["radio"]["x"] + surfaces["radio"]["width"] == surfaces["music"]["x"], "Radio/music strip gap or overlap", errors)
        require(surfaces["music"]["x"] + surfaces["music"]["width"] == surfaces["date"]["x"], "Music/date strip gap or overlap", errors)
        require(surfaces["date"]["x"] + surfaces["date"]["width"] == 1225, "Strip does not end at safe-right", errors)

    viewport = profile.get("theme_viewport", {}) if isinstance(profile, dict) else {}
    require(viewport.get("resolution") == "1280x652", "Compatibility viewport must be 1280x652", errors)
    require((viewport.get("width"), viewport.get("height")) == (1280, 652), "Compatibility viewport dimensions changed", errors)
    require((viewport.get("physical_origin_x"), viewport.get("physical_origin_y")) == (0, 55), "Compatibility viewport physical origin changed", errors)
    require((viewport.get("safe_right"), viewport.get("safe_bottom")) == (1225, 647), "Compatibility viewport safe bounds changed", errors)
    compat = profile.get("compatibility_surfaces", {}) if isinstance(profile, dict) else {}
    expected_compat = {"medias": (81, 0, 966, 64), "date": (1047, 0, 178, 64), "map": (81, 64, 1144, 583)}
    for name, spec in expected_compat.items():
        s = compat.get(name, {})
        actual = (s.get("x"), s.get("y"), s.get("width"), s.get("height"))
        require(actual == spec, f"Unexpected {name} compatibility geometry: {actual}", errors)
        if all(isinstance(v, int) for v in actual):
            require(actual[0] + actual[2] <= viewport.get("safe_right", -1), f"{name} enters right SystemUI region in theme coordinates", errors)
            require(actual[1] + actual[3] <= viewport.get("safe_bottom", -1), f"{name} exceeds theme safe bottom", errors)
    if all(name in compat for name in ("medias", "date", "map")):
        ox, oy = viewport.get("physical_origin_x", 0), viewport.get("physical_origin_y", 0)
        require((compat["map"]["x"] + ox, compat["map"]["y"] + oy, compat["map"]["width"], compat["map"]["height"]) == expected_physical["map"], "Theme map does not project onto the known physical map surface", errors)
        require((compat["date"]["x"] + ox, compat["date"]["y"] + oy, compat["date"]["width"], compat["date"]["height"]) == expected_physical["date"], "Theme date does not project onto the known physical date surface", errors)
        combined_media = (expected_physical["radio"][0], expected_physical["radio"][1], expected_physical["radio"][2] + expected_physical["music"][2], expected_physical["radio"][3])
        require((compat["medias"]["x"] + ox, compat["medias"]["y"] + oy, compat["medias"]["width"], compat["medias"]["height"]) == combined_media, "Unified medias widget does not project onto the former radio+music strip", errors)

    expected_types = ["desktop_window", "medias", "time"]
    mapping = {"desktop_window": "map", "medias": "medias", "time": "date"}
    for relative in (Path("theme_config.json"), Path("layout-1280x652/theme_config.json")):
        theme = documents.get(relative, {})
        pages = theme.get("config", [{}])[0].get("page_config", []) if isinstance(theme, dict) and theme.get("config") else []
        types = [item.get("soft_type") for item in pages if isinstance(item, dict)]
        require(types == expected_types, f"Unexpected page surface order/types in {relative}: {types}", errors)
        require(not any(t in {"local_radio", "local_music", "bt_music", "local_music|bt_music"} for t in types), f"Source/media mode was incorrectly used as a widget type in {relative}", errors)
        for item in pages:
            if not isinstance(item, dict) or item.get("soft_type") not in mapping: continue
            try: x, y = _gravity_xy(str(item.get("gravity", "")))
            except ValidationError as exc: errors.append(str(exc)); continue
            s = compat.get(mapping[item["soft_type"]], {})
            require((x, y, item.get("width"), item.get("height")) == (s.get("x"), s.get("y"), s.get("width"), s.get("height")), f"{relative} does not match compatibility profile for {item.get('soft_type')}", errors)

    mirrored = (
        "theme_config.json", "hotseat_config.json", "home/view_config.json", "home/app_widget_view_config1.json",
        "media/media_config.json", "media/media_view_config.json", "time/time2_config.json", "time/view2_config.json",
    )
    for relative in mirrored:
        generic = documents.get(Path(relative))
        specific = documents.get(Path("layout-1280x652") / relative)
        require(generic is not None and specific is not None, f"Missing generic/1280x652 mirror for {relative}", errors)
        if generic is not None and specific is not None:
            require(generic == specific, f"1280x652 compatibility copy drifted from generic {relative}", errors)

    generated = documents.get(Path(".gen/c.json"), {})
    require(generated.get("variant") == "land", "Generated compatibility metadata must declare land variant", errors)
    require(generated.get("all_resolutions") == ["1280x652"], "Generated compatibility metadata must target only 1280x652 for this candidate", errors)
    require(generated.get("support_night") is True and generated.get("support_systemui") is False and generated.get("support_systemui_night") is False, "Generated support flags do not match the theme contract", errors)
    expected_plugins = ["app", "desktop_window", "medias", "time"]
    require(generated.get("all_plugins") == expected_plugins, f"Unexpected generated plugin inventory: {generated.get('all_plugins')}", errors)
    layouts = generated.get("layouts", []) if isinstance(generated, dict) else []
    require(len(layouts) == 1 and isinstance(layouts[0], dict) and layouts[0].get("resolution") == "1280x652", "Generated metadata must have one 1280x652 layout", errors)
    if len(layouts) == 1 and isinstance(layouts[0], dict):
        require(layouts[0].get("all_plugins") == expected_plugins, "Generated per-layout plugin inventory drifted", errors)
        plugin_index = layouts[0].get("plugins", {})
        expected_theme_index = [{"type": "desktop_window", "count": 1}, {"type": "medias", "count": 1}, {"type": "time", "count": 1}]
        expected_hotseat_index = [{"type": "app", "count": 5}]
        require(plugin_index.get("theme_config", {}).get("json") == expected_theme_index, "Generated theme_config JSON plugin index is wrong", errors)
        require(plugin_index.get("hotseat_config", {}).get("json") == expected_hotseat_index, "Generated hotseat_config JSON plugin index is wrong", errors)

    media = attributes_by_id(documents.get(Path("media/media_view_config.json"), {}))
    require(media.get("tv_media_name", {}).get("width", 0) >= 430, "Media title field is too narrow", errors)
    for vid in ("iv_media_pre", "iv_media_pp", "iv_media_next"):
        require(media.get(vid, {}).get("width", 0) >= 56 and media.get(vid, {}).get("height", 0) >= 56, f"{vid} touch target is below 56 px", errors)
    for vid in ("iv_radio_pre", "iv_radio_next"):
        require(media.get(vid, {}).get("width", 0) >= 54 and media.get(vid, {}).get("height", 0) >= 54, f"{vid} touch target is below 54 px", errors)
    time_config = documents.get(Path("time/time2_config.json"), {}); require(time_config.get("format") == "dd MMM", "Date format must be dd MMM", errors)
    identity = documents.get(Path("import_theme_info_config.json"), {}); require(identity.get("themeId") == "202608220001" and identity.get("oemId") == "1132", "Theme/OEM identity changed", errors)

    try: release = load_json(ROOT / "release-config.json")
    except ValidationError as exc: errors.append(str(exc)); release = {}
    require(release.get("application_id") == CUSTOM_PACKAGE and release.get("plugin_id") == CUSTOM_PLUGIN_ID, "Release identity does not match hardened window/PIP identity", errors)
    require(release.get("sdk") == {"min":16,"target":26,"compile":29} and release.get("native_abis") == [], "Release SDK/native contract changed", errors)
    require(release.get("layout_profile") == "config/ts18-layout.json", "Release layout profile authority missing", errors)
    required_entries = set(release.get("required_apk_entries", []))
    for entry in ("assets/.gen/c.json", "assets/layout-1280x652/theme_config.json", "assets/layout-1280x652/hotseat_config.json"):
        require(entry in required_entries, f"Release contract does not require compatibility entry: {entry}", errors)
    install_tools = release.get("install_tools", [])
    for relative in install_tools: require(isinstance(relative, str) and (ROOT / relative).is_file(), f"Missing install-tool input: {relative}", errors)
    build_text = (ROOT / "theme" / "build.gradle.kts").read_text(encoding="utf-8")
    require(f'applicationId = "{CUSTOM_PACKAGE}"' in build_text, "Gradle applicationId is not hardened identity", errors)
    require("ignoreAssetsPattern" in build_text and "<dir>_*" in build_text, "Gradle must intentionally package assets/.gen instead of relying on AAPT's dot-directory default", errors)

    try:
        root = ElementTree.parse(THEME_ROOT / "AndroidManifest.xml").getroot(); android = "{http://schemas.android.com/apk/res/android}"
        metadata = {item.get(f"{android}name"): item.get(f"{android}value") for item in root.findall("./application/meta-data")}
        require(metadata.get("launcher.variety.theme.plugin") == CUSTOM_PLUGIN_ID, "Manifest plug-in metadata mismatch", errors)
        require(metadata.get("support_systemui") == "false", "Theme must not claim SystemUI ownership", errors)
        permissions = {item.get(f"{android}name") for item in root.findall("uses-permission")}
        require(permissions == {"android.permission.WRITE_EXTERNAL_STORAGE","android.permission.READ_PHONE_STATE","android.permission.READ_EXTERNAL_STORAGE"}, "Manifest compatibility permission set changed", errors)
        components = [item.tag for item in root.findall("./application/*") if item.tag != "meta-data"]
        require(not components, f"Declarative theme must not add Android components: {components}", errors)
    except (OSError, ElementTree.ParseError) as exc: errors.append(f"Invalid Android manifest: {exc}")

    expected_png = {"radio_bg.png":(286,64), "media_bg.png":(680,64), "time_bg.png":(178,64)}
    for name, dims in expected_png.items():
        try: require(png_dimensions(THEME_ROOT / "res" / "mipmap-mdpi-v4" / name) == dims, f"Unexpected {name} dimensions", errors)
        except ValidationError as exc: errors.append(str(exc))
    for path in sorted((THEME_ROOT / "res").rglob("*.xml")):
        try: ElementTree.parse(path)
        except (OSError, ElementTree.ParseError) as exc: errors.append(f"Invalid Android resource XML {path.relative_to(ROOT)}: {exc}")
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
