package com.neonote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

private val NeoNoteColorScheme = lightColorScheme(
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

public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NeoNoteApp() }
    }
}

@Composable
public fun NeoNoteApp(controller: NeoNoteEditorController? = null) {
    val editorController = controller ?: viewModel<NeoNoteEditorViewModel>().controller

    MaterialTheme(colorScheme = NeoNoteColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            NeoNoteEditorScreen(controller = editorController)
        }
    }
}
