package su.expensive.twitchtv.data

/** One rendition from Twitch's multivariant playlist. */
data class Variant(
    val name: String,          // "1080p60 (source)", "1440p60", "720p60", "audio_only"
    val groupId: String,       // "chunked", "720p60", ...
    val width: Int,
    val height: Int,
    val fps: Double,
    val bandwidth: Long,
    val codecs: String,
    val url: String,
) {
    val isAudioOnly get() = height == 0 || groupId == "audio_only"
    val isHevc get() = codecs.startsWith("hvc1") || codecs.startsWith("hev1") || codecs.contains(",hvc1") || codecs.contains(",hev1")
    val isAv1 get() = codecs.startsWith("av01")
    val isSource get() = name.contains("source", true) || groupId == "chunked"
    val fpsRounded get() = Math.round(fps).toInt()

    /** e.g. "1440p60", "1080p60", "720p" */
    val label: String
        get() = if (isAudioOnly) "Только звук" else "${height}p" + (if (fpsRounded > 30) fpsRounded.toString() else "")

    val codecLabel: String get() = when { isHevc -> "HEVC"; isAv1 -> "AV1"; isAudioOnly -> "AAC"; else -> "H.264" }
}

object Hls {
    private val attrRegex = Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""")

    fun parseAttributes(line: String): Map<String, String> {
        val body = line.substringAfter(':', "")
        return attrRegex.findAll(body).associate { it.groupValues[1] to it.groupValues[2].trim('"') }
    }

    /** Parses the usher multivariant playlist. Order: best first (as Twitch lists them). */
    /**
     * Attributes of Twitch's #EXT-X-TWITCH-INFO line in the multivariant playlist (USER-COUNTRY,
     * NODE, …). USER-COUNTRY is Twitch's own geolocation of the address the usher request came
     * from — the only routing evidence the app can measure without a third-party service.
     */
    fun twitchInfo(text: String): Map<String, String> =
        text.lineSequence().firstOrNull { it.startsWith("#EXT-X-TWITCH-INFO:") }
            ?.let { parseAttributes(it) } ?: emptyMap()

    fun parseMultivariant(text: String): List<Variant> {
        val lines = text.replace("\r", "").split('\n')
        val names = HashMap<String, String>()
        lines.filter { it.startsWith("#EXT-X-MEDIA:") }.forEach {
            val a = parseAttributes(it)
            val g = a["GROUP-ID"]; val n = a["NAME"]
            if (g != null && n != null) names[g] = n
        }
        val out = ArrayList<Variant>()
        for (i in lines.indices) {
            val l = lines[i]
            if (!l.startsWith("#EXT-X-STREAM-INF:")) continue
            val url = lines.getOrNull(i + 1)?.trim().orEmpty()
            if (!url.startsWith("http")) continue
            val a = parseAttributes(l)
            val res = a["RESOLUTION"]?.split('x')
            val group = a["VIDEO"] ?: ""
            out += Variant(
                name = names[group] ?: group,
                groupId = group,
                width = res?.getOrNull(0)?.toIntOrNull() ?: 0,
                height = res?.getOrNull(1)?.toIntOrNull() ?: 0,
                fps = a["FRAME-RATE"]?.toDoubleOrNull() ?: 30.0,
                bandwidth = a["BANDWIDTH"]?.toLongOrNull() ?: 0,
                codecs = a["CODECS"] ?: "",
                url = url,
            )
        }
        return out
    }

    /** Video variants sorted best → worst (height, fps, then HEVC over AVC at equal size). */
    fun sortVideo(v: List<Variant>): List<Variant> =
        v.filter { !it.isAudioOnly }.sortedWith(
            compareByDescending<Variant> { it.height }.thenByDescending { it.fpsRounded }.thenByDescending { it.bandwidth })

    /** Closest variant to a target height/fps (used to keep quality when switching playlists). */
    fun closest(v: List<Variant>, height: Int, fps: Int): Variant? {
        val video = v.filter { !it.isAudioOnly }
        return video.firstOrNull { it.height == height && it.fpsRounded == fps }
            ?: video.minByOrNull { Math.abs(it.height - height) * 10 + Math.abs(it.fpsRounded - fps) }
    }
}

/**
 * Twitch server-side ad ("stitched ad") detection and stripping for media playlists.
 *
 * Twitch marks every real stream segment `#EXTINF:<dur>,live`. Ad segments carry another title
 * (e.g. `Amazon|<id>`) and the playlist contains `#EXT-X-DATERANGE ... CLASS="twitch-stitched-ad"`.
 * Same signals TwitchAdSolutions/VAFT relies on (AdSignifier = "stitched", ",live" check).
 */
object AdFilter {
    fun hasAds(playlist: String): Boolean {
        if (playlist.contains("stitched-ad") || playlist.contains("twitch-stitched-ad")) return true
        return playlist.lineSequence().any { it.startsWith("#EXTINF") && isAdExtinf(it) }
    }

    private fun isAdExtinf(line: String): Boolean {
        val title = line.substringAfter(',', "")
        // Only judge when Twitch actually labels segments; unlabeled playlists are not treated as ads.
        return title.isNotEmpty() && !title.startsWith("live")
    }

    fun liveSegmentCount(playlist: String) = playlist.lineSequence().count { it.startsWith("#EXTINF") && !isAdExtinf(it) }

    data class Stripped(val text: String, val removed: Int, val remaining: Int)

    /**
     * Remembers which original media-sequence numbers were ads and which discontinuities were
     * dropped, across refreshes of one stream session. With it every live segment gets the same
     * output number on every refresh (orig − ads before it), even when an ad sits in the middle of
     * the window and later slides out of it. Without it a mid-playlist ad would renumber the live
     * segments behind it differently from refresh to refresh, and the player would repeat or skip
     * segments. Twitch numbers segments identically across variants, so one tracker per session
     * (not per variant) also keeps variant switches aligned. Thread-safe.
     */
    class Tracker {
        private val adSeqs = java.util.TreeSet<Long>()
        private val droppedDiscs = java.util.TreeSet<Long>()
        @Synchronized fun addAd(seq: Long) { adSeqs += seq }
        @Synchronized fun addDroppedDisc(n: Long) { droppedDiscs += n }
        @Synchronized fun adsBefore(seq: Long): Int = adSeqs.headSet(seq, false).size
        @Synchronized fun discsDroppedUpTo(n: Long): Int = droppedDiscs.headSet(n, true).size
        @Synchronized fun reset() { adSeqs.clear(); droppedDiscs.clear() }
    }

    /**
     * Removes ad segments (EXTINF + their tags + URI), stitched-ad DATERANGEs and low-latency
     * prefetch hints.
     *
     * - EXT-X-MEDIA-SEQUENCE: rewritten so live segment N keeps number N − (ads before N), stable
     *   across refreshes via [tracker].
     * - EXT-X-DISCONTINUITY: a discontinuity whose next segment is an ad is dropped (and counted in
     *   [tracker]); one whose next segment is live is kept, so each live→ad→live junction leaves
     *   exactly one discontinuity (live timestamps really jump there). EXT-X-DISCONTINUITY-SEQUENCE
     *   is rewritten to stay consistent with the dropped ones.
     * - EXT-X-MAP (fMP4): the init section in effect is re-emitted before the first live segment
     *   and whenever it changes, so live media never inherits an ad's init segment.
     */
    fun strip(playlist: String, tracker: Tracker = Tracker()): Stripped {
        val lines = playlist.replace("\r", "").split('\n')
        val mediaSeq = lines.firstOrNull { it.startsWith("#EXT-X-MEDIA-SEQUENCE:") }
            ?.substringAfter(':')?.trim()?.toLongOrNull() ?: 0L
        val discSeq = lines.firstOrNull { it.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE:") }
            ?.substringAfter(':')?.trim()?.toLongOrNull() ?: 0L

        val header = ArrayList<String>()           // lines before the first segment-related tag
        val body = ArrayList<String>()
        val pending = ArrayList<String>()          // per-segment tags waiting for their EXTINF
        val pendingDiscs = ArrayList<Long>()       // numbers of DISCONTINUITY tags in pending
        var removed = 0; var remaining = 0
        var segIdx = 0L; var disc = discSeq
        var firstSeq: Long? = null; var firstDisc = 0L; var discsBeforeFirst = 0
        var currentMap: String? = null; var emittedMap: String? = null
        var inBody = false
        var i = 0
        while (i < lines.size) {
            val l = lines[i]
            when {
                l.startsWith("#EXT-X-MEDIA-SEQUENCE:") || l.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE:") -> {} // re-emitted below
                l.startsWith("#EXT-X-MAP") -> { currentMap = l; inBody = true }
                l.startsWith("#EXT-X-TWITCH-PREFETCH") -> {}
                l.startsWith("#EXT-X-DATERANGE") && l.contains("stitched") -> {}
                l == "#EXT-X-DISCONTINUITY" || (l.startsWith("#EXT-X-DISCONTINUITY") && !l.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE")) -> {
                    disc++; pending += l; pendingDiscs += disc; inBody = true
                }
                l.startsWith("#EXT-X-PROGRAM-DATE-TIME") -> { pending += l; inBody = true }
                l.startsWith("#EXTINF") -> {
                    inBody = true
                    val uri = lines.getOrNull(i + 1) ?: ""
                    val seq = mediaSeq + segIdx
                    if (isAdExtinf(l)) {
                        removed++
                        tracker.addAd(seq)
                        pendingDiscs.forEach { tracker.addDroppedDisc(it) }
                    } else {
                        remaining++
                        if (firstSeq == null) { firstSeq = seq; firstDisc = disc; discsBeforeFirst = pendingDiscs.size }
                        body += pending
                        if (currentMap != null && currentMap != emittedMap) { body += currentMap!!; emittedMap = currentMap }
                        body += l; body += uri
                    }
                    pending.clear(); pendingDiscs.clear()
                    segIdx++
                    i++ // URI line consumed
                }
                l.isBlank() -> {}
                !inBody -> header += l
                // Other tags in the segment area travel with the segment that follows them
                // (dropped together with an ad).
                else -> pending += l
            }
            i++
        }

        val outSeq: Long; val outDisc: Long
        if (firstSeq != null) {
            outSeq = firstSeq!! - tracker.adsBefore(firstSeq!!)
            outDisc = firstDisc - tracker.discsDroppedUpTo(firstDisc) - discsBeforeFirst
        } else {
            outSeq = mediaSeq + segIdx - tracker.adsBefore(mediaSeq + segIdx)
            outDisc = disc - tracker.discsDroppedUpTo(disc)
        }
        // Trailing tags with no segment after them (e.g. #EXT-X-ENDLIST). Trailing
        // DISCONTINUITY tags are held back: whether they survive depends on the next segment.
        body += pending.filterNot { it.startsWith("#EXT-X-DISCONTINUITY") || it.startsWith("#EXT-X-PROGRAM-DATE-TIME") }
        val out = ArrayList<String>(header.size + body.size + 2)
        out += header
        out += "#EXT-X-MEDIA-SEQUENCE:$outSeq"
        if (outDisc != 0L || lines.any { it.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE:") }) out += "#EXT-X-DISCONTINUITY-SEQUENCE:$outDisc"
        out += body
        return Stripped(out.joinToString("\n"), removed, remaining)
    }
}
