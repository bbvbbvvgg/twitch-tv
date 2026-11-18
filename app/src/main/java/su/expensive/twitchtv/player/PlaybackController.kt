package su.expensive.twitchtv.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import su.expensive.twitchtv.data.AdFilter
import su.expensive.twitchtv.data.BufferMode
import su.expensive.twitchtv.data.Hls
import su.expensive.twitchtv.data.Net
import su.expensive.twitchtv.data.Prefs
import su.expensive.twitchtv.data.TwitchApi
import su.expensive.twitchtv.data.Variant
import java.io.IOException
import java.util.ArrayDeque

enum class PlayStatus { IDLE, LOADING, PLAYING, BUFFERING, RECONNECTING, OFFLINE, ERROR }

/** User quality choice: null = Auto. */
data class QualityChoice(val height: Int, val fps: Int, val hevc: Boolean)

/**
 * Owns one ExoPlayer for the full-screen player.
 *
 * Stream path: GQL PlaybackAccessToken → usher multivariant → one media playlist (fixed variant)
 * → segments directly from Twitch CDN. Quality switching = switching media playlists; "Auto" is a
 * conservative controller (starts at max, steps down on repeated stalls, steps back up after a
 * long stable period) instead of per-segment ABR, so 1080p↔1440p does not flap.
 *
 * Ads: primary token is requested with a player type that currently gets no preroll for anonymous
 * viewers ("popout", same trick as VAFT's ForceAccessTokenPlayerType). If a media playlist still
 * shows stitched ads, ad segments are stripped and playback switches to a clean backup token
 * (embed → popout with new device id → autoplay 360p). While on backup the primary playlist is
 * polled every few seconds and playback returns to it as soon as the ad break ends.
 */
@UnstableApi
class PlaybackController(private val context: Context, private val prefs: Prefs) {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ---------- observable UI state ----------
    var status by mutableStateOf(PlayStatus.IDLE); private set
    var statusText by mutableStateOf(""); private set
    var variants by mutableStateOf<List<Variant>>(emptyList()); private set
    var current by mutableStateOf<Variant?>(null); private set
    var userChoice by mutableStateOf<QualityChoice?>(null); private set
    var autoCapLabel by mutableStateOf<String?>(null); private set
    var adState by mutableStateOf(""); private set
    var reconnects by mutableIntStateOf(0); private set
    var login by mutableStateOf(""); private set
    val diag = Diagnostics()

    // ---------- player ----------
    private val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
    private val mode: BufferMode = prefs.bufferMode
    val player: ExoPlayer = buildPlayer()

    // ---------- stream state ----------
    private enum class Source { PRIMARY, BACKUP }
    private var source = Source.PRIMARY
    private var primaryType = "site"
    private var primaryVariants: List<Variant> = emptyList()
    private var backupTypeIndex = -1
    @Volatile private var activeUrl: String? = null
    @Volatile private var adTracker = AdFilter.Tracker()
    @Volatile private var lastCleanPlaylist: String? = null
    @Volatile private var adBlockActive = prefs.adBlock
    @Volatile private var adPassThrough = false
    private var openJob: Job? = null
    private var backupJob: Job? = null
    private var pollJob: Job? = null
    private var watchdogJob: Job? = null
    private var retryJob: Job? = null
    private var hevcBroken = false

