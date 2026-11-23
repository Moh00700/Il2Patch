package com.example.il2patch

import java.io.ByteArrayOutputStream

/**
 * 🟠 Asm.kt — Tiny assembler + ARM64 scanner
 * --------------------------------------------------
 * What this file does, in plain language:
 *
 *   🟢 assemble()     — turns text like "mov w0, #1; ret" into real bytes
 *   🟢 returnValue()  — builds "return true / 10000 / 1.5" code for you
 *   🟠 flipAddSub()   — turns ONE add -> sub, or sub -> add, in place
 *   🔎 scanDeep()     — walks a function + the functions it calls, lists every sub
 *   💬 describeSub()  — turns a raw word into "sub x5, x5, x1" so you can pick the right one
 *
 * 🎨 COLOUR CODE (matches MainActivity UI):
 *   🟢 Green  = safe, return-value patch
 *   🟠 Orange = Bytes / Flip — advanced, check first
 *   🔵 Blue   = Batch list
 *   🟣 Purple = Search by bytes
 *   🔴 Red    = dangerous / can crash if wrong address
 */
class AsmError(msg: String) : Exception(msg)

object Asm {
    // 🔴 RET — every function ends with this (ARM64). Scanner stops here.
    private const val RET64 = 0xD65F03C0L
    private const val FMOV_S0_W0 = 0x1E270000

