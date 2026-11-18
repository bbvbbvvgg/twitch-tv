package su.expensive.twitchtv.ui.classic

import android.view.KeyEvent as AKeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import su.expensive.twitchtv.chat.ChatClient
import su.expensive.twitchtv.chat.Part
import su.expensive.twitchtv.data.Prefs
import su.expensive.twitchtv.data.StreamItem
import su.expensive.twitchtv.player.PlayStatus
import su.expensive.twitchtv.player.PlaybackController
import su.expensive.twitchtv.player.QualityChoice
import su.expensive.twitchtv.ui.core.PlayerSession
import su.expensive.twitchtv.ui.core.ToastTimer
import su.expensive.twitchtv.ui.core.VideoSurface
import su.expensive.twitchtv.ui.core.formatViewers
import su.expensive.twitchtv.ui.core.playerKeys
import su.expensive.twitchtv.ui.core.statusMessage

private enum class Overlay { NONE, QUALITY, STREAMS }

@UnstableApi
@Composable
fun PlayerScreen(session: PlayerSession) {
    val ctx = LocalContext.current
    val s = session
    val controller = s.controller
    val chat = s.chat
    val item = s.item
    val list = s.list
    var panel by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var lastKey by remember { mutableStateOf(System.currentTimeMillis()) }
    val rootFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { rootFocus.requestFocus() }
    // Auto-hide the control panel.
    LaunchedEffect(panel, lastKey, overlay) {
        if (panel && overlay == Overlay.NONE) { delay(8_000); panel = false; rootFocus.requestFocus() }
    }
    ToastTimer(s)
    LaunchedEffect(panel) { if (panel) runCatching { delay(50); panelFocus.requestFocus() } }

    BackHandler(enabled = overlay != Overlay.NONE || panel) {
        if (overlay != Overlay.NONE) overlay = Overlay.NONE else panel = false
        rootFocus.requestFocus()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .playerKeys(s, controlsVisible = { panel || overlay != Overlay.NONE }, onReveal = { panel = true },
                onAnyKey = { lastKey = System.currentTimeMillis() })
            .focusRequester(rootFocus).focusable(),
    ) {
        VideoSurface(s, poster = false)

        // Status (loading / reconnect / offline)
        statusMessage(s)?.let { msg ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    msg,
                    color = Color.White, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.background(Color(0xAA000000), RoundedCornerShape(8.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
        if (controller.adState.isNotEmpty()) {
            Text(controller.adState, color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        s.toast?.let {
            Text(it, color = Color.White, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.TopCenter).padding(24.dp).background(Color(0xAA000000), RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
        }
        if (s.diagOn) DiagnosticsBox(s, Modifier.align(Alignment.TopStart).padding(16.dp))
        if (s.chatOn) ChatPanel(chat, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(340.dp))

        if (panel) {
            ControlPanel(
                controller = controller, item = item, prefs = s.prefs, chatOn = s.chatOn, diagOn = s.diagOn, favVer = s.favVersion,
                firstFocus = panelFocus,
                onQuality = { overlay = Overlay.QUALITY },
                onStreams = { overlay = Overlay.STREAMS },
                onChat = { s.toggleChat() },
                onDiag = { s.toggleDiag() },
                onFav = { s.toggleFavorite() },
                onLive = { s.resumeLive() },
                onExport = { s.exportLog(ctx) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        when (overlay) {
            Overlay.QUALITY -> QualityPicker(controller, Modifier.align(Alignment.CenterEnd)) { overlay = Overlay.NONE; panel = false; rootFocus.requestFocus() }
            Overlay.STREAMS -> StreamPicker(list, item, Modifier.align(Alignment.BottomCenter)) {
                overlay = Overlay.NONE; panel = false; s.switchTo(it); rootFocus.requestFocus()
            }
            Overlay.NONE -> {}
        }
    }
}

@UnstableApi
@Composable
private fun ControlPanel(
    controller: PlaybackController, item: StreamItem, prefs: Prefs, chatOn: Boolean, diagOn: Boolean, favVer: Int,
    firstFocus: FocusRequester,
    onQuality: () -> Unit, onStreams: () -> Unit, onChat: () -> Unit, onDiag: () -> Unit, onFav: () -> Unit,
    onLive: () -> Unit, onExport: () -> Unit, modifier: Modifier,
) {
    Column(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
            .padding(start = 48.dp, end = 48.dp, top = 60.dp, bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.avatar.isNotEmpty()) AsyncImage(item.avatar, null, Modifier.size(44.dp).background(Color.DarkGray, RoundedCornerShape(22.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title.ifEmpty { item.displayName }, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.displayName}  ·  ${item.game}  ·  ${formatViewers(item.viewers)} зрителей", style = MaterialTheme.typography.bodyMedium, color = Palette.dim, maxLines = 1)
            }
            val cur = controller.current
            val q = when {
                cur == null -> "—"
                controller.userChoice == null -> "Авто · ${cur.label} ${cur.codecLabel}"
                else -> "${cur.label} ${cur.codecLabel}"
            }
            Text(q, color = Color.White, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.background(Palette.accent, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PanelButton("Качество", Modifier.focusRequester(firstFocus), onQuality)
            PanelButton("Стримы", onClick = onStreams)
            PanelButton(if (chatOn) "Чат: вкл" else "Чат: выкл", onClick = onChat)
            PanelButton(if (favVer >= 0 && prefs.isFavorite(item.login)) "★ В избранном" else "☆ В избранное", onClick = onFav)
            PanelButton("К эфиру", onClick = onLive)
            PanelButton(if (diagOn) "Диагностика: вкл" else "Диагностика", onClick = onDiag)
            if (diagOn) PanelButton("Экспорт лога", onClick = onExport)
        }
    }
}

@Composable
private fun PanelButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        colors = ButtonDefaults.colors(containerColor = Color(0x33FFFFFF), contentColor = Color.White, focusedContainerColor = Color.White, focusedContentColor = Color.Black)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@UnstableApi
@Composable
private fun QualityPicker(controller: PlaybackController, modifier: Modifier, onDone: () -> Unit) {
    val fr = remember { FocusRequester() }
    Column(modifier.fillMaxHeight().width(360.dp).background(Color(0xF0101014)).padding(24.dp)) {
        Text("Качество", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                val cur = controller.current
                PickRow("Авто" + (cur?.let { " (сейчас ${it.label})" } ?: ""), controller.userChoice == null, Modifier.focusRequester(fr)) {
                    controller.selectQuality(null); onDone()
                }
            }
            items(controller.variants) { v ->
                val sel = controller.userChoice?.let { it.height == v.height && it.fps == v.fpsRounded && it.hevc == v.isHevc } == true
                PickRow("${v.label}  ${v.codecLabel}  ${"%.1f".format(v.bandwidth / 1_000_000.0)} Мбит/с" + if (v.isSource) "  · источник" else "", sel) {
                    controller.selectQuality(QualityChoice(v.height, v.fpsRounded, v.isHevc)); onDone()
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { delay(50); fr.requestFocus() } }
}

@Composable
private fun PickRow(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ButtonDefaults.colors(containerColor = if (selected) Palette.accent else Color(0x22FFFFFF), contentColor = Color.White,
            focusedContainerColor = Color.White, focusedContentColor = Color.Black)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StreamPicker(list: List<StreamItem>, current: StreamItem, modifier: Modifier, onPick: (StreamItem) -> Unit) {
    val fr = remember { FocusRequester() }
    val idx = list.indexOfFirst { it.login == current.login }.coerceAtLeast(0)
    val state = rememberLazyListState(idx)
    Column(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0000000)))).padding(top = 40.dp, bottom = 28.dp)) {
        Text("Переключить трансляцию", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        if (list.isEmpty()) Text("Список пуст", color = Palette.dim, modifier = Modifier.padding(start = 48.dp))
        LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 48.dp)) {
            items(list, key = { it.login }) { s ->
                Box(Modifier.width(260.dp).then(if (s.login == current.login) Modifier.focusRequester(fr) else Modifier)) {
                    StreamCard(s, false, onClick = { onPick(s) }, onLongClick = {})
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { delay(80); fr.requestFocus() } }
}

@UnstableApi
@Composable
private fun DiagnosticsBox(s: PlayerSession, modifier: Modifier) {
    // Values are read from Compose state updated once per second by the controller's watchdog.
    val text = s.diagText()
    Text(text, color = Color(0xFFB9F6CA), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
        modifier = modifier.background(Color(0xB3000000), RoundedCornerShape(6.dp)).padding(10.dp))
}

@Composable
private fun ChatPanel(chat: ChatClient, modifier: Modifier) {
    val state = rememberLazyListState()
    val lines = chat.lines
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) state.scrollToItem(lines.lastIndex) }
    Column(modifier.background(Color(0x99000000)).padding(10.dp)) {
        chat.state.value.takeIf { it.isNotEmpty() }?.let { Text(it, color = Palette.dim, fontSize = 12.sp) }
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(3.dp), userScrollEnabled = false) {
            items(lines, key = { it.id }) { line ->
                val inline = HashMap<String, InlineTextContent>()
                val text = buildAnnotatedString {
                    withStyle(SpanStyle(color = line.color?.let { Color(it) } ?: Palette.accent, fontWeight = FontWeight.Bold)) { append(line.user) }
                    append(": ")
                    line.parts.forEachIndexed { i, p ->
                        when (p) {
                            is Part.Text -> append(p.text)
                            is Part.Emote -> {
                                val id = "e$i"
                                appendInlineContent(id, p.name)
                                inline[id] = InlineTextContent(Placeholder(22.sp, 22.sp, PlaceholderVerticalAlign.Center)) {
                                    AsyncImage(p.url, p.name, Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
                Text(text, inlineContent = inline, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}
