#!/usr/bin/env python3
"""Turn a "Help improve the parser" report into golden SMS test stubs.

Usage: scripts/feedback-to-golden.py <report.txt> [--dry-run]

Reads the report the app emails or saves (see core/data/.../feedback/ParserFeedback.kt): JSON
Lines from 1.0.3 (ADR-0047; a pasted email body works too, even if the mail app wrapped long
lines, and several reports can be concatenated), or the 1.0.2 text format
("budget-widget-parser-feedback v1"). It appends one stub per message to the matching
ingest/src/test/resources/golden/<bank>.txt, with `type: TODO`. Senders of banks we don't ship
go to golden/unsupported.txt, which the tests do not load.

A stub makes GoldenSmsTest fail until someone fills in the expected values by reading the SMS
(docs/PARSER_IMPROVEMENT.md). Messages that still look unredacted (an @, or a run of 6+ digits
that isn't the app's mask: all 9s except possibly the last 2) are refused, never written.
Standard library only.
"""
import json
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
LONG_RUN = re.compile(r"\d{6,}")


def looks_unredacted(body):
    """1.0.3+ reports turn digits into 9s (ADR-0046); account and card numbers keep their last 2."""
    return "@" in body or any(set(m[0][:-2]) != {"9"} for m in LONG_RUN.finditer(body))


def parse(text):
    if text.lstrip().startswith(HEADER):
        return parse_v1(text)
    return parse_json_lines(text)


def parse_json_lines(text):
    """One object per line. Lines that aren't JSON (the email's intro) are skipped. A line a mail
    app wrapped is joined back: text is gathered until it parses as a whole object."""
    entries, buffer = [], ""
    for line in text.splitlines():
        if not buffer and not line.lstrip().startswith("{"):
            continue
        buffer += line
        try:
            obj = json.loads(buffer)
        except json.JSONDecodeError:
            continue
        buffer = ""
        if not isinstance(obj, dict) or obj.get("v") != 2:
            sys.exit(f"unsupported report line (expected v 2): {str(obj)[:60]}")
        entries.append({
            "n": str(len(entries) + 1), "sender": obj["sender"], "reason": obj["reason"],
            "month": obj["month"], "count": str(obj["count"]), "body": obj["body"].replace("\n", "\\n"),
        })
    if buffer:
        sys.exit("report ends in the middle of a message (cut short?)")
    if not entries:
        sys.exit("not a parser feedback report (no messages found)")
    return entries


def parse_v1(text):
    lines = text.lstrip().splitlines()
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
        if looks_unredacted(e["body"]):
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
