"""Checks the publication guard without contacting GitHub or changing release files."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parent.parent
PACKAGE_DIR = Path(os.environ.get("TOUCHXBOX_TEST_PACKAGE_DIR", ROOT / "release"))
spec = importlib.util.spec_from_file_location("publisher", ROOT / "scripts/publish-release.py")
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class ReleasePackageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="touchxbox-publication-test-")
        self.directory = Path(self.temp.name)
        self.metadata = json.loads((PACKAGE_DIR / "latest.json").read_text(encoding="utf-8"))
        self.tag = "v" + self.metadata["versionName"]
        shutil.copyfile(PACKAGE_DIR / self.metadata["apkFile"], self.directory / self.metadata["apkFile"])

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

    def test_release_includes_standalone_cmd(self):
        _, apk, manifest = self.validate()
        files = publisher.release_assets(apk, manifest)
        self.assertEqual([path.name for path in files], [apk.name, "latest.json", "TouchXbox-authorize.cmd"])

    def test_missing_cmd_blocks_publication(self):
        with mock.patch.object(publisher, "ROOT", self.directory):
            with self.assertRaisesRegex(ValueError, "Missing.*CMD"):
                publisher.release_assets(self.directory / "app.apk", self.directory / "latest.json")

    def test_empty_or_invalid_cmd_blocks_publication(self):
        script = self.directory / "TouchXbox-authorize.cmd"
        for data in (b"", b"not a cmd", b"@echo off\n#<TOUCHXBOX_POWERSHELL> powershell.exe", b"@echo off\r\n"):
            script.write_bytes(data)
            with mock.patch.object(publisher, "ROOT", self.directory):
                with self.assertRaisesRegex(ValueError, "CMD"):
                    publisher.release_assets(self.directory / "app.apk", self.directory / "latest.json")

    def test_uploaded_cmd_is_verified_with_other_assets(self):
        _, apk, manifest = self.validate()
        files = publisher.release_assets(apk, manifest)
        requested = []
        def download(*args):
            name = args[args.index("--pattern") + 1]
            destination = Path(args[args.index("--dir") + 1]) / name
            requested.append(name)
            source = next(file for file in files if file.name == name)
            shutil.copyfile(source, destination)
        with mock.patch.object(publisher, "gh", side_effect=download):
            publisher.verify_uploaded(self.tag, files)
        self.assertEqual(requested, [file.name for file in files])

    def test_corrupt_uploaded_cmd_is_rejected(self):
        script = ROOT / "TouchXbox-authorize.cmd"
        def download(*args):
            destination = Path(args[args.index("--dir") + 1]) / script.name
            destination.write_bytes(b"damaged download")
        with mock.patch.object(publisher, "gh", side_effect=download):
            with self.assertRaisesRegex(ValueError, "Uploaded file differs.*cmd"):
                publisher.verify_uploaded(self.tag, [script])


if __name__ == "__main__":
    unittest.main()
