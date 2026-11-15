package io.github.swashbuckler104.budgetwidget.ingest.feedback

import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.feedback.ParserFeedbackReport
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import io.github.swashbuckler104.budgetwidget.ingest.parser.PennyWiseSmsParser
import io.github.swashbuckler104.budgetwidget.ingest.work.SmsInboxSource
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Fake messages in the style of docs/handoff/probe-sms.tsv; no real data. */
class InboxParserFeedbackScannerTest {
    private class FakeInbox(private val readable: Boolean, private val messages: List<RawSms>) : SmsInboxSource {
        var since: Long? = null

        override fun canRead() = readable

        override suspend fun forEachSince(afterMillis: Long, action: suspend (RawSms) -> Unit) {
            since = afterMillis
            messages.filter { it.receivedAt > afterMillis }.forEach { action(it) }
        }
    }

    private val now = 1_794_000_000_000L
    private fun sms(sender: String, body: String, daysAgo: Int = 1) = RawSms(sender, body, now - daysAgo * DAY)

    private val inbox = listOf(
        // Parsed fine: not feedback.
        sms("JD-KOTAKB-S", "Sent Rs.42.00 from Kotak Bank AC X4821 to shop@paytm on 03-11-26.UPI Ref 6301928374."),
        // A bank alert the parser can't read: kept twice, counted once.
        sms("ATMSBI", "Dear Customer, Your A/C XXXXX5503 has a debit by transfer of Rs 8,450.00 on 05/11/26. -SBI"),
        sms("ATMSBI", "Dear Customer, Your A/C XXXXX5503 has a debit by transfer of Rs 8,450.00 on 05/11/26. -SBI"),
        // Unknown bank sender with a transaction alert: kept, masked.
        sms(
            "JD-JUPITR-S",
            "Rs.275.00 debited from your Jupiter account XX4410 via UPI to chaayos@ybl. Ref 630812345678.",
        ),
        // Never kept: OTP, offer, personal, reminder, and anything older than 90 days.
        sms("AD-HDFCBK", "OTP is 482913 for txn of INR 2,499.00 at AMAZON on HDFC Bank card ending 1234."),
        sms("AD-HDFCBK", "Pre-approved Personal Loan of Rs.5,00,000 for you! Apply now on HDFC Bank NetBanking."),
        sms("+919812345678", "Bro I sent Rs 500 to your account, credited? check"),
        sms("AD-HDFCBK", "EMI of Rs.12,500.00 for your Loan A/c XX4567 is due on 05-Nov-26. Ignore if already paid."),
        sms("ATMSBI", "Your A/C XXXXX5503 has a debit by transfer of Rs 1.00 on 01/06/26. -SBI", daysAgo = 120),
    )

    @Test
    fun `keeps only unread bank alerts from the last 90 days, masked and counted`() = runTest {
        val fake = FakeInbox(true, inbox)
        val found = InboxParserFeedbackScanner(fake, PennyWiseSmsParser()).scan(90, now)!!

        assertThat(fake.since).isEqualTo(now - 90 * DAY)
        assertThat(found.map { it.sender }).containsExactly("ATMSBI", "JD-JUPITR-S").inOrder()
        val sbi = found[0]
        assertThat(sbi.count).isEqualTo(2)
        assertThat(sbi.reason).isEqualTo(UnparsedReason.NO_MATCH)
        assertThat(sbi.body)
            .isEqualTo("Dear Customer, Your A/C XXXXX9903 has a debit by transfer of Rs 9,999.99 on 99/99/99. -SBI")
        val jupiter = found[1]
        assertThat(jupiter.reason).isEqualTo(UnparsedReason.UNKNOWN_SENDER)
        assertThat(jupiter.body)
            .isEqualTo("Rs.999.99 debited from your Jupiter account XX9910 via UPI to <UPI>. Ref 999999999999.")
        assertThat(jupiter.month).matches("""\d{4}-\d{2}""")
    }

    @Test
    fun `keep real amounts keeps only the amounts`() = runTest {
        val found = InboxParserFeedbackScanner(FakeInbox(true, inbox), PennyWiseSmsParser())
            .scan(90, now, keepAmounts = true)!!
        assertThat(found.map { it.body }).containsExactly(
            "Dear Customer, Your A/C XXXXX9903 has a debit by transfer of Rs 8,450.00 on 99/99/99. -SBI",
            "Rs.275.00 debited from your Jupiter account XX9910 via UPI to <UPI>. Ref 999999999999.",
        ).inOrder()
    }

    @Test
    fun `a bank alert with a secret in it is never shared`() = runTest {
        val alert =
            sms(
                "ATMSBI",
                "Your A/C XXXXX5503 has a debit by transfer of Rs 9.00 on 05/11/26. Never share your PIN. -SBI",
            )
        val found = InboxParserFeedbackScanner(FakeInbox(true, listOf(alert)), PennyWiseSmsParser()).scan(90, now)!!
        assertThat(found).isEmpty()
    }

    @Test
    fun `without SMS access there is nothing to scan`() = runTest {
        assertThat(InboxParserFeedbackScanner(FakeInbox(false, inbox), PennyWiseSmsParser()).scan(90, now)).isNull()
    }

    @Test
    fun `the report lists every message with its sender, reason and month`() = runTest {
        val found = InboxParserFeedbackScanner(FakeInbox(true, inbox), PennyWiseSmsParser()).scan(90, now)!!
        val lines = ParserFeedbackReport.jsonLines(found, "1.0.3").lines().filter { it.isNotEmpty() }
        assertThat(lines).hasSize(2)
        assertThat(lines[0]).startsWith("""{"v":2,"app":"1.0.3","parser":"0623c2b","sender":"ATMSBI",""")
        assertThat(lines[0]).contains(""""reason":"NO_MATCH","count":2,""")
        assertThat(lines.joinToString()).doesNotContain("482913")
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
