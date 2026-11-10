package com.yuzuki.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

sealed interface Route {
    data object Library : Route
    data class Work(val rj: String) : Route
    data object Favorites : Route
    data object History : Route
    data object Settings : Route
    data object ChangePassword : Route
}

/** 轻量导航：不引入 Navigation 依赖，页面少且返回栈需要完全可控。 */
class AppNav {
    var current by mutableStateOf<Route>(Route.Library)
        private set
    private val stack = ArrayDeque<Route>()

    fun go(route: Route) {
        if (current == route) return
        stack.addLast(current)
        current = route
    }

    fun replace(route: Route) {
        stack.clear()
        current = route
    }

    fun back(): Boolean {
        val previous = stack.removeLastOrNull() ?: return false
        current = previous
        return true
    }

    val canBack: Boolean get() = stack.isNotEmpty()
}

@Composable
fun rememberAppNav(): AppNav = remember { AppNav() }
