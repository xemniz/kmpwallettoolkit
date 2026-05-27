package xyz.wallet.toolkit.sample.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavBackStack
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Navigator(
    private val backStack: NavBackStack<Route>,
) {

    val current: Route
        get() = backStack.last()

    fun push(route: Route) {
        backStack.add(route)
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeLastOrNull()
        return true
    }

    fun replace(route: Route) {
        backStack.clear()
        backStack.add(route)
    }
}

@Composable
fun rememberRouteBackStack(vararg elements: Route): NavBackStack<Route> =
    rememberSaveable(saver = RouteBackStackSaver) {
        NavBackStack(*elements)
    }

private val RouteBackStackSaver: Saver<NavBackStack<Route>, String> = Saver(
    save = { backStack -> Json.encodeToString(backStack.toList()) },
    restore = { raw ->
        val restored = runCatching { Json.decodeFromString<List<Route>>(raw) }
            .getOrElse { listOf(Route.Welcome) }
        NavBackStack(*restored.toTypedArray())
    },
)
