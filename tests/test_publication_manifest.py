import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts" / "release"))
from publication_manifest import load_publication_manifest  # noqa: E402
from release_lib import ReleaseError  # noqa: E402

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
if __name__=="__main__": unittest.main()
