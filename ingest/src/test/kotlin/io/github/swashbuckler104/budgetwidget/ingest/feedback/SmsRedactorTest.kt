package io.github.swashbuckler104.budgetwidget.ingest.feedback

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParseResult
import io.github.swashbuckler104.budgetwidget.ingest.parser.GoldenCorpus
import io.github.swashbuckler104.budgetwidget.ingest.parser.PennyWiseSmsParser
import java.text.Normalizer
import org.junit.Test

/** Fake messages in the style of docs/handoff/probe-sms.tsv; no real data. */
class SmsRedactorTest {
    private fun redact(body: String, keepAmounts: Boolean = false) = SmsRedactor.redact(body, keepAmounts)

    @Test
    fun `messages with a secret are dropped, not masked`() {
        listOf(
            "482913 is your OTP for Rs.500 at shop",
            "Use OTP482913 to pay Rs 500",
            "Your CVV and PIN are never asked for. Rs.500 debited",
            "Your net banking password was changed",
        ).forEach { assertWithMessage(it).that(redact(it)).isNull() }
    }

    @Test
    fun `links and UPI IDs or emails become placeholders`() {
        assertThat(redact("Rs.5 to asha.self@okaxis. Not you? https://bank.example/f?x=1"))
            .isEqualTo("Rs.9 to <UPI>. Not you? <URL>")
        assertThat(redact("visit www.bank.example now")).isEqualTo("visit <URL> now")
    }

    @Test
    fun `every digit becomes 9 with its separators kept, except the last 2 of an account or card`() {
        assertThat(redact("Rs.1,24,500.00 debited from A/c XX8812 on 05-11-26 14:05"))
            .isEqualTo("Rs.9,99,999.99 debited from A/c XX9912 on 99-99-99 99:99")
        assertThat(redact("Credit Card ending 2290")).isEqualTo("Credit Card ending 9990")
        assertThat(redact("from A/c no. 123456784821 to")).isEqualTo("from A/c no. 999999999921 to")
        assertThat(redact("Acct ****8812 and AC X4821")).isEqualTo("Acct ****9912 and AC X9921")
        // A reference is not an account: all of it goes, and it stays 12 digits long.
        assertThat(redact("UPI Ref No 630712345678")).isEqualTo("UPI Ref No 999999999999")
    }

    @Test
    fun `keep real amounts keeps the amount but still masks balances and limits`() {
        assertThat(
            redact("INR 12,500.00 debited. Avl bal: INR 48,750.00. Avl Lmt Rs 9,000", keepAmounts = true),
        ).isEqualTo("INR 12,500.00 debited. Avl bal: INR 99,999.99. Avl Lmt Rs 9,999")
        assertThat(redact("Sent ₹350 from Kotak", keepAmounts = true)).isEqualTo("Sent ₹350 from Kotak")
    }

    @Test
    fun `words outside the vocabulary become x of the same length and case`() {
        assertThat(redact("Rs 450 spent at SWIGGY Bangalore for Ramesh Kumar via UPI"))
            .isEqualTo("Rs 999 spent at XXXXXX Xxxxxxxxx for Xxxxxx Xxxxx via UPI")
        assertThat(redact("ACH D- BAJAJ FINANCE LTD-P400PHL1234567"))
            .isEqualTo("ACH D- XXXXX FINANCE LTD-P999XXX9999999")
        assertThat(redact("रमेश को Rs 5 sent")).isEqualTo("xxxx xx Rs 9 sent")
        // Styled letters are judged by their plain form; styled and non-Latin digits are masked too.
        assertThat(
            redact("Rs 𝟣𝟤𝟥 𝗌𝗉𝖾𝗇𝗍 at 𝖣𝖬𝖺𝗋𝗍 ref ४५६७"),
        ).isEqualTo("Rs 999 𝗌𝗉𝖾𝗇𝗍 at XXxxx ref 9999")
    }

    @Test
    fun `the sender's wording, the bank and the date words stay`() {
        val body = "Dear Customer, Rs.9.99 debited from your HDFC Bank A/c XX9912 on 99-Nov-99 via UPI"
        assertThat(redact(body)).isEqualTo(body)
    }

    @Test
    fun `redacting twice changes nothing`() {
        val once = redact("Rs.5000.00 debited from A/c XX8812 to sbicard.2290@sbi (UPI Ref No 630612345678) Mr Arun")!!
        assertThat(redact(once)).isEqualTo(once)
        val kept = redact("INR 1,250.00 paid at Cafe Leo, Avl Bal INR 3,000", keepAmounts = true)!!
        assertThat(redact(kept, keepAmounts = true)).isEqualTo(kept)
    }

    @Test
    fun `nothing identifying survives anywhere in the golden corpus or the probe set`() {
        val bodies = goldenBodies() + probeBodies()
        assertThat(bodies.size).isGreaterThan(80)
        bodies.forEach { body ->
            listOf(false, true).forEach { keepAmounts ->
                val out = redact(body, keepAmounts) ?: return@forEach
                val why = "$body -> $out"
                assertWithMessage("no @: $why").that(out).doesNotContain("@")
                LONG_RUN.findAll(out).forEach { run ->
                    assertWithMessage("6+ digit run is 9s but its last 2: $why")
                        .that(run.value.dropLast(2).all { it == '9' }).isTrue()
                }
                WORD.findAll(out).forEach { word ->
                    val w = word.value
                    val plain = Normalizer.normalize(w, Normalizer.Form.NFKC).lowercase()
                    assertWithMessage("word '$w' is in the vocabulary or masked: $why").that(
                        w.codePointCount(0, w.length) == 1 || plain in RedactorVocabulary ||
                            w.all { it == 'x' || it == 'X' },
                    ).isTrue()
                }
                assertWithMessage("only ASCII 9s and kept digits: $why")
                    .that(out.codePoints().noneMatch { Character.isDigit(it) && it > 'z'.code }).isTrue()
                assertWithMessage("idempotent: $why").that(redact(out, keepAmounts)).isEqualTo(out)
            }
        }
    }

    @Test
    fun `masked golden messages keep their shape, and with real amounts still parse the same`() {
        val parser = PennyWiseSmsParser()
        var parsed = 0
        GoldenCorpus.BANK_FILES.flatMap(GoldenCorpus::load).forEach { case ->
            val body = case.required("body")
            val sender = case.required("sender")
            val out = redact(body, keepAmounts = true) ?: return@forEach
            if (!LINK_OR_HANDLE.containsMatchIn(body)) {
                assertWithMessage("${case.name}: same length").that(out.codePointCount(0, out.length))
                    .isEqualTo(body.codePointCount(0, body.length))
            }
            val before = parser.parse(RawSms(sender, body, 0))
            if (before !is SmsParseResult.Transaction) return@forEach
            val after = parser.parse(RawSms(sender, out, 0))
            assertWithMessage("${case.name}: still a transaction after masking\n$out")
                .that(after).isInstanceOf(SmsParseResult.Transaction::class.java)
            after as SmsParseResult.Transaction
            assertWithMessage("${case.name}: same amount").that(after.parsed.amountPaise)
                .isEqualTo(before.parsed.amountPaise)
            assertWithMessage("${case.name}: same direction").that(after.parsed.direction)
                .isEqualTo(before.parsed.direction)
            parsed++
        }
        assertThat(parsed).isGreaterThan(30)
    }

    private fun goldenBodies() = GoldenCorpus.BANK_FILES.flatMap(GoldenCorpus::load).map { it.required("body") }

    /** docs/handoff/probe-sms.tsv: `id<TAB>sender<TAB>body`, all fake. */
    private fun probeBodies(): List<String> {
        val file = generateSequence(java.io.File("").absoluteFile) { it.parentFile }
            .map { it.resolve("docs/handoff/probe-sms.tsv") }
            .first { it.exists() }
        return file.readLines().filter { '\t' in it }.map { it.substringAfterLast('\t') }
    }

    private companion object {
        val LONG_RUN = Regex("""\d{6,}""")
        val WORD = Regex("""[\p{L}\p{M}]+""")
        val LINK_OR_HANDLE = Regex("""(?i)https?://|www\.|@""")
    }
}
