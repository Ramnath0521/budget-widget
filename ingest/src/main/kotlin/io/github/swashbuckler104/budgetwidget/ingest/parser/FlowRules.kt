package io.github.swashbuckler104.budgetwidget.ingest.parser

import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.ParserType
import io.github.swashbuckler104.budgetwidget.core.model.sms.FlowHint
import io.github.swashbuckler104.budgetwidget.core.model.sms.ParsedSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParseResult

/**
 * Our corrections on top of parser-core, which stays unmodified (hard constraint 2, ADR-0042).
 * They run on every parsed transaction and decide what money movement really is:
 * - a loan account or credit card being credited is the other side of a payment from a bank
 *   account: a transfer, never income (the pipeline links it with the bank debit by amount);
 * - a loan EMI, a credit card bill or a loan disbursal is a transfer, not spending or income;
 * - HDFC "ACH D- <NAME>-<ref>" debits name the payee, which upstream misses (it reports our own
 *   account as the merchant).
 * Every rule has golden cases (`ingest/src/test/resources/golden/`).
 */
internal object FlowRules {
    fun apply(parsed: ParsedSms): SmsParseResult {
        val body = parsed.body
        val merchant = achPayee(body) ?: parsed.merchant
        val flow = when {
            parsed.direction == Direction.IN && LOAN_DISBURSAL.containsMatchIn(body) -> FlowHint.LOAN_DISBURSAL

            parsed.direction == Direction.IN && isLoanPaymentReceived(body) -> FlowHint.LOAN_REPAYMENT

            parsed.direction == Direction.IN && CARD_PAYMENT_RECEIVED.containsMatchIn(
                body,
            ) -> FlowHint.CARD_BILL_PAYMENT

            parsed.direction == Direction.OUT && !parsed.isCard && isLoanRepayment(body, merchant) ->
                FlowHint.LOAN_REPAYMENT

            parsed.direction == Direction.OUT && !parsed.isCard && isCardBillPayment(body, merchant) ->
                FlowHint.CARD_BILL_PAYMENT

            else -> null
        }
        return SmsParseResult.Transaction(
            parsed.copy(
                merchant = merchant,
                parserType = if (flow != null) ParserType.TRANSFER else parsed.parserType,
                flow = flow,
            ),
        )
    }

    /**
     * A loan account or credit card was credited: the lender's or card's side of a payment from a
     * bank account. An unparsed or unknown-sender message like this is not kept: the bank debit
     * holds the money, and there is nothing to fix in the parser.
     */
    fun isPaymentConfirmation(body: String): Boolean =
        isLoanPaymentReceived(body) || CARD_PAYMENT_RECEIVED.containsMatchIn(body)

    private fun isLoanPaymentReceived(body: String): Boolean =
        (LOAN_ACCOUNT.containsMatchIn(body) && !LOAN_DISBURSAL.containsMatchIn(body)) ||
            LENDER_RECEIVED.containsMatchIn(body)

    private fun isLoanRepayment(body: String, merchant: String?): Boolean =
        LOAN_WORDS.containsMatchIn(body) || merchant?.uppercase()?.let { m -> LENDERS.any { it in m } } == true

    private fun isCardBillPayment(body: String, merchant: String?): Boolean =
        CARD_VPA.containsMatchIn(body) || CARD_BILL_WORDS.containsMatchIn(body) ||
            merchant?.let { CARD_VPA.containsMatchIn(it) } == true

    /** "Info: ACH D- BAJAJ FINANCE LTD-P400PHL1234567" -> "BAJAJ FINANCE LTD". */
    private fun achPayee(body: String): String? =
        ACH.find(body)?.groupValues?.get(1)?.trim()?.replace(SPACES, " ")?.takeIf { it.length >= 3 }

    private val SPACES = Regex("""\s+""")
    private val ACH = Regex("""\b(?:N?ACH|ECS)\s*D-\s*([A-Za-z][A-Za-z0-9 .&]*?)\s*-\s*\w""", RegexOption.IGNORE_CASE)

    /** "credited to your HDFC Bank Loan A/c", "your SBI Loan A/c XX9012 is credited". */
    private val LOAN_ACCOUNT = Regex("""\bloan\s*(?:a/?c|acct|account)\b""", RegexOption.IGNORE_CASE)
    private val LOAN_DISBURSAL = Regex("""\bloan\s*disburs""", RegexOption.IGNORE_CASE)
    private val CARD_PAYMENT_RECEIVED = Regex(
        // Not [^.]: amounts like "Rs.5,000.00" contain dots.
        """\b(?:received|credited)\b.{0,80}\bpayment\b.{0,80}\bcredit\s*card\b|""" +
            """\bpayment\b.{0,60}\b(?:received|credited)\b.{0,60}\bcredit\s*card\b""",
        RegexOption.IGNORE_CASE,
    )
    private val LENDER_RECEIVED = Regex(
        """\breceived\s+(?:your\s+)?(?:emi|loan|instal+ment)\s+(?:payment|repayment|amount)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val LOAN_WORDS = Regex("""\b(?:emi|loan|instal+ment)\b""", RegexOption.IGNORE_CASE)

    /** Card-bill UPI handles: sbicard.2290@sbi, cred.club@axisb, hdfccardbill@hdfcbank. */
    private val CARD_VPA = Regex("""\b[\w.-]*(?:card|cred)[\w.-]*@[\w.-]+""", RegexOption.IGNORE_CASE)
    private val CARD_BILL_WORDS = Regex("""\bcredit\s*card\s*(?:bill|payment|dues)\b""", RegexOption.IGNORE_CASE)

    /** Lenders that collect EMIs by mandate (ACH/NACH); matched inside the payee name. */
    private val LENDERS = listOf(
        "BAJAJ FIN", "BAJAJ HOUSING", "TATA CAPITAL", "HDB FIN", "HOME CREDIT", "MUTHOOT", "LIC HOUSING",
        "L&T FIN", "L AND T FIN", "ADITYA BIRLA FIN", "PIRAMAL", "CHOLAMANDALAM", "MAHINDRA FIN", "SHRIRAM",
        "FULLERTON", "KREDITBEE", "NAVI FIN", "POONAWALLA", "IIFL", "INDIABULLS", "CAPRI GLOBAL", "AAVAS",
    )
}
