package su.expensive.twitchtv.ui.gtv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import su.expensive.twitchtv.data.Account
import su.expensive.twitchtv.ui.core.AppState
import su.expensive.twitchtv.ui.core.Design
import su.expensive.twitchtv.ui.core.DesignPreview
import su.expensive.twitchtv.ui.core.LOGIN_PRIVACY
import su.expensive.twitchtv.ui.core.Setting
import su.expensive.twitchtv.ui.core.SettingsModel
import su.expensive.twitchtv.net.Connection
import su.expensive.twitchtv.ui.core.rememberDeviceLogin

/**
 * Google TV settings: two panes. Left, the categories (focus selects, like the system settings);
 * right, that category's controls — Material switches, radio lists for choices, the design gallery
 * and the sign-in code. Moving right enters the detail pane; Back/left returns.
 */
private data class Cat(val title: String, val icon: ImageVector)

@UnstableApi
@Composable
fun GtvSettings(app: AppState) {
    val m = app.settings
    val cats = listOf(
        Cat("Аккаунт", Icons.Rounded.AccountCircle), Cat("Воспроизведение", Icons.Rounded.PlayCircle),
        Cat(SettingsModel.NET_GROUP, Icons.Rounded.Router), Cat("Чат", Icons.AutoMirrored.Rounded.Chat),
        Cat("Интерфейс", Icons.Rounded.Palette), Cat("Диагностика", Icons.Rounded.Insights), Cat("О приложении", Icons.Rounded.Info),
    )
    var sel by remember { mutableIntStateOf(if (app.openNetworkOnce) 2 else if (app.openSettingsOnce) 4 else 0) }
    var editingProxy by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxSize().padding(top = 84.dp)) {
        Column(Modifier.width(360.dp).fillMaxHeight().padding(start = 48.dp, end = 20.dp)) {
            Text("Настройки", style = TType.hero.copy(fontSize = 34.sp), color = T.text, modifier = Modifier.padding(bottom = 18.dp, start = 12.dp))
            cats.forEachIndexed { i, c ->
                Surface(
                    onClick = { sel = i }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).onFocusChanged { if (it.isFocused) sel = i },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(28.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = if (sel == i) T.surfaceHi else Color.Transparent, contentColor = T.text,
                        focusedContainerColor = T.focus, focusedContentColor = T.onFocus),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                ) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(c.icon, null, Modifier.size(24.dp)); Spacer(Modifier.width(14.dp)); Text(c.title, style = TType.label)
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(end = 48.dp).background(T.surface, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))) {
            AnimatedContent(sel, transitionSpec = { (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 16 }) togetherWith fadeOut(tween(120)) }, label = "pane") { i ->
                LazyColumn(contentPadding = PaddingValues(28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (i) {
                        0 -> item { AccountPane(app) }
                        4 -> item { DesignGallery(app) }
                        2 -> {
                            item { Text(SettingsModel.NET_GROUP, style = TType.headline, color = T.text, modifier = Modifier.padding(start = 18.dp, bottom = 4.dp)) }
                            item { Text("Видео всегда идёт напрямую с CDN Twitch. Happ и другие VPN работают в режиме «Прямое».", style = TType.body,
                                color = T.dim, modifier = Modifier.padding(start = 18.dp, bottom = 10.dp)) }
                            val g = m.groups.first { it.title == SettingsModel.NET_GROUP }
                            items(g.items, key = { it.id }) { s ->
                                m.version; Connection.version
                                if (s == m.proxy) {
                                    SettingItem(s, m.valueOf(s)) { editingProxy = !editingProxy }
                                    if (editingProxy) GtvProxyForm(m) { editingProxy = false }
                                } else SettingItem(s, m.valueOf(s))
                            }
                        }
                        6 -> items(m.aboutLines) { Text(it, style = TType.body, color = T.dim, modifier = Modifier.padding(bottom = 10.dp)) }
                        else -> {
                            val g = m.groups.first { it.title == cats[i].title }
                            items(g.items, key = { it.id }) { s -> m.version; SettingItem(s, m.valueOf(s)) }
                        }
                    }
                }
            }
        }
    }
    if (m.consentAsk) GtvConsent(m)
    }
}

