package io.github.swashbuckler104.budgetwidget.core.data.ingest

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.inMemoryDb
import io.github.swashbuckler104.budgetwidget.core.data.testTxn
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.database.entity.UnparsedSmsEntity
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.datastore.SmsRetention
import io.github.swashbuckler104.budgetwidget.core.model.TransactionStatus
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsBodyRetentionTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val db: BudgetDatabase = inMemoryDb()
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day

    @After
    fun tearDown() = db.close()

    private fun TestScope.settings() = SettingsStore(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.root.resolve("s.preferences_pb") },
    )

    private suspend fun seed(): Map<String, Long> {
        val dao = db.transactionDao()
        fun sms(hash: String, ageDays: Long, status: TransactionStatus) = testTxn(hash, at = now - ageDays * day)
            .copy(smsBody = "Rs 100 debited ($hash)", smsSender = "AD-BANKSM", status = status)
        val ids = mapOf(
            "oldSorted" to dao.insert(sms("oldSorted", 40, TransactionStatus.CATEGORIZED)),
            "oldIgnored" to dao.insert(sms("oldIgnored", 100, TransactionStatus.IGNORED)),
            "oldPending" to dao.insert(sms("oldPending", 100, TransactionStatus.PENDING)),
            "newSorted" to dao.insert(sms("newSorted", 29, TransactionStatus.CATEGORIZED)),
        )
        listOf(10L, 45L, 120L).forEach { ageDays ->
            db.unparsedSmsDao().insertOrIgnore(
                UnparsedSmsEntity(
                    sender = "AD-BANKSM",
                    bodyHash = "h$ageDays",
                    body = "body $ageDays",
                    receivedAt = now - ageDays * day,
                    reason = UnparsedReason.NO_MATCH,
                ),
            )
        }
        return ids
    }

    private suspend fun bodies(ids: Map<String, Long>) = ids.mapValues { (_, id) ->
        db.transactionDao().get(id)!!.smsBody
    }

    private suspend fun unparsedAges() = db.unparsedSmsDao().getAll().map { (now - it.receivedAt) / day }.sorted()

    @Test
    fun `forever, the default, keeps every SMS`() = runTest {
        val ids = seed()
        val retention = SmsBodyRetention(db, settings()) { now }

        assertThat(retention.apply()).isEqualTo(0)
        assertThat(bodies(ids).values).doesNotContain(null)
        assertThat(unparsedAges()).containsExactly(10L, 45L, 120L).inOrder()
    }

    @Test
    fun `30 days drops the SMS of older sorted transactions and older unparsed entries only`() = runTest {
        val ids = seed()
        val settings = settings().apply { setSmsRetention(SmsRetention.DAYS_30) }
        val retention = SmsBodyRetention(db, settings) { now }
        val before = db.transactionDao().get(ids.getValue("oldSorted"))!!

        assertThat(retention.apply()).isEqualTo(4)

        val after = bodies(ids)
        assertThat(after["oldSorted"]).isNull()
        assertThat(after["oldIgnored"]).isNull()
        assertThat(after["oldPending"]).isNotNull()
        assertThat(after["newSorted"]).isNotNull()
        assertThat(unparsedAges()).containsExactly(10L)
        // Only the text goes: what the transaction says stays, and the edit time doesn't move.
        assertThat(db.transactionDao().get(ids.getValue("oldSorted"))).isEqualTo(before.copy(smsBody = null))

        assertThat(retention.apply()).isEqualTo(0)
    }

    @Test
    fun `90 days keeps what 30 days would drop`() = runTest {
        val ids = seed()
        val retention = SmsBodyRetention(db, settings()) { now }

        assertThat(retention.apply(SmsRetention.DAYS_90)).isEqualTo(2)

        val after = bodies(ids)
        assertThat(after["oldIgnored"]).isNull()
        assertThat(after["oldSorted"]).isNotNull()
        assertThat(unparsedAges()).containsExactly(10L, 45L).inOrder()
    }
}
