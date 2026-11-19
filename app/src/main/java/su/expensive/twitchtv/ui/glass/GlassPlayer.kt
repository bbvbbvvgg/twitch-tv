package su.expensive.twitchtv.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
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

private enum class GOverlay { NONE, QUALITY, CHANNELS }

/**
 * Glass player: nothing on screen but the stream until a key is pressed. Then an info capsule
 * floats down from the top-left and an orb dock rises from the bottom; the focused orb names
 * itself in a bubble above the dock. Quality is a floating glass column on the right, channels a
 * filmstrip of rounded tiles. Panels are flat translucent fills — no blur over video.
 */
@UnstableApi
@Composable
fun GlassPlayer(app: AppState, s: PlayerSession) {
    val ctx = LocalContext.current
    var ui by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(GOverlay.NONE) }
    var lastKey by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var hint by remember { mutableStateOf("") }
    val root = remember { FocusRequester() }
    val dockFirst = remember { FocusRequester() }

    LaunchedEffect(Unit) { root.requestFocus() }
    LaunchedEffect(ui, lastKey, overlay) { if (ui && overlay == GOverlay.NONE) { delay(7_000); ui = false; root.requestFocus() } }
    LaunchedEffect(ui) { if (ui) { delay(60); runCatching { dockFirst.requestFocus() } } }
    ToastTimer(s)
    BackHandler(enabled = ui || overlay != GOverlay.NONE) {
        if (overlay != GOverlay.NONE) { overlay = GOverlay.NONE; runCatching { dockFirst.requestFocus() } } else { ui = false; root.requestFocus() }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .playerKeys(s, { ui || overlay != GOverlay.NONE }, onReveal = { ui = true }, onAnyKey = { lastKey = System.currentTimeMillis() })
            .focusRequester(root).focusable(),
    ) {
        VideoSurface(s)

        statusMessage(s)?.let { StatusCapsule(it, Modifier.align(Alignment.Center)) }
        if (s.controller.adState.isNotEmpty()) Text(s.controller.adState, style = GType.small, color = G.text,
            modifier = Modifier.align(Alignment.TopEnd).padding(28.dp).glass(CircleShape).padding(horizontal = 16.dp, vertical = 8.dp))
        AnimatedVisibility(s.toast != null, Modifier.align(Alignment.TopCenter), enter = fadeIn() + slideInVertically { -it }, exit = fadeOut()) {
            Text(s.toast ?: "", style = GType.label, color = G.text,
                modifier = Modifier.padding(top = 28.dp).glass(CircleShape, 1.6f).padding(horizontal = 24.dp, vertical = 12.dp))
        }
        if (s.chatOn) GlassChat(s.chat, Modifier.align(Alignment.CenterEnd).padding(28.dp).fillMaxHeight().width(360.dp))
        if (s.diagOn) Text(s.diagText(), color = Color(0xFFD7F5FF), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(if (s.chatOn) Alignment.BottomStart else Alignment.TopEnd).padding(28.dp)
                .glass(RoundedCornerShape(20.dp)).background(Color(0x66000000), RoundedCornerShape(20.dp)).padding(14.dp))

        // Info capsule (top-left)
        AnimatedVisibility(ui, Modifier.align(Alignment.TopStart),
            enter = fadeIn(tween(260)) + slideInVertically(spring(0.8f, Spring.StiffnessLow)) { -it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(220)) { -it / 3 }) {
            InfoCapsule(s, Modifier.padding(28.dp))
        }
        // Orb dock (bottom-centre)
        AnimatedVisibility(ui && overlay == GOverlay.NONE, Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(260)) + slideInVertically(spring(0.75f, Spring.StiffnessLow)) { it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(220)) { it / 3 }) {
            Column(Modifier.padding(bottom = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(hint, style = GType.label, color = G.text, modifier = Modifier.alpha(if (hint.isEmpty()) 0f else 1f)
                    .glass(CircleShape, 1.4f).padding(horizontal = 18.dp, vertical = 8.dp))
                Spacer(Modifier.height(14.dp))
                Row(Modifier.glass(CircleShape, 1.2f).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    fun Modifier.named(n: String) = onFocusChanged { if (it.isFocused) hint = n }
                    GlassOrb(Icons.Rounded.HighQuality, "Качество", Modifier.focusRequester(dockFirst).named("Качество · ${s.qualityLabel}")) { overlay = GOverlay.QUALITY }
                    GlassOrb(Icons.Rounded.ViewCarousel, "Каналы", Modifier.named("Другие трансляции")) { overlay = GOverlay.CHANNELS }
                    GlassOrb(Icons.AutoMirrored.Rounded.Chat, "Чат", Modifier.named(if (s.chatOn) "Скрыть чат" else "Показать чат"), active = s.chatOn) { s.toggleChat() }
                    GlassOrb(if (s.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline, "Избранное",
                        Modifier.named(if (s.isFavorite) "Убрать из избранного" else "В избранное"), active = s.isFavorite) { s.toggleFavorite() }
                    GlassOrb(Icons.Rounded.FiberManualRecord, "К эфиру", Modifier.named("Вернуться к прямому эфиру")) { s.resumeLive() }
                    GlassOrb(Icons.Rounded.Insights, "Диагностика", Modifier.named(if (s.diagOn) "Скрыть диагностику" else "Диагностика"), active = s.diagOn) { s.toggleDiag() }
                    if (s.diagOn) GlassOrb(Icons.Rounded.SaveAlt, "Экспорт", Modifier.named("Экспорт лога без токенов")) { s.exportLog(ctx) }
                }
            }
        }
        AnimatedVisibility(overlay == GOverlay.QUALITY, Modifier.align(Alignment.CenterEnd),
            enter = fadeIn() + slideInHorizontally(spring(0.8f, Spring.StiffnessMediumLow)) { it / 2 }, exit = fadeOut() + slideOutHorizontally { it / 3 }) {
            QualityColumn(s) { overlay = GOverlay.NONE; ui = false; root.requestFocus() }
        }
        AnimatedVisibility(overlay == GOverlay.CHANNELS, Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically(spring(0.8f, Spring.StiffnessMediumLow)) { it / 2 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
            Filmstrip(s.list, s.item) { overlay = GOverlay.NONE; ui = false; s.switchTo(it); root.requestFocus() }
        }
    }
}

@UnstableApi
@Composable
private fun InfoCapsule(s: PlayerSession, modifier: Modifier) {
    val it = s.item
    Row(modifier.widthIn(max = 760.dp).glass(RoundedCornerShape(30.dp), 1.3f).background(Color(0x4D000000), RoundedCornerShape(30.dp))
        .padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (it.avatar.isNotEmpty()) AsyncImage(it.avatar, null, Modifier.size(52.dp).clip(CircleShape))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(it.displayName, style = GType.title, color = G.text, maxLines = 1)
            Text(it.title, style = GType.small, color = G.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(18.dp))
        LivePill(formatViewers(it.viewers))
        Spacer(Modifier.width(10.dp))
        Text(s.qualityLabel, style = GType.small, color = G.onSolid, maxLines = 1,
            modifier = Modifier.background(G.solid, CircleShape).padding(horizontal = 12.dp, vertical = 5.dp))
    }
}

@Composable
private fun StatusCapsule(text: String, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "st")
    val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot")
    Row(modifier.glass(CircleShape, 1.6f).background(Color(0x66000000), CircleShape).padding(horizontal = 26.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).alpha(a).background(G.tint, CircleShape))
        Spacer(Modifier.width(14.dp))
        Text(text, style = GType.label, color = G.text)
    }
}

