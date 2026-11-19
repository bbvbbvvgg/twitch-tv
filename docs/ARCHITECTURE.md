# Architecture — one engine, four interfaces

```
MainActivity ── AppState (Activity-scoped)
                 ├─ design: Design            ← Prefs.design (persisted by name)
                 ├─ session: PlayerSession?   ← the open stream (ExoPlayer + chat)
                 └─ settings: SettingsModel   ← settings as data
        key(design) { ClassicApp | GlassApp | GtvApp | ViceApp }   ← exactly one tree composed

ui/core     shared behaviour, no visuals of its own
  AppState        open()/close() a stream, applyDesign(), lifecycle → session
  PlayerSession   PlaybackController + ChatClient + player-screen state (chat/diag/toast/favourite)
  StreamRepo      process-wide list cache: Popular, GTA VI, Favorites, History, Search, Recommended
  rememberStreams Loading / Failed / Ready state per list, IO dispatcher, keeps last good data
  SettingsModel   Toggle / Choice / Action / Screen items; every design renders them its own way
  DeviceLogin     Twitch device-code flow state (each design draws its own sign-in screen)
  PlayerCommon    VideoSurface (PlayerView bound to the session's player), playerKeys (CH±, Play/Pause, reveal), status text
  ChatText        one chat line with inline emotes
  DesignPreview   vector miniatures of the four layouts for the pickers
ui/classic  1.x UI on the shared core
ui/glass    Liquid Glass
ui/gtv      Google TV
ui/vice     GTA VI / Vice City
data/, player/, chat/   unchanged engine (TwitchApi, Account, PlaybackController, ad filter, HEVC/1440p guard)
```

## Why this split
- **Behaviour once, structure four times.** Loading, caching, favourites, history, sign-in,
  settings semantics, remote shortcuts and the player engine live in `ui/core` and below. Each
  design owns its composables end to end — navigation, layouts, cards, player chrome, pickers,
  settings — so none of them is a re-skin of another.
- **One tree at a time.** `key(app.design)` disposes the old design's whole composition (views,
  remembered state, LaunchedEffects) before the new one is built. No hidden trees, no leaks.
- **The player never belongs to a design.** `PlayerSession` is created by `AppState.open()` and
  released by `close()`. A design switch only recreates the `PlayerView`, which re-binds to the same
  `ExoPlayer`; recomposition never rebuilds the player. Verified by
  `DesignScreenshots.switchDesignDuringPlayback` (same session and player instance across five switches).
- **Lifecycle** is observed once in `MainActivity`: ON_STOP stops the player and chat, ON_START
  reopens at the live edge — identical for all designs.

## Switching
Settings → «Дизайн интерфейса» → `AppState.applyDesign(d)`: writes `Prefs.design`, sets
`openSettingsOnce` so the new design opens on its own settings (with its picker visible), and
swaps the tree. No restart, no reinstall; favourites, history and all settings are untouched
because they live in `Prefs`, not in a design.

## Remote
Every design: D-pad moves focus, OK activates, long-press OK toggles favourite on a stream,
Back goes one level up (overlay → controls → home), CH+/CH− switch channel in the player,
Play/Pause returns to the live edge, Menu/Info reveal player controls. Overlays that must keep
focus (sheets, quality pickers, channel lists) trap it with `focusProperties { exit = Cancel }`.

## Network layer (2.1)

```
net/NetSettings.kt       mode (DIRECT | REYOHOHO | CUSTOM), Reyohoho server, consent + its version,
                         custom proxy (password sealed by data/SecretBox.kt, Android Keystore AES-GCM)
net/Reyohoho.kt          server list proxy4/7/5/6.rte.net.ru, CONSENT_VERSION, master URL builder (no token in URL)
net/ConnectionEngine.kt  fetchMaster(): direct, or ≤2 Reyohoho servers per call, one anonymous retry after a
                         403 with token, 5-min cooldown per failed server, then fallback to direct. No loops.
net/Connection.kt        singleton wiring (App.kt); forgets the in-memory token on account change
net/NetCheck.kt          one-shot "check connection" on lofigirl, never with a token
data/Net.kt              OkHttp clients: http (direct), proxyApi (custom proxy for gql/usher), segments always direct
```

The four designs only render `SettingsModel` items of group «Сеть и прокси»; every design changes the mode
through `SettingsModel.chooseMode()`, which raises the consent modal before Reyohoho can be selected.
`PlaybackController.resolvePrimary()` asks the engine for the master playlist; media playlists and segments
keep the 2.0 path (ad filter, EXT-X-MAP, HEVC), so the player does not know which route was used except for
diagnostics (`netMode`, `route`, `fallback`, `netError`, `maxOffered`).
