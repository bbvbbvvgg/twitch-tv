# Liquid Glass

## Shape brief (impeccable shape)
- **Job and audience:** the same viewer, evening, wants the TV to feel calm and premium; picks a
  stream by looking at it, not by reading a grid. Mode: Experience on Home, Operate in Settings.
- **Outcome:** one large, living preview at a time; the remote's focus is the protagonist.
- **Direction:** Apple TV / iOS 26 materials — floating capsules, layered translucent slabs,
  light that comes from the content itself.
- **Anti-goals:** no blur over video, no grid, no decorative glass with nothing behind it.
- **Constraints:** API 30 has no `RenderEffect`; the TV SoC also decodes HEVC 1440p60.

## Visual system
- **Backdrop = the content.** The focused stream's preview decoded at **32×18 px** and stretched
  with bilinear filtering: a GPU-free "blur" that crossfades (600 ms) as focus moves, under an ink
  gradient `#070A14` (40→95 %) and a faint cyan radial glow.
- **Glass slab** (`Modifier.glass`): white 10 % fill × strength, top-lit sheen (white 16→4 %),
  1 dp rim gradient (white 42→6 %). No offscreen layers, no shaders.
- **Focus:** lit white slab (`#F4F7FF`, dark ink text), scale 1.08–1.1, coloured glow halo
  (`#9FDBFF`, tv-material `Glow`) — depth from light, not from borders.
- **Type:** Onest (OFL, Cyrillic): display 40/44 Bold −0.6, title 24 SemiBold, body 17, label 16
  Medium, small 14. One family, weight carries hierarchy.
- **Colour roles:** text 95 %, dim 65 %, faint 40 % white; live red `#FF453A`; tint `#7FD4FF`.
- **Radii:** dock and pills = capsule; tiles 24; hero 34; sheets 36.

## Layout
Home (960×540 dp canvas): dock (top 20, centred) → spotlight row 220 dp high (16:9 preview + name
40 sp, title, live pill, game, hint) → a single shelf of 204 dp tiles. Search swaps the spotlight
for a capsule field and a 160 dp spotlight. Settings: centred 66 % column of inset-grouped cards;
choices open a pop-over menu, sign-in and design open a right-side sheet that traps focus.

Player: nothing but video until a key. Info capsule (avatar, name, title, live pill, quality
chip) top-left; orb dock bottom-centre with a label bubble that names the focused orb; quality
column on the right; filmstrip of tiles at the bottom; chat as a floating rounded card.

## Motion (impeccable animate)
Thesis: *things float and settle.* Springs (damping 0.75–0.82, low stiffness) for panels and
the entrance; 1.1× spring-like lift on focus; crossfades for the backdrop and hero; switches use
an underdamped spring knob. Sections change with fade + 0.97→1 scale. Nothing loops except
loading placeholders and the status dot. Compose follows the system animator scale.

## Performance (impeccable optimize)
- 32×18 backdrop decode, 480×270 tiles, 960×540 hero: Coil sizes requests explicitly.
- No `Modifier.blur`/`RenderEffect`; panels over video are flat translucent fills.
- Glow uses the platform shadow (API 28+), only on the focused element.
