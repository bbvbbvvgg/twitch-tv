package su.expensive.twitchtv.ui.gtv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import su.expensive.twitchtv.data.Account
import su.expensive.twitchtv.data.StreamItem
import su.expensive.twitchtv.ui.core.AppState
import su.expensive.twitchtv.ui.core.Load
import su.expensive.twitchtv.ui.core.StreamRepo
import su.expensive.twitchtv.ui.core.StreamsHolder
import su.expensive.twitchtv.ui.core.formatViewers
import su.expensive.twitchtv.ui.core.rememberStreams

/**
 * Google TV home. Structure: a top navigation strip of text tabs (search icon, For you, Live,
 * GTA VI, Library, account + settings on the right) → a full-bleed cinematic hero → vertical
 * stack of horizontal carousels. Detail appears on focus; artwork is the colour.
 */
private enum class Tab(val label: String) { SEARCH("Поиск"), FOR_YOU("Для вас"), LIVE("Прямой эфир"), GTA("GTA VI"), LIBRARY("Библиотека"), SETTINGS("Настройки") }

private object GtvMemory { var lastLogin: String? = null }

@UnstableApi
@Composable
fun GtvApp(app: AppState) {
    GtvTheme {
        val s = app.session
        if (s == null) GtvHome(app) else {
            BackHandler { app.close() }
            GtvPlayer(app, s)
        }
    }
    LaunchedEffect(Unit) { app.openSettingsOnce = false; app.openNetworkOnce = false }
}

@UnstableApi
@Composable
private fun GtvHome(app: AppState) {
    var tab by rememberSaveable { mutableStateOf(if (app.openSettingsOnce) Tab.SETTINGS else Tab.FOR_YOU) }
    Box(Modifier.fillMaxSize().background(T.bg)) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 24 }) togetherWith fadeOut(tween(150)) },
            label = "tab",
        ) { t ->
            when (t) {
                Tab.FOR_YOU -> ForYou(app)
                Tab.LIVE -> LiveByCategory(app)
                Tab.GTA -> GtaTab(app)
                Tab.LIBRARY -> Library(app)
                Tab.SEARCH -> SearchTab(app)
                Tab.SETTINGS -> GtvSettings(app)
            }
        }
        TopNav(tab) { tab = it }
    }
}

@Composable
private fun TopNav(current: Tab, onSelect: (Tab) -> Unit) {
    val req = remember { Tab.entries.associateWith { FocusRequester() } }
    var keyed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { req.getValue(current).requestFocus() } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 18.dp).onPreviewKeyEvent { keyed = true; false },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Twitch TV", style = TType.headline.copy(fontWeight = FontWeight.Bold), color = T.text, modifier = Modifier.padding(end = 22.dp))
        fun Modifier.nav(t: Tab): Modifier {
            return focusRequester(req.getValue(t))
        }
        val tabs = listOf(Tab.SEARCH, Tab.FOR_YOU, Tab.LIVE, Tab.GTA, Tab.LIBRARY)
        tabs.forEach { t ->
            var pending by remember { mutableStateOf(false) }
            LaunchedEffect(pending) { if (pending) { delay(250); if (t != current) onSelect(t); pending = false } }
            Pill(if (t == Tab.SEARCH) null else t.label, if (t == Tab.SEARCH) Icons.Rounded.Search else null,
                Modifier.nav(t).onFocusChanged { pending = it.isFocused && keyed }.padding(end = 4.dp), selected = t == current,
                padding = if (t == Tab.SEARCH) PaddingValues(10.dp) else PaddingValues(horizontal = 18.dp, vertical = 9.dp)) { onSelect(t) }
        }
        Spacer(Modifier.weight(1f))
        Pill(Account.login ?: "Войти", Icons.Rounded.AccountCircle, padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { onSelect(Tab.SETTINGS) }
        Spacer(Modifier.width(6.dp))
        Pill(null, Icons.Rounded.Settings, Modifier.nav(Tab.SETTINGS), selected = current == Tab.SETTINGS, padding = PaddingValues(10.dp)) { onSelect(Tab.SETTINGS) }
    }
}

// ---------------------------------------------------------------- For you

@UnstableApi
@Composable
private fun ForYou(app: AppState) {
    val prefs = app.prefs
    val popular = rememberStreams(StreamRepo.Key.POPULAR, prefs)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp)) {
        item(key = "hero") { Hero(popular, app) }
        // Personal rows lead only when they have something; empty ones move below the live rows.
        val personalFirst = prefs.history().isNotEmpty() || prefs.favorites().isNotEmpty()
        val personal: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
            item(key = "continue") {
                val h = rememberStreams(StreamRepo.Key.HISTORY, prefs)
                Carousel("Продолжить просмотр", h, app, live = true, progress = true,
                    empty = "Здесь появятся каналы, которые вы недавно открывали и которые сейчас в эфире.")
            }
            item(key = "fav") { BubbleRow("Избранные каналы", rememberStreams(StreamRepo.Key.FAVORITES, prefs), app,
                "Удерживайте OK на любой карточке, чтобы добавить канал.") }
        }
        if (personalFirst) personal()
        item(key = "pop") { Carousel("Популярное сейчас", popular, app) }
        item(key = "gta") { Carousel("GTA VI", rememberStreams(StreamRepo.Key.GTA, prefs), app) }
        if (!personalFirst) personal()
        item(key = "rec") { Carousel("Рекомендуем по вашим играм", rememberStreams(StreamRepo.Key.RECOMMENDED, prefs), app,
            empty = "Посмотрите пару трансляций — подборка соберётся из игр, которые вы смотрите.") }
    }
}

