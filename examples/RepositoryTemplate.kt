/*
 * RepositoryTemplate — the shape of a Repository (interface in domain/, impl in data/).
 *
 * The interface knows nothing about the backend SDK. The impl exposes a private
 * MutableStateFlow as an immutable StateFlow, reads directly, and routes ALL
 * mutations through the BFF (server functions) wrapped in Result.
 *
 * Generic skeleton only. No business logic, no backend SDK names.
 */

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// --- domain/ : the contract -------------------------------------------------

data class Foo(val id: String, val label: String)
data class FooInput(val label: String)

interface FooRepository {
    val foo: StateFlow<Foo?>                 // real-time, immutable to callers
    suspend fun fetchFoo()                    // refresh into the flow (cache-guarded)
    suspend fun getFoo(): Foo?                // one-shot read
    suspend fun saveFoo(input: FooInput): Result<Foo>   // mutation -> Result
}

// --- data/ : the implementation ---------------------------------------------

class FooRepositoryImpl(
    private val server: ServerClient,         // BFF / database client — lives ONLY in data/
) : FooRepository {

    private val _foo = MutableStateFlow<Foo?>(null)
    override val foo: StateFlow<Foo?> = _foo.asStateFlow()

    override suspend fun fetchFoo() {
        if (foo.value != null) return                       // cache guard
        _foo.value = getFoo()
    }

    override suspend fun getFoo(): Foo? =
        server.collection("foos").document(currentId()).get()

    override suspend fun saveFoo(input: FooInput): Result<Foo> = runCatching {
        // Mutations NEVER write the DB from the client — they call a BFF function.
        server.callFunction("saveFoo").invoke(input)
    }

    private fun currentId(): String = "current"
}

// --- A minimal backend-agnostic client surface (illustrative) ---------------
// In a real app this is your KMP database/HTTP client; the point is that the
// domain interface above never references it.

interface ServerClient {
    fun collection(name: String): Collection
    fun callFunction(name: String): ServerFunction
}
interface Collection { fun document(id: String): Document }
interface Document {
    suspend fun get(): Foo?
}
interface ServerFunction { suspend fun invoke(input: Any): Foo }
