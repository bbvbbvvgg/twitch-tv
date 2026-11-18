package su.expensive.twitchtv.ui.gtv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import su.expensive.twitchtv.chat.ChatClient
import su.expensive.twitchtv.player.QualityChoice
import su.expensive.twitchtv.ui.core.AppState
import su.expensive.twitchtv.ui.core.ChatLineText
import su.expensive.twitchtv.ui.core.PlayerSession
import su.expensive.twitchtv.ui.core.ToastTimer
import su.expensive.twitchtv.ui.core.VideoSurface
import su.expensive.twitchtv.ui.core.formatViewers
import su.expensive.twitchtv.ui.core.playerKeys
import su.expensive.twitchtv.ui.core.readableName
import su.expensive.twitchtv.ui.core.statusMessage

/**
 * Google TV player: the familiar transport layout — title top-left, a live bar, a row of labelled
 * actions and, below it, the next channels as cards. Chat does not float over the picture: the
 * video steps aside and the chat takes a side column. Quality and stats open as a right-hand
 * system side panel.
 */
@UnstableApi
@Composable
fun GtvPlayer(app: AppState, s: PlayerSession) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var ui by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(false) }
    var lastKey by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val root = remember { FocusRequester() }
    val firstAction = remember { FocusRequester() }
    val rowFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { root.requestFocus() }
    LaunchedEffect(ui, lastKey, panel) { if (ui && !panel) { delay(8_000); ui = false; root.requestFocus() } }
    LaunchedEffect(ui) { if (ui) { delay(60); runCatching { firstAction.requestFocus() } } }
    ToastTimer(s)
    BackHandler(enabled = ui || panel) {
        if (panel) { panel = false; runCatching { firstAction.requestFocus() } } else { ui = false; root.requestFocus() }
    }

    Row(
        Modifier.fillMaxSize().background(Color.Black)
            .playerKeys(s, { ui || panel }, onReveal = { ui = true }, onAnyKey = { lastKey = System.currentTimeMillis() })
            .focusRequester(root).focusable(),
    ) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            VideoSurface(s)
            statusMessage(s)?.let {
                Text(it, style = TType.headline, color = T.text, modifier = Modifier.align(if (ui) Alignment.TopCenter else Alignment.Center).padding(top = if (ui) 150.dp else 0.dp)
                    .background(Color(0xCC202124), RoundedCornerShape(12.dp)).padding(horizontal = 24.dp, vertical = 14.dp))
            }
            if (s.controller.adState.isNotEmpty()) Text(s.controller.adState, style = TType.caption, color = T.text,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).background(Color(0xCC202124), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
            if (s.diagOn) Column(Modifier.align(Alignment.TopEnd).padding(top = 56.dp, end = 20.dp).width(380.dp)
                .background(Color(0xE6202124), RoundedCornerShape(12.dp)).padding(16.dp)) {
                Text("Статистика", style = TType.label, color = T.text, modifier = Modifier.padding(bottom = 8.dp))
                Text(s.diagText(), color = T.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
            }

            androidx.compose.animation.AnimatedVisibility(ui, enter = fadeIn(tween(220)), exit = fadeOut(tween(220))) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x99000000), 0.25f to Color.Transparent,
                    0.45f to Color.Transparent, 1f to Color(0xF0000000))))
            }
            androidx.compose.animation.AnimatedVisibility(ui, Modifier.align(Alignment.TopStart), enter = fadeIn(tween(220)), exit = fadeOut(tween(220))) {
                Row(Modifier.padding(36.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (s.item.avatar.isNotEmpty()) AsyncImage(s.item.avatar, null, Modifier.size(48.dp).clip(CircleShape))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(s.item.displayName, style = TType.headline, color = T.text)
                        Text("${s.item.game} · ${formatViewers(s.item.viewers)} зрителей", style = TType.caption, color = T.dim)
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(ui && !panel, Modifier.align(Alignment.BottomStart),
                enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 6 }, exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 8 }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
                    Text(s.item.title, style = TType.rowTitle, color = T.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 36.dp))
                    LiveBar(s, Modifier.padding(horizontal = 36.dp, vertical = 14.dp))
                    Row(Modifier.padding(horizontal = 30.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Качество", Icons.Rounded.Tune, Modifier.focusRequester(firstAction)) { panel = true }
                        Pill(if (s.chatOn) "Скрыть чат" else "Чат", Icons.AutoMirrored.Rounded.Chat, selected = s.chatOn) { s.toggleChat() }
                        Pill("Каналы", Icons.Rounded.ViewCarousel) { runCatching { rowFocus.requestFocus() } }
                        Pill(if (s.isFavorite) "В избранном" else "В избранное", if (s.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline, selected = s.isFavorite) { s.toggleFavorite() }
                        Pill("К эфиру", Icons.Rounded.FastForward) { s.resumeLive() }
                        Pill("Статистика", Icons.Rounded.BarChart, selected = s.diagOn) { s.toggleDiag() }
                        if (s.diagOn) Pill("Экспорт", Icons.Rounded.SaveAlt) { s.exportLog(ctx) }
                    }
                    Text("Ещё в эфире", style = TType.label, color = T.dim, modifier = Modifier.padding(start = 36.dp, top = 18.dp))
                    val idx = s.list.indexOfFirst { it.login == s.item.login }.coerceAtLeast(0)
                    LazyRow(state = rememberLazyListState(idx), horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 36.dp, vertical = 10.dp)) {
                        items(s.list, key = { it.login }) { st ->
                            MediaCard(st, 176.dp, if (st.login == s.item.login) Modifier.focusRequester(rowFocus) else Modifier) {
                                ui = false; s.switchTo(st); root.requestFocus()
                            }
                        }
                    }
                }
            }
            // Snackbar-style toast, bottom-left.
            androidx.compose.animation.AnimatedVisibility(s.toast != null, Modifier.align(Alignment.BottomStart), enter = fadeIn() + slideInVertically { it }, exit = fadeOut()) {
                Text(s.toast ?: "", style = TType.body, color = T.onFocus, modifier = Modifier.padding(28.dp)
                    .background(T.focus, RoundedCornerShape(8.dp)).padding(horizontal = 20.dp, vertical = 14.dp))
            }
            androidx.compose.animation.AnimatedVisibility(panel, Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(tween(260)) { it } + fadeIn(), exit = slideOutHorizontally(tween(200)) { it } + fadeOut()) {
                QualityPanel(s) { panel = false; ui = false; root.requestFocus() }
            }
        }
        if (s.chatOn) GtvChat(s.chat, s.item.displayName, Modifier.width(380.dp).fillMaxHeight())
    }
}

