package io.github.swashbuckler104.budgetwidget.feature.settings

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.data.feedback.FeedbackMessage
import io.github.swashbuckler104.budgetwidget.core.data.feedback.ParserFeedbackScanner
import io.github.swashbuckler104.budgetwidget.core.model.sms.UnparsedReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ParserFeedbackTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `keep real amounts is off unless ticked, and reaches the scan`() {
        val scans = mutableListOf<Boolean>()
        compose.setContent {
            ParserFeedbackScreen(
                state = FeedbackState.Intro,
                snackbar = SnackbarHostState(),
                onBack = {},
                onScan = { scans += it },
                onRemove = {},
                onEmail = {},
                onSave = {},
            )
        }
        compose.onNodeWithText("Scan last 90 days").performClick()
        compose.onNodeWithText("Keep real amounts").performClick()
        compose.onNodeWithText("Scan last 90 days").performClick()
        assertThat(scans).containsExactly(false, true).inOrder()
    }

    @Test
    fun `the view model passes the choice to the scanner and shows what it found`() = runTest {
        val asked = mutableListOf<Boolean>()
        val scanner = object : ParserFeedbackScanner {
            override suspend fun scan(days: Int, nowMillis: Long, keepAmounts: Boolean): List<FeedbackMessage> {
                asked += keepAmounts
                return listOf(FeedbackMessage("AD-BANKSM", "Rs 999 spent", UnparsedReason.NO_MATCH, "2026-11"))
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = ParserFeedbackViewModel(scanner, { 0L }, context.contentResolver)

        viewModel.scan(keepAmounts = true)

        val ready = viewModel.state.first { it is FeedbackState.Ready } as FeedbackState.Ready
        assertThat(asked).containsExactly(true)
        assertThat(ready.messages.single().body).isEqualTo("Rs 999 spent")
        assertThat(viewModel.report("1.0.3")).contains("words the app doesn't know are x")
    }
}