/** Quality picker: a floating glass column; the active rendition carries a check, focus lifts a capsule. */
@UnstableApi
@Composable
private fun QualityColumn(s: PlayerSession, onDone: () -> Unit) {
    val c = s.controller
    val fr = remember { FocusRequester() }
    Column(Modifier.padding(28.dp).fillMaxHeight().width(400.dp).glass(RoundedCornerShape(36.dp), 1.5f)
        .background(Color(0x59000000), RoundedCornerShape(36.dp)).padding(22.dp)
        .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()) {
        Text("Качество", style = GType.title, color = G.text)
        Text("Сейчас: ${s.qualityLabel}", style = GType.small, color = G.dim, modifier = Modifier.padding(top = 4.dp))
        Text(connectionLine(c), style = GType.small, color = G.faint, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
            item {
                QualityRow("Авто", c.current?.let { "сейчас ${it.label}" } ?: "по сети", c.userChoice == null, Modifier.focusRequester(fr)) { s.selectQuality(null); onDone() }
            }
            items(c.variants) { v ->
                val sel = c.userChoice?.let { it.height == v.height && it.fps == v.fpsRounded && it.hevc == v.isHevc } == true
                QualityRow(v.label, "${v.codecLabel} · ${"%.1f".format(v.bandwidth / 1_000_000.0)} Мбит/с" + if (v.isSource) " · источник" else "", sel) {
                    s.selectQuality(QualityChoice(v.height, v.fpsRounded, v.isHevc)); onDone()
                }
            }
            proxyRetryLabel(c)?.let { label ->
                item { QualityRow(label, "разово для этого стрима, текущий поток не прервётся", false) { c.retryViaReyohoho() } }
            }
        }
    }
    LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
}

