# Type-safe navigation

Routes are `@Serializable` types, not strings. Destinations are declared with the
generic `composable<Route>`, and typed arguments are read back with
`backStackEntry.toRoute<T>()`. No string parsing, no manual argument keys.

## Declaring routes

```kotlin
// FooRoutes.kt
@Serializable data object FooGraph
@Serializable data object FooHome                        // no args
@Serializable data class  FooDetail(val id: String = "") // typed args
```

An `object` route carries no arguments; a `data class` route carries typed,
serializable arguments with defaults.

## Building the graph

```kotlin
internal fun NavGraphBuilder.fooGraph(navController: NavController) {
    navigation<FooGraph>(startDestination = FooHome) {

        composable<FooHome> {
            val viewModel = koinViewModel<FooViewModel>()
            FooHomePage(
                viewModel,
                navigateToDetail = { id -> navController.navigate(FooDetail(id)) },
            )
        }

        composable<FooDetail> { backStackEntry ->
            val args = backStackEntry.toRoute<FooDetail>()   // type-safe args
            val viewModel = koinViewModel<FooViewModel>()
            FooDetailPage(
                viewModel,
                id = args.id,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
```

## The conventions

- **Every destination is a `@Serializable` type.** `object` for no args, `data
  class` for typed args.
- **Navigation is "construct the route instance"** — `navController.navigate(FooDetail(id))`,
  not a string path.
- **Read args with `toRoute<T>()`** off the back-stack entry — fully typed, no keys.
- **Resolve ViewModels per destination** with `koinViewModel<…>()`.
- **Split large graphs** into `NavGraphBuilder` extension functions
  (`fun NavGraphBuilder.fooGraph(...)`) and nest them with `navigation<Graph>`.
- **Pass callbacks down, not the NavController** — give Pages lambdas
  (`navigateToDetail`, `onBack`) so they stay navigation-agnostic and previewable.
