# MVVM + Delegate + BFF

The data flows top to bottom; state flows back up. Each layer talks only to its
direct neighbor.

```
ViewModel  →  Delegate  →  Repository  →  DataSource  →  BFF / Server
```

## ViewModel — glue only

The ViewModel owns no state. It **composes delegates** with Kotlin `by` delegation,
re-exporting their `state` / `onEvent` / `sideEffects` to the UI, and wires their
lifecycle by calling each `init…Delegate(viewModelScope)` in `init`.

```kotlin
class FooViewModel(
    private val delegate: FooDelegate,
) : ViewModel(), FooDelegate by delegate {   // re-exports the delegate's surface
    init {
        delegate.initFooDelegate(scope = viewModelScope)
    }
}
```

The UI receives the ViewModel **typed as the delegate interface** — e.g.
`FooHomePage(viewModel: FooDelegate)`. The Composable never knows there's a
ViewModel; it only sees `state`, `onEvent`, `sideEffects`.

When a screen needs several independent concerns, compose multiple delegates:

```kotlin
class BarViewModel(
    private val fooDelegate: FooDelegate,
    private val bazDelegate: BazDelegate,
    private val barRepository: BarRepository,
) : ViewModel(),
    FooDelegate by fooDelegate,
    BazDelegate by bazDelegate {
    init {
        viewModelScope.launch {
            initBazDelegate(this)
            val barId = barRepository.getBarId()
            if (barId != null) initFooDelegate(this, barId)
        }
    }
}
```

> **Why a Repository call appears here:** resolving an *id* needed to start a
> delegate is lifecycle wiring, not business logic. The screen's actual logic still
> lives in the delegates.

## Delegate — business logic + state

A delegate is a self-contained unit of logic for a slice of a screen. It defines:

- `val state: StateFlow<FooState>` — the continuous UI state.
- `val sideEffects: Flow<FooSideEffect>` — one-time events (navigation, toasts).
- `fun onEvent(event: FooEvent)` — the single entry point for UI intent.
- `fun initFooDelegate(scope: CoroutineScope, …)` — receives the parent scope.

```kotlin
interface FooDelegate {
    val state: StateFlow<FooState>
    val sideEffects: Flow<FooSideEffect>

    fun onEvent(event: FooEvent)
    fun sendSideEffect(effect: FooSideEffect)

    fun initFooDelegate(scope: CoroutineScope, barId: String) {}
}
```

The impl holds private mutable state and a side-effect `Channel`, and captures the
scope in `init` — **never** in the constructor:

```kotlin
class FooDelegateImpl(
    private val repository: FooRepository,
) : FooDelegate {
    private val _state = MutableStateFlow<FooState>(FooState.Loading)
    override val state: StateFlow<FooState> = _state.asStateFlow()

    private val _sideEffects = Channel<FooSideEffect>()
    override val sideEffects: Flow<FooSideEffect> = _sideEffects.receiveAsFlow()

    private lateinit var scope: CoroutineScope   // set in init, NOT the constructor

    override fun initFooDelegate(scope: CoroutineScope, barId: String) {
        this.scope = scope
        // start collecting / initial load on the borrowed scope
    }

    override fun sendSideEffect(effect: FooSideEffect) {
        scope.launch { _sideEffects.send(effect) }
    }

    override fun onEvent(event: FooEvent) {
        // when (event) { ... } -> mutate _state, call repository, sendSideEffect(...)
    }
}
```

**Invariant:** the `CoroutineScope` is `lateinit` and borrowed from the ViewModel.
A delegate never builds its own scope — that would outlive the screen and leak.

## Repository — domain contract + caching

The interface lives in `domain/` and knows nothing about the backend SDK. The impl
lives in `data/`, exposes a private `MutableStateFlow` as immutable, reads
directly, and routes **all mutations through the BFF** wrapped in `Result`.

```kotlin
// domain/
interface FooRepository {
    val foo: StateFlow<Foo?>
    suspend fun fetchFoo()
    suspend fun getFoo(): Foo?
    suspend fun saveFoo(input: FooInput): Result<Foo>
}

// data/
class FooRepositoryImpl(
    private val server: ServerClient,
) : FooRepository {
    private val _foo = MutableStateFlow<Foo?>(null)
    override val foo: StateFlow<Foo?> = _foo.asStateFlow()

    override suspend fun fetchFoo() {
        if (foo.value != null) return                 // cache guard
        _foo.value = getFoo()
    }

    override suspend fun getFoo(): Foo? =
        server.collection("foos").document(currentId()).get().data()

    override suspend fun saveFoo(input: FooInput): Result<Foo> = runCatching {
        server.callFunction("saveFoo").invoke(input)   // mutation via the BFF
    }
}
```

## DataSource & BFF

See `data-flow-rules.md` for the DataSource listener rules and why every write goes
through the BFF.

## The invariants, in one place

- ViewModel composes delegates with `by`; owns the only real scope.
- Delegate owns `StateFlow<State>` + `Flow<SideEffect>`; borrows the scope via `init`.
- State is always private `MutableStateFlow` → public `StateFlow` (`.asStateFlow()`);
  one-time events are `Channel` → `Flow` (`.receiveAsFlow()`).
- Mutations return `Result<T>` and run through the BFF; the backend SDK never
  appears in `domain/`.
