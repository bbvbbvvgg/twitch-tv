package su.expensive.twitchtv.player

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * In-memory ring log (no disk writes unless the user exports). Every line is sanitized:
 * Twitch playback tokens/signatures, OAuth headers and long opaque segment ids never get stored.
 */
object DiagLog {
    private const val MAX = 400
    private val lines = ArrayDeque<String>(MAX)
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val secretPatterns = listOf(
        Regex("""(?i)\b(sig|token|nauth|nauthsig|signature|access_token|oauth|auth|password|pass)=([^&\s"]+)""") to "$1=<скрыто>",
        // ReYohoho token header and proxy credentials (Proxy-Authorization / user:pass@host).
        Regex("""(?i)(x-auth-token|proxy-authorization)\s*[:=]\s*(?:Basic\s+)?\S+""") to "$1: <скрыто>",
        Regex("""(?i)\b(https?|socks5?)://[^\s/:@]+:[^\s/@]+@""") to "$1://<скрыто>@",
        Regex("""(?i)(authorization|client-integrity)\s*[:=]\s*(?:(?:OAuth|Bearer)\s+)?\S+""") to "$1: <скрыто>",
        Regex("""(?i)\b(OAuth|Bearer)\s+[a-z0-9]{20,}""") to "$1 <скрыто>",
        Regex("""(?i)"(access_token|refresh_token|device_code)"\s*:\s*"[^"]*"""") to "\"$1\":\"<скрыто>\"",
        Regex("""(?i)(refresh_token|device_code)=([^&\s"]+)""") to "$1=<скрыто>",
        Regex("""(?i)"(value|signature)"\s*:\s*"[^"]*"""") to "\"$1\":\"<скрыто>\"",
        // Media playlist / segment URLs embed signed paths; keep host only.
        Regex("""https://([a-z0-9.-]+\.ttvnw\.net|[a-z0-9.-]+\.cloudfront\.net)/[^\s"]+""") to "https://$1/…",
        Regex("""https://([a-z0-9.-]+\.live-video\.net)/[^\s"]+""") to "https://$1/…",
        // The user's public IP (Twitch echoes it in USER-IP) never goes into an exported log.
        Regex("""(?i)(USER-IP\s*=\s*)"?[0-9a-f.:]+"?""") to "$1<скрыто>",
        Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""") to "<ip>",
    )

    fun sanitize(s: String): String = secretPatterns.fold(s) { acc, (r, rep) -> r.replace(acc, rep) }

    @Synchronized
    fun log(msg: String) {
        if (lines.size >= MAX) lines.removeFirst()
        lines.addLast(fmt.format(Date()) + "  " + sanitize(msg))
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()
}
