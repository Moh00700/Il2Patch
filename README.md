# SoPatcher

Android hex/assembly patcher for `libil2cpp.so` (and any ELF `.so`) in offline games you own.

## Features
- Open a `.so` or an `.apk` (pick the `.so` / `global-metadata.dat` inside it)
- RVA or file offset (RVA is converted through the ELF segment table)
- Raw hex or mini-assembly: `mov w0, #1; ret`, `nop`, `reti 999`, `retf 1.5`, `fmov s0, w0` (ARM64); `mov r0, #1; bx lr` (ARM32)
- "Return a value": pick long/int/bool/float/double + a value, the app writes the code (also in batch: `1A2B3C: return long 10000`)
- Simple flow with bottom navigation: **Start** (open, save, undo), **Find** (analyze, one-tap fixes, export), **Patch** (Value / Bytes / List / Search), **Settings** (theme, defaults, cache), **Log**
- Segmented controls and filters now scroll horizontally instead of truncating their labels on narrow screens
- Find: reads global-metadata.dat + libil2cpp.so (Unity 2019+, metadata 24 to 31 and 39), lists every class and method with RVA, file offset, parameter and return types, lists likely gems/coins, ads, premium/VIP, unlocks, timers/cooldowns, tutorial/rate-us prompts and achievement/star functions as cards (void functions are never suggested) with a one-tap *Apply fix* + Undo, and exports everything or only the suggestions as JSON
- Batch mode with dry run, old-byte checks, and pattern-relative patches
- Pattern search with `??` wildcards (offsets change every game update, patterns usually don't)
- Undo last / undo all, hex view, save to original, save copy
- APK mode: repack with the patched file and sign (v1+v2+v3), 4096-aligned `.so`

## Batch format
```
# comment
1A2B3C: mov w0, #1; ret
1A2B50: 1F 20 03 D5
1A2B60: 00 00 80 52 => 20 00 80 52     # only if old bytes match
find FD 7B ?? A9 +4: nop                # unique pattern, patch at match + 0x4
```
All numbers are hex. All patches are validated first; if one fails, none are applied.

## Build locally
Open the folder in Android Studio and run. The signing key (`app/src/main/assets/sopatcher.p12`)
is generated automatically on first build.

## Build on GitHub
1. Create an empty repo on GitHub and push this folder:
   ```
   git init && git add . && git commit -m "SoPatcher"
   git branch -M main
   git remote add origin https://github.com/<you>/SoPatcher.git
   git push -u origin main
   ```
2. Open the **Actions** tab, wait for "Build APK" to finish, download the `SoPatcher-apk` artifact.
3. Or open **Releases > Latest build** and download `SoPatcher.apk` directly (works from a phone).
4. Push a tag like `v2.0` for a normal release.

## Notes
- A repacked APK has a different signature: uninstall the original app first. Saves in the game's data are lost unless backed up.
- Use on offline / single-player games you own. Online games usually detect modified libraries.
- Debug-signed APK from CI is installable as is.
