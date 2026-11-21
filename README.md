<div align="center">

<img src="docs/images/icon.png" width="96" height="96" alt="Il2Patch icon" />

# Il2Patch

**A hex / assembly patcher for `libil2cpp.so`, built for offline Unity games you own.**
Open the file, find the function you want, patch it, save. All on your phone.

[![License](https://img.shields.io/badge/license-personal%20use-blue)](#notes--legal)
[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](#requirements)
[![Min SDK](https://img.shields.io/badge/minSdk-26%20(Android%208.0)-informational)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white)](#requirements)
[![Metadata](https://img.shields.io/badge/IL2CPP%20metadata-24--31%20%7C%2039-orange)](#metadata-version-support)
[![Build](https://img.shields.io/github/actions/workflow/status/<you>/Il2Patch/build.yml?branch=main&label=build)](../../actions)

</div>

---

## What it does

Unity games compiled with IL2CPP ship their game logic as native machine code inside `libil2cpp.so`,
instead of readable script files. Il2Patch lets you open that file (or the whole APK) directly on
your phone, scan it for interesting functions, and rewrite them — no PC, no root, no reverse‑engineering
experience required for the common cases.

- **Dump & suggest** — reads `global-metadata.dat` + `libil2cpp.so` and lists every class and method
  with its address, parameters and return type. It then flags the functions that most likely control
  gems/coins, ads, premium/VIP status, unlocks, cooldown timers, tutorial/rate‑us prompts,
  achievements, god mode and damage, each with a ready‑made patch and a one‑tap **Apply fix**.
- **Patch by hand** — raw hex, mini‑assembly (`mov w0, #1; ret`), or "make this function always return
  `long 10000`" — pick the type and value, Il2Patch writes the machine code for you.
- **Flip + / − / Scan** — turns a resource's `Spend` function into one that increases the resource
  instead, by flipping a single ARM64 `add`/`sub` instruction in place. The built‑in scanner finds the
  instruction for you, no external disassembler needed — see [Resources that increase when spent](#resources-that-increase-when-spent) below.
- **Batch mode** — a whole list of patches at once, with a dry run and old‑byte verification before
  anything is written.
- **Pattern search** — find code by a byte pattern with `??` wildcards, since exact offsets change on
  every game update but patterns usually don't.
- **APK mode** — repack the patched `.so` back into the APK and sign it (v1+v2+v3), ready to install.
- **Undo**, hex view, save to the original file or a copy, dark/light/system theme, and a Settings tab
  for defaults.

---

## Requirements

| | |
|---|---|
| **Device** | Android 8.0 (API 26) or newer |
| **Game** | An offline / single‑player Unity (IL2CPP) game you own, with IL2CPP metadata version 24–31 or 39 |
| **To build it yourself** | Android Studio, or just GitHub (see below) — no local SDK needed either way |

> Metadata versions **32–38** are early‑access Unity builds and are not supported on purpose.
> Versions **106 and above** (Unity 6000.5+) use a different metadata layout and aren't supported yet.

---

## Getting the app

### Option A — build on GitHub (no computer needed)

1. Create an empty GitHub repository and push this project to it:
   ```bash
   git init
   git add .
   git commit -m "Il2Patch"
   git branch -M main
   git remote add origin https://github.com/<you>/Il2Patch.git
   git push -u origin main
   ```
2. Open the repo's **Actions** tab on GitHub and wait for **Build APK** to finish (a couple of minutes).
3. Download the APK one of two ways:
   - From the finished Actions run, under **Artifacts → `Il2Patch-apk`**, or
   - From **Releases → Latest build → `Il2Patch.apk`** (this link works straight from a phone browser).
4. Install the APK (you may need to allow "install unknown apps" for your browser/file manager once).
5. Push a tag like `v1.0` any time you want a permanent, versioned release instead of the rolling
   "latest" build:
   ```bash
   git tag v1.0 && git push origin v1.0
   ```

### Option B — build locally in Android Studio

1. Open this folder in Android Studio.
2. Press **Run**. The signing key (`app/src/main/assets/il2patch.p12`) is generated automatically the
   first time you build — you don't need to create one yourself.

---

## Using the app, step by step

Il2Patch is organized as five tabs at the bottom: **Start → Find → Patch → Settings → Log**.

1. **Start → Open .so or .apk**
   Pick `libil2cpp.so` directly, or an APK — if you pick an APK, Il2Patch lists the `.so` files and
   `global-metadata.dat` inside it and asks which one you want.

2. **Find → Analyze**
   If you opened an APK, the metadata is picked up automatically; for a plain `.so` you'll be asked to
   also pick `global-metadata.dat` from the game's files (usually under
   `Android/data/<package>/files/`, or extracted from the APK's `assets/bin/Data/`). Tap **Analyze** —
   Il2Patch reads every class and method and lists the ones worth looking at as cards, grouped by
   category (Resources, Ads, Premium, Unlocks, Timers, Tutorial, Achievements, God Mode, Damage). Each
   card shows what it will do, e.g. *"Always returns true"* or *"Disabled: does nothing"*.

3. **Find → Apply fix**
   Tap it on any suggestion to patch it immediately — a snackbar lets you **Undo** right away if it's
   not what you wanted. Tap **Edit** instead to open it on the Patch tab first and change the value.

4. **Patch tab** (for anything not covered by a suggestion)
   - **Value** — give any function's address and a type (long/int/bool/float/double) and a value; it
     always returns that.
   - **Bytes** — write raw hex or mini‑assembly at an address, or read bytes back to inspect them.
   - **List** — paste or build a whole batch of patches (see format below) and apply them together.
   - **Search** — find a byte pattern with `??` wildcards when you don't have an exact address yet.

5. **Save your changes**
   On the Start tab: **Save to original** writes straight back into the opened file (handy if
   "write each change straight into the original file" is on in Settings — the default). Otherwise use
   **Save copy as...**, or for an APK, **Build patched APK** to repack and sign it.

6. **Settings tab**
   - **Appearance** — System / Light / Dark.
   - **Defaults** — whether new changes write straight to the original file, whether addresses are
     treated as RVAs by default, and whether the patch list defaults to a dry run.
   - **Storage** — clear cached metadata/APK copies, or clear the log.
   - **About** — app version.

7. **Log tab**
   Every patch you apply is recorded here (address, old bytes, new bytes) for the session.

### Batch list format

```
# comment
1A2B3C: mov w0, #1; ret
1A2B50: 1F 20 03 D5
1A2B60: 00 00 80 52 => 20 00 80 52     # only applied if the old bytes match
1A2B70: return long 10000
find FD 7B ?? A9 +4: nop                # unique pattern, patch at match + 0x4
```
All numbers are hex. Every patch in the list is validated first — if one fails, none of them are
applied.

### Export

From the Find tab you can export **Suggestions only** (just the flagged functions and their patches)
or **Everything** (every class and method Il2Patch found) as a JSON file.

---

## God mode, damage, and resources that increase when spent

These three are a bit different from the other categories, so here's exactly what each one does.

**God mode** looks for damage/death/health‑check functions and offers:
- `TakeDamage(float)`‑style methods (void, takes a parameter) → **disabled** (patched to `ret`), so
  taking damage does nothing.
- `IsDead()` / `IsAlive()`‑style checks (returns bool) → forced to the "not dead" answer.
- `get_Health()` / `get_HP()`‑style getters (returns a number) → forced to a very large value.

**Damage** looks for attack/damage *getters* (`get_AttackDamage()`, `get_Power()`, etc. — functions
that return a number and take no parameters) and overrides them to a very large fixed value. This is
**not a true multiplier** — there's no safe, generic way to read a value a function computes at
runtime and multiply it while keeping the rest of the function's logic intact with a fixed‑return
patch, only to replace the return value outright. A suggestion in this category is labelled
*"not a true multiplier"* so it's clear what you're getting: effectively one‑hit‑kill damage, not
"2x damage, 3x damage, etc."

### Resources that increase when spent

A function like `Spend(int amount)` usually does `resource -= amount` *in place*, with no return value
to patch — so it can't be turned into "resource increases" with a one‑tap suggestion the way a
`bool`/`int`‑returning function can. There's also no way to be 100% certain, for an arbitrary function,
which instruction inside it is "the" subtraction — so this still isn't a guaranteed one‑tap fix, but
there's now a way to do it entirely inside the app, with no external disassembler:

1. Void (no return value) functions like `Spend(int amount)` aren't shown as suggestion cards — there's
   no return value for a one‑tap "Apply fix" to set, so they don't fit the suggestion list the way a
   `bool`/`int`‑returning function does. Find the exact `Spend`‑style method yourself instead (Find's
   search, by name), note its address, put it in **Offset** on the Patch → Bytes tab, and tap
   **Deep scan for subtract (beta, follows branches/calls, ARM64)**.
2. Il2Patch reads the function's own instructions (no Ghidra needed) and lists every `sub` instruction
   it can reach, with its address — following conditional branches both ways, unconditional branches,
   and `bl` calls (up to a couple of levels deep), since real compiled functions are full of guard
   checks and shared helpers, not just one straight line. A candidate found only by following a call
   is marked *"in a called function"*, since that function may be shared by other code too.
3. Tap a candidate in the list to flip it immediately (a snackbar offers **Undo** right away) — `sub`
   becomes `add`, so spending now increases the resource instead of decreasing it.

This is still a heuristic, not a real disassembler: it has no idea which register holds "the resource"
if a function touches more than one number, so a complex function can list the wrong candidate first
or several candidates to try; it refuses anything that looks like stack‑pointer math (almost always
frame setup, not game logic); and it can't follow an **indirect** call (a virtual/interface method call
through a function pointer), only a direct `bl` — if that's what's happening, or if it still finds
nothing, you'll need a disassembler like Ghidra to follow the chain by hand and the manual
**Flip + / −** button to apply it once you've found the instruction. Check the result either way, and
use **Undo** if it's not what you expected. ARM64 (`arm64-v8a`) only, which is what almost all modern
Android devices and Unity builds use.

---

## Metadata version support

| Unity version | Metadata version | Supported |
|---|---|---|
| 2019 – 2022 / early 2023 | 24 – 31 | ✅ |
| 2023 alpha builds | 32 – 38 | ❌ (alpha‑only, intentionally skipped) |
| 6000.3 / 6000.4 | 39 | ✅ |
| 6000.5 and newer | 106+ | ❌ (not yet supported) |

If Analyze fails, the **Show technical details** button on the Find tab prints the metadata version
and a short diagnostic you can share when asking for help.

---

## FAQ

**Why does the package name say `com.example.il2patch`?**
`com.example` is Android Studio's default placeholder domain — every new project starts with it, and
it has no functional meaning. It's safe to leave as is. If you'd rather it said something else (e.g.
`com.yourname.il2patch`), that's a one‑line change, but like any `applicationId` change, Android will
then treat it as a different app from whatever's currently installed.

**Why do I need to uninstall the old app after rebuilding?**
A repacked or rebuilt APK is signed with a different key (or a different `applicationId`) than the
original game or a previous Il2Patch install, so Android refuses to install over it. Uninstall first.

**Will this work on an online/multiplayer game?**
You can, but most online games detect a modified library server‑side and will ban the account or
reject it. Il2Patch is meant for offline, single‑player games.

**My save file is gone after installing the patched APK.**
A repacked APK has a different signature, so Android treats it as a new app with its own, empty data
folder. Back up any save data you want to keep before installing over the original.

---

## Notes & legal

Use this only on offline, single‑player games you own, to modify your own copy for your own use.
Debug‑signed APKs from the GitHub Actions build are installable as they are.
