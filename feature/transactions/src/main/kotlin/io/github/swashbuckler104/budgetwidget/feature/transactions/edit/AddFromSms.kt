package io.github.swashbuckler104.budgetwidget.feature.transactions.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.swashbuckler104.budgetwidget.core.data.WallClock
import io.github.swashbuckler104.budgetwidget.core.data.category.CategoryRepository
import io.github.swashbuckler104.budgetwidget.core.data.edit.EditResult
import io.github.swashbuckler104.budgetwidget.core.data.edit.ManualTransaction
import io.github.swashbuckler104.budgetwidget.core.data.edit.SmsPrefill
import io.github.swashbuckler104.budgetwidget.core.data.edit.TransactionEditor
import io.github.swashbuckler104.budgetwidget.core.data.repository.TransactionRepository
import io.github.swashbuckler104.budgetwidget.core.model.Category
import io.github.swashbuckler104.budgetwidget.core.model.CategoryGroup
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [prefill] null while loading, or when the SMS is gone (deleted meanwhile, or has no text). */
internal data class AddFromSmsState(
    val loaded: Boolean = false,
    val prefill: AddPrefill? = null,
    val groups: List<CategoryGroup> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
)

/**
 * "Add as transaction" on an unparsed bank SMS (ADR-0048, plan B5): the manual-add form, prefilled
 * from the SMS. Saving stores a manual transaction with the SMS as its source and takes the entry
 * off the unparsed list (as a dismissal, so a rescan doesn't bring it back).
 */
@HiltViewModel
internal class AddFromSmsViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    categories: CategoryRepository,
    private val editor: TransactionEditor,
    private val clock: WallClock,
) : ViewModel() {
    private val prefill = MutableStateFlow<AddPrefill?>(null)
    private val loaded = MutableStateFlow(false)

    val state: StateFlow<AddFromSmsState> = combine(prefill, loaded, categories.observeGroups()) { p, done, groups ->
        AddFromSmsState(
            loaded = done,
            prefill = p,
            groups = groups,
            categories = groups.flatMap { listOf(it.category) + it.children }.associateBy { it.id },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AddFromSmsState())

    fun load(unparsedId: Long) {
        if (loaded.value) return
        viewModelScope.launch {
            val sms = transactions.getUnparsed(unparsedId)
            val body = sms?.body
            prefill.value = if (sms != null && body != null) {
                val guess = SmsPrefill.from(body)
                AddPrefill(sms.id, guess.amountPaise, guess.direction, sms.receivedAt, sms.sender, body)
            } else {
                null
            }
            loaded.value = true
        }
    }

    fun nowMillis(): Long = clock.nowMillis()

    /** [onDone] gets true once the transaction is stored and the entry left the unparsed list. */
    fun add(manual: ManualTransaction, onDone: (Boolean) -> Unit) {
        val source = prefill.value ?: return
        viewModelScope.launch {
            val saved = editor.addManual(manual) is EditResult.Saved
            if (saved) transactions.deleteUnparsed(source.key)
            onDone(saved)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * The add sheet for one unparsed SMS, for the app's nav host to show over the unparsed list (the
 * settings feature doesn't depend on this one). [onDone]: true when a transaction was added,
 * false when the sheet was closed, the SMS was gone, or saving failed.
 */
@Composable
fun AddFromSmsSheet(unparsedId: Long, onDone: (added: Boolean) -> Unit) {
    val viewModel: AddFromSmsViewModel = hiltViewModel(key = "add-from-sms-$unparsedId")
    LaunchedEffect(unparsedId) { viewModel.load(unparsedId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return
    val prefill = state.prefill
    if (prefill == null) {
        val done by rememberUpdatedState(onDone)
        LaunchedEffect(Unit) { done(false) }
        return
    }
    AddTransactionSheet(
        groups = state.groups,
        categories = state.categories,
        nowMillis = remember { viewModel.nowMillis() },
        onAdd = { viewModel.add(it, onDone) },
        onDismiss = { onDone(false) },
        prefill = prefill,
    )
}
