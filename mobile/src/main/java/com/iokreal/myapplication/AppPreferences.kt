package com.iokreal.myapplication

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

data class Camera(
    val name: String,
    val id: String,
    val token: String
)

data class Door(
    val id: String,
    val name: String
)

object AppPreferences {
    private const val PREFS_NAME = "AppPreferences"
    private lateinit var sharedPref: SharedPreferences

    fun init(context: Context) {
        if (!::sharedPref.isInitialized) {
            sharedPref = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    var urlCam: String
        get() = sharedPref.getString("urlCam", "") ?: ""
        set(value) = sharedPref.edit { putString("urlCam", value) }

    var idCam: String
        get() = sharedPref.getString("idCam", "") ?: ""
        set(value) = sharedPref.edit { putString("idCam", value) }

    var idDoor: String
        get() = sharedPref.getString("idDoor", "") ?: ""
        set(value) = sharedPref.edit { putString("idDoor", value) }

    var key: String
        get() = sharedPref.getString("key", "") ?: ""
        set(value) = sharedPref.edit { putString("key", value) }

    var webInited: Boolean
        get() = sharedPref.getBoolean("webInited", false)
        set(value) = sharedPref.edit { putBoolean("webInited", value) }
}