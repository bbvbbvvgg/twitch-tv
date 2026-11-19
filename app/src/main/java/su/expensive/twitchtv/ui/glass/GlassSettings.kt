package su.expensive.twitchtv.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import su.expensive.twitchtv.data.Account
import su.expensive.twitchtv.ui.core.AppState
import su.expensive.twitchtv.ui.core.Design
import su.expensive.twitchtv.ui.core.DesignPreview
import su.expensive.twitchtv.ui.core.LOGIN_PRIVACY
import su.expensive.twitchtv.ui.core.Setting
import su.expensive.twitchtv.ui.core.rememberDeviceLogin

private sealed interface Sheet {
    data object Account : Sheet
    data object Design : Sheet
    data class Pick(val s: Setting.Choice) : Sheet
    data object Mode : Sheet
    data object Proxy : Sheet
}

/**
 * Glass settings: inset-grouped glass cards in one centred column. Switches are glass toggles;
 * a choice opens a floating pop-over menu; sign-in and design open a sheet that floats in from
 * the right over the dimmed scene.
 */
@UnstableApi
@Composable
fun GlassSettings(app: AppState) {
    val m = app.settings
    var sheet by remember { mutableStateOf<Sheet?>(if (app.openSettingsOnce && !app.openNetworkOnce) Sheet.Design else null) }
    val rowFocus = remember { HashMap<String, FocusRequester>() }
    var opener by remember { mutableStateOf("design") }
    fun close() { sheet = null }
    LaunchedEffect(sheet, m.consentAsk) { if (sheet == null && !m.consentAsk) { delay(80); runCatching { rowFocus[opener]?.requestFocus() } } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxWidth(0.66f).align(Alignment.TopCenter),
            contentPadding = PaddingValues(top = 6.dp, bottom = 60.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            m.groups.forEachIndexed { gi, g ->
                item(key = "h" + g.title) {
                    Text(g.title.uppercase(), style = GType.small.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold),
                        color = G.dim, modifier = Modifier.padding(start = 22.dp, top = if (gi == 0) 0.dp else 18.dp, bottom = 6.dp))
                }
                item(key = "g" + g.title) {
                    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        g.items.forEachIndexed { i, s ->
                            m.version
                            val mod = Modifier.focusRequester(rowFocus.getOrPut(s.id) { FocusRequester() })
                            SettingRow(s, m.valueOf(s), mod) {
                                opener = s.id
                                when (s) {
                                    is Setting.Toggle -> s.flip()
                                    is Setting.Choice -> sheet = if (s == m.connMode) Sheet.Mode else Sheet.Pick(s)
                                    is Setting.Action -> s.run()
                                    is Setting.Screen -> sheet = when (s) { m.account -> Sheet.Account; m.proxy -> Sheet.Proxy; else -> Sheet.Design }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(start = 22.dp, top = 22.dp)) {
                    m.aboutLines.forEach { Text(it, style = GType.small, color = G.faint, modifier = Modifier.padding(bottom = 4.dp)) }
                }
            }
        }

        val open = sheet
        AnimatedVisibility(open != null, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
            Box(Modifier.fillMaxSize().background(Color(0x99070A14)))
        }
        AnimatedVisibility(
            open != null, modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn(),
            exit = slideOutHorizontally(tween(220)) { it / 2 } + fadeOut(tween(200)),
        ) {
            val last = remember { mutableStateOf(open) }
            if (open != null) last.value = open
            Box(Modifier.fillMaxHeight().width(560.dp).padding(vertical = 8.dp).background(G.ink.copy(alpha = 0.9f), RoundedCornerShape(36.dp))
                .glass(RoundedCornerShape(36.dp), 1.5f).padding(32.dp)
                .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()) {
                when (val sh = last.value) {
                    Sheet.Account -> AccountSheet(app) { close() }
                    Sheet.Design -> DesignSheet(app)
                    is Sheet.Pick -> PickSheet(sh.s) { close() }
                    Sheet.Mode -> GlassModeSheet(m) { close() }
                    Sheet.Proxy -> GlassProxySheet(m) { close() }
                    null -> {}
                }
            }
        }
        if (open != null) BackHandler { close() }
        if (m.consentAsk) GlassConsent(m)
    }
}

@Composable
private fun SettingRow(s: Setting, value: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = G.text,
            focusedContainerColor = G.solid, focusedContentColor = G.onSolid),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(G.glow.copy(alpha = 0.4f), 12.dp)),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.title, style = GType.label)
                s.hint?.let { Text(it, style = GType.small, modifier = Modifier.padding(top = 2.dp), color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.62f)) }
            }
            Spacer(Modifier.width(16.dp))
            when (s) {
                is Setting.Toggle -> GlassSwitch(s.get())
                is Setting.Choice -> { Text(value, style = GType.label); Icon(Icons.Rounded.UnfoldMore, null, Modifier.padding(start = 6.dp).size(20.dp)) }
                is Setting.Action -> Text(value, style = GType.small, maxLines = 1)
                is Setting.Screen -> { Text(value, style = GType.label); Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(24.dp)) }
            }
        }
    }
}

