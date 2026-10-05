#!/usr/bin/env python3
"""Offline, bounded TS18 1.3 analysis. Never extract or execute archive members."""
import argparse
from collections import Counter, defaultdict
import hashlib
import gzip
import io
import json
from pathlib import Path, PurePosixPath
import re
import tarfile

MAX_BYTES = 128 * 1024 * 1024
MAX_FILES = 4096

def safe_name(name):
    p = PurePosixPath(name)
    if p.is_absolute() or '..' in p.parts or '\\' in name:
        raise ValueError('unsafe archive/manifest path')
    return str(p)

def load(path):
    files = {}
    total = 0
    if path.is_dir():
        entries = path.rglob('*')
        for entry in entries:
            if entry.is_symlink():
                raise ValueError('symlinks are not evidence files')
            if not entry.is_file():
                continue
            total += entry.stat().st_size
            if total > MAX_BYTES or len(files) >= MAX_FILES:
                raise ValueError('evidence size/file limit exceeded')
            files[entry.relative_to(path).as_posix()] = entry.read_bytes()
    else:
        # Consume gzip trailer (CRC), bounded before parsing untrusted headers.
        with gzip.open(path, 'rb') as compressed:
            limit = MAX_BYTES + MAX_FILES * 1024
            raw = compressed.read(limit + 1)
            if len(raw) > limit:
                raise ValueError('expanded archive limit exceeded')
        with tarfile.open(fileobj=io.BytesIO(raw), mode='r:') as archive:
            root = None
            for member_index, member in enumerate(archive):
                if member_index >= MAX_FILES:
                    raise ValueError('archive member limit exceeded')
                name = safe_name(member.name)
                if member.isdir():
                    continue
                if not member.isfile() or getattr(member, 'sparse', None):
                    raise ValueError('links/devices/sparse members rejected')
                total += member.size
                if total > MAX_BYTES or len(files) >= MAX_FILES:
                    raise ValueError('evidence size/file limit exceeded')
                parts = PurePosixPath(name).parts
                if len(parts) < 2:
                    raise ValueError('archive requires one run directory')
                if root is None:
                    root = parts[0]
                if root != parts[0]:
                    raise ValueError('multiple run directories')
                relative = '/'.join(parts[1:])
                if relative in files:
                    raise ValueError('duplicate archive member')
                stream = archive.extractfile(member)
                files[relative] = stream.read(member.size + 1)
                if len(files[relative]) != member.size:
                    raise ValueError('short archive member')
    return files

def metadata(raw):
    return dict(line.split('=', 1) for line in raw.decode(errors='replace').splitlines() if '=' in line)

