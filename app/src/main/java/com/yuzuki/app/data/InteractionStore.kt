package com.yuzuki.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** 独立 DataStore（`app_interaction`），与外观 / token / 缓存分开。 */
private val Context.interactionDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_interaction")

/**
 * 交互偏好本机存储：写入后立即更新 [state]，手势侧订阅即可生效；
 * 退出登录不清除（本机偏好，不是账号数据）。
 */
class InteractionStore(context: Context) {
    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(InteractionPrefs.Default)
    val state: StateFlow<InteractionPrefs> = _state
    val current: InteractionPrefs get() = _state.value

    suspend fun load(): InteractionPrefs = withContext(Dispatchers.IO) {
        val stored = runCatching { appContext.interactionDataStore.data.first() }.getOrNull()
        val prefs = InteractionPrefs(
            exitRandomHoldSeconds = stored?.get(KEY_HOLD_SECONDS) ?: InteractionPrefs.DefaultHoldSeconds,
        ).sanitized()
        _state.value = prefs
        prefs
    }

    suspend fun save(value: InteractionPrefs): InteractionPrefs = withContext(Dispatchers.IO) {
        val prefs = value.sanitized()
        runCatching {
            appContext.interactionDataStore.edit {
                it[KEY_HOLD_SECONDS] = prefs.exitRandomHoldSeconds
            }
        }
        _state.value = prefs
        prefs
    }

    suspend fun reset(): InteractionPrefs = save(InteractionPrefs.Default)

    private companion object {
        val KEY_HOLD_SECONDS = intPreferencesKey("exit_random_hold_seconds")
    }
}
