package io.github.swashbuckler104.budgetwidget.core.model.sms

import io.github.swashbuckler104.budgetwidget.core.model.Direction
import io.github.swashbuckler104.budgetwidget.core.model.ParserType

/**
 * A transaction read from one bank SMS. Money is in paise (positive; [direction] gives the
 * sign). [occurredAt] is the SMS receive time, a UTC instant in epoch milliseconds.
 *
 * [txnHash] is stable for the same SMS whether it arrives through the receiver or the inbox scan,
 * so it is the idempotency key.
 */
data class ParsedSms(
    val txnHash: String,
    val amountPaise: Long,
    val direction: Direction,
    val parserType: ParserType,
    val isCard: Boolean,
    val merchant: String?,
    val reference: String?,
    val bankName: String,
    val accountLast4: String?,
    val balanceAfterPaise: Long?,
    val sender: String,
    val body: String,
    val occurredAt: Long,
    /** Money moved between the user's own money pots, not spent or earned (ADR-0042). */
    val flow: FlowHint? = null,
)

/**
 * What a transaction really is when it is not spending or income (ADR-0042). Each one is sorted
 * into a transfer-kind seed category, which every total already leaves out.
 */
enum class FlowHint {
    /** Between two of the user's own accounts (both legs are linked). */
    OWN_TRANSFER,

    /** A loan EMI paid from a bank account. */
    LOAN_REPAYMENT,

    /** A credit card bill paid from a bank account; the card spends were already counted. */
    CARD_BILL_PAYMENT,

    /** A loan paid out into a bank account. */
    LOAN_DISBURSAL,
}
