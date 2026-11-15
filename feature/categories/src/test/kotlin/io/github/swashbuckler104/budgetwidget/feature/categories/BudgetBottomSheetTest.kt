package io.github.swashbuckler104.budgetwidget.feature.categories

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.swashbuckler104.budgetwidget.core.designsystem.component.BudgetBottomSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shared sheet (ADR-0041), tested here because :core:designsystem has no UI-test setup. The
 * bug it fixes: scrolling a long form back to the top a little too hard closed the sheet.
 */
@RunWith(AndroidJUnit4::class)
class BudgetBottomSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private var dismissed = 0

    private fun show(unsaved: Boolean = false) = compose.setContent {
        BudgetBottomSheet(
            onDismissRequest = { dismissed++ },
            hasUnsavedChanges = unsaved,
            header = { Text("Header") },
        ) {
            repeat(ROWS) { Text("Row $it") }
        }
    }

    @Test
    fun `swiping down hard on long content scrolls it but never closes the sheet`() {
        show()
        compose.onNodeWithText("Row 3").performTouchInput { swipeUp() }
        compose.onNodeWithText("Row 3").performTouchInput { swipeUp() }
        repeat(3) {
            compose.onNodeWithText("Header").assertIsDisplayed()
            compose.onNodeWithText("Row 3").performTouchInput { swipeDown(durationMillis = 80) }
        }
        compose.waitForIdle()
        assertThat(dismissed).isEqualTo(0)
        compose.onNodeWithText("Header").assertIsDisplayed()
    }

    @Test
    fun `dragging the header down closes a sheet without changes`() {
        show()
        compose.onNodeWithText("Header").performTouchInput { swipeDown(endY = bottom + 2_000f) }
        compose.waitForIdle()
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    fun `with unsaved changes it asks before closing, and can keep editing`() {
        show(unsaved = true)
        compose.onNodeWithText("Header").performTouchInput { swipeDown(endY = bottom + 2_000f) }
        compose.waitForIdle()
        assertThat(dismissed).isEqualTo(0)
        compose.onNodeWithText("Discard changes?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Header").assertIsDisplayed()
        assertThat(dismissed).isEqualTo(0)

        compose.onNodeWithText("Header").performTouchInput { swipeDown(endY = bottom + 2_000f) }
        compose.onNodeWithText("Discard").performClick()
        compose.waitForIdle()
        assertThat(dismissed).isEqualTo(1)
    }

    private companion object {
        const val ROWS = 80
    }
}
