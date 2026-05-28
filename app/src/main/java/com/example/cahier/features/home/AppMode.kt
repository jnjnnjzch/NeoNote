package com.example.cahier.features.home

import android.content.Context

enum class AppMode {
    NORMAL,
    DEBUG
}

private const val APP_MODE_PREFS = "neonote_app_mode"
private const val APP_MODE_KEY = "app_mode"

fun loadAppMode(context: Context): AppMode {
    val value = context.getSharedPreferences(APP_MODE_PREFS, Context.MODE_PRIVATE)
        .getString(APP_MODE_KEY, AppMode.NORMAL.name)
    return AppMode.entries.firstOrNull { it.name == value } ?: AppMode.NORMAL
}

fun persistAppMode(context: Context, mode: AppMode) {
    context.getSharedPreferences(APP_MODE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(APP_MODE_KEY, mode.name)
        .apply()
}
