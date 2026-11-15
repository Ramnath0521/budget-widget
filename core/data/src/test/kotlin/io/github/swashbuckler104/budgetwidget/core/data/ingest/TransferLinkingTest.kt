package io.github.swashbuckler104.budgetwidget.core.data.ingest

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.category.TransferSeeds
import io.github.swashbuckler104.budgetwidget.core.data.inbox.InboxEvents
import io.github.swashbuckler104.budgetwidget.core.data.inbox.RecordingInboxListener
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.CategoryEntity
import io.github.swashbuckler104.budgetwidget.core.model.CategoryKind
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.ParserType
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import io.github.swashbuckler104.budgetwidget.core.model.sms.FlowHint
import io.github.swashbuckler104.budgetwidget.core.model.sms.ParsedSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.RawSms
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParseResult
import io.github.swashbuckler104.budgetwidget.core.model.sms.SmsParser
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** ADR-0042: transfers are sorted out of the totals, and the two legs of one movement are linked. */
@RunWith(AndroidJUnit4::class)
class TransferLinkingTest {
    private lateinit var db: BudgetDatabase
    private val parser = ScriptedParser()
    private val listener = RecordingInboxListener()
    private lateinit var pipeline: IngestPipeline
    private val categories = mutableMapOf<String, Long>()

    @Before
    fun setUp() = runTest {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                BudgetDatabase::class.java,
            )
                .build()
        pipeline = IngestPipeline(parser, db, { NOW }, { false }, InboxEvents(setOf(listener)))
        listOf(TransferSeeds.OWN_TRANSFER, TransferSeeds.LOAN_EMI, TransferSeeds.CARD_BILL).forEachIndexed { i, key ->
            categories[key] = db.categoryDao().insert(
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

    private suspend fun ingest(sms: ParsedSms): Long {
        parser.next = sms
        return (
            pipeline.ingest(
                RawSms(sms.sender, sms.body, sms.occurredAt),
                IngestOrigin.REALTIME,
            ) as IngestOutcome.Inserted
            ).id
    }

    private suspend fun row(id: Long) = db.transactionDao().get(id)!!

    private suspend fun spent() = db.transactionDao().spentBetween(0, Long.MAX_VALUE)

    @Test
    fun `a loan EMI the parser recognised leaves the inbox and the totals, without a notification`() = runTest {
        val id = ingest(sms("emi", 12_500_00, Direction.OUT, "8812", flow = FlowHint.LOAN_REPAYMENT))
        assertThat(row(id).status).isEqualTo(TransactionStatus.CATEGORIZED)
        assertThat(row(id).categoryId).isEqualTo(categories[TransferSeeds.LOAN_EMI])
        assertThat(row(id).autoCategorized).isTrue()
        assertThat(spent()).isEqualTo(0)
        assertThat(listener.events).isEmpty()
    }

    @Test
    fun `both legs of a transfer between own accounts are linked by reference and sorted`() = runTest {
        val out = ingest(sms("s1", 10_000_00, Direction.OUT, "8812", ref = "630712345678", bank = "HDFC Bank"))
        assertThat(row(out).status).isEqualTo(TransactionStatus.PENDING)
        val inc =
            ingest(sms("s2", 10_000_00, Direction.IN, "4821", ref = "630712345678", bank = "Kotak Bank", at = 2_000))

        assertThat(row(out).linkedToId).isEqualTo(inc)
        assertThat(row(inc).linkedToId).isEqualTo(out)
        listOf(out, inc).forEach { assertThat(row(it).categoryId).isEqualTo(categories[TransferSeeds.OWN_TRANSFER]) }
        assertThat(spent()).isEqualTo(0)
    }

    @Test
    fun `the bank debit and the loan account credit of one EMI are linked by amount, in either order`() = runTest {
        // Lender side first, then a bank debit the parser did not recognise as an EMI.
        val credit = ingest(sms("l4", 8_450_00, Direction.IN, "9012", flow = FlowHint.LOAN_REPAYMENT, bank = "SBI"))
        val debit = ingest(sms("l7", 8_450_00, Direction.OUT, "5503", bank = "SBI", at = 1_500))
        assertThat(row(debit).linkedToId).isEqualTo(credit)
        assertThat(row(debit).categoryId).isEqualTo(categories[TransferSeeds.LOAN_EMI])
        assertThat(spent()).isEqualTo(0)

        // Bank side first (plain), then the card's confirmation.
        val bill = ingest(sms("c1", 5_000_00, Direction.OUT, "8812", at = 3_000))
        assertThat(row(bill).status).isEqualTo(TransactionStatus.PENDING)
        val card =
            ingest(
                sms(
                    "c2",
                    5_000_00,
                    Direction.IN,
                    "2290",
                    flow = FlowHint.CARD_BILL_PAYMENT,
                    bank = "SBI Card",
                    at = 4_000,
                ),
            )
        assertThat(row(bill).linkedToId).isEqualTo(card)
        assertThat(row(bill).categoryId).isEqualTo(categories[TransferSeeds.CARD_BILL])
        assertThat(spent()).isEqualTo(0)
    }

    @Test
    fun `nothing is linked when two legs could match`() = runTest {
        val a = ingest(sms("a", 5_000_00, Direction.OUT, "8812"))
        val b = ingest(sms("b", 5_000_00, Direction.OUT, "1234", bank = "Axis Bank", at = 1_100))
        val card =
            ingest(
                sms(
                    "c",
                    5_000_00,
                    Direction.IN,
                    "2290",
                    flow = FlowHint.CARD_BILL_PAYMENT,
                    bank = "SBI Card",
                    at = 1_200,
                ),
            )
        listOf(a, b, card).forEach { assertThat(row(it).linkedToId).isNull() }
        listOf(a, b).forEach { assertThat(row(it).status).isEqualTo(TransactionStatus.PENDING) }
        // The card credit itself is still a transfer, never income.
        assertThat(row(card).categoryId).isEqualTo(categories[TransferSeeds.CARD_BILL])
    }

    @Test
    fun `legs further apart than 3 days are not linked`() = runTest {
        val out = ingest(sms("x", 10_000_00, Direction.OUT, "8812", ref = "630700000001"))
        val inc = ingest(
            sms("y", 10_000_00, Direction.IN, "4821", ref = "630700000001", bank = "Kotak Bank", at = 1_000 + 4 * DAY),
        )
        assertThat(row(out).linkedToId).isNull()
        assertThat(row(inc).linkedToId).isNull()
    }

    @Test
    fun `a hinted transaction waits in the inbox when its Transfers category is archived`() = runTest {
        db.categoryDao().setArchivedWithChildren(categories.getValue(TransferSeeds.LOAN_EMI), true)
        val id = ingest(sms("emi", 12_500_00, Direction.OUT, "8812", flow = FlowHint.LOAN_REPAYMENT))
        assertThat(row(id).status).isEqualTo(TransactionStatus.PENDING)
        assertThat(listener.events).hasSize(1)
    }

    private class ScriptedParser : SmsParser {
        lateinit var next: ParsedSms

        override fun isKnownBankSender(sender: String) = true

        override fun parse(sms: RawSms): SmsParseResult = SmsParseResult.Transaction(next)
    }

    private fun sms(
        hash: String,
        paise: Long,
        direction: Direction,
        last4: String,
        ref: String? = null,
        flow: FlowHint? = null,
        bank: String = "HDFC Bank",
        at: Long = 1_000,
    ) = ParsedSms(
        txnHash = hash,
        amountPaise = paise,
        direction = direction,
        parserType = if (flow !=
            null
        ) {
            ParserType.TRANSFER
        } else if (direction == Direction.IN) {
            ParserType.INCOME
        } else {
            ParserType.EXPENSE
        },
        isCard = false,
        merchant = null,
        reference = ref,
        bankName = bank,
        accountLast4 = last4,
        balanceAfterPaise = null,
        sender = "AD-BANK",
        body = "body $hash",
        occurredAt = at,
        flow = flow,
    )

    private companion object {
        const val NOW = 42L
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
