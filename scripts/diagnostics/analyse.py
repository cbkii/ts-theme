#!/usr/bin/env python3
"""Offline, bounded TS18 1.3 analysis. Never extract or execute archive members."""
import argparse
from collections import Counter, defaultdict
import math
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
        for entry in path.rglob('*'):
            if entry.is_symlink():
                raise ValueError('symlinks are not evidence files')
            if entry.is_dir():
                continue
            if not entry.is_file():
                raise ValueError('links/devices/sockets/fifos are not evidence files')
            total += entry.stat().st_size
            if total > MAX_BYTES or len(files) >= MAX_FILES:
                raise ValueError('evidence size/file limit exceeded')
            files[entry.relative_to(path).as_posix()] = entry.read_bytes()
    else:
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
                if stream is None:
                    raise ValueError('unreadable archive member')
                files[relative] = stream.read(member.size + 1)
                if len(files[relative]) != member.size:
                    raise ValueError('short archive member')
    return files


def metadata(raw):
    return dict(line.split('=', 1) for line in raw.decode(errors='replace').splitlines() if '=' in line)


def marker_tuple(raw):
    parts = raw.strip().split(maxsplit=1)
    if len(parts) != 2:
        return None
    try:
        value = float(parts[0])
        return (value, parts[1]) if math.isfinite(value) else None
    except ValueError:
        return None


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
        if metadata(files['COMPLETE.txt']).get('producer') != 'COMPLETE':
            raise ValueError('invalid completion sentinel')
        if not text('SEALED.txt').strip().startswith('PASS'):
            raise ValueError('invalid seal sentinel')
        if any(x in files for x in ('FORCED_STOP.txt', 'UNSEALED.txt', 'CAPTURE_UNSAFE.txt')):
            raise ValueError('partial/forced run')
        result['integrity'] = 'PASS'
    except (ValueError, KeyError) as exc:
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

    counts = Counter()
    markers = []
    phases = []
    for name in files:
        if name.startswith('marker-'):
            raw = text(name).strip()
            parsed = marker_tuple(raw)
            if parsed is not None:
                markers.append((parsed[0], raw))
                phases.append(parsed)
            else:
                result['warnings'].append('ignored malformed marker: ' + name)
    markers.sort(key=lambda item: item[0])
    result['action_markers_uptime'] = [raw for _, raw in markers]
    phases.sort(key=lambda item: item[0])

    label_seen = Counter()
    phase_records = []
    for start, label in phases:
        label_seen[label] += 1
        key = f'{label}#{label_seen[label]}'
        phase_records.append((start, label, key))

    context = metadata(files.get('CONTEXT.txt', b''))
    epoch_offset = None
    try:
        epoch_offset = float(context['start_epoch']) - float(context['start_uptime'])
    except (KeyError, ValueError):
        pass

    audio = Counter()
    suppressed = Counter()
    last_uptime = None
    for line in text('commands/live-log/output.txt').splitlines():
        phase_key = 'UNMARKED#1'
        epoch = re.match(r'\s*(\d+\.\d+)\s', line)
        if epoch and epoch_offset is not None:
            current = float(epoch.group(1)) - epoch_offset
            last_uptime = current
            for start, _label, key in phase_records:
                if current >= start:
                    phase_key = key
                else:
                    break
        if 'chatty' in line:
            suppressed[phase_key] += 1
        if 'remountUidExternalStorage' in line:
            uid = re.search(r'\buid[= :]+(\d+)', line)
            if uid and (re.search(r'\bSTART\b', line, re.I) or re.search(r'Cmd send remountUidExternalStorage uid \d+', line)):
                counts[uid.group(1)] += 1
            elif not re.search(r'\bEND\b', line, re.I):
                result['remount_unclassified_lines'] += 1
        if 'get_presentation_position: Operation not permitted' in line:
            audio[phase_key] += 1

    result['remount_starts'] = dict(counts)
    result['audio_position_lines'] = dict(audio)
    result['chatty_lines'] = dict(suppressed)
    result['audio_observed_rates'] = {}
    for i, (start, label, key) in enumerate(phase_records):
        end = phase_records[i + 1][0] if i + 1 < len(phase_records) else last_uptime
        if end is not None and end > start:
            result['audio_observed_rates'][key] = {
                'label': label,
                'occurrence': int(key.rsplit('#', 1)[1]),
                'seconds': round(end - start, 3),
                'lines_per_second_lower_bound': round(audio[key] / (end - start), 3),
                'chatty_lines': suppressed[key],
            }

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
