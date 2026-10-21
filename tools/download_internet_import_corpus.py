"""Fetch the pinned public corpus and verify every original with SHA-256.

Run from the project root: python tools/download_internet_import_corpus.py
The files are test data, never programs to execute. Cached verified files are reused.
"""
import concurrent.futures
import hashlib
import json
import pathlib
import shutil
import urllib.request

PROJECT = pathlib.Path(__file__).resolve().parents[1]
MANIFEST = PROJECT / "docs/diagnostics/internet-import-corpus-2026-10-10.json"
DESTINATION = PROJECT / "app/build/internet-import-review"


def download(entry):
    assert pathlib.Path(entry["id"]).name == entry["id"]
    destination = DESTINATION / entry["id"]
    if destination.is_file() and hashlib.sha256(destination.read_bytes()).hexdigest() == entry["sha"]:
        return entry["id"] + " verified (cached)"
    assert entry["url"].startswith("https://")
    request = urllib.request.Request(entry["url"], headers={"User-Agent": "AndroidFT-import-compatibility-audit"})
    with urllib.request.urlopen(request, timeout=45) as response:
        data = response.read()
    if hashlib.sha256(data).hexdigest() != entry["sha"]:
        raise ValueError("Public source changed: " + entry["id"])
    destination.write_bytes(data)
    return entry["id"] + " downloaded and verified"


if __name__ == "__main__":
    DESTINATION.mkdir(parents=True, exist_ok=True)
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for message in pool.map(download, manifest):
            print(message)
    shutil.copyfile(MANIFEST, DESTINATION / "manifest.json")
