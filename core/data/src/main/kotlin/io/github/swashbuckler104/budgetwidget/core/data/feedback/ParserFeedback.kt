package io.github.swashbuckler104.budgetwidget.core.data.feedback

import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

/**
 * The report (ADR-0047): JSON Lines, one self-contained object per message, so reports from several
 * phones can simply be concatenated. The saved file is exactly [jsonLines]; the email body puts
 * [EMAIL_INTRO] above it. docs/PARSER_IMPROVEMENT.md and scripts/feedback-to-golden.py read it.
 */
object ParserFeedbackReport {
    const val VERSION = 2

    /** One line for people reading the email; the script skips lines that aren't JSON objects. */
    const val EMAIL_INTRO = "Budget Widget parser report: bank SMS the app couldn't read, masked on the phone " +
        "(digits are 9, unknown words are x). One JSON object per line."

    @Serializable
    private data class Line(
        val v: Int,
        val app: String,
        val parser: String,
        val sender: String,
        val reason: String,
        val count: Int,
        val month: String,
        val body: String,
    )

    /** Compact JSON escapes newlines in a body, so each message is exactly one line. */
    fun jsonLines(messages: List<FeedbackMessage>, appVersion: String, parserCommit: String = PARSER_COMMIT): String =
        messages.joinToString("") { m ->
            Json.encodeToString(
                Line(VERSION, appVersion, parserCommit, m.sender, m.reason.name, m.count, m.month, m.body),
            ) + "\n"
        }

    fun emailBody(messages: List<FeedbackMessage>, appVersion: String): String =
        EMAIL_INTRO + "\n\n" + jsonLines(messages, appVersion)

    /** The pinned upstream commit (third_party/pennywise); bump with the submodule. */
    const val PARSER_COMMIT = "0623c2b"
}
