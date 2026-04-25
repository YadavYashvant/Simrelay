package com.example.simrelay

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

object ConfigManager {
    private const val PREFS_NAME = "simrelay_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val DEFAULT_API_KEY = "sk_test_simrelay_8f92"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getApiKey(): String {
        return prefs.getString(KEY_API_KEY, DEFAULT_API_KEY) ?: DEFAULT_API_KEY
    }

    fun setApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun regenerateApiKey(): String {
        val newKey = "sk_" + UUID.randomUUID().toString().replace("-", "").take(16)
        setApiKey(newKey)
        return newKey
    }
}
