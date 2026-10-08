"""Checks the publication guard without contacting GitHub or changing release files."""
import importlib.util
import json
from pathlib import Path
import shutil
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("publisher", ROOT / "scripts/publish-release.py")
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class ReleasePackageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="touchxbox-publication-test-")
        self.directory = Path(self.temp.name)
        self.metadata = json.loads((ROOT / "release/latest.json").read_text(encoding="utf-8"))
        self.tag = "v" + self.metadata["versionName"]
        shutil.copyfile(ROOT / "release" / self.metadata["apkFile"], self.directory / self.metadata["apkFile"])

    def tearDown(self):
        self.temp.cleanup()

    def validate(self):
        (self.directory / "latest.json").write_text(json.dumps(self.metadata), encoding="utf-8")
        return publisher.validate(self.directory, self.tag)

    def test_current_release_is_valid(self):
        metadata, apk, _ = self.validate()
        self.assertEqual(apk.stat().st_size, metadata["size"])

    def test_modified_package_is_rejected(self):
        apk = self.directory / self.metadata["apkFile"]
        data = bytearray(apk.read_bytes())
        data[20] ^= 1
        apk.write_bytes(data)
        with self.assertRaisesRegex(ValueError, "SHA-256"):
            self.validate()

    def test_truncated_package_is_rejected(self):
        self.metadata["size"] += 1
        with self.assertRaisesRegex(ValueError, "size"):
            self.validate()

    def test_wrong_tag_is_rejected(self):
        self.tag = "v0.0.0"
        with self.assertRaisesRegex(ValueError, "tag"):
            self.validate()

    def test_unsafe_filename_is_rejected(self):
        self.metadata["apkFile"] = "../different.apk"
        with self.assertRaisesRegex(ValueError, "filename"):
            self.validate()

    def test_different_source_version_is_rejected(self):
        self.metadata["versionCode"] += 1
        with self.assertRaisesRegex(ValueError, "version code"):
            self.validate()


if __name__ == "__main__":
    unittest.main()
