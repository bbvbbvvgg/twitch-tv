package su.expensive.twitchtv.ui.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import su.expensive.twitchtv.data.Prefs
import su.expensive.twitchtv.data.StreamItem
import su.expensive.twitchtv.data.TwitchApi

/** Result of loading a list of streams; every design renders all three states in its own style. */
sealed interface Load {
    data object Loading : Load
    data class Failed(val message: String) : Load
    data class Ready(val items: List<StreamItem>) : Load
}

/**
 * Shared, process-wide list loading and caching. Lists survive Home ⇄ Player round trips and
 * design switches, so "Back" and a design change are instant. Network work runs on IO.
 */
object StreamRepo {
    enum class Key(val ttlMs: Long) { FAVORITES(0), POPULAR(60_000), GTA(60_000), HISTORY(0), RECOMMENDED(180_000), SEARCH(Long.MAX_VALUE) }

    private val cache = HashMap<String, Pair<Long, List<StreamItem>>>()
    /** Bumped on invalidate; screens observing a key reload. */
    var generation by mutableIntStateOf(0); private set

    var gtaTitle: String = "Grand Theft Auto VI"; private set
    var gtaViewers: Int = 0; private set
    var searchQuery: String = ""

    fun invalidate(key: Key) { synchronized(cache) { cache.keys.removeAll { it.startsWith(key.name) } }; generation++ }

    fun cached(id: String): List<StreamItem>? = synchronized(cache) { cache[id]?.second }

    private fun fresh(id: String, ttl: Long): List<StreamItem>? = synchronized(cache) {
        cache[id]?.takeIf { ttl > 0 && System.currentTimeMillis() - it.first < ttl }?.second
    }

    private fun put(id: String, l: List<StreamItem>) = synchronized(cache) { cache[id] = System.currentTimeMillis() to l }

    /** Blocking; call from IO. */
    fun load(key: Key, prefs: Prefs, arg: String = ""): List<StreamItem> {
        val id = key.name + ":" + arg
        fresh(id, key.ttlMs)?.let { return it }
        val l = when (key) {
            Key.FAVORITES -> TwitchApi.channels(prefs.favorites())
                .sortedWith(compareByDescending<StreamItem> { it.live }.thenByDescending { it.viewers })
            Key.POPULAR -> TwitchApi.topStreams(30)
            Key.GTA -> {
                val cat = TwitchApi.findCategory(TwitchApi.GTA6_NAME) ?: error("категория GTA VI не найдена")
                gtaTitle = cat.name; gtaViewers = cat.viewers
                TwitchApi.categoryStreams(cat.id, 30)
            }
            Key.HISTORY -> {
                val order = prefs.history()
                val res = TwitchApi.channels(order).associateBy { it.login.lowercase() }
                order.mapNotNull { res[it] }
            }
            Key.SEARCH -> if (arg.isBlank()) emptyList() else TwitchApi.search(arg.trim())
            Key.RECOMMENDED -> recommended(prefs)
        }
        put(id, l)
        return l
    }

    /**
     * Real recommendations, no invented data: live streams from the categories the user actually
     * watches (games of live channels in history and favorites), excluding channels already in
     * those lists. Empty when there is no history yet — the UI then explains why.
     */
    private fun recommended(prefs: Prefs): List<StreamItem> {
        val mine = (load(Key.HISTORY, prefs) + load(Key.FAVORITES, prefs))
        val known = mine.map { it.login.lowercase() }.toSet()
        val games = mine.filter { it.live && it.game.isNotBlank() }.groupingBy { it.game }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }.take(2)
        val out = LinkedHashMap<String, StreamItem>()
        for (g in games) {
            val cat = runCatching { TwitchApi.findCategory(g) }.getOrNull() ?: continue
            runCatching { TwitchApi.categoryStreams(cat.id, 12) }.getOrDefault(emptyList())
                .filter { it.login.lowercase() !in known }
                .forEach { out.putIfAbsent(it.login.lowercase(), it) }
        }
        return out.values.sortedByDescending { it.viewers }.take(20)
    }

    fun errorText(t: Throwable): String = "Не удалось загрузить: ${t.message ?: "нет сети"}. Проверьте Happ и интернет."
}

@Stable
class StreamsHolder internal constructor(initial: Load) {
    var state by mutableStateOf(initial); internal set
    internal var retry by mutableIntStateOf(0)
    fun reload() { retry++ }
    val items: List<StreamItem> get() = (state as? Load.Ready)?.items ?: emptyList()
}

/** Loads [key] (re-runs on invalidate/reload), keeping the last good list visible on refresh errors. */
@Composable
fun rememberStreams(key: StreamRepo.Key, prefs: Prefs, arg: String = "", enabled: Boolean = true): StreamsHolder {
    val id = key.name + ":" + arg
    val h = remember(id) { StreamsHolder(StreamRepo.cached(id)?.let { Load.Ready(it) } ?: Load.Loading) }
    val gen = StreamRepo.generation
    LaunchedEffect(id, gen, h.retry, enabled) {
        if (!enabled) return@LaunchedEffect
        if (h.state !is Load.Ready) h.state = Load.Loading
        runCatching { withContext(Dispatchers.IO) { StreamRepo.load(key, prefs, arg) } }
            .onSuccess { h.state = Load.Ready(it) }
            .onFailure { if (h.state !is Load.Ready) h.state = Load.Failed(StreamRepo.errorText(it)) }
    }
    return h
}

fun formatViewers(n: Int): String = when {
    n >= 1_000_000 -> "%.1f млн".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1f тыс.".format(n / 1_000.0)
    else -> n.toString()
}
