import hashlib
import importlib.util
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / 'scripts/ci/reconcile_testing_release.py'
spec = importlib.util.spec_from_file_location('reconcile_testing_release', HELPER)
notes = importlib.util.module_from_spec(spec)
spec.loader.exec_module(notes)


class TestingReleaseNoteReconciliationTests(unittest.TestCase):
    def fixture(self, count=2):
        repo = 'cbkii/ts-theme'
        assets, blobs = [], {}
        for index in range(count):
            source = ('a' if index == 0 else 'b') * 40
            group = f'PR{26 + index}-{source[:7]}'
            prefix = 'TS18-Standalone-Launcher'
            names = [f'{prefix}-TESTING-{group}.apk', f'{prefix}-DEBUG-{group}.apk',
                     f'BUILD_INFO-{group}.txt', f'SIGNER-{group}.txt',
                     f'SHA256SUMS-{group}.txt']
            data = (f'repository={repo}\nsource_sha={source}\nbuilt_sha={source}\n'
                    f'pr_number={26 + index}\ngroup_id={group}\n'
                    f'install_asset={names[0]}\ndebug_asset={names[1]}\n'
                    f'metadata_asset={names[2]}\nsigner_asset={names[3]}\n'
                    f'checksums_asset={names[4]}\nchange_1=Cold-start improvement\n').encode()
            signer = b'signer'
            for part, name in enumerate(names):
                asset = {'name': name, 'id': len(assets) + 1,
                         'created_at': f'2026-10-2{index}T00:00:00Z',
                         'state': 'uploaded', 'size': 100,
                         'browser_download_url':
                         f'https://github.com/{repo}/releases/download/untagged-abc/{name}'}
                assets.append(asset)
                if part == 2:
                    blobs[asset['id']] = data
                if part == 3:
                    blobs[asset['id']] = signer
            sums = '\n'.join(
                f'{hashlib.sha256(data if part == 2 else signer if part == 3 else bytes([part])).hexdigest()}  {names[part]}'
                for part in range(4)) + '\n'
            blobs[assets[-1]['id']] = sums.encode()
        release = {'draft': True, 'name': '000 Testing Only Version',
                   'tag_name': 'untagged-31fecdcb8d777c1a4184',
                   'body': f'{notes.MANAGED}\nDo not publish this draft\n'}
        return repo, release, assets, blobs

    def run_fixture(self, *, count=2, body=None, mutate=None):
        repo, release, assets, blobs = self.fixture(count)
        if body is not None:
            release['body'] = body
        if mutate:
            mutate(release, assets, blobs)
        writes = []

        def fake_gh(path, *, patch=None):
            if patch:
                writes.append(patch)
                release.update(patch)
            return assets if path.endswith('/assets?per_page=100') else release

        with patch.object(notes, 'gh', fake_gh), patch.object(
                notes, 'download', lambda _, asset: blobs[asset['id']]):
            notes.reconcile(repo, 6)
            notes.reconcile(repo, 6)
        return release, writes

    def test_two_snapshots_are_rendered_and_repair_is_idempotent(self):
        release, writes = self.run_fixture()
        self.assertEqual(len(writes), 1)
        self.assertEqual(release['body'].count('Cold-start improvement'), 2)
        self.assertEqual(release['body'].count('TESTING APK]('), 2)
        self.assertIn(notes.BEGIN, release['body'])
        self.assertIn(notes.END, release['body'])

    def test_manual_content_outside_anchors_is_preserved(self):
        body = (f'Manual introduction\n{notes.MANAGED}\n{notes.BEGIN}\n'
                f'stale\n{notes.END}\nManual appendix\n')
        release, _ = self.run_fixture(count=1, body=body)
        self.assertTrue(release['body'].startswith('Manual introduction'))
        self.assertTrue(release['body'].endswith('Manual appendix\n'))
        self.assertNotIn('stale', release['body'])

    def test_fails_on_missing_asset(self):
        with self.assertRaisesRegex(ValueError, 'missing/unready'):
            self.run_fixture(mutate=lambda release, assets, blobs: assets.pop(0))

    def test_fails_on_changed_metadata_checksum(self):
        def tamper(release, assets, blobs):
            info = next(a for a in assets if a['name'].startswith('BUILD_INFO'))
            blobs[info['id']] += b'change_2=Tampered\n'
        with self.assertRaisesRegex(ValueError, 'BUILD_INFO checksum mismatch'):
            self.run_fixture(mutate=tamper)

    def test_fails_on_orphaned_managed_asset(self):
        def orphan(release, assets, blobs):
            extra = dict(assets[0], name='BUILD_INFO-PR55-1234567.txt')
            assets.append(extra)
        with self.assertRaisesRegex(ValueError, 'Expected 1–2 retained'):
            self.run_fixture(mutate=orphan)

    def test_unrecognised_unanchored_text_is_not_overwritten(self):
        with self.assertRaisesRegex(ValueError, 'Unanchored body'):
            self.run_fixture(body=f'{notes.MANAGED}\nCustom release description')

    def test_broken_anchor_is_not_overwritten(self):
        with self.assertRaisesRegex(ValueError, 'Incomplete or duplicated'):
            self.run_fixture(body=f'{notes.MANAGED}\n{notes.BEGIN}\n')


if __name__ == '__main__':
    unittest.main()
