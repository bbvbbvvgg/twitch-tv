package su.expensive.twitchtv.ui.core

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * Miniature of each design's real screen structure, drawn as vector geometry (no bitmaps, no
 * third-party art). Used by every design's picker so the choice shows layout, not just colour.
 */
@Composable
fun DesignPreview(d: Design, modifier: Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(8.dp))) {
        when (d) {
            Design.CLASSIC -> classic()
            Design.GLASS -> glass()
            Design.GTV -> gtv()
            Design.VICE -> vice()
        }
    }
}

private fun DrawScope.r(x: Float, y: Float, w: Float, h: Float, c: Color, rad: Float = 0f) =
    drawRoundRect(c, Offset(size.width * x, size.height * y), Size(size.width * w, size.height * h), CornerRadius(rad * size.width, rad * size.width))

private fun DrawScope.classic() {
    drawRect(Color(0xFF0B0B0F))
    r(0.05f, 0.07f, 0.12f, 0.06f, Color(0xFF9147FF), 0.01f)
    for (i in 0..5) r(0.22f + i * 0.11f, 0.07f, 0.09f, 0.06f, if (i == 1) Color(0xFFEFEFF1) else Color(0xFF23232B), 0.02f)
    for (row in 0..1) for (col in 0..3) {
        val x = 0.05f + col * 0.23f; val y = 0.24f + row * 0.38f
        r(x, y, 0.21f, 0.22f, Color(0xFF23232B), 0.01f)
        r(x, y + 0.24f, 0.21f, 0.08f, Color(0xFF17171D), 0.01f)
        r(x + 0.01f, y + 0.02f, 0.05f, 0.04f, Color(0xFFE91916))
    }
}

private fun DrawScope.glass() {
    drawRect(Brush.linearGradient(listOf(Color(0xFF1B2A4A), Color(0xFF3B2A5A), Color(0xFF0E3B44)), Offset.Zero, Offset(size.width, size.height)))
    drawCircle(Color(0x334FD1FF), size.height * 0.5f, Offset(size.width * 0.8f, size.height * 0.2f))
    r(0.30f, 0.06f, 0.40f, 0.09f, Color(0x40FFFFFF), 0.05f)
    r(0.32f, 0.075f, 0.09f, 0.06f, Color(0xCCFFFFFF), 0.03f)
    r(0.06f, 0.22f, 0.56f, 0.46f, Color(0x33FFFFFF), 0.035f)
    r(0.66f, 0.30f, 0.26f, 0.05f, Color(0xCCFFFFFF), 0.01f)
    r(0.66f, 0.38f, 0.20f, 0.03f, Color(0x80FFFFFF), 0.01f)
    for (i in 0..4) r(0.06f + i * 0.19f, 0.74f, 0.17f, 0.18f, if (i == 0) Color(0x99FFFFFF) else Color(0x33FFFFFF), 0.03f)
}

private fun DrawScope.gtv() {
    drawRect(Color(0xFF111316))
    r(0f, 0f, 1f, 0.56f, Color(0xFF3A4A5E))
    drawRect(Brush.horizontalGradient(listOf(Color(0xF0111316), Color.Transparent), 0f, size.width * 0.7f), size = Size(size.width, size.height * 0.56f))
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF111316)), size.height * 0.35f, size.height * 0.56f))
    for (i in 0..4) r(0.05f + i * 0.09f, 0.05f, 0.075f, 0.05f, if (i == 1) Color(0xFFE8EAED) else Color(0x40E8EAED), 0.025f)
    r(0.05f, 0.22f, 0.32f, 0.06f, Color(0xFFE8EAED), 0.01f)
    r(0.05f, 0.31f, 0.22f, 0.03f, Color(0x99E8EAED), 0.01f)
    r(0.05f, 0.39f, 0.10f, 0.06f, Color(0xFFE8EAED), 0.03f)
    for (row in 0..1) for (i in 0..5) r(0.05f + i * 0.16f, 0.60f + row * 0.2f, 0.145f, 0.15f, if (row == 0 && i == 0) Color(0xFFBFC6D0) else Color(0xFF2A2F36), 0.015f)
}

private fun DrawScope.vice() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0B2E), Color(0xFF5B1A55), Color(0xFFFF6A5C), Color(0xFFFFB36B))))
    val sun = Offset(size.width * 0.72f, size.height * 0.62f)
    drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFE08A), Color(0xFFFF4F8B)), sun.y - size.height * 0.3f, sun.y + size.height * 0.3f), size.height * 0.3f, sun)
    for (i in 0..3) drawRect(Color(0xFF5B1A55), Offset(size.width * 0.4f, sun.y + i * size.height * 0.07f), Size(size.width * 0.6f, size.height * 0.02f))
    drawRect(Color(0xE61A0B2E), Offset.Zero, Size(size.width * 0.38f, size.height))
    for (i in 0..5) r(0.05f, 0.14f + i * 0.13f, if (i == 1) 0.28f else 0.2f, 0.07f, if (i == 1) Color(0xFFFF4F8B) else Color(0x99FFFFFF), 0f)
    rotate(-4f, Offset(size.width * 0.66f, size.height * 0.4f)) {
        r(0.47f, 0.16f, 0.38f, 0.42f, Color(0xFF2EE6D6), 0f)
        r(0.485f, 0.18f, 0.35f, 0.3f, Color(0xFF1A0B2E), 0f)
    }
}
