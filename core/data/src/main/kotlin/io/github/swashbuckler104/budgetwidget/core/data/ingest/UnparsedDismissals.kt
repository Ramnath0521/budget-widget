package io.github.swashbuckler104.budgetwidget.core.data.ingest

import io.github.swashbuckler104.budgetwidget.core.datastore.IngestCheckpointStore
import javax.inject.Inject

/**
 * Remembers which unparsed SMS the user deleted: the inbox scan re-reads a 10-minute overlap (and
 * backfill re-reads history), which would otherwise put deleted messages straight back into the
 * list. A deleted entry is remembered by its body hash, because the two readers time the same SMS
 * differently (the receiver uses the sent time, the scan the arrival time; ADR-0049). "Delete all"
 * also sets a time mark: nothing received at or before it is recorded again.
 */
interface UnparsedDismissals {
    suspend fun upTo(): Long?

    suspend fun hashes(): Set<String>

    /** [bodyHashes]: the deleted entries; [upTo]: also everything received up to then (epoch ms). */
    suspend fun dismiss(bodyHashes: Collection<String>, upTo: Long? = null)

    /** Nothing is ever dismissed: for tests and callers without settings. */
    object None : UnparsedDismissals {
        override suspend fun upTo(): Long? = null

        override suspend fun hashes(): Set<String> = emptySet()

        override suspend fun dismiss(bodyHashes: Collection<String>, upTo: Long?) = Unit
    }
}

internal class StoredUnparsedDismissals @Inject constructor(private val store: IngestCheckpointStore) :
    UnparsedDismissals {
    override suspend fun upTo(): Long? = store.unparsedDismissedUpTo()

    override suspend fun hashes(): Set<String> = store.dismissedUnparsedHashes()

    override suspend fun dismiss(bodyHashes: Collection<String>, upTo: Long?) {
        store.dismissUnparsedHashes(bodyHashes)
        upTo?.let { store.dismissUnparsedUpTo(it) }
    }
}
