<div align="center">

<img src="docs/images/icon.png" width="150" alt="Il2Patch logo">

<p style="font-weight: bold; font-size: 37px;">Il2Patch</p>

---

<b>A hex / assembly patcher for <code>libil2cpp.so</code>, built for offline Unity games you own.</b><br>
  Open the file, find the function you want, patch it, save. All on your phone.

  <img src="https://img.shields.io/badge/license-personal_use-blue" alt="license personal use">
  <img src="https://img.shields.io/badge/platform-Android-green?logo=android" alt="platform Android">
  <br>
  <img src="https://img.shields.io/badge/minSdk-26_(Android_8.0)-blue" alt="minSdk 26 (Android 8.0)">
  <img src="https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin" alt="Kotlin 1.9.24">
  <br>
  <img src="https://img.shields.io/badge/IL2CPP_metadata-24--31_%7C_39-orange" alt="IL2CPP metadata 24-31 | 39">
  <br>
  <img src="https://img.shields.io/badge/build-repo_or_workflow_not_found-red" alt="build repo or workflow not found">

---

## What it does

Unity games compiled with IL2CPP ship their game logic as native machine code inside `libil2cpp.so`, instead of readable script files.

That means things like **gems, coins, health, ads, premium status** live inside functions in that file.

Example:
- `GetGems()` — returns how many gems you have
- `IsPremium()` — returns `true` if you bought VIP
- `SpendGems(amount)` — subtracts gems when you buy something
- `ShowAd()` — shows an ad

**Il2Patch finds those functions by their NAME and lets you change what they do.**

| You want... | Il2Patch does... |
|---|---|
| ♾️ Infinite gems | Make `GetGems()` always return `999999` |
| 🚫 No ads | Make `ShowAd()` do nothing |
| 👑 Free Premium | Make `IsPremium()` always return `true` |
| ❤️ God Mode | Make `TakeDamage()` do nothing |
| 💸 Spend = Gain | Flip `Spend()`'s `sub` → `add`, so spending **increases** gems |

> ⚠️ **Use only on offline, single-player games you own, on your own copy, for your own use.**

---

## ✨ What Is This?

Games store things like **gems, coins, health, ads, premium status** inside functions in a file called `libil2cpp.so`.

**Il2Patch finds those functions by their NAME and lets you change what they do.**

---

## 🚀 Quick Start — 3 Steps

### 1️⃣ Start Tab — Open Your File
**What this tab does:** Opens the game file and saves your work.

| Field / Button | What it does | What to type / tap |
|---|---|---|
| 📂 **Open .so or .apk** | Opens a file picker | Pick `libil2cpp.so` OR a `.apk` game file |
| ℹ️ **File info** | Shows file size, ELF type, mode | Just read it — green = ready |
| 💾 **Save to original** | Overwrites the file you opened | Use when patching a loose `.so` |
| 📄 **Save copy as...** | Saves a new file, original untouched | ✅ **Use this the first time!** Safest |
| 📦 **Build patched APK** | Rebuilds + signs the APK with your patched `.so` inside | Only in APK mode |
| ↩️ **Undo last** | Removes the last patch you made | Tap if a patch broke something |
| 🗑️ **Undo all** | Removes ALL patches this session | Red = dangerous, asks nothing — be sure |

> 💡 **APK mode warning:** You must **uninstall the original app first**, then install the patched APK. Your in-game saves are usually lost unless you backed them up.

---

### 2️⃣ Find Tab — Find What To Patch
**What this tab does:** Reads `global-metadata.dat` + `libil2cpp.so` and lists every function that *probably* controls something useful.

#### 🗂️ 1 — Files
| Field | What it does |
|---|---|
| **Metadata: global-metadata.dat** | Shows which metadata file is loaded. APK = found automatically. Plain `.so` = you must pick it by hand. |
| **Pick metadata file** | Opens picker for `global-metadata.dat` |
| **Advanced → Include Unity / System** | OFF = clean list (recommended). ON = also show UnityEngine / System functions — bigger, slower, only if you are hunting a Unity function. |

#### 🔍 2 — Scan
| Field / Button | What it does |
|---|---|
| **Analyze** | Scans ALL classes + methods. Takes 10-60 seconds on big games. |
| **Show technical details** | Shows raw counts: how many methods, how many suggestions, any errors. |
| **Summary line** | Example: `Found 12 Resources, 3 Ads, 1 Premium...` — green = found, grey = nothing yet. |