    // ---------- auto quality / stability bookkeeping ----------
    private var autoCap = 0                         // index into variants (0 = best)
    private val rebuffers = ArrayDeque<Long>()
    private var lastRebufferAt = 0L
    private var bufferingSince = 0L
    private var lastSwitchAt = 0L
    private var lastStepUpCheck = 0L
    private val levelFailures = HashMap<String, MutableList<Long>>()
    private var backoffStep = 0
    private val recoverTimes = ArrayDeque<Long>()
    private var lastPos = -1L
    private var frozenSince = 0L
    private var wasReady = false

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Net.onNetworkChanged()
            main.post {
                DiagLog.log("Сеть доступна (${if (isVpnActive()) "VPN" else "без VPN"})")
                if (status == PlayStatus.RECONNECTING || status == PlayStatus.ERROR) retryNow("сеть восстановлена")
            }
        }
        override fun onLost(network: Network) {
            Net.onNetworkChanged()
            main.post { DiagLog.log("Сеть потеряна") }
        }
    }


    private fun buildPlayer(): ExoPlayer {
        val (minB, maxB, playB, rebufB) = when (mode) {
            BufferMode.LOW_LATENCY -> listOf(2_000, 12_000, 1_000, 2_000)
            BufferMode.BALANCED -> listOf(6_000, 25_000, 1_500, 3_000)
            BufferMode.STABLE -> listOf(15_000, 50_000, 2_500, 5_000)
        }
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minB, maxB, playB, rebufB)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(0, false)
            .build()
        val speed = DefaultLivePlaybackSpeedControl.Builder().apply {
            if (mode == BufferMode.STABLE) setFallbackMinPlaybackSpeed(1f).setFallbackMaxPlaybackSpeed(1f)
            else setFallbackMinPlaybackSpeed(0.97f).setFallbackMaxPlaybackSpeed(1.03f)
        }.build()
        val renderers = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableDecoderFallback(true)
        return ExoPlayer.Builder(context, renderers)
            .setLoadControl(load)
            .setBandwidthMeter(bandwidthMeter)
            .setLivePlaybackSpeedControl(speed)
            .build().apply { playWhenReady = true }
    }

    private val targetOffsetMs: Long get() = when (mode) {
        BufferMode.LOW_LATENCY -> 3_000; BufferMode.BALANCED -> 7_000; BufferMode.STABLE -> 14_000
    }
    private val stallTimeoutMs: Long get() = when (mode) {
        BufferMode.LOW_LATENCY -> 8_000; BufferMode.BALANCED -> 12_000; BufferMode.STABLE -> 20_000
    }

    private val dataSourceFactory by lazy {
        val http = OkHttpDataSource.Factory(Net.http)
            .setUserAgent("Mozilla/5.0 (Linux; Android TV) TwitchTV/1.0")
        PlaylistDataSource.Factory(http, ::onPlaylist)
    }

    fun isVpnActive(): Boolean = runCatching {
        val n = connectivity?.activeNetwork ?: return false
        connectivity.getNetworkCapabilities(n)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
    }.getOrDefault(false)

    // =====================================================================================
    // Opening a channel
    // =====================================================================================
    fun open(channel: String) {
        cancelStreamJobs()
        login = channel.lowercase()
        adBlockActive = prefs.adBlock
        primaryType = if (adBlockActive) "popout" else "site"
        source = Source.PRIMARY
        backupTypeIndex = -1
        adPassThrough = false
        lastCleanPlaylist = null
        // Nothing from the previous channel may survive: the ad fallback decides on activeUrl,
        // and an old stream must not keep playing while this one resolves.
        backupJob?.cancel(); pollJob?.cancel()
        activeUrl = null
        current = null
        player.stop()
        player.clearMediaItems()
        adState = ""
        autoCap = 0
        autoCapLabel = null
        rebuffers.clear(); recoverTimes.clear(); backoffStep = 0
        status = PlayStatus.LOADING; statusText = "Загрузка…"
        diag.reset()
        DiagLog.log("Открываю $login (токен: $primaryType, VPN-интерфейс: ${isVpnActive()}, буфер: ${mode.name})")
        openJob = scope.launch { resolveAndPlay(initial = true) }
        startWatchdog()
    }

    private suspend fun resolvePrimary(): List<Variant> = withContext(Dispatchers.IO) {
        val codecs = DeviceCaps.usherCodecs(prefs.allowHevc && !hevcBroken)
        val tok = TwitchApi.playbackToken(login, primaryType)
        val url = TwitchApi.usherUrl(login, tok, codecs)
        val r = Net.getText(url)
        when {
            r.ok -> {
                val info = Hls.twitchInfo(r.body)
                val country = info["USER-COUNTRY"]
                if (country != diag.twitchCountry) DiagLog.log("Twitch определил страну запроса: ${country ?: "нет данных"}")
                diag.twitchCountry = country
                Hls.parseMultivariant(r.body)
            }
            r.code == 404 || r.code == 403 -> throw OfflineException()
            else -> throw IOException("usher HTTP ${r.code}")
        }
    }

    private class OfflineException : IOException("offline")

    private fun playable(v: List<Variant>): List<Variant> = Hls.sortVideo(v).filter {
        when {
            it.isHevc -> prefs.allowHevc && !hevcBroken && DeviceCaps.hevcHardware
            it.isAv1 -> prefs.allowHevc && !hevcBroken && DeviceCaps.av1Hardware
            else -> true
        }
    }

    private suspend fun resolveAndPlay(initial: Boolean) {
        try {
            val all = resolvePrimary()
            val list = playable(all)
            if (list.isEmpty()) throw IOException("Нет воспроизводимых вариантов качества")
            primaryVariants = list
            variants = list
            DiagLog.log("Варианты: " + list.joinToString { "${it.label} ${it.codecLabel} ${it.bandwidth / 1000}k" })
            val target = pickVariant(list)
            startPrimary(target, checkAds = adBlockActive)
        } catch (e: OfflineException) {
            val live = runCatching { withContext(Dispatchers.IO) { TwitchApi.streamInfo(login) } }.getOrNull()
            if (live == null) {
                status = PlayStatus.OFFLINE; statusText = "Трансляция не идёт"
                DiagLog.log("$login офлайн")
            } else scheduleRetry("usher отказал, канал в эфире")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            DiagLog.log("Ошибка получения потока: ${e.message}")
            scheduleRetry(e.message ?: "сеть")
        }
    }

    private fun pickVariant(list: List<Variant>): Variant {
        userChoice?.let { c ->
            list.firstOrNull { it.height == c.height && it.fpsRounded == c.fps && it.isHevc == c.hevc }?.let { return it }
            Hls.closest(list, c.height, c.fps)?.let { return it }
        }
        val maxH = prefs.defaultQuality.height
        val startIdx = list.indexOfFirst { it.height <= maxH }.let { if (it < 0) list.lastIndex else it }
        if (userChoice == null && prefs.autoQuality) {
            autoCap = autoCap.coerceAtLeast(startIdx).coerceAtMost(list.lastIndex)
            return list[autoCap]
        }
        return list[startIdx]
    }

    private suspend fun startPrimary(v: Variant, checkAds: Boolean) {
        if (checkAds) {
            val text = withContext(Dispatchers.IO) { runCatching { Net.getText(v.url) }.getOrNull() }
            if (text != null && text.ok && AdFilter.hasAds(text.body)) {
                DiagLog.log("Реклама в начальном плейлисте ($primaryType) — сразу резервный поток")
                current = v
                adState = "Пропускаю рекламу…"
                startBackup(fromIndex = 0, playerStarted = false)
                return
            }
        }
        source = Source.PRIMARY
        playUrl(v, "основной")
    }

    private fun playUrl(v: Variant, what: String) {
        current = v
        activeUrl = v.url
        lastCleanPlaylist = null
        adTracker = AdFilter.Tracker() // new MediaSource → new segment numbering session
        lastSwitchAt = SystemClock.elapsedRealtime()
        wasReady = false
        val item = MediaItem.Builder()
            .setUri(v.url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setLiveConfiguration(MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(targetOffsetMs)
                .setMinOffsetMs((targetOffsetMs / 2))
                .setMaxOffsetMs(targetOffsetMs * 3)
                .build())
            .build()
        val ms = HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(item)
        player.setMediaSource(ms)
        player.prepare()
        player.playWhenReady = true
        if (status != PlayStatus.PLAYING) { status = PlayStatus.LOADING; statusText = "Загрузка…" }
        DiagLog.log("Воспроизведение: ${v.label} ${v.codecLabel} ($what)")
    }

    // =====================================================================================
    // Ads
    // =====================================================================================

    /** Called on a loader thread for every media playlist refresh. */
    private fun onPlaylist(uri: String, text: String): String {
        if (!adBlockActive || adPassThrough) return text
        if (!AdFilter.hasAds(text)) {
            if (uri == activeUrl) lastCleanPlaylist = text
            return text
        }
        val st = AdFilter.strip(text, adTracker)
        main.post { onAdsSeen(st.removed) }
        return when {
            st.remaining > 0 -> st.text
            // Nothing but ads: hand the player the last clean playlist (it just waits a moment)
            // while we switch to a clean backup source.
            lastCleanPlaylist != null -> lastCleanPlaylist!!
            else -> st.text
        }
    }

    private fun onAdsSeen(removed: Int) {
        diag.adSegmentsStripped += removed
        if (source == Source.PRIMARY) {
            if (backupJob?.isActive != true) {
                DiagLog.log("Реклама в основном потоке: вырезано $removed сегм., ищу чистый поток")
                adState = "Пропускаю рекламу…"
                startBackup(0)
            }
        } else if (backupJob?.isActive != true) {
            DiagLog.log("Реклама и в резервном потоке ${BACKUP_TYPES.getOrNull(backupTypeIndex)}")
            startBackup(backupTypeIndex + 1)
        }
    }

    /**
     * @param playerStarted false when called from [startPrimary] before any source was given to
     *   the player for this attempt (initial open or re-resolve): then the fallback must start one.
     */
    private fun startBackup(fromIndex: Int, playerStarted: Boolean = true) {
        backupJob?.cancel()
        backupJob = scope.launch {
            val want = current
            // Bounded search: a slow or hanging backup must not leave the screen on "Загрузка…".
            val found = withTimeoutOrNull(BACKUP_SEARCH_TIMEOUT_MS) {
                for (i in fromIndex until BACKUP_TYPES.size) {
                    val type = BACKUP_TYPES[i]
                    val v = runCatching {
                        runInterruptible(Dispatchers.IO) {
                            val tok = TwitchApi.playbackToken(login, type, TwitchApi.newDeviceId())
                            val mv = Net.getText(TwitchApi.usherUrl(login, tok, DeviceCaps.usherCodecs(prefs.allowHevc && !hevcBroken)))
                            if (!mv.ok) return@runInterruptible null
                            val list = playable(Hls.parseMultivariant(mv.body))
                            val cand = (if (want != null) Hls.closest(list, want.height, want.fpsRounded) else list.firstOrNull())
                                ?: return@runInterruptible null
                            val pl = Net.getText(cand.url)
                            if (pl.ok && !AdFilter.hasAds(pl.body)) cand else null
                        }
                    }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull()
                    if (v != null) return@withTimeoutOrNull i to v
                }
                null
            }
            if (found != null) {
                val (i, v) = found
                val type = BACKUP_TYPES[i]
                backupTypeIndex = i
                source = Source.BACKUP
                adState = if (v.height < (want?.height ?: 0)) "Реклама: показываю эфир в ${v.label}" else "Реклама пропущена"
                diag.adBreaks++
                DiagLog.log("Резервный поток: $type ${v.label}")
                playUrl(v, "резерв $type")
                startPrimaryPolling()
                return@launch
            }
            fallBackToAds(want, playerStarted)
        }
    }

    /**
     * No clean source (all backups had ads, failed, or the search timed out): stability first —
     * play Twitch's ad instead of freezing. Always ends in a state where the player has a source.
     */
    private fun fallBackToAds(want: Variant?, playerStarted: Boolean) {
        DiagLog.log("Чистый поток не найден: показываю рекламу Twitch до конца блока")
        adState = "Реклама Twitch (обойти не удалось)"
        adPassThrough = true
        when (AdFallback.decide(onBackup = source == Source.BACKUP, hasActiveSource = playerStarted && activeUrl != null)) {
            AdFallback.Action.KEEP_BACKUP_WAIT_PRIMARY -> startPrimaryPolling()
            AdFallback.Action.KEEP_PRIMARY_WAIT_AD_END -> startAdEndPolling()
            AdFallback.Action.START_PRIMARY_WITH_ADS -> {
                // Came from startPrimary(): the player was never started. Start the primary
                // stream now, ads included, then watch for the end of the break.
                val v = want ?: primaryVariants.getOrNull(autoCap) ?: primaryVariants.firstOrNull()
                if (v == null) { scheduleRetry("нет вариантов потока"); return }
                source = Source.PRIMARY
                backupTypeIndex = -1
                playUrl(v, "основной, с рекламой")
                startAdEndPolling()
            }
        }
    }

    /** While on backup (or passing an ad through): watch the primary playlist for the end of the break. */
    private fun startPrimaryPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            var clean = 0
            while (isActive) {
                delay(4_000)
                val want = userChoice?.let { c -> Hls.closest(primaryVariants, c.height, c.fps) }
                    ?: primaryVariants.getOrNull(autoCap) ?: primaryVariants.firstOrNull() ?: continue
                val r = withContext(Dispatchers.IO) { runCatching { Net.getText(want.url) }.getOrNull() }
                if (r == null) continue
                if (!r.ok) { // token expired → refresh primary list
                    runCatching { primaryVariants = playable(resolvePrimary()); variants = primaryVariants }
                    continue
                }
                if (AdFilter.hasAds(r.body)) { clean = 0; continue }
                if (++clean >= 2) {
                    DiagLog.log("Рекламный блок закончился, возвращаюсь к основному потоку")
                    adPassThrough = false
                    adState = ""
                    source = Source.PRIMARY
                    backupTypeIndex = -1
                    playUrl(want, "основной после рекламы")
                    return@launch
                }
            }
        }
    }

    private fun startAdEndPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(4_000)
                val url = activeUrl ?: continue
                val r = withContext(Dispatchers.IO) { runCatching { Net.getText(url) }.getOrNull() } ?: continue
                if (r.ok && !AdFilter.hasAds(r.body)) {
                    adPassThrough = false; adState = ""; return@launch
                }
            }
        }
    }

    // =====================================================================================
    // Quality
    // =====================================================================================
    fun selectQuality(choice: QualityChoice?) {
        userChoice = choice
        if (primaryVariants.isEmpty()) return
        if (choice == null) { autoCap = 0; autoCapLabel = null }
        val v = pickVariant(primaryVariants)
        DiagLog.log("Выбрано качество: ${choice?.let { "${it.height}p${it.fps}" } ?: "Авто"}")
        if (source == Source.BACKUP) return // applied when the ad break ends
        if (v.url == activeUrl) return
        playUrl(v, "смена качества")
    }

    private fun autoEnabled() = userChoice == null && prefs.autoQuality

    private fun noteRebuffer(durationMs: Long) {
        val now = SystemClock.elapsedRealtime()
        rebuffers.addLast(now); lastRebufferAt = now
        while (rebuffers.isNotEmpty() && now - rebuffers.first() > 60_000) rebuffers.removeFirst()
        diag.rebuffers++
        if (!autoEnabled() || source != Source.PRIMARY) return
        val bad = rebuffers.size >= 2 || durationMs > 5_000
        if (bad && autoCap < primaryVariants.lastIndex && now - lastSwitchAt > 15_000) {
            val failed = primaryVariants[autoCap]
            levelFailures.getOrPut(failed.label + failed.codecLabel) { mutableListOf() }.add(now)
            autoCap++
            val v = primaryVariants[autoCap]
            autoCapLabel = v.label
            rebuffers.clear()
            DiagLog.log("Авто: нестабильно, снижаю до ${v.label}")
            playUrl(v, "авто вниз")
        }
    }

    private fun maybeStepUp(now: Long) {
        if (!autoEnabled() || source != Source.PRIMARY || autoCap == 0) return
        if (now - lastStepUpCheck < 10_000) return
        lastStepUpCheck = now
        if (now - lastRebufferAt < 180_000 || now - lastSwitchAt < 180_000) return
        val up = primaryVariants[autoCap - 1]
        val fails = levelFailures[up.label + up.codecLabel]?.count { now - it < 600_000 } ?: 0
        if (fails >= 2) return
        val est = bandwidthMeter.bitrateEstimate
        if (est > 0 && est < up.bandwidth * 1.3) return
        autoCap--
        autoCapLabel = if (autoCap == 0) null else up.label
        DiagLog.log("Авто: стабильно 3 мин, повышаю до ${up.label}")
        playUrl(up, "авто вверх")
    }

    // =====================================================================================
    // Stability: errors, retries, watchdog
    // =====================================================================================
    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            val now = SystemClock.elapsedRealtime()
            when (state) {
                Player.STATE_READY -> {
                    if (bufferingSince > 0 && wasReady) noteRebuffer(now - bufferingSince)
                    bufferingSince = 0
                    wasReady = true
                    backoffStep = 0
                    status = PlayStatus.PLAYING; statusText = ""
                }
                Player.STATE_BUFFERING -> {
                    bufferingSince = now
                    if (wasReady) { status = PlayStatus.BUFFERING; statusText = "Буферизация…" }
                }
                Player.STATE_ENDED -> {
                    DiagLog.log("Плейлист завершён (конец эфира?) — проверяю")
                    scheduleRetry("поток завершился")
                }
                else -> {}
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            diag.lastError = "${error.errorCodeName}: ${error.cause?.javaClass?.simpleName ?: ""} ${error.cause?.message ?: ""}".trim()
            DiagLog.log("Ошибка плеера: ${diag.lastError}")
            val cause = error.cause
            when {
                error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                    player.seekToDefaultPosition(); player.prepare()
                }
                error.errorCode in DECODER_ERRORS && current?.isHevc == true -> {
                    hevcBroken = true
                    DiagLog.log("HEVC-декодер не справился — перехожу на H.264")
                    primaryVariants = primaryVariants.filter { !it.isHevc && !it.isAv1 }
                    variants = primaryVariants
                    autoCap = 0
                    primaryVariants.firstOrNull()?.let { playUrl(it, "без HEVC") } ?: scheduleRetry("декодер")
                }
                cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode in listOf(403, 404, 410) -> {
                    // Token expired or stream restarted → fresh token/playlist.
                    reResolve("HTTP ${cause.responseCode}")
                }
                else -> scheduleRetry(error.errorCodeName)
            }
        }
    }

    private fun reResolve(reason: String) {
        cancelStreamJobs(keepWatchdog = true)
        reconnects++
        status = PlayStatus.RECONNECTING; statusText = "Переподключение…"
        DiagLog.log("Обновляю поток: $reason")
        source = Source.PRIMARY; adPassThrough = false
        openJob = scope.launch { resolveAndPlay(initial = false) }
    }

    private fun scheduleRetry(reason: String) {
        if (retryJob?.isActive == true) return
        val delays = longArrayOf(1_000, 2_000, 4_000, 8_000, 15_000)
        val d = delays[backoffStep.coerceAtMost(delays.lastIndex)]
        backoffStep++
        reconnects++
        status = PlayStatus.RECONNECTING
        statusText = "Нет связи с Twitch, повтор через ${d / 1000} с"
        DiagLog.log("Повтор через ${d}мс ($reason), попытка $backoffStep")
        retryJob = scope.launch {
            delay(d)
            if (primaryVariants.isEmpty() || backoffStep % 3 == 0) reResolve("повтор $backoffStep")
            else {
                player.seekToDefaultPosition()
                player.prepare()
            }
        }
    }

    private fun retryNow(reason: String) {
        retryJob?.cancel()
        reResolve(reason)
    }

    private fun recover(reason: String) {
        val now = SystemClock.elapsedRealtime()
        recoverTimes.addLast(now)
        while (recoverTimes.isNotEmpty() && now - recoverTimes.first() > 60_000) recoverTimes.removeFirst()
        DiagLog.log("Сторож: $reason")
        if (recoverTimes.size >= 3) { recoverTimes.clear(); reResolve("3 зависания за минуту") ; return }
        reconnects++
        bufferingSince = 0; frozenSince = 0
        player.seekToDefaultPosition()
        player.prepare()
        player.playWhenReady = true
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            var lastRendered = 0; var lastTick = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(1_000)
                val now = SystemClock.elapsedRealtime()
                // FPS from real decoder counters
                player.videoDecoderCounters?.let { c ->
                    c.ensureUpdated()
                    val dt = (now - lastTick).coerceAtLeast(1)
                    val rendered = c.renderedOutputBufferCount
                    if (rendered >= lastRendered) diag.renderFps = (rendered - lastRendered) * 1000.0 / dt
                    lastRendered = rendered
                    diag.droppedFrames = c.droppedBufferCount
                }
                lastTick = now
                diag.bufferMs = player.totalBufferedDuration
                diag.liveOffsetMs = player.currentLiveOffset.takeIf { it != C.TIME_UNSET && it >= 0 }
                diag.bandwidthEstimate = bandwidthMeter.bitrateEstimate
                diag.reconnects = reconnects
                diag.vpn = isVpnActive()
                diag.source = if (source == Source.BACKUP) "резерв ${BACKUP_TYPES.getOrNull(backupTypeIndex)}" else "основной ($primaryType)"

                if (!player.playWhenReady || status == PlayStatus.OFFLINE) continue
                when (player.playbackState) {
                    Player.STATE_BUFFERING -> if (bufferingSince > 0 && now - bufferingSince > stallTimeoutMs) recover("буферизация дольше ${stallTimeoutMs / 1000} с")
                    Player.STATE_READY -> {
                        val pos = player.currentPosition
                        if (pos == lastPos) {
                            if (frozenSince == 0L) frozenSince = now
                            else if (now - frozenSince > 8_000) recover("картинка стоит 8 с")
                        } else frozenSince = 0
                        lastPos = pos
                        maybeStepUp(now)
                    }
                    else -> {}
                }
            }
        }
    }

    // =====================================================================================
    // Diagnostics feed
    // =====================================================================================
    private val analytics = object : AnalyticsListener {
        override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
            diag.width = format.width; diag.height = format.height
            diag.codec = format.codecs ?: format.sampleMimeType
            diag.formatFps = format.frameRate.takeIf { it > 0 }?.toDouble()
        }
        override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
            diag.decoder = decoderName
            DiagLog.log("Декодер: $decoderName")
        }
        override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
            if (mediaLoadData.dataType == C.DATA_TYPE_MEDIA) diag.onSegment(loadEventInfo.bytesLoaded, SystemClock.elapsedRealtime())
        }
    }

    // =====================================================================================
    // Lifecycle
    // =====================================================================================
    fun pause() { player.playWhenReady = false }
    fun resumeLive() {
        if (status == PlayStatus.OFFLINE) return
        player.seekToDefaultPosition(); player.playWhenReady = true; player.prepare()
    }

    private fun cancelStreamJobs(keepWatchdog: Boolean = false) {
        openJob?.cancel(); backupJob?.cancel(); pollJob?.cancel(); retryJob?.cancel()
        if (!keepWatchdog) watchdogJob?.cancel()
    }

    fun release() {
        cancelStreamJobs()
        scope.cancel()
        runCatching { connectivity?.unregisterNetworkCallback(netCallback) }
        player.removeListener(listener)
        player.removeAnalyticsListener(analytics)
        player.release()
        DiagLog.log("Плеер освобождён")
    }

    init {
        runCatching { connectivity?.registerDefaultNetworkCallback(netCallback) }
        player.addAnalyticsListener(analytics)
        player.addListener(listener)
    }

    companion object {
        val BACKUP_TYPES = listOf("embed", "popout", "autoplay")
        /** Below ExoPlayer's playlist-stuck limit (~3.5 × target duration ≈ 21 s on Twitch). */
        const val BACKUP_SEARCH_TIMEOUT_MS = 10_000L
        private val DECODER_ERRORS = setOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        )
    }
}

