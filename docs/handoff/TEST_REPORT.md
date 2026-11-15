# Device test report

Date: 2026-11-15. Build: debug APK from `9d9e358` (v1.0.1).

**Where it ran.** Emulator `bw36` (API 36, 1080x2400, gesture navigation, font scale 1.0), app data
cleared first, driven with `adb` input and `uiautomator` dumps. The real phone (S23) was connected
but locked during the main session. A short follow-up ran on it later (see "Phone follow-up"
at the end); other real-phone items are marked "phone: not run". All SMS in this report are fake.

Helper for repro: `adb root` then
`sqlite3 /data/data/io.github.swashbuckler104.budgetwidget/databases/budget.db`.
The Quick-Sort sheet for one transaction can be opened without the widget (root only):
`adb shell am start -n io.github.swashbuckler104.budgetwidget/.QuickSortActivity --el transactionId <id>`.

## Summary

| # | Item | Result |
|---|---|---|
| 1 | Outgoing amounts red | CONFIRMED, 6 sites listed |
| 2 | Quick-Sort sheet jitter | NOT REPRODUCED on emulator (9 gesture variants). Phone: not run. Ranked hypotheses below |
| 3 | Add Category sheet dismisses too easily | REPRODUCED, root cause found |
| 3b | Same easy-dismiss on the Quick-Sort sheet | NEW, reproduced |
| 4 | Smoke test | No crash, no ANR. Notes below |
| 5 | Parser with fake SMS | 9 mis-classifications, incl. loan credit as income and double counting |
| 6 | OTP and promo SMS from known banks are stored | NEW, privacy issue |

## 1. Amount colours

Wanted: outgoing = normal text colour, income = green, sign always shown.

Every place an amount is coloured by direction:

| Where | File | Now |
|---|---|---|
| Inbox cards (Transactions tab, "to sort") | `feature/transactions/.../list/TransactionItems.kt:241-246` (`emphasizeSpend = true`) | OUT **red**, IN green |
| Edit transaction sheet header | `feature/transactions/.../edit/TransactionSheets.kt:146` (`emphasizeSpend = true`) | OUT **red**, IN green |
| Shared component | `core/designsystem/.../component/CommonComponents.kt:52-57` (`MoneyText`) | red only when `emphasizeSpend` |
| Quick-Sort widget, focused amount | `widget/.../QuickSortWidgetContent.kt:212` | OUT **red** (`WidgetMoney.spend`), IN green |
| Insight widget, "x% more than last month" | `widget/.../InsightWidgetContent.kt:112` | more = **red**, less = green |
| Analysis: month-vs-last chip and Net | `feature/analysis/.../AnalysisScreen.kt:520, 545` | more / negative net = **red** |
| Notification accent | `notifications/.../TransactionNotificationFactory.kt:41, 122` | OUT accent **red** `#C62828`, IN green |
| Quick-Sort sheet header | `feature/transactions/.../quicksort/QuickSortSheet.kt:178` | OUT neutral (good), IN uses `primary` (teal), not the income green: inconsistent |
| Sorted list rows, day totals | `TransactionItems.kt` (no `emphasizeSpend`) | already neutral |

Also: the sign is an ASCII hyphen `-` everywhere (`CommonComponents.kt:49`, `QuickSortSheet.kt:176`,
`QuickSortWidgetContent.kt:209`, `TransactionNotificationFactory.kt:92`). A real minus `−` (U+2212)
is the same width as `+` in tabular figures and reads better.

Repro: fresh install, import SMS, open Transactions. Inbox cards show red `-₹1,200`; rows under a
date header show black `-₹350`. Screenshot checked.

## 2. Quick-Sort sheet jitter (widget -> More)

Composable: `QuickSortRoute` / `QuickSortContent` in
`feature/transactions/.../quicksort/QuickSortSheet.kt:59-158`, hosted by the translucent
`QuickSortActivity`.

