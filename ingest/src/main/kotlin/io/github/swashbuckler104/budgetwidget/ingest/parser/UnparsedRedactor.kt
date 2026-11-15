package io.github.swashbuckler104.budgetwidget.ingest.parser

/**
 * Masks what identifies the user in a bank SMS before it is kept in the unparsed list: links,
 * UPI IDs and email addresses, account and card numbers (last 2 digits kept), and long digit runs
 * such as reference and phone numbers. Rupee amounts, dates, times and wording stay, so the message
 * can still be used to fix the parser. Names are not detected; the screen asks the user to remove
 * them before sharing. Idempotent: redacting a redacted body changes nothing.
 */
internal object UnparsedRedactor {
    fun redact(body: String): String {
        val amounts = mutableListOf<String>()
        var text = LINK.replace(body, "<link>")
        text = HANDLE.replace(text, "<upi>")
        // Amounts are set aside first so "Rs.1,24,500.00" is never mistaken for a reference number.
        text = AMOUNT.replace(text) { match ->
            amounts += match.value
            "$PLACEHOLDER${amounts.size - 1}$PLACEHOLDER"
        }
        text = MASKED_NUMBER.replace(text) { m -> m.groupValues[1] + keepLastTwo(m.groupValues[2]) }
        text = LONG_NUMBER.replace(text) { m -> if (YEAR.matches(m.value)) m.value else keepLastTwo(m.value) }
        return PLACEHOLDER_REF.replace(text) { m -> amounts[m.groupValues[1].toInt()] }
    }

    private fun keepLastTwo(digits: String): String = "X".repeat(digits.length - 2) + digits.takeLast(2)

    private const val PLACEHOLDER = '\u0000'
    private val PLACEHOLDER_REF = Regex("""\u0000(\d+)\u0000""")
    private val LINK = Regex("""(?i)\b(?:https?://|www\.)\S+""")

    /** UPI IDs (name@bank) and email addresses. */
    private val HANDLE = Regex("""[\w.+-]+@[\w.-]+""")
    private val AMOUNT = Regex("""(?i)(?:(?<![a-z])(?:rs\.?|inr)|₹)\s*\d[\d,]*(?:\.\d{1,2})?""")

    /** "XX8812", "****8812", "X4821": the bank already hid part of it; keep only the last 2 digits. */
    private val MASKED_NUMBER = Regex("""(?<![A-Za-z])([Xx*]+)(\d{3,})(?!\d)""")

    /** References, phone numbers, card and account numbers written in full. */
    private val LONG_NUMBER = Regex("""(?<![\d\u0000])\d{4,}(?![\d\u0000])""")

    /** A year inside a date ("06-11-2026", "04NOV2026") is not identifying. */
    private val YEAR = Regex("""20\d\d""")
}
