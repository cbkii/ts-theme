#!/usr/bin/env python3
"""Read-only evidence indexer for TS18 Execution Preflight; no device access.

A successful collection/manifest is not evidence that an Android feature works.
Requires Python 3.9+ and the completed run folders or generated .tar.gz.
Does not print raw log lines or video/precise-location content.
"""
from __future__ import annotations
import argparse
from collections import Counter
import csv
import io
import json
from pathlib import Path, PurePosixPath
import re
import tarfile
from typing import Iterator

NEEDED = {"SUMMARY.txt", "preflight/PACKAGE_SCOPE.tsv", "preflight/CAPABILITY_MATRIX.tsv",
          "preflight/PROBE_RUN.txt", "events.tsv", "status.tsv", "video-timeline.tsv",
          "MANIFEST.sha256", "manifest-check.txt"}
MAX_SMALL = 1_000_000

def safe_parts(name: str) -> list[str]:
    p = PurePosixPath(name)
    if p.is_absolute() or any(part in ('..', '') for part in p.parts):
        raise ValueError(f"Unsafe archive entry: {name}")
    return list(p.parts)

def iter_material(path: Path) -> Iterator[tuple[str, bytes]]:
    if path.is_dir():
        for file in sorted(path.rglob('*')):
            if not file.is_file() or file.is_symlink():
                continue
            parts = file.relative_to(path).parts
            if len(parts) >= 2 and parts[-1] in {"SUMMARY.txt", "events.tsv", "status.tsv", "video-timeline.tsv", "MANIFEST.sha256", "manifest-check.txt"} or (
                len(parts) >= 3 and '/'.join(parts[-2:]) in NEEDED
            ):
                if file.stat().st_size <= MAX_SMALL:
                    yield str(file.relative_to(path)), file.read_bytes()
    else:
        with tarfile.open(path, 'r:gz') as tar:
            for m in tar:
                parts = safe_parts(m.name)
                if not m.isfile() or m.issym() or m.size > MAX_SMALL:
                    continue
                if not parts or not parts[0].startswith('boot-'):
                    continue
                relative = '/'.join(parts[1:])
                if relative not in NEEDED:
                    continue
                f = tar.extractfile(m)
                if f is not None:
                    yield '/'.join(parts), f.read(MAX_SMALL + 1)

def select_run(path: str):
    for i, part in enumerate(path.split('/')):
        if re.fullmatch(r'boot-[123]-[a-f0-9-]+', part):
            return part, '/'.join(path.split('/')[i+1:])
    return None, None

def parse_lines(text: bytes) -> dict[str, str]:
    lines=text.decode('utf-8','replace').splitlines()
    return dict(line.split('=',1) for line in lines if '=' in line and not line.startswith('#'))

def parse_tsv(text: bytes):
    return list(csv.reader(io.StringIO(text.decode('utf8','replace')),delimiter='\t'))