/** Glass toggle: the knob springs across a translucent track; "on" fills the track with light. */
@Composable
private fun GlassSwitch(on: Boolean) {
    val x by animateDpAsState(if (on) 24.dp else 2.dp, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "knob")
    Box(Modifier.width(54.dp).height(32.dp).background(if (on) Color(0xFF34C759) else Color(0x40FFFFFF), CircleShape)) {
        Box(Modifier.offset(x = x, y = 2.dp).size(28.dp).background(Color.White, CircleShape))
    }
}

@Composable
private fun PickSheet(s: Setting.Choice, onDone: () -> Unit) {
    val fr = remember { FocusRequester() }
    Column {
        Text(s.title, style = GType.title, color = G.text)
        s.hint?.let { Text(it, style = GType.small, color = G.dim, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(22.dp))
        s.options.forEachIndexed { i, o ->
            val sel = i == s.index()
            GlassButton(o, if (sel) Icons.Rounded.Check else null,
                Modifier.fillMaxWidth().padding(vertical = 4.dp).then(if (sel) Modifier.focusRequester(fr) else Modifier),
                selected = sel, shape = RoundedCornerShape(20.dp)) { s.step(i - s.index()); onDone() }
        }
    }
    LaunchedEffect(Unit) { delay(60); runCatching { fr.requestFocus() } }
}

@UnstableApi
@Composable
private fun DesignSheet(app: AppState) {
    val fr = remember { FocusRequester() }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Дизайн интерфейса", style = GType.title, color = G.text)
        Text("Меняется сразу. Избранное, история и настройки общие.", style = GType.small, color = G.dim, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
        Design.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                row.forEach { d ->
                    val sel = app.design == d
                    val shape = RoundedCornerShape(24.dp)
                    Surface(
                        onClick = { app.applyDesign(d) },
                        modifier = Modifier.weight(1f).then(if (sel) Modifier.focusRequester(fr) else Modifier),
                        shape = ClickableSurfaceDefaults.shape(shape),
                        colors = ClickableSurfaceDefaults.colors(containerColor = G.fill, focusedContainerColor = G.solid,
                            contentColor = G.text, focusedContentColor = G.onSolid),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(BorderStroke(if (sel) 2.dp else 1.dp, Color.White.copy(alpha = if (sel) 0.9f else 0.2f)), shape = shape)),
                        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(G.glow.copy(alpha = 0.55f), 16.dp)),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            DesignPreview(d, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                            Row(Modifier.padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(d.title, style = GType.label, modifier = Modifier.weight(1f))
                                if (sel) Icon(Icons.Rounded.Check, "Выбран", Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
}

@UnstableApi
@Composable
private fun AccountSheet(app: AppState, onClose: () -> Unit) {
    val m = app.settings
    val who = Account.login
    val fr = remember { FocusRequester() }
    Column {
        Text("Аккаунт Twitch", style = GType.title, color = G.text)
        Spacer(Modifier.height(18.dp))
        if (who != null) {
            Text("Вы вошли как", style = GType.body, color = G.dim)
            Text(who, style = GType.display, color = G.text)
            Text("С входом Twitch отдаёт 1440p60 там, где стример его транслирует.", style = GType.small, color = G.dim, modifier = Modifier.padding(top = 10.dp, bottom = 24.dp))
            GlassButton("Выйти и отозвать токен", modifier = Modifier.focusRequester(fr)) { m.signOut(); onClose() }
            LaunchedEffect(Unit) { delay(60); runCatching { fr.requestFocus() } }
        } else {
            val st = rememberDeviceLogin { m.signedIn() }
            st.code?.let {
                Text("На телефоне откройте", style = GType.body, color = G.dim)
                Text(st.site, style = GType.title.copy(fontSize = 30.sp), color = G.tint)
                Text("и введите код", style = GType.body, color = G.dim, modifier = Modifier.padding(top = 14.dp))
                Box(Modifier.padding(top = 10.dp).glass(RoundedCornerShape(24.dp), 1.6f).padding(horizontal = 26.dp, vertical = 14.dp)) {
                    Text(it.userCode, style = GType.display.copy(fontSize = 52.sp, letterSpacing = 8.sp), color = G.text)
                }
            }
            Text(st.message, style = GType.body, color = G.dim, modifier = Modifier.padding(top = 18.dp))
            Text(LOGIN_PRIVACY, style = GType.small, color = G.faint, modifier = Modifier.padding(top = 10.dp, bottom = 18.dp))
            GlassButton("Закрыть", modifier = Modifier.focusRequester(fr)) { onClose() }
            LaunchedEffect(Unit) { delay(60); runCatching { fr.requestFocus() } }
        }
    }
}
