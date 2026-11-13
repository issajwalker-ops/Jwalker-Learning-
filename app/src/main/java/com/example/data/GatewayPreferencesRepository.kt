package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.gatewayDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gateway_preferences"
)

data class GatewayBookmark(
    val id: String,
    val title: String,
    val url: String,
    val addedAtMillis: Long
)

data class GatewaySettings(
    val immersiveMode: Boolean = false,
    val resumeLastPage: Boolean = false,
    val lastVisitedUrl: String = GatewayPreferencesRepository.PORTAL_HOME_URL,
    val bookmarks: List<GatewayBookmark> = emptyList()
)

class GatewayPreferencesRepository(private val context: Context) {

    private object Keys {
        val IMMERSIVE_MODE = booleanPreferencesKey("immersive_mode")
        val RESUME_LAST_PAGE = booleanPreferencesKey("resume_last_page")
        val LAST_VISITED_URL = stringPreferencesKey("last_visited_url")
        val BOOKMARKS_JSON = stringPreferencesKey("bookmarks_json")
    }

    val settingsFlow: Flow<GatewaySettings> = context.gatewayDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            GatewaySettings(
                immersiveMode = prefs[Keys.IMMERSIVE_MODE] ?: false,
                resumeLastPage = prefs[Keys.RESUME_LAST_PAGE] ?: false,
                lastVisitedUrl = prefs[Keys.LAST_VISITED_URL] ?: PORTAL_HOME_URL,
                bookmarks = decodeBookmarks(prefs[Keys.BOOKMARKS_JSON])
            )
        }

    suspend fun setImmersiveMode(enabled: Boolean) {
        context.gatewayDataStore.edit { prefs ->
            prefs[Keys.IMMERSIVE_MODE] = enabled
        }
    }

    suspend fun setResumeLastPage(enabled: Boolean) {
        context.gatewayDataStore.edit { prefs ->
            prefs[Keys.RESUME_LAST_PAGE] = enabled
        }
    }

    suspend fun setLastVisitedUrl(url: String) {
        if (url.isBlank() || url == "about:blank") return
        context.gatewayDataStore.edit { prefs ->
            prefs[Keys.LAST_VISITED_URL] = url
        }
    }

    suspend fun addBookmark(title: String, url: String) {
        val cleanUrl = url.trim()
        val cleanTitle = title.trim().ifEmpty { cleanUrl }
        context.gatewayDataStore.edit { prefs ->
            val current = decodeBookmarks(prefs[Keys.BOOKMARKS_JSON]).toMutableList()
            current.removeAll { it.url.equals(cleanUrl, ignoreCase = true) }
            current.add(
                0,
                GatewayBookmark(
                    id = UUID.randomUUID().toString(),
                    title = cleanTitle,
                    url = cleanUrl,
                    addedAtMillis = System.currentTimeMillis()
                )
            )
            prefs[Keys.BOOKMARKS_JSON] = encodeBookmarks(current)
        }
    }

    suspend fun removeBookmark(bookmarkId: String) {
        context.gatewayDataStore.edit { prefs ->
            val current = decodeBookmarks(prefs[Keys.BOOKMARKS_JSON])
                .filterNot { it.id == bookmarkId }
            prefs[Keys.BOOKMARKS_JSON] = encodeBookmarks(current)
        }
    }

    private fun encodeBookmarks(bookmarks: List<GatewayBookmark>): String {
        val array = JSONArray()
        bookmarks.forEach { bookmark ->
            val obj = JSONObject().apply {
                put("id", bookmark.id)
                put("title", bookmark.title)
                put("url", bookmark.url)
                put("addedAtMillis", bookmark.addedAtMillis)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun decodeBookmarks(rawJson: String?): List<GatewayBookmark> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(rawJson)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        GatewayBookmark(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            title = obj.optString("title", "Saved Page"),
                            url = obj.optString("url", PORTAL_HOME_URL),
                            addedAtMillis = obj.optLong("addedAtMillis", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val PORTAL_HOME_URL = "https://j-walker-learning-gateway.lovable.app"
        const val PORTAL_HOST = "j-walker-learning-gateway.lovable.app"
    }
}
