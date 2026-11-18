# Google TV

## Shape brief
- **Job:** "what's on now that I'd like?" — browse by rows, decide from artwork. Mode: Experience
  on For you, Operate in Settings/Player.
- **Outcome:** content is the colour; chrome is quiet grey; focus is unmistakable.
- **Direction:** Google TV's language — cinematic hero with left/bottom scrims, horizontal
  carousels, text tabs, side panels, Roboto on Material roles.
- **Anti-goals:** no fake recommendations, no decorative gradients, no grids on the home tab.

## Visual system
- **Colours:** bg `#0E1013`, surface `#1C1F24`, surfaceHi `#2A2E35`, text `#E8EAED`, dim
  `#9AA0A6`, faint `#5F6368`, accent `#8AB4F8`, live `#EA4335`, focus `#F1F3F4` with `#202124` text.
- **Type:** platform Roboto. Hero 44/50 Medium, headline 22, row title 19, body 16, label 15, caption 13.
- **Focus:** white pill / white 3 dp frame + 1.1 lift on cards; card caption swaps from viewers to
  the stream title when focused.
- **Cards:** 16:9, radius 12, 236 dp (row), 280 dp (GTA top), 176 dp (player row). Channel bubbles
  96 dp with a red live ring.

## Information architecture
Top strip: search icon · Для вас · Прямой эфир · GTA VI · Библиотека … account · gear.
- **Для вас:** hero (top 5 live, rotates every 9 s, pauses while its buttons have focus) →
  Продолжить просмотр (live channels from history, red progress bar) → Избранные каналы (bubbles)
  → Популярное сейчас → GTA VI → Рекомендуем по вашим играм.
- **Recommendations (real data):** the two most frequent games among live channels in the user's
  history + favourites → live streams in those categories, minus channels the user already has.
  With no history the row says how it will fill.
- **Прямой эфир:** popular streams regrouped into one carousel per category, sorted by audience.
- **GTA VI:** hero with category audience, "Больше всего зрителей" (wide cards), "Ещё в эфире".
- **Библиотека:** Избранное and История carousels. **Поиск:** pill field, streams row + channels row.
- **Settings:** two panes — categories left (focus selects), details right with Material switches,
  expanding radio lists, the design gallery and the sign-in steps.

## Player
Transport layout: avatar + name + game top-left; title, live bar with real latency and quality,
labelled action pills, then "Ещё в эфире" cards. Chat does not cover the picture: the video
column shrinks and chat gets a 380 dp side column. Quality is a system-style side panel with
radio rows. Stats card top-right. Toasts are snackbars bottom-left.

## Motion
Tabs: fade + 1/24 rise; hero: 700 ms crossfade; side panels slide from the edge (260 ms);
everything else is the platform's focus scale. Calm and quick.
