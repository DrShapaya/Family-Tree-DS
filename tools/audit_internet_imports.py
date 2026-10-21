"""Annotate downloaded originals without changing their bytes; compare independent INDI counts."""
import hashlib
import json
import pathlib
import re
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1] / "app/build/internet-import-review"
manifest = json.loads((ROOT / "manifest.json").read_text(encoding="utf-8-sig"))
for entry in manifest:
    path = ROOT / entry["id"]
    data = path.read_bytes()
    assert hashlib.sha256(data).hexdigest() == entry["sha"]
    entry["kind"] = "text"
    if zipfile.is_zipfile(path):
        entry["kind"] = "zip"
        with zipfile.ZipFile(path) as archive:
            ged = [n for n in archive.namelist() if n.lower().endswith((".ged", ".gedcom"))]
            entry["archiveEntries"] = len(archive.infolist())
            entry["gedcomEntries"] = ged
            data = archive.read(ged[0]) if len(ged) == 1 else b""
    if data.startswith((b"\xff\xfe", b"\xfe\xff")):
        text = data.decode("utf-16")
    elif data[:100].count(b"\x00") > 20:
        text = data.decode("utf-16-le" if data[1] == 0 else "utf-16-be")
    else:
        # Latin-1 is a lossless byte mapping for metadata, not an import decoder.
        text = data.decode("utf-8-sig", errors="replace")
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    header = text.split("\n0 @", 1)[0]
    source = re.search(r"(?m)^1[ \t]+SOUR[ \t]*(.*)", header)
    encoding = re.search(r"(?m)^1[ \t]+CHAR[ \t]+(.*)", header)
    version = re.search(r"(?m)^1[ \t]+GEDC[ \t]*\n2[ \t]+VERS[ \t]+(.*)", header)
    entry["sourceApplication"] = source.group(1).strip() if source else "unknown"
    entry["encoding"] = encoding.group(1).strip() if encoding else "not declared"
    entry["gedcomVersion"] = version.group(1).strip() if version else "not declared"
    entry["expectedPeople"] = len(re.findall(r"(?m)^0[ \t]+(?:@[^@]+@[ \t]+)?INDI[ \t]*$", text))
    entry["families"] = len(re.findall(r"(?m)^0[ \t]+(?:@[^@]+@[ \t]+)?FAM[ \t]*$", text))
    entry["nonemptyLines"] = sum(bool(line.strip()) for line in text.splitlines())
    entry["photoReferences"] = len(re.findall(r"(?m)^\d+\s+FILE\s+.*\.(jpg|jpeg|png|gif|bmp|webp)\s*$", text, re.I))
    entry["expectedDisposition"] = "open" if entry["expectedPeople"] else "no_people"
    if entry["kind"] == "zip" and not entry["gedcomEntries"]:
        entry["expectedDisposition"] = "foreign_archive"
    if len(data) > 25 * 1024 * 1024:
        entry["expectedDisposition"] = "text_limit"
    if entry["nonemptyLines"] > 500_000:
        entry["expectedDisposition"] = "line_limit"
    if len(data) > 25 * 1024 * 1024:
        entry["expectedDisposition"] = "text_limit"
    if entry["encoding"].upper() == "ANSEL" and any(b > 127 for b in data):
        entry["expectedDisposition"] = "unsupported_encoding"
    if entry["encoding"].upper() == "ASCII" and any(b > 127 for b in data):
        entry["expectedDisposition"] = "invalid_declared_encoding"
    print(entry["id"], entry["sourceApplication"], entry["encoding"], entry["expectedPeople"], entry["expectedDisposition"])
(ROOT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
