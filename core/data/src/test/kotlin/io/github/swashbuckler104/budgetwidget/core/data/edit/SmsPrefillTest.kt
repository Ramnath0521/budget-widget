package io.github.swashbuckler104.budgetwidget.core.data.edit

import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.model.Direction
import org.junit.Test

/** Fake messages in the style of docs/handoff/probe-sms.tsv; no real data. */
class SmsPrefillTest {
    @Test
    fun `first rupee amount, in paise, in every common spelling`() {
        assertThat(SmsPrefill.from("Rs.1,24,500.50 debited, Avl Bal Rs 9,000").amountPaise).isEqualTo(124_500_50)
        assertThat(SmsPrefill.from("INR 250 spent at shop").amountPaise).isEqualTo(250_00)
        assertThat(SmsPrefill.from("₹ 99.5 received").amountPaise).isEqualTo(99_50)
        assertThat(SmsPrefill.from("Rs 0.00 charged").amountPaise).isNull()
        assertThat(SmsPrefill.from("Your A/c XX1234 was updated").amountPaise).isNull()
        assertThat(SmsPrefill.from("hours 5 and Rs.12 sent").amountPaise).isEqualTo(12_00)
        assertThat(SmsPrefill.from("Rs 99,99,99,99,99,999 debited").amountPaise).isNull()
    }

    @Test
    fun `the first movement word decides the direction`() {
        assertThat(SmsPrefill.from("Rs.240 debited from A/c XX81 and credited to Asha").direction)
            .isEqualTo(Direction.OUT)
        assertThat(SmsPrefill.from("Rs.240 credited to A/c XX81 from Asha, debited from theirs").direction)
            .isEqualTo(Direction.IN)
        assertThat(SmsPrefill.from("A/C XX03 has a credit by NEFT of Rs 82,000").direction).isEqualTo(Direction.IN)
        assertThat(SmsPrefill.from("A/C XX03 has a debit by transfer of Rs 8,450").direction).isEqualTo(Direction.OUT)
        assertThat(SmsPrefill.from("Refund of Rs 99 processed").direction).isEqualTo(Direction.IN)
        assertThat(SmsPrefill.from("Rs 99 for your account").direction).isNull()
    }
}