#### ✅ 3 — Fix — The Main List
Every card has 4 parts:

```
┌─────────────────────────────────────────┐
│ 🟢 RESOURCES              RVA 0x1A2B3C  │  ← Category pill (colour) + address
│ Player.GetGems                          │  ← Class.Method
│ ✅ Always returns 999999 (int)          │  ← GREEN = what the patch will do
│ returns int  Getter: the game reads...  │  ← Grey = explanation / warning
│ [ Apply fix ]  [ Edit ]                 │  ← Buttons
└─────────────────────────────────────────┘
```

| Button | What it does |
|---|---|
| **Apply fix** | Patches RIGHT NOW. Snackbar pops up with **Undo** for 5 seconds. |
| **Edit** | Fills the Patch tab with this function so you can change the value first. Safer for beginners. |

| Filter | What it does |
|---|---|
| **Category chips** | Tap to show / hide a whole category. Example: tap `Ads` off to hide all ads. |
| **Search by name** | Type part of a name: `gem`, `spend`, `ad`, `health`. Leave empty = show everything. |
| **Show more** | Shows 30 more results. |
| **Add shown to Batch list** | Sends everything you see now into Patch → List, so you can patch 20 things at once. |

#### 💸 Increase On Spend — Special Section
**Why is this separate?** `Spend()` functions are `void` — they return NOTHING. So there is no return value to patch. They live here, not in the main list.

| What you see | What it means |
|---|---|
| `Player.SpendGems  •  SPEND` | This function subtracts gems when you spend. |
| **💸 Increase on spend — Deep Scan** | Tap it → fills Patch address → jumps to Bytes → runs Deep Scan. |

