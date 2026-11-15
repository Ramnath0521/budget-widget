package io.github.swashbuckler104.budgetwidget.feature.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/**
 * Shows sheet content the way `BudgetBottomSheet` does: inside a vertical scroll. The contents no
 * longer scroll themselves (ADR-0041), so tests that scroll to a node need this parent.
 */
internal fun ComposeContentTestRule.setSheetContent(content: @Composable ColumnScope.() -> Unit) = setContent {
    Column(Modifier.verticalScroll(rememberScrollState()), content = content)
}
