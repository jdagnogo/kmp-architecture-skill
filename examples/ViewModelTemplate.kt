/*
 * ViewModelTemplate — the shape of a ViewModel.
 *
 * A ViewModel is GLUE. It owns no state. It composes one or more delegates with
 * Kotlin `by` delegation, re-exporting their state/onEvent/sideEffects, and
 * wires their lifecycle by calling each init...Delegate(viewModelScope) in init.
 *
 * The UI receives the ViewModel typed as the delegate interface, so the
 * Composable never knows a ViewModel exists.
 *
 * Generic skeleton only. No business logic.
 */

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

// --- Single delegate --------------------------------------------------------

class FooViewModel(
    private val delegate: FooDelegate,
) : ViewModel(), FooDelegate by delegate {          // re-exports the delegate's surface
    init {
        delegate.initFooDelegate(scope = viewModelScope, barId = "")
    }
}

// --- Multiple delegates -----------------------------------------------------

class BarViewModel(
    private val fooDelegate: FooDelegate,
    private val bazDelegate: BazDelegate,
    private val barRepository: BarRepository,
) : ViewModel(),
    FooDelegate by fooDelegate,                     // compose N delegates...
    BazDelegate by bazDelegate {                    // ...each contributes its own surface
    init {
        viewModelScope.launch {
            // Each delegate borrows this ViewModel's scope.
            initBazDelegate(this)

            // A Repository call here is lifecycle wiring (resolve an id needed to
            // start a delegate), not business logic — that stays in the delegates.
            val barId = barRepository.getBarId()
            if (barId != null) initFooDelegate(this, barId)
        }
    }
}

// --- Referenced types (see the other templates for full shapes) -------------

interface BazDelegate {
    fun initBazDelegate(scope: kotlinx.coroutines.CoroutineScope) {}
}

interface BarRepository {
    suspend fun getBarId(): String?
}
