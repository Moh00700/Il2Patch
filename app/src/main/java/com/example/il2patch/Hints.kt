package com.example.il2patch

/** A function that probably controls a resource, ads, premium or similar status, with a ready-made patch. */
class Suggestion(
    val category: String,   // Resources, Ads, Premium, Unlocks, Timers, Tutorial, Achievements
    val cls: String,
    val method: String,
    val rva: Long,
    val fileOff: Long,
    val ret: String,        // return type as C# name
    val params: String,
    val kind: String,       // long/int/bool/float/double for "return <kind> <value>", or "" for void ("ret")
    val value: String,
    val note: String,
    val score: Int
) {
    /** Text that works in the Patch and Batch tabs. */
    fun patch(): String = if (kind.isEmpty()) "ret" else "return $kind $value"

    fun batchLine(): String = "%X: %s".format(rva, patch())
}

/** Name-based hints. These are guesses from function names: always verify before relying on one. */
object Hints {
    private val CURRENCY = setOf(
        "gem", "gems", "coin", "coins", "cash", "gold", "diamond", "diamonds", "money", "credit", "credits",
        "crystal", "crystals", "energy", "stamina", "ticket", "tickets", "resource", "resources", "currency",
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
        "insufficient", "lack", "missing", "not"
    )
    private val UNLOCKS = setOf(
        "unlock", "unlocked", "unlockable", "lock", "locked"
    )
    private val TIMERS = setOf(
        "cooldown", "cooldowns", "timer", "timers", "wait", "waiting", "regen", "regenerate", "regeneration",
        "recharge", "rechargetime", "refill", "refilltime"
    )
    private val TUTORIAL = setOf(
        "tutorial", "tutorials", "onboarding", "walkthrough", "firstrun", "firsttime"
    )
    private val ACHIEVEMENTS = setOf(
        "achievement", "achievements", "trophy", "trophies", "star", "stars", "medal", "medals", "badge", "badges"
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
            if (sb.isNotEmpty()) {
                out.add(sb.toString())
                sb.setLength(0)
            }
        }
        for (i in s.indices) {
            val c = s[i]
            if (!c.isLetterOrDigit()) {
                flush()
                continue
            }
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
            "charge", "take", "decrement" -> SPEND
            "show", "display", "play", "watch", "request", "load", "open", "start", "init", "initialize",
            "fetch", "preload" -> SHOW
            else -> OTHER
        }
        // a property getter that returns bool is a check, e.g. get_IsPremium
        return if (op == GET && kind == "bool") CHECK else op
    }

    fun classify(r: MethodRow): List<Suggestion> {
        if (r.rva <= 0) return emptyList()
        if (r.ns.startsWith("UnityEngine") || r.ns.startsWith("System") || r.cls == "Resources") return emptyList()
        val mt = tokens(r.name)
        val ct = tokens(r.cls)
        if (mt.isEmpty()) return emptyList()
        // when the type table could not be read the return type is "?": guess it from the name
        val guessed = r.ret == "?"
        val kind = if (guessed) {
            when (opOf(mt, "other")) {
                CHECK -> "bool"
                GET -> "int"
                else -> "other"
            }
        } else kindOf(r.ret)
        val op = opOf(mt, kind)
        val out = ArrayList<Suggestion>()
        val ptxt = r.paramText()

        fun make(cat: String, k: String, v: String, note: String, score: Int) {
            if (k.isEmpty()) return // void functions are never suggested
            val n = if (guessed) "$note (return type guessed from the name, verify)" else note
            out.add(Suggestion(cat, r.cls, r.name, r.rva, r.fileOff, r.ret, ptxt, k, v, n, score))
        }

        val numeric = kind == "int" || kind == "long" || kind == "float" || kind == "double"
        val negative = mt.any { it in NEGATIVE }

        // ---- resources
        run {
            val mHit = mt.any { it in CURRENCY }
            val cHit = ct.any { it in CURRENCY }
            val genericOk = mt.any { it in VALUE_WORDS } || op == SPEND || op == CHECK
            if (mHit || (cHit && genericOk)) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                when {
                    op == GET && numeric ->
                        make("Resources", kind, bigValue(r.ret, kind), "Getter: the game will read this amount.", score + 2)
                    op == CHECK && kind == "bool" ->
                        make(
                            "Resources", "bool", if (negative) "false" else "true",
                            "Check (enough / can afford).", score + 1
                        )
                    op == SPEND && kind == "void" ->
                        make("Resources", "", "", "Spend: does nothing, so nothing is deducted.", score)
                    op == SPEND && kind == "bool" ->
                        make("Resources", "bool", "true", "Spend: reports success without deducting.", score)
                    op == ADD ->
                        make("Resources", "", "", "Adds an amount passed as an argument; prefer patching the getter.", 0)
                }
            }
        }

        // ---- ads
        run {
            val mHit = mt.any { it in ADS }
            val cHit = ct.any { it in ADS }
            if (mHit || (cHit && (op == SHOW || op == CHECK))) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                val rewarded = mt.contains("rewarded") || ct.contains("rewarded")
                when {
                    op == SHOW && kind == "void" ->
                        make("Ads", "", "", "Show/load: does nothing. Rewarded ads will not give their reward.", score + 1)
                    op == SHOW && kind == "bool" ->
                        make("Ads", "bool", "false", "Show/load reports failure.", score + 1)
                    op == CHECK && kind == "bool" -> {
                        val positive = mt.any { it in ADS_POSITIVE }
                        if (positive) {
                            make("Ads", "bool", "true", "Ads removed / no-ads state: true means no ads.", score + 2)
                        } else if (rewarded && mt.any { it == "ready" || it == "loaded" || it == "available" }) {
                            make("Ads", "bool", "true", "Rewarded ad ready: true shows the watch-ad button.", score)
                        } else {
                            make("Ads", "bool", "false", "Should/can show an ad: false means no ads.", score + 2)
                        }
                    }
                }
            }
        }

        // ---- premium / vip
        run {
            val mHit = mt.any { it in PREMIUM }
            val cHit = ct.any { it in PREMIUM }
            if (mHit || (cHit && (op == CHECK || op == GET))) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                when {
                    (op == CHECK || op == GET) && kind == "bool" ->
                        make(
                            "Premium", "bool", if (negative) "false" else "true",
                            "Premium / VIP / purchased status.", score + 2
                        )
                    op == GET && kind == "int" ->
                        make("Premium", "int", "10", "Level, tier or days value; adjust if the game needs more.", score)
                }
            }
        }

        // ---- unlocks (levels, chapters, characters, skins, content)
        run {
            val mHit = mt.any { it in UNLOCKS }
            if (mHit) {
                val score = 4 + (if (r.paramTypes.isEmpty()) 1 else 0)
                if ((op == CHECK || op == GET) && kind == "bool") {
                    make(
                        "Unlocks", "bool", if (negative) "false" else "true",
                        "Unlock status (level, chapter, character or skin).", score + 1
                    )
                }
            }
        }

        // ---- timers / cooldowns / energy regen
        run {
            val mHit = mt.any { it in TIMERS }
            val cHit = ct.any { it in TIMERS }
            if (mHit || (cHit && (op == GET || op == CHECK))) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                when {
                    op == GET && numeric ->
                        make("Timers", kind, "0", "Getter: cooldown / wait time; 0 means ready now.", score + 2)
                    op == CHECK && kind == "bool" ->
                        make(
                            "Timers", "bool", if (negative) "false" else "true",
                            "Ready / cooldown-finished check.", score + 1
                        )
                }
            }
        }

        // ---- tutorial skips / rate-us prompts
        run {
            val mHit = mt.any { it in TUTORIAL }
            val cHit = ct.any { it in TUTORIAL }
            val rateUs = mt.containsAll(listOf("rate", "us")) || (mt.contains("review") && (op == SHOW || op == CHECK))
            if (mHit || cHit || rateUs) {
                val score = (if (mHit || rateUs) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                if ((op == SHOW || op == CHECK) && kind == "bool") {
                    make(
                        "Tutorial", "bool", if (negative) "true" else "false",
                        "Skip the tutorial / hide the prompt.", score + 1
                    )
                }
            }
        }

        // ---- achievements / stars / trophies
        run {
            val mHit = mt.any { it in ACHIEVEMENTS }
            val cHit = ct.any { it in ACHIEVEMENTS }
            if (mHit || (cHit && (op == GET || op == CHECK))) {
                val score = (if (mHit) 4 else 2) + (if (r.paramTypes.isEmpty()) 1 else 0)
                when {
                    op == CHECK && kind == "bool" ->
                        make(
                            "Achievements", "bool", if (negative) "false" else "true",
                            "Achievement / star unlocked check.", score + 1
                        )
                    op == GET && numeric ->
                        make("Achievements", kind, bigValue(r.ret, kind), "Getter: stars / achievement count.", score)
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