def analyse(files):
    def text(name):
        return files.get(name, b'').decode(errors='replace')
    result = {'integrity': 'UNKNOWN', 'qualification': 'NOT_RUN', 'warnings': [],
              'uid_map': 'UNKNOWN', 'uids': {}, 'remount_starts': {},
              'remount_unclassified_lines': 0, 'caller': 'UNKNOWN (target UID is not caller)',
              'memory': 'RSS/VmRSS in samples; PSS only from successful pss-* dumpsys; no equivalence'}
    listed = set()
    try:
        manifest = text('MANIFEST.sha256')
        if not manifest:
            raise ValueError('missing manifest')
        for line in manifest.splitlines():
            digest, name = line.split('  ', 1)
            name = safe_name(name)
            if name in listed or not re.fullmatch('[0-9a-f]{64}', digest):
                raise ValueError('invalid/duplicate manifest entry')
            listed.add(name)
            if name not in files or hashlib.sha256(files[name]).hexdigest() != digest:
                raise ValueError('hash mismatch: ' + name)
        if set(files) - {'MANIFEST.sha256', 'SEALED.txt'} != listed:
            raise ValueError('unmanifested evidence')
        if 'SEALED.txt' not in files or 'COMPLETE.txt' not in listed:
            raise ValueError('missing completion/seal')
        if any(x in files for x in ('FORCED_STOP.txt', 'UNSEALED.txt')):
            raise ValueError('partial/forced run')
        result['integrity'] = 'PASS'
    except ValueError as exc:
        result['integrity'] = 'FAIL'
        result['warnings'].append(str(exc))
    commands = {name[:-9]: metadata(raw) for name, raw in files.items() if name.endswith('/meta.txt')}
    result['producer_results'] = dict(Counter(x.get('result', 'UNKNOWN') for x in commands.values()))
    source = text('UID_MAP_SOURCE.txt').strip()
    uid_meta = commands.get(source, {})
    if result['integrity'] == 'PASS' and uid_meta.get('result') == 'PASS' and uid_meta.get('producer_rc') == '0':
        mapping = defaultdict(list)
        for pkg, uid in re.findall(r'^package:(\S+) uid:(\d+)\s*$', text(source + '/output.txt'), re.M):
            mapping[uid].append(pkg)
        if mapping:
            result['uid_map'] = 'PASS'
            for uid in ('10148', '10186'):
                result['uids'][uid] = sorted(set(mapping.get(uid, []))) or ['UNMAPPED in successful live map']
    # Only live stream: history overlaps it. END must never count as another start.
    counts = Counter()
    audio = Counter()
    markers = []
    for name in files:
        if name.startswith('marker-'):
            markers.append(text(name).strip())
    result['action_markers_uptime'] = sorted(markers)
    context = metadata(files.get('CONTEXT.txt', b''))
    epoch_offset = None
    try:
        epoch_offset = float(context['start_epoch']) - float(context['start_uptime'])
    except (KeyError, ValueError):
        pass
    phases = []
    for marker in markers:
        parts = marker.split(maxsplit=1)
        if len(parts) == 2:
            try:
                phases.append((float(parts[0]), parts[1]))
            except ValueError:
                pass
    phases.sort()
    suppressed = Counter()
    last_uptime = None
    for line in text('commands/live-log/output.txt').splitlines():
        phase = 'UNMARKED'
        epoch = re.match(r'\s*(\d+\.\d+)\s', line)
        if epoch and epoch_offset is not None:
            current = float(epoch.group(1)) - epoch_offset
            last_uptime = current
            for start, label in phases:
                if current >= start:
                    phase = label
        if 'chatty' in line:
            suppressed[phase] += 1
        if 'remountUidExternalStorage' in line:
            uid = re.search(r'\buid[= :]+(\d+)', line)
            if uid and (re.search(r'\bSTART\b', line, re.I) or re.search(r'Cmd send remountUidExternalStorage uid \d+', line)):
                counts[uid.group(1)] += 1
            elif not re.search(r'\bEND\b', line, re.I):
                result['remount_unclassified_lines'] += 1
        if 'get_presentation_position: Operation not permitted' in line:
            audio[phase] += 1
    result['remount_starts'] = dict(counts)
    result['audio_position_lines'] = dict(audio)
    result['chatty_lines'] = dict(suppressed)
    result['audio_observed_rates'] = {}
    for i, (start, label) in enumerate(phases):
        end = phases[i + 1][0] if i + 1 < len(phases) else last_uptime
        if end is not None and end > start:
            result['audio_observed_rates'][label] = {'seconds': round(end - start, 3),
                'lines_per_second_lower_bound': round(audio[label] / (end - start), 3),
                'chatty_lines': suppressed[label]}

    result['warnings'].append('Log suppression/chatty and capture truncation can undercount; lines are not unsuppressed event rates. Correlate epoch logs with uptime/UTC context and manual action markers.')
    if result['integrity'] != 'PASS':
        result['warnings'].append('Counts are partial/untrusted; no absence or attribution conclusions allowed.')
    return result

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture', type=Path)
    parser.add_argument('--out', type=Path)
    args = parser.parse_args()
    try:
        result = analyse(load(args.capture))
    except (ValueError, OSError, EOFError, tarfile.TarError) as exc:
        result = {'integrity': 'FAIL', 'qualification': 'NOT_RUN', 'error': str(exc)}
    rendered = json.dumps(result, indent=2, sort_keys=True) + '\n'
    if args.out:
        args.out.write_text(rendered)
    else:
        print(rendered, end='')
    return 0 if result['integrity'] == 'PASS' else 1

if __name__ == '__main__':
    raise SystemExit(main())
