/*
 * UseCaseTemplate — the shape of a UseCase.
 *
 * A UseCase is ONE business operation, named after what it does. It is stateless,
 * reusable across delegates, and composes one or more repositories. This is where
 * business logic lives — so it's unit-testable in isolation and never duplicated
 * across screens.
 *
 * Convention: a single public operator fun invoke(...) so call sites read like a
 * function call: saveFoo(input).
 *
 * Pragmatic exception: a pure pass-through read (delegate just exposes a
 * repository StateFlow) does NOT need a UseCase — that's ceremony. Add a UseCase
 * when there's a real operation: orchestration, validation, combining sources,
 * mapping domain -> UI.
 *
 * Generic skeleton only. No real business logic.
 */

// --- domain/ : the contract -------------------------------------------------

interface SaveFooUseCase {
    suspend operator fun invoke(input: FooInput): Result<Foo>
}

// --- domain/ (or data/) : the implementation --------------------------------

class SaveFooUseCaseImpl(
    private val fooRepository: FooRepository,     // composes one or more repos
    private val barRepository: BarRepository,
) : SaveFooUseCase {

    override suspend fun invoke(input: FooInput): Result<Foo> {
        // 1. validate / apply business rules that are NOT UI concerns
        if (input.label.isBlank()) {
            return Result.failure(IllegalArgumentException("label required"))
        }

        // 2. orchestrate across repositories if needed
        val barId = barRepository.getBarId()
            ?: return Result.failure(IllegalStateException("no bar"))

        // 3. delegate the actual persistence to the repository (which routes the
        //    write through the BFF and returns a Result)
        return fooRepository.saveFoo(input.copy(barId = barId))
    }
}

// --- Referenced types (see the other templates for full shapes) -------------

data class Foo(val id: String, val label: String)
data class FooInput(val label: String, val barId: String = "")

interface FooRepository {
    suspend fun saveFoo(input: FooInput): Result<Foo>
}
interface BarRepository {
    suspend fun getBarId(): String?
}
