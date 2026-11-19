package su.expensive.twitchtv.ui.vice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import su.expensive.twitchtv.data.StreamItem
import su.expensive.twitchtv.ui.core.AppState
import su.expensive.twitchtv.ui.core.Load
import su.expensive.twitchtv.ui.core.StreamRepo
import su.expensive.twitchtv.ui.core.StreamsHolder
import su.expensive.twitchtv.ui.core.formatViewers
import su.expensive.twitchtv.ui.core.rememberStreams

/**
 * Vice home = a game pause menu. Left: the "VICE" wordmark and a vertical menu that slides and
 * lights up. Right: the section as a numbered mission list beside a tilted postcard of the focused
 * stream. No cards, no grid, no carousels. The Leonida (GTA VI) section raises a bigger sun and
 * its own headline.
 */
private enum class VSec(val label: String) { LIVE("В эфире"), LEONIDA("GTA VI · Леонида"), FAV("Избранное"), SEARCH("Поиск"), HIST("История"), SETTINGS("Настройки") }

private object ViceMemory { var lastLogin: String? = null }

@UnstableApi
@Composable
fun ViceApp(app: AppState) {
    ViceTheme {
        val s = app.session
        if (s == null) ViceHome(app) else {
            BackHandler { app.close() }
            VicePlayer(app, s)
        }
    }
    LaunchedEffect(Unit) { app.openSettingsOnce = false; app.openNetworkOnce = false }
}

@UnstableApi
@Composable
private fun ViceHome(app: AppState) {
    val prefs = app.prefs
    var sec by rememberSaveable { mutableStateOf(if (app.openSettingsOnce) VSec.SETTINGS else if (prefs.favorites().isNotEmpty()) VSec.FAV else VSec.LIVE) }
    val menuFocus = remember { VSec.entries.associateWith { FocusRequester() } }
    var contentFocused by remember { mutableStateOf(false) }
    var keyed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { menuFocus.getValue(sec).requestFocus() } }
    // Back inside a section returns to the menu first, like a game menu.
    BackHandler(enabled = contentFocused) { runCatching { menuFocus.getValue(sec).requestFocus() } }

    Box(Modifier.fillMaxSize().background(V.night).onPreviewKeyEvent { keyed = true; false }) {
        DuskBackdrop(sunScale = if (sec == VSec.LEONIDA) 1.7f else 1f, horizon = if (sec == VSec.LEONIDA) 0.58f else 0.66f)
        // Readability: the menu side sits on a deep violet wash.
        Box(Modifier.fillMaxHeight().width(420.dp).background(Brush.horizontalGradient(listOf(V.night.copy(alpha = 0.96f), V.night.copy(alpha = 0.6f), Color.Transparent))))
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(330.dp).fillMaxHeight().padding(start = 44.dp, top = 30.dp)) {
                Text("Vice", style = androidx.compose.ui.text.TextStyle(fontFamily = Script, fontSize = 64.sp, shadow = VType.neon(V.magenta, 30f)), color = V.cream)
                Text("TWITCH TV", style = VType.tag.copy(letterSpacing = 6.sp), color = V.teal, modifier = Modifier.padding(start = 6.dp, bottom = 22.dp))
                VSec.entries.forEach { s ->
                    var pending by remember { mutableStateOf(false) }
                    LaunchedEffect(pending) { if (pending) { delay(200); if (s != sec) sec = s; pending = false } }
                    MenuItem(s.label, s == sec, Modifier.focusRequester(menuFocus.getValue(s)).padding(vertical = 2.dp),
                        onFocus = { pending = it && keyed }) { sec = s }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().padding(top = 34.dp, end = 44.dp).onFocusChanged { contentFocused = it.hasFocus }) {
                AnimatedContent(sec, transitionSpec = {
                    (fadeIn(tween(260)) + slideInHorizontally(tween(320)) { it / 10 }) togetherWith (fadeOut(tween(140)) + slideOutHorizontally(tween(140)) { -it / 20 })
                }, label = "vsec") { s ->
                    when (s) {
                        VSec.LIVE -> Missions("В эфире сейчас", rememberStreams(StreamRepo.Key.POPULAR, prefs), app)
                        VSec.LEONIDA -> Missions("Леонида в эфире", rememberStreams(StreamRepo.Key.GTA, prefs), app, leonida = true)
                        VSec.FAV -> Missions("Свои люди", rememberStreams(StreamRepo.Key.FAVORITES, prefs), app,
                            empty = "Пока никого. Удерживайте OK на любом канале — он появится здесь.")
                        VSec.SEARCH -> ViceSearch(app)
                        VSec.HIST -> Missions("Недавно", rememberStreams(StreamRepo.Key.HISTORY, prefs), app, empty = "История пуста — откройте любую трансляцию.")
                        VSec.SETTINGS -> ViceSettings(app)
                    }
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun Missions(title: String, h: StreamsHolder, app: AppState, leonida: Boolean = false, empty: String = "Сейчас тихо") {
    Column(Modifier.fillMaxSize()) {
        if (leonida) {
            Text("ПРИВЕТ ИЗ", style = VType.tag, color = V.teal)
            NeonTitle("Леонида Live", V.magenta)
            Text(
                if (StreamRepo.gtaViewers > 0) "${StreamRepo.gtaTitle} · ${formatViewers(StreamRepo.gtaViewers)} зрителей прямо сейчас" else StreamRepo.gtaTitle,
                style = VType.body, color = V.cream, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp).background(V.night.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        } else NeonTitle(title, if (title == "Свои люди") V.teal else V.magenta, Modifier.padding(bottom = 14.dp), VType.mega.copy(fontSize = 44.sp))
        when (val st = h.state) {
            Load.Loading -> ViceLoading()
            is Load.Failed -> ViceSign("Нет сигнала", st.message, V.magenta, "Повторить") { h.reload() }
            is Load.Ready -> if (st.items.isEmpty()) ViceSign("Пусто", empty) else MissionBoard(st.items, app, leonida)
        }
    }
}

/** Numbered list + postcard. Focus on a row tilts a new postcard in. */
@UnstableApi
@Composable
private fun MissionBoard(items: List<StreamItem>, app: AppState, leonida: Boolean) {
    val start = items.indexOfFirst { it.login == ViceMemory.lastLogin }.coerceAtLeast(0)
    var focused by remember(items) { mutableStateOf(items[start]) }
    var favTick by remember { mutableIntStateOf(0) }
    Row(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(0.5f).fillMaxHeight().background(V.night.copy(alpha = 0.62f), SlantShape(0.012f)), state = rememberLazyListState(start),
            contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(items, key = { _, s -> s.login }) { i, s ->
                val fr = remember { FocusRequester() }
                MissionRow(i + 1, s, favTick >= 0 && app.prefs.isFavorite(s.login), Modifier.focusRequester(fr),
                    onFocus = { focused = s },
                    onLong = { app.prefs.toggleFavorite(s.login); StreamRepo.invalidate(StreamRepo.Key.FAVORITES); favTick++ }) {
                    if (s.live) { ViceMemory.lastLogin = s.login; app.open(s, items) }
                }
                LaunchedEffect(Unit) { if (ViceMemory.lastLogin == s.login) { runCatching { fr.requestFocus() }; ViceMemory.lastLogin = null } }
            }
        }
        Box(Modifier.weight(0.5f).fillMaxHeight().padding(start = 18.dp, top = 6.dp)) {
            AnimatedContent(focused, transitionSpec = {
                (fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 14 }) togetherWith fadeOut(tween(160))
            }, label = "postcard") { s -> Postcard(s, leonida) }
        }
    }
}

@Composable
private fun MissionRow(n: Int, s: StreamItem, fav: Boolean, modifier: Modifier, onFocus: () -> Unit, onLong: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick, onLongClick = onLong,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(SlantShape(0.12f)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = V.cream,
            focusedContainerColor = V.cream, focusedContentColor = V.night),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 22.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("%02d".format(n), style = VType.row.copy(fontSize = 20.sp), color = if (focused) V.magenta else V.teal, modifier = Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                Text(s.displayName.uppercase(), style = VType.row, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (s.live) "${formatViewers(s.viewers)} · ${s.game}" else "не в эфире",
                    style = VType.small, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (focused) V.plum else V.dim,
                )
            }
            if (fav) Box(Modifier.size(10.dp).background(if (focused) V.magenta else V.teal, SlantShape(0.3f)))
        }
    }
}

