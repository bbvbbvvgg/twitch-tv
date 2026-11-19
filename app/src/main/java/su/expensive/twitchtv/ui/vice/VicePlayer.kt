package su.expensive.twitchtv.ui.vice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import su.expensive.twitchtv.chat.ChatClient
import su.expensive.twitchtv.data.StreamItem
import su.expensive.twitchtv.player.QualityChoice
import su.expensive.twitchtv.ui.core.connectionLine
import su.expensive.twitchtv.ui.core.proxyRetryLabel
import su.expensive.twitchtv.ui.core.AppState
import su.expensive.twitchtv.ui.core.ChatLineText
import su.expensive.twitchtv.ui.core.PlayerSession
import su.expensive.twitchtv.ui.core.ToastTimer
import su.expensive.twitchtv.ui.core.VideoSurface
import su.expensive.twitchtv.ui.core.formatViewers
import su.expensive.twitchtv.ui.core.playerKeys
import su.expensive.twitchtv.ui.core.readableName
import su.expensive.twitchtv.ui.core.statusMessage

private enum class VOverlay { NONE, RADIO, PHONE }

/**
 * Vice player = an in-game HUD. Idle: only a small "now on air" tag. A key opens the pause
 * strip on the left (big condensed items). Quality is a radio-station dial (←/→ tune, OK to lock),
 * channels live in a phone's contact list, chat is the phone's message thread.
 */
@UnstableApi
@Composable
fun VicePlayer(app: AppState, s: PlayerSession) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(VOverlay.NONE) }
    var lastKey by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val root = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    var banner by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { root.requestFocus() }
    LaunchedEffect(s.item.login) { banner = true; delay(5_000); banner = false }
    LaunchedEffect(menu, lastKey, overlay) { if (menu && overlay == VOverlay.NONE) { delay(8_000); menu = false; root.requestFocus() } }
    LaunchedEffect(menu) { if (menu) { delay(60); runCatching { first.requestFocus() } } }
    ToastTimer(s)
    BackHandler(enabled = menu || overlay != VOverlay.NONE) {
        if (overlay != VOverlay.NONE) { overlay = VOverlay.NONE; runCatching { first.requestFocus() } } else { menu = false; root.requestFocus() }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .playerKeys(s, { menu || overlay != VOverlay.NONE }, onReveal = { menu = true }, onAnyKey = { lastKey = System.currentTimeMillis() })
            .focusRequester(root).focusable(),
    ) {
        VideoSurface(s)

        statusMessage(s)?.let { StatusSign(it, Modifier.align(Alignment.Center)) }
        if (s.controller.adState.isNotEmpty()) Text(s.controller.adState.uppercase(), style = VType.tag, color = V.cream,
            modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).border(1.dp, V.teal).background(V.panel).padding(horizontal = 12.dp, vertical = 6.dp))

        // "Now on air" tag: shows on channel change and while the menu is open.
        AnimatedVisibility(banner || menu, Modifier.align(Alignment.BottomEnd),
            enter = fadeIn(tween(300)) + slideInHorizontally(tween(380)) { it / 3 }, exit = fadeOut(tween(300))) {
            OnAirTag(s, Modifier.padding(end = 40.dp, bottom = 34.dp))
        }
        // Mission-passed style toast.
        AnimatedVisibility(s.toast != null, Modifier.align(Alignment.TopCenter), enter = fadeIn() + scaleIn(initialScale = 1.15f), exit = fadeOut()) {
            Column(Modifier.padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text((s.toast ?: "").uppercase(), style = VType.mega.copy(fontSize = 40.sp, shadow = VType.neon(V.magenta, 26f)), color = V.cream)
                Box(Modifier.padding(top = 6.dp).width(160.dp).height(3.dp).background(V.dusky))
            }
        }
        if (s.diagOn) Text(s.diagText(), color = V.teal, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 24.dp).border(1.dp, V.teal.copy(alpha = 0.6f)).background(V.panel).padding(12.dp))
        if (s.chatOn) Phone("СООБЩЕНИЯ", Modifier.align(Alignment.CenterEnd).padding(end = 30.dp)) { PhoneChat(s.chat) }

        AnimatedVisibility(menu && overlay == VOverlay.NONE, Modifier.align(Alignment.CenterStart),
            enter = fadeIn(tween(200)) + expandHorizontally(tween(260), Alignment.Start), exit = fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it }) {
            Column(Modifier.fillMaxHeight().width(380.dp).background(Brush.horizontalGradient(listOf(V.night.copy(alpha = 0.95f), V.night.copy(alpha = 0.75f), Color.Transparent)))
                .padding(start = 40.dp, top = 40.dp)) {
                Text("ПАУЗА", style = VType.tag, color = V.teal)
                Text("Vice", style = androidx.compose.ui.text.TextStyle(fontFamily = Script, fontSize = 48.sp, shadow = VType.neon(V.magenta, 24f)), color = V.cream,
                    modifier = Modifier.padding(bottom = 12.dp))
                MenuItem("Качество · ${s.controller.current?.label ?: "—"}", false, Modifier.focusRequester(first)) { overlay = VOverlay.RADIO }
                MenuItem("Каналы", false) { overlay = VOverlay.PHONE }
                MenuItem(if (s.chatOn) "Чат: вкл" else "Чат: выкл", s.chatOn) { s.toggleChat() }
                MenuItem(if (s.isFavorite) "В избранном" else "В избранное", s.isFavorite) { s.toggleFavorite() }
                MenuItem("К эфиру", false) { s.resumeLive() }
                MenuItem(if (s.diagOn) "Диагностика: вкл" else "Диагностика", s.diagOn) { s.toggleDiag() }
                if (s.diagOn) MenuItem("Экспорт лога", false) { s.exportLog(ctx) }
                MenuItem("В меню", false) { app.close() }
            }
        }
        AnimatedVisibility(overlay == VOverlay.RADIO, Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically(spring(dampingRatio = 0.75f)) { it / 2 }, exit = fadeOut()) {
            RadioDial(s) { overlay = VOverlay.NONE; menu = false; root.requestFocus() }
        }
        AnimatedVisibility(overlay == VOverlay.PHONE, Modifier.align(Alignment.CenterEnd),
            enter = fadeIn() + slideInVertically(spring(dampingRatio = 0.7f)) { it }, exit = fadeOut()) {
            Phone("КОНТАКТЫ", Modifier.padding(end = 30.dp).focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()) {
                Contacts(s.list, s.item) { overlay = VOverlay.NONE; menu = false; s.switchTo(it); root.requestFocus() }
            }
        }
    }
}

