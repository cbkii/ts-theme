#!/usr/bin/env python3
"""Audit APK interoperability evidence and validate the clean TS18 theme source.

Python 3.9+; standard library only. Vendor APKs are accepted only by the read-only
``audit`` command and are never used as release build inputs.
"""

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
CUSTOM_PACKAGE = "launcher.variety.theme.plugin.cbk_black"
CUSTOM_PLUGIN_ID = "cbk_black"
MAX_APK_SIZE = 64 * 1024 * 1024
MAX_ENTRY_SIZE = 32 * 1024 * 1024
MAX_EXPANDED_SIZE = 128 * 1024 * 1024
MAX_ENTRIES = 10_000


class ThemeError(RuntimeError):
    """Base domain error."""


class ValidationError(ThemeError):
    """Input or project content is invalid."""


class SafetyStop(ThemeError):
    """An operation is outside the audited safety boundary."""


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_member_name(name: str) -> bool:
    path = PurePosixPath(name)
    return (
        bool(name)
        and not name.startswith(("/", "\\"))
        and "\\" not in name
        and not any(part in ("", ".", "..") for part in path.parts)
        and not (path.parts and ":" in path.parts[0])
    )


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
            seen: set[str] = set()
            expanded = 0
            for info in infos:
                if not safe_member_name(info.filename):
                    raise SafetyStop(f"Unsafe APK entry: {info.filename!r}")
                if info.filename in seen:
                    raise SafetyStop(f"Duplicate APK entry: {info.filename}")
                seen.add(info.filename)
                expanded += info.file_size
                if info.file_size > MAX_ENTRY_SIZE:
                    raise SafetyStop(f"Oversized APK entry: {info.filename}")
                if expanded > MAX_EXPANDED_SIZE:
                    raise SafetyStop("APK expanded size exceeds audited bound")
            dex_names = [name for name in seen if re.fullmatch(r"classes(?:[2-9][0-9]*)?\.dex", name)]
            if "AndroidManifest.xml" not in seen or not dex_names:
                raise ValidationError("APK is missing AndroidManifest.xml or DEX code")
            return infos
    except zipfile.BadZipFile as exc:
        raise ValidationError(f"Invalid APK ZIP: {apk}") from exc


def _u16(data: bytes, offset: int) -> int:
    return struct.unpack_from("<H", data, offset)[0]


def _u32(data: bytes, offset: int) -> int:
    return struct.unpack_from("<I", data, offset)[0]


def _length8(data: bytes, offset: int) -> tuple[int, int]:
    value = data[offset]
    offset += 1
    if value & 0x80:
        value = ((value & 0x7F) << 8) | data[offset]
        offset += 1
    return value, offset


def _length16(data: bytes, offset: int) -> tuple[int, int]:
    value = _u16(data, offset)
    offset += 2
    if value & 0x8000:
        value = ((value & 0x7FFF) << 16) | _u16(data, offset)
        offset += 2
    return value, offset


