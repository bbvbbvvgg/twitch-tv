package su.expensive.twitchtv.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
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
import su.expensive.twitchtv.data.StreamItem
import su.expensive.twitchtv.ui.core.AppState
import su.expensive.twitchtv.ui.core.Load
import su.expensive.twitchtv.ui.core.StreamRepo
import su.expensive.twitchtv.ui.core.StreamsHolder
import su.expensive.twitchtv.ui.core.formatViewers
import su.expensive.twitchtv.ui.core.rememberStreams

/**
 * Liquid Glass home. Structure: a floating dock (top-centre capsule) → a spotlight (one huge
 * preview of the focused stream + its story) → a single shelf of rounded tiles. Focus on the shelf
 * drives the spotlight and the blurred backdrop; there is no grid anywhere.
 */
enum class GSection(val label: String, val icon: ImageVector) {
    FAV("Избранное", Icons.Rounded.Star),
    POP("Популярное", Icons.Rounded.Whatshot),
    GTA("GTA VI", Icons.Rounded.SportsEsports),
    SEARCH("Поиск", Icons.Rounded.Search),
    HIST("История", Icons.Rounded.History),
    SETTINGS("Настройки", Icons.Rounded.Settings),
}

private object GlassMemory { var lastLogin: String? = null }

@UnstableApi
@Composable
fun GlassApp(app: AppState) {
    GlassTheme {
        val s = app.session
        if (s == null) GlassHome(app) else {
            BackHandler { app.close() }
            GlassPlayer(app, s)
        }
    }
    LaunchedEffect(Unit) { app.openSettingsOnce = false }
}

@UnstableApi
@Composable
private fun GlassHome(app: AppState) {
    val prefs = app.prefs
    var section by rememberSaveable {
        mutableStateOf(if (app.openSettingsOnce) GSection.SETTINGS else if (prefs.favorites().isNotEmpty()) GSection.FAV else GSection.POP)
    }
    var spotlight by remember { mutableStateOf<StreamItem?>(null) }
    // Entrance: the whole scene settles in with a gentle spring.
    val enter = remember { Animatable(0.96f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) }

    Box(Modifier.fillMaxSize()) {
        GlassBackdrop(spotlight?.thumbnail?.ifEmpty { spotlight?.avatar })
        Column(Modifier.fillMaxSize().graphicsLayer { scaleX = enter.value; scaleY = enter.value }.padding(horizontal = 56.dp)) {
            Spacer(Modifier.height(20.dp))
            Dock(section, onSelect = { section = it; if (it == GSection.SETTINGS || it == GSection.SEARCH) spotlight = null })
            Spacer(Modifier.height(18.dp))
            AnimatedContent(
                targetState = section,
                transitionSpec = { (fadeIn(tween(320)) + scaleIn(tween(420), initialScale = 0.97f)) togetherWith fadeOut(tween(160)) },
                label = "section",
            ) { sec ->
                when (sec) {
                    GSection.FAV -> Spotlight(rememberStreams(StreamRepo.Key.FAVORITES, prefs), app, onFocus = { spotlight = it },
                        emptyTitle = "Здесь будут любимые каналы", emptyBody = "Удерживайте OK на любой плитке, чтобы добавить канал в избранное.")
                    GSection.POP -> Spotlight(rememberStreams(StreamRepo.Key.POPULAR, prefs), app, onFocus = { spotlight = it })
                    GSection.GTA -> Spotlight(rememberStreams(StreamRepo.Key.GTA, prefs), app, onFocus = { spotlight = it },
                        caption = { "${StreamRepo.gtaTitle} · ${formatViewers(StreamRepo.gtaViewers)} зрителей" })
                    GSection.SEARCH -> GlassSearch(app, onFocus = { spotlight = it })
                    GSection.HIST -> Spotlight(rememberStreams(StreamRepo.Key.HISTORY, prefs), app, onFocus = { spotlight = it },
                        emptyTitle = "История пуста", emptyBody = "Открытые трансляции появятся здесь.")
                    GSection.SETTINGS -> GlassSettings(app)
                }
            }
        }
    }
}