**Not reproduced on the emulator.** Tried on the single-transaction sheet (the widget "More"
path) and the inbox sheet, with "All categories" collapsed and expanded: slow 120-150 px drags
both ways, 40-60 px hold-then-release drags with zero velocity, and short flicks. Frame counter
(`dumpsys gfxinfo`) stopped within one sample each time and the drag handle stayed at one position.

So the trigger is something the emulator did not have: the phone's real category list (taller
content), a real finger (the stretch overscroll only reacts to real touch streams), Samsung's
inset behaviour, or 120 Hz. **The implementer must reproduce on the phone first** (project rule),
with the steps the user gave: widget -> More -> scroll slightly down -> release.

What the code shows, most likely cause first:

1. **Two scroll owners for one gesture.** The sheet content is one
   `Column(Modifier.verticalScroll(...))` directly inside `ModalBottomSheet`
   (`QuickSortSheet.kt:111-119`). The sheet's nested-scroll connection takes every leftover pixel
   and all leftover fling velocity from that column. When "All categories" makes the content
   taller than the screen, a small drag is split between "scroll the column" and "move the sheet",
   and on release both the column's fling/overscroll and the sheet's settle animation run. Item 3b
   below proves the two are coupled on this sheet.
2. **Sheet height changes while it is open.** `showAll` (`QuickSortSheet.kt:106, 143`) and the
   "possible duplicate" line change the content height, so the sheet's anchors are recomputed. If
   that lands during a settle animation the sheet re-targets, which looks like bouncing.
3. **Insets applied twice.** The column adds `navigationBarsPadding()` (`QuickSortSheet.kt:117`)
   on top of the sheet's own `contentWindowInsets`, inside an edge-to-edge translucent window with
   `adjustResize`. On Samsung the bottom inset changes when the gesture hint hides, which again
   changes the content height.

The fix in PLAN.md (pinned header and footer, one scrolling body, sheet drag only from the handle)
removes all three, so it should hold whichever is the real trigger. Acceptance is the phone test.

## 3. Add Category sheet dismisses too easily

Composable: `CategoryEditorSheet`, `feature/categories/.../CategoryEditorSheet.kt:53-75`.

Repro (emulator, 3 of 3 with this gesture):
1. Categories tab -> New category.
2. Scroll the sheet down about 300 px (to the colour grid).
3. Swipe down at normal speed to get back to Save (`input swipe 540 1200 540 1700 250`).
4. The content reaches the top after 300 px, the remaining 200 px and the fling go to the sheet,
   and **the sheet closes**. Typed name, icon and colour are gone.

A slow swipe (same distance in 600 ms) does not close it.

**Root cause.** `ModalBottomSheet` with `skipPartiallyExpanded = true` has only two positions,
Expanded and Hidden. Its nested-scroll connection takes the unconsumed drag and fling of the inner
`verticalScroll` column, and any release past a small distance or velocity settles to the nearest
anchor, which is Hidden. So "scroll back to the top a bit too hard" equals "dismiss". Made worse
by: Save is inside the scrolled content (`CategoryEditorSheet.kt:85`), so the user must scroll up
to reach it, and there is no `confirmValueChange` or unsaved-changes guard.

Also seen: system Back closes the sheet and drops the input without asking.

### 3b. Same thing on the Quick-Sort sheet (new)

- Single-transaction sheet, content short: a 60 px flick in 60 ms closes it.
- "All categories" open and scrolled 140 px: `input swipe 540 1300 540 1500 150` closes it.

The other sheets with the same pattern (`verticalScroll` column directly in a
`ModalBottomSheet`): `TransactionSheets.kt:85, 302`, `FilterBar.kt:246, 311`,
`MerchantRulesSheet.kt:51`.

## 4. Smoke test