def parse_binary_manifest(data: bytes) -> list[dict[str, Any]]:
    """Return start elements and typed attributes from Android binary XML."""
    if len(data) < 8 or _u16(data, 0) != 0x0003:
        raise ValidationError("Manifest is not Android binary XML")
    offset = 8
    strings: list[str] = []
    elements: list[dict[str, Any]] = []
    while offset < len(data):
        if offset + 8 > len(data):
            raise ValidationError("Truncated binary XML chunk")
        chunk_type = _u16(data, offset)
        header_size = _u16(data, offset + 2)
        chunk_size = _u32(data, offset + 4)
        if header_size < 8 or chunk_size < header_size or offset + chunk_size > len(data):
            raise ValidationError("Invalid binary XML chunk bounds")
        if chunk_type == 0x0001:
            count = _u32(data, offset + 8)
            flags = _u32(data, offset + 16)
            strings_start = _u32(data, offset + 20)
            utf8 = bool(flags & 0x100)
            if count > 100_000:
                raise SafetyStop("Manifest string pool is unreasonably large")
            strings = []
            for index in range(count):
                relative = _u32(data, offset + header_size + index * 4)
                cursor = offset + strings_start + relative
                if utf8:
                    _, cursor = _length8(data, cursor)
                    byte_length, cursor = _length8(data, cursor)
                    value = data[cursor : cursor + byte_length].decode("utf-8", "strict")
                else:
                    char_length, cursor = _length16(data, cursor)
                    value = data[cursor : cursor + char_length * 2].decode("utf-16le", "strict")
                strings.append(value)
        elif chunk_type == 0x0102:
            if not strings:
                raise ValidationError("Manifest element precedes string pool")
            name_index = _u32(data, offset + 20)
            attribute_start = _u16(data, offset + 24)
            attribute_size = _u16(data, offset + 26)
            attribute_count = _u16(data, offset + 28)
            if attribute_size < 20 or attribute_count > 4096:
                raise ValidationError("Invalid binary XML attribute table")
            attributes: dict[str, Any] = {}
            base = offset + 16 + attribute_start
            for index in range(attribute_count):
                cursor = base + index * attribute_size
                if cursor + 20 > offset + chunk_size:
                    raise ValidationError("Manifest attribute exceeds chunk")
                attr_name_index = _u32(data, cursor + 4)
                raw_index = _u32(data, cursor + 8)
                data_type = data[cursor + 15]
                typed_data = _u32(data, cursor + 16)
                attr_name = strings[attr_name_index]
                if raw_index != 0xFFFFFFFF:
                    value: Any = strings[raw_index]
                elif data_type == 0x03:
                    value = strings[typed_data]
                elif data_type == 0x12:
                    value = bool(typed_data)
                elif data_type in (0x10, 0x11):
                    value = typed_data
                elif data_type == 0x01:
                    value = f"@0x{typed_data:08x}"
                else:
                    value = {"type": data_type, "data": typed_data}
                attributes[attr_name] = value
            elements.append({"name": strings[name_index], "attributes": attributes})
        offset += chunk_size
    if not elements or elements[0]["name"] != "manifest":
        raise ValidationError("No manifest start element found")
    return elements


