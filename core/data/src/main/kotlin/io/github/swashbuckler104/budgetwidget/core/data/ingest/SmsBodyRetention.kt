package io.github.swashbuckler104.budgetwidget.core.data.ingest

import androidx.room.withTransaction
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.database.BudgetDatabase
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import io.github.swashbuckler104.budgetwidget.core.datastore.SmsRetention
import javax.inject.Inject
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Applies "Keep original SMS text" (ADR-0045, audit F3). Past the limit, a sorted transaction
 * loses its stored SMS but keeps every parsed field (amount, merchant, account, reference), and
 * unparsed entries, which are nothing but their SMS, are deleted. Transactions still waiting in
 * the inbox keep their SMS. Idempotent; the reconcile worker runs it on every scan, and Settings
 * runs it when the limit changes.
 */
class SmsBodyRetention @Inject constructor(
    private val db: BudgetDatabase,
    private val settings: SettingsStore,
    private val clock: WallClock,
) {
    /** Returns how many stored SMS were dropped (transaction texts plus unparsed entries). */
    suspend fun apply(): Int = apply(settings.smsRetention.first())

    suspend fun apply(retention: SmsRetention): Int {
        val days = retention.days ?: return 0
        val before = clock.nowMillis() - days.days.inWholeMilliseconds
        return withContext(Dispatchers.IO) {
            db.withTransaction {
                db.transactionDao().dropSmsBodiesBefore(before) + db.unparsedSmsDao().deleteBefore(before)
            }
        }
    }
}