**Deep Scan** walks the function + functions it calls (depth 3) and lists every `sub` it finds:
```
RVA 0x1A2C10   sub x5, x5, x1   [sub (register) -> add]   (in a called function)
```
- Pick the one whose registers match the `amount` parameter — NOT the one that says `#1` (that's usually a counter).
- If it says `Stopped at indirect (BLR) call` — the game used a virtual/interface call. Il2Patch can't follow that automatically — use Ghidra to follow it by hand.
- If it finds nothing — the Result box shows the exact start address + RVA switch + first bytes, so you can tell if you pasted the wrong address.

---

### 3️⃣ Patch Tab — Change One Function By Hand
**What this tab does:** Edits the function at the 🎯 **Target address** at the top.

#### 🎯 Target Address — READ THIS FIRST
| Field | What to type |
|---|---|
| **Function address (hex)** | Example: `1A2B3C` — copy from Find (RVA) or Ghidra. **No `0x`, no spaces.** |
| **RVA switch** | **ON** = address from Il2CppDumper / Find (most people want ON). **OFF** = raw file offset from a hex editor. Wrong setting = scan finds nothing. |

#### 🟢 Value — Easiest, Use This First
Best for: `GetGems`, `IsPremium`, `GetHealth`, `GetPrice`, `IsUnlocked`.

| Field | What to do |
|---|---|
| **Type chips: long / int / bool / float / double** | Pick the type Find says: `returns int` → tap `int`, `returns bool` → tap `bool` |
| **Value** | What should it return? Gems = `999999`, bool = `true`, price = `0`, health = `999999` |
| **Patch function** | Writes the code. Function now returns your value instantly, old code never runs. |
| **Result** | Green = patched. `Runs past end` = address is wrong. |

#### 🟠 Bytes — Full Control
Best for: you know assembly, or you want NOP / custom bytes.

| Field / Button | What it does |
|---|---|
| **New bytes** | Two ways: HEX `1F 20 03 D5` OR assembly `mov w0, #1; ret` |
| **Presets** | Ready-made snippets: return true / false / 999 / NOP / RET |
| **Apply** | Writes your bytes at the target address |
| **Flip + / −** | Changes ONE `add` → `sub` OR `sub` → `add` at the target. Use for Increase-on-Spend. |
| **Deep scan for subtract** | Same as Find → Increase on Spend. Lists all `sub`s near the target. |
| **Read bytes → Bytes to read** | How many bytes to show. 64 is enough to see a function's start. |
| **Read at address** | Shows what's REALLY at the address before you change it. Always read first if unsure. |

#### 🔵 List — Patch Many At Once
Best for: applying everything you picked in Find.

**Format — one patch per line, numbers in HEX:**
```
# This line is a comment, ignored
1A2B3C: mov w0, #1; ret
1A2B50: 1F 20 03 D5
1A2B60: 00 00 80 52 => 20 00 80 52
1A2B70: return long 10000
find FD 7B ?? A9 +4: nop
```

| Field | What it does |
|---|---|
| **Patch list** | Paste / type your lines here. `#` = comment. |
| **Dry run** | ✅ **ALWAYS ON first.** Only checks, changes NOTHING. If it says `0 errors`, turn it OFF and run again to really patch. |
| **Run list** | Runs every line. Stops and reports the first error — nothing is applied if ANY line fails. |
| **Load / Save** | Load a `.txt` list from storage, or save the current list. |

#### 🟣 Search — Find By Bytes, Not Address
Best for: game updated, Il2CppDumper addresses are old.

| Field | What it does |
|---|---|
| **Bytes with ??** | Example: `FD 7B ?? A9 ?? ?? 00 91` — `??` = any byte. |
| **Search** | Finds all matches. Tap a match to use it as your target address. |

> 💡 **Why Search > Address?** Addresses change every update. Byte patterns usually don't.

---

### 4️⃣ Settings Tab — Make The App Yours
| Setting | What it does |
|---|---|
| 🎨 **Appearance: System / Light / Dark** | Material You follows your wallpaper colours. System = follow your phone. |
| ⚙️ **Write each change straight into original** | ON = every patch saves instantly. OFF = patches only live in app until you tap Save. Beginners: keep ON, use Undo. |
| ⚙️ **Use RVA by default** | ON = paste Il2CppDumper addresses. OFF = paste file offsets. Most people want ON. |
| ⚙️ **Dry run by default** | ON = Patch List only checks. Safest default. |
| 🧹 **Clear cache** | Deletes metadata + APK copies the app keeps. Your game saves are NOT touched. |
| 🧹 **Clear log** | Clears the Log tab. |

### 5️⃣ Log Tab — What Happened
Every patch, undo, analysis and error is written here, newest first, in mono font.

**If something doesn't work, copy this page and send it** — it tells us the exact address + old bytes + new bytes.

---

## 📚 All 20 Categories Explained

| # | Category | Colour | What it finds | Example patch |
|---|---|---|---|---|
| 1 | 💎 **Resources** | Green | Gems, coins, gold, cash, tickets | `GetGems() → 999999` |
| 2 | 📺 **Ads** | Orange | ShowAd, RewardedAd, Banner | `ShowAd() → does nothing` |
| 3 | 👑 **Premium** | Purple | IsPremium, IsVIP, HasSubscription | `IsPremium() → true` |
| 4 | 🔓 **Unlocks** | Blue | IsUnlocked, HasSkin, HasLevel | `IsUnlocked() → true` |
| 5 | ⏳ **Timers** | Teal | Cooldown, Regen, WaitTime | `GetCooldown() → 0` |
| 6 | 🎓 **Tutorial** | Brown | Tutorial, Onboarding, RateUs | `ShowTutorial() → false` |
| 7 | 🏆 **Achievements** | Gold | Achievement, Trophy, Stars | `HasAchievement() → true` |
| 8 | ❤️ **God Mode** | Red | TakeDamage, Die, Health | `TakeDamage() → does nothing` |
| 9 | ⚔️ **Damage** | Dark Orange | Attack, Damage, Power | `GetDamage() → 999999` ⚠️ *Not a true multiplier — flat override* |
| 10 | 📈 **Stats** | Violet | XP, Level, Strength, Skill | `GetXP() → 999999` |
| 11 | 🔫 **Weapons** | Dark Red | Ammo, Weapon, Reload, Durability | `GetAmmo() → 999999` |
| 12 | 💨 **Movement** | Teal-Blue | Speed, Jump, Dash, Sprint | `GetSpeed() → 999999` ⚠️ *Too big can break physics* |
| 13 | 👹 **Enemy** | Purple | Enemy, Boss, Spawn, Difficulty | `GetEnemyCount() → 0` |
| 14 | 🗺️ **Map** | Green | Map, Fog, Vision, Camera, Zoom | `GetVision() → 999999` |
| 15 | 💾 **Save** | Brown | Save, Load, Checkpoint, Profile | `HasSave() → true` ⚠️ *Never patch Save/Load blindly* |
| 16 | 🛒 **Shop** | Orange | Shop, Price, Gacha, Summon, Pack | `GetPrice() → 0` |
| 17 | 📅 **Daily** | Pink | Daily, Quest, Event, Reward, Mail | `CanClaim() → true` |
| 18 | ⚙️ **Settings** | Grey | Debug, Cheat, Config, FPS | `IsDebug() → true` ⚠️ *Debug flags can crash* |
| 19 | 🚗 **Vehicle** | Slate | Car, Pet, Mount, Building, Farm | `GetVehicleSpeed() → 999999` |
| 20 | 🟢 **Bars** | Mint | Energy, Stamina, Mana, Shield, Hunger | `GetEnergy() → 999999` |

---

## 🧠 How Hints Work (Important)

Il2Patch guesses from **function NAMES only**. It does NOT read the game's logic.

- `GetGems()` → probably returns gems ✅
- `IsPremium()` → probably returns premium status ✅
- `GetSomething()` → might be something else ❌

**Always test in-game after patching.** If the game crashes or the value doesn't change, tap **Undo** in the Snackbar, or Start → Undo last.

### Damage Is NOT A Multiplier
`GetDamage() → 999999` sets damage to a fixed 999999. It does NOT multiply your real damage by 999999. If the game caps damage, or uses a different function for real damage, this patch will look like it "does nothing".

### Increase On Spend — How It Really Works
1. `SpendGems(100)` normally does: `gems = gems - 100`
2. Deep Scan finds the `sub` instruction: `sub x5, x5, x1`
3. Flip changes it to: `add x5, x5, x1`
4. Now `SpendGems(100)` does: `gems = gems + 100` → **spending gives you gems** 💸

If the game uses `Math.Max(0, gems - amount)` or a setter in another class, the `sub` is 2-3 calls deep — Deep Scan follows up to depth 3, but if it hits a virtual call (`BLR`) it will tell you and stop.

---

## ❓ FAQ / Troubleshooting

| Problem | Fix |
|---|---|
| **Analyze finds nothing** | Wrong `global-metadata.dat`? Pick the one from the SAME version of the game. Try Advanced → Include Unity/System. |
| **Apply fix does nothing in-game** | You patched a getter the game doesn't use, or the game checks server-side. Try another function with same name, or use Ghidra. |
| **Game crashes after patch** | Undo last patch. You patched a function the game needs for something else (e.g. `Save()` or `Debug()`). |
| **Deep Scan finds nothing** | Check RVA switch: ON = Il2CppDumper address, OFF = file offset. Check Result box — it shows start address + first bytes. |
| **Patched APK won't install** | Uninstall original app first. Patched APK has a different signature. |
| **Saves lost after installing patched APK** | Expected — Android deletes app data when signature changes. Back up saves before. |
| **Build fails** | Copy the exact error line from Android Studio and paste it in Issues. |

---

## 🛠️ For Developers

```
app/src/main/java/com/example/il2patch/
├── MainActivity.kt     # All UI — 5 tabs, Material You, cards, colours, fonts
├── Hints.kt            # Name-based suggestions, 20 categories
├── Asm.kt              # ARM64/ARM32 assembler, flip add/sub, Deep Scan
├── Il2CppAnalyzer.kt   # Reads global-metadata.dat + libil2cpp.so
├── ElfInfo.kt          # ELF parsing, RVA <-> file offset
├── Patcher.kt          # Reads/writes work.bin, journal for Undo
├── Batch.kt            # Patch-list parser
├── Hex.kt              # Hex dump / parse
└── ApkTools.kt         # APK extract / rebuild / sign
```

**Build:** Android Studio → Sync → Run. Min SDK 26, Target SDK 34, Kotlin 1.9.24.

---

## ⚖️ Legal

Use this only on **offline, single-player games you own**, to modify **your own copy**, for **your own use**.
Do not use it to cheat in online / multiplayer games, to bypass payments in a way that harms developers, or to redistribute patched APKs.
The authors are not responsible for bans, lost saves, or broken games. **Back up before patching.**
</div>
