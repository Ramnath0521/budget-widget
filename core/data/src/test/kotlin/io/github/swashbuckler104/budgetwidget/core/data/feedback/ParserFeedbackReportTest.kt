package io.github.swashbuckler104.budgetwidget.core.data.feedback

import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class ParserFeedbackReportTest {
    private val messages = listOf(
        FeedbackMessage(
            "ATMSBI",
            "Rs 9,999.99 debited\nfrom A/c XX9903 \"quoted\"",
            UnparsedReason.NO_MATCH,
            "2026-11",
            2,
        ),
        FeedbackMessage("JD-XXXXXX-S", "Rs.999 to Xxxxx", UnparsedReason.UNKNOWN_SENDER, "2026-10"),
    )

    @Test
    fun `one self-contained JSON object per message, newlines and quotes escaped`() {
        val text = ParserFeedbackReport.jsonLines(messages, "1.0.3")
        val lines = text.split('\n')
        assertThat(lines.last()).isEmpty()
        val objects = lines.dropLast(1).map { Json.parseToJsonElement(it).jsonObject }
        assertThat(objects).hasSize(2)
        val first = objects[0]
        assertThat(first.keys).containsExactly("v", "app", "parser", "sender", "reason", "count", "month", "body")
            .inOrder()
        assertThat(first.getValue("v").jsonPrimitive.int).isEqualTo(2)
        assertThat(first.getValue("parser").jsonPrimitive.content).isEqualTo(ParserFeedbackReport.PARSER_COMMIT)
        assertThat(first.getValue("count").jsonPrimitive.int).isEqualTo(2)
        assertThat(first.getValue("body").jsonPrimitive.content).isEqualTo(messages[0].body)
        assertThat(objects[1].getValue("reason").jsonPrimitive.content).isEqualTo("UNKNOWN_SENDER")
    }

    @Test
    fun `the email has a line for people above the same lines as the file`() {
        val email = ParserFeedbackReport.emailBody(messages, "1.0.3")
        assertThat(email).isEqualTo(
            ParserFeedbackReport.EMAIL_INTRO + "\n\n" + ParserFeedbackReport.jsonLines(messages, "1.0.3"),
        )
        assertThat(ParserFeedbackReport.jsonLines(emptyList(), "1.0.3")).isEmpty()
    }
}
