# Data-flow rules

These are the rules that keep a real-time, multiplatform data layer fast and
stable. They look conservative until the first time you violate one and the whole
app's data connection hangs — then they look obvious. Each rule below is paid for.

## 1. One live listener per source of truth

Continuous data is owned by a **single DataSource** that holds one cancellable
listener job, deduped by id. Everything reads from its `StateFlow`. You never spin
up a second listener on the same data.

```kotlin
interface FooDataSource {
    val foo: StateFlow<Foo?>
    fun listenToFoo(fooId: String)
    suspend fun getChild(fooId: String, childId: String): Child?   // one-shot
    fun stopListening()
    fun updateOptimistically(transform: (Foo) -> Foo)
}

class FooDataSourceImpl(private val server: ServerClient) : FooDataSource {
    private val _foo = MutableStateFlow<Foo?>(null)
    override val foo: StateFlow<Foo?> = _foo
    private var listenerJob: Job? = null
    private var currentId: String? = null

    override fun listenToFoo(fooId: String) {
        if (fooId.isBlank()) { _foo.value = null; return }
        if (currentId == fooId && listenerJob?.isActive == true) return  // dedupe
        listenerJob?.cancel()
        currentId = fooId
        listenerJob = CoroutineScope(Dispatchers.Default).launch {
            server.collection("foos").document(fooId).snapshots().collect { snap ->
                _foo.value = if (snap.exists)
                    runCatching { snap.data<Foo>() }.getOrNull() else null
            }
        }
    }

    override suspend fun getChild(fooId: String, childId: String): Child? =
        server.collection("foos").document(fooId)
            .collection("children").document(childId).get()
            .takeIf { it.exists }?.data()

    override fun stopListening() { listenerJob?.cancel(); _foo.value = null }
}
```

The DataSource is a **Koin `single`** so there is exactly one listener app-wide.

## 2. Live listeners for the root; one-shot reads for everything else

- The **primary document** your app is built around (the user, the workspace, the
  session — whatever everything hangs off) gets a real-time listener, because it's
  the single source of truth the whole UI reacts to.
- **Subcollections and secondary data** are read with one-shot `get()`. Don't
  attach a fresh real-time listener to every list and detail screen.

Why: live listeners are not free, and on some mobile database SDKs, **a new
listener opened right after a write can stall** instead of delivering. Defaulting to
one-shot reads sidesteps a whole class of "why is my screen frozen" bugs.

## 3. All writes go through the BFF — never write from the client

The client **never** mutates the database directly. No `set()`, no `update()`, no
array-union from the app. Every mutation is a call to a server function that
performs the write, returning `Result<T>` to the client.

```kotlin
// Repository impl — the ONLY way the client changes data
override suspend fun saveFoo(input: FooInput): Result<Foo> = runCatching {
    server.callFunction("saveFoo").invoke(input)   // BFF does the actual write
}
```

This isn't only about validation and security (though those are reasons — business
rules, calculations, and anything secret belong on the server). It's also a
**stability** rule:

> On some mobile database SDKs, a **direct client-side write to a document that has
> an active real-time listener can wedge the SDK's underlying connection**, which
> then blocks *all subsequent reads and writes*. The screen looks frozen and nothing
> recovers until restart.

Routing every write through the BFF means the client side of the connection only
ever *reads*. The write happens server-side, and the change comes back down through
the existing listener. No client write + listener collision, no wedge.

**The one exception:** genuine one-time setup before any listener exists (e.g.
creating the root document during onboarding). After that, all writes go through the
BFF.

## 4. Optimistic local updates instead of client writes

Because the client can't write, "instant" UI comes from patching local state ahead
of the round-trip, then letting the listener reconcile:

```kotlin
override fun updateOptimistically(transform: (Foo) -> Foo) {
    _foo.value = _foo.value?.let(transform)
}
```

Call `updateOptimistically { … }` for the snappy UI change, fire the BFF mutation,
and when the server write lands the listener overwrites local state with the
authoritative version. If the mutation fails, the next listener emission corrects
the UI.

## Summary

| Concern              | Rule                                                          |
|----------------------|--------------------------------------------------------------|
| Root data            | One live listener, owned by a `single` DataSource, deduped   |
| Lists / detail / subcollections | One-shot `get()`                                  |
| Writes               | Always via the BFF; client never writes directly             |
| Instant UI           | `updateOptimistically` locally, reconcile via the listener   |
| Setup exception      | One-time root-document creation before any listener exists    |
