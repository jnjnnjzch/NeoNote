package com.neonote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

private val NeoNoteLightScheme = lightColorScheme(
    primary = Color(0xFF5B3FD1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1A75),
    secondary = Color(0xFF6F5AA8),
    onSecondary = Color.White,
    background = Color(0xFFF4F3F8),
    onBackground = Color(0xFF1C1A24),
    surface = Color.White,
    onSurface = Color(0xFF1C1A24),
    surfaceVariant = Color(0xFFF1EEF6),
    onSurfaceVariant = Color(0xFF625D6D),
    outline = Color(0xFFCBC5D5),
    error = Color(0xFFB42318),
    onError = Color.White,
)

private val NeoNoteDarkScheme = darkColorScheme(
    primary = Color(0xFFB8A7FF),
    onPrimary = Color(0xFF2D176D),
    primaryContainer = Color(0xFF463092),
    onPrimaryContainer = Color(0xFFE8E0FF),
    secondary = Color(0xFFC8B7F2),
    onSecondary = Color(0xFF33265B),
    background = Color(0xFF15131A),
    onBackground = Color(0xFFE9E4EE),
    surface = Color(0xFF201D27),
    onSurface = Color(0xFFE9E4EE),
    surfaceVariant = Color(0xFF302C39),
    onSurfaceVariant = Color(0xFFCEC6D6),
    outline = Color(0xFF8F8799),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NeoNoteApp() }
    }
}

@Composable
public fun NeoNoteApp(controller: NeoNoteEditorController? = null) {
    val editorController = controller ?: viewModel<NeoNoteEditorViewModel>().controller
    val context = LocalContext.current
    val preferencesStore = remember(context) { NeoNotePreferencesStore(context) }
    var preferences by remember { mutableStateOf(preferencesStore.load()) }
    val dark = when (preferences.appearance) {
        AppAppearance.System -> isSystemInDarkTheme()
        AppAppearance.Light -> false
        AppAppearance.Dark -> true
    }

    MaterialTheme(colorScheme = if (dark) NeoNoteDarkScheme else NeoNoteLightScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            NeoNoteEditorScreen(
                controller = editorController,
                preferences = preferences,
                onPreferencesChange = { next ->
                    preferences = next
                    preferencesStore.save(next)
                },
            )
        }
    }
}
