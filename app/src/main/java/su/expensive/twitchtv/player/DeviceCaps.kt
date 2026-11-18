package su.expensive.twitchtv.player

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

/** What the TV's decoders can actually do. Results are cached for the process lifetime. */
object DeviceCaps {
    data class Codec(val name: String, val hardware: Boolean, val maxW: Int, val maxH: Int, val fps1440: Boolean)

    private fun isHardware(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= 29) return info.isHardwareAccelerated && !info.isSoftwareOnly
        val n = info.name.lowercase()
        return !(n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.contains(".sw.") || n.startsWith("omx.ffmpeg"))
    }

    private fun decoders(mime: String): List<Codec> = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { !it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, true) } }
            .mapNotNull { info ->
                val caps = runCatching { info.getCapabilitiesForType(mime).videoCapabilities }.getOrNull() ?: return@mapNotNull null
                Codec(
                    name = info.name,
                    hardware = isHardware(info),
                    maxW = caps.supportedWidths.upper,
                    maxH = caps.supportedHeights.upper,
                    fps1440 = runCatching { caps.areSizeAndRateSupported(2560, 1440, 60.0) }.getOrDefault(false),
                )
            }
    }.getOrDefault(emptyList())

    val avc: List<Codec> by lazy { decoders("video/avc") }
    val hevc: List<Codec> by lazy { decoders("video/hevc") }
    val av1: List<Codec> by lazy { decoders("video/av01") }

    /** HEVC is only offered when a hardware decoder exists (software HEVC at 1440p60 would stutter on a TV SoC). */
    val hevcHardware: Boolean get() = hevc.any { it.hardware }
    val hevc1440p60: Boolean get() = hevc.any { it.hardware && it.fps1440 }
    val av1Hardware: Boolean get() = av1.any { it.hardware }

    /**
     * A hardware decoder accepts this rendition's size. Frame rate is deliberately not required:
     * many TV SoCs under-report performance points, so 1440p60 is tried and [QualityPolicy.HiResGuard]
     * falls back to 1080p60 if the TV really cannot keep up.
     */
    fun supports(v: su.expensive.twitchtv.data.Variant): Boolean {
        val mime = when { v.isHevc -> "video/hevc"; v.isAv1 -> "video/av01"; else -> "video/avc" }
        val w = if (v.width > 0) v.width else v.height * 16 / 9
        return supportsCache.getOrPut("$mime $w x ${v.height}") {
            runCatching {
                MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                    !info.isEncoder && isHardware(info) && info.supportedTypes.any { it.equals(mime, true) } &&
                        runCatching { info.getCapabilitiesForType(mime).videoCapabilities.isSizeSupported(w, v.height) }.getOrDefault(false)
                }
            }.getOrDefault(false)
        }
    }
    private val supportsCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun usherCodecs(allowHevc: Boolean): String = buildList {
        if (allowHevc && av1Hardware) add("av1")
        if (allowHevc && hevcHardware) add("h265")
        add("h264")
    }.joinToString(",")

    fun summary(): String =
        "AVC: ${avc.joinToString { it.name + if (it.hardware) "(hw)" else "(sw)" }}\n" +
            "HEVC: ${hevc.joinToString { it.name + if (it.hardware) "(hw)" else "(sw)" }.ifEmpty { "нет" }}; 1440p60=${hevc1440p60}\n" +
            "AV1: ${av1.joinToString { it.name + if (it.hardware) "(hw)" else "(sw)" }.ifEmpty { "нет" }}\n" +
            "Устройство: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
}
