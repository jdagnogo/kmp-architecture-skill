/*
 * DataSourceTemplate — the shape of a DataSource.
 *
 * A DataSource owns the LIVE data connection for one source of truth: a single
 * cancellable listener job, deduped by id (Koin `single`, so one listener
 * app-wide). Continuous reads use the listener; point reads use one-shot get().
 *
 * See references/data-flow-rules.md for WHY: one listener per source of truth,
 * one-shot reads for everything else, and all writes through the BFF.
 *
 * Generic skeleton only. No business logic, no backend SDK names.
 */

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

interface FooDataSource {
    val foo: StateFlow<Foo?>
    fun listenToFoo(fooId: String)                              // real-time
    suspend fun getChild(fooId: String, childId: String): Child? // one-shot
    fun stopListening()
    fun updateOptimistically(transform: (Foo) -> Foo)
}

class FooDataSourceImpl(
    private val server: ServerClient,
) : FooDataSource {

    private val _foo = MutableStateFlow<Foo?>(null)
    override val foo: StateFlow<Foo?> = _foo

    private var listenerJob: Job? = null
    private var currentId: String? = null

    override fun listenToFoo(fooId: String) {
        if (fooId.isBlank()) { _foo.value = null; return }
        // Dedupe: don't reopen a listener that's already live for this id.
        if (currentId == fooId && listenerJob?.isActive == true) return
        listenerJob?.cancel()
        currentId = fooId
        listenerJob = CoroutineScope(Dispatchers.Default).launch {
            server.collection("foos").document(fooId).snapshots().collect { snap ->
                _foo.value = if (snap.exists)
                    runCatching { snap.data() }.getOrNull() else null
            }
        }
    }

    // Subcollections / secondary data: one-shot read, NOT a new listener.
    override suspend fun getChild(fooId: String, childId: String): Child? =
        server.collection("foos").document(fooId)
            .collection("children").document(childId).get()

    override fun stopListening() {
        listenerJob?.cancel()
        _foo.value = null
    }

    // Instant UI ahead of the BFF round-trip; the listener reconciles afterward.
    override fun updateOptimistically(transform: (Foo) -> Foo) {
        _foo.value = _foo.value?.let(transform)
    }
}

// --- Illustrative backend-agnostic surface ----------------------------------

data class Foo(val id: String)
data class Child(val id: String)

interface ServerClient { fun collection(name: String): Collection }
interface Collection { fun document(id: String): Document }
interface Document {
    fun collection(name: String): Collection
    fun snapshots(): kotlinx.coroutines.flow.Flow<Snapshot>
    suspend fun <T> get(): T?
}
interface Snapshot {
    val exists: Boolean
    fun <T> data(): T
}
