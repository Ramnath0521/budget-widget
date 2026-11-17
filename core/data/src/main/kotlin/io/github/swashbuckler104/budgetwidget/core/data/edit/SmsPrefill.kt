package io.github.swashbuckler104.budgetwidget.core.data.edit

import io.github.swashbuckler104.budgetwidget.core.model.Direction
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A first guess for "Add as transaction" on an unparsed bank SMS (ADR-0048, plan B5): the first
 * rupee amount and the money direction from the first movement word. Only a starting point for the
 * add form; the user checks and saves it. Null fields mean "no guess".
 */
data class SmsPrefill(val amountPaise: Long?, val direction: Direction?) {
    companion object {
        fun from(body: String): SmsPrefill = SmsPrefill(amount(body), direction(body))

        private fun amount(body: String): Long? {
            val number = AMOUNT.find(body)?.groupValues?.get(1)?.replace(",", "") ?: return null
            return try {
                // Money = Long paise, converted once with explicit rounding (CLAUDE.md constraint 5).
                BigDecimal(number).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
                    .takeIf { it in 1..TransactionEditor.MAX_AMOUNT_PAISE }
            } catch (_: ArithmeticException) {
                null
            } catch (_: NumberFormatException) {
                null
            }
        }

        /** Whichever kind of movement word comes first: "debited from A/c ... credited to X" is money out. */
        private fun direction(body: String): Direction? {
            val firstIn = MONEY_IN.find(body)?.range?.first
            val firstOut = MONEY_OUT.find(body)?.range?.first
            return when {
                firstIn == null && firstOut == null -> null
                firstOut == null -> Direction.IN
                firstIn == null -> Direction.OUT
                firstIn < firstOut -> Direction.IN
                else -> Direction.OUT
            }
        }

        private val AMOUNT = Regex("""(?i)(?:(?<![a-z])(?:rs\.?|inr)|₹)\s*(\d[\d,]*(?:\.\d{1,2})?)""")
        private val MONEY_IN = Regex(
            """(?i)\b(credited|received|deposited|refunded|refund|cashback|reversed)\b|\bcredit\s+(by|of)\b""",
        )
        private val MONEY_OUT = Regex(
            """(?i)\b(debited|spent|paid|sent|withdrawn|purchase|deducted|charged)\b|\bdebit\s+(by|of)\b""",
        )
    }
}
