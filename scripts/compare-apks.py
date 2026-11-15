#!/usr/bin/env python3
"""Compare two APKs entry by entry, ignoring signatures (ADR-0050, docs/REPRODUCIBLE_BUILD.md).

Usage: scripts/compare-apks.py <a.apk> <b.apk> [--ignore NAME ...]

A reproducible build gives the same zip entries with the same bytes; only the signature differs.
The v2/v3 signature lives in the APK signing block, outside the zip entries, so it is ignored by
construction. v1 (JAR) signature files under META-INF/ (*.SF, *.RSA, *.EC, *.DSA, MANIFEST.MF) are
ignored too. Exit 0 when everything else is identical, 1 otherwise. Standard library only.
"""
import hashlib
import re
import sys
import zipfile

V1_SIGNATURE = re.compile(r"^META-INF/([^/]+\.(SF|RSA|EC|DSA)|MANIFEST\.MF)$", re.IGNORECASE)


def entries(path, ignored):
    with zipfile.ZipFile(path) as apk:
        return {
            info.filename: hashlib.sha256(apk.read(info)).hexdigest()
            for info in apk.infolist()
            if not V1_SIGNATURE.match(info.filename) and info.filename not in ignored
        }


def main():
    args = sys.argv[1:]
    ignored = set()
    while "--ignore" in args:
        i = args.index("--ignore")
        ignored.add(args[i + 1])
        del args[i:i + 2]
    if len(args) != 2:
        sys.exit(__doc__)
    a, b = (entries(p, ignored) for p in args)
    only_a = sorted(a.keys() - b.keys())
    only_b = sorted(b.keys() - a.keys())
    changed = sorted(n for n in a.keys() & b.keys() if a[n] != b[n])
    for name in only_a:
        print(f"only in {args[0]}: {name}")
    for name in only_b:
        print(f"only in {args[1]}: {name}")
    for name in changed:
        print(f"differs: {name}")
    same = not (only_a or only_b or changed)
    print(f"{len(a.keys() & b.keys()) - len(changed)} entries identical" + ("" if same else ", NOT reproducible"))
    return 0 if same else 1


if __name__ == "__main__":
    sys.exit(main())
