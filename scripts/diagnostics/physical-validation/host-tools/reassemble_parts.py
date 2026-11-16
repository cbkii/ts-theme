#!/usr/bin/env python3
"""Reassemble TS18 bounded archive parts OFF DEVICE after SHA-256 validation.

Usage: python3 reassemble_parts.py <Download/TS18-Validation-v3.6.0> <third-boot-id>
Creates the corresponding .tar.gz only on the host, never writes to TS18.
"""
from __future__ import annotations
import hashlib
from pathlib import Path
import re
import sys
import tarfile


def reassemble(directory: Path, third_boot_id: str) -> Path:
    if not re.fullmatch(r'[a-f0-9-]+', third_boot_id):
        raise ValueError('invalid boot ID')
    prefix = f'TS18-Validation-v3.6.0-{third_boot_id}.tar.gz.part-'
    manifest = directory / f'{prefix}MANIFEST.sha256'
    lines = manifest.read_text(encoding='utf-8').splitlines()
    expected = {}
    for line in lines:
        m = re.fullmatch(r'([a-f0-9]{64})  (TS18-Validation-v3.6.0-[a-f0-9-]+\.tar\.gz\.part-\d{3})', line)
        if not m or not m.group(2).startswith(prefix) or m.group(2) in expected:
            raise ValueError('invalid part manifest')
        expected[m.group(2)] = m.group(1)
    names = sorted(expected)
    if not (1 <= len(names) <= 5) or names != [f'{prefix}{i:03d}' for i in range(len(names))]:
        raise ValueError('missing, duplicate, or out-of-order part')
    total = 0
    for name in names:
        part = directory / name
        if not part.is_file() or part.is_symlink():
            raise ValueError(f'not a normal part file: {name}')
        size = part.stat().st_size
        if not (0 < size <= 20_000_000):
            raise ValueError(f'part too large or empty: {name}')
        total += size
        with part.open('rb') as fh:
            h = hashlib.sha256()
            while chunk := fh.read(1024 * 1024):
                h.update(chunk)
            digest = h.hexdigest()
        if digest != expected[name]:
            raise ValueError(f'SHA mismatch: {name}')
    if total > 100_000_000:
        raise ValueError('combined archive is oversized')
    output = directory / f'TS18-Validation-v3.6.0-{third_boot_id}.tar.gz'
    if output.exists():
        raise FileExistsError(f'will not replace existing output: {output}')
    temp = directory / f'.reassembling-{third_boot_id}.partial'
    try:
        with temp.open('xb') as dest:
            for name in names:
                with (directory / name).open('rb') as src:
                    while buf := src.read(1024 * 1024):
                        dest.write(buf)
        with tarfile.open(temp, 'r:gz') as tf:
            members = tf.getmembers()
            if not members or len(members) > 1500:
                raise ValueError('empty or unexpectedly large member list')
            if any(m.issym() or m.islnk() or m.name.startswith('/') or '..' in Path(m.name).parts for m in members):
                raise ValueError('unsafe tar member')
        temp.rename(output)
    finally:
        if temp.exists():
            temp.unlink()
    return output


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    try:
        result = reassemble(Path(sys.argv[1]), sys.argv[2])
    except (ValueError, OSError, tarfile.TarError) as err:
        print(f'BLOCKED: {err}', file=sys.stderr)
        return 1
    print(f'PASS: verified {result} ({result.stat().st_size} bytes)')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
