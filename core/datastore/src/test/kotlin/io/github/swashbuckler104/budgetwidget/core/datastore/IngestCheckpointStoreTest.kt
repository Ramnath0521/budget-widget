package io.github.swashbuckler104.budgetwidget.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class IngestCheckpointStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `checkpoint starts empty and only moves forward`() = runTest {
        val store = IngestCheckpointStore(dataStore(this))
        assertThat(store.get()).isNull()
        store.advanceTo(1_000)
        store.advanceTo(500)
        assertThat(store.get()).isEqualTo(1_000)
        store.advanceTo(2_000)
        assertThat(store.get()).isEqualTo(2_000)
    }

    @Test
    fun `dismissed hashes keep the newest 500, each once`() = runTest {
        val store = IngestCheckpointStore(dataStore(this))
        assertThat(store.dismissedUnparsedHashes()).isEmpty()
        store.dismissUnparsedHashes(listOf("a", "b"))
        store.dismissUnparsedHashes(listOf("a"))
        assertThat(store.dismissedUnparsedHashes()).containsExactly("a", "b")

        store.dismissUnparsedHashes((1..600).map { "h$it" })
        val kept = store.dismissedUnparsedHashes()
        assertThat(kept).hasSize(500)
        assertThat(kept).contains("h600")
        assertThat(kept).doesNotContain("h100")
        assertThat(kept).doesNotContain("a")
    }

    private fun dataStore(scope: TestScope) = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
        tmp.newFile("ingest.preferences_pb").also { it.delete() }
    }
}
