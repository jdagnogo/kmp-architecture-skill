# Koin DI

Modules are split by layer and aggregated in `initKoin`. The platform-specific
dependencies come in through an `expect/actual` `platformModule()`.

```kotlin
expect fun platformModule(): Module          // actual per platform

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(
            provideViewModelModule,
            provideDelegateModule,
            provideUseCaseModule,
            provideRepositoryModule,
            provideApiModule,
            provideDataStoreModule,
            platformModule(),
        )
    }
}
```

## The canonical binding

```kotlin
singleOf(::FooRepositoryImpl).bind(FooRepository::class)
```

`singleOf(::Impl).bind(Interface::class)` binds an implementation to its interface
with constructor injection inferred. Use the explicit form when you need to control
construction:

```kotlin
single<BarRepository> { BarRepositoryImpl(get(), get()) }
```

## Scope choice per layer

| Layer        | Koin function                       | Why                                              |
|--------------|-------------------------------------|--------------------------------------------------|
| Repository   | `single` / `singleOf`               | Shared, holds cached `StateFlow` state           |
| UseCase      | `single` / `singleOf`               | Stateless, safe to share                         |
| Delegate     | `factory`                           | One fresh instance per ViewModel                 |
| ViewModel    | `viewModelOf` / `viewModel { }`     | Lifecycle-scoped to the screen                   |
| DataSource   | `single`                            | Owns the one live listener — must be a singleton |

```kotlin
val provideRepositoryModule = module {
    singleOf(::FooRepositoryImpl).bind(FooRepository::class)
}

val provideDelegateModule = module {
    factory { FooDelegateImpl(get(), get()) }.bind(FooDelegate::class)   // per-VM
    singleOf(::SharedDelegateImpl).bind(SharedDelegate::class)           // unless shared

    factory<VIPDelegate>(named("guestVIPDelegate")) {                    // qualified variant
        VIPDelegateImpl(get(), get(), isEditable = false)
    }
}

val provideViewModelModule = module {
    viewModelOf(::FooViewModel)                                          // no-arg ctor
    viewModel { params -> BarViewModel(id = params.get(), get(), get()) } // runtime args
}

val provideUseCaseModule = module {
    singleOf(::DoFooUseCaseImpl).bind(DoFooUseCase::class)
}
```

## Rules of thumb

- **Delegates are `factory`** — each ViewModel gets its own, so their `lateinit
  scope` and state never bleed between screens. A delegate that's genuinely shared
  across the app can be `single`, but that's the exception.
- **DataSources are `single`** — there must be exactly one live listener per source
  of truth (see `data-flow-rules.md`).
- **Use `named(...)` qualifiers** for behavioral variants of the same interface
  (e.g. editable vs read-only).
- **Use `platformModule()`** (`expect/actual`) for anything platform-bound, so the
  common graph stays platform-agnostic.
- **Pass runtime args** with `viewModel { params -> ... }` and `params.get()`.
