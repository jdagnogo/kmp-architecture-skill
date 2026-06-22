---
name: kmp-architecture
description: >-
  Scaffold a Kotlin Multiplatform + Compose Multiplatform app with a proven,
  production-tested architecture: shared UI, MVVM + Delegate composition, a
  Repository/DataSource data layer, and a Backend-for-Frontend (BFF) boundary.
  Use this when starting a new KMP/CMP feature or app, wiring ViewModels and
  Delegates, structuring modules (commonMain/androidMain/iosMain), setting up
  Koin DI, or adding type-safe navigation. The patterns here come from a real
  shipped app (Android + iOS from one Compose codebase).
---

# KMP Architecture Skill

A battle-tested architecture for **Kotlin Multiplatform (KMP)** apps that share
their **UI** via **Compose Multiplatform (CMP)** — one Compose codebase running on
Android and iOS — with a clean data layer and a Backend-for-Frontend boundary.

> **KMP vs CMP:** KMP shares *business logic* (each platform keeps its native UI —
> SwiftUI on iOS, Compose on Android, so you build every screen twice). CMP shares
> the *UI too* — you write the screens once in Compose and they run on both. This
> skill assumes CMP: the substance is shared UI, and "KMP" is the umbrella keyword.

## When to apply

Use this skill when you are:

- Starting a new KMP/CMP app or feature and want a structure that scales.
- Writing a `ViewModel` and unsure how to split logic out of it.
- Wiring state flows, one-time UI events, or coroutine scopes.
- Structuring `commonMain` / `androidMain` / `iosMain` source sets.
- Setting up Koin DI for ViewModels, delegates, repositories.
- Adding type-safe navigation between screens.
- Deciding what belongs on the client vs behind the BFF.

## The core idea

```
ViewModel → Delegate → UseCase → Repository → DataSource → BFF / Server
  (glue)    (state +   (one        (domain     (live +      (all writes,
            side       business     contract    one-shot     validation,
            effects)   operation)   + cache)    reads)       secrets)
```

Each layer has one job and never reaches past its neighbor:

- **ViewModel** — pure glue. Owns no state of its own. Composes one or more
  **Delegates** with Kotlin `by` delegation and wires their lifecycle to
  `viewModelScope`. Never calls a UseCase or Repository directly.
- **Delegate** — owns the screen-slice's `StateFlow<State>` and `Flow<SideEffect>`,
  and translates UI intent (`onEvent`) into calls on **UseCases**. It orchestrates
  *UI* state; it does not contain the business rules themselves. Receives its
  `CoroutineScope` via `initFooDelegate(scope)` — never creates its own.
- **UseCase** — one business operation, named after what it does (`SaveFooUseCase`,
  `GetFeedUseCase`). Stateless, reusable across delegates, composes one or more
  repositories. This is where business logic lives — so it's unit-testable in
  isolation and not duplicated across screens. (Trivial pass-through reads may skip
  it and let the delegate call the repository directly — see below.)
- **Repository** — the domain contract (interface in `domain/`, impl in `data/`).
  Exposes a private `MutableStateFlow` as an immutable `StateFlow`. Reads directly,
  routes **all mutations through the BFF**, wraps them in `Result`.
- **DataSource** — owns the live data connection. Real-time reads use a single
  cancellable listener (one source of truth, deduped by id); point reads use
  one-shot `get()`.
- **BFF / Server** — every write, every validation, every security-sensitive
  calculation. The client is "dumb": it renders state and forwards intent.

> **Why a UseCase layer?** A common critique of the Delegate pattern is that the
> delegate becomes a junk drawer of business logic. The UseCase layer answers that:
> the delegate handles *state and effects*, the UseCase handles *what the operation
> actually does*. A UseCase reused by three delegates is written and tested once.
> **Pragmatic exception:** for a pure pass-through (a delegate that just reads a
> `StateFlow` off a repository), a dedicated UseCase is ceremony — let the delegate
> use the repository directly. Add the UseCase when there's a real operation:
> orchestration, validation, combining sources, mapping.

