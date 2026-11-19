package su.expensive.twitchtv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import su.expensive.twitchtv.data.Hls
import su.expensive.twitchtv.data.HttpText
import su.expensive.twitchtv.data.Net
import su.expensive.twitchtv.data.SecretCodec
import su.expensive.twitchtv.net.ConnectionEngine
import su.expensive.twitchtv.net.ConnectionEngine.NetSnapshot
import su.expensive.twitchtv.net.ConnectionMode
import su.expensive.twitchtv.net.CustomProxy
import su.expensive.twitchtv.net.NetSettings
import su.expensive.twitchtv.net.ProxyKind
import su.expensive.twitchtv.net.Reyohoho
import su.expensive.twitchtv.net.Route
import su.expensive.twitchtv.player.DiagLog
import java.io.File
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * The 2.1 connection layer: direct / system VPN, ReYohoho with consent, own proxy.
 * Engine tests use a fake transport that records every request, so "the token was never sent"
 * is checked on the exact requests, not inferred.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.LEGACY)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@org.robolectric.annotation.Config(sdk = [34])
class ConnectionTest {
    private val token = "oauthsecret0123456789abcdefghij"
    private val master = "#EXTM3U\n#EXT-X-SESSION-DATA:DATA-ID=\"USER-COUNTRY\",VALUE=\"DE\"\n" +
        "#EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID=\"chunked\",NAME=\"1080p60 (source)\",AUTOSELECT=YES,DEFAULT=YES\n" +
        "#EXT-X-STREAM-INF:BANDWIDTH=8000000,RESOLUTION=1920x1080,CODECS=\"avc1.64002A,mp4a.40.2\",VIDEO=\"chunked\",FRAME-RATE=60.000\n" +
        "https://euw12.playlist.ttvnw.net/v1/playlist/abc.m3u8\n"
    private val directMaster = master.replace("DE", "RU").replace("1920x1080", "1280x720").replace("1080p60", "720p60")

    private class Req(val url: String, val headers: Map<String, String>)

    /** Scripted transport: each URL prefix answers with the next queued outcome. */
    private class FakeNet {
        val log = ArrayList<Req>()
        val script = ArrayList<(Req) -> HttpText?>()
        var fallback: (Req) -> HttpText = { HttpText(200, "") }
        fun get(url: String, h: Map<String, String>): HttpText {
            val r = Req(url, h); log += r
            val step = if (script.isNotEmpty()) script.removeAt(0) else null
            return step?.invoke(r) ?: throw SocketTimeoutException("timeout")
        }
    }

    private fun engine(snap: NetSnapshot, net: FakeNet, clock: () -> Long = { 1_000L }) =
        ConnectionEngine({ snap }, net::get, clock)

    private var directCalls = 0
    private val direct: () -> HttpText = { directCalls++; HttpText(200, directMaster) }

    @Before fun setUp() { directCalls = 0; Net.initForTests() }

    // ---------- direct / system VPN ----------

    @Test fun directModeNeverTouchesProxyNorToken() {
        val net = FakeNet()
        var tokenRead = false
        val r = engine(NetSnapshot(ConnectionMode.DIRECT, "auto", consented = true, sendToken = true), net)
            .fetchMaster("ch", "h264", direct) { tokenRead = true; token }
        assertEquals(Route.DIRECT, r.route); assertEquals(1, directCalls)
        assertTrue("no proxy request", net.log.isEmpty()); assertFalse("token not even read", tokenRead)
    }

    @Test fun directModeWithSystemVpnIsTheSameCode() {
        // A system VPN (Happ) is below the app: the engine has no VPN branch, the request is identical.
        val net = FakeNet()
        val r = engine(NetSnapshot(ConnectionMode.DIRECT, "auto", true, true), net).fetchMaster("ch", "h264", direct) { token }
        assertEquals(Route.DIRECT, r.route); assertTrue(net.log.isEmpty())
    }

