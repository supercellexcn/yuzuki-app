package com.yuzuki.app.playback

/** 非法数值统一归零，避免把 NaN / Infinity 写进事件 JSON。 */
private fun jsonNumber(v: Double): Double = if (v.isFinite()) v else 0.0

private fun jsonEscape(s: String): String = buildString {
    s.forEach { c ->
        when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u").append(c.code.toString(16).padStart(4, '0'))
            else append(c)
        }
    }
}

/**
 * 原生播放器的最新状态快照（方案 §6.2）。WebView 重建后由 getSnapshot() 回放一次。
 * 纯 Kotlin、不依赖 Android/JSON 库，便于 JVM 单元测试。
 */
data class NativeAudioState(
    val playing: Boolean = false,
    val paused: Boolean = true,
    val currentTime: Double = 0.0,
    val duration: Double = 0.0,
    val volume: Float = 1f,
    val loop: Boolean = false,
    val sourceUrl: String = "",
) {
    fun toEventJson(): String = buildString {
        append("{\"type\":\"state\"")
        append(",\"playing\":").append(playing)
        append(",\"paused\":").append(paused)
        append(",\"currentTime\":").append(jsonNumber(currentTime))
        append(",\"duration\":").append(jsonNumber(duration))
        append(",\"volume\":").append(jsonNumber(volume.toDouble()))
        append(",\"loop\":").append(loop)
        append("}")
    }
}

/** Native → JS 事件 JSON（§6.4）。手写拼接，避免依赖 android.jar 的 org.json stub。 */
object NativeEvents {
    fun simple(type: String): String = "{\"type\":\"${jsonEscape(type)}\"}"

    fun timeupdate(currentTime: Double, duration: Double): String = buildString {
        append("{\"type\":\"timeupdate\"")
        append(",\"currentTime\":").append(jsonNumber(currentTime))
        append(",\"duration\":").append(jsonNumber(duration))
        append("}")
    }

    fun durationchange(duration: Double): String =
        "{\"type\":\"durationchange\",\"duration\":${jsonNumber(duration)}}"

    fun error(message: String): String =
        "{\"type\":\"error\",\"message\":\"${jsonEscape(message)}\"}"
}
