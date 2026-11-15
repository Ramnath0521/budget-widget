package io.github.swashbuckler104.budgetwidget.ingest.feedback

import io.github.swashbuckler104.budgetwidget.core.data.feedback.FeedbackMessage
import io.github.swashbuckler104.budgetwidget.core.data.feedback.ParserFeedbackScanner
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParseResult
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParser
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import io.github.swashbuckler104.budgetwidget.ingest.work.SmsInboxSource
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans the inbox in memory (ADR-0043). Keeps only messages the app itself would put in the
 * unparsed list (a bank's unmatched transaction alert, or a transaction alert from an unknown
 * business sender: OTPs, offers, reminders and personal SMS never qualify), masked with the same
 * redactor. Every other message is dropped as soon as it is read. Nothing is written anywhere.
 */
internal class InboxParserFeedbackScanner @Inject constructor(
    private val inbox: SmsInboxSource,
    private val parser: SmsParser,
) : ParserFeedbackScanner {
    override suspend fun scan(days: Int, nowMillis: Long): List<FeedbackMessage>? = withContext(Dispatchers.IO) {
        if (!inbox.canRead()) return@withContext null
        val found = LinkedHashMap<Pair<String, String>, FeedbackMessage>()
        inbox.forEachSince(nowMillis - days * DAY_MILLIS) { sms ->
            val reason = unparsedReason(sms) ?: return@forEachSince
            val body = parser.redactUnparsed(sms.body)
            val key = sms.sender to body
            val seen = found[key]
            when {
                seen != null -> found[key] = seen.copy(count = seen.count + 1)

                found.size < ParserFeedbackScanner.MAX_MESSAGES ->
                    found[key] = FeedbackMessage(sms.sender, body, reason, month(sms.receivedAt))
            }
        }
        found.values.toList()
    }

    private fun unparsedReason(sms: RawSms): UnparsedReason? = when {
        parser.isKnownBankSender(sms.sender) -> (parser.parse(sms) as? SmsParseResult.Unparsed)?.reason
        parser.looksLikeUnknownBankAlert(sms) -> UnparsedReason.UNKNOWN_SENDER
        else -> null
    }

    private fun month(epochMillis: Long): String =
        YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())).toString()

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
