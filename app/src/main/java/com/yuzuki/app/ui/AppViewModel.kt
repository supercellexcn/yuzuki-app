package com.yuzuki.app.ui

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuzuki.app.SessionEvents
import com.yuzuki.app.data.ApiException
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.LibraryFilterPrefs
import com.yuzuki.app.data.LoginResult
import com.yuzuki.app.playback.PlaybackController
import com.yuzuki.app.playback.PlaybackService
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 启动链路：health → me → 主界面；首次登录强制改密由原生页面完成（方案 §5 / A5）。 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    sealed interface State {
        data object Loading : State
        data object Setup : State
        data object Login : State
        data object ChangePassword : State
        data class Unreachable(val baseUrl: String) : State
        data class Main(val baseUrl: String, val token: String) : State
        data class Fatal(val message: String) : State
    }

    var state by mutableStateOf<State>(State.Loading)
        private set

    private val config get() = AppContainer.serverConfig
    private val tokens get() = AppContainer.tokenStore
    private val api get() = AppContainer.api

    init {
        bootstrap()
        viewModelScope.launch {
            SessionEvents.logout.collect { handleLogout() }
        }
    }

    fun bootstrap() {
        viewModelScope.launch {
            state = State.Loading
            val base = config.currentBaseUrl()
            if (base == null) {
                state = State.Setup
                return@launch
            }
            checkAndEnter(base)
        }
    }

    suspend fun testConnection(raw: String): Result<String> {
        val base = config.normalize(raw).getOrElse { return Result.failure(it) }
        return api.health(base).map { base }
    }

    suspend fun saveServer(raw: String): Result<String> = config.save(raw)

    suspend fun login(username: String, password: String): Result<LoginResult> {
        val base = config.currentBaseUrl()
            ?: return Result.failure(IllegalStateException("尚未配置服务地址"))
        return api.login(base, username, password)
    }

    fun onServerConfigured(base: String) {
        viewModelScope.launch { checkAndEnter(base) }
    }

    fun onLoginSuccess(result: LoginResult) {
        viewModelScope.launch {
            val base = config.currentBaseUrl() ?: return@launch
            if (tokens.save(result.token, result.username).isFailure) {
                state = State.Fatal("安全存储不可用，无法保存登录态")
                return@launch
            }
            AppContainer.bindSession(base, result.token)
            api.token = result.token
            if (result.mustChangePassword) {
                state = State.ChangePassword
            } else {
                enterMain(base, result.token)
            }
        }
    }

    suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> {
        val result = api.changePassword(oldPassword, newPassword)
        if (result.isSuccess) {
            val base = config.currentBaseUrl() ?: return result
            val token = tokens.getToken().orEmpty()
            enterMain(base, token)
        }
        return result
    }

    fun onUnreachable(base: String) {
        if (state !is State.Unreachable) state = State.Unreachable(base)
    }

    fun onChangeServer() {
        viewModelScope.launch {
            tokens.clear()
            config.clear()
            api.token = null
            api.cachedBaseUrl = ""
            state = State.Setup
        }
    }

    fun logout() {
        viewModelScope.launch { handleLogout() }
    }

    private suspend fun enterMain(base: String, token: String) {
        AppContainer.bindSession(base, token)
        // 服务端设置只用于「每页数量 / 快进秒数 / 相关推荐开关」这类非配色项；
        // 界面配色完全走本机 DataStore（AppearanceStore），不从这里覆盖。
        withTimeoutOrNull(1500) {
            AppContainer.settings.load()
        }

        // 库页的搜索 / 筛选 / 排序：读**本机**上次的条件（DataStore `app_library_filter`），
        // 既不进 `/api/prefs`，也不会把网页端保存的条件带进来。
        // 必须在 state = Main 之前完成，首帧才直接是上次的条件，不会先按空条件拉一遍列表。
        val saved = runCatching { AppContainer.filter.load() }.getOrDefault(LibraryFilterPrefs.Default)
        LibraryState.restore(saved)
        LibraryState.persistHook = { AppContainer.filter.saveAsync(it) }

        state = State.Main(base, token)
        // 离线补报：失败不阻塞进入
        viewModelScope.launch { AppContainer.reporter.flushPendingSuspend() }
    }

    private suspend fun handleLogout() {
        tokens.clear()
        api.token = null
        PlaybackController.stop()
        PlaybackController.disconnect()
        runCatching { getApplication<Application>().stopService(Intent(getApplication(), PlaybackService::class.java)) }
        state = State.Login
    }

    private suspend fun checkAndEnter(base: String) {
        if (api.health(base).isFailure) {
            state = State.Unreachable(base)
            return
        }
        val token = tokens.getToken()
        if (token == null) {
            state = State.Login
            return
        }
        AppContainer.bindSession(base, token)
        api.me().fold(
            onSuccess = { user ->
                if (user.mustChangePassword) state = State.ChangePassword
                else enterMain(base, token)
            },
            onFailure = { error ->
                if (error is ApiException && error.code == 401) {
                    tokens.clear()
                    api.token = null
                    state = State.Login
                } else {
                    // 网络抖动不清 token；离线时仍可用缓存进入
                    enterMain(base, token)
                }
            },
        )
    }
}