/** Live position bar: always at the edge for live, with the real latency from the player. */
@UnstableApi
@Composable
private fun LiveBar(s: PlayerSession, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("В ЭФИРЕ", style = TType.caption.copy(fontWeight = FontWeight.Bold), color = Color.White,
            modifier = Modifier.background(T.live, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f).height(4.dp).background(T.live, CircleShape))
        Box(Modifier.size(14.dp).background(Color.White, CircleShape))
        Spacer(Modifier.width(14.dp))
        val lag = s.controller.diag.liveOffsetMs
        Text((lag?.let { "задержка %.1f с · ".format(it / 1000.0) } ?: "") + s.qualityLabel, style = TType.caption, color = T.dim)
    }
}

@UnstableApi
@Composable
private fun QualityPanel(s: PlayerSession, onDone: () -> Unit) {
    val c = s.controller
    val fr = remember { FocusRequester() }
    Column(Modifier.fillMaxHeight().width(420.dp).background(T.surface).padding(28.dp)
        .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()) {
        Text("Качество видео", style = TType.headline, color = T.text)
        Text(s.qualityLabel, style = TType.caption, color = T.dim, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item { RadioRow("Автоматически", c.current?.let { "Сейчас ${it.label}, по скорости сети" } ?: "По скорости сети", c.userChoice == null, Modifier.focusRequester(fr)) { s.selectQuality(null); onDone() } }
            items(c.variants) { v ->
                val sel = c.userChoice?.let { it.height == v.height && it.fps == v.fpsRounded && it.hevc == v.isHevc } == true
                RadioRow(v.label + if (v.isSource) " (источник)" else "", "${v.codecLabel} · ${"%.1f".format(v.bandwidth / 1_000_000.0)} Мбит/с", sel) {
                    s.selectQuality(QualityChoice(v.height, v.fpsRounded, v.isHevc)); onDone()
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
}

@Composable
private fun RadioRow(title: String, sub: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = T.text,
            focusedContainerColor = T.focus, focusedContentColor = T.onFocus),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (on) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column { Text(title, style = TType.label.copy(fontSize = 17.sp)); Text(sub, style = TType.caption, color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.7f)) }
        }
    }
}

@Composable
private fun GtvChat(chat: ChatClient, channel: String, modifier: Modifier) {
    val state = rememberLazyListState()
    val lines = chat.lines
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) state.scrollToItem(lines.lastIndex) }
    Column(modifier.background(T.surface).padding(horizontal = 18.dp, vertical = 20.dp)) {
        Text("Чат · $channel", style = TType.label, color = T.text)
        Text(chat.state.value.ifEmpty { "Сообщения в реальном времени" }, style = TType.caption, color = T.faint, modifier = Modifier.padding(bottom = 12.dp))
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) {
            items(lines, key = { it.id }) { l ->
                ChatLineText(l, TType.body.copy(fontSize = 15.sp, lineHeight = 20.sp), T.text, T.accent, 22.sp, nameColor = { readableName(it, T.accent) })
            }
        }
    }
}