@UnstableApi
@Composable
private fun OnAirTag(s: PlayerSession, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (s.item.avatar.isNotEmpty()) AsyncImage(s.item.avatar, null, Modifier.size(56.dp).border(2.dp, V.teal).padding(2.dp))
        Spacer(Modifier.width(14.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("СЕЙЧАС В ЭФИРЕ", style = VType.tag, color = V.teal)
            Text(s.item.displayName.uppercase(), style = VType.mega.copy(fontSize = 36.sp, shadow = VType.neon(V.magenta, 22f)), color = V.cream)
            Text("${s.item.game} · ${formatViewers(s.item.viewers)} · ${s.qualityLabel}", style = VType.small, color = V.cream,
                modifier = Modifier.background(V.night.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun StatusSign(text: String, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "flicker")
    val a by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
    Text(text.uppercase(), style = VType.title.copy(shadow = VType.neon(V.magenta, 20f)), color = V.cream, textAlign = TextAlign.Center,
        modifier = modifier.alpha(a).border(2.dp, V.magenta).background(V.panel).padding(horizontal = 26.dp, vertical = 12.dp))
}

/**
 * Quality as a radio-station dial: each rendition is a station; ←/→ moves along the band, the
 * focused station shows its details in the centre display, OK locks it in.
 */
@UnstableApi
@Composable
private fun RadioDial(s: PlayerSession, onDone: () -> Unit) {
    val c = s.controller
    data class Station(val name: String, val sub: String, val choice: QualityChoice?, val active: Boolean)
    val stations = listOf(Station("АВТО", c.current?.let { "сейчас ${it.label}" } ?: "по сети", null, c.userChoice == null)) +
        c.variants.map { v ->
            Station(v.label.uppercase(), "${v.codecLabel} · ${"%.1f".format(v.bandwidth / 1_000_000.0)} Мбит/с" + if (v.isSource) " · источник" else "",
                QualityChoice(v.height, v.fpsRounded, v.isHevc),
                c.userChoice?.let { it.height == v.height && it.fps == v.fpsRounded && it.hevc == v.isHevc } == true)
        }
    var focusedIdx by remember { mutableIntStateOf(stations.indexOfFirst { it.active }.coerceAtLeast(0)) }
    val fr = remember { FocusRequester() }
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, V.night.copy(alpha = 0.92f), V.night)))
        .padding(top = 40.dp, bottom = 34.dp).focusProperties { exit = { FocusRequester.Cancel } }.focusGroup(),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("РАДИО КАЧЕСТВА", style = VType.tag, color = V.teal)
        val st = stations.getOrNull(focusedIdx)
        Text(st?.name ?: "—", style = VType.mega.copy(shadow = VType.neon(V.magenta, 30f)), color = V.cream)
        Text(st?.sub ?: "", style = VType.body, color = V.dim)
        Text(connectionLine(c).uppercase(), style = VType.tag, color = V.faint, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(16.dp))
        // The band: a thin dusk line the stations sit on.
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth(0.8f).height(2.dp).background(V.dusky))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(horizontal = 80.dp, vertical = 14.dp)) {
                itemsIndexed(stations) { i, stn ->
                    Surface(
                        onClick = { s.selectQuality(stn.choice); onDone() },
                        modifier = Modifier.size(92.dp).then(if (i == focusedIdx) Modifier.focusRequester(fr) else Modifier)
                            .onFocusChanged { if (it.isFocused) focusedIdx = i },
                        shape = ClickableSurfaceDefaults.shape(CircleShape),
                        colors = ClickableSurfaceDefaults.colors(containerColor = if (stn.active) V.teal else V.night, contentColor = if (stn.active) V.night else V.cream,
                            focusedContainerColor = V.magenta, focusedContentColor = V.night),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.22f),
                        border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(2.dp, V.teal), shape = CircleShape),
                            focusedBorder = Border(BorderStroke(3.dp, V.cream), shape = CircleShape)),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stn.name.substringBefore(" ").replace("P60", "").replace("P30", ""), style = VType.row.copy(fontSize = 18.sp), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        proxyRetryLabel(c)?.let { label -> MenuItem(label, false, Modifier.padding(top = 6.dp), VType.row) { c.retryViaReyohoho() } }
        Text("←  →  НАСТРОИТЬ   ·   OK  ВЫБРАТЬ   ·   НАЗАД  ЗАКРЫТЬ", style = VType.tag, color = V.faint, modifier = Modifier.padding(top = 10.dp))
    }
    LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
}

