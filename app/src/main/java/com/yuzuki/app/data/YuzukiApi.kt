package com.yuzuki.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LoginResult(
    val token: String,
    val username: String,
    val mustChangePassword: Boolean,
)

data class MeResult(
    val id: Long,
    val username: String,
    val mustChangePassword: Boolean,
)

class ApiException(val code: Int, message: String) : Exception(message)

/** 只调用现有 /api/ 接口，不新增任何后端接口（方案 C1/C2）。 */
class YuzukiApi(
    private val serverConfig: ServerConfig,
    private val client: OkHttpClient = defaultClient(),
) {
    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            // 外链重定向一律视为失败，避免 token 被带到站外
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    suspend fun health(baseUrl: String): Result<Unit> =
        request(baseUrl, "/api/health") { Unit }

    suspend fun login(baseUrl: String, username: String, password: String): Result<LoginResult> {
        val body: RequestBody = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()
            .toRequestBody(JSON)
        return request(baseUrl, "/api/auth/login", body = body) { text ->
            val json = JSONObject(text)
            LoginResult(
                token = json.getString("token"),
                username = json.optString("username", username),
                mustChangePassword = json.optBoolean("must_change_password", false),
            )
        }
    }

    suspend fun me(baseUrl: String, token: String): Result<MeResult> =
        request(baseUrl, "/api/auth/me", token = token) { text ->
            val json = JSONObject(text)
            MeResult(
                id = json.optLong("id", -1L),
                username = json.optString("username", ""),
                mustChangePassword = json.optBoolean("must_change_password", false),
            )
        }

    private suspend fun <T> request(
        baseUrl: String,
        path: String,
        body: RequestBody? = null,
        token: String? = null,
        parse: (String) -> T,
    ): Result<T> = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + path
        if (!serverConfig.isAllowedUrl(url, baseUrl)) {
            return@withContext Result.failure(ApiException(0, "请求地址不在白名单内"))
        }
        runCatching {
            val builder = Request.Builder().url(url)
            if (token != null) builder.header("Authorization", "Bearer $token")
            if (body != null) builder.post(body) else builder.get()
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching { JSONObject(text).optString("error") }.getOrDefault("")
                    throw ApiException(response.code, message.ifBlank { "HTTP ${response.code}" })
                }
                parse(text)
            }
        }
    }
}