def analyse(path: Path) -> dict:
    runs: dict = {}
    for name, raw in iter_material(path):
        run, rel = select_run(name)
        if not run: continue
        r = runs.setdefault(run, {'run':run,'summary':{},'package_scope':{},'capabilities':{},'observed_events':Counter(),
                                  'probe_status':Counter(),'video_segments':[], 'manifest_present':False,'manifest_report_present':False})
        if rel=='SUMMARY.txt': r['summary']=parse_lines(raw)
        elif rel=='preflight/PACKAGE_SCOPE.tsv':
            for row in parse_tsv(raw):
                if len(row)>=2:r['package_scope'][row[0]]=row[1]
        elif rel=='preflight/CAPABILITY_MATRIX.tsv':
            for row in parse_tsv(raw)[1:]:
                if len(row)>=4:r['capabilities'][row[0]]={'status':row[1],'artifact':row[2],'limit':row[3]}
        elif rel=='events.tsv':
            for row in parse_tsv(raw):
                if len(row)>=2:r['observed_events'][row[1]]+=1
        elif rel=='status.tsv':
            for row in parse_tsv(raw):
                if len(row)>=3:r['probe_status'][row[2]]+=1
        elif rel=='video-timeline.tsv':
            r['video_segments']=[{'uptime': x[0], 'detail': x[1]} for x in parse_tsv(raw) if len(x)==2]
        elif rel=='MANIFEST.sha256':r['manifest_present']=True
        elif rel=='manifest-check.txt':r['manifest_report_present']=True
    if len(runs)>3:
        raise ValueError(f'Expected <=3 run directories, got {len(runs)}; archive may be mixed')
    normalized=[]
    for name,r in sorted(runs.items()):
        r['observed_events']=dict(r['observed_events']);r['probe_status']=dict(r['probe_status'])
        r['packages_with_pm_paths']=sorted(pkg for pkg,status in r['package_scope'].items() if status=='OBSERVED_PATH')
        r['package_queries_without_pm_path']=sorted(pkg for pkg,status in r['package_scope'].items() if status!='OBSERVED_PATH')
        r['critical_gaps']={k:v['status'] for k,v in r['capabilities'].items() if k in {
            'M2_SESSIONS','M2_TRANSPORT','TOPWAY_RADIO_CONTROL','GNSS_TTFF','WAKE_ACC_ONLY'}}
        normalized.append(r)
    return {'source':str(path),'run_count':len(normalized),'runs':normalized,
            'qualifications':{'M2_transport_actions':'NOT_RUN by observer','stock_tuner_mutations':'NOT_RUN by observer',
                              'GNSS_fresh_fix':'NOT_RUN by observer','audible_output':'NOT_RUN by observer',
                              'ACC_sleep_wake':'NOT_RUN unless externally recorded','device_qualification':'NOT_RUN by builder'},
            'note':'Manifest presence does not prove integrity. Verify MANIFEST.sha256 and SHA-256 archive separately.'}

def markdown(report: dict):
    out=['# TS18 execution preflight — offline evidence index','',
         f"Run folders found: **{report['run_count']}**. Results are observations, not launcher functional PASS.",'']
    for r in report['runs']:
        s=r['summary']
        out += [f"## {r['run']}",'',f"- Boot ID: `{s.get('boot_id','UNKNOWN')}`; attempt `{s.get('attempt','UNKNOWN')}`; reason `{s.get('reason','UNKNOWN')}`.",
                f"- Capture uptime: {s.get('start_uptime','?')}–{s.get('stop_uptime','?')} seconds. HOME event: {s.get('home_event_uptime','UNKNOWN')}.",
                f"- Structurally valid MP4 segments reported: {s.get('video_structurally_valid','UNKNOWN')}; decode/content NOT_RUN.",
                f"- Manifest generated: {r['manifest_present']}; verification report present: {r['manifest_report_present']} (independently recheck before use).",
                f"- Packages with a resolved PM path in inspected scope: {', '.join(r['packages_with_pm_paths']) or '(none)'}.",
                f"- Probe classifications: {dict(r['probe_status'])}.",'',
                '| Technical gap | Observer classification |','|---|---|']
        for k,v in r['critical_gaps'].items():out.append(f'| `{k}` | {v} |')
        out.append('')
    out += ['## Unresolved regardless of successful capture','',
            'A normal Magisk-root snapshot cannot establish that the launcher UID can invoke a private OEM binder, '
            'that a map has usable pixels/input, that audio is audible, or that Organic Maps received '
            'a fresh GNSS fix. Those require appropriately scoped POC/runtime and physical tests.','',
            '**Privacy:** The raw archive may contain locations, accounts or notifications. Review before sharing.','']
    return '\n'.join(out)

def main():
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('input',type=Path,help='completed three-run tar.gz OR Downloads evidence directory')
    ap.add_argument('--output',type=Path,default=Path('TS18-Validation-v3.6.0-Analysis.md'))
    args=ap.parse_args()
    report=analyse(args.input)
    args.output.write_text(markdown(report),encoding='utf-8')
    args.output.with_suffix('.json').write_text(json.dumps(report,indent=2,sort_keys=True),encoding='utf-8')
    print(f"Wrote {args.output} and {args.output.with_suffix('.json')}; run_count={report['run_count']}")

if __name__=='__main__':main()
