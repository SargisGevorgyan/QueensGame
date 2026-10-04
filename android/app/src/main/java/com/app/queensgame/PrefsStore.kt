package com.app.queensgame

import android.content.Context
import android.content.SharedPreferences
import com.app.queensgame.core.KeyValueStore

/** SharedPreferences-backed [KeyValueStore] — the Android stand-in for UserDefaults. */
class PrefsStore(context: Context) : KeyValueStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("queens", Context.MODE_PRIVATE)

    override fun getInt(key: String): Int? = if (prefs.contains(key)) prefs.getInt(key, 0) else null
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun getBoolean(key: String): Boolean? = if (prefs.contains(key)) prefs.getBoolean(key, false) else null
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun getStringSet(key: String): Set<String>? = prefs.getStringSet(key, null)?.toSet()
    override fun putStringSet(key: String, value: Set<String>) = prefs.edit().putStringSet(key, value).apply()
}
