package com.yuzuki.app.data

/**
 * App 本机交互偏好（手势时长等），只存本机 DataStore，不同步服务端。
 *
 * 纯 Kotlin：范围校验与毫秒换算可直接被 JVM 单测断言。
 */
data class InteractionPrefs(
    /** 右半屏下拉「按住不动退出随机推荐」的时长（秒）。 */
    val exitRandomHoldSeconds: Int = DefaultHoldSeconds,
) {
    fun sanitized(): InteractionPrefs = copy(
        exitRandomHoldSeconds = exitRandomHoldSeconds.coerceIn(MinHoldSeconds, MaxHoldSeconds),
    )

    companion object {
        const val MinHoldSeconds = 1
        const val MaxHoldSeconds = 10
        const val DefaultHoldSeconds = 3

        val Default = InteractionPrefs()

        /** 秒 → 毫秒（夹到合法范围），供手势计时直接使用。 */
        fun holdMillis(seconds: Int): Long =
            seconds.coerceIn(MinHoldSeconds, MaxHoldSeconds) * 1000L
    }
}
