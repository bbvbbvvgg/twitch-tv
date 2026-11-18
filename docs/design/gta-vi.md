# GTA VI — "Vice"

## Shape brief
- **Job:** the owner watches a lot of GTA VI; this design should feel like the game's own menu
  while staying a fast, readable stream picker. Mode: Experience.
- **Direction:** dusk over the Atlantic, neon signage, a game pause menu. Taste over noise:
  one sunset, two neon voices (magenta, teal), condensed type with discipline.
- **Rights:** everything is original — vector sun/water geometry drawn in code, OFL/Apache
  fonts (Oswald, Yellowtail). No Rockstar marks, screenshots, leaked art or game fonts.
- **Anti-goals:** no gradient soup, no glowing everything; glow only on headlines and status.

## Visual system
- **Palette:** night `#120A1F`, dusk `#2A0F3D`, plum `#4A1650`, magenta `#FF3D8B`, coral
  `#FF7A59`, sun `#FFC36B`, teal `#2DE2D0`, cream `#FFF4E8`, dim `#CDB8DC`, faint `#8C7699`.
- **Type:** Oswald (condensed, Cyrillic) — mega 56 Bold, menu 28 SemiBold, title 26, row 22,
  tag 14 tracked +2; uppercase for anything that's a sign. Yellowtail script only for the Latin
  "Vice" wordmark. Body copy stays in the system sans for legibility.
- **Shapes:** `SlantShape` parallelograms for highlights, rows, tabs, signs — speed and attitude.
- **Backdrop:** `DuskBackdrop` — violet sky → plum → coral horizon, a sun cut by six widening
  slits, water with shimmering reflection bars (the only idle motion). The GTA VI section grows
  the sun (spring) and lifts the horizon.
- **Neon:** text shadow in the accent colour, used on headlines, LIVE tags and status only.

## Structure
- **Home = pause menu:** left column (wordmark + six menu items, slanted magenta highlight that
  slides 14 dp on a spring). Right: a numbered **mission list** (01, 02 …, NAME, viewers · game)
  next to a **tilted postcard** of the focused stream (cream frame, LIVE neon tag, title caption).
  Back inside a section returns to the menu first.
- **«GTA VI · Леонида»**: own headline "ПРИВЕТ ИЗ / ЛЕОНИДА LIVE", category audience, bigger sun,
  steeper postcard.
- **Settings = game options:** tabs (Аккаунт · Видео · Чат · Интерфейс · Диагностика); value rows
  are ◀ VALUE ▶ selectors changed with ←/→; the design choice is four tilted postcards; sign-in
  shows the code as a neon board.
- **Player = HUD:** idle shows a "СЕЙЧАС В ЭФИРЕ" tag (5 s after each channel change). A key opens
  the left pause strip. Quality = **radio dial** (stations on a band, ←/→ tune, centre display
  shows codec and bitrate, OK locks). Channels = **phone contacts**. Chat = the phone's message
  thread. Toasts = mission-passed headline.

## Motion
Spring slide on menu focus; sections slide in 1/10 width; postcards rise 1/14 and fade; radio
stations scale 1.22 on focus; status sign flickers softly. Reflection shimmer is a 5.2 s
ping-pong on the home screen only — the player has none.
