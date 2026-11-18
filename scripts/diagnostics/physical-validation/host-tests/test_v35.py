#!/usr/bin/env python3
"""v3.6.0-specific passive TESTING campaign contract tests. Host-only."""
import hashlib
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest
import os

ROOT = Path(__file__).resolve().parents[1]

class ContractTests(unittest.TestCase):
    def test_new_apk_sources_and_expected_digest_literals(self):
        records = {}
        for line in (ROOT/'expected-apks.tsv').read_text().splitlines():
            if not line or line.startswith('#'):
                continue
            package, source, digest = line.split()
            if source != 'PENDING_BUILD':
                self.assertEqual(len(source), 40)
                self.assertEqual(len(digest), 64)
            else:
                self.assertEqual(digest, 'PENDING_BUILD')
            self.assertNotIn(package, records)
            records[package] = (source,digest)
        self.assertEqual(set(records), {'com.cbkii.ts18launcher', 'com.tw.media', 'app.organicmaps.incar'})

    def test_boot_hooks_remain_unattended_no_automation(self):
        for script in ('post-fs-data.sh','service.sh'):
            content=(ROOT/script).read_text()
            self.assertIn('setsid',content)
            self.assertIn('collector.sh',content)
            self.assertNotIn('input keyevent',content)
        content=(ROOT/'collector.sh').read_text()
        self.assertIn('[ "$count" -lt 3 ] || exit 0',content)
        self.assertIn('RUN_BUDGET_BYTES=32000000',content)
        self.assertIn('validation) MAX_VIDEO=7',content)
        self.assertIn('lean) [ "$count" -eq 3 ]',content)
        self.assertIn('VTRIES=$MAX_VIDEO',content)
        self.assertIn('start_tasks',content)
        self.assertIn('start_framework',content)
        self.assertIn('LOG-STREAM-STATUS.tsv',content)
        self.assertIn('NEXT_VIDEO=$(up)',content)
        self.assertIn('VERIFIED_READ_WRITE_RENAME',content)

    def test_event_capture_retains_native_writer_and_stop_status(self):
        stream=(ROOT/'stream.sh').read_text()
        self.assertIn('exec /system/bin/logcat "$@" -f "$dest"',stream)
        self.assertIn('producer_exit=',stream)
        self.assertIn('trap on_stop TERM HUP INT',stream)
        self.assertIn('task) set -- -b events',stream)
        for event in ('am_finish_activity','am_destroy_activity','am_create_activity','am_task_removed'):
            self.assertIn(event+':I',stream)
            self.assertIn(event,(ROOT/'ai-summary.sh').read_text())
        self.assertIn('capture "$OUT/snapshots/$n-recents.txt"',(ROOT/'snapshots.sh').read_text())
        self.assertNotIn('| b awk',stream)

    def test_three_run_bundle_with_three_parts(self):
        import sys
        sys.path.insert(0,str(ROOT/'host-tests'))
        from test_v32 import fixture,invoke_bundle
        from sys import path
        sys.path.insert(0,str(ROOT/'host-tools'))
        from reassemble_parts import reassemble
        with tempfile.TemporaryDirectory() as td:
            base=Path(td)
            root=fixture(base,size=0)
            # 3 ~13.4MB noncompressible inputs => needs all three output parts.
            for i,name in ((1,'aabb-0011'),(2,'ccdd-0022'),(3,'eeff-0033')):
                run=root/f'boot-{i}-{name}'
                data=run/'sample.bin';data.write_bytes(os.urandom(13_800_000))
                (run/'MANIFEST.sha256').write_text(''.join(hashlib.sha256((run/n).read_bytes()).hexdigest()+f'  ./{n}\n' for n in ('SUMMARY.txt','sample.bin')))
            command=invoke_bundle(base)
            result=subprocess.run(['dash',str(command),str(root),'eeff-0033'],capture_output=True,text=True,timeout=80)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)
            parts=sorted(root.glob('*.part-[0-9]*'))
            self.assertEqual(len(parts),3)
            self.assertLessEqual(sum(x.stat().st_size for x in parts),42_000_000)
            bundle=reassemble(root,'eeff-0033')
            with tarfile.open(bundle,'r:gz') as tar:
                for i,name in ((1,'aabb-0011'),(2,'ccdd-0022'),(3,'eeff-0033')):
                    self.assertIn(f'boot-{i}-{name}/SUMMARY.txt',tar.getnames())

    def test_no_implied_app_mutations_or_second_collector(self):
        code='\n'.join((ROOT/x).read_text() for x in ('collector.sh','preflight.sh','snapshots.sh','stream.sh','record.sh'))
        for forbidden in ('/system/bin/input keyevent','/system/bin/am start','/system/bin/pm clear','/system/bin/settings put','/system/bin/setenforce','/system/bin/service call','/system/bin/reboot','/system/bin/pm install','chmod 777'):
            self.assertNotIn(forbidden,code)
        self.assertNotIn('qualify-consolidated-candidate-reference.sh',code)
        self.assertNotIn('campaign.py',code)

if __name__=='__main__':unittest.main()
