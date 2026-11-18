package su.expensive.twitchtv.ui

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

private enum class Overlay { NONE, QUALITY, STREAMS }

@UnstableApi
@Composable
fun PlayerScreen(prefs: Prefs, start: StreamItem, list: List<StreamItem>, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val controller = remember { PlaybackController(ctx.applicationContext, prefs) }
    val chat = remember { ChatClient(prefs.chatThirdPartyEmotes) }
    var item by remember { mutableStateOf(start) }
    var panel by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var chatOn by remember { mutableStateOf(prefs.chatEnabled) }
    var diagOn by remember { mutableStateOf(prefs.showDiagnostics) }
    var lastKey by remember { mutableStateOf(System.currentTimeMillis()) }
    var toast by remember { mutableStateOf<String?>(null) }
    var favVer by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }

    fun switchTo(s: StreamItem) {
        item = s
        prefs.addHistory(s.login)
        controller.open(s.login)
        if (chatOn) chat.connect(s.login)
        toast = s.displayName
    }
    fun step(delta: Int) {
        if (list.isEmpty()) return
        val idx = list.indexOfFirst { it.login == item.login }
        val next = list[((if (idx < 0) 0 else idx) + delta + list.size) % list.size]
        if (next.login != item.login) switchTo(next)
    }

    // Lifecycle: free decoders/network when the TV app goes to background, reopen at live edge on return.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        var stopped = false
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> { stopped = true; controller.player.stop(); chat.disconnect() }
                Lifecycle.Event.ON_START -> if (stopped) { stopped = false; controller.open(item.login); if (chatOn) chat.connect(item.login) }
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); chat.release(); controller.release() }
    }
    LaunchedEffect(Unit) {
        controller.open(start.login)
        if (chatOn) chat.connect(start.login)
        rootFocus.requestFocus()
    }
    // Auto-hide the control panel.
    LaunchedEffect(panel, lastKey, overlay) {
        if (panel && overlay == Overlay.NONE) { delay(8_000); panel = false; rootFocus.requestFocus() }
    }
    LaunchedEffect(toast) { if (toast != null) { delay(2_500); toast = null } }
    LaunchedEffect(panel) { if (panel) runCatching { delay(50); panelFocus.requestFocus() } }

    BackHandler(enabled = overlay != Overlay.NONE || panel) {
        if (overlay != Overlay.NONE) overlay = Overlay.NONE else panel = false
        rootFocus.requestFocus()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { ev ->
                lastKey = System.currentTimeMillis()
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val code = ev.key.nativeKeyCode
                when (code) {
                    AKeyEvent.KEYCODE_CHANNEL_UP -> { step(1); true }
                    AKeyEvent.KEYCODE_CHANNEL_DOWN -> { step(-1); true }
                    AKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, AKeyEvent.KEYCODE_MEDIA_PLAY -> { controller.resumeLive(); true }
                    else -> if (!panel && overlay == Overlay.NONE && code in OPEN_KEYS) { panel = true; true } else false
                }
            }
            .focusRequester(rootFocus).focusable(),
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    keepScreenOn = true
                    isFocusable = false
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    player = controller.player
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        // Status (loading / reconnect / offline)
        val st = controller.status
        if (st != PlayStatus.PLAYING && st != PlayStatus.IDLE) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    when (st) { PlayStatus.OFFLINE -> "${item.displayName}: трансляция не идёт"; else -> controller.statusText.ifEmpty { "Загрузка…" } },
                    color = Color.White, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.background(Color(0xAA000000), RoundedCornerShape(8.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
        if (controller.adState.isNotEmpty()) {
            Text(controller.adState, color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        toast?.let {
            Text(it, color = Color.White, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.TopCenter).padding(24.dp).background(Color(0xAA000000), RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
        }
        if (diagOn) DiagnosticsBox(controller, Modifier.align(Alignment.TopStart).padding(16.dp))
        if (chatOn) ChatPanel(chat, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(340.dp))

        if (panel) {
            ControlPanel(
                controller = controller, item = item, prefs = prefs, chatOn = chatOn, diagOn = diagOn, favVer = favVer,
                firstFocus = panelFocus,
                onQuality = { overlay = Overlay.QUALITY },
                onStreams = { overlay = Overlay.STREAMS },
                onChat = { chatOn = !chatOn; if (chatOn) chat.connect(item.login) else chat.disconnect() },
                onDiag = { diagOn = !diagOn },
                onFav = { val now = prefs.toggleFavorite(item.login); HomeCache.data.remove(0); favVer++; toast = if (now) "Добавлено в избранное" else "Убрано из избранного" },
                onLive = { controller.resumeLive() },
                onExport = { toast = exportLogs(ctx, prefs, controller.diag.asText()) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        when (overlay) {
            Overlay.QUALITY -> QualityPicker(controller, Modifier.align(Alignment.CenterEnd)) { overlay = Overlay.NONE; panel = false; rootFocus.requestFocus() }
            Overlay.STREAMS -> StreamPicker(list, item, Modifier.align(Alignment.BottomCenter)) {
                overlay = Overlay.NONE; panel = false; switchTo(it); rootFocus.requestFocus()
            }
            Overlay.NONE -> {}
        }
    }
}

private val OPEN_KEYS = setOf(
    AKeyEvent.KEYCODE_DPAD_CENTER, AKeyEvent.KEYCODE_ENTER, AKeyEvent.KEYCODE_NUMPAD_ENTER, AKeyEvent.KEYCODE_MENU,
    AKeyEvent.KEYCODE_DPAD_UP, AKeyEvent.KEYCODE_DPAD_DOWN, AKeyEvent.KEYCODE_DPAD_LEFT, AKeyEvent.KEYCODE_DPAD_RIGHT, AKeyEvent.KEYCODE_INFO,
)

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
private fun DiagnosticsBox(c: PlaybackController, modifier: Modifier) {
    val d = c.diag
    // Values are read from Compose state updated once per second by the controller's watchdog.
    val text = d.asText() + (c.current?.let { "Вариант: ${it.label} ${it.codecLabel} (${it.bandwidth / 1000} кбит/с)\n" } ?: "") +
        (c.autoCapLabel?.let { "Авто ограничило до: $it\n" } ?: "")
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
