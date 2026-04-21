package xyz.wallet.toolkit.sample.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

class Navigator(initial: Route) {
    private val stack: MutableState<List<Route>> = mutableStateOf(listOf(initial))

    val current: Route
        get() = stack.value.last()

    fun push(route: Route) {
        stack.value = stack.value + route
    }

    fun pop(): Boolean {
        if (stack.value.size <= 1) return false
        stack.value = stack.value.dropLast(1)
        return true
    }

    fun replace(route: Route) {
        stack.value = listOf(route)
    }
}

@Composable
fun rememberNavigator(initial: Route = Route.Welcome): Navigator =
    remember { Navigator(initial) }