| Flow | Result |
|---|---|
| Onboarding, 7 steps | OK. SMS disclosure screen is shown **before** the system prompt (step 2), notifications step 3, battery step 4, history choice step 5, categories step 6, widget step 7 |
| SMS + notification permission prompts | OK |
| Backfill of existing inbox SMS | OK (30 imported) |
| Live SMS -> transaction -> notification | OK, 18 injected, 14 became transactions, 4 went to the unparsed list, all within about 2 s |
| Notification tap -> Quick-Sort sheet | OK |
| Notification action button ("Public transport") | OK, row became `CATEGORIZED`, notification removed |
| Sort from the sheet | OK |
| Analysis tab | OK, renders; see item 5 for what the totals include |
| Backup (JSON via file picker) | OK, 41 KB file in Downloads |
| Restore | OK, 42 transactions and both sorted rows came back |
| Settings -> Unparsed bank SMS | OK, list, copy and delete buttons present |
| Duplicate hint ("Looks like the same payment", Merge / Keep both) | OK, shown on the inbox card |
| Crashes (`logcat -b crash`), ANRs | none |
| Widget on home screen | not run (no widget placed on the emulator this session; last checked in M4/M7) |
| Close-the-day and 9 PM reminder | not run (time based) |
| Lock-screen redaction | code checked (`VISIBILITY_PRIVATE` + public version); not checked on a locked device |

Notes:
- Unsorted transactions are counted in Analysis "Spent" under "To sort". That is by design, but
  it means everything in item 5 inflates the totals until the user sorts it.
- Logcat noise only: `WindowOnBackDispatcher: sendCancelIfRunning` warnings from
  `androidx.navigationevent` on each back press. Harmless, library side.
- After a restore the unparsed list is rebuilt from the inbox scan (11 before, 6 after). Fine,
  just not obvious.

## 5. Parser with fake SMS

31 messages were run through the real parser and mapper (`PennyWiseSmsParser`, the same code the
app uses) in a throwaway unit test, and 18 of them were also injected on the emulator with
`adb emu sms send`. The messages are written by hand in the style of real ones; **SBI formats in
particular may differ from real SMS**, so rows marked (?) need a real sample before fixing.

### Loan repayment chain

| # | Message (short) | Sender | Got | Should be |
|---|---|---|---|---|
| L1 | "EMI of Rs.12,500 for your Loan A/c XX4567 is due on..." | AD-HDFCBK | unparsed | ignored (reminder), OK |
| L2 | "INR 12,500.00 debited from HDFC Bank XX8812... Info: ACH D- BAJAJ FINANCE LTD-..." | AD-HDFCBK | **INVESTMENT**, merchant "HDFC Bank XX8812" | loan repayment, merchant "BAJAJ FINANCE LTD" |
| L3 | "we have received your EMI payment of Rs. 12,500 towards your Loan A/c XX4567" | VM-BAJAJF | unknown sender, kept in unparsed list | link to L2, no second transaction. OK today, but it is noise in the unparsed list |
| L4 | "Rs.12,500.00 credited to your HDFC Bank Loan A/c XX4567 towards EMI repayment" | AD-HDFCBK | **INCOME +12,500**, account ··4567, merchant "EMI repayment. Outstanding principal Rs.2,35,000.00" | not income; second half of L2 |
| L5 | "INR 2,00,000.00 deposited in A/c XX8812 for LOAN DISBURSEMENT-PL4567" | AD-HDFCBK | **INCOME +2,00,000** | loan disbursal (transfer kind), not income |
| L6 | "Rs.12,500.00 will be debited from A/c XX8812 on 05-Nov-26 towards BAJAJ FINANCE" | AD-HDFCBK | unparsed | ignored (future debit), OK |
| L7 | "Your A/C XXXXX5503 has a debit by transfer of Rs 8,450.00" | ATMSBI | unparsed (?) | expense / repayment |
| L8 | "your SBI Loan A/c XXXXX9012 is credited with EMI Rs 8,450.00" | ATMSBI | **INCOME +8,450**, account ··9012 | not income |
| L9 | "Your EMI of INR 6,300.00 for Loan A/c ... has bounced" | AX-AXISBK-S | unparsed | ignored, OK |

Net effect of one EMI today: Spent +12,500 (as an "investment" until sorted) **and** Income
+12,500. The same money is counted on both sides.

