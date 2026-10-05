#!/usr/bin/env python3
"""Package reviewed source, checksums and a clearly synthetic failure example."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('output', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parent
names = ('ts18-startup-1.3.sh', 'capture-lib.sh', 'install.sh', 'analyse.py',
         'README.md', 'QUALIFICATION.md')
input_paths = {(root / name).resolve() for name in names}
output = args.output.resolve()
if output in input_paths:
    parser.error('output must not replace a bundle input')
files = {name: (root / name).read_bytes() for name in names}
files['example-partial.json'] = (json.dumps({
    'example': 'SYNTHETIC, not device evidence',
    'integrity': 'PASS (partial bytes sealed; not all producers succeeded)',
    'qualification': 'NOT_RUN', 'uid_map': 'UNKNOWN', 'uids': {},
    'producer_results': {'PASS': 3, 'WARN_TIMEOUT': 1, 'BLOCKED': 1},
    'producer_example': {'name': 'packages-ready-2', 'producer_rc': 'UNKNOWN',
                         'duration_s': 6, 'result': 'WARN_TIMEOUT'},
    'caller': 'UNKNOWN (target UID is not caller)',
    'outer_su_rc': 124,
    'producer_sentinel': 'COMPLETE; inspect command results independently',
    'export': 'WARN Downloads unavailable; private archive retained'
}, indent=2) + '\n').encode()
files['SHA256SUMS'] = ''.join(f'{hashlib.sha256(data).hexdigest()}  {name}\n'
                            for name, data in sorted(files.items())).encode()
output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_STORED) as archive:
    for name, data in sorted(files.items()):
        item = zipfile.ZipInfo('TS18-diagnostics-1.3/' + name)
        item.date_time = (1980, 1, 1, 0, 0, 0)
        item.create_system = 3
        item.external_attr = 0o100600 << 16
        archive.writestr(item, data, compress_type=zipfile.ZIP_STORED)
print(hashlib.sha256(output.read_bytes()).hexdigest(), output)