def manifest_summary(elements: list[dict[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    permissions: list[str] = []
    metadata: list[dict[str, Any]] = []
    for element in elements:
        name = element["name"]
        attrs = element["attributes"]
        if name == "manifest":
            result.update(
                package=attrs.get("package"),
                version_code=attrs.get("versionCode"),
                version_name=attrs.get("versionName"),
                compile_sdk=attrs.get("compileSdkVersion"),
            )
        elif name == "uses-sdk":
            result["min_sdk"] = attrs.get("minSdkVersion")
            result["target_sdk"] = attrs.get("targetSdkVersion")
        elif name == "uses-permission":
            permissions.append(str(attrs.get("name")))
        elif name == "meta-data":
            metadata.append({"name": attrs.get("name"), "value": attrs.get("value")})
    result["permissions"] = permissions
    result["metadata"] = metadata
    return result


def _uleb128(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    shift = 0
    for _ in range(5):
        current = data[offset]
        offset += 1
        value |= (current & 0x7F) << shift
        if current < 0x80:
            return value, offset
        shift += 7
    raise ValidationError("Invalid DEX ULEB128")


def dex_classes(data: bytes) -> list[str]:
    if len(data) < 0x70 or not data.startswith(b"dex\n"):
        raise ValidationError("classes.dex has an invalid header")
    string_count, string_offset = _u32(data, 0x38), _u32(data, 0x3C)
    type_count, type_offset = _u32(data, 0x40), _u32(data, 0x44)
    class_count, class_offset = _u32(data, 0x60), _u32(data, 0x64)
    if max(string_count, type_count, class_count) > 1_000_000:
        raise SafetyStop("DEX table count exceeds audited bound")
    strings: list[str] = []
    for index in range(string_count):
        value_offset = _u32(data, string_offset + index * 4)
        _, value_offset = _uleb128(data, value_offset)
        end = data.find(b"\0", value_offset)
        if end < 0:
            raise ValidationError("Unterminated DEX string")
        strings.append(data[value_offset:end].decode("utf-8", "replace"))
    types = [strings[_u32(data, type_offset + index * 4)] for index in range(type_count)]
    return [types[_u32(data, class_offset + index * 32)] for index in range(class_count)]


def signer_fingerprint(apk: Path) -> str | None:
    keytool = shutil.which("keytool")
    if keytool is None:
        return None
    try:
        completed = subprocess.run(
            [keytool, "-printcert", "-jarfile", str(apk)],
            check=False,
            capture_output=True,
            text=True,
            timeout=30,
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    match = re.search(r"SHA256:\s*([0-9A-F:]+)", completed.stdout + completed.stderr)
    return match.group(1).replace(":", "") if match else None


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ValidationError(f"Invalid JSON: {path.relative_to(ROOT)}: {exc}") from exc


def png_dimensions(path: Path) -> tuple[int, int]:
    try:
        header = path.read_bytes()[:24]
    except OSError as exc:
        raise ValidationError(f"Unable to read PNG {path}: {exc}") from exc
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        raise ValidationError(f"Invalid PNG header: {path}")
    return struct.unpack(">II", header[16:24])


def require(condition: bool, message: str, errors: list[str]) -> None:
    if not condition:
        errors.append(message)


def attributes_by_id(config: dict[str, Any]) -> dict[str, dict[str, Any]]:
    result: dict[str, dict[str, Any]] = {}
    for item in config.get("attributes", []):
        if isinstance(item, dict) and isinstance(item.get("id_name"), str):
            result[item["id_name"]] = item
    return result


def validate_project() -> list[str]:
    errors: list[str] = []
    json_paths = sorted((THEME_ROOT / "assets").rglob("*.json"))
    require(bool(json_paths), "No theme JSON files found", errors)
    documents: dict[Path, Any] = {}
    for path in json_paths:
        try:
            documents[path.relative_to(THEME_ROOT / "assets")] = load_json(path)
        except ValidationError as exc:
            errors.append(str(exc))

    theme = documents.get(Path("theme_config.json"), {})
    pages = theme.get("config", [{}])[0].get("page_config", []) if isinstance(theme, dict) else []
    types = [item.get("soft_type") for item in pages if isinstance(item, dict)]
    require(types == ["desktop_window", "local_radio", "local_music|bt_music", "time"],
            f"Unexpected page surface order/types: {types}", errors)
    for item in pages:
        if not isinstance(item, dict):
            errors.append("Non-object page_config item")
            continue
        require(isinstance(item.get("width"), int) and item["width"] > 0,
                f"Invalid width for {item.get('soft_type')}", errors)
        require(isinstance(item.get("height"), int) and item["height"] > 0,
                f"Invalid height for {item.get('soft_type')}", errors)
    window = next((item for item in pages if item.get("soft_type") == "desktop_window"), {})
    require(window.get("width", 0) * window.get("height", 0) >= 650_000,
            "Navigation window is not substantially larger than FYD", errors)
    require(
        (window.get("width"), window.get("height"), window.get("gravity"))
        == (1154, 583, "l_93|t_119"),
        "Navigation window must occupy the unobstructed area below the top strip",
        errors,
    )
    expected_strip = {
        "local_radio": (286, 64, "l_93|t_55"),
        "local_music|bt_music": (690, 64, "l_379|t_55"),
        "time": (178, 64, "l_1069|t_55"),
    }
    for soft_type, expected in expected_strip.items():
        surface = next((item for item in pages if item.get("soft_type") == soft_type), {})
        actual = (surface.get("width"), surface.get("height"), surface.get("gravity"))
        require(actual == expected, f"Unexpected {soft_type} strip geometry: {actual}", errors)

    time_view = attributes_by_id(documents.get(Path("time/view2_config.json"), {}))
    require(time_view.get("tv_time_day", {}).get("visibility") is True,
            "Date must remain visible", errors)
    require(time_view.get("tv_time_hour", {}).get("visibility") is False,
            "Clock must be hidden", errors)
    require(time_view.get("tv_ap", {}).get("visibility") is False,
            "AM/PM must be hidden", errors)
    time_config = documents.get(Path("time/time2_config.json"), {})
    require(time_config.get("format") == "dd MMM", "Date format must be dd MMM", errors)

    media_view = attributes_by_id(documents.get(Path("media/media_view_config.json"), {}))
    require(media_view.get("iv_media_icon", {}).get("visibility") is False,
            "Album art must be hidden", errors)
    require(media_view.get("iv_media_icon_bg", {}).get("visibility") is False,
            "Visualizer/background artwork must be hidden", errors)
    require(media_view.get("tv_media_name", {}).get("width", 0) >= 430,
            "Media ticker field is not wide enough", errors)
    require(media_view.get("tv_media_type", {}).get("visibility") is False,
            "Redundant media source line must be hidden", errors)
    require(media_view.get("iv_radio_pp", {}).get("visibility") is False,
            "Radio play/pause must be hidden", errors)
    require(media_view.get("tv_radio_unit", {}).get("visibility") is False,
            "Radio unit must be hidden in the compact frequency format", errors)
    for view_id in ("iv_radio_pre", "iv_radio_next"):
        target = media_view.get(view_id, {})
        require(target.get("width", 0) >= 48 and target.get("height", 0) >= 48,
                f"{view_id} touch target is below 48 px", errors)

    theme_json_text = "\n".join(
        path.read_text(encoding="utf-8") for path in json_paths if path.is_file()
    ).upper()
    for cool_colour in ("#00E5FF", "#00B4D8", "#B8C1CC"):
        require(cool_colour not in theme_json_text,
                f"Cool colour token remains in theme JSON: {cool_colour}", errors)
    require(not (ROOT / "design" / "icons" / "visualiser.svg").exists(),
            "Obsolete visualiser asset must not be present", errors)

    identity = documents.get(Path("import_theme_info_config.json"), {})
    require(identity.get("themeId") == "202608220001", "Unexpected development theme ID", errors)

    try:
        release_config = load_json(ROOT / "release-config.json")
    except ValidationError as exc:
        errors.append(str(exc))
        release_config = {}
    require(release_config.get("application_id") == CUSTOM_PACKAGE,
            "Release package authority does not match the DoFun plug-in package", errors)
    require(release_config.get("plugin_id") == CUSTOM_PLUGIN_ID,
            "Release plug-in authority does not match the DoFun metadata ID", errors)
    require(release_config.get("gradle_task") == ":theme:assembleRelease",
            "Release Gradle task authority is unexpected", errors)
    require(release_config.get("apk_path") == "theme/build/outputs/apk/release/theme-release.apk",
            "Release APK path authority is unexpected", errors)
    require(release_config.get("sdk") == {"min": 16, "target": 26, "compile": 29},
            "Release SDK authority does not match the audited DoFun contract", errors)
    require(release_config.get("native_abis") == [],
            "Declarative theme must not introduce a native ABI payload", errors)
    try:
        properties = {}
        for raw in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
            if raw.strip() and not raw.lstrip().startswith(("#", "!")) and "=" in raw:
                key, value = raw.split("=", 1)
                properties[key.strip()] = value.strip()
        require(re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", properties.get("VERSION_NAME", "")) is not None,
                "VERSION_NAME must be strict X.Y.Z", errors)
        require(int(properties.get("VERSION_CODE", "0")) > 0,
                "VERSION_CODE must be a positive integer", errors)
    except (OSError, ValueError) as exc:
        errors.append(f"Invalid Gradle version authority: {exc}")

    manifest_path = THEME_ROOT / "AndroidManifest.xml"
    try:
        manifest_root = ElementTree.parse(manifest_path).getroot()
        android = "{http://schemas.android.com/apk/res/android}"
        application = manifest_root.find("application")
        metadata = {
            item.get(f"{android}name"): item.get(f"{android}value")
            for item in manifest_root.findall("./application/meta-data")
        }
        require(application is not None, "Android manifest has no application element", errors)
        require(metadata.get("launcher.variety.theme.plugin") == CUSTOM_PLUGIN_ID,
                "Android manifest has incorrect DoFun plug-in metadata", errors)
        require(metadata.get("support_systemui") == "false",
                "Theme must not claim SystemUI ownership", errors)
        permissions = {
            item.get(f"{android}name") for item in manifest_root.findall("uses-permission")
        }
        require(permissions == {
                    "android.permission.WRITE_EXTERNAL_STORAGE",
                    "android.permission.READ_PHONE_STATE",
                    "android.permission.READ_EXTERNAL_STORAGE",
                },
                "Android manifest compatibility permission set changed", errors)
        components = [
            item.tag for item in manifest_root.findall("./application/*")
            if item.tag != "meta-data"
        ]
        require(not components,
                f"Declarative theme must not add Android components: {components}", errors)
    except (OSError, ElementTree.ParseError) as exc:
        errors.append(f"Invalid Android manifest: {exc}")

    required_resources = {
        "res/values/strings.xml",
        "res/values/aliases.xml",
        "res/drawable/selector_media_previous.xml",
        "res/drawable/selector_media_play.xml",
        "res/drawable/selector_media_stop.xml",
        "res/drawable/selector_media_next.xml",
        "res/drawable/selector_media_bt_pp.xml",
        "res/drawable/selector_media_radio_pp.xml",
    }
    for relative in sorted(required_resources):
        require((THEME_ROOT / relative).is_file(), f"Missing clean-build resource: {relative}", errors)

    for path in sorted((THEME_ROOT / "res").rglob("*.xml")):
        try:
            ElementTree.parse(path)
        except (OSError, ElementTree.ParseError) as exc:
            errors.append(f"Invalid Android resource XML {path.relative_to(ROOT)}: {exc}")

    expected_png_dimensions = {
        "radio_bg.png": (286, 64),
        "media_bg.png": (690, 64),
        "time_bg.png": (178, 64),
    }
    for name, expected in expected_png_dimensions.items():
        path = THEME_ROOT / "res" / "mipmap-mdpi-v4" / name
        try:
            actual = png_dimensions(path)
            require(actual == expected, f"Unexpected {name} dimensions: {actual}", errors)
        except ValidationError as exc:
            errors.append(str(exc))

    for svg in sorted((ROOT / "design").rglob("*.svg")):
        try:
            ElementTree.parse(svg)
        except (OSError, ElementTree.ParseError) as exc:
            errors.append(f"Invalid SVG {svg.relative_to(ROOT)}: {exc}")

    forbidden_suffixes = {".apk", ".aab", ".aar", ".jks", ".keystore", ".p12", ".pem"}
    ignored_roots = {".git", ".local", "build", "dist", "__pycache__"}
    for path in ROOT.rglob("*"):
        relative = path.relative_to(ROOT)
        if any(part in ignored_roots for part in relative.parts):
            continue
        if path.is_file() and path.suffix.lower() in forbidden_suffixes:
            errors.append(f"Forbidden binary/key in repository: {path.relative_to(ROOT)}")
    return errors


def audit_apk(apk: Path) -> dict[str, Any]:
    infos = inspect_zip(apk)
    with zipfile.ZipFile(apk) as archive:
        manifest = manifest_summary(parse_binary_manifest(archive.read("AndroidManifest.xml")))
        dex_names = sorted(
            info.filename for info in infos if re.fullmatch(r"classes(?:[2-9][0-9]*)?\.dex", info.filename)
        )
        classes = sorted({item for name in dex_names for item in dex_classes(archive.read(name))})
        json_entries = sorted(
            info.filename for info in infos if info.filename.startswith("assets/") and info.filename.endswith(".json")
        )
        invalid_json: list[str] = []
        for entry in json_entries:
            try:
                json.loads(archive.read(entry).decode("utf-8"))
            except (UnicodeError, json.JSONDecodeError):
                invalid_json.append(entry)
    return {
        "file": str(apk),
        "sha256": sha256_file(apk),
        "size_bytes": apk.stat().st_size,
        "manifest": manifest,
        "entry_count": len(infos),
        "json_entry_count": len(json_entries),
        "invalid_json_entries": invalid_json,
        "dex_class_count": len(classes),
        "dex_file_count": len(dex_names),
        "dex_classes": classes,
        "signer_certificate_sha256": signer_fingerprint(apk),
    }


def write_json(value: Any) -> None:
    sys.stdout.write(json.dumps(value, indent=2, sort_keys=True) + "\n")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("validate", help="validate repository JSON, layout invariants and policy")

    audit = subparsers.add_parser("audit", help="read-only static audit of a theme APK")
    audit.add_argument("--apk", type=Path, required=True)

    return parser


def main(argv: Iterable[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(list(argv) if argv is not None else None)
    try:
        if args.command == "validate":
            errors = validate_project()
            if errors:
                for error in errors:
                    print(f"ERROR: {error}", file=sys.stderr)
                print(f"FAILED: {len(errors)} validation error(s)", file=sys.stderr)
                return 1
            print("SUCCESS: repository validation passed")
            return 0
        if args.command == "audit":
            write_json(audit_apk(args.apk.resolve()))
            return 0
        parser.error("Unknown command")
    except SafetyStop as exc:
        print(f"STOP: {exc}", file=sys.stderr)
        print("STOPPED FOR SAFETY", file=sys.stderr)
        return 2
    except ThemeError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        print("FAILED", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("INTERRUPTED", file=sys.stderr)
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
