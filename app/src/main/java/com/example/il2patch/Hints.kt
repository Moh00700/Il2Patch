package com.example.il2patch

/** A function that probably controls a resource, ads, premium or similar status, with a ready-made patch. */
class Suggestion(
    val category: String,
    val cls: String,
    val method: String,
    val rva: Long,
    val fileOff: Long,
    val ret: String,
    val params: String,
    val kind: String,
    val value: String,
    val note: String,
    val score: Int
) {
    fun patch(): String = if (kind.isEmpty() || kind == "noop") "ret" else "return $kind $value"
    fun batchLine(): String = "%X: %s".format(rva, patch())
}

/** Name-based hints. These are guesses from function names: always verify before relying on one. */
object Hints {
    private val CURRENCY = setOf(
        "gem", "gems", "coin", "coins", "cash", "gold", "diamond", "diamonds", "money", "credit", "credits",
        "crystal", "crystals", "ticket", "tickets", "resource", "resources", "currency",
        "currencies", "wallet", "life", "lives", "heart", "hearts", "ruby", "rubies", "jewel", "jewels", "bucks"
    )
    private val ADS = setOf(
        "ad", "ads", "advert", "adverts", "advertisement", "advertisements", "advertising", "interstitial",
        "interstitials", "rewarded", "banner", "admob", "applovin", "ironsource", "vungle", "chartboost",
        "adcolony", "unityads", "mopub", "fyber", "appodeal", "startapp", "inmobi", "pangle", "mintegral"
    )
    private val PREMIUM = setOf(
        "premium", "vip", "pro", "subscribe", "subscribed", "subscription", "subscriptions", "subscriber",
        "paid", "license", "licensed", "entitlement", "entitlements", "member", "membership", "iap", "purchased"
    )
    private val VALUE_WORDS = setOf(
        "amount", "count", "value", "total", "current", "owned", "enough", "afford", "available", "have", "balance"
    )
    private val ADS_POSITIVE = setOf(
        "remove", "removed", "no", "free", "disabled", "purchased", "premium", "vip", "paid", "off", "hidden", "skip"
    )
    private val NEGATIVE = setOf(
        "expired", "expire", "trial", "locked", "banned", "limited", "restricted", "blocked", "empty",
        "insufficient", "lack", "missing", "not", "dead", "die", "death", "kill", "killed"
    )
    private val UNLOCKS = setOf("unlock", "unlocked", "unlockable", "lock", "locked")
    private val TIMERS = setOf(
        "cooldown", "cooldowns", "timer", "timers", "wait", "waiting", "regen", "regenerate", "regeneration",
        "recharge", "rechargetime", "refill", "refilltime"
    )
    private val TUTORIAL = setOf("tutorial", "tutorials", "onboarding", "walkthrough", "firstrun", "firsttime")
    private val ACHIEVEMENTS = setOf(
        "achievement", "achievements", "trophy", "trophies", "star", "stars", "medal", "medals", "badge", "badges"
    )
    private val GODMODE = setOf(
        "health", "hp", "godmode", "invincible", "invincibility", "die", "death", "dead", "kill", "killed",
        "damage", "hurt"
    )
    private val HEALTH_WORDS = setOf("health", "hp")
    private val DAMAGE_DEALT = setOf("damage", "attack", "atk", "power", "multiplier")