/** A phone handset frame for contacts and chat. */
@Composable
private fun Phone(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.width(340.dp).fillMaxHeight(0.86f).clip(RoundedCornerShape(30.dp)).background(V.night)
        .border(3.dp, V.plum, RoundedCornerShape(30.dp)).padding(10.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(70.dp).height(5.dp).background(V.plum, CircleShape))
        Column(Modifier.padding(top = 10.dp).fillMaxSize().clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(V.dusk, V.night))).padding(14.dp)) {
            Text(title, style = VType.tag, color = V.teal, modifier = Modifier.padding(bottom = 10.dp))
            content()
        }
    }
}

@Composable
private fun Contacts(list: List<StreamItem>, current: StreamItem, onPick: (StreamItem) -> Unit) {
    val fr = remember { FocusRequester() }
    val idx = list.indexOfFirst { it.login == current.login }.coerceAtLeast(0)
    if (list.isEmpty()) Text("Нет контактов в эфире", style = VType.body, color = V.dim)
    LazyColumn(state = rememberLazyListState(idx), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(list, key = { it.login }) { st ->
            Surface(
                onClick = { onPick(st) }, modifier = Modifier.fillMaxWidth().then(if (st.login == current.login) Modifier.focusRequester(fr) else Modifier),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = if (st.login == current.login) V.plum else Color.Transparent, contentColor = V.cream,
                    focusedContainerColor = V.teal, focusedContentColor = V.night),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(st.avatar, null, Modifier.size(40.dp).clip(CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(st.displayName, style = VType.row.copy(fontSize = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${formatViewers(st.viewers)} · ${st.game}", style = VType.small, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(100); runCatching { fr.requestFocus() } }
}

@Composable
private fun PhoneChat(chat: ChatClient) {
    val state = rememberLazyListState()
    val lines = chat.lines
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) state.scrollToItem(lines.lastIndex) }
    chat.state.value.takeIf { it.isNotEmpty() }?.let { Text(it, style = VType.small, color = V.dim) }
    LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(6.dp), userScrollEnabled = false) {
        items(lines, key = { it.id }) { l ->
            Box(Modifier.background(V.plum.copy(alpha = 0.7f), RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 14.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)) {
                ChatLineText(l, VType.small.copy(fontSize = 14.sp), V.cream, V.teal, 20.sp, nameColor = { readableName(it, V.teal) })
            }
        }
    }
}
