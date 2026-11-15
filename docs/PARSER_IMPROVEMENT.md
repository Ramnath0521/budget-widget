# Improving the parser

How bank SMS the app can't read become parser fixes. There is no model in the app: bank SMS are
template-like, so reviewed rules plus test cases work better and keep the app small, offline and
auditable (docs/handoff/PLAN.md, section B). The "model" is this loop, run by a developer with a
coding agent.

```
phone: Settings > Help improve the parser  ->  email / file (masked)
dev:   scripts/feedback-to-golden.py       ->  golden stubs (type: TODO)
agent: fill expected values, write rules   ->  tests green
dev:   review, commit, release
```

## 1. Collect

On the phone: *Settings → Help improve the parser → Scan last 90 days*. The scan runs in memory and
changes no budget data (ADR-0043). It keeps only messages that read like bank transactions but
were not parsed, masks account, card, UPI, reference and phone numbers, and shows exactly what
will be sent. Remove anything you don't want to share, and check for names: they are not masked.
Then *Email* (your own mail app sends it) or *Save file*.

Single messages can also be copied from *Settings → Unparsed bank SMS*. Paste them into a file in
the same format (see `ParserFeedbackReport` in `core/data/.../feedback/ParserFeedback.kt`).

Never commit an unmasked message. Never paste real SMS into a public issue.

## 2. Turn the report into test stubs

```sh
python3 -I scripts/feedback-to-golden.py report.txt --dry-run   # see where each message goes
python3 -I scripts/feedback-to-golden.py report.txt             # append stubs
```

Each message becomes a case with `type: TODO` in `ingest/src/test/resources/golden/<bank>.txt`
(banks we don't ship go to `golden/unsupported.txt`, which the tests don't load). A message that
still has a run of 6+ digits or an `@` is refused: mask it by hand first. `GoldenSmsTest` fails
until every TODO is filled in, which is the point.

## 3. Let an agent write the rules

Start a Claude Code session in the repo and paste:

```
Read CLAUDE.md, docs/PARSER_IMPROVEMENT.md, ingest/src/test/resources/golden/README.md and
docs/DECISIONS.md ADR-0038, ADR-0042. In ingest/src/test/resources/golden/*.txt, fill in every case
with `type: TODO`: write a short name, then the expected type, amount, direction, merchant, last4
and flow by READING the SMS text (never by running the parser). Run
`scripts/capped.sh ./gradlew :ingest:testDebugUnitTest --tests '*Golden*'`. For each failing case,
fix it in our own layer, never in third_party/: FlowRules.kt for meaning (transfer, loan, card bill),
FinancialSmsHeuristic.kt for what is kept as unparsed, ParsedTransactionMapper.kt for field
clean-up. If the bank's format is simply unsupported upstream, draft docs/upstream/000N-*.md
instead and mark the case UNPARSED with a comment linking the draft. Then run
`scripts/capped.sh ./gradlew :ingest:testDebugUnitTest :core:data:testDebugUnitTest :parser-core:test`.
One commit per bank, no unmasked data, no em dashes.
```

## 4. Review before committing

- Every expected value matches what the SMS says.
- No fixture holds a real name, account, card, UPI ID, phone or reference number.
- New rules are narrow (sender and wording), each with a golden case that would fail without it.
- `docs/upstream/` drafts exist for anything that belongs in PennyWise.

Upstream bumps follow [PARSER_UPGRADE.md](PARSER_UPGRADE.md); the golden corpus is the gate.
