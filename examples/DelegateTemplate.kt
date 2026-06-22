/*
 * DelegateTemplate — the shape of a Delegate (interface + impl).
 *
 * A Delegate owns the business logic and state for one slice of a screen.
 * It exposes a StateFlow<State> + a Flow<SideEffect>, takes UI intent through
 * onEvent, and BORROWS its CoroutineScope from the ViewModel via init.
 *
 * Generic skeleton only — rename Foo and fill in the bodies. No business logic.
 */

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

// --- domain/ : the contract + the UI vocabulary -----------------------------

sealed interface FooState {
    data object Loading : FooState
    data class Content(val items: List<String>) : FooState
    data class Error(val message: String) : FooState
}

sealed interface FooEvent {
    data object Refresh : FooEvent
    data class ItemClicked(val id: String) : FooEvent
}

sealed interface FooSideEffect {
    data class NavigateToDetail(val id: String) : FooSideEffect
    data class ShowMessage(val text: String) : FooSideEffect
}

interface FooDelegate {
    val state: StateFlow<FooState>
    val sideEffects: Flow<FooSideEffect>

    fun onEvent(event: FooEvent)
    fun sendSideEffect(effect: FooSideEffect)

    /**
     * MUST be called first, from the ViewModel's init, passing viewModelScope.
     * The delegate launches all of its coroutines on this borrowed scope —
     * it never creates its own.
     */
    fun initFooDelegate(scope: CoroutineScope, barId: String) {}
}

// --- data/ (or ui/viewmodel/) : the implementation --------------------------

class FooDelegateImpl(
    private val repository: FooRepository,          // deps injected via Koin
) : FooDelegate {

    private val _state = MutableStateFlow<FooState>(FooState.Loading)
    override val state: StateFlow<FooState> = _state.asStateFlow()

    private val _sideEffects = Channel<FooSideEffect>()
    override val sideEffects: Flow<FooSideEffect> = _sideEffects.receiveAsFlow()

    private lateinit var scope: CoroutineScope      // set in init, NOT the constructor
    private lateinit var barId: String

    override fun initFooDelegate(scope: CoroutineScope, barId: String) {
        this.scope = scope
        this.barId = barId
        load()
    }

    override fun onEvent(event: FooEvent) {
        when (event) {
            FooEvent.Refresh -> load()
            is FooEvent.ItemClicked ->
                sendSideEffect(FooSideEffect.NavigateToDetail(event.id))
        }
    }

    override fun sendSideEffect(effect: FooSideEffect) {
        scope.launch { _sideEffects.send(effect) }
    }

    private fun load() {
        scope.launch {
            _state.value = FooState.Loading
            runCatching { repository.getFoo(barId) }
                .onSuccess { _state.value = FooState.Content(it) }
                .onFailure { _state.value = FooState.Error(it.message.orEmpty()) }
        }
    }
}

// Referenced by the template; see RepositoryTemplate.kt for the real shape.
interface FooRepository {
    suspend fun getFoo(barId: String): List<String>
}
