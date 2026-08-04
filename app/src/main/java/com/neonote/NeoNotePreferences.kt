package com.neonote

import android.content.Context
import com.neonote.model.DefaultInkColorArgb
import com.neonote.model.EraserMode
import com.neonote.model.InkBrush

public enum class AppAppearance { System, Light, Dark }

public data class NeoNotePreferences(
    val appearance: AppAppearance = AppAppearance.System,
    val defaultInkColorArgb: Int = DefaultInkColorArgb,
    val defaultInkWidth: Float = 3f,
    val defaultInkOpacity: Float = 1f,
    val pressureEnabled: Boolean = true,
    val defaultBrush: InkBrush = InkBrush.Pen,
    val eraserMode: EraserMode = EraserMode.Segment,
)

public class NeoNotePreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    public fun load(): NeoNotePreferences = NeoNotePreferences(
        appearance = preferences.getString(AppearanceKey, null)
            ?.let { runCatching { AppAppearance.valueOf(it) }.getOrNull() }
            ?: AppAppearance.System,
        defaultInkColorArgb = preferences.getInt(InkColorKey, DefaultInkColorArgb),
        defaultInkWidth = preferences.getFloat(InkWidthKey, 3f).coerceIn(0.5f, 40f),
        defaultInkOpacity = preferences.getFloat(InkOpacityKey, 1f).coerceIn(0.05f, 1f),
        pressureEnabled = preferences.getBoolean(PressureKey, true),
        defaultBrush = preferences.getString(BrushKey, null)
            ?.let { runCatching { InkBrush.valueOf(it) }.getOrNull() }
            ?: InkBrush.Pen,
        eraserMode = preferences.getString(EraserKey, null)
            ?.let { runCatching { EraserMode.valueOf(it) }.getOrNull() }
            ?: EraserMode.Segment,
    )

    public fun save(value: NeoNotePreferences) {
        preferences.edit()
            .putString(AppearanceKey, value.appearance.name)
            .putInt(InkColorKey, value.defaultInkColorArgb)
            .putFloat(InkWidthKey, value.defaultInkWidth.coerceIn(0.5f, 40f))
            .putFloat(InkOpacityKey, value.defaultInkOpacity.coerceIn(0.05f, 1f))
            .putBoolean(PressureKey, value.pressureEnabled)
            .putString(BrushKey, value.defaultBrush.name)
            .putString(EraserKey, value.eraserMode.name)
            .apply()
    }

    private companion object {
        const val PreferencesName = "neonote_preferences"
        const val AppearanceKey = "appearance"
        const val InkColorKey = "ink_color"
        const val InkWidthKey = "ink_width"
        const val InkOpacityKey = "ink_opacity"
        const val PressureKey = "pressure"
        const val BrushKey = "brush"
        const val EraserKey = "eraser"
    }
}
