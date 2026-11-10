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

/** 轻量导航：不引入 Navigation 依赖，返回栈逻辑放在 NavStack（可单测），这里只负责通知 Compose。 */
class AppNav {
    private val stack = NavStack<Route>(Route.Library)

    var current by mutableStateOf<Route>(Route.Library)
        private set

    fun go(route: Route) {
        if (stack.go(route)) current = stack.current
    }

    fun replace(route: Route) {
        stack.replace(route)
        current = stack.current
    }

    fun back(): Boolean {
        val moved = stack.back()
        if (moved) current = stack.current
        return moved
    }

    /** 详情页返回 / 系统返回键统一入口：栈空也会回媒体库。 */
    fun backOrHome(): Boolean {
        val moved = stack.backOr(Route.Library)
        if (moved) current = stack.current
        return moved
    }

    val canBack: Boolean get() = stack.canBack
}

@Composable
fun rememberAppNav(): AppNav = remember { AppNav() }
