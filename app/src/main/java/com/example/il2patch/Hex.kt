package com.example.il2patch

object Hex {
    /** Parses "1F 20 03 D5", "0x1F2003D5", "1f,20,03,d5". Returns null if it is not pure hex. */
    fun parse(s: String): ByteArray? {
        val clean = s.replace("0x", "", ignoreCase = true).filter { !it.isWhitespace() && it != ',' }
        if (clean.isEmpty() || clean.length % 2 != 0) return null
        if (!clean.all { it in "0123456789abcdefABCDEF" }) return null
        return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    fun fmt(b: ByteArray): String = b.joinToString(" ") { "%02X".format(it) }

    /** 8 bytes per row: offset, hex, ascii. */
    fun dump(b: ByteArray, base: Long): String {
        val sb = StringBuilder()
        var i = 0
        while (i < b.size) {
            val n = minOf(8, b.size - i)
            sb.append("%08X  ".format(base + i))
            for (j in 0 until 8) sb.append(if (j < n) "%02X ".format(b[i + j]) else "   ")
            sb.append(" ")
            for (j in 0 until n) {
                val c = b[i + j].toInt() and 0xFF
                sb.append(if (c in 32..126) c.toChar() else '.')
            }
            sb.append('\n')
            i += 8
        }
        return sb.toString().trimEnd()
    }
}