    private val STATS = setOf(
        "xp", "exp", "experience", "level", "lvl", "rank", "skill", "skills", "skillpoint", "skillpoints",
        "stat", "stats", "attribute", "attributes", "strength", "agility", "intelligence", "dexterity",
        "vitality", "luck", "charisma", "proficiency", "mastery", "talent", "talents"
    )
    private val WEAPONS = setOf(
        "weapon", "weapons", "gun", "guns", "rifle", "pistol", "sword", "blade", "bow", "arrow", "arrows",
        "ammo", "ammunition", "bullet", "bullets", "magazine", "clip", "reload", "grenade", "grenades",
        "bomb", "bombs", "inventory", "slot", "slots", "item", "items", "equipment", "equip", "equipped",
        "durability", "upgrade", "upgrades", "enhance", "craft", "crafting"
    )
    private val MOVEMENT = setOf(
        "speed", "movespeed", "movement", "move", "velocity", "jump", "jumpheight", "jumpforce",
        "dash", "sprint", "run", "walk", "fly", "flying", "gravity", "acceleration", "deceleration",
        "sprintspeed", "walkspeed", "teleport", "blink", "dodge", "roll", "climb"
    )
    private val ENEMY = setOf(
        "enemy", "enemies", "boss", "bosses", "mob", "mobs", "ai", "difficulty", "spawn", "spawns",
        "spawnrate", "spawninterval", "aggression", "aggressive", "hostile", "threat", "detection",
        "detectionrange", "sight", "hearing", "patrol", "chase", "attackrange", "enemyspeed", "enemyhealth"
    )
    private val MAP_CAMERA = setOf(
        "map", "minimap", "fog", "fogofwar", "vision", "sightrange", "viewdistance", "renderdistance",
        "camera", "cameradistance", "zoom", "fov", "fieldofview", "reveal", "revealed", "explored",
        "waypoint", "waypoints", "marker", "markers", "radar", "location", "position", "teleportto"
    )
    private val SAVE_ACCOUNT = setOf(
        "save", "saved", "savegame", "savefile", "load", "loadgame", "checkpoint", "autosave",
        "profile", "account", "userdata", "progress", "backup", "restore", "cloudsave"
    )
    private val SHOP_GACHA = setOf(
        "shop", "store", "market", "marketplace", "buy", "purchase", "purchases", "gacha", "summon",
        "summoning", "pull", "pulls", "crate", "crates", "chest", "chests", "pack", "packs", "bundle",
        "bundles", "offer", "offers", "deal", "deals", "price", "prices", "discount", "sale", "lottery"
    )
    private val DAILY_EVENTS = setOf(
        "daily", "dailies", "weekly", "event", "events", "quest", "quests", "mission", "missions",
        "challenge", "challenges", "reward", "rewards", "login", "logindays", "streak", "mail",
        "mailbox", "inbox", "gift", "gifts", "claim", "claimed", "calendar", "season", "seasonpass",
        "battlepass", "tournament", "leaderboard", "ranking"
    )
    private val SETTINGS_DEBUG = setOf(
        "debug", "cheat", "cheats", "god", "developer", "devmode", "test", "testing",
        "setting", "settings", "config", "configuration", "option", "options", "toggle", "enabled",
        "quality", "graphics", "resolution", "framerate", "fps", "volume", "sound", "music", "vibration"
    )
    private val VEHICLE_PET_BUILDING = setOf(
        "vehicle", "vehicles", "car", "cars", "bike", "bikes", "motorcycle", "boat", "boats", "plane",
        "planes", "aircraft", "mount", "mounts", "pet", "pets", "companion", "companions", "follower",
        "building", "buildings", "build", "construction", "house", "houses", "base", "castle", "tower",
        "towers", "farm", "farming", "harvest", "upgradebuilding"
    )
    private val BARS = setOf(
        "energy", "stamina", "mana", "rage", "focus", "hunger", "thirst", "fatigue", "sanity",
        "oxygen", "shield", "shields", "armor", "armour", "bar", "gauge", "meter", "fuel", "battery"
    )

    private const val GET = 0
    private const val CHECK = 1
    private const val SET = 2
    private const val ADD = 3
    private const val SPEND = 4
    private const val SHOW = 5
    private const val OTHER = 6

    fun tokens(s: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        fun flush() {
            if (sb.isNotEmpty()) { out.add(sb.toString()); sb.setLength(0) }
        }
        for (i in s.indices) {
            val c = s[i]
            if (!c.isLetterOrDigit()) { flush(); continue }
            if (sb.isNotEmpty()) {
                val prev = s[i - 1]
                if (c.isUpperCase() && (prev.isLowerCase() || prev.isDigit())) flush()
                else if (c.isUpperCase() && prev.isUpperCase() && i + 1 < s.length && s[i + 1].isLowerCase()) flush()
            }
            sb.append(c.lowercaseChar())
        }
        flush()
        return out
    }

    private fun kindOf(ret: String): String = when (ret) {
        "bool" -> "bool"
        "int", "uint", "short", "ushort", "byte", "sbyte", "char" -> "int"
        "long", "ulong" -> "long"
        "float" -> "float"
        "double" -> "double"
        "void" -> "void"
        else -> "other"
    }

    private fun bigValue(ret: String, kind: String): String = when (kind) {
        "long" -> "999999999"
        "float", "double" -> "999999"
        else -> when (ret) {
            "byte", "sbyte" -> "100"
            "short", "ushort" -> "30000"
            else -> "999999"
        }
    }