    @Test fun reyohohoWithoutConsentFallsToDirectAndSendsNothing() {
        val net = FakeNet()
        val r = engine(NetSnapshot(ConnectionMode.REYOHOHO, "auto", consented = false, sendToken = true), net)
            .fetchMaster("ch", "h264", direct) { token }
        assertEquals(Route.DIRECT, r.route); assertTrue("nothing went to ReYohoho before consent", net.log.isEmpty())
    }

    // ---------- ReYohoho ----------

    @Test fun reyohohoWithConsentSendsTokenOnlyInHeader() {
        val net = FakeNet().apply { script += { HttpText(200, master) } }
        val r = engine(NetSnapshot(ConnectionMode.REYOHOHO, "proxy5", true, true), net).fetchMaster("Streamer", "h265,h264", direct) { token }
        assertEquals(Route.REYOHOHO_AUTH, r.route); assertEquals("proxy5.rte.net.ru", r.server); assertEquals(0, directCalls)
        val req = net.log.single()
        assertTrue(req.url.startsWith("https://proxy5.rte.net.ru/https://usher.ttvnw.net/api/channel/hls/streamer.m3u8?"))
        assertFalse("token never in URL", req.url.contains(token))
        assertFalse("no sig/token params", Regex("[?&](sig|token|auth)=").containsMatchIn(req.url))
        assertEquals(token, req.headers[Reyohoho.TOKEN_HEADER])
        assertEquals("DE", Hls.twitchInfo(r.text.body)["USER-COUNTRY"])
    }

    @Test fun signedOutReyohohoIsAnonymous() {
        val net = FakeNet().apply { script += { HttpText(200, master) } }
        val r = engine(NetSnapshot(ConnectionMode.REYOHOHO, "auto", true, true), net).fetchMaster("ch", "h264", direct) { null }
        assertEquals(Route.REYOHOHO_ANON, r.route); assertTrue(net.log.single().headers.isEmpty())
    }

    @Test fun tokenTransferSwitchedOffIsAnonymous() {
        val net = FakeNet().apply { script += { HttpText(200, master) } }
        var read = false
        val r = engine(NetSnapshot(ConnectionMode.REYOHOHO, "auto", true, sendToken = false), net).fetchMaster("ch", "h264", direct) { read = true; token }
        assertEquals(Route.REYOHOHO_ANON, r.route); assertFalse(read); assertTrue(net.log.single().headers.isEmpty())
    }

    @Test fun rejectedTokenRetriesAnonymouslyOnceAndIsRemembered() {
        val net = FakeNet().apply { script += { HttpText(403, "") }; script += { HttpText(200, master) } }
        val e = engine(NetSnapshot(ConnectionMode.REYOHOHO, "proxy4", true, true), net)
        val r = e.fetchMaster("ch", "h264", direct) { token }
        assertEquals(Route.REYOHOHO_ANON, r.route); assertTrue(r.tokenRejected)
        assertEquals(2, net.log.size); assertTrue(net.log[1].headers.isEmpty())
        // Next stream: the server that refused the token does not get it again.
        net.script += { HttpText(200, master) }
        e.fetchMaster("ch2", "h264", direct) { token }
        assertTrue(net.log[2].headers.isEmpty())
        // A new sign-in clears the memory.
        e.resetTokenMemory(); net.script += { HttpText(200, master) }
        e.fetchMaster("ch3", "h264", direct) { token }
        assertEquals(token, net.log[3].headers[Reyohoho.TOKEN_HEADER])
    }

    @Test fun unreachableProxyFallsBackToDirectAfterTwoServers() {
        val net = FakeNet() // every call times out
        val r = engine(NetSnapshot(ConnectionMode.REYOHOHO, "auto", true, true), net).fetchMaster("ch", "h264", direct) { token }
        assertEquals(Route.DIRECT, r.route); assertNotNull(r.fallbackReason); assertEquals(1, directCalls)
        assertEquals("bounded: ${ConnectionEngine.MAX_SERVERS} servers", ConnectionEngine.MAX_SERVERS, net.log.size)
    }

