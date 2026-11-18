# Screenshots — how they are made and what they are

`docs/screenshots/*.jpg` are produced by `app/src/test/.../DesignScreenshots.kt`: the real app
(MainActivity, real Compose UI, live Twitch data) driven with D-pad key events and rendered by
**Robolectric Native Graphics** — Android's own Skia/HWUI drawing code running in a JVM.

What they are **not**: an emulator or the TCL TV. Robolectric has no media codecs, so the player
screenshots show no video: a black frame and «Загрузка…» (Google TV draws the stream's preview
behind its scrim, as it does on the TV before the first frame); real playback, HEVC 1440p60 decoding, frame pacing and remote
latency have to be checked on the TV.

Robolectric's native runtime ships x86_64/macOS binaries only. On an aarch64 Linux host the test
JVM runs under qemu-user:

```
sudo apt-get install qemu-user libc6-amd64-cross libstdc++6-amd64-cross
# x86_64 JDK 17 unpacked to $X64JDK; wrapper must live at <home>/bin/java:
mkdir -p jqemu/bin && cat > jqemu/bin/java <<'SH'
#!/bin/sh
exec qemu-x86_64 -L /usr/x86_64-linux-gnu $X64JDK/bin/java "$@"
SH
./gradlew testDebugUnitTest --tests '*DesignScreenshots*' -Dshots=true -Dlive=true -PtestJvm=$PWD/jqemu/bin/java
```
On x86_64 Linux or macOS drop `-PtestJvm`. Output: `test-report/shots/*.png`.