    private fun opOf(mt: List<String>, kind: String): Int {
        val first = if (mt.isEmpty()) "" else mt[0]
        val op = when (first) {
            "get" -> GET
            "is", "has", "can", "should", "need", "needs", "are", "was", "did", "check", "contains", "enough" -> CHECK
            "set" -> SET
            "add", "earn", "give", "grant", "reward", "increase", "gain", "collect", "receive", "increment" -> ADD
            "spend", "consume", "remove", "use", "deduct", "pay", "decrease", "subtract", "buy", "purchase",
            "charge", "take", "decrement", "hurt", "die", "kill" -> SPEND
            "show", "display", "play", "watch", "request", "load", "open", "start", "init", "initialize",
            "fetch", "preload" -> SHOW
            else -> OTHER
        }
        return if (op == GET && kind == "bool") CHECK else op
    }

    fun classify(r: MethodRow): List<Suggestion> {
        if (r.rva <= 0) return emptyList()
        if (r.ns.startsWith("UnityEngine") || r.ns.startsWith("System") || r.cls == "Resources") return emptyList()
        val mt = tokens(r.name)
        val ct = tokens(r.cls)
        if (mt.isEmpty()) return emptyList()
        val guessed = r.ret == "?"
        val kind = if (guessed) {
            when (opOf(mt, "other")) { CHECK -> "bool"; GET -> "int"; else -> "other" }
        } else kindOf(r.ret)
        val op = opOf(mt, kind)
        val out = ArrayList<Suggestion>()
        val ptxt = r.paramText()
        fun make(cat: String, k: String, v: String, note: String, score: Int) {
            if (k.isEmpty()) return
            val n = if (guessed) "$note (return type guessed from the name, verify)" else note
            out.add(Suggestion(cat, r.cls, r.name, r.rva, r.fileOff, r.ret, ptxt, k, v, n, score))
        }
        val numeric = kind == "int" || kind == "long" || kind == "float" || kind == "double"
        val negative = mt.any { it in NEGATIVE }

        // Resources
        run {
            val mHit = mt.any { it in CURRENCY }
            val cHit = ct.any { it in CURRENCY }
            val genericOk = mt.any { it in VALUE_WORDS } || op == SPEND || op == CHECK
            if (mHit || (cHit && genericOk)) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                when {
                    op == GET && numeric -> make("Resources", kind, bigValue(r.ret, kind), "Getter: the game will read this amount.", score + 2)
                    op == CHECK && kind == "bool" -> make("Resources", "bool", if (negative) "false" else "true", "Check (enough / can afford).", score + 1)
                    op == SPEND && kind == "void" -> make("Resources", "noop", "", "Spend: does nothing, so nothing is deducted.", score)
                    op == SPEND && kind == "bool" -> make("Resources", "bool", "true", "Spend: reports success without deducting.", score)
                    op == ADD -> make("Resources", "", "", "Adds an amount passed as an argument; prefer patching the getter.", 0)
                }
            }
        }
        // Ads
        run {
            val mHit = mt.any { it in ADS }
            val cHit = ct.any { it in ADS }
            if (mHit || (cHit && (op == SHOW || op == CHECK))) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                val rewarded = mt.contains("rewarded") || ct.contains("rewarded")
                when {
                    op == SHOW && kind == "void" -> make("Ads", "noop", "", "Show/load: does nothing. Rewarded ads will not give their reward.", score + 1)
                    op == SHOW && kind == "bool" -> make("Ads", "bool", "false", "Show/load reports failure.", score + 1)
                    op == CHECK && kind == "bool" -> {
                        val positive = mt.any { it in ADS_POSITIVE }
                        if (positive) make("Ads", "bool", "true", "Ads removed / no-ads state: true means no ads.", score + 2)
                        else if (rewarded && mt.any { it == "ready" || it == "loaded" || it == "available" }) make("Ads", "bool", "true", "Rewarded ad ready: true shows the watch-ad button.", score)
                        else make("Ads", "bool", "false", "Should/can show an ad: false means no ads.", score + 2)
                    }
                }
            }
        }
        // Premium
        run {
            val mHit = mt.any { it in PREMIUM }
            val cHit = ct.any { it in PREMIUM }
            if (mHit || (cHit && (op == CHECK || op == GET))) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                when {
                    (op == CHECK || op == GET) && kind == "bool" -> make("Premium", "bool", if (negative) "false" else "true", "Premium / VIP / purchased status.", score + 2)
                    op == GET && kind == "int" -> make("Premium", "int", "10", "Level, tier or days value; adjust if the game needs more.", score)
                }
            }
        }
        // Unlocks
        run {
            val mHit = mt.any { it in UNLOCKS }
            if (mHit) {
                val score = 4 + (if (r.paramTypes.isEmpty()) 1 else 0)
                if ((op == CHECK || op == GET) && kind == "bool") make("Unlocks", "bool", if (negative) "false" else "true", "Unlock status (level, chapter, character or skin).", score + 1)
            }
        }
        // Timers
        run {
            val mHit = mt.any { it in TIMERS }
            val cHit = ct.any { it in TIMERS }
            if (mHit || (cHit && (op == GET || op == CHECK))) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                when {
                    op == GET && numeric -> make("Timers", kind, "0", "Getter: cooldown / wait time; 0 means ready now.", score + 2)
                    op == CHECK && kind == "bool" -> make("Timers", "bool", if (negative) "false" else "true", "Ready / cooldown-finished check.", score + 1)
                }
            }
        }
        // Tutorial
        run {
            val mHit = mt.any { it in TUTORIAL }
            val cHit = ct.any { it in TUTORIAL }
            val rateUs = mt.containsAll(listOf("rate", "us")) || (mt.contains("review") && (op == SHOW || op == CHECK))
            if (mHit || cHit || rateUs) {
                val score = (if (mHit || rateUs) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                if ((op == SHOW || op == CHECK) && kind == "bool") make("Tutorial", "bool", if (negative) "true" else "false", "Skip the tutorial / hide the prompt.", score + 1)
            }
        }
        // Achievements
        run {
            val mHit = mt.any { it in ACHIEVEMENTS }
            val cHit = ct.any { it in ACHIEVEMENTS }
            if (mHit || (cHit && (op == GET || op == CHECK))) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                when {
                    op == CHECK && kind == "bool" -> make("Achievements", "bool", if (negative) "false" else "true", "Achievement / star unlocked check.", score + 1)
                    op == GET && numeric -> make("Achievements", kind, bigValue(r.ret, kind), "Getter: stars / achievement count.", score)
                }
            }
        }
        // God Mode
        run {
            val mHit = mt.any { it in GODMODE }
            val cHit = ct.any { it in GODMODE }
            if (mHit || (cHit && (op == SPEND || op == CHECK || op == GET))) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                val healthHit = mt.any { it in HEALTH_WORDS } || ct.any { it in HEALTH_WORDS }
                when {
                    op == SPEND && kind == "void" -> make("God Mode", "noop", "", "Damage / death trigger: does nothing, so it can't hurt or kill you.", score + 1)
                    op == CHECK && kind == "bool" -> make("God Mode", "bool", if (negative) "false" else "true", "Dead / down / game-over check.", score + 1)
                    op == GET && numeric && healthHit -> make("God Mode", kind, bigValue(r.ret, kind), "Getter: health / HP; always full.", score + 2)
                }
            }
        }
        // Damage — NOT a true multiplier, flat override only
        run {
            val mHit = mt.any { it in DAMAGE_DEALT }
            val cHit = ct.any { it in DAMAGE_DEALT }
            if ((mHit || cHit) && op == GET && numeric) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                make("Damage", kind, bigValue(r.ret, kind), "Getter: attack / damage stat. This overrides it to a large fixed value, not a true multiplier.", score + 2)
            }
        }
        // Stats
        run {
            val mHit = mt.any { it in STATS }
            val cHit = ct.any { it in STATS }
            if (mHit || (cHit && (op == GET || op == CHECK))) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                when {
                    op == GET && numeric -> make("Stats", kind, bigValue(r.ret, kind), "Getter: XP / level / stat; always max.", score + 2)
                    op == CHECK && kind == "bool" -> make("Stats", "bool", if (negative) "false" else "true", "Stat / level requirement check.", score + 1)
                }
            }
        }
        // Weapons
        run {
            val mHit = mt.any { it in WEAPONS }
            val cHit = ct.any { it in WEAPONS }
            if (mHit || (cHit && (op == GET || op == CHECK))) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                when {
                    op == GET && numeric -> make("Weapons", kind, bigValue(r.ret, kind), "Getter: ammo / durability / item count; always full.", score + 2)
                    op == CHECK && kind == "bool" -> make("Weapons", "bool", if (negative) "false" else "true", "Has weapon / can reload / inventory check.", score + 1)
                    op == SPEND && kind == "void" -> make("Weapons", "noop", "", "Consume ammo / item: does nothing, so nothing is consumed.", score)
                }
            }
        }
        // Movement
        run {
            val mHit = mt.any { it in MOVEMENT }
            val cHit = ct.any { it in MOVEMENT }
            if (mHit || (cHit && op == GET)) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                if (op == GET && numeric) make("Movement", kind, bigValue(r.ret, kind), "Getter: speed / jump / movement; very large value. Verify — too large can break physics.", score + 2)
            }
        }
        // Enemy
        run {
            val mHit = mt.any { it in ENEMY }
            val cHit = ct.any { it in ENEMY }
            if (mHit || (cHit && (op == GET || op == CHECK))) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                when {
                    op == GET && numeric -> make("Enemy", kind, "0", "Getter: enemy count / spawn rate / difficulty; 0 = none / easiest.", score + 2)
                    op == CHECK && kind == "bool" -> make("Enemy", "bool", "false", "Enemy active / can detect you: false.", score + 1)
                }
            }
        }
        // Map
        run {
            val mHit = mt.any { it in MAP_CAMERA }
            val cHit = ct.any { it in MAP_CAMERA }
            if (mHit || (cHit && (op == GET || op == CHECK))) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                when {
                    op == GET && numeric -> make("Map", kind, bigValue(r.ret, kind), "Getter: vision / view distance / reveal; max.", score + 2)
                    op == CHECK && kind == "bool" -> make("Map", "bool", "true", "Revealed / visible check: true.", score + 1)
                }
            }
        }
        // Save — bool checks only
        run {
            val mHit = mt.any { it in SAVE_ACCOUNT }
            if (mHit && op == CHECK && kind == "bool") make("Save", "bool", if (negative) "false" else "true", "Save / account check. Verify — do not patch save/load blindly.", 3)
        }
        // Shop
        run {
            val mHit = mt.any { it in SHOP_GACHA }
            val cHit = ct.any { it in SHOP_GACHA }
            if (mHit || (cHit && (op == GET || op == CHECK))) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                when {
                    op == GET && numeric -> make("Shop", kind, "0", "Getter: shop price / cost; 0 = free. Verify in game.", score + 2)
                    op == CHECK && kind == "bool" -> make("Shop", "bool", if (negative) "false" else "true", "Can buy / owns pack check.", score + 1)
                }
            }
        }
        // Daily
        run {
            val mHit = mt.any { it in DAILY_EVENTS }
            val cHit = ct.any { it in DAILY_EVENTS }
            if (mHit || (cHit && (op == GET || op == CHECK))) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                when {
                    op == GET && numeric -> make("Daily", kind, bigValue(r.ret, kind), "Getter: daily reward / quest count; max.", score + 1)
                    op == CHECK && kind == "bool" -> make("Daily", "bool", if (negative) "false" else "true", "Claimed / completed check.", score + 1)
                }
            }
        }
        // Settings — bool checks only
        run {
            val mHit = mt.any { it in SETTINGS_DEBUG }
            if (mHit && op == CHECK && kind == "bool") make("Settings", "bool", if (negative) "false" else "true", "Debug / cheat / setting enabled check. Verify — debug flags can crash a build.", 3)
        }
        // Vehicle
        run {
            val mHit = mt.any { it in VEHICLE_PET_BUILDING }
            val cHit = ct.any { it in VEHICLE_PET_BUILDING }
            if (mHit || (cHit && (op == GET || op == CHECK))) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                when {
                    op == GET && numeric -> make("Vehicle", kind, bigValue(r.ret, kind), "Getter: vehicle / pet / building stat; max.", score + 1)
                    op == CHECK && kind == "bool" -> make("Vehicle", "bool", if (negative) "false" else "true", "Owns vehicle / pet / building check.", score + 1)
                }
            }
        }
        // Bars
        run {
            val mHit = mt.any { it in BARS }
            val cHit = ct.any { it in BARS }
            if (mHit || (cHit && (op == GET || op == CHECK))) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                when {
                    op == GET && numeric -> make("Bars", kind, bigValue(r.ret, kind), "Getter: energy / stamina / mana / shield; always full.", score + 2)
                    op == CHECK && kind == "bool" -> make("Bars", "bool", if (negative) "false" else "true", "Bar empty / depleted check.", score + 1)
                    op == SPEND && kind == "void" -> make("Bars", "noop", "", "Drain bar: does nothing, so bar never drains.", score)
                }
            }
        }
        return out
    }

    fun rank(all: List<Suggestion>): List<Suggestion> {
        val perCat = HashMap<String, Int>()
        val sorted = all.filter { it.score > 0 }.sortedWith(
            compareByDescending<Suggestion> { it.score }.thenBy { it.cls }.thenBy { it.method }
        )
        val out = ArrayList<Suggestion>()
        for (s in sorted) {
            val n = perCat.getOrElse(s.category) { 0 }
            if (n >= 400) continue
            perCat[s.category] = n + 1
            out.add(s)
        }
        return out
    }
}