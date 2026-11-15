package io.github.swashbuckler104.budgetwidget.ingest.parser

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxEvents
import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestOrigin
import io.github.swashbuckler104.budgetwidget.core.data.ingest.IngestPipeline
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Message chains from docs/handoff/probe-sms.tsv through the real parser and pipeline (ADR-0042):
 * one money movement must be counted once, and a transfer never as spending or income.
 */
@RunWith(AndroidJUnit4::class)
class TransferChainTest {
    private lateinit var db: BudgetDatabase
    private lateinit var pipeline: IngestPipeline
    private var clock = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            BudgetDatabase::class.java,
        ).build()
        pipeline = IngestPipeline(PennyWiseSmsParser(), db, { clock }, { false }, InboxEvents(emptySet()))
        // The Transfers seed group (keys from SeedCategories); kind TRANSFER is what totals leave out.
        listOf("own_transfer", "loan_emi", "card_bill", "loan_received").forEachIndexed { i, key ->
            db.categoryDao().insert(
                CategoryEntity(
                    parentId = null,
                    name = key,
                    iconKey = "🔁",
                    colorArgb = 0,
                    kind = CategoryKind.TRANSFER,
                    sortOrder = i,
                    isSeed = true,
                    seedKey = key,
                ),
            )
        }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun receive(sender: String, body: String, minute: Int) {
        clock = START + minute * 60_000L
        pipeline.ingest(RawSms(sender, body, clock), IngestOrigin.REALTIME)
    }

    private suspend fun spent() = db.transactionDao().spentBetween(0, Long.MAX_VALUE)

    /** Income as the app counts it: incoming, not ignored, not in a transfer-kind category. */
    private suspend fun countsAsIncome(): Long {
        val rows = db.backupDao().transactions()
        val transferIds = db.backupDao().categories().filter { it.kind == CategoryKind.TRANSFER }.map { it.id }.toSet()
        return rows.filter { it.direction.name == "IN" && it.status.name != "IGNORED" && it.categoryId !in transferIds }
            .sumOf { it.amountPaise }
    }

    private suspend fun inbox() = db.transactionDao().countOpen()

    private suspend fun unparsed() = db.unparsedSmsDao().count()

    @Test
    fun `a loan EMI with its reminder, lender confirmation and loan account credit counts nowhere`() = runTest {
        receive(
            "AD-HDFCBK",
            "Dear Customer, EMI of Rs.12,500.00 for your HDFC Bank Loan A/c XX4567 is due on " +
                "05-Nov-26. Please maintain " +
                "sufficient balance in A/c XX8812. Ignore if already paid.",
            0,
        )
        receive(
            "AD-HDFCBK",
            "UPDATE: INR 12,500.00 debited from HDFC Bank XX8812 on 05-NOV-26. Info: ACH D- BAJAJ FINANCE " +
                "LTD-P400PHL1234567. Avl bal:INR 48,750.00",
            60,
        )
        receive(
            "VM-BAJAJF",
            "Dear Customer, we have received your EMI payment of Rs. 12,500 towards your Loan " +
                "A/c XX4567 on 05-11-2026. " +
                "Thank you. Bajaj Finance",
            75,
        )
        receive(
            "AD-HDFCBK",
            "Rs.12,500.00 credited to your HDFC Bank Loan A/c XX4567 on 05-Nov-26 towards EMI repayment. Outstanding " +
                "principal Rs.2,35,000.00",
            80,
        )

        assertThat(spent()).isEqualTo(0)
        assertThat(countsAsIncome()).isEqualTo(0)
        assertThat(inbox()).isEqualTo(0)
        assertThat(unparsed()).isEqualTo(0)
        val rows = db.backupDao().transactions()
        assertThat(rows).hasSize(2)
        assertThat(rows[0].linkedToId).isEqualTo(rows[1].id)
    }

    @Test
    fun `a transfer between own accounts is one linked movement`() = runTest {
        receive(
            "AD-HDFCBK",
            "Rs.10000.00 debited from A/c XX8812 on 07-Nov-26 to asha.self@kotak " +
                "(UPI Ref No 630712345678)",
            0,
        )
        receive(
            "JD-KOTAKB-S",
            "Received Rs.10000.00 in your Kotak Bank AC X4821 from asha.r@okhdfcbank on " +
                "07-11-26.UPI Ref 630712345678. " +
                "Not you, https://kotak.com/KBANKT/Fraud",
            1,
        )
        assertThat(spent()).isEqualTo(0)
        assertThat(countsAsIncome()).isEqualTo(0)
        assertThat(inbox()).isEqualTo(0)
    }

    @Test
    fun `a credit card bill paid from the bank counts once, through the card spends`() = runTest {
        receive(
            "SBICRD",
            "Rs.649.00 spent on your SBI Credit Card ending with 2290 on 05Nov26. Your " +
                "available limit is Rs.54,351.00. " +
                "If not done by you, call 39 02 02 02.",
            0,
        )
        receive(
            "AD-HDFCBK",
            "Rs.649.00 debited from A/c XX8812 on 06-Nov-26 to sbicard.2290@sbi " +
                "(UPI Ref No 630612345678)",
            60,
        )
        receive(
            "SBICRD",
            "Your payment of Rs.649.00 has been credited to your SBI Credit Card ending with " +
                "2290. Your available limit " +
                "is Rs.55,000.00.",
            70,
        )
        // Only the card spend is spending.
        assertThat(spent()).isEqualTo(649_00)
        assertThat(countsAsIncome()).isEqualTo(0)
        assertThat(inbox()).isEqualTo(1)
    }

    @Test
    fun `an ordinary day still counts as before`() = runTest {
        receive(
            "JD-KOTAKB-S",
            "Sent Rs.42.00 from Kotak Bank AC X4821 to paytmqr281105050101k27bzdmxqp@paytm on " +
                "03-11-26.UPI Ref 6301928374. " +
                "Not you, https://kotak.com/KBANKT/Fraud",
            0,
        )
        receive(
            "AD-FEDBNK",
            "Rs 4200.00 credited to your A/c XX3071 via IMPS on 04NOV2026 11:05:30 IMPS Ref no 630112233447 Bal:Rs " +
                "31840.25 -Federal Bank",
            5,
        )
        assertThat(spent()).isEqualTo(42_00)
        assertThat(countsAsIncome()).isEqualTo(4200_00)
        assertThat(inbox()).isEqualTo(2)
    }

    private companion object {
        const val START = 1_793_000_000_000L
    }
}
