package nl.msvos.karoodrinkwaterpunten

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF002B3A),
    background = Color(0xFF101820),
    surface = Color(0xFF18242E),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
