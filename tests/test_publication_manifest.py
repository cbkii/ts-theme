import hashlib
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts" / "release"))
from publication_manifest import load_publication_manifest  # noqa: E402
from qualify import deterministic_install_zip  # noqa: E402
from release_lib import ReleaseError, sha256_file  # noqa: E402

class PublicationManifestTests(unittest.TestCase):
    def fixture(self, root: Path, four: bool):
        roles=[("Theme.apk","installable_apk"),("Theme.apk.sha256","sha256_sidecar"),("Theme.apk.metadata.txt","metadata_sidecar")]
        if four: roles.append(("Theme-install-tools.zip","installer_tools"))
        assets=[]
        for name,role in roles:
            payload=role.encode(); (root/name).write_bytes(payload); assets.append({"name":name,"role":role,"destination":"release","size":len(payload),"sha256":hashlib.sha256(payload).hexdigest()})
        (root/"release-notes.md").write_text("notes",encoding="utf-8")
        manifest={"schema_version":1,"product_name":"Theme","tag":"v1.0.0","version_name":"1.0.0","version_code":1_000_000,"source_sha":"a"*40,"package_id":"launcher.variety.theme.plugin.sfp_cbk_black" if four else "legacy.pkg","plugin_id":"sfp_cbk_black" if four else "legacy","signer_sha256":"A"*64,"release_mode":"create_new_release","release_state":"stable","replace_existing_assets":False,"assets":assets}
        path=root/"release-manifest.json"; path.write_text(json.dumps(manifest),encoding="utf-8"); return path
    def test_current_four_asset_manifest(self):
        with tempfile.TemporaryDirectory() as td: load_publication_manifest(self.fixture(Path(td),True))
    def test_legacy_three_asset_manifest_remains_repairable(self):
        with tempfile.TemporaryDirectory() as td: load_publication_manifest(self.fixture(Path(td),False))
    def test_hardened_identity_cannot_drop_installer_tools(self):
        with tempfile.TemporaryDirectory() as td:
            path=self.fixture(Path(td),False); data=json.loads(path.read_text()); data["package_id"]="launcher.variety.theme.plugin.sfp_cbk_black"; path.write_text(json.dumps(data))
            with self.assertRaisesRegex(ReleaseError,"must include installer_tools"): load_publication_manifest(path)
    def test_manifest_rejects_invalid_transaction_state_and_signer(self):
        with tempfile.TemporaryDirectory() as td:
            path=self.fixture(Path(td),True); data=json.loads(path.read_text()); data["release_state"]="published"; path.write_text(json.dumps(data))
            with self.assertRaisesRegex(ReleaseError,"mode or state"): load_publication_manifest(path)
        with tempfile.TemporaryDirectory() as td:
            path=self.fixture(Path(td),True); data=json.loads(path.read_text()); data["signer_sha256"]="bad"; path.write_text(json.dumps(data))
            with self.assertRaisesRegex(ReleaseError,"signer"): load_publication_manifest(path)
    def test_install_tools_zip_is_deterministic_and_contains_no_apk(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td); source=root/"source"; source.mkdir(); (source/"a.sh").write_text("#!/bin/sh\necho ok\n",encoding="utf-8"); (source/"README.md").write_text("readme\n",encoding="utf-8")
            config={"asset_stem":"Theme","install_tools":["a.sh","README.md"]}; install={"schema_version":1,"tag":"v1.2.3","apk_sha256":"a"*64}
            out1=root/"one"; out1.mkdir(); out2=root/"two"; out2.mkdir()
            z1=deterministic_install_zip(source,out1,config,install,"v1.2.3"); z2=deterministic_install_zip(source,out2,config,install,"v1.2.3")
            self.assertIsNotNone(z1); self.assertIsNotNone(z2); self.assertEqual(sha256_file(z1),sha256_file(z2))
            with zipfile.ZipFile(z1) as archive:
                self.assertEqual({"a.sh","README.md","install-manifest.json"},set(archive.namelist())); self.assertFalse(any(name.endswith(".apk") for name in archive.namelist()))
if __name__=="__main__": unittest.main()
