package com.taqlyn.sdk.adapters

import android.content.Context

/**
 * Local prefs for resolved-once flag and small SDK state.
 */
interface KeyValueStore {
    fun getBoolean(key: String, default: Boolean = false): Boolean

    fun putBoolean(key: String, value: Boolean)

    fun getString(key: String, default: String? = null): String?

    fun putString(key: String, value: String?)

    fun remove(key: String)
}

/** In-memory store for unit tests. */
class InMemoryKeyValueStore : KeyValueStore {
    private val booleans = mutableMapOf<String, Boolean>()
    private val strings = mutableMapOf<String, String?>()

    override fun getBoolean(key: String, default: Boolean): Boolean =
        booleans[key] ?: default

    override fun putBoolean(key: String, value: Boolean) {
        booleans[key] = value
    }

    override fun getString(key: String, default: String?): String? =
        if (strings.containsKey(key)) strings[key] else default

    override fun putString(key: String, value: String?) {
        if (value == null) strings.remove(key) else strings[key] = value
    }

    override fun remove(key: String) {
        booleans.remove(key)
        strings.remove(key)
    }
}

/**
 * SharedPreferences-backed store (MODE_PRIVATE).
 * Named "secure-store" adapter in architecture — local prefs for resolved-once.
 */
class SharedPrefsKeyValueStore(
    context: Context,
    name: String = "taqlyn_sdk",
) : KeyValueStore {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getBoolean(key: String, default: Boolean): Boolean =
        prefs.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getString(key: String, default: String?): String? =
        prefs.getString(key, default)

    override fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

object SdkStoreKeys {
    const val DEFERRED_RESOLVED = "taqlyn.deferred_resolved"
}
