package su.expensive.twitchtv.ui.core

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import su.expensive.twitchtv.chat.ChatLine
import su.expensive.twitchtv.chat.Part

/**
 * One chat message with inline emotes. Shared rendering primitive; each design wraps it in its
 * own chat container (strip, glass card, side sheet, phone). [nameColor] overrides Twitch user
 * colours when a design needs guaranteed contrast.
 */
@Composable
fun ChatLineText(line: ChatLine, style: TextStyle, textColor: Color, fallbackName: Color, emote: TextUnit, nameColor: ((Int?) -> Color)? = null, modifier: Modifier = Modifier) {
    val (text, inline) = remember(line.id) {
        val inline = HashMap<String, InlineTextContent>()
        val t = buildAnnotatedString {
            val c = nameColor?.invoke(line.color) ?: line.color?.let { Color(it) } ?: fallbackName
            withStyle(SpanStyle(color = c, fontWeight = FontWeight.Bold)) { append(line.user) }
            append(": ")
            line.parts.forEachIndexed { i, p ->
                when (p) {
                    is Part.Text -> append(p.text)
                    is Part.Emote -> {
                        val id = "e$i"
                        appendInlineContent(id, p.name)
                        inline[id] = InlineTextContent(Placeholder(emote, emote, PlaceholderVerticalAlign.Center)) {
                            AsyncImage(p.url, p.name, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        t to inline
    }
    Text(text, inlineContent = inline, color = textColor, style = style, modifier = modifier)
}

/** Brightens dark Twitch name colours so they stay readable on dark glass/neon surfaces. */
fun readableName(argb: Int?, fallback: Color): Color {
    val c = argb?.let { Color(it) } ?: return fallback
    val lum = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
    return if (lum < 0.45f) Color(red = c.red + (1 - c.red) * 0.5f, green = c.green + (1 - c.green) * 0.5f, blue = c.blue + (1 - c.blue) * 0.5f) else c
}
