# Classic

The 1.x interface, kept as-is. This document records the reference so future work does not drift.

## Reference
Baseline captured **before** the 2.0 refactor (1.1-test code): `docs/screenshots/classic-baseline/`.
2.0 captures: `docs/screenshots/classic-*.jpg`. Differences are intentional and limited to:
- Settings has one new row, «Дизайн интерфейса», with an inline picker (previews + tagline).
- Internals: lists come from the shared `StreamRepo`, the player uses the shared `PlayerSession`.

## Tokens (unchanged)
`bg #0B0B0F`, `surface #17171D`, `surfaceHi #23232B`, `accent #9147FF`, `live #E91916`,
`text #EFEFF1`, `dim #ADADB8`. System sans: headline 26 sp, title 18, body 15, small 13, label 16.
Cards: radius 10 dp, focus scale 1.04 + 3 dp white border. Grid: 4 columns, 20 dp gaps, 48 dp side margins.

## Interaction
Tabs across the top switch on focus (only after the first key press, so the start tab sticks).
OK opens a live stream; long-press OK toggles favourite. Player: any D-pad key opens the bottom
panel; it hides after 8 s. Settings: OK flips/cycles a value in place.