    @Test fun badProxyAnswerFallsBackToDirect() {
        val net = FakeNet().apply { script += { HttpText(200, "<html>captive portal</html>") }; script += { HttpText(502, "") } }
        val r = engine(NetSnapshot(ConnectionMode.REYOHOHO, "auto", true, true), net).fetchMaster("ch", "h264", direct) { null }
        assertEquals(Route.DIRECT, r.route); assertEquals(1, directCalls); assertTrue(r.fallbackReason!!.contains("502"))
    }

    @Test fun rateLimitedServerIsCooledDownAndSkipped() {
        var now = 0L
        val net = FakeNet().apply { script += { HttpText(429, "") }; script += { HttpText(200, master) } }
        val e = engine(NetSnapshot(ConnectionMode.REYOHOHO, "proxy4", true, true), net) { now }
        val r = e.fetchMaster("ch", "h264", direct) { null }
        assertEquals(Route.REYOHOHO_ANON, r.route); assertEquals("proxy7.rte.net.ru", r.server)
        // Within the cooldown the 429 server goes last: the next call starts elsewhere.
        now = 60_000; net.script += { HttpText(200, master) }
        e.fetchMaster("ch", "h264", direct) { null }
        assertFalse(net.log.last().url.startsWith("https://proxy4."))
        // After the cooldown the chosen server is first again.
        now = ConnectionEngine.COOLDOWN_MS + 10; net.script += { HttpText(200, master) }
        e.fetchMaster("ch", "h264", direct) { null }
        assertTrue(net.log.last().url.startsWith("https://proxy4."))
    }

    @Test fun offlineChannelThroughProxyIsDecidedByDirect() {
        val net = FakeNet().apply { script += { HttpText(403, "") } }
        val r = engine(NetSnapshot(ConnectionMode.REYOHOHO, "auto", true, true), net).fetchMaster("gone", "h264",
            { directCalls++; HttpText(404, "") }) { null }
        assertEquals(1, net.log.size); assertEquals(404, r.text.code); assertEquals(1, directCalls)
    }

    @Test fun noEndlessLoopsWhateverHappens() {
        // 100 opens against a dead proxy: each open costs at most MAX_SERVERS proxy calls + 1 direct.
        val net = FakeNet()
        var now = 0L
        val e = engine(NetSnapshot(ConnectionMode.REYOHOHO, "auto", true, true), net) { now }
        repeat(100) { now += 1000; e.fetchMaster("ch", "h264", direct) { token } }
        assertTrue(net.log.size <= 100 * ConnectionEngine.MAX_SERVERS)
        assertEquals(100, directCalls)
    }

    @Test fun interruptedCallIsAFailureNotACrash() {
        val net = FakeNet().apply { script += { throw InterruptedIOException("cancelled") } }
        val r = engine(NetSnapshot(ConnectionMode.REYOHOHO, "auto", true, true), net).fetchMaster("ch", "h264", direct) { null }
        assertEquals(Route.DIRECT, r.route)
    }

    @Test fun twoChannelsInARowUseTheirOwnPlaylists() {
        val net = FakeNet().apply { script += { HttpText(200, master) }; script += { HttpText(200, master) } }
        val e = engine(NetSnapshot(ConnectionMode.REYOHOHO, "auto", true, true), net)
        e.fetchMaster("first", "h264", direct) { null }; e.fetchMaster("second", "h264", direct) { null }
        assertTrue(net.log[0].url.contains("/hls/first.m3u8")); assertTrue(net.log[1].url.contains("/hls/second.m3u8"))
    }

    @Test fun statusParsing() {
        assertTrue(Reyohoho.parseStatus("""{"enabled":true,"usherViaProxy":true}"""))
        assertTrue(Reyohoho.parseStatus("""{"enabled":true,"overload":{"inflight":10,"maxInflight":100}}"""))
        assertFalse(Reyohoho.parseStatus("""{"enabled":true,"overload":{"inflight":95,"maxInflight":100}}"""))
        assertFalse(Reyohoho.parseStatus("""{"enabled":false}"""))
        assertFalse(Reyohoho.parseStatus("not json"))
    }

