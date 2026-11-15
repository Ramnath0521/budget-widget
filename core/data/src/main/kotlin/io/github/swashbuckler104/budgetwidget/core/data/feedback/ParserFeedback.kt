package io.github.swashbuckler104.budgetwidget.core.data.feedback

import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason

/**
 * One bank message the parser could not read, as it will be shared: already masked, with how often
 * the same masked text appeared. [month] is `yyyy-MM`, never the exact time.
 */
data class FeedbackMessage(
    val sender: String,
    val body: String,
    val reason: UnparsedReason,
    val month: String,
    val count: Int = 1,
)

/**
 * "Help improve the parser" (docs/DECISIONS.md ADR-0043): reads the SMS inbox for messages that
 * look like bank transactions but did not parse. Read-only by construction: implementations get
 * the inbox and the parser, never the database, notifications or work scheduling, so a scan cannot
 * change budget data.
 */
interface ParserFeedbackScanner {
    /**
     * Null when SMS can't be read (permission missing). At most [MAX_MESSAGES] distinct messages,
     * masked for sharing (ADR-0046). [keepAmounts]: transaction amounts stay real; balances,
     * limits and every other number are still masked.
     */
    suspend fun scan(days: Int, nowMillis: Long, keepAmounts: Boolean = false): List<FeedbackMessage>?

    companion object {
        const val DAYS = 90
        const val MAX_MESSAGES = 300
    }
}

/** The plain-text report: the email body and the saved file. docs/PARSER_IMPROVEMENT.md reads it. */
object ParserFeedbackReport {
    const val FORMAT = "budget-widget-parser-feedback v1"

    fun text(messages: List<FeedbackMessage>, appVersion: String, parserCommit: String = PARSER_COMMIT): String =
        buildString {
            appendLine(FORMAT)
            appendLine("app: $appVersion, parser-core: $parserCommit, messages: ${messages.size}")
            appendLine(
                "Masked on the phone: digits are 9 (account and card numbers keep their last 2), " +
                    "words the app doesn't know are x, links and UPI IDs are placeholders.",
            )
            messages.forEachIndexed { i, m ->
                appendLine()
                appendLine("--- ${i + 1} | ${m.sender} | ${m.reason} | ${m.month} | x${m.count}")
                appendLine(m.body)
            }
        }

    /** The pinned upstream commit (third_party/pennywise); bump with the submodule. */
    const val PARSER_COMMIT = "0623c2b"
}
