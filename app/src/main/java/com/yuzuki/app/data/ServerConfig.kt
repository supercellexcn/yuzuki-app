package com.yuzuki.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.serverStore: DataStore<Preferences> by preferencesDataStore(name = "server_config")

/** 服务地址持久化；校验策略统一委托给 [HostPolicy]（方案 §7）。 */
class ServerConfig(context: Context) {
    private val appContext = context.applicationContext
    private val store: DataStore<Preferences> get() = appContext.serverStore

    companion object {
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
    }

    val baseUrl: Flow<String?> = store.data.map { it[KEY_BASE_URL] }

    suspend fun currentBaseUrl(): String? = baseUrl.first()

    suspend fun save(raw: String): Result<String> {
        val normalized = HostPolicy.normalize(raw).getOrElse { return Result.failure(it) }
        store.edit { it[KEY_BASE_URL] = normalized }
        return Result.success(normalized)
    }

    suspend fun clear() {
        store.edit { it.remove(KEY_BASE_URL) }
    }

    fun normalize(raw: String): Result<String> = HostPolicy.normalize(raw)

    fun isAllowedUrl(url: String, configuredBaseUrl: String): Boolean =
        HostPolicy.isAllowedUrl(url, configuredBaseUrl)
}