    // ---------- 1440p detection ----------

    @Test fun detects1440AndItsAbsence() {
        val hi = "#EXTM3U\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=12000000,RESOLUTION=2560x1440,CODECS=\"hev1.1.2.L150.90.0.0.0.0.0,mp4a.40.2\",VIDEO=\"1440p60\",FRAME-RATE=60.000\nhttps://a/1440.m3u8\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=8000000,RESOLUTION=1920x1080,CODECS=\"avc1.64002A,mp4a.40.2\",VIDEO=\"1080p60\",FRAME-RATE=60.000\nhttps://a/1080.m3u8\n"
        assertEquals(1440, Hls.sortVideo(Hls.parseMultivariant(hi)).first().height)
        assertEquals(1080, Hls.sortVideo(Hls.parseMultivariant(master)).first().height)
        assertEquals(720, Hls.sortVideo(Hls.parseMultivariant(directMaster)).first().height)
    }

    @Test fun sessionDataAndOldTwitchInfoBothGiveCountry() {
        assertEquals("DE", Hls.twitchInfo(master)["USER-COUNTRY"])
        assertEquals("NL", Hls.twitchInfo("#EXTM3U\n#EXT-X-TWITCH-INFO:NODE=\"x\",USER-COUNTRY=\"NL\"\n")["USER-COUNTRY"])
    }

    // ---------- settings / consent ----------

    private class FakeBox : SecretCodec {
        override fun seal(plain: String) = "sealed:" + plain.reversed()
        override fun open(sealed: String) = sealed.removePrefix("sealed:").reversed()
    }

    private fun settings(name: String): Pair<NetSettings, android.content.SharedPreferences> {
        val sp = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(name, Context.MODE_PRIVATE)
        sp.edit().clear().commit()
        return NetSettings(sp, FakeBox()) to sp
    }

    @Test fun reyohohoIsOffByDefault() {
        val (s, _) = settings("t1")
        assertEquals(ConnectionMode.DIRECT, s.mode); assertFalse(s.reyohohoConsented)
    }

    @Test fun modeCannotBeReyohohoWithoutConsent() {
        val (s, _) = settings("t2")
        s.mode = ConnectionMode.REYOHOHO
        assertEquals("ignored without consent", ConnectionMode.DIRECT, s.mode)
    }

    @Test fun consentIsSavedAndRevocable() {
        val (s, sp) = settings("t3")
        s.grantReyohohoConsent(); s.mode = ConnectionMode.REYOHOHO
        val again = NetSettings(sp, FakeBox()) // "app restart"
        assertTrue(again.reyohohoConsented); assertEquals(ConnectionMode.REYOHOHO, again.mode)
        again.revokeReyohohoConsent()
        assertFalse(again.reyohohoConsented); assertEquals(ConnectionMode.DIRECT, again.mode)
    }

    @Test fun changedRecipientOrDataAsksAgain() {
        val (s, sp) = settings("t4")
        sp.edit().putString("rteConsent", "rte-0|old.host|oauth-in-url").commit()
        assertFalse("old consent text does not count", s.reyohohoConsented)
        assertTrue(Reyohoho.CONSENT_VERSION.contains("X-Auth-Token-header"))
        Reyohoho.SERVERS.forEach { assertTrue(Reyohoho.CONSENT_VERSION.contains(it.host)) }
    }

    @Test fun proxyPasswordIsSealedAndNeverDisplayed() {
        val (s, sp) = settings("t5")
        s.customProxy = CustomProxy(ProxyKind.HTTP, "10.0.0.2", 3128, "me", "hunter2pass")
        assertFalse(sp.all.values.any { it.toString().contains("hunter2pass") })
        assertEquals("hunter2pass", s.customProxy.password)
        assertFalse(s.customProxy.display.contains("hunter2pass")); assertFalse(s.customProxy.toString().contains("hunter2pass"))
        assertFalse(s.dump().contains("hunter2pass"))
    }