```mermaid
flowchart TB
    subgraph CLIENT["📱 &nbsp;CLIENT · commonMain — shared Compose UI"]
        direction TB
        UI["🎨 &nbsp;Compose UI<br/><i>Page / Screen</i>"]
        VM["🧩 &nbsp;ViewModel<br/><i>glue · by-delegation</i>"]
        D["⚙️ &nbsp;Delegate<br/><i>StateFlow + SideEffect</i>"]
        UC["🎯 &nbsp;UseCase<br/><i>one business operation</i>"]
        R["📦 &nbsp;Repository<br/><i>domain contract + cache</i>"]
        DS["📡 &nbsp;DataSource<br/><i>live + one-shot reads</i>"]
    end
    subgraph SERVER["☁️ &nbsp;SERVER"]
        BFF["🔒 &nbsp;BFF<br/><i>writes · validation · secrets</i>"]
    end

    UI  -- "onEvent" --> VM
    VM  -- "init(scope)" --> D
    D   --> UC
    UC  --> R
    R   --> DS
    DS  --> BFF
    D  -. "StateFlow / SideEffect" .-> UI

    classDef ui   fill:#EDE9FE,stroke:#7C3AED,stroke-width:2px,color:#2E1065;
    classDef glue fill:#DBEAFE,stroke:#2563EB,stroke-width:2px,color:#0C2A66;
    classDef biz  fill:#D1FAE5,stroke:#059669,stroke-width:2px,color:#053D2B;
    classDef data fill:#FEF3C7,stroke:#D97706,stroke-width:2px,color:#5A3206;
    classDef srv  fill:#FFE4E6,stroke:#E11D48,stroke-width:2px,color:#5C0A1E;

    class UI ui;
    class VM,D glue;
    class UC,R biz;
    class DS data;
    class BFF srv;

    style CLIENT fill:#F8FAFC,stroke:#CBD5E1,stroke-width:1px,color:#334155;
    style SERVER fill:#FFF1F2,stroke:#FDA4AF,stroke-width:1px,color:#9F1239;
```

## The rules that matter

1. **Never skip a layer.** A ViewModel that calls a Repository, or a Composable
   that calls a DataSource, breaks the contract.
2. **The ViewModel owns the only real scope.** Delegates borrow `viewModelScope`
   through their `init` function. A delegate that creates its own
   `CoroutineScope` leaks.
3. **State exposure is always private-mutable → public-immutable.** Private
   `MutableStateFlow` → public `StateFlow` via `.asStateFlow()`. One-time events
   via `Channel` → `Flow` via `.receiveAsFlow()`.
4. **All writes go through the BFF.** The client never mutates the database
   directly. Mutations are server functions returning `Result<T>`. The backend
   SDK type never appears in `domain/`.
5. **One live listener per source of truth.** Continuous data is owned by a single
   DataSource listener, deduped by id. Everything else is a one-shot read.
6. **`expect/actual` is for platform-bound concerns only** — platform info,
   permissions, pickers, social auth, media, share, locale, DI platform module.
   Business logic and UI stay in `commonMain`.

## How the pieces fit — walkthrough

See `references/` for the deep dives and `examples/` for copy-paste skeletons:

- `references/mvvm-delegate-bff.md` — the full ViewModel → Delegate → UseCase →
  Repository → DataSource → BFF flow, with the scope-ownership and state-exposure
  invariants and where business logic belongs.
- `references/module-structure.md` — `commonMain` / `androidMain` / `iosMain`
  layout, per-feature `domain` / `data` / `ui` layering, `expect/actual`.
- `references/koin-di.md` — module split, `singleOf(::Impl).bind(...)`, when to use
  `single` vs `factory` vs `viewModelOf`, qualifiers, platform module.
- `references/type-safe-navigation.md` — `@Serializable` routes, generic
  `composable<Route>`, typed args via `toRoute<T>()`.
- `references/data-flow-rules.md` — the hard-won rules about live listeners,
  one-shot reads, and why every write goes through the BFF.

- `examples/DelegateTemplate.kt` — interface + impl skeleton (calls a UseCase).
- `examples/ViewModelTemplate.kt` — single- and multi-delegate composition.
- `examples/UseCaseTemplate.kt` — one business operation, interface + impl.
- `examples/RepositoryTemplate.kt` — domain interface + data impl.
- `examples/DataSourceTemplate.kt` — live listener + one-shot read.

> The examples are **generic skeletons** (`Foo`/`Bar`) — the *shape* of each layer,
> with no business logic. Copy one, rename it, fill in the body.