@Composable
private fun QualityRow(title: String, sub: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth(), shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = if (selected) G.fillHi else G.fill, contentColor = G.text,
            focusedContainerColor = G.solid, focusedContentColor = G.onSolid),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)), shape = shape)),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(G.glow.copy(alpha = 0.5f), 14.dp)),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = GType.title.copy(fontSize = 20.sp))
                Text(sub, style = GType.small, color = androidx.tv.material3.LocalContentColor.current.copy(alpha = 0.65f))
            }
            if (selected) Icon(Icons.Rounded.Check, "Выбрано", Modifier.size(24.dp))
        }
    }
}

@Composable
private fun Filmstrip(list: List<StreamItem>, current: StreamItem, onPick: (StreamItem) -> Unit) {
    val ctx = LocalContext.current
    val fr = remember { FocusRequester() }
    val idx = list.indexOfFirst { it.login == current.login }.coerceAtLeast(0)
    Column(Modifier.padding(28.dp).fillMaxWidth().glass(RoundedCornerShape(36.dp), 1.4f)
        .background(Color(0x59000000), RoundedCornerShape(36.dp)).padding(vertical = 20.dp)
        .focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()) {
        Text("Другие трансляции", style = GType.title, color = G.text, modifier = Modifier.padding(start = 28.dp, bottom = 10.dp))
        if (list.isEmpty()) Text("Список пуст", style = GType.body, color = G.dim, modifier = Modifier.padding(start = 28.dp))
        LazyRow(state = rememberLazyListState(idx), horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)) {
            items(list, key = { it.login }) { st ->
                val shape = RoundedCornerShape(22.dp)
                Column(Modifier.width(220.dp)) {
                    Surface(
                        onClick = { onPick(st) },
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).then(if (st.login == current.login) Modifier.focusRequester(fr) else Modifier),
                        shape = ClickableSurfaceDefaults.shape(shape),
                        colors = ClickableSurfaceDefaults.colors(containerColor = G.fill, focusedContainerColor = G.fill),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(BorderStroke(if (st.login == current.login) 2.dp else 1.dp, Color.White.copy(alpha = if (st.login == current.login) 0.7f else 0.15f)), shape = shape),
                            focusedBorder = Border(BorderStroke(2.5.dp, Color.White), shape = shape)),
                        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(G.glow.copy(alpha = 0.6f), 18.dp)),
                    ) {
                        AsyncImage(ImageRequest.Builder(ctx).data(st.thumbnail.ifEmpty { st.avatar }).size(440, 248).build(), null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Text(st.displayName, style = GType.label, color = G.text, maxLines = 1, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                }
            }
        }
    }
    LaunchedEffect(Unit) { delay(100); runCatching { fr.requestFocus() } }
}

@Composable
private fun GlassChat(chat: ChatClient, modifier: Modifier) {
    val state = rememberLazyListState()
    val lines = chat.lines
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) state.scrollToItem(lines.lastIndex) }
    Column(modifier.glass(RoundedCornerShape(30.dp), 1.2f).background(Color(0x73000000), RoundedCornerShape(30.dp)).padding(16.dp)) {
        Text("Чат", style = GType.label, color = G.text, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        chat.state.value.takeIf { it.isNotEmpty() }?.let { Text(it, style = GType.small, color = G.dim, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)) }
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(6.dp), userScrollEnabled = false) {
            items(lines, key = { it.id }) { line ->
                ChatLineText(line, GType.small.copy(fontSize = 15.sp), G.text, G.tint, 22.sp, nameColor = { readableName(it, G.tint) })
            }
        }
    }
}