@Composable
private fun SettingItem(s: Setting, value: String, onScreen: () -> Unit = {}) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Surface(
            onClick = {
                when (s) {
                    is Setting.Toggle -> s.flip()
                    is Setting.Choice -> expanded = !expanded
                    is Setting.Action -> s.run()
                    is Setting.Screen -> onScreen()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = T.text,
                focusedContainerColor = T.focus, focusedContentColor = T.onFocus),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.title, style = TType.label.copy(fontSize = 17.sp))
                    val sub = if (s is Setting.Choice) value else s.hint
                    sub?.let { Text(it, style = TType.caption, color = LocalContentColor.current.copy(alpha = 0.7f), modifier = Modifier.padding(top = 2.dp)) }
                }
                when (s) {
                    is Setting.Toggle -> Switch(checked = s.get(), onCheckedChange = null,
                        colors = SwitchDefaults.colors(checkedThumbColor = T.onFocus, checkedTrackColor = T.accent))
                    is Setting.Action -> Text(value, style = TType.caption, maxLines = 2, modifier = Modifier.widthIn(max = 420.dp))
                    is Setting.Screen -> Text(value, style = TType.caption, maxLines = 1)
                    else -> {}
                }
            }
        }
        if (s is Setting.Choice && expanded) Column(Modifier.padding(start = 18.dp)) {
            s.options.forEachIndexed { i, o ->
                val on = i == s.index()
                Surface(onClick = { s.step(i - s.index()); expanded = false }, modifier = Modifier.fillMaxWidth(),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = T.text,
                        focusedContainerColor = T.focus, focusedContentColor = T.onFocus),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (on) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp)); Text(o, style = TType.body)
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun DesignGallery(app: AppState) {
    Column {
        Text("Дизайн интерфейса", style = TType.headline, color = T.text)
        Text("Применяется сразу, без перезапуска. Избранное, история и настройки общие.", style = TType.body, color = T.dim,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Design.entries.forEach { d ->
                val sel = app.design == d
                val shape = RoundedCornerShape(12.dp)
                Column(Modifier.weight(1f)) {
                    Surface(onClick = { app.applyDesign(d) }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        shape = ClickableSurfaceDefaults.shape(shape),
                        colors = ClickableSurfaceDefaults.colors(containerColor = T.bg, focusedContainerColor = T.bg),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(BorderStroke(if (sel) 2.dp else 0.dp, if (sel) T.accent else Color.Transparent), shape = shape),
                            focusedBorder = Border(BorderStroke(3.dp, T.focus), shape = shape))) {
                        DesignPreview(d, Modifier.fillMaxSize())
                    }
                    Text(d.title + if (sel) " · выбран" else "", style = TType.label, color = if (sel) T.accent else T.text, modifier = Modifier.padding(top = 10.dp))
                    Text(d.tagline, style = TType.caption, color = T.dim, maxLines = 4)
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun AccountPane(app: AppState) {
    val m = app.settings
    val who = Account.login
    var signing by remember { mutableStateOf(false) }
    Column {
        Text("Аккаунт Twitch", style = TType.headline, color = T.text)
        if (who != null) {
            Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).background(T.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Text(who.take(1).uppercase(), style = TType.headline.copy(fontWeight = FontWeight.Bold), color = T.onFocus)
                }
                Spacer(Modifier.width(16.dp))
                Column { Text(who, style = TType.headline, color = T.text); Text("Вход выполнен · 1440p60 доступно", style = TType.caption, color = T.dim) }
            }
            Pill("Выйти (токен будет отозван)", selected = true) { m.signOut() }
        } else if (!signing) {
            Text("Войдите, чтобы Twitch отдавал 1440p60 там, где стример его транслирует.", style = TType.body, color = T.dim, modifier = Modifier.padding(vertical = 14.dp))
            Pill("Войти с телефона", Icons.Rounded.AccountCircle, selected = true) { signing = true }
        } else {
            val st = rememberDeviceLogin { signing = false; m.signedIn() }
            Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("1. На телефоне откройте", style = TType.body, color = T.dim)
                    Text(st.site, style = TType.headline.copy(fontSize = 28.sp), color = T.accent)
                    Text("2. Введите код", style = TType.body, color = T.dim, modifier = Modifier.padding(top = 14.dp))
                    Text(st.code?.userCode ?: "· · · ·", style = TType.hero.copy(letterSpacing = 6.sp), color = T.text)
                    Text(st.message, style = TType.body, color = T.dim, modifier = Modifier.padding(top = 10.dp))
                }
            }
            Text(LOGIN_PRIVACY, style = TType.caption, color = T.faint, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
            Pill("Отмена", selected = true) { signing = false }
        }
    }
}
