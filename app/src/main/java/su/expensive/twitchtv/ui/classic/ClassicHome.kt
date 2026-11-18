package su.expensive.twitchtv.ui.classic

import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import su.expensive.twitchtv.data.Prefs
import su.expensive.twitchtv.data.StreamItem
import su.expensive.twitchtv.data.TwitchApi
import su.expensive.twitchtv.ui.core.AppState
import su.expensive.twitchtv.ui.core.Load
import su.expensive.twitchtv.ui.core.StreamRepo
import su.expensive.twitchtv.ui.core.formatViewers
import su.expensive.twitchtv.ui.core.rememberStreams
import androidx.media3.common.util.UnstableApi

private val TABS = listOf("Избранное", "Популярное", "GTA VI", "Поиск", "История", "Настройки")

/** Focus memory: the card that opened the player gets focus back on return. */
object HomeCache {
    var lastLogin: String? = null
}

@UnstableApi
@Composable
fun HomeScreen(app: AppState, initialTab: Int, onTabChanged: (Int) -> Unit, onOpen: (StreamItem, List<StreamItem>) -> Unit) {
    val prefs = app.prefs
    var tab by remember { mutableIntStateOf(initialTab) }
    // Focus moves the tab only after the user pressed a key: automatic initial focus (which
    // lands on the first tab) must not switch the screen away from the chosen start tab.
    var userKeyed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)
        .onPreviewKeyEvent { userKeyed = true; false }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Twitch", style = MaterialTheme.typography.headlineSmall, color = Palette.accent)
            Text(" TV", style = MaterialTheme.typography.headlineSmall, color = Palette.text)
            Spacer(Modifier.width(32.dp))
            // Start with focus on the selected tab, otherwise TV focus search lands on the first
            // tab and silently switches the screen to it.
            val tabFocus = remember { List(TABS.size) { FocusRequester() } }
            LaunchedEffect(Unit) { runCatching { tabFocus[tab].requestFocus() } }
            TabRow(selectedTabIndex = tab) {
                TABS.forEachIndexed { i, title ->
                    Tab(selected = tab == i, modifier = Modifier.focusRequester(tabFocus[i]), onFocus = { if (userKeyed && tab != i) { tab = i; onTabChanged(i) } }) {
                        Text(title, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                0 -> StreamTab(StreamRepo.Key.FAVORITES, prefs, onOpen, empty = "Добавьте стримера: удерживайте OK на карточке")
                1 -> StreamTab(StreamRepo.Key.POPULAR, prefs, onOpen)
                2 -> StreamTab(StreamRepo.Key.GTA, prefs, onOpen, header = {
                    if (StreamRepo.gtaViewers > 0) "${StreamRepo.gtaTitle} · ${formatViewers(StreamRepo.gtaViewers)} зрителей" else StreamRepo.gtaTitle })
                3 -> SearchTab(prefs, onOpen)
                4 -> StreamTab(StreamRepo.Key.HISTORY, prefs, onOpen, empty = "История пуста")
                5 -> SettingsScreen(app)
            }
        }
    }
}

@Composable
private fun StreamTab(
    key: StreamRepo.Key,
    prefs: Prefs,
    onOpen: (StreamItem, List<StreamItem>) -> Unit,
    empty: String = "Нет трансляций",
    header: (() -> String)? = null,
) {
    val h = rememberStreams(key, prefs)
    var favVersion by remember { mutableIntStateOf(0) }
    Column {
        header?.let { Text(it(), style = MaterialTheme.typography.titleMedium, color = Palette.dim, modifier = Modifier.padding(bottom = 12.dp)) }
        when (val st = h.state) {
            is Load.Failed -> Message(st.message)
            Load.Loading -> Message("Загрузка…")
            is Load.Ready -> if (st.items.isEmpty()) Message(empty)
                else StreamGrid(st.items, prefs, favVersion, onToggleFav = { favVersion++ }, onOpen = { onOpen(it, st.items.filter { s -> s.live }) })
        }
    }
}

@Composable
fun Message(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Palette.dim)
    }
}

@Composable
fun StreamGrid(list: List<StreamItem>, prefs: Prefs, favVersion: Int, onToggleFav: () -> Unit, onOpen: (StreamItem) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 48.dp, top = 4.dp, start = 4.dp, end = 4.dp),
    ) {
        items(list, key = { it.login }) { s ->
            StreamCard(s, prefs.isFavorite(s.login).also { favVersion }, onClick = { HomeCache.lastLogin = s.login; onOpen(s) },
                onLongClick = { prefs.toggleFavorite(s.login); StreamRepo.invalidate(StreamRepo.Key.FAVORITES); onToggleFav() })
        }
    }
}

