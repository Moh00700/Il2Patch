package com.example.il2patch

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Byte pattern with "??" wildcards, e.g. "FD 7B ?? A9 ?? ?? 00 91". */
class BytePattern(val bytes: ByteArray, val mask: BooleanArray) {
    companion object {
        fun parse(s: String): BytePattern? {
            val b = ArrayList<Byte>()
            val m = ArrayList<Boolean>()
            for (tok in s.trim().split(Regex("\\s+"))) {
                if (tok.isEmpty()) continue
                if (tok == "?" || tok == "??") {
                    b.add(0); m.add(false)
                    continue
                }
                if (tok.length % 2 != 0) return null
                var i = 0
                while (i < tok.length) {
                    val pair = tok.substring(i, i + 2)
                    if (pair == "??") {
                        b.add(0); m.add(false)
                    } else {
                        val v = pair.toIntOrNull(16) ?: return null
                        b.add(v.toByte()); m.add(true)
                    }
                    i += 2
                }
            }
            if (b.isEmpty() || m.none { it }) return null
            return BytePattern(b.toByteArray(), m.toBooleanArray())
        }
    }
}

/** Parsed ELF program headers, used to convert RVA/virtual addresses to file offsets. */
class ElfInfo(val is64: Boolean, val machine: Int, val segments: List<Segment>) {
    class Segment(val fileOff: Long, val vaddr: Long, val fileSize: Long)

    fun toFile(v: Long): Long? = segments.firstOrNull {
        it.fileSize > 0 && v >= it.vaddr && v - it.vaddr < it.fileSize
    }?.let { segment ->
        val delta = v - segment.vaddr
        if (delta > Long.MAX_VALUE - segment.fileOff) null else segment.fileOff + delta
    }

    fun toVaddr(f: Long): Long? = segments.firstOrNull {
        it.fileSize > 0 && f >= it.fileOff && f - it.fileOff < it.fileSize
    }?.let { segment ->
        val delta = f - segment.fileOff
        if (delta > Long.MAX_VALUE - segment.vaddr) null else segment.vaddr + delta
    }

    fun archName(): String = when (machine) {
        0xB7 -> "ARM64"
        0x28 -> "ARM32"
        0x3E -> "x86_64"
        0x03 -> "x86"
        else -> "machine 0x%X".format(machine)
    }

    companion object {
        fun parse(file: File): ElfInfo? {
            RandomAccessFile(file, "r").use { r ->
                val head = ByteArray(64)
                val n = r.read(head)
                if (n < 52) return null
                if (head[0] != 0x7F.toByte() || head[1] != 'E'.code.toByte() ||
                    head[2] != 'L'.code.toByte() || head[3] != 'F'.code.toByte()
                ) return null
                val is64 = head[4].toInt() == 2
                if (is64 && n < 64) return null
                val bb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
                val machine = bb.getShort(18).toInt() and 0xFFFF
                val phoff: Long
                val phentsize: Int
                val phnum: Int
                if (is64) {
                    phoff = bb.getLong(32)
                    phentsize = bb.getShort(54).toInt() and 0xFFFF
                    phnum = bb.getShort(56).toInt() and 0xFFFF
                } else {
                    phoff = bb.getInt(28).toLong() and 0xFFFFFFFFL
                    phentsize = bb.getShort(42).toInt() and 0xFFFF
                    phnum = bb.getShort(44).toInt() and 0xFFFF
                }
                if (phnum == 0 || phnum > 512 || phentsize < 32 || phoff <= 0 ||
                    phoff + phentsize.toLong() * phnum > r.length()
                ) return ElfInfo(is64, machine, emptyList())

                val table = ByteArray(phentsize * phnum)
                r.seek(phoff)
                r.readFully(table)
                val tb = ByteBuffer.wrap(table).order(ByteOrder.LITTLE_ENDIAN)
                val segs = ArrayList<Segment>()
                for (i in 0 until phnum) {
                    val base = i * phentsize
                    if (tb.getInt(base) != 1) continue // PT_LOAD
                    if (is64) {
                        segs.add(Segment(tb.getLong(base + 8), tb.getLong(base + 16), tb.getLong(base + 32)))
                    } else {
                        segs.add(
                            Segment(
                                tb.getInt(base + 4).toLong() and 0xFFFFFFFFL,
                                tb.getInt(base + 8).toLong() and 0xFFFFFFFFL,
                                tb.getInt(base + 16).toLong() and 0xFFFFFFFFL
                            )
                        )
                    }
                }
                return ElfInfo(is64, machine, segs)
            }
        }
    }
}

/** Reads, writes and searches the local working copy. Keeps an undo journal. */
class Patcher(val file: File) {
    class Rec(val off: Long, val old: ByteArray, val new: ByteArray)

    val journal = ArrayList<Rec>()