/** Floating dock: a glass capsule; the active section is a lit pill, focus switches sections. */
@Composable
private fun Dock(current: GSection, onSelect: (GSection) -> Unit) {
    val requesters = remember { GSection.entries.associateWith { FocusRequester() } }
    var userKeyed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { requesters.getValue(current).requestFocus() } }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.glass(CircleShape, 1.2f).padding(8.dp).onPreviewKeyEvent { userKeyed = true; false },
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Twitch", style = GType.label.copy(fontSize = 18.sp), color = G.text, modifier = Modifier.padding(start = 16.dp, end = 10.dp))
            GSection.entries.forEach { s ->
                var pending by remember { mutableStateOf(false) }
                // Debounced: sweeping across the dock does not load every section on the way.
                LaunchedEffect(pending) { if (pending) { delay(220); if (s != current) onSelect(s); pending = false } }
                GlassButton(
                    if (s == current) s.label else null, s.icon,
                    modifier = Modifier.focusRequester(requesters.getValue(s))
                        .onFocusChanged { pending = it.isFocused && userKeyed },
                    selected = s == current,
                    padding = PaddingValues(horizontal = if (s == current) 20.dp else 14.dp, vertical = 11.dp),
                ) { onSelect(s) }
            }
        }
    }
}

@UnstableApi
@Composable
private fun Spotlight(
    h: StreamsHolder, app: AppState, onFocus: (StreamItem) -> Unit,
    emptyTitle: String = "Сейчас пусто", emptyBody: String? = null, caption: (() -> String)? = null, heroHeight: androidx.compose.ui.unit.Dp = 220.dp,
) {
    when (val st = h.state) {
        Load.Loading -> SpotlightSkeleton(heroHeight)
        is Load.Failed -> GlassError(st.message) { h.reload() }
        is Load.Ready -> if (st.items.isEmpty()) GlassNotice(Icons.Rounded.Star, emptyTitle, emptyBody)
        else SpotlightContent(st.items, app, onFocus, caption, heroHeight)
    }
}

@UnstableApi
@Composable
private fun SpotlightContent(items: List<StreamItem>, app: AppState, onFocus: (StreamItem) -> Unit, caption: (() -> String)?, heroHeight: androidx.compose.ui.unit.Dp) {
    val startIdx = items.indexOfFirst { it.login == GlassMemory.lastLogin }.coerceAtLeast(0)
    var focused by remember(items) { mutableStateOf(items[startIdx]) }
    var favTick by remember { mutableStateOf(0) }
    LaunchedEffect(focused) { onFocus(focused) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(heroHeight), verticalAlignment = Alignment.CenterVertically) {
            Hero(focused, Modifier.fillMaxHeight().aspectRatio(16f / 9f))
            Spacer(Modifier.width(40.dp))
            Column(Modifier.weight(1f)) {
                caption?.let { Text(it(), style = GType.small, color = G.tint, modifier = Modifier.padding(bottom = 10.dp)) }
                Text(focused.displayName, style = GType.display, color = G.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Text(focused.title.ifEmpty { "Без названия" }, style = GType.body, color = G.dim, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (focused.live) LivePill("${formatViewers(focused.viewers)} зрителей") else
                        Text("Не в эфире", style = GType.small, color = G.faint)
                    if (focused.game.isNotBlank()) Text(focused.game, style = GType.small, color = G.dim, maxLines = 1,
                        modifier = Modifier.padding(start = 14.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    (if (favTick >= 0 && app.prefs.isFavorite(focused.login)) "В избранном · " else "") + "OK — смотреть · удерживайте OK — избранное",
                    style = GType.small, color = G.faint,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val list = rememberLazyListState(startIdx)
        LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(vertical = 14.dp, horizontal = 10.dp)) {
            itemsIndexed(items, key = { _, s -> s.login }) { _, s ->
                Tile(s, app.prefs.isFavorite(s.login).also { favTick },
                    onFocus = { focused = s },
                    onClick = { if (s.live) { GlassMemory.lastLogin = s.login; app.open(s, items) } },
                    onLong = { app.prefs.toggleFavorite(s.login); StreamRepo.invalidate(StreamRepo.Key.FAVORITES); favTick++ })
            }
        }
    }
}

/** The spotlight preview: a big rounded window with a lit rim. Not focusable — the shelf drives it. */
@Composable
private fun Hero(s: StreamItem, modifier: Modifier) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(34.dp)
    Box(modifier.clip(shape).background(Color(0x33000000)).border(1.5.dp, G.rim(1.4f), shape)) {
        AnimatedContent(targetState = s, transitionSpec = { fadeIn(tween(380)) togetherWith fadeOut(tween(380)) }, label = "hero") { it ->
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(it.thumbnail.ifEmpty { it.avatar }).size(960, 540).build(),
                contentDescription = it.displayName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color.Transparent, Color.Transparent))))
    }
}

