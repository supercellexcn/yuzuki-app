package com.yuzuki.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 独立 DataStore（`app_library_filter`），与外观 / 手势 / token / 缓存分开。 */
private val Context.filterDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_library_filter")

/**
 * 库页筛选条件的本机存储：整份条件序列化成一行 JSON 落盘。
 *
 * 退出登录不清除（设备偏好，与账号无关）；[saveAsync] 串行写盘，UI 改条件时直接调用即可，
 * 不阻塞主线程。
 */
class FilterStore(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun load(): LibraryFilterPrefs = withContext(Dispatchers.IO) {
        val stored = runCatching { appContext.filterDataStore.data.first()[KEY_FILTER] }.getOrNull()
        LibraryFilterPrefs.decode(stored)
    }

    suspend fun save(prefs: LibraryFilterPrefs) = withContext(Dispatchers.IO) {
        val value = prefs.sanitized().encode()
        runCatching { appContext.filterDataStore.edit { it[KEY_FILTER] = value } }
        Unit
    }

    fun saveAsync(prefs: LibraryFilterPrefs) {
        scope.launch { save(prefs) }
    }

    private companion object {
        val KEY_FILTER = stringPreferencesKey("filter_json")
    }
}
