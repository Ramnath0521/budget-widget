package io.github.swashbuckler104.budgetwidget.ingest.parser

import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms

/**
 * Recognises a bank transaction alert from a sender no parser knows (docs/DECISIONS.md ADR-0028),
 * so it can be kept in the unparsed list instead of vanishing: the owner's Jupiter account alerts
 * never showed up, and nothing told them why. Deliberately strict, because these bodies are
 * stored: a registered business header (never a phone number or a promotional `-P` header), an
 * amount in rupees, a money-movement verb and an account word, and no one-time password.
 */
internal object FinancialSmsHeuristic {
    fun looksLikeTransaction(sms: RawSms): Boolean {
        if (!BUSINESS_HEADER.matches(sms.sender) || PROMOTIONAL.matches(sms.sender)) return false
        return looksLikeTransactionBody(sms.body)
    }

    /**
     * The body alone reads like a completed money movement: a rupee amount, a movement word and an
     * account word, and no secret, reminder, offer or failure wording. Used for known bank senders
     * too, so OTPs and promotions they send never reach the unparsed list.
     */
    fun looksLikeTransactionBody(body: String): Boolean = AMOUNT.containsMatchIn(body) &&
        MOVEMENT.containsMatchIn(body) &&
        ACCOUNT.containsMatchIn(body) &&
        !SECRET.containsMatchIn(body) &&
        !NOT_A_TRANSACTION.containsMatchIn(body) &&
        // The lender's or card's side of a payment the bank debit already shows (ADR-0042).
        !FlowRules.isPaymentConfirmation(body)

    /** TRAI headers: `XX-NAME`, optionally with an `-S`/`-T`/`-G`/`-P` suffix; or a bare 6-letter name. */
    private val BUSINESS_HEADER = Regex("""^([A-Z]{2}-[A-Z0-9]{3,9}(-[A-Z])?|[A-Z]{6})$""", RegexOption.IGNORE_CASE)
    private val PROMOTIONAL = Regex("""^[A-Z]{2}-[A-Z0-9]+-P$""", RegexOption.IGNORE_CASE)

    /** "Rs", "INR" or "₹" as a word of its own ("hours 5" is not an amount) before digits. */
    private val AMOUNT =
        Regex("""(?:(?<![a-z])(?:rs\.?|inr)|₹)\s*\d[\d,]*(?:\.\d{1,2})?""", RegexOption.IGNORE_CASE)
    private val MOVEMENT = Regex(
        """\b(credited|debited|spent|received|withdrawn|deposited|paid|sent|transferred)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val ACCOUNT =
        Regex("""\b(a/c|acct|account|card|upi|vpa|wallet|imps|neft|rtgs)\b""", RegexOption.IGNORE_CASE)

    /**
     * Reminders, future debits, bounced or declined payments, collect requests and offers. "Failed"
     * is left out on purpose: a reversal reads "credited ... for failed UPI txn".
     */
    private val NOT_A_TRANSACTION = Regex(
        """\b(is due|due on|due date|amt due|amount due|will be (debited|deducted|charged|auto-?debited)|""" +
            """bounced|declined|has requested|requested rs|collect request|pre-?approved|apply now)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** One-time passwords (also glued to digits: "OTP482913"), PINs and card codes. */
    private val SECRET = Regex(
        """\b(otp|one[ -]time password|verification code|passcode|cvv|m?pin)\b|\botp\d""",
        RegexOption.IGNORE_CASE,
    )
}