/** The focused stream as a tilted postcard: cream frame, a live neon tag, the title as the caption. */
@Composable
private fun Postcard(s: StreamItem, leonida: Boolean) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Box(Modifier.fillMaxWidth().rotate(if (leonida) -3.5f else -2f).background(V.cream).padding(10.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(V.night)) {
                AsyncImage(ImageRequest.Builder(ctx).data(s.thumbnail.ifEmpty { s.avatar }).size(800, 450).build(), s.displayName,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, V.magenta.copy(alpha = 0.35f)))))
                if (s.live) Text("LIVE", style = VType.tag.copy(fontSize = 16.sp, shadow = VType.neon(V.magenta, 22f)), color = V.cream,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).border(2.dp, V.magenta).padding(horizontal = 10.dp, vertical = 2.dp))
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(s.displayName.uppercase(), style = VType.title.copy(shadow = VType.neon(V.teal, 14f)), color = V.cream)
        Text(s.title.ifEmpty { s.game }, style = VType.body, color = V.dim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text("OK — смотреть · удерживайте OK — в избранное", style = VType.small, color = V.faint, modifier = Modifier.padding(top = 10.dp))
    }
}

@UnstableApi
@Composable
private fun ViceSearch(app: AppState) {
    var query by remember { mutableStateOf(StreamRepo.searchQuery) }
    var committed by remember { mutableStateOf(StreamRepo.searchQuery.trim()) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(query) { StreamRepo.searchQuery = query; if (query.trim().length >= 2) { delay(600); committed = query.trim() } }
    Column {
        NeonTitle("Поиск", V.teal, Modifier.padding(bottom = 14.dp), VType.mega.copy(fontSize = 44.sp))
        Box(Modifier.width(560.dp).border(2.dp, if (focused) V.magenta else V.teal, SlantShape(0.1f)).background(V.panel, SlantShape(0.1f))
            .padding(horizontal = 26.dp, vertical = 12.dp)) {
            if (query.isEmpty()) Text("ИМЯ КАНАЛА", style = VType.title, color = V.faint)
            BasicTextField(query, { query = it }, singleLine = true, textStyle = VType.title.copy(color = V.cream),
                cursorBrush = SolidColor(V.magenta), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { committed = query.trim() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused })
        }
        Spacer(Modifier.height(10.dp))
        if (committed.isBlank()) ViceSign("Кого ищем?", "Наведите на поле и нажмите OK — откроется клавиатура. Результаты — после двух букв.")
        else {
            val h = rememberStreams(StreamRepo.Key.SEARCH, app.prefs, committed)
            when (val st = h.state) {
                Load.Loading -> ViceLoading("Ищем")
                is Load.Failed -> ViceSign("Нет сигнала", st.message, V.magenta, "Повторить") { h.reload() }
                is Load.Ready -> if (st.items.isEmpty()) ViceSign("Никого", "По запросу «$committed» каналов нет.") else MissionBoard(st.items, app, false)
            }
        }
    }
}
