"""Publish the locally signed APK, without ever uploading a signing key.

The package generator validates the APK signature locally. This workflow checks
the committed metadata and bytes, uploads a draft, verifies uploaded files, and
only then publishes it. Existing public releases are never overwritten.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent.parent
ANDROID = "{http://schemas.android.com/apk/res/android}"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate(package_dir, tag):
    manifest_path = package_dir / "latest.json"
    require(manifest_path.stat().st_size <= 128 * 1024, "Metadata is too large")
    metadata = json.loads(manifest_path.read_text(encoding="utf-8"))
    require(metadata["schemaVersion"] == 1, "Unsupported metadata schema")
    require(metadata["packageName"] == "dev.touchxbox.pad", "Unexpected package name")
    for field in ("versionCode", "minSdk", "size"):
        require(type(metadata[field]) is int and metadata[field] > 0, f"Invalid {field}")
    require(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}", metadata["versionName"]), "Invalid version name")
    require(tag == "v" + metadata["versionName"], "Release tag and APK version do not match")
    require(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,180}\.apk", metadata["apkFile"]), "Unsafe APK filename")
    require(re.fullmatch(r"[0-9a-f]{64}", metadata["sha256"]), "Invalid APK digest")
    require(re.fullmatch(r"[0-9a-f]{64}", metadata["signerSha256"]), "Invalid signing certificate digest")
    require(metadata["size"] <= 95 * 1024 * 1024, "APK exceeds the updater size limit")
    apk = package_dir / metadata["apkFile"]
    require(not apk.is_symlink() and apk.is_file(), "APK must be a regular file")
    require(apk.stat().st_size == metadata["size"], "APK size does not match metadata")
    require(digest(apk) == metadata["sha256"], "APK SHA-256 does not match metadata")
    with zipfile.ZipFile(apk) as archive:
        require({"AndroidManifest.xml", "classes.dex"}.issubset(archive.namelist()), "Not a complete APK")
        require(archive.testzip() is None, "APK archive is damaged")
    source = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
    require(source.attrib["package"] == metadata["packageName"], "Source package differs")
    require(source.attrib[ANDROID + "versionName"] == metadata["versionName"], "Source version name differs")
    require(int(source.attrib[ANDROID + "versionCode"]) == metadata["versionCode"], "Source version code differs")
    require(int(source.find("uses-sdk").attrib[ANDROID + "minSdkVersion"]) == metadata["minSdk"], "Source minimum SDK differs")
    return metadata, apk, manifest_path


def gh(*args, check=True):
    return subprocess.run(["gh", *args], text=True, encoding="utf-8", capture_output=True, check=check)


def verify_uploaded(tag, files):
    with tempfile.TemporaryDirectory(prefix="touchxbox-release-") as directory:
        for file in files:
            gh("release", "download", tag, "--dir", directory, "--pattern", file.name)
            require(digest(Path(directory) / file.name) == digest(file), f"Uploaded file differs: {file.name}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True)
    parser.add_argument("--package-dir", type=Path, default=ROOT / "release")
    parser.add_argument("--validate-only", action="store_true")
    options = parser.parse_args()
    metadata, apk, manifest = validate(options.package_dir, options.tag)
    print(f"Validated {options.tag}: {apk.name} ({metadata['size']} bytes)", flush=True)
    if options.validate_only:
        return
    require(os.environ.get("GH_REPO") == "Zhgma/TouchXbox", "Unexpected publishing repository")
    require(bool(os.environ.get("GH_TOKEN")), "Missing GitHub Actions token")
    files = [apk, manifest, ROOT / "TouchXbox-authorize.cmd"]
    existing = gh("release", "view", options.tag, "--json", "isDraft,isPrerelease", check=False)
    if existing.returncode == 0:
        release = json.loads(existing.stdout)
        if not release["isDraft"]:
            require(not release["isPrerelease"], "Existing version is a prerelease")
            verify_uploaded(options.tag, files)
            print("Release already published with matching files; left unchanged.")
            return
    else:
        gh("release", "create", options.tag, "--verify-tag", "--draft", "--title",
           f"TouchXbox {metadata['versionName']}", "--notes-file", str(ROOT / "release-notes.txt"))
    gh("release", "upload", options.tag, *(str(file) for file in files), "--clobber")
    verify_uploaded(options.tag, files)
    gh("release", "edit", options.tag, "--draft=false", "--latest")
    print(f"Published https://github.com/Zhgma/TouchXbox/releases/tag/{options.tag}")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        raise SystemExit(error.stderr.strip() or f"GitHub CLI exited with {error.returncode}")
    except (OSError, ValueError, KeyError, TypeError, zipfile.BadZipFile) as error:
        raise SystemExit(str(error))
