# Product

<!-- impeccable:product-schema 1 -->

> Written by `impeccable init` without an interview round: the owner asked for fully autonomous
> work ("не спрашивай подтверждения"), so every fact below comes from the 2.0 brief and the code.
> Facts the brief did not state are marked *(inferred)*.

## Platform

android

## Users
One household on a TCL Android TV 11 (build AR2101), driving everything with the remote's D-pad,
OK, Back, Play/Pause and CH+/CH−. Russian-speaking. Watches Twitch daily on the big screen, often
GTA VI streams; connects through Happ VPN. *(inferred)* Sits 2–3 m from the screen in a dim room.

## Product Purpose
A private Twitch client for the TV that plays streams more reliably than the official app: ad
bypass, 1440p60 HEVC after sign-in, automatic recovery from drops, quality control, chat and
diagnostics. Success = a stream starts quickly and keeps playing; the interface gets out of the way.

## Positioning
The only client the owner trusts on this TV: it reconnects through Happ, removes ads, unlocks the
source 1440p60 rendition and lets the owner choose between four complete interfaces
(Classic, Liquid Glass, Google TV, GTA VI) over one engine.

## Operating Context
Remote only, no pointer, no touch. 1920×1080 output (also 1280×720). The video decoder shares a
modest SoC with the UI: any UI effect that drops video frames is a defect.

## Capabilities and Constraints
- Shared engine: TwitchApi (GQL), PlaybackController (Media3/ExoPlayer, HLS ad filter, backup
  streams, reconnects, HEVC/1440p guard with fallback to 1080p60), Account (device-code sign-in,
  Keystore-encrypted token), Prefs (favorites, history, settings), ChatClient (IRC + 7TV/BTTV/FFZ).
- minSdk 24, target device API 30: no RenderEffect blur (API 31+).
- Sections every design must provide: Popular, GTA VI, Favorites, Search, History, Settings,
  Player (controls, quality, channel switch, chat, diagnostics), loading/empty/error states.
- Classic must stay visually identical to 1.x.

## Brand Commitments
- Classic: the 1.x look (Twitch purple #9147FF on near-black) is binding.
- GTA VI design: original artwork only; no Rockstar logos, screenshots, leaked material or game fonts.

## Evidence on Hand
Live Twitch data only. No invented channels, recommendations or numbers: "Recommended" is built
from the categories of channels in the user's own history/favorites.

## Product Principles
1. Playback first: no UI effect may cost video frames.
2. Every control reachable with the D-pad; Back always goes one level up.
3. Four designs are four structures, not four palettes.
4. Real data or an honest empty state — never filler.

## Accessibility & Inclusion
Readable at 3 m: body ≥ 14 sp on a 960 dp-wide canvas, strong focus indication in every design,
system "Remove animations" honoured by Compose's animation scale.
