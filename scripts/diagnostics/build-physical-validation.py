#!/usr/bin/env python3
"""Package the passive physical module only with a complete final APK selection."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parent / 'physical-validation'
PACKAGES = {'com.cbkii.ts18launcher', 'com.tw.media', 'app.organicmaps.incar'}


def build(selection: Path, output: Path, collector_source: str) -> str:
    rows = {}
    for line in selection.read_text().splitlines():
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        fields = line.split()
        if len(fields) != 3:
            raise ValueError('selection needs package, full source SHA and APK SHA-256')
        package, source, digest = fields
        if package not in PACKAGES or package in rows:
            raise ValueError('unknown or duplicate package')
        if not re.fullmatch('[a-f0-9]{40}', source) or not re.fullmatch('[a-f0-9]{64}', digest):
            raise ValueError('unpinned source/APK identity')
        rows[package] = (source, digest)
    if set(rows) != PACKAGES or not re.fullmatch('[a-f0-9]{40}', collector_source):
        raise ValueError('complete selection and collector source SHA required')
    content = {}
    for path in sorted(ROOT.rglob('*')):
        if path.is_symlink():
            raise ValueError('module source contains symlink')
        if not path.is_file() or '__pycache__' in path.parts or path.suffix == '.pyc':
            continue
        name = path.relative_to(ROOT).as_posix()
        if name in {'SOURCES.sha256', 'expected-apks.tsv'}:
            continue
        content[name] = path.read_bytes()
    content['expected-apks.tsv'] = ''.join(
        f'{p} {rows[p][0]} {rows[p][1]}\n' for p in sorted(rows)).encode()
    content['SOURCE-PROVENANCE.json'] = (json.dumps({
        'collector_source': collector_source,
        'application_selection': {p: {'source': v[0], 'apk_sha256': v[1]} for p, v in rows.items()},
        'physical_acceptance': 'NOT_RUN_BY_BUILDER',
    }, indent=2) + '\n').encode()
    content['SOURCES.sha256'] = ''.join(
        f'{hashlib.sha256(data).hexdigest()}  {name}\n'
        for name, data in sorted(content.items())).encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive create preserves any earlier valid deliverable on packaging failure/retry.
    try:
        with output.open('xb') as stream, zipfile.ZipFile(stream, 'w', zipfile.ZIP_DEFLATED) as archive:
            for name, data in sorted(content.items()):
                info = zipfile.ZipInfo(name, (2026, 11, 17, 0, 0, 0))
                info.create_system = 3
                mode = 0o755 if name.endswith('.sh') else 0o644
                info.external_attr = (0o100000 | mode) << 16
                info.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(info, data)
        with zipfile.ZipFile(output) as archive:
            if archive.testzip() is not None:
                raise ValueError('ZIP CRC verification failed')
    except FileExistsError:
        raise
    except Exception:
        output.unlink(missing_ok=True)
        raise
    return hashlib.sha256(output.read_bytes()).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--selection', required=True, type=Path)
    parser.add_argument('--collector-source', required=True)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    print(build(args.selection, args.output, args.collector_source))


if __name__ == '__main__':
    main()