/** Measured values only; null/negative = not measurable on this device/stream. */
class Diagnostics {
    var width by mutableIntStateOf(0)
    var height by mutableIntStateOf(0)
    var codec by mutableStateOf<String?>(null)
    var decoder by mutableStateOf<String?>(null)
    var formatFps by mutableStateOf<Double?>(null)
    var renderFps by mutableStateOf<Double?>(null)
    var droppedFrames by mutableIntStateOf(0)
    var bufferMs by mutableStateOf(0L)
    var liveOffsetMs by mutableStateOf<Long?>(null)
    var bandwidthEstimate by mutableStateOf(0L)
    var actualBitrate by mutableStateOf<Long?>(null)
    var reconnects by mutableIntStateOf(0)
    var rebuffers by mutableIntStateOf(0)
    var lastError by mutableStateOf<String?>(null)
    var adBreaks by mutableIntStateOf(0)
    var adSegmentsStripped by mutableIntStateOf(0)
    var source by mutableStateOf("")
    /** The app's default network (per-UID, so per-app VPN rules apply) has TRANSPORT_VPN. */
    var vpn by mutableStateOf(false)
    /** USER-COUNTRY from Twitch's usher response for the current stream; null = not reported. */
    var twitchCountry by mutableStateOf<String?>(null)