/** Rotating featured banner: the top live streams, full-bleed artwork, two actions. */
@UnstableApi
@Composable
private fun Hero(h: StreamsHolder, app: AppState, featured: List<StreamItem>? = null, caption: String? = null) {
    val list = (featured ?: h.items).filter { it.live }.take(5)
    var idx by remember { mutableIntStateOf(0) }
    var focusedInside by remember { mutableStateOf(false) }
    var favTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(list.size, focusedInside) {
        while (list.size > 1 && !focusedInside) { delay(9_000); idx = (idx + 1) % list.size }
    }
    Box(Modifier.fillMaxWidth().height(380.dp)) {
        if (list.isEmpty()) {
            if (h.state is Load.Failed) Box(Modifier.padding(top = 120.dp, start = 48.dp)) {
                GtvMessage("Не удалось загрузить", (h.state as Load.Failed).message, "Повторить") { h.reload() }
            } else Box(Modifier.fillMaxSize().background(T.surface))
            return@Box
        }
        val s = list[idx.coerceIn(0, list.lastIndex)]
        AnimatedContent(s, transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(700)) }, label = "hero") {
            ScrimmedArt(it.thumbnail.ifEmpty { it.avatar }, Modifier.fillMaxSize())
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 26.dp).width(560.dp)) {
            caption?.let { Text(it, style = TType.label, color = T.accent, modifier = Modifier.padding(bottom = 8.dp)) }
            Text(s.displayName, style = TType.hero, color = T.text, maxLines = 1)
            Text(s.title, style = TType.body, color = T.dim, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            Text("${s.game} · ${formatViewers(s.viewers)} зрителей", style = TType.caption, color = T.dim, maxLines = 1,
                modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 16.dp).onFocusChanged { focusedInside = it.hasFocus }, horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                HeroButton("Смотреть", Icons.Rounded.PlayArrow) { GtvMemory.lastLogin = null; app.open(s, list) }
                val fav = favTick >= 0 && app.prefs.isFavorite(s.login)
                HeroButton(if (fav) "В избранном" else "В избранное", if (fav) Icons.Rounded.Star else Icons.Rounded.StarOutline, primary = false) {
                    app.prefs.toggleFavorite(s.login); StreamRepo.invalidate(StreamRepo.Key.FAVORITES); favTick++
                }
                Spacer(Modifier.width(12.dp))
                list.indices.forEach { i ->
                    Box(Modifier.padding(end = 6.dp).size(if (i == idx) 22.dp else 8.dp, 8.dp).background(if (i == idx) T.text else T.faint, CircleShape))
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun Carousel(title: String, h: StreamsHolder, app: AppState, live: Boolean = false, progress: Boolean = false,
                     empty: String = "Пока ничего нет", items: List<StreamItem>? = null, cardWidth: androidx.compose.ui.unit.Dp = 236.dp) {
    var favTick by remember { mutableIntStateOf(0) }
    Column(Modifier.padding(top = 22.dp)) {
        Text(title, style = TType.rowTitle, color = T.text, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        val list = items ?: h.items.let { if (live) it.filter { s -> s.live } else it }
        when {
            items == null && h.state is Load.Loading -> Box(Modifier.padding(start = 48.dp)) { SkeletonRow(cardWidth) }
            items == null && h.state is Load.Failed -> Row(Modifier.padding(start = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text((h.state as Load.Failed).message, style = TType.body, color = T.dim, modifier = Modifier.weight(1f, false))
                Pill("Повторить", selected = true, modifier = Modifier.padding(start = 12.dp)) { h.reload() }
            }
            list.isEmpty() -> Text(empty, style = TType.body, color = T.faint, modifier = Modifier.padding(start = 48.dp))
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp)) {
                items(list, key = { it.login }) { s ->
                    val fr = remember { FocusRequester() }
                    MediaCard(s, cardWidth, Modifier.focusRequester(fr), showProgress = progress, favorite = favTick >= 0 && app.prefs.isFavorite(s.login),
                        onLong = { app.prefs.toggleFavorite(s.login); StreamRepo.invalidate(StreamRepo.Key.FAVORITES); favTick++ }) {
                        if (s.live) { GtvMemory.lastLogin = s.login; app.open(s, list) }
                    }
                    LaunchedEffect(Unit) { if (GtvMemory.lastLogin == s.login) { runCatching { fr.requestFocus() }; GtvMemory.lastLogin = null } }
                }
            }
        }
    }
}

@UnstableApi
@Composable
private fun BubbleRow(title: String, h: StreamsHolder, app: AppState, empty: String) {
    var favTick by remember { mutableIntStateOf(0) }
    Column(Modifier.padding(top = 22.dp)) {
        Text(title, style = TType.rowTitle, color = T.text, modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        when (val st = h.state) {
            Load.Loading -> Row(Modifier.padding(start = 48.dp)) { repeat(8) { Box(Modifier.padding(end = 22.dp).size(96.dp).background(T.surface, CircleShape)) } }
            is Load.Failed -> Text(st.message, style = TType.body, color = T.dim, modifier = Modifier.padding(start = 48.dp))
            is Load.Ready -> if (st.items.isEmpty()) Text(empty, style = TType.body, color = T.faint, modifier = Modifier.padding(start = 48.dp))
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp)) {
                items(st.items, key = { it.login }) { s ->
                    ChannelBubble(s, onLong = { app.prefs.toggleFavorite(s.login); StreamRepo.invalidate(StreamRepo.Key.FAVORITES); favTick++ }) {
                        if (s.live) app.open(s, st.items)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Live (by category)

@UnstableApi
@Composable
private fun LiveByCategory(app: AppState) {
    val h = rememberStreams(StreamRepo.Key.POPULAR, app.prefs)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 80.dp, bottom = 60.dp)) {
        when (val st = h.state) {
            Load.Loading -> items(3) { Column(Modifier.padding(top = 22.dp, start = 48.dp)) { SkeletonRow(236.dp) } }
            is Load.Failed -> item { Box(Modifier.padding(start = 44.dp)) { GtvMessage("Не удалось загрузить эфиры", st.message, "Повторить") { h.reload() } } }
            is Load.Ready -> {
                if (st.items.isEmpty()) item { Box(Modifier.padding(start = 44.dp)) { GtvMessage("Сейчас нет трансляций") } }
                val groups = st.items.groupBy { it.game.ifBlank { "Без категории" } }.entries.sortedByDescending { g -> g.value.sumOf { it.viewers } }
                groups.forEach { (game, list) ->
                    item(key = game) { Carousel("$game · ${formatViewers(list.sumOf { it.viewers })} зрителей", h, app, items = list) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- GTA VI

@UnstableApi
@Composable
private fun GtaTab(app: AppState) {
    val h = rememberStreams(StreamRepo.Key.GTA, app.prefs)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp)) {
        item(key = "hero") {
            Hero(h, app, caption = if (StreamRepo.gtaViewers > 0) "${StreamRepo.gtaTitle} · ${formatViewers(StreamRepo.gtaViewers)} зрителей сейчас" else StreamRepo.gtaTitle)
        }
        val all = h.items
        item(key = "top") { Carousel("Больше всего зрителей", h, app, items = if (h.state is Load.Ready) all.take(10) else null, cardWidth = 280.dp) }
        if (all.size > 10) item(key = "more") { Carousel("Ещё в эфире", h, app, items = all.drop(10)) }
    }
}

// ---------------------------------------------------------------- Library

@UnstableApi
@Composable
private fun Library(app: AppState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 80.dp, bottom = 60.dp)) {
        item(key = "fav") { Carousel("Избранное", rememberStreams(StreamRepo.Key.FAVORITES, app.prefs), app,
            empty = "Удерживайте OK на любой карточке, чтобы добавить канал в избранное.") }
        item(key = "hist") { Carousel("История просмотра", rememberStreams(StreamRepo.Key.HISTORY, app.prefs), app,
            empty = "Вы ещё ничего не открывали.") }
    }
}

// ---------------------------------------------------------------- Search

@UnstableApi
@Composable
private fun SearchTab(app: AppState) {
    var query by remember { mutableStateOf(StreamRepo.searchQuery) }
    var committed by remember { mutableStateOf(StreamRepo.searchQuery.trim()) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(query) { StreamRepo.searchQuery = query; if (query.trim().length >= 2) { delay(600); committed = query.trim() } }
    Column(Modifier.fillMaxSize().padding(top = 90.dp)) {
        Row(
            Modifier.padding(start = 48.dp).width(720.dp).height(60.dp)
                .background(if (focused) T.surfaceHi else T.surface, CircleShape)
                .then(if (focused) Modifier.border(2.dp, T.focus, CircleShape) else Modifier)
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = T.dim)
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text("Поиск каналов на Twitch", style = TType.headline, color = T.faint)
                BasicTextField(query, { query = it }, singleLine = true, textStyle = TType.headline.copy(color = T.text),
                    cursorBrush = SolidColor(T.accent), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { committed = query.trim() }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused })
            }
        }
        if (committed.isBlank()) Box(Modifier.padding(start = 44.dp)) {
            GtvMessage("Что посмотреть?", "Нажмите OK на строке поиска, введите имя канала — результаты появятся сами.")
        } else {
            val h = rememberStreams(StreamRepo.Key.SEARCH, app.prefs, committed)
            Carousel("Трансляции", h, app, live = true, empty = "Никто из найденных сейчас не в эфире.")
            BubbleRow("Каналы", h, app, "Ничего не найдено по запросу «$committed».")
        }
    }
}