### Other double counting and wrong types

| # | Message (short) | Got | Should be |
|---|---|---|---|
| C1 | HDFC "Rs.5000 debited ... to sbicard.2290@sbi" (credit card bill payment) | EXPENSE 5,000 | transfer: the card spends are already counted as CREDIT |
| C2 | SBI Card "received payment of Rs.5,000 ... credited to your SBI Credit Card ending 2290" | unparsed | link to C1 |
| S1 | HDFC "Rs.10000 debited ... to asha.self@kotak (UPI Ref No 630712345678)" | EXPENSE 10,000 | self transfer |
| S2 | Kotak "Received Rs.10000 ... UPI Ref 630712345678" | **INCOME +10,000**, same ref as S1 | self transfer, pair with S1 |
| R1 | Axis "INR 418.00 credited ... Info- REFUND/BLINKIT" | INCOME +418 | refund (reduce spending, not income) |
| R2 | Federal "Rs 320.00 reversed and credited ... failed UPI txn Ref No ..." | INCOME +320 | reversal of the earlier debit with the same ref |
| B7 | HDFC "ACH D- INDIAN CLEARING CORP-MF12345" (mutual fund SIP) | INVESTMENT, merchant "HDFC Bank XX8812" | type right, merchant wrong (same bug as L2) |
| B1 | Jupiter savings "Rs.275.00 debited from your Jupiter account" (JD-JUPITR-S) | unknown sender, unparsed list | expense (known gap, ADR-0028) |
| B4 | SBI "has a credit by NEFT of Rs 82,000.00 ... by ACME TECHNOLOGIES" | unparsed (?) | income |

Correct: OTP, declined card, collect request, pre-approved loan promo, balance alert, card bill
reminder (all unparsed, no transaction); one live message each for Kotak, Federal, Slice, SBI
Card, Jupiter Edge card and Axis card (right amount, type and merchant); a personal number
(ignored, not stored).

Pairs that can be linked with data already in the DB: S1+S2 (same UPI reference, same amount,
opposite direction, different accounts), R2 + its original debit (same reference), L2+L4 and C1+C2
(same amount, same day, opposite direction, "loan a/c" / "credit card" wording).

## 6. OTP and promo SMS from known bank senders are stored (new)

Repro: inject
`adb emu sms send AD-HDFCBK "OTP is 482913 for txn of INR 2,499.00 at AMAZON on HDFC Bank card ending 1234. Valid till 10:42. Do not share OTP with anyone"`.
Then `select sender, reason, body from unparsed_sms`: the full body, OTP included, is stored with
reason `NO_MATCH` and shown in Settings -> Unparsed bank SMS. The pre-approved-loan promo, the EMI
reminder and the mandate notice are stored the same way.

Cause: the OTP / "looks like a transaction" filter (`FinancialSmsHeuristic`) is only applied to
**unknown** senders (`core/data/.../ingest/IngestPipeline.kt:99`). For a known bank sender every
`NO_MATCH` body is saved. This breaks hard constraint 4 ("persist SMS bodies only for financial
messages") and matters more once unparsed messages can be shared (PLAN.md section C). Logged as
F9 in SECURITY_AUDIT.md.

## Phone follow-up (2026-11-15, S23, Android 17, 3-button navigation)

Nothing was installed and no data was changed; only sheet position and a frame counter were read.

- The phone runs the **release-signed** v1.0.1 from GitHub (not a debug build, not debuggable).
- Item 3 reproduced on the phone: New category, scroll 300 px, `input swipe 540 1200 540 1700 250`
  closes the sheet. Slow 60-80 px drags did not move it and showed no jitter.
- Item 2 (Quick-Sort jitter) could not be run: the inbox was empty, so there was no transaction to
  open the sheet with, and SMS cannot be injected on a real phone. It needs the user's finger on
  the next real transaction (PLAN.md A2 step 0).
- Difference from the emulator worth knowing for A2: the phone uses 3-button navigation (taller
  bottom inset), the emulator used gesture navigation.