    private val window = ArrayDeque<Pair<Long, Long>>() // (time, bytes)

    fun onSegment(bytes: Long, now: Long) {
        synchronized(window) {
            window.addLast(now to bytes)
            while (window.isNotEmpty() && now - window.first().first > 12_000) window.removeFirst()
            if (window.size >= 3) {
                val span = (now - window.first().first).coerceAtLeast(1)
                val total = window.drop(1).sumOf { it.second }
                actualBitrate = total * 8_000 / span
            }
        }
    }

    fun reset() {
        width = 0; height = 0; codec = null; decoder = null; formatFps = null; renderFps = null
        droppedFrames = 0; liveOffsetMs = null; actualBitrate = null; lastError = null
        rebuffers = 0; adBreaks = 0; adSegmentsStripped = 0; twitchCountry = null
        synchronized(window) { window.clear() }
    }

    fun asText(): String = buildString {
        appendLine("Разрешение: ${if (height > 0) "${width}x$height" else "—"}")
        appendLine("Кодек: ${codec ?: "—"}  декодер: ${decoder ?: "—"}")
        appendLine("FPS (вывод): ${renderFps?.let { "%.1f".format(it) } ?: "—"}  поток: ${formatFps?.let { "%.0f".format(it) } ?: "—"}")
        appendLine("Пропущено кадров: $droppedFrames")
        appendLine("Буфер: ${"%.1f".format(bufferMs / 1000.0)} с")
        appendLine("Битрейт факт.: ${actualBitrate?.let { "${it / 1000} кбит/с" } ?: "—"}  оценка канала: ${if (bandwidthEstimate > 0) "${bandwidthEstimate / 1000} кбит/с" else "—"}")
        appendLine("Задержка от эфира: ${liveOffsetMs?.let { "%.1f с".format(it / 1000.0) } ?: "недоступна"}")
        appendLine("Переподключений: $reconnects  ребуферов: $rebuffers")
        appendLine("Источник: $source")
        // Honest wording: a VPN interface on the app's network does not prove Happ sends Twitch
        // through the proxy (Happ's own rules may route these domains direct). The country Twitch
        // reports is measured, but only for the usher request, not for every video segment.
        appendLine("VPN-интерфейс у приложения: ${if (vpn) "да" else "нет"}")
        appendLine("Страна по данным Twitch (usher): ${twitchCountry ?: "нет данных"}")
        appendLine("Рекламных блоков обойдено: $adBreaks  вырезано сегментов: $adSegmentsStripped")
        appendLine("Ошибка: ${lastError ?: "нет"}")
    }
}

/** Pure decision for the "no clean stream during an ad" case; unit-tested. */
internal object AdFallback {
    enum class Action { KEEP_BACKUP_WAIT_PRIMARY, KEEP_PRIMARY_WAIT_AD_END, START_PRIMARY_WITH_ADS }
    fun decide(onBackup: Boolean, hasActiveSource: Boolean): Action = when {
        onBackup && hasActiveSource -> Action.KEEP_BACKUP_WAIT_PRIMARY
        hasActiveSource -> Action.KEEP_PRIMARY_WAIT_AD_END
        else -> Action.START_PRIMARY_WITH_ADS
    }
}
