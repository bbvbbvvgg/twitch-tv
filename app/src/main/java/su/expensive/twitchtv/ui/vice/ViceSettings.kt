package su.expensive.twitchtv.ui.vice

import android.view.KeyEvent as AKeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import su.expensive.twitchtv.data.Account
import su.expensive.twitchtv.ui.core.AppState
import su.expensive.twitchtv.ui.core.Design
import su.expensive.twitchtv.ui.core.DesignPreview
import su.expensive.twitchtv.ui.core.LOGIN_PRIVACY
import su.expensive.twitchtv.ui.core.Setting
import su.expensive.twitchtv.ui.core.rememberDeviceLogin

/**
 * Pause-menu settings. Tabs across the top (focus a tab to switch); every value row is a
 * ◀ value ▶ selector changed with ←/→ (or OK to step forward), like a game's options screen.
 * Design choice is a row of postcards; sign-in shows the code on a neon board.
 */
private val TABS = listOf("Аккаунт", "Видео", "Чат", "Интерфейс", "Диагностика")

@UnstableApi
@Composable
fun ViceSettings(app: AppState) {
    val m = app.settings
    var tab by remember { mutableIntStateOf(if (app.openSettingsOnce) 3 else 1) }
    Column(Modifier.fillMaxSize()) {
        NeonTitle("Настройки", V.teal, Modifier.padding(bottom = 16.dp), VType.mega.copy(fontSize = 44.sp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TABS.forEachIndexed { i, t ->
                Surface(
                    onClick = { tab = i }, modifier = Modifier.onFocusChanged { if (it.isFocused) tab = i },
                    shape = ClickableSurfaceDefaults.shape(SlantShape(0.2f)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = if (tab == i) V.plum else Color.Transparent,
                        contentColor = if (tab == i) V.cream else V.dim, focusedContainerColor = V.teal, focusedContentColor = V.night),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                ) { Text(t.uppercase(), style = VType.tag.copy(fontSize = 16.sp), modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(2.dp).background(V.dusky))
        AnimatedContent(tab, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "vtab") { t ->
            LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when (t) {
                    0 -> item { AccountBoard(app) }
                    3 -> item { DesignPostcards(app) }
                    else -> {
                        val group = when (t) { 1 -> "Воспроизведение"; 2 -> "Чат"; else -> "Диагностика" }
                        items(m.groups.first { it.title == group }.items, key = { it.id }) { s -> m.version; OptionRow(s, m.valueOf(s)) }
                        if (t == 4) items(m.aboutLines) { Text(it, style = VType.small, color = V.faint, modifier = Modifier.padding(start = 16.dp, top = 8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(s: Setting, value: String) {
    var focused by remember { mutableStateOf(false) }
    val cyclic = s is Setting.Toggle || s is Setting.Choice
    fun change(d: Int) = when (s) {
        is Setting.Toggle -> s.flip()
        is Setting.Choice -> s.step(d)
        is Setting.Action -> if (d > 0) s.run() else Unit
        is Setting.Screen -> Unit
    }
    Surface(
        onClick = { change(1) },
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.onPreviewKeyEvent { e ->
            if (!cyclic || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key.nativeKeyCode) {
                AKeyEvent.KEYCODE_DPAD_LEFT -> { change(-1); true }
                AKeyEvent.KEYCODE_DPAD_RIGHT -> { change(1); true }
                else -> false
            }
        },
        shape = ClickableSurfaceDefaults.shape(SlantShape(0.06f)),
        colors = ClickableSurfaceDefaults.colors(containerColor = V.panel.copy(alpha = 0.6f), contentColor = V.cream,
            focusedContainerColor = V.cream, focusedContentColor = V.night),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.title.uppercase(), style = VType.row.copy(fontSize = 19.sp))
                s.hint?.let { Text(it, style = VType.small, color = LocalContentColor.current.copy(alpha = 0.65f)) }
            }
            if (cyclic) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "меньше", Modifier.size(26.dp).alpha(if (focused) 1f else 0.35f))
                Text(value.uppercase(), style = VType.row.copy(fontSize = 19.sp), color = if (focused) V.magenta else V.teal,
                    modifier = Modifier.width(210.dp).padding(horizontal = 6.dp), maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "больше", Modifier.size(26.dp).alpha(if (focused) 1f else 0.35f))
            } else Text(value, style = VType.small, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@UnstableApi
@Composable
private fun DesignPostcards(app: AppState) {
    Column {
        Text("Дизайн меняется сразу, без перезапуска. Избранное, история и настройки общие.", style = VType.body, color = V.dim,
            modifier = Modifier.padding(bottom = 18.dp, start = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Design.entries.forEachIndexed { i, d ->
                val sel = app.design == d
                Column(Modifier.weight(1f)) {
                    Surface(
                        onClick = { app.applyDesign(d) },
                        modifier = Modifier.fillMaxWidth().rotate(if (i % 2 == 0) -2f else 2f),
                        shape = ClickableSurfaceDefaults.shape(androidx.compose.ui.graphics.RectangleShape),
                        colors = ClickableSurfaceDefaults.colors(containerColor = if (sel) V.teal else V.cream, focusedContainerColor = V.magenta),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(0.dp, Color.Transparent))),
                    ) {
                        Box(Modifier.padding(8.dp)) { DesignPreview(d, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) }
                    }
                    Text(d.title.uppercase(), style = VType.row.copy(fontSize = 18.sp), color = if (sel) V.teal else V.cream, modifier = Modifier.padding(top = 14.dp))
                    Text(if (sel) "ВЫБРАН" else d.tagline, style = if (sel) VType.tag else VType.small, color = if (sel) V.teal else V.dim, maxLines = 4)
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun AccountBoard(app: AppState) {
    val m = app.settings
    val who = Account.login
    var signing by remember { mutableStateOf(false) }
    Column(Modifier.padding(start = 4.dp)) {
        if (who != null) {
            Text("ВЫ ВОШЛИ КАК", style = VType.tag, color = V.teal)
            Text(who.uppercase(), style = VType.mega.copy(shadow = VType.neon(V.teal, 22f)), color = V.cream)
            Text("Twitch отдаёт 1440p60 там, где стример его транслирует.", style = VType.body, color = V.dim, modifier = Modifier.padding(bottom = 14.dp))
            MenuItem("Выйти и отозвать токен", false, style = VType.row) { m.signOut() }
        } else if (!signing) {
            Text("Вход открывает 1440p60. Пароль вводится только на сайте Twitch.", style = VType.body, color = V.dim, modifier = Modifier.padding(bottom = 14.dp))
            MenuItem("Войти с телефона", false, style = VType.row) { signing = true }
        } else {
            val st = rememberDeviceLogin { signing = false; m.signedIn() }
            Text("НА ТЕЛЕФОНЕ ОТКРОЙТЕ", style = VType.tag, color = V.teal)
            Text(st.site.uppercase(), style = VType.title.copy(shadow = VType.neon(V.teal, 16f)), color = V.cream)
            Text("И ВВЕДИТЕ КОД", style = VType.tag, color = V.teal, modifier = Modifier.padding(top = 12.dp))
            Text(st.code?.userCode ?: "— — — —", style = VType.mega.copy(fontSize = 60.sp, letterSpacing = 10.sp, shadow = VType.neon(V.magenta, 30f)), color = V.cream)
            Text(st.message, style = VType.body, color = V.dim)
            Text(LOGIN_PRIVACY, style = VType.small, color = V.faint, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
            MenuItem("Отмена", false, style = VType.row) { signing = false }
        }
    }
}