    @Test fun proxyAddressParsing() {
        assertEquals(Triple(ProxyKind.SOCKS5, "1.2.3.4", 1080), CustomProxy.parseAddress("socks5://1.2.3.4:1080", ProxyKind.HTTP))
        assertEquals(Triple(ProxyKind.HTTP, "proxy.lan", 8080), CustomProxy.parseAddress(" proxy.lan:8080 ", ProxyKind.HTTP))
        assertNull(CustomProxy.parseAddress("user:pw@host:80", ProxyKind.HTTP))
        assertNull(CustomProxy.parseAddress("host", ProxyKind.HTTP))
        assertNull(CustomProxy.parseAddress("host:99999", ProxyKind.HTTP))
        assertFalse(CustomProxy(ProxyKind.HTTP, "", 0, "", "").isValid)
    }

    @Test fun customProxyClientIsBuiltOnceAndUsesTheProxy() {
        val p = CustomProxy(ProxyKind.SOCKS5, "127.0.0.1", 1, "", "")
        val c1 = Net.viaProxy(p); val c2 = Net.viaProxy(p)
        assertTrue("cached, not rebuilt per request", c1 === c2)
        assertEquals(java.net.Proxy.Type.SOCKS, c1.proxy!!.type())
        assertTrue("shares the main pool", c1.connectionPool === Net.http.connectionPool)
        // An unreachable own proxy fails fast instead of hanging.
        val t = System.currentTimeMillis()
        val r = runCatching { Net.getText("https://usher.ttvnw.net/", via = c1) }
        assertTrue(r.isFailure); assertTrue(System.currentTimeMillis() - t < 15_000)
    }

    // ---------- logs ----------

    @Test fun tokensAndProxySecretsNeverReachLogs() {
        val lines = listOf(
            "X-Auth-Token: $token", "x-auth-token=$token", "https://proxy4.rte.net.ru/https://usher.ttvnw.net/x.m3u8?a=1&auth=$token",
            "Proxy-Authorization: Basic $token", "http://me:$token@10.0.0.2:3128", "OAuth $token",
        )
        lines.forEach { assertFalse("leaked: $it", DiagLog.sanitize(it).contains(token)) }
        DiagLog.log("X-Auth-Token: $token")
        assertFalse(DiagLog.snapshot().any { it.contains(token) })
    }

    // ---------- live (real ReYohoho servers, anonymous only) ----------

    @Test fun liveReyohohoAnonymous() {
        assumeTrue(System.getProperty("live") == "true")
        val out = File(System.getProperty("liveOut", "build/live-report.txt"))
        val e = ConnectionEngine({ NetSnapshot(ConnectionMode.REYOHOHO, "auto", true, sendToken = false) },
            { u, h -> Net.getText(u, h, Net.http) })
        val r = e.fetchMaster("lofigirl", "h265,h264", { error("direct must not be needed") }) { error("no token in live test") }
        val v = Hls.sortVideo(Hls.parseMultivariant(r.text.body))
        val line = "ReYohoho live: route=${r.route} server=${r.server} country=${Hls.twitchInfo(r.text.body)["USER-COUNTRY"]} " +
            "max=${v.firstOrNull()?.label} variants=${v.size} mediaHost=${v.firstOrNull()?.url?.substringAfter("://")?.substringBefore('/')}"
        println(line); out.parentFile?.mkdirs(); out.appendText(line + "\n")
        assertEquals(Route.REYOHOHO_ANON, r.route); assertTrue(v.isNotEmpty())
        assertTrue("media playlists are Twitch's, not the proxy's", v.all { it.url.contains(".ttvnw.net/") && !it.url.contains("rte.net.ru") })
        assertTrue(e.probe(Reyohoho.SERVERS.first { it.host == r.server }))
    }
}
