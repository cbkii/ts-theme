#!/usr/bin/env python3
"""Shared, standard-library-only release planning and publication primitives."""

from __future__ import annotations

import dataclasses
import hashlib
import json
import os
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path, PurePosixPath
from typing import Any, Iterable


STRICT_TAG = re.compile(r"^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")
STRICT_VERSION = re.compile(r"^v?(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")
SHA1 = re.compile(r"^[0-9a-f]{40}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
ALLOWED_STATES = {"draft", "prerelease", "stable"}
ALLOWED_MODES = {"create_new_release", "repair_existing_release"}
ALLOWED_VERSION_SOURCES = {"workflow_dispatch", "auto_increment", "auto_baseline"}
ANDROID_VERSION_CODE_MAX = 2_100_000_000
VERSION_COMPONENT_BASE = 1_000


class ReleaseError(RuntimeError):
    """Fail-closed release contract violation."""


@dataclasses.dataclass(frozen=True, order=True)
class SemVer:
    major: int
    minor: int
    patch: int

    @classmethod
    def from_tag(cls, value: str) -> "SemVer | None":
        match = STRICT_TAG.fullmatch(value)
        if not match:
            return None
        return cls(*(int(part) for part in match.groups()))

    @classmethod
    def from_dispatch(cls, value: str) -> "SemVer | None":
        match = STRICT_VERSION.fullmatch(value.strip())
        if not match:
            return None
        return cls(*(int(part) for part in match.groups()))

    @property
    def tag(self) -> str:
        return f"v{self.major}.{self.minor}.{self.patch}"

    @property
    def version_name(self) -> str:
        return f"{self.major}.{self.minor}.{self.patch}"

    @property
    def android_version_code(self) -> int:
        if self.minor >= VERSION_COMPONENT_BASE or self.patch >= VERSION_COMPONENT_BASE:
            raise ReleaseError("minor and patch versions must each be between 0 and 999")
        value = (
            self.major * VERSION_COMPONENT_BASE * VERSION_COMPONENT_BASE
            + self.minor * VERSION_COMPONENT_BASE
            + self.patch
        )
        if value < 1 or value > ANDROID_VERSION_CODE_MAX:
            raise ReleaseError(
                f"{self.tag} cannot be represented as an Android versionCode between 1 and "
                f"{ANDROID_VERSION_CODE_MAX}"
            )
        return value

    def next_patch(self) -> "SemVer":
        if self.patch + 1 < VERSION_COMPONENT_BASE:
            return SemVer(self.major, self.minor, self.patch + 1)
        if self.minor + 1 < VERSION_COMPONENT_BASE:
            return SemVer(self.major, self.minor + 1, 0)
        return SemVer(self.major + 1, 0, 0)


@dataclasses.dataclass(frozen=True)
class TagRecord:
    name: str
    source_sha: str


@dataclasses.dataclass(frozen=True)
class ReleaseRecord:
    release_id: int
    tag: str
    draft: bool
    prerelease: bool
    html_url: str = ""

    @property
    def state(self) -> str:
        if self.draft:
            return "draft"
        return "prerelease" if self.prerelease else "stable"

    @property
    def published(self) -> bool:
        return not self.draft


@dataclasses.dataclass(frozen=True)
class RemoteSnapshot:
    tags: tuple[TagRecord, ...]
    releases: tuple[ReleaseRecord, ...]

    def tag_map(self) -> dict[str, TagRecord]:
        return unique_by(self.tags, "name")

    def release_map(self) -> dict[str, ReleaseRecord]:
        return unique_by(self.releases, "tag")


def unique_by(items: Iterable[Any], attribute: str) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for item in items:
        key = getattr(item, attribute)
        if key in result:
            raise ReleaseError(f"Ambiguous duplicate remote {attribute}: {key}")
        result[key] = item
    return result


def parse_properties(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        raise ReleaseError(f"Unable to read version authority {path}: {exc}") from exc
    for line_number, raw in enumerate(lines, 1):
        line = raw.strip()
        if not line or line.startswith(("#", "!")):
            continue
        if "=" not in line:
            raise ReleaseError(f"Malformed property at {path}:{line_number}")
        key, value = line.split("=", 1)
        key, value = key.strip(), value.strip()
        if not key or key in result:
            raise ReleaseError(f"Duplicate or empty property at {path}:{line_number}")
        result[key] = value
    return result


def load_config(root: Path) -> dict[str, Any]:
    path = root / "release-config.json"
    try:
        config = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ReleaseError(f"Invalid release configuration: {exc}") from exc
    required = {
        "schema_version",
        "product_name",
        "asset_stem",
        "application_id",
        "plugin_id",
        "module",
        "gradle_task",
        "apk_path",
        "version_file",
        "version_name_property",
        "version_code_property",
        "sdk",
        "native_abis",
        "required_apk_entries",
        "required_dex_classes",
    }
    missing = sorted(required - set(config))
    if missing:
        raise ReleaseError(f"Release configuration is missing: {', '.join(missing)}")
    if config["schema_version"] != 1:
        raise ReleaseError("Unsupported release configuration schema")
    string_fields = (
        "product_name",
        "asset_stem",
        "application_id",
        "plugin_id",
        "module",
        "gradle_task",
        "apk_path",
        "version_file",
        "version_name_property",
        "version_code_property",
    )
    if any(not isinstance(config[field], str) or not config[field] for field in string_fields):
        raise ReleaseError("Release configuration contains an empty or non-string authority")
    for field in ("apk_path", "version_file"):
        path_value = PurePosixPath(config[field])
        if path_value.is_absolute() or ".." in path_value.parts:
            raise ReleaseError(f"Release configuration contains an unsafe path: {field}")
    if not isinstance(config["sdk"], dict) or set(config["sdk"]) != {"min", "target", "compile"}:
        raise ReleaseError("Release SDK authority is malformed")
    if any(not isinstance(value, int) or value < 1 for value in config["sdk"].values()):
        raise ReleaseError("Release SDK levels must be positive integers")
    if not isinstance(config["native_abis"], list) or any(
        not isinstance(value, str) or not value for value in config["native_abis"]
    ):
        raise ReleaseError("Release native ABI authority is malformed")
    for field in ("required_apk_entries", "required_dex_classes"):
        values = config[field]
        if (
            not isinstance(values, list)
            or not values
            or any(not isinstance(value, str) or not value for value in values)
            or len(values) != len(set(values))
        ):
            raise ReleaseError(f"Release configuration {field} is empty, duplicate or malformed")
    return config


def source_version(root: Path, config: dict[str, Any]) -> tuple[str, int]:
    properties = parse_properties(root / config["version_file"])
    try:
        version_name = properties[config["version_name_property"]]
        version_code_text = properties[config["version_code_property"]]
    except KeyError as exc:
        raise ReleaseError(f"Missing version property: {exc.args[0]}") from exc
    parsed = SemVer.from_tag(f"v{version_name}")
    if parsed is None:
        raise ReleaseError(f"Source version is not strict X.Y.Z: {version_name}")
    try:
        version_code = int(version_code_text)
    except ValueError as exc:
        raise ReleaseError(f"VERSION_CODE is not an integer: {version_code_text}") from exc
    if version_code < 1:
        raise ReleaseError("VERSION_CODE must be positive")
    return parsed.version_name, version_code


def resolve_plan(
    *,
    mode: str,
    requested_tag: str,
    requested_state: str,
    replace_existing: bool,
    current_source_sha: str,
    current_version_name: str,
    snapshot: RemoteSnapshot,
) -> dict[str, Any]:
    if mode not in ALLOWED_MODES:
        raise ReleaseError(f"Unsupported release mode: {mode}")
    if requested_state not in ALLOWED_STATES:
        raise ReleaseError(f"Unsupported release state: {requested_state}")
    if not SHA1.fullmatch(current_source_sha):
        raise ReleaseError("Current source SHA is not a full 40-character commit SHA")

    tags = snapshot.tag_map()
    releases = snapshot.release_map()
    for release_tag in releases:
        if SemVer.from_tag(release_tag) is not None and release_tag not in tags:
            raise ReleaseError(f"Release {release_tag} exists without its matching tag")
    remote_versions = [
        parsed
        for value in set(tags) | set(releases)
        if (parsed := SemVer.from_tag(value)) is not None
    ]

    requested_text = requested_tag.strip()
    if requested_text:
        requested = SemVer.from_dispatch(requested_text)
        if requested is None:
            raise ReleaseError("version_tag must use strict X.Y.Z or vX.Y.Z syntax")
        version_source = "workflow_dispatch"
    elif mode == "repair_existing_release":
        raise ReleaseError("Repair requires an explicit existing version_tag")
    elif remote_versions:
        tag_only_versions = sorted(
            parsed
            for tag_name in set(tags) - set(releases)
            if (parsed := SemVer.from_tag(tag_name)) is not None
        )
        if tag_only_versions:
            raise ReleaseError(
                f"Automatic versioning found interrupted tag-only transaction "
                f"{tag_only_versions[-1].tag}; repair it explicitly before creating another release"
            )
        requested = max(remote_versions).next_patch()
        version_source = "auto_increment"
    else:
        requested = SemVer.from_dispatch(current_version_name)
        if requested is None:
            raise ReleaseError(
                f"Checked-in auto-version baseline is not strict X.Y.Z: {current_version_name}"
            )
        version_source = "auto_baseline"

    requested_tag = requested.tag
    version_code = requested.android_version_code
    exact_tag = tags.get(requested_tag)
    exact_release = releases.get(requested_tag)

    if mode == "create_new_release":
        if exact_tag is not None or exact_release is not None:
            raise ReleaseError(
                f"{requested_tag} is already an interrupted or completed transaction; use repair"
            )
        if remote_versions and requested <= max(remote_versions):
            raise ReleaseError(
                f"Requested {requested_tag} is not newer than remote authority {max(remote_versions).tag}"
            )
        source_sha = current_source_sha
        remote_state = "absent"
    else:
        if exact_tag is None:
            raise ReleaseError(f"Repair requires the existing immutable tag {requested_tag}")
        if not SHA1.fullmatch(exact_tag.source_sha):
            raise ReleaseError(f"Tag {requested_tag} does not resolve to a commit SHA")
        source_sha = exact_tag.source_sha
        remote_state = exact_release.state if exact_release else "tag_only"

    return {
        "schema_version": 1,
        "mode": mode,
        "tag": requested_tag,
        "version_name": requested.version_name,
        "version_code": version_code,
        "version_source": version_source,
        "source_sha": source_sha,
        "release_state": requested_state,
        "replace_existing_assets": bool(replace_existing),
        "observed_remote_state": remote_state,
    }


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_asset_name(name: str) -> bool:
    path = PurePosixPath(name)
    return (
        bool(name)
        and path.name == name
        and name not in {".", ".."}
        and not name.startswith(("-", "."))
        and re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", name) is not None
    )


def load_manifest(path: Path, verify_files: bool = True) -> dict[str, Any]:
    try:
        manifest = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ReleaseError(f"Invalid publication manifest: {exc}") from exc
    required = {
        "schema_version",
        "product_name",
        "tag",
        "version_name",
        "version_code",
        "source_sha",
        "package_id",
        "plugin_id",
        "signer_sha256",
        "release_mode",
        "release_state",
        "replace_existing_assets",
        "assets",
    }
    missing = sorted(required - set(manifest))
    if missing:
        raise ReleaseError(f"Publication manifest is missing: {', '.join(missing)}")
    parsed_tag = SemVer.from_tag(manifest["tag"])
    if manifest["schema_version"] != 1 or parsed_tag is None:
        raise ReleaseError("Publication manifest schema or tag is invalid")
    if manifest["version_name"] != parsed_tag.version_name:
        raise ReleaseError("Publication manifest tag and versionName disagree")
    if not isinstance(manifest["version_code"], int) or manifest["version_code"] < 1:
        raise ReleaseError("Publication manifest versionCode is invalid")
    if manifest["version_code"] != parsed_tag.android_version_code:
        raise ReleaseError("Publication manifest tag and versionCode disagree")
    if not SHA1.fullmatch(str(manifest["source_sha"])):
        raise ReleaseError("Publication manifest source SHA is invalid")
    if manifest["release_mode"] not in ALLOWED_MODES or manifest["release_state"] not in ALLOWED_STATES:
        raise ReleaseError("Publication manifest mode or state is invalid")
    if not isinstance(manifest["replace_existing_assets"], bool):
        raise ReleaseError("Publication manifest replacement authority is invalid")
    for field in ("product_name", "package_id", "plugin_id"):
        if not isinstance(manifest[field], str) or not manifest[field]:
            raise ReleaseError(f"Publication manifest {field} is invalid")
    if re.fullmatch(r"[0-9A-F]{64}", str(manifest["signer_sha256"])) is None:
        raise ReleaseError("Publication manifest signer SHA-256 is invalid")
    assets = manifest["assets"]
    if not isinstance(assets, list) or not assets:
        raise ReleaseError("Publication manifest must contain at least one asset")
    names: set[str] = set()
    roles: set[str] = set()
    for item in assets:
        if not isinstance(item, dict):
            raise ReleaseError("Publication manifest asset is not an object")
        name = item.get("name")
        if not isinstance(name, str) or not safe_asset_name(name) or name in names:
            raise ReleaseError(f"Unsafe or duplicate publication asset name: {name!r}")
        names.add(name)
        role = item.get("role")
        if role not in {"installable_apk", "sha256_sidecar", "metadata_sidecar"} or role in roles:
            raise ReleaseError(f"Unsupported or duplicate publication asset role: {role!r}")
        roles.add(role)
        if item.get("destination") != "release":
            raise ReleaseError(f"Unsupported asset destination for {name}")
        if not isinstance(item.get("size"), int) or item["size"] < 0:
            raise ReleaseError(f"Invalid asset size for {name}")
        if not isinstance(item.get("sha256"), str) or not SHA256.fullmatch(item["sha256"]):
            raise ReleaseError(f"Invalid asset SHA-256 for {name}")
        if verify_files:
            asset_path = path.parent / name
            if not asset_path.is_file():
                raise ReleaseError(f"Publication asset is missing: {name}")
            if asset_path.stat().st_size != item["size"]:
                raise ReleaseError(f"Publication asset size changed: {name}")
            if sha256_file(asset_path) != item["sha256"]:
                raise ReleaseError(f"Publication asset digest changed: {name}")
    if roles != {"installable_apk", "sha256_sidecar", "metadata_sidecar"}:
        raise ReleaseError("Publication manifest does not contain the exact maintained asset roles")
    if verify_files:
        allowed = names | {path.name, "release-notes.md", "release-plan.json"}
        actual = {item.name for item in path.parent.iterdir() if item.is_file()}
        unexpected = sorted(actual - allowed)
        if unexpected:
            raise ReleaseError(f"Unplanned files in qualified bundle: {', '.join(unexpected)}")
    return manifest


def classify_assets(
    *,
    expected_assets: list[dict[str, Any]],
    remote_assets: list[dict[str, Any]],
    mode: str,
    release_published: bool,
    replace_existing: bool,
) -> dict[str, list[str]]:
    expected = {item["name"]: item for item in expected_assets}
    if len(expected) != len(expected_assets):
        raise ReleaseError("Expected asset set contains duplicate names")
    remote: dict[str, dict[str, Any]] = {}
    for item in remote_assets:
        name = item.get("name")
        if not isinstance(name, str) or name in remote:
            raise ReleaseError(f"Remote Release contains duplicate asset name: {name!r}")
        remote[name] = item
    unexpected = sorted(set(remote) - set(expected))
    if mode == "create_new_release" and unexpected:
        raise ReleaseError(f"New Release contains unplanned assets: {', '.join(unexpected)}")

    result = {"reuse": [], "upload": [], "replace": [], "preserve": unexpected}
    for name, expected_item in expected.items():
        remote_item = remote.get(name)
        if remote_item is None:
            result["upload"].append(name)
            continue
        matches = (
            remote_item.get("state") == "uploaded"
            and remote_item.get("size") == expected_item["size"]
            and remote_item.get("download_sha256") == expected_item["sha256"]
        )
        if matches:
            result["reuse"].append(name)
            continue
        if not replace_existing:
            raise ReleaseError(f"Remote asset differs and replacement is disabled: {name}")
        if release_published:
            raise ReleaseError(f"Published Release asset cannot be replaced safely: {name}")
        result["replace"].append(name)
    return result


def validate_remote_for_plan(
    plan: dict[str, Any], snapshot: RemoteSnapshot
) -> tuple[TagRecord | None, ReleaseRecord | None]:
    """Recheck immutable tag/Release authority immediately before publication work."""
    tags = snapshot.tag_map()
    releases = snapshot.release_map()
    tag = tags.get(plan["tag"])
    release = releases.get(plan["tag"])
    if release is not None and tag is None:
        raise ReleaseError(f"Release {plan['tag']} exists without its matching tag")
    if plan["mode"] == "create_new_release":
        if tag is not None or release is not None:
            raise ReleaseError(
                f"Remote state for {plan['tag']} changed; rerun as repair after inspection"
            )
        return None, None
    if tag is None:
        raise ReleaseError(f"Repair tag disappeared: {plan['tag']}")
    if tag.source_sha != plan["source_sha"]:
        raise ReleaseError(
            f"Immutable tag {plan['tag']} resolves to {tag.source_sha}, not {plan['source_sha']}"
        )
    return tag, release


class GitHubClient:
    """Small REST client with bounded read retries and no mutating-call replay."""

    def __init__(self, token: str, repository: str, *, timeout: int = 30) -> None:
        if not token:
            raise ReleaseError("GITHUB_TOKEN is required")
        if re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository) is None:
            raise ReleaseError("GITHUB_REPOSITORY must be owner/name")
        self.token = token
        self.repository = repository
        self.timeout = timeout
        self.api = "https://api.github.com"
        self.uploads = "https://uploads.github.com"
        self.opener = urllib.request.build_opener(SafeRedirectHandler())

    def _request(
        self,
        method: str,
        url: str,
        *,
        body: bytes | None = None,
        accept: str = "application/vnd.github+json",
        content_type: str | None = None,
    ) -> tuple[bytes, Any]:
        headers = {
            "Accept": accept,
            "Authorization": f"Bearer {self.token}",
            "User-Agent": "ts-theme-release/1",
            "X-GitHub-Api-Version": "2022-11-28",
        }
        if content_type:
            headers["Content-Type"] = content_type
        attempts = 3 if method == "GET" else 1
        for attempt in range(attempts):
            request = urllib.request.Request(url, data=body, headers=headers, method=method)
            try:
                with self.opener.open(request, timeout=self.timeout) as response:
                    return response.read(), response.headers
            except urllib.error.HTTPError as exc:
                detail = exc.read(4096).decode("utf-8", "replace")
                transient = exc.code in {429, 500, 502, 503, 504}
                if transient and method == "GET" and attempt + 1 < attempts:
                    time.sleep(2**attempt)
                    continue
                raise ReleaseError(f"GitHub API {method} {url} failed ({exc.code}): {detail}") from exc
            except (urllib.error.URLError, TimeoutError) as exc:
                if method == "GET" and attempt + 1 < attempts:
                    time.sleep(2**attempt)
                    continue
                raise ReleaseError(f"GitHub API {method} {url} failed: {exc}") from exc
        raise AssertionError("unreachable")

    def json(
        self, method: str, path: str, payload: dict[str, Any] | None = None
    ) -> tuple[Any, Any]:
        body = json.dumps(payload, separators=(",", ":")).encode() if payload is not None else None
        raw, headers = self._request(
            method,
            f"{self.api}{path}",
            body=body,
            content_type="application/json" if body is not None else None,
        )
        if not raw:
            return None, headers
        try:
            return json.loads(raw), headers
        except json.JSONDecodeError as exc:
            raise ReleaseError(f"GitHub returned invalid JSON for {path}") from exc

    def paginate(self, path: str, *, max_pages: int = 20) -> list[Any]:
        url = f"{self.api}{path}"
        result: list[Any] = []
        for _ in range(max_pages):
            raw, headers = self._request("GET", url)
            try:
                page = json.loads(raw)
            except json.JSONDecodeError as exc:
                raise ReleaseError(f"GitHub returned invalid paginated JSON for {url}") from exc
            if not isinstance(page, list):
                raise ReleaseError(f"GitHub pagination endpoint returned a non-list: {url}")
            result.extend(page)
            next_url = parse_next_link(headers.get("Link", ""))
            if not next_url:
                return result
            url = next_url
        raise ReleaseError(f"GitHub pagination exceeded {max_pages} pages")

    def snapshot(self) -> RemoteSnapshot:
        tag_payloads = self.paginate(f"/repos/{self.repository}/tags?per_page=100")
        release_payloads = self.paginate(f"/repos/{self.repository}/releases?per_page=100")
        tags = tuple(
            TagRecord(name=item["name"], source_sha=item["commit"]["sha"])
            for item in tag_payloads
        )
        releases = tuple(
            ReleaseRecord(
                release_id=int(item["id"]),
                tag=item["tag_name"],
                draft=bool(item["draft"]),
                prerelease=bool(item["prerelease"]),
                html_url=item.get("html_url", ""),
            )
            for item in release_payloads
        )
        return RemoteSnapshot(tags=tags, releases=releases)

    def list_assets(self, release_id: int) -> list[dict[str, Any]]:
        return self.paginate(
            f"/repos/{self.repository}/releases/{release_id}/assets?per_page=100"
        )

    def download_asset(self, asset_id: int, destination: Path) -> str:
        raw, _ = self._request(
            "GET",
            f"{self.api}/repos/{self.repository}/releases/assets/{asset_id}",
            accept="application/octet-stream",
        )
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(raw)
        return hashlib.sha256(raw).hexdigest()

    def create_tag(self, tag: str, source_sha: str) -> None:
        self.json(
            "POST",
            f"/repos/{self.repository}/git/refs",
            {"ref": f"refs/tags/{tag}", "sha": source_sha},
        )

    def create_draft_release(self, tag: str, source_sha: str, name: str, body: str) -> dict[str, Any]:
        result, _ = self.json(
            "POST",
            f"/repos/{self.repository}/releases",
            {
                "tag_name": tag,
                "target_commitish": source_sha,
                "name": name,
                "body": body,
                "draft": True,
                "prerelease": False,
            },
        )
        return result

    def delete_asset(self, asset_id: int) -> None:
        self.json("DELETE", f"/repos/{self.repository}/releases/assets/{asset_id}")

    def upload_asset(self, release_id: int, name: str, path: Path) -> dict[str, Any]:
        encoded = urllib.parse.urlencode({"name": name})
        raw, _ = self._request(
            "POST",
            f"{self.uploads}/repos/{self.repository}/releases/{release_id}/assets?{encoded}",
            body=path.read_bytes(),
            content_type="application/octet-stream",
        )
        try:
            return json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ReleaseError(f"GitHub returned invalid JSON while uploading {name}") from exc

    def update_release_state(
        self, release_id: int, state: str, *, name: str, body: str
    ) -> dict[str, Any]:
        payload = {
            "name": name,
            "body": body,
            "draft": state == "draft",
            "prerelease": state == "prerelease",
        }
        result, _ = self.json(
            "PATCH", f"/repos/{self.repository}/releases/{release_id}", payload
        )
        return result


def parse_next_link(value: str) -> str | None:
    for part in value.split(","):
        match = re.match(r'\s*<([^>]+)>;\s*rel="([^"]+)"', part)
        if match and match.group(2) == "next":
            url = match.group(1)
            if not url.startswith("https://api.github.com/"):
                raise ReleaseError("GitHub pagination escaped api.github.com")
            return url
    return None


class SafeRedirectHandler(urllib.request.HTTPRedirectHandler):
    """Do not forward the GitHub bearer token to signed cross-host asset URLs."""

    def redirect_request(self, req: Any, fp: Any, code: int, msg: str, headers: Any, newurl: str) -> Any:
        if urllib.parse.urlsplit(newurl).scheme != "https":
            raise ReleaseError("GitHub attempted a non-HTTPS redirect")
        redirected = super().redirect_request(req, fp, code, msg, headers, newurl)
        if redirected is None:
            return None
        if urllib.parse.urlsplit(req.full_url).netloc != urllib.parse.urlsplit(newurl).netloc:
            redirected.remove_header("Authorization")
        return redirected


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp")
    temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    os.replace(temporary, path)
