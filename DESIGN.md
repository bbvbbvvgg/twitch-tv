# Twitch TV — design systems

Twitch TV 2.0 ships four complete interfaces over one engine. The selected one is stored in
`Prefs.design` and switched live from Settings → «Дизайн интерфейса». Each design has its own
document:

| Design | Package | Document |
|---|---|---|
| Classic | `ui/classic` | [docs/design/classic.md](docs/design/classic.md) |
| Liquid Glass | `ui/glass` | [docs/design/liquid-glass.md](docs/design/liquid-glass.md) |
| Google TV | `ui/gtv` | [docs/design/google-tv.md](docs/design/google-tv.md) |
| GTA VI (Vice City) | `ui/vice` | [docs/design/gta-vi.md](docs/design/gta-vi.md) |

Architecture and switching: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
Reviews (Impeccable critique / audit / polish passes): [docs/design/reviews.md](docs/design/reviews.md).

## The structure test

"Remove every colour and texture — do they still differ?" Yes, by construction:

| | Classic | Liquid Glass | Google TV | GTA VI |
|---|---|---|---|---|
| Navigation | Tab row, top-left after the logo | Floating icon dock, top-centre capsule; label only on the active section | Text tabs top strip + search icon left, account and gear right | Vertical pause menu, left column, slanted highlight |
| Home composition | 4-column grid of cards | One huge spotlight preview + story, single shelf of tiles below | Full-bleed rotating hero, then a vertical stack of carousels (continue, channels, popular, GTA VI, recommended) | Numbered mission list beside a tilted postcard of the focused stream |
| Stream unit | Card: image + avatar + 2 lines | Rounded tile, name below; details live in the spotlight | Landscape card; title revealed only on focus; round channel bubbles | Text row: rank · NAME · viewers/game |
| Focus | 1.04 scale + white border | 1.1 lift + glow halo + lit rim; drives backdrop & spotlight | 1.1 + white frame; caption swaps to title | Row turns cream; menu item slides 14 dp right on a spring |
| Player controls | Bottom gradient panel with text buttons | Info capsule top-left + orb dock bottom-centre, label bubble | Transport: title, live bar with latency, labelled pills, next-channels row | Left pause strip; "on air" tag bottom-right |
| Quality picker | Right column of rows | Floating glass column with checks | System side panel with radio rows | Radio dial: horizontal "stations", ←/→ tune, OK lock |
| Channel switch | Bottom row of cards | Glass filmstrip | Cards row inside the transport | Phone contact list |
| Chat | Translucent strip over video | Floating rounded glass card | Video resizes; chat gets its own side column | Phone message thread |
| Settings | One flat list, OK cycles values | Inset-grouped cards, switches; choices in a pop-over; sheets for sign-in/design | Two panes: categories → detail, radio lists | Tabs + ◀ value ▶ rows changed with ←/→ |
| Design picker | Inline list with previews | Right sheet, 2×2 previews | Gallery of four in the Interface pane | Four tilted postcards |
