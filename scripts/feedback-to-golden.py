#!/usr/bin/env python3
"""Turn a "Help improve the parser" report into golden SMS test stubs.

Usage: scripts/feedback-to-golden.py <report.txt> [--dry-run]

Reads the report the app emails or saves (format "budget-widget-parser-feedback v1", see
core/data/.../feedback/ParserFeedback.kt), and appends one stub per message to the matching
ingest/src/test/resources/golden/<bank>.txt, with `type: TODO`. Senders of banks we don't ship
go to golden/unsupported.txt, which the tests do not load.

A stub makes GoldenSmsTest fail until someone fills in the expected values by reading the SMS
(docs/PARSER_IMPROVEMENT.md). Messages that still look unredacted (a run of 6+ digits other than
the masked X's, or an @) are refused, never written. Standard library only.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
GOLDEN = ROOT / "ingest/src/test/resources/golden"
HEADER = "budget-widget-parser-feedback v1"

# TRAI header core (the part after "XX-") -> golden file. Same banks as SCOPE.md section 2.
BANKS = [
    (r"HDFC", "hdfc"), (r"SBI|ATMSBI|SBICRD|SBIINB", "sbi"), (r"AXIS", "axis"), (r"KOTAK", "kotak"),
    (r"FEDBNK|FEDERAL", "federal"), (r"JTEDGE|JUPITR|JUPITER", "jupiter"), (r"SLICE|SLCEIT|SLCBNK", "slice"),
]
ENTRY = re.compile(r"^--- (\d+) \| (.+?) \| ([A-Z_]+) \| (\d{4}-\d{2}) \| x(\d+)$")
UNREDACTED = re.compile(r"(?<![X\d])\d{6,}|@")


def parse(text):
    lines = text.splitlines()
    if not lines or lines[0].strip() != HEADER:
        sys.exit(f"not a parser feedback report (first line must be '{HEADER}')")
    entries, current = [], None
    for line in lines[1:]:
        m = ENTRY.match(line)
        if m:
            current = {"n": m[1], "sender": m[2], "reason": m[3], "month": m[4], "count": m[5], "body": []}
            entries.append(current)
        elif current is not None:
            current["body"].append(line)
    for e in entries:
        e["body"] = "\\n".join(e["body"]).strip()
    return entries


def bank_file(sender):
    core = sender.split("-")[1] if sender.count("-") >= 1 else sender
    for pattern, name in BANKS:
        if re.search(pattern, core, re.IGNORECASE):
            return name
    return "unsupported"


def stub(e):
    return (
        f"# From parser feedback ({e['month']}, seen {e['count']}x, reason {e['reason']}). "
        "Fill in from the SMS text, not from the parser.\n"
        f"name: TODO describe ({e['sender']} #{e['n']})\n"
        f"sender: {e['sender']}\n"
        f"body: {e['body']}\n"
        "type: TODO\n"
    )


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry = "--dry-run" in sys.argv
    if len(args) != 1:
        sys.exit(__doc__)
    entries = parse(pathlib.Path(args[0]).read_text(encoding="utf-8"))
    refused, written = [], {}
    for e in entries:
        if UNREDACTED.search(e["body"]):
            refused.append(e)
            continue
        written.setdefault(bank_file(e["sender"]), []).append(stub(e))
    for name, stubs in written.items():
        path = GOLDEN / f"{name}.txt"
        print(f"{path.relative_to(ROOT)}: +{len(stubs)}")
        if not dry:
            existing = path.read_text(encoding="utf-8").rstrip("\n") if path.exists() else ""
            sep = "\n---\n" if existing else ""
            path.write_text(existing + sep + "\n---\n".join(stubs), encoding="utf-8")
    for e in refused:
        print(f"REFUSED #{e['n']} from {e['sender']}: looks unredacted (6+ digits or @). Mask it by hand.",
              file=sys.stderr)
    return 1 if refused else 0


if __name__ == "__main__":
    sys.exit(main())
