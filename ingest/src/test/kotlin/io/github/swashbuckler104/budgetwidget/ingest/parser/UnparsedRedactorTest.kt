package io.github.swashbuckler104.budgetwidget.ingest.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Fake messages in the style of docs/handoff/probe-sms.tsv; no real data. */
class UnparsedRedactorTest {
    private fun redact(body: String) = UnparsedRedactor.redact(body)

    @Test
    fun `account and card numbers keep only their last 2 digits`() {
        assertThat(
            redact("debited from HDFC Bank XX8812 on 05-NOV-26"),
        ).isEqualTo("debited from HDFC Bank XXXX12 on 05-NOV-26")
        assertThat(redact("from A/c ****8812 to")).isEqualTo("from A/c ****XX12 to")
        assertThat(redact("Kotak Bank AC X4821 from")).isEqualTo("Kotak Bank AC XXX21 from")
        assertThat(redact("Credit Card ending 2290 is")).isEqualTo("Credit Card ending XX90 is")
        assertThat(redact("Loan A/c no. XX771904 has")).isEqualTo("Loan A/c no. XXXXXX04 has")
    }

    @Test
    fun `UPI IDs, links, references and phone numbers are masked`() {
        assertThat(
            redact("to asha.self@kotak (UPI Ref No 630712345678)"),
        ).isEqualTo("to <upi> (UPI Ref No XXXXXXXXXX78)")
        assertThat(redact("Not you, https://kotak.com/KBANKT/Fraud")).isEqualTo("Not you, <link>")
        assertThat(redact("Call 18005556789 or +919951860002.")).isEqualTo("Call XXXXXXXXX89 or +XXXXXXXXXX02.")
        assertThat(
            redact("ACH D- BAJAJ FINANCE LTD-P400PHL1234567."),
        ).isEqualTo("ACH D- BAJAJ FINANCE LTD-P400PHLXXXXX67.")
    }

    @Test
    fun `amounts, dates, times and wording stay`() {
        val body = "INR 12,500.00 debited on 06-11-2026 20:38:23 IST. Avl bal:INR 48,750.00 Rs.1,24,500 ₹99"
        assertThat(redact(body)).isEqualTo(body)
        assertThat(redact("on 04NOV2026 11:05:30")).isEqualTo("on 04NOV2026 11:05:30")
    }

    @Test
    fun `redacting twice changes nothing`() {
        val once = redact("Rs.5000.00 debited from A/c XX8812 to sbicard.2290@sbi (UPI Ref No 630612345678)")
        assertThat(redact(once)).isEqualTo(once)
    }
}
