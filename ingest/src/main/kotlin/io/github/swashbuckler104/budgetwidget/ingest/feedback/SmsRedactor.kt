package io.github.swashbuckler104.budgetwidget.ingest.feedback

import io.github.swashbuckler104.budgetwidget.ingest.parser.FinancialSmsHeuristic
import java.text.Normalizer

/**
 * Masks a bank SMS for sharing in a parser report (ADR-0046, plan C2). An allowlist, not a
 * blocklist, because a blocklist can't know every name:
 *
 * 1. A message holding a secret (OTP, CVV, PIN, password) is dropped, not masked.
 * 2. Links become `<URL>`; anything shaped `x@y` (UPI IDs, email addresses) becomes `<UPI>`.
 * 3. Every digit becomes `9`, except the last 2 digits of an account or card number and, with
 *    [keepAmounts], rupee amounts that aren't a balance or limit.
 * 4. A word is kept only if it is in [RedactorVocabulary] or is a single letter; any other word,
 *    so every name and merchant, becomes `x`/`X` of the same length and case. Styled Unicode
 *    letters are compared in their plain (NFKC) form.
 *
 * Apart from rule 2, the shape survives (lengths, separators, case, positions), so the parser can
 * still be fixed from the result: `Rs.1,24,500.00` becomes `Rs.9,99,999.99`. Idempotent.
 */
internal object SmsRedactor {
    /** Null when the message holds a secret and must not be shared at all. */
    fun redact(body: String, keepAmounts: Boolean = false): String? {
        if (FinancialSmsHeuristic.hasSecret(body)) return null
        val text = HANDLE.replace(LINK.replace(body, "<URL>"), "<UPI>")
        val keep = BooleanArray(text.length)
        ACCOUNT_NUMBER.findAll(text).forEach { m ->
            val digits = m.groups[DIGITS]!!.range
            for (i in maxOf(digits.first, digits.last - 1)..digits.last) keep[i] = true
        }
        if (keepAmounts) {
            AMOUNT.findAll(text).forEach { m ->
                val before = text.substring(maxOf(0, m.range.first - BALANCE_LOOKBACK), m.range.first)
                if (!BALANCE.containsMatchIn(before)) m.range.forEach { keep[it] = true }
            }
        }
        // By code point: styled digits ("𝟣𝟤") are two UTF-16 units each and must not slip through.
        val digitsMasked = buildString(text.length) {
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                if (Character.isDigit(cp) && !keep[i]) append('9') else appendCodePoint(cp)
                i += Character.charCount(cp)
            }
        }
        return WORD.replace(digitsMasked) { m ->
            val word = m.value
            if (word.codePointCount(0, word.length) == 1 || isKnown(word)) word else mask(word)
        }
    }

    /** Styled letters ("𝗌𝗉𝖾𝗇𝗍", fullwidth) are compared as their plain form, and kept as sent. */
    private fun isKnown(word: String): Boolean =
        Normalizer.normalize(word, Normalizer.Form.NFKC).lowercase() in RedactorVocabulary

    /** One `x` or `X` per character as the reader sees it (code point), not per UTF-16 unit. */
    private fun mask(word: String): String = buildString(word.length) {
        word.codePoints().forEach { append(if (Character.isUpperCase(it)) 'X' else 'x') }
    }

    private const val DIGITS = "digits"

    /** How far before an amount to look for "balance" or "limit". */
    private const val BALANCE_LOOKBACK = 24

    private val LINK = Regex("""(?i)\b(?:https?://|www\.)\S+""")

    /** The domain part never ends in a dot, so "to asha@okaxis." keeps its full stop. */
    private val HANDLE = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)*""")

    /**
     * Digits after an account or card word ("A/c no. 1234", "Card ending 2290") or after the
     * bank's own mask ("XX8812", "****8812"): their last 2 digits stay, as banks print them.
     */
    private val ACCOUNT_NUMBER = Regex(
        """(?i)(?:\b(?:a/c|acct|ac|account|card|ending)\b[\s.:#-]*(?:no\.?|number)?[\s.:#-]*""" +
            """[Xx*]*|(?<![A-Za-z])[Xx*]+)(?<$DIGITS>\d{2,})(?!\d)""",
    )
    private val AMOUNT = Regex("""(?i)(?:(?<![a-z])(?:rs\.?|inr)|₹)\s*\d[\d,]*(?:\.\d{1,2})?""")
    private val BALANCE = Regex("""(?i)\b(?:bal|balance|avl|avbl|aval|available|limit|lmt|outstanding)\b""")

    /** Letters with their combining marks, so Devanagari words are masked whole. */
    private val WORD = Regex("""[\p{L}\p{M}]+""")
}