    // 🟢 Turn "mov w0, #1; ret" into bytes the game can run
    fun assemble(text: String, is64: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        val parts = text.split(';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) throw AsmError("nothing to assemble")
        for (p in parts) {
            if (p.lowercase().startsWith("return ")) {
                val tk = tokens(p)
                out.write(returnValue(tk[1], tk.getOrElse(2) { "" }, is64))
                continue
            }
            val words = if (is64) arm64(p) else arm32(p)
            for (w in words) {
                out.write(w and 0xFF)
                out.write((w shr 8) and 0xFF)
                out.write((w shr 16) and 0xFF)
                out.write((w ushr 24) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    private fun tokens(p: String): List<String> =
        p.lowercase().replace(",", " ").trim().split(Regex("\\s+"))

    private fun le(words: List<Int>): ByteArray {
        val out = ByteArrayOutputStream()
        for (w in words) {
            out.write(w and 0xFF)
            out.write((w shr 8) and 0xFF)
            out.write((w shr 16) and 0xFF)
            out.write((w ushr 24) and 0xFF)
        }
        return out.toByteArray()
    }

    private fun load64(x: Boolean, rd: Int, bits: Long): List<Int> {
        val list = ArrayList<Int>()
        val chunks = if (x) 4 else 2
        for (i in 0 until chunks) {
            val c = (bits ushr (16 * i)) and 0xFFFF
            if (c == 0L) continue
            list.add(movImm(if (list.isEmpty()) "mov" else "movk", x, rd, c, 16 * i))
        }
        if (list.isEmpty()) list.add(movImm("mov", x, rd, 0, 0))
        return list
    }

    private fun movt(rd: Int, v: Long): Int {
        val i = v.toInt()
        return 0xE3400000L.toInt() or (((i shr 12) and 0xF) shl 16) or (rd shl 12) or (i and 0xFFF)
    }

    private fun load32(rd: Int, v: Long): List<Int> {
        val list = ArrayList<Int>()
        list.add(movw(rd, v and 0xFFFF))
        val hi = (v ushr 16) and 0xFFFF
        if (hi != 0L) list.add(movt(rd, hi))
        return list
    }

    // 🟢 Build "return <value>" code — used by Patch → Value tab
    //    bool true/false | int 999 | long 10000 | float 1.5 | double 2.5
    fun returnValue(type: String, text: String, is64: Boolean): ByteArray {
        val t = text.trim()
        if (t.isEmpty()) throw AsmError("enter a value")
        val bits: Long
        val wide: Boolean
        val fp: Boolean
        when (type.lowercase()) {
            "bool" -> {
                bits = when (t.lowercase()) {
                    "true", "1" -> 1L
                    "false", "0" -> 0L
                    else -> throw AsmError("bool must be true or false")
                }
                wide = false; fp = false
            }
            "int" -> {
                val v = imm(t)
                if (v < Int.MIN_VALUE.toLong() || v > 0xFFFFFFFFL) throw AsmError("int out of range")
                bits = v and 0xFFFFFFFFL
                wide = false; fp = false
            }
            "long" -> { bits = imm(t); wide = true; fp = false }
            "float" -> {
                val f = t.toFloatOrNull() ?: throw AsmError("bad float '$t'")
                bits = f.toRawBits().toLong() and 0xFFFFFFFFL
                wide = false; fp = true
            }
            "double" -> {
                val d = t.toDoubleOrNull() ?: throw AsmError("bad double '$t'")
                bits = d.toRawBits()
                wide = true; fp = true
            }
            else -> throw AsmError("unknown type '$type'")
        }
        val w = ArrayList<Int>()
        if (is64) {
            w.addAll(load64(wide, 0, bits))
            if (fp) w.add(if (wide) 0x9E670000L.toInt() else 0x1E270000)
            w.add(RET64.toInt())
        } else {
            w.addAll(load32(0, bits and 0xFFFFFFFFL))
            if (wide) w.addAll(load32(1, (bits ushr 32) and 0xFFFFFFFFL))
            w.add(0xE12FFF1EL.toInt())
        }
        return le(w)
    }

    private fun imm(s: String?): Long {
        var x = (s ?: throw AsmError("missing immediate")).trim().removePrefix("#")
        var neg = false
        if (x.startsWith("-")) { neg = true; x = x.substring(1) }
        val v = (if (x.startsWith("0x")) x.substring(2).toLongOrNull(16) else x.toLongOrNull())
            ?: throw AsmError("bad immediate '$s'")
        return if (neg) -v else v
    }

    private fun reg64(s: String?): Pair<Boolean, Int> {
        val t = s ?: throw AsmError("missing register")
        if (t == "wzr") return Pair(false, 31)
        if (t == "xzr") return Pair(true, 31)
        val m = Regex("^([wx])(\\d{1,2})$").find(t) ?: throw AsmError("bad register '$t'")
        val n = m.groupValues[2].toInt()
        if (n > 31) throw AsmError("bad register '$t'")
        return Pair(m.groupValues[1] == "x", n)
    }

    private fun movImm(op: String, x: Boolean, rd: Int, imm: Long, shift: Int): Int {
        if (shift < 0 || shift % 16 != 0 || shift > (if (x) 48 else 16)) throw AsmError("bad shift")
        val hw = shift / 16
        if (op == "movk") {
            if (imm < 0 || imm > 0xFFFF) throw AsmError("movk immediate must be 0..65535")
            return (if (x) 0xF2800000L else 0x72800000L).toInt() or (hw shl 21) or (imm.toInt() shl 5) or rd
        }
        if (imm < 0) {
            val n = -imm - 1
            if (n > 0xFFFF) throw AsmError("immediate out of range")
            return (if (x) 0x92800000L else 0x12800000L).toInt() or (hw shl 21) or (n.toInt() shl 5) or rd
        }
        if (imm > 0xFFFF) throw AsmError("immediate must fit 16 bits (use movz + movk)")
        return (if (x) 0xD2800000L else 0x52800000L).toInt() or (hw shl 21) or (imm.toInt() shl 5) or rd
    }

    private fun arm64(p: String): IntArray {
        val t = tokens(p)
        when (t[0]) {
            "nop" -> return intArrayOf(0xD503201FL.toInt())
            "ret" -> return intArrayOf(RET64.toInt())
            "mov", "movz", "movk" -> {
                val r = reg64(t.getOrNull(1))
                val v = imm(t.getOrNull(2))
                var shift = 0
                if (t.size >= 5 && t[3] == "lsl") shift = imm(t[4]).toInt()
                return intArrayOf(movImm(if (t[0] == "movk") "movk" else "mov", r.first, r.second, v, shift))
            }
            "reti" -> {
                val v = imm(t.getOrNull(1))
                return intArrayOf(movImm("mov", false, 0, v, 0), RET64.toInt())
            }
            "retf" -> {
                val f = t.getOrNull(1)?.toFloatOrNull() ?: throw AsmError("retf needs a float, e.g. retf 1.5")
                val bits = f.toRawBits()
                val lo = (bits and 0xFFFF).toLong()
                val hi = ((bits ushr 16) and 0xFFFF).toLong()
                val list = ArrayList<Int>()
                list.add(movImm("mov", false, 0, lo, 0))
                if (hi != 0L) list.add(movImm("movk", false, 0, hi, 16))
                list.add(FMOV_S0_W0)
                list.add(RET64.toInt())
                return list.toIntArray()
            }
            "fmov" -> {
                val d = t.getOrNull(1) ?: throw AsmError("fmov needs operands")
                val s = reg64(t.getOrNull(2))
                val n = d.drop(1).toIntOrNull() ?: throw AsmError("bad register '$d'")
                if (n > 31) throw AsmError("bad register '$d'")
                return when {
                    d.startsWith("s") && !s.first -> intArrayOf(0x1E270000 or (s.second shl 5) or n)
                    d.startsWith("d") && s.first -> intArrayOf(0x9E670000L.toInt() or (s.second shl 5) or n)
                    else -> throw AsmError("supported: fmov sN, wM  /  fmov dN, xM")
                }
            }
        }
        throw AsmError("unsupported ARM64 instruction '${t[0]}'")
    }

    private fun reg32(s: String?): Int {
        val t = s ?: throw AsmError("missing register")
        val n = when (t) {
            "sp" -> 13
            "lr" -> 14
            "pc" -> 15
            else -> Regex("^r(\\d{1,2})$").find(t)?.groupValues?.get(1)?.toInt() ?: throw AsmError("bad register '$t'")
        }
        if (n > 15) throw AsmError("bad register '$t'")
        return n
    }

    private fun movw(rd: Int, v: Long): Int {
        if (v < 0 || v > 0xFFFF) throw AsmError("immediate must be 0..65535")
        val i = v.toInt()
        return 0xE3000000L.toInt() or (((i shr 12) and 0xF) shl 16) or (rd shl 12) or (i and 0xFFF)
    }

    private fun arm32(p: String): IntArray {
        val t = tokens(p)
        val bxLr = 0xE12FFF1EL.toInt()
        when (t[0]) {
            "nop" -> return intArrayOf(0xE320F000L.toInt())
            "ret" -> return intArrayOf(bxLr)
            "bx" -> {
                if (t.getOrNull(1) == "lr") return intArrayOf(bxLr)
                throw AsmError("only 'bx lr' is supported")
            }
            "mov", "movw" -> return intArrayOf(movw(reg32(t.getOrNull(1)), imm(t.getOrNull(2))))
            "reti" -> return intArrayOf(movw(0, imm(t.getOrNull(1))), bxLr)
        }
        throw AsmError("unsupported ARM32 instruction '${t[0]}'")
    }

    // 🟠 Flip ONE add <-> sub — the heart of "Increase on spend"
    //    SpendGems() does: gems = gems - amount  (sub)
    //    Flip makes it:   gems = gems + amount  (add)  -> spending gives you gems 💸
    //    Returns null if it's not a recognised add/sub, or if it touches SP (x31) — we never touch SP.
    fun flipAddSub(word: Int): Pair<Int, String>? {
        val rd = word and 0x1F
        val rn = (word ushr 5) and 0x1F
        val opBit = (word ushr 30) and 1
        val immClass = (word ushr 24) and 0x1F
        val regClass = (word ushr 24) and 0x1F
        val now = if (opBit == 0) "add" else "sub"
        val to = if (opBit == 0) "sub" else "add"
        if (immClass == 0b10001) {
            if (rd == 31 || rn == 31) return null
            return (word xor (1 shl 30)) to "$now (immediate) -> $to"
        }
        if (regClass == 0b01011) {
            if (((word ushr 21) and 1) == 1) return null
            if (rd == 31 || rn == 31) return null
            return (word xor (1 shl 30)) to "$now (register) -> $to"
        }
        return null
    }

    fun scanForSubCandidates(bytes: ByteArray, maxWords: Int = 1024): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        var i = 0
        while (i + 4 <= bytes.size && i / 4 < maxWords) {
            val word = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8) or
                ((bytes[i + 2].toInt() and 0xFF) shl 16) or ((bytes[i + 3].toInt() and 0xFF) shl 24)
            if (word == RET64.toInt()) break
            val flip = flipAddSub(word)
            if (flip != null && flip.second.startsWith("sub")) out.add(i to flip.second)
            i += 4
        }
        return out
    }

    // 💬 Human-readable: "sub x5, x5, x1" — registers tell you WHICH sub is the resource.
    //    Pick the one that uses the `amount` parameter, NOT the one that says #1 (usually a counter).
    fun describeSub(word: Int): String {
        val rd = word and 0x1F
        val rn = (word ushr 5) and 0x1F
        val rm = (word ushr 16) and 0x1F
        val immClass = (word ushr 24) and 0x1F
        return if (immClass == 0b10001) {
            val imm12 = (word ushr 10) and 0xFFF
            "sub x$rd, x$rn, #$imm12"
        } else {
            "sub x$rd, x$rn, x$rm"
        }
    }

    // 🔎 Deep Scan — follows branches + calls to find the real sub
    //    Spend() often looks like: Spend() -> TrySpend() -> set_Resource(get - amount)
    //    The sub is 2-3 calls deep, not in Spend() itself. This follows up to depth 3.
    //    If it hits BLR (virtual/interface call) it CANNOT know the target — it reports it
    //    instead of silently failing, so you know to follow it by hand in Ghidra.
    fun scanDeep(
        read: (Long) -> Int?,
        startOff: Long,
        maxVisits: Int = 4096,
        maxCallDepth: Int = 3
    ): List<Triple<Long, String, Boolean>> {
        data class Item(val off: Long, val depth: Int, val viaCall: Boolean)
        val out = ArrayList<Triple<Long, String, Boolean>>()
        val visited = HashSet<Long>()
        val queue = ArrayDeque<Item>()
        queue.addLast(Item(startOff, 0, false))
        var visits = 0
        while (queue.isNotEmpty() && visits < maxVisits) {
            val first = queue.removeFirst()
            var off = first.off
            val viaCall = first.viaCall
            val depth = first.depth
            while (visits < maxVisits) {
                if (!visited.add(off)) break
                visits++
                val word = read(off) ?: break
                if (word == RET64.toInt()) break // 🔴 End of this function
                val top6 = (word ushr 25) and 0x3F
                val top8 = (word ushr 24) and 0xFF
                var stop = false
                when {
                    // TBZ / TBNZ — "if bit is set, jump" — follow BOTH paths
                    top6 == 0b011011 -> {
                        val b5 = (word ushr 31) and 1
                        var imm14 = (word ushr 5) and 0x3FFF
                        if (imm14 and 0x2000 != 0) imm14 -= 0x4000
                        queue.addLast(Item(off + imm14 * 4L, depth, viaCall))
                    }
                    // CBZ / CBNZ — "if zero, jump" — follow BOTH paths
                    top6 == 0b011010 -> {
                        var imm19 = (word ushr 5) and 0x7FFFF
                        if (imm19 and 0x40000 != 0) imm19 -= 0x80000
                        queue.addLast(Item(off + imm19 * 4L, depth, viaCall))
                    }
                    // B.cond — "if condition, jump" — follow BOTH paths
                    top8 == 0x54 && ((word ushr 4) and 1) == 0 -> {
                        var imm19 = (word ushr 5) and 0x7FFFF
                        if (imm19 and 0x40000 != 0) imm19 -= 0x80000
                        queue.addLast(Item(off + imm19 * 4L, depth, viaCall))
                    }
                    // B — unconditional jump — follow it, STOP this path
                    ((word ushr 26) and 0x3F) == 0b000101 -> {
                        var imm26 = word and 0x3FFFFFF
                        if (imm26 and 0x2000000 != 0) imm26 -= 0x4000000
                        queue.addLast(Item(off + imm26 * 4L, depth, viaCall))
                        stop = true
                    }
                    // BL — direct call — follow it (depth + 1), AND keep going here (it returns)
                    ((word ushr 26) and 0x3F) == 0b100101 -> {
                        var imm26 = word and 0x3FFFFFF
                        if (imm26 and 0x2000000 != 0) imm26 -= 0x4000000
                        if (depth < maxCallDepth) queue.addLast(Item(off + imm26 * 4L, depth + 1, true))
                    }
                    // BLR Xn — indirect / virtual call, target cannot be resolved from bytes alone.
                    // ✅ FIXED (Claude catch): bits 15:10 are 000000 in real BR/BLR/RET, never 111111.
                    // Bit 21 = 1 distinguishes BLR from BR, bits 20:16 = 11111 is the shared op2 field.
                    ((word ushr 22) and 0x3FF) == 0b1101011000 &&
                    ((word ushr 21) and 1) == 1 &&
                    ((word ushr 16) and 0x1F) == 0b11111 &&
                    ((word ushr 10) and 0x3F) == 0b000000 &&
                    ((word ushr 5) and 0x1F) != 31 -> {
                        out.add(Triple(off, "indirect-call (BLR, cannot follow)", viaCall))
                    }
                    // Anything else — is it a sub we can flip?
                    else -> {
                        val flip = flipAddSub(word)
                        if (flip != null && flip.second.startsWith("sub")) out.add(Triple(off, flip.second, viaCall))
                    }
                }
                if (stop) break
                off += 4
            }
        }
        return out
    }
}
