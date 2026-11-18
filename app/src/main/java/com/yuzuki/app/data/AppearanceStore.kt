package com.yuzuki.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 独立的 DataStore（`app_appearance`），与 token / 缓存互不影响。
 * 退出登录不清除：外观是本机偏好，不是账号数据。
 */
private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_appearance")

/**
 * App 外观的本机存储。写入后立即更新 [state]，Compose 侧订阅即可即时换色；
 * 全程不触达 `/api/settings`，服务端配色与 App 互不影响。
 */
class AppearanceStore(context: Context) {
    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(AppearancePrefs.Default)
    val state: StateFlow<AppearancePrefs> = _state
    val current: AppearancePrefs get() = _state.value

    /** 冷启动读取一次；读失败保留默认外观，不影响启动。 */
    suspend fun load(): AppearancePrefs = withContext(Dispatchers.IO) {
        val stored = runCatching { appContext.appearanceDataStore.data.first() }.getOrNull()
        val prefs = if (stored == null) {
            AppearancePrefs.Default
        } else {
            AppearancePrefs(
                navColor = stored[KEY_NAV] ?: AppearancePrefs.DefaultNav,
                actorColor = stored[KEY_ACTOR] ?: AppearancePrefs.DefaultActor,
                tagColor = stored[KEY_TAG] ?: AppearancePrefs.DefaultTag,
                panelOpacity = stored[KEY_PANEL] ?: 62,
                controlOpacity = stored[KEY_CONTROL] ?: 70,
                actorTagOpacity = stored[KEY_ACTOR_TAG] ?: 100,
                genreTagOpacity = stored[KEY_GENRE_TAG] ?: 30,
            ).sanitized()
        }
        _state.value = prefs
        prefs
    }

    /** 规范化后落盘并更新内存状态；返回实际生效的值。 */
    suspend fun save(value: AppearancePrefs): AppearancePrefs = withContext(Dispatchers.IO) {
        val prefs = value.sanitized()
        runCatching {
            appContext.appearanceDataStore.edit {
                it[KEY_NAV] = prefs.navColor
                it[KEY_ACTOR] = prefs.actorColor
                it[KEY_TAG] = prefs.tagColor
                it[KEY_PANEL] = prefs.panelOpacity
                it[KEY_CONTROL] = prefs.controlOpacity
                it[KEY_ACTOR_TAG] = prefs.actorTagOpacity
                it[KEY_GENRE_TAG] = prefs.genreTagOpacity
            }
        }
        // 落盘失败也先应用，界面不至于「点了没反应」；下次进入回到默认值。
        _state.value = prefs
        prefs
    }

    suspend fun reset(): AppearancePrefs = save(AppearancePrefs.Default)

    private companion object {
        val KEY_NAV = stringPreferencesKey("nav_color")
        val KEY_ACTOR = stringPreferencesKey("actor_color")
        val KEY_TAG = stringPreferencesKey("tag_color")
        val KEY_PANEL = intPreferencesKey("panel_opacity")
        val KEY_CONTROL = intPreferencesKey("control_opacity")
        val KEY_ACTOR_TAG = intPreferencesKey("actor_tag_opacity")
        val KEY_GENRE_TAG = intPreferencesKey("genre_tag_opacity")
    }
}
