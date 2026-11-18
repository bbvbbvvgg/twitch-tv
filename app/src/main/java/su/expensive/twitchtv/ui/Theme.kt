package su.expensive.twitchtv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

object Palette {
    val bg = Color(0xFF0B0B0F)
    val surface = Color(0xFF17171D)
    val surfaceHi = Color(0xFF23232B)
    val accent = Color(0xFF9147FF)   // Twitch purple
    val live = Color(0xFFE91916)
    val text = Color(0xFFEFEFF1)
    val dim = Color(0xFFADADB8)
}

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.accent,
            onPrimary = Color.White,
            background = Palette.bg,
            surface = Palette.surface,
            onSurface = Palette.text,
            surfaceVariant = Palette.surfaceHi,
            onSurfaceVariant = Palette.dim,
            border = Color.White,
        ),
        typography = Typography(
            headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            bodyMedium = TextStyle(fontSize = 15.sp),
            bodySmall = TextStyle(fontSize = 13.sp),
            labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
}

fun formatViewers(n: Int): String = when {
    n >= 1_000_000 -> "%.1f млн".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1f тыс.".format(n / 1_000.0)
    else -> n.toString()
}
