package su.expensive.twitchtv.player

import su.expensive.twitchtv.data.Variant

/** Pure rules for >1080p playback; unit-tested. */
internal object QualityPolicy {
    fun maxHeight(v: List<Variant>): Int = v.filter { !it.isAudioOnly }.maxOfOrNull { it.height } ?: 0

    /**
     * Watches decoder counters while a >1080p rendition plays. Falls back when, after a 5 s
     * warm-up, the TV drops ≥ 30 frames within 10 s, or renders below 80 % of the stream's frame
     * rate for 15 s in a row. Returns the reason once, then stays silent until [reset].
     */
    class HiResGuard {
        private var startAt = 0L
        private val drops = ArrayDeque<Pair<Long, Int>>()
        private var slowSince = 0L

        fun reset() { startAt = 0L; drops.clear(); slowSince = 0L }

        fun onTick(now: Long, droppedTotal: Int, renderFps: Double?, streamFps: Double): String? {
            if (startAt == 0L) { startAt = now; drops.clear(); drops.addLast(now to droppedTotal); return null }
            drops.addLast(now to droppedTotal)
            while (drops.size > 1 && now - drops.first().first > 10_000) drops.removeFirst()
            if (now - startAt < 5_000) return null
            val dropped = droppedTotal - drops.first().second
            if (dropped >= 30) return "пропущено $dropped кадров за ${(now - drops.first().first) / 1000} с"
            if (renderFps != null && streamFps > 0 && renderFps < streamFps * 0.8) {
                if (slowSince == 0L) slowSince = now
                else if (now - slowSince >= 15_000) return "вывод %.0f из %.0f fps 15 с".format(renderFps, streamFps)
            } else slowSince = 0L
            return null
        }
    }
}