@Composable
fun StreamCard(s: StreamItem, favorite: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val fr = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    Card(
        onClick = { if (s.live) onClick() },
        onLongClick = onLongClick,
        modifier = Modifier.focusRequester(fr).onFocusChanged { focused = it.isFocused },
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1.04f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(10.dp))),
        colors = CardDefaults.colors(containerColor = Palette.surface, focusedContainerColor = Palette.surfaceHi),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            val img = s.thumbnail.ifEmpty { s.avatar }
            if (img.isNotEmpty()) AsyncImage(
                model = ImageRequest.Builder(ctx).data(img).size(480, 270).build(),
                contentDescription = null,
                contentScale = if (s.thumbnail.isNotEmpty()) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (s.live) Row(
                Modifier.padding(8.dp).background(Palette.live, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            ) { Text("LIVE  ${formatViewers(s.viewers)}", color = Color.White, fontSize = 12.sp) }
            else Text("офлайн", color = Palette.dim, fontSize = 12.sp,
                modifier = Modifier.padding(8.dp).background(Color(0xCC000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
            if (favorite) Text("★", color = Color(0xFFFFD54F), fontSize = 20.sp, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (s.avatar.isNotEmpty()) AsyncImage(
                model = ImageRequest.Builder(ctx).data(s.avatar).size(70, 70).build(), contentDescription = null,
                modifier = Modifier.size(32.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(s.displayName, style = MaterialTheme.typography.labelLarge, color = Palette.text, maxLines = 1)
                Text(s.title.ifEmpty { s.game }, style = MaterialTheme.typography.bodySmall, color = Palette.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    LaunchedEffect(Unit) {
        if (HomeCache.lastLogin == s.login) { runCatching { fr.requestFocus() }; HomeCache.lastLogin = null }
    }
}

@Composable
private fun SearchTab(prefs: Prefs, onOpen: (StreamItem, List<StreamItem>) -> Unit) {
    var query by remember { mutableStateOf(StreamRepo.searchQuery) }
    var results by remember { mutableStateOf<List<StreamItem>?>(StreamRepo.cached("SEARCH:" + StreamRepo.searchQuery)) }
    var error by remember { mutableStateOf<String?>(null) }
    var favVersion by remember { mutableIntStateOf(0) }
    var editFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun run(q: String) = scope.launch {
        if (q.isBlank()) return@launch
        error = null
        runCatching { withContext(Dispatchers.IO) { StreamRepo.load(StreamRepo.Key.SEARCH, prefs, q.trim()) } }
            .onSuccess { results = it }
            .onFailure { error = "Поиск не удался: ${it.message}" }
    }
    LaunchedEffect(query) {
        StreamRepo.searchQuery = query
        if (query.length >= 2) { delay(600); run(query) }
    }
    Column {
        Surface(
            modifier = Modifier.fillMaxWidth(0.6f).height(56.dp),
            shape = RoundedCornerShape(10.dp),
            colors = SurfaceDefaults.colors(containerColor = if (editFocused) Palette.surfaceHi else Palette.surface),
            border = if (editFocused) Border(BorderStroke(2.dp, Palette.accent)) else Border.None,
        ) {
            Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text("Имя канала…", color = Palette.dim)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Palette.text, fontSize = 20.sp),
                    cursorBrush = SolidColor(Palette.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { run(query) }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { editFocused = it.isFocused },
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        val r = results
        when {
            error != null -> Message(error!!)
            r == null -> Message("Введите имя стримера (OK — открыть клавиатуру)")
            r.isEmpty() -> Message("Ничего не найдено")
            else -> StreamGrid(r, prefs, favVersion, onToggleFav = { favVersion++ }, onOpen = { onOpen(it, r.filter { s -> s.live }) })
        }
    }
}

@Suppress("unused")
private val vignette = Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))

/** Classic root: Home ⇄ full-screen Player. Back from the player returns to the same tab/focus. */
@UnstableApi
@Composable
fun ClassicApp(app: AppState) {
    val s = app.session
    var tab by androidx.compose.runtime.saveable.rememberSaveable {
        mutableIntStateOf(if (app.openSettingsOnce) 5 else if (app.prefs.favorites().isNotEmpty()) 0 else 1)
    }
    TvTheme {
        Box(Modifier.fillMaxSize().background(Palette.bg)) {
            if (s == null) {
                HomeScreen(app, initialTab = tab, onTabChanged = { tab = it }, onOpen = { item, list -> app.open(item, list) })
            } else {
                androidx.activity.compose.BackHandler { app.close() }
                PlayerScreen(s)
            }
        }
    }
    LaunchedEffect(Unit) { app.openSettingsOnce = false }
}
