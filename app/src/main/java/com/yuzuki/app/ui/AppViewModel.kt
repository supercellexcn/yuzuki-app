package com.yuzuki.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuzuki.app.data.ApiException
import com.yuzuki.app.data.LoginResult
import com.yuzuki.app.data.ServerConfig
import com.yuzuki.app.data.TokenStore
import com.yuzuki.app.data.YuzukiApi
import kotlinx.coroutines.launch

/** 启动链路：health → me 校验 token → WebView；异常分别落到设置 / 登录 / 不可达（方案 §4）。 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    val serverConfig = ServerConfig(application)
    private val tokenStore = TokenStore(application)
    private val api = YuzukiApi(serverConfig)

    sealed interface State {
        data object Loading : State
        data object Setup : State
        data object Login : State
        data class Unreachable(val baseUrl: String) : State
        data class Web(val baseUrl: String, val token: String) : State
        data class Fatal(val message: String) : State
    }

    var state by mutableStateOf<State>(State.Loading)
        private set

    init {
        bootstrap()
    }

    fun bootstrap() {
        viewModelScope.launch {
            state = State.Loading
            val base = serverConfig.currentBaseUrl()
            if (base == null) {
                state = State.Setup
                return@launch
            }
            checkAndEnter(base)
        }
    }

    /** 只做连通性预检，不落盘。 */
    suspend fun testConnection(raw: String): Result<String> {
        val base = serverConfig.normalize(raw).getOrElse { return Result.failure(it) }
        return api.health(base).map { base }
    }

    suspend fun saveServer(raw: String): Result<String> = serverConfig.save(raw)

    suspend fun login(username: String, password: String): Result<LoginResult> {
        val base = serverConfig.currentBaseUrl()
            ?: return Result.failure(IllegalStateException("尚未配置服务地址"))
        return api.login(base, username, password)
    }

    fun onServerConfigured(base: String) {
        viewModelScope.launch { checkAndEnter(base) }
    }

    fun onLoginSuccess(result: LoginResult) {
        viewModelScope.launch {
            val base = serverConfig.currentBaseUrl() ?: return@launch
            if (tokenStore.save(result.token, result.username).isFailure) {
                state = State.Fatal("安全存储不可用，无法保存登录态")
                return@launch
            }
            // 首次登录 must_change_password 由前端路由守卫跳 /change-password（方案 §4）
            state = State.Web(base, result.token)
        }
    }

    fun onUnreachable(base: String) {
        if (state !is State.Unreachable) state = State.Unreachable(base)
    }

    fun onChangeServer() {
        viewModelScope.launch {
            tokenStore.clear()
            serverConfig.clear()
            state = State.Setup
        }
    }

    fun onBridgeUnavailable() {
        state = State.Fatal("请升级 Android System WebView 后重试")
    }

    private suspend fun checkAndEnter(base: String) {
        if (api.health(base).isFailure) {
            state = State.Unreachable(base)
            return
        }
        val token = tokenStore.getToken()
        if (token == null) {
            state = State.Login
            return
        }
        api.me(base, token).fold(
            onSuccess = { state = State.Web(base, token) },
            onFailure = { error ->
                if (error is ApiException && error.code == 401) {
                    tokenStore.clear()
                    state = State.Login
                } else {
                    // 网络抖动不清除 token，避免误登出
                    state = State.Unreachable(base)
                }
            },
        )
    }
}
