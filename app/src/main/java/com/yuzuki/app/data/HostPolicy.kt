package com.yuzuki.app.data

import java.net.URI

/**
 * 运行时 host 白名单（方案 §7）：所有出站口的唯一校验入口。
 * 纯 Kotlin、无 Android 依赖，便于 JVM 单元测试直接断言。
 */
object HostPolicy {
    const val DEFAULT_PORT = 8080
    private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    /** 仅接受字面 IPv4 局域网地址；拒绝域名，避免 DNS rebinding 与公网地址。 */
    fun isLanHost(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val p = parts.map { it.toIntOrNull() ?: return false }
        if (p.any { it !in 0..255 }) return false
        val a = p[0]
        val b = p[1]
        return when {
            a == 10 -> true
            a == 192 && b == 168 -> true
            a == 172 && b in 16..31 -> true
            a == 127 && b == 0 && p[2] == 0 && p[3] == 1 -> true
            else -> false
        }
    }

    /** 规范化为 http://host:port，并强制局域网白名单。 */
    fun normalize(raw: String): Result<String> {
        val input = raw.trim()
        if (input.isEmpty()) return Result.failure(ServerConfigException.Empty)

        val withScheme = if (SCHEME_PREFIX.containsMatchIn(input)) input else "http://$input"
        val uri = runCatching { URI(withScheme) }
            .getOrElse { return Result.failure(ServerConfigException.Format) }

        if (uri.scheme?.lowercase() != "http") return Result.failure(ServerConfigException.Format)
        if (uri.userInfo != null) return Result.failure(ServerConfigException.Format)
        val host = uri.host ?: return Result.failure(ServerConfigException.Format)
        if (!isLanHost(host)) return Result.failure(ServerConfigException.NotLan)

        val path = uri.rawPath.orEmpty()
        if (path.isNotBlank() && path != "/") return Result.failure(ServerConfigException.Format)
        if (uri.rawQuery != null || uri.rawFragment != null) {
            return Result.failure(ServerConfigException.Format)
        }
        val port = if (uri.port > 0) uri.port else DEFAULT_PORT
        return Result.success("http://$host:$port")
    }

    /** 相对 URL 天然同源；绝对 URL 必须与配置地址同 scheme、同 host、同端口。 */
    fun isAllowedUrl(url: String, configuredBaseUrl: String): Boolean {
        if (configuredBaseUrl.isBlank()) return false
        // 无 scheme 且非协议相对（//host），视为当前页面的相对路径
        if (!SCHEME_PREFIX.containsMatchIn(url) && !url.startsWith("//")) return true
        return runCatching {
            val base = URI(configuredBaseUrl)
            val target = URI(url)
            if (target.scheme?.lowercase() != "http") return@runCatching false
            if (target.userInfo != null) return@runCatching false
            val baseHost = base.host ?: return@runCatching false
            val targetHost = target.host ?: return@runCatching false
            if (!targetHost.equals(baseHost, ignoreCase = true)) return@runCatching false
            val basePort = if (base.port > 0) base.port else 80
            val targetPort = if (target.port > 0) target.port else 80
            basePort == targetPort
        }.getOrDefault(false)
    }
}

sealed class ServerConfigException(message: String) : IllegalArgumentException(message) {
    data object Empty : ServerConfigException("地址不能为空")
    data object Format : ServerConfigException("地址格式不正确")
    data object NotLan : ServerConfigException("仅允许局域网 IP")
}
