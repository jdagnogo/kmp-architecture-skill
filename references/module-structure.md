# Module structure

One `shared` KMP module, three source sets. **~95% of the code lives in
`commonMain`** — including the UI, because this is Compose Multiplatform.

```
shared/src/
├── commonMain/kotlin/com/<org>/<app>/      ← almost everything
│   ├── common/                              cross-cutting: auth, di, navigation,
│   │                                        network, analytics, ui (Compose MP)
│   ├── <feature>/                           per feature, internally layered:
│   │   ├── domain/   → interfaces (Repository, Delegate, UseCase),
│   │   │              models, State / Event / SideEffect
│   │   ├── data/     → *Impl, *DataSource, *DataStore
│   │   │              (the backend SDK lives ONLY here)
│   │   └── ui/       → Pages / Screens (Composable) + viewmodel/
│   │                  (ViewModel + Delegate impls)
│   ├── Platform.kt                          interface + `expect fun getPlatform()`
│   └── composeResources/, resources/        shared assets + strings
│
├── androidMain/kotlin/...                   `actual` impls + Android-only
│   └── platformModule.android.kt → actual fun platformModule()
│
└── iosMain/kotlin/...                       `actual` impls + iOS-only
        platformModule.ios.kt   → actual fun platformModule()
```

## Per-feature layering

Inside a feature, the three folders map onto the architecture layers:

| Folder    | Holds                                              | Knows about the backend SDK? |
|-----------|----------------------------------------------------|------------------------------|
| `domain/` | Interfaces, models, `State` / `Event` / `SideEffect` | No                         |
| `data/`   | `*Impl`, `*DataSource`, `*DataStore`               | Yes — only here              |
| `ui/`     | Compose `Page`/`Screen` + `viewmodel/` (VM + Delegate impls) | No                  |

Keeping the backend SDK confined to `data/` is what lets `domain/` stay pure and
testable, and what makes the BFF boundary real rather than aspirational.

## `expect` / `actual`

Reserve `expect/actual` for genuinely platform-bound concerns. Everything else —
business logic *and* UI — stays in `commonMain`.

```kotlin
// commonMain
interface Platform {
    val name: String
    val isIOS: Boolean
}
expect fun getPlatform(): Platform

// androidMain
actual fun getPlatform(): Platform = AndroidPlatform()

// iosMain
actual fun getPlatform(): Platform = IOSPlatform()
```

Typical `expect/actual` candidates: platform info, permissions, file/media pickers,
social auth, share sheets, locale, build environment, and the Koin
`platformModule()`. If you find yourself reaching for `expect/actual` to do
business logic, the logic belongs in `commonMain` instead.

## Naming conventions

Consistent suffixes make the layer obvious at a glance:

- `FooDelegate` / `FooDelegateImpl`
- `FooRepository` / `FooRepositoryImpl`
- `FooDataSource` / `FooDataSourceImpl`
- `FooViewModel`
- `FooGraph` / `FooRoutes`
- `FooState`, `FooEvent`, `FooSideEffect`
- File naming: `FooScreen.kt` (stateful, with ViewModel), `FooPage.kt` (stateless),
  `FooGraph.kt` (navigation).
