package com.example.il2patch

import java.io.ByteArrayOutputStream

class AsmError(msg: String) : Exception(msg)

/**
 * Tiny assembler for the instructions you typically need when stubbing out a function.
 * Not a general assembler.
 *
 * ARM64: nop | ret | mov/movz/movk wN|xN, #imm [, lsl #16|32|48] | fmov sN, wM | fmov dN, xM
 *        reti <int>   (mov w0,#int ; ret)
 *        retf <float> (builds the float in w0, fmov s0,w0 ; ret)
 * ARM32: nop | ret | bx lr | mov/movw rN, #imm16 | reti <int>
 * Also: return <bool|int|long|float|double> <value>   (whole function stub incl. ret)
 * Separate instructions with ';' or a new line.
 */
object Asm {
    private const val RET64 = 0xD65F03C0L
    private const val FMOV_S0_W0 = 0x1E270000

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

    /** ARM64: loads [bits] into w/x register [rd] with movz + movk (skips zero chunks). */
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

    /** ARM32: loads a 32-bit value into register [rd] with movw + movt. */
    private fun load32(rd: Int, v: Long): List<Int> {
        val list = ArrayList<Int>()
        list.add(movw(rd, v and 0xFFFF))
        val hi = (v ushr 16) and 0xFFFF
        if (hi != 0L) list.add(movt(rd, hi))
        return list
    }

    /**
     * Code that makes a function return a constant. [type]: bool, int, long, float, double.
     * ARM64: result in w0/x0 (float/double in s0/d0). ARM32 (softfp ABI): result in r0 (r0:r1 for 64-bit).
     */
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
            "long" -> {
                bits = imm(t)
                wide = true; fp = false
            }
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
        if (x.startsWith("-")) {
            neg = true
            x = x.substring(1)
        }
        val v = (if (x.startsWith("0x")) x.substring(2).toLongOrNull(16) else x.toLongOrNull())
            ?: throw AsmError("bad immediate '$s'")
        return if (neg) -v else v
    }

    /** Returns (is64bitRegister, number). */
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
}