    fun size(): Long = file.length()

    fun read(off: Long, len: Int): ByteArray {
        require(off >= 0) { "negative file offset" }
        require(len >= 0) { "negative read length" }
        RandomAccessFile(file, "r").use { r ->
            if (off >= r.length() || len == 0) return ByteArray(0)
            val n = minOf(len.toLong(), r.length() - off).toInt()
            val b = ByteArray(n)
            r.seek(off)
            r.readFully(b)
            return b
        }
    }

    fun write(off: Long, data: ByteArray) {
        require(off >= 0) { "negative file offset" }
        require(data.isNotEmpty()) { "empty patch" }
        require(off <= file.length() && data.size.toLong() <= file.length() - off) {
            "write would extend beyond file bounds"
        }
        RandomAccessFile(file, "rw").use { r ->
            r.seek(off)
            r.write(data)
        }
    }

    /** Returns up to [max] file offsets where the pattern matches. */
    fun find(p: BytePattern, max: Int): List<Long> {
        if (max <= 0 || p.bytes.isEmpty() || p.bytes.size != p.mask.size) return emptyList()
        val res = ArrayList<Long>()
        val n = p.bytes.size
        RandomAccessFile(file, "r").use { r ->
            val len = r.length()
            val chunk = 1 shl 20
            val buf = ByteArray(chunk + n)
            var pos = 0L
            while (pos < len && res.size < max) {
                val cnt = minOf(buf.size.toLong(), len - pos).toInt()
                r.seek(pos)
                r.readFully(buf, 0, cnt)
                val lim = minOf(cnt - n, chunk - 1)
                var i = 0
                while (i <= lim) {
                    var ok = true
                    var j = 0
                    while (j < n) {
                        if (p.mask[j] && buf[i + j] != p.bytes[j]) {
                            ok = false
                            break
                        }
                        j++
                    }
                    if (ok) {
                        res.add(pos + i)
                        if (res.size >= max) break
                    }
                    i++
                }
                pos += chunk
            }
        }
        return res
    }
}

/**
 * Batch patch list format (one patch per line):
 *   1A2B3C: 20 00 80 52 C0 03 5F D6        raw hex at RVA / offset
 *   1A2B3C: mov w0, #1; ret                assembly
 *   1A2B3C: 1F 20 03 D5 => 00 00 80 52     only patch if the old bytes match
 *   find FD 7B ?? A9 +4: nop               find a unique pattern, patch at match + 0x4
 * Comments: lines starting with # or //, or text after //
 */
object Batch {
    class Op(
        val line: Int,
        val offset: Long?,
        val pattern: BytePattern?,
        val delta: Long,
        val old: ByteArray?,
        val new: ByteArray
    )

    fun parse(text: String, is64: Boolean): Pair<List<Op>, List<String>> {
        val ops = ArrayList<Op>()
        val errs = ArrayList<String>()
        val arrow = Regex("\\s*(=>|->)\\s*")
        val findRe = Regex("^find\\s+(.+?)\\s*(?:\\+\\s*(?:0x)?([0-9A-Fa-f]+))?$", RegexOption.IGNORE_CASE)

        text.lines().forEachIndexed { idx, rawLine ->
            val ln = idx + 1
            var line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) return@forEachIndexed
            val cm = line.indexOf("//")
            if (cm >= 0) line = line.substring(0, cm).trim()
            val colon = line.indexOf(':')
            if (colon < 0) {
                errs.add("line $ln: missing ':'")
                return@forEachIndexed
            }
            val left = line.substring(0, colon).trim()
            val right = line.substring(colon + 1).trim()
            try {
                var old: ByteArray? = null
                var newTxt = right
                val m = arrow.find(right)
                if (m != null) {
                    old = Hex.parse(right.substring(0, m.range.first))
                        ?: throw IllegalArgumentException("old bytes must be hex")
                    newTxt = right.substring(m.range.last + 1)
                }
                val nb = Hex.parse(newTxt) ?: Asm.assemble(newTxt, is64)

                if (left.lowercase().startsWith("find ")) {
                    val fm = findRe.find(left) ?: throw IllegalArgumentException("bad find syntax")
                    val pat = BytePattern.parse(fm.groupValues[1])
                        ?: throw IllegalArgumentException("bad pattern")
                    val d = if (fm.groupValues[2].isEmpty()) 0L else fm.groupValues[2].toLong(16)
                    ops.add(Op(ln, null, pat, d, old, nb))
                } else {
                    val off = left.removePrefix("0x").removePrefix("0X").toLongOrNull(16)
                        ?: throw IllegalArgumentException("offset must be hex")
                    ops.add(Op(ln, off, null, 0L, old, nb))
                }
            } catch (e: Exception) {
                errs.add("line $ln: ${e.message}")
            }
        }
        return Pair(ops, errs)
    }
}
