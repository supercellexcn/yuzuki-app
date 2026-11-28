package com.yuzuki.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** 独立 DataStore（`app_tab_order`），与外观 / 手势 / 筛选 / token 分开。 */
private val Context.tabOrderDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_tab_order")

/**
 * 底部板块顺序的本机存储：一行字符串落盘，写入后立即更新 [state]，
 * `RootScreen` 订阅它即可即时重排 Tab 与 Pager。
 *
 * 退出登录不清除（设备偏好，与账号无关）。
 */
class TabOrderStore(context: Context) {
    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(TabOrderPrefs.Default)
    val state: StateFlow<TabOrderPrefs> = _state
    val current: TabOrderPrefs get() = _state.value

    suspend fun load(): TabOrderPrefs = withContext(Dispatchers.IO) {
        val stored = runCatching { appContext.tabOrderDataStore.data.first()[KEY_ORDER] }.getOrNull()
        val prefs = TabOrderPrefs.decode(stored)
        _state.value = prefs
        prefs
    }

    suspend fun save(value: TabOrderPrefs): TabOrderPrefs = withContext(Dispatchers.IO) {
        val prefs = value.sanitized()
        runCatching { appContext.tabOrderDataStore.edit { it[KEY_ORDER] = prefs.encode() } }
        _state.value = prefs
        prefs
    }

    suspend fun reset(): TabOrderPrefs = save(TabOrderPrefs.Default)

    private companion object {
        val KEY_ORDER = stringPreferencesKey("tab_order")
    }
}