@Composable
private fun Tile(s: StreamItem, favorite: Boolean, onFocus: () -> Unit, onClick: () -> Unit, onLong: () -> Unit) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(24.dp)
    val fr = remember { FocusRequester() }
    Column(Modifier.width(204.dp)) {
        Surface(
            onClick = onClick, onLongClick = onLong,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).focusRequester(fr).onFocusChanged { if (it.isFocused) onFocus() },
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(containerColor = G.fill, focusedContainerColor = G.fill),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)), shape = shape),
                focusedBorder = Border(BorderStroke(2.5.dp, Color.White), shape = shape),
            ),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(G.glow.copy(alpha = 0.6f), 22.dp)),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(s.thumbnail.ifEmpty { s.avatar }).size(480, 270).build(),
                    contentDescription = null, contentScale = if (s.thumbnail.isNotEmpty()) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(), alpha = if (s.live) 1f else 0.45f,
                )
                // Specular highlight along the top edge.
                Box(Modifier.fillMaxWidth().height(40.dp).background(Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color.Transparent))))
                if (favorite) Icon(Icons.Rounded.Star, "В избранном", tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(26.dp).glass(CircleShape, 1.4f).padding(4.dp))
            }
        }
        Text(s.displayName, style = GType.label, color = G.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp, start = 6.dp))
    }
    LaunchedEffect(Unit) { if (GlassMemory.lastLogin == s.login) { runCatching { fr.requestFocus() }; GlassMemory.lastLogin = null } }
}

@Composable
private fun SpotlightSkeleton(heroHeight: androidx.compose.ui.unit.Dp) {
    Column {
        Row(Modifier.fillMaxWidth().height(heroHeight)) {
            GlassPlaceholder(Modifier.fillMaxHeight().aspectRatio(16f / 9f), RoundedCornerShape(34.dp))
            Spacer(Modifier.width(40.dp))
            Column(Modifier.weight(1f).padding(top = 40.dp)) {
                GlassPlaceholder(Modifier.fillMaxWidth(0.7f).height(44.dp), RoundedCornerShape(14.dp))
                Spacer(Modifier.height(18.dp))
                GlassPlaceholder(Modifier.fillMaxWidth(0.9f).height(20.dp), RoundedCornerShape(10.dp))
                Spacer(Modifier.height(10.dp))
                GlassPlaceholder(Modifier.fillMaxWidth(0.5f).height(20.dp), RoundedCornerShape(10.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            repeat(6) { GlassPlaceholder(Modifier.width(204.dp).aspectRatio(16f / 9f), RoundedCornerShape(24.dp)) }
        }
    }
}

@UnstableApi
@Composable
private fun GlassSearch(app: AppState, onFocus: (StreamItem) -> Unit) {
    var query by remember { mutableStateOf(StreamRepo.searchQuery) }
    var committed by remember { mutableStateOf(StreamRepo.searchQuery.trim()) }
    var editFocused by remember { mutableStateOf(false) }
    LaunchedEffect(query) { StreamRepo.searchQuery = query; if (query.trim().length >= 2) { delay(600); committed = query.trim() } }
    Column {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                Modifier.width(640.dp).height(56.dp)
                    .glass(CircleShape, if (editFocused) 2.2f else 1.2f)
                    .then(if (editFocused) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                    .padding(horizontal = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = G.dim, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(16.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Канал или стример", style = GType.title, color = G.faint)
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = GType.title.copy(color = G.text), cursorBrush = SolidColor(G.tint),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { committed = query.trim() }),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { editFocused = it.isFocused },
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (committed.isBlank()) GlassNotice(Icons.Rounded.Search, "Найдите канал", "Наведите на поле и нажмите OK — откроется клавиатура. Результаты появятся после двух букв.")
        else Spotlight(rememberStreams(StreamRepo.Key.SEARCH, app.prefs, committed), app, onFocus, "Ничего не найдено", "Проверьте написание имени канала.", heroHeight = 160.dp)
    }
}
