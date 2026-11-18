# Impeccable reviews — Twitch TV 2.0

Method: Impeccable `critique` (Assessment A only — native Android, so the web detector
`impeccable detect` does not apply), `polish`, `harden`, `optimize`, `animate`, `layout`,
`typeset` passes, judged on rendered 1920×1080 frames from `DesignScreenshots` and on the code.
Scores are Nielsen heuristics 0–4. All runs: 2026-11-19.

## Round 1 → fixes

| Design | Score | P1 issues found | Fixed in 2.0.0 |
|---|---|---|---|
| Classic | 29/40 | none new (1.x reference); design row lives at the end of the list | — (intentionally unchanged) |
| Liquid Glass | 27/40 | (1) player orb icons rendered off-centre, focused orb read as an empty white disc; (2) design sheet too transparent — settings text bled through its title; (3) 2×2 preview grid overflowed the sheet | (1) content centred inside the surface; (2) ink base 90 % under the glass; (3) sheet scrolls with focus |
| Google TV | 28/40 | (1) on a fresh install the first rows under the hero were two empty explanations — no content above the fold; (2) "Загрузка…" sat on top of the transport bar; (3) design taglines truncated | (1) empty personal rows move below Popular/GTA VI, return to the top once they have content; (2) status moves above the transport while controls are open; (3) four lines |
| GTA VI | 26/40 | (1) secondary row text (viewers · game) unreadable over the coral sun; (2) category line over the sun; (3) tagline truncated in the postcards | (1) list sits on a 62 % night slab; (2) line gets its own night chip; (3) four lines |

## Heuristic notes (after fixes)

- **Visibility of status:** every design shows live viewers, quality + codec, loading/reconnect
  status and ad-filter state; GTV adds measured latency. Vice uses the "on air" tag on every
  channel change.
- **Match with the real world:** Vice menu metaphors (radio for quality, phone for contacts and
  chat) are discoverable because each item is labelled in words, not icons.
- **User control:** Back is always one level; overlays trap focus so the remote can't fall
  "through" a sheet into the page behind it.
- **Consistency:** shortcuts identical across designs (OK, long-OK, CH±, Play/Pause).
- **Error prevention/recovery:** network failures show the reason and a Retry action in each
  design's own voice; the player keeps auto-reconnect and 1440p→1080p fallback underneath.
- **Aesthetic and minimalist:** Glass and GTV show one decision at a time; Vice keeps glow to
  headlines and status only.

## Performance / harden
- No `RenderEffect`, no `Modifier.blur`, no full-screen offscreen layers. Glass "blur" = 32×18
  decode. Vice backdrop = one Canvas; the only idle animation is the home-screen shimmer, none in
  the player. Coil requests are sized (480×270 tiles, 800×450 postcard, 960×540 hero).
- Long names/titles: single-line ellipsis everywhere in rows/cards; titles in spotlight/postcard
  get two lines. Emoji in titles render (see screenshots).
- Empty favourites/history/search, failed loads and offline states exist in every design.
- 1280×720 checked for all four homes (`*-720p-home.png` in the test output).

## Open (needs the TV)
Frame pacing with 1440p60 HEVC under each design's overlays, remote key-repeat feel, and
legibility at 3 m can only be judged on the TCL — see the checklist in the release notes.
