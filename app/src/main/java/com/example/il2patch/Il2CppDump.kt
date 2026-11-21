package com.example.il2patch

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.io.Writer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Arrays

class DumpError(msg: String) : Exception(msg)

/** One method of the game, as read from global-metadata.dat plus the code address from libil2cpp.so. */
class MethodRow(
    val image: String,
    val ns: String,
    val cls: String,
    val name: String,
    val rva: Long,          // -1 when the library has no code for it (abstract, generic, ...)
    val fileOff: Long,      // -1 when not mapped to the file
    val ret: String,
    val paramTypes: List<String>,
    val paramNames: List<String>,
    val isStatic: Boolean,
    val token: Long
) {
    fun paramText(): String {
        val sb = StringBuilder("(")
        for (i in paramTypes.indices) {
            if (i > 0) sb.append(", ")
            sb.append(paramTypes[i]).append(' ').append(paramNames.getOrElse(i) { "p$i" })
        }
        return sb.append(')').toString()
    }
}

private class LongList {
    var a = LongArray(1024)
    var n = 0
    fun add(v: Long) {
        if (n == a.size) a = a.copyOf(n * 2)
        a[n++] = v
    }
}

/**
 * Virtual-address view of an ELF file. Resolves relocated data pointers (RELA and Android packed APS2
 * relative relocations), because on 64-bit Android the pointers in libil2cpp.so are usually not stored in the file.
 */
class ElfView(private val buf: ByteBuffer, val info: ElfInfo) {
    val is64: Boolean = info.is64
    val w: Int = if (is64) 8 else 4
    private val nSeg = info.segments.size
    private val segOff = LongArray(nSeg) { info.segments[it].fileOff }
    private val segVa = LongArray(nSeg) { info.segments[it].vaddr }
    private val segSz = LongArray(nSeg) { info.segments[it].fileSize }

    private var relOff = LongArray(0)
    private var relVal = LongArray(0)
    var relCount = 0
        private set

    fun fileOf(v: Long): Int {
        for (i in 0 until nSeg) {
            if (v >= segVa[i] && v < segVa[i] + segSz[i]) return (v - segVa[i] + segOff[i]).toInt()
        }
        return -1
    }

    fun vaddrOf(f: Long): Long {
        for (i in 0 until nSeg) {
            if (f >= segOff[i] && f < segOff[i] + segSz[i]) return f - segOff[i] + segVa[i]
        }
        return -1
    }

    /** 32-bit value stored in the file at a virtual address, or -1. */
    fun u32(v: Long): Long {
        val f = fileOf(v)
        if (f < 0 || f + 4 > buf.limit()) return -1
        return buf.getInt(f).toLong() and 0xFFFFFFFFL
    }

    /** Pointer stored at a virtual address with relocations applied, or -1. */
    fun readPtr(v: Long): Long {
        if (is64 && relCount > 0) {
            val i = Arrays.binarySearch(relOff, 0, relCount, v)
            if (i >= 0) return relVal[i]
        }
        val f = fileOf(v)
        if (f < 0 || f + w > buf.limit()) return -1
        return if (is64) buf.getLong(f) else buf.getInt(f).toLong() and 0xFFFFFFFFL
    }

    private var sp = 0

    private fun sleb(): Long {
        var result = 0L
        var shift = 0
        var b: Int
        do {
            b = buf.get(sp++).toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            shift += 7
        } while ((b and 0x80) != 0)
        if (shift < 64 && (b and 0x40) != 0) result = result or (-1L shl shift)
        return result
    }

    /** Reads DT_RELA and DT_ANDROID_RELA relative relocations. Returns a short description. */
    fun loadRelocations(): String {
        if (!is64) return "32-bit library: pointers are stored in the file"
        val phoff = buf.getLong(32)
        val phentsize = buf.getShort(54).toInt() and 0xFFFF
        val phnum = buf.getShort(56).toInt() and 0xFFFF
        var dynOff = -1L
        var dynSize = 0L
        for (i in 0 until phnum) {
            val b = phoff + i.toLong() * phentsize
            if (b + 56 > buf.limit()) break
            if (buf.getInt(b.toInt()) == 2) {
                dynOff = buf.getLong(b.toInt() + 8)
                dynSize = buf.getLong(b.toInt() + 32)
            }
        }
        if (dynOff < 0) return "no dynamic section"

        var rela = 0L
        var relasz = 0L
        var arela = 0L
        var arelasz = 0L
        var p = dynOff
        while (p + 16 <= dynOff + dynSize && p + 16 <= buf.limit()) {
            val tag = buf.getLong(p.toInt())
            val v = buf.getLong(p.toInt() + 8)
            if (tag == 0L) break
            if (tag == 7L) rela = v
            if (tag == 8L) relasz = v
            if (tag == 0x60000011L) arela = v
            if (tag == 0x60000012L) arelasz = v
            p += 16
        }

        val offs = LongList()
        val vals = LongList()
        val kinds = StringBuilder()

        if (rela != 0L && relasz > 0) {
            val f = fileOf(rela)
            if (f >= 0) {
                var q = f.toLong()
                val end = q + relasz
                while (q + 24 <= end && q + 24 <= buf.limit()) {
                    val off = buf.getLong(q.toInt())
                    val inf = buf.getLong(q.toInt() + 8)
                    val add = buf.getLong(q.toInt() + 16)
                    val type = (inf and 0xFFFFFFFFL).toInt()
                    if (type == 1027 || type == 8) {   // R_AARCH64_RELATIVE, R_X86_64_RELATIVE
                        offs.add(off)
                        vals.add(add)
                    }
                    q += 24
                }
                kinds.append("RELA ")
            }
        }

        if (arela != 0L && arelasz > 8) {
            val f = fileOf(arela)
            if (f >= 0 && buf.get(f) == 'A'.code.toByte() && buf.get(f + 1) == 'P'.code.toByte() &&
                buf.get(f + 2) == 'S'.code.toByte() && buf.get(f + 3) == '2'.code.toByte()
            ) {
                sp = f + 4
                val end = f + arelasz
                val count = sleb()
                var offset = sleb()
                var addend = 0L
                var emitted = 0L
                while (emitted < count && sp < end && sp < buf.limit()) {
                    val gsize = sleb()
                    val gflags = sleb()
                    val byInfo = (gflags and 1L) != 0L
                    val byDelta = (gflags and 2L) != 0L
                    val byAddend = (gflags and 4L) != 0L
                    val hasAddend = (gflags and 8L) != 0L
                    val gdelta = if (byDelta) sleb() else 0L
                    val ginfo = if (byInfo) sleb() else 0L
                    if (hasAddend && byAddend) addend += sleb()
                    if (!hasAddend) addend = 0L
                    var i = 0L
                    while (i < gsize) {
                        offset += if (byDelta) gdelta else sleb()
                        val inf = if (byInfo) ginfo else sleb()
                        if (hasAddend && !byAddend) addend += sleb()
                        val type = (inf and 0xFFFFFFFFL).toInt()
                        if (type == 1027 || type == 8) {
                            offs.add(offset)
                            vals.add(addend)
                        }
                        i++
                    }
                    emitted += gsize
                }
                kinds.append("APS2 ")
            }
        }

        val n = offs.n
        var sorted = true
        for (i in 1 until n) {
            if (offs.a[i] < offs.a[i - 1]) {
                sorted = false
                break
            }
        }
        if (sorted) {
            relOff = offs.a
            relVal = vals.a
        } else {
            val idx = (0 until n).sortedBy { offs.a[it] }
            val so = LongArray(n)
            val sv = LongArray(n)
            for (i in 0 until n) {
                so[i] = offs.a[idx[i]]
                sv[i] = vals.a[idx[i]]
            }
            relOff = so
            relVal = sv
        }
        relCount = n
        return if (kinds.isEmpty()) "none found" else kinds.toString().trim() + ", $n relative entries"
    }

    /** Virtual addresses of every pointer-sized slot holding [target]. */
    fun findPointersTo(target: Long): List<Long> {
        val out = LinkedHashSet<Long>()
        if (is64) {
            for (i in 0 until relCount) if (relVal[i] == target) out.add(relOff[i])
        }
        for (s in 0 until nSeg) {
            var p = segOff[s]
            val end = minOf(segOff[s] + segSz[s], buf.limit().toLong()) - w
            while (p <= end) {
                val v = if (is64) buf.getLong(p.toInt()) else buf.getInt(p.toInt()).toLong() and 0xFFFFFFFFL
                if (v == target) out.add(p - segOff[s] + segVa[s])
                p += w
            }
        }
        return out.toList()
    }

    /**
     * Finds Il2CppMetadataRegistration by its signature: the field-offset table and the type-size table both have
     * one entry per type definition, and sit two (count, pointer) pairs apart.
     */
    fun findMetadataRegistration(typeDefCount: Int): Long {
        val n = typeDefCount.toLong()
        for (s in 0 until nSeg) {
            var p = segOff[s]
            val end = minOf(segOff[s] + segSz[s], buf.limit().toLong()) - 2L * w - 4
            while (p <= end) {
                if ((buf.getInt(p.toInt()).toLong() and 0xFFFFFFFFL) == n &&
                    (buf.getInt((p + 2L * w).toInt()).toLong() and 0xFFFFFFFFL) == n
                ) {
                    val q = p - segOff[s] + segVa[s]
                    val reg = q - 10L * w
                    if (reg >= 0 && fileOf(reg) >= 0) {
                        val typesCount = u32(reg + 6L * w)
                        val typesPtr = readPtr(reg + 7L * w)
                        val fo = readPtr(q + w)
                        val ts = readPtr(q + 3L * w)
                        if (typesCount >= n && typesCount < 20_000_000L && typesPtr > 0 && fileOf(typesPtr) >= 0 &&
                            fo > 0 && fileOf(fo) >= 0 && ts > 0 && fileOf(ts) >= 0
                        ) {
                            val first = readPtr(typesPtr)
                            if (first > 0 && fileOf(first) >= 0) return reg
                        }
                    }
                }
                p += w
            }
        }
        return -1
    }
}

/**
 * Reads global-metadata.dat and libil2cpp.so and produces every class/method with its address and types.
 * Struct sizes are derived from the data itself (and anchored from the end of each struct), so the
 * layout differences between metadata versions 24 to 31 do not matter. Version 39 (Unity 6) uses variable-width
 * indices; their widths are derived from the record sizes in the header.
 */
class Il2CppAnalyzer(private val metaFile: File, private val soFile: File) : Closeable {
    private val metaRaf = RandomAccessFile(metaFile, "r")
    private val soRaf = RandomAccessFile(soFile, "r")
    private val meta: ByteBuffer
    private val so: ByteBuffer

    val diag = StringBuilder()
    var version = 0
        private set
    var arch = ""
        private set

    private fun d(s: String) {
        diag.append(s).append('\n')
    }

    init {
        if (metaFile.length() < 512) throw DumpError("The metadata file is too small")
        if (soFile.length() < 2048) throw DumpError("The library file is too small")
        meta = metaRaf.channel.map(FileChannel.MapMode.READ_ONLY, 0, metaFile.length()).order(ByteOrder.LITTLE_ENDIAN)
        so = soRaf.channel.map(FileChannel.MapMode.READ_ONLY, 0, soFile.length()).order(ByteOrder.LITTLE_ENDIAN)
    }

    override fun close() {
        try {
            metaRaf.close()
        } catch (_: Exception) {
        }
        try {
            soRaf.close()
        } catch (_: Exception) {
        }
    }

    // ---- metadata tables
    private var strOff = 0
    private var imgCount = 0
    private var imgName = IntArray(0)
    private var imgTypeStart = IntArray(0)
    private var imgTypeCount = IntArray(0)
    private var imgMethods = IntArray(0)
    private var tdCount = 0
    private var tdOff = 0
    private var tdSize = 0
    private var tdName = IntArray(0)
    private var tdNs = IntArray(0)
    private var tdDecl = IntArray(0)
    private var tdMethodStart = IntArray(0)
    private var tdMethodCount = IntArray(0)
    private var methodsOff = 0
    private var mSize = 0
    private var paramsOff = 0
    private var v39 = false
    private var pSize = 12      // parameter record size
    private var pTypeW = 4      // width of the parameter type index
    private var mDeclW = 4      // width of the method's declaring type index
    private var mRetW = 4       // width of the method's return type index
    private var mGenW = 4       // width of the method's generic container index
    private var target = BooleanArray(0)
    private var clsCache = arrayOfNulls<String>(0)

    // ---- native side
    private lateinit var elf: ElfView
    private var typesPtr = -1L
    private var typesCount = 0
    private val typeCache = HashMap<Int, String>()
    private var modPtr = LongArray(0)
    private var modCount = IntArray(0)

    var totalMethods = 0
        private set
    var resolvedMethods = 0
        private set

    // versions 24..31: (offset, size) pairs; version 39: (offset, size, count) triples
    private fun hOff(i: Int) = if (v39) meta.getInt(8 + 12 * i) else meta.getInt(8 + 8 * i)
    private fun hSize(i: Int) = if (v39) meta.getInt(12 + 12 * i) else meta.getInt(12 + 8 * i)
    private fun hCount(i: Int) = if (v39) meta.getInt(16 + 12 * i) else 0

    /** Index of 1, 2 or 4 bytes; all ones means "none" (-1). */
    private fun readIdx(pos: Int, w: Int): Int = when (w) {
        1 -> {
            val v = meta.get(pos).toInt() and 0xFF
            if (v == 0xFF) -1 else v
        }
        2 -> {
            val v = meta.getShort(pos).toInt() and 0xFFFF
            if (v == 0xFFFF) -1 else v
        }
        else -> meta.getInt(pos)
    }

    private fun hexAt(pos: Int, len: Int): String {
        val sb = StringBuilder()
        for (i in 0 until len) {
            if (pos + i >= meta.limit()) break
            sb.append("%02X ".format(meta.get(pos + i)))
        }
        return sb.toString().trim()
    }

    private fun str(idx: Int): String {
        if (idx < 0) return ""
        val start = strOff + idx
        if (start >= meta.limit()) return ""
        var p = start
        while (p < meta.limit() && meta.get(p).toInt() != 0) p++
        val n = p - start
        if (n <= 0) return ""
        val b = ByteArray(n)
        for (i in 0 until n) b[i] = meta.get(start + i)
        return String(b, Charsets.UTF_8)
    }

    private fun isSystemImage(n: String): Boolean {
        val l = n.lowercase()
        return l.startsWith("mscorlib") || l.startsWith("system") || l.startsWith("unityengine") ||
            l.startsWith("unity.") || l.startsWith("mono.") || l.startsWith("netstandard") ||
            l.startsWith("microsoft.") || l.startsWith("i18n") || l.startsWith("newtonsoft") ||
            l.startsWith("icsharpcode")
    }

    // ------------------------------------------------------------------ analysis

    fun analyze(includeAll: Boolean, progress: (String) -> Unit) {
        progress("Reading metadata...")
        parseMetadata()

        target = BooleanArray(imgCount) { includeAll || !isSystemImage(str(imgName[it])) }
        val names = ArrayList<String>()
        for (i in 0 until imgCount) if (target[i]) names.add(str(imgName[i]))
        d("Assemblies analysed: ${names.size} of $imgCount" + if (names.size <= 8) " (" + names.joinToString(", ") + ")" else "")

        progress("Reading library...")
        val info = ElfInfo.parse(soFile) ?: throw DumpError("The opened file is not a valid ELF library (.so)")
        arch = info.archName()
        elf = ElfView(so, info)
        d("Library: $arch, ${info.segments.size} load segments")

        progress("Applying relocations...")
        d("Relocations: " + elf.loadRelocations())

        progress("Finding type table...")
        val reg = elf.findMetadataRegistration(tdCount)
        if (reg >= 0) {
            typesCount = elf.u32(reg + 6L * elf.w).toInt()
            typesPtr = elf.readPtr(reg + 7L * elf.w)
            d("Type table: %d entries at 0x%X".format(typesCount, typesPtr))
        } else {
            d("Type table NOT found: return and parameter types are unknown")
        }

        progress("Finding method addresses...")
        locateModules()

        totalMethods = 0
        resolvedMethods = 0
        for (i in 0 until imgCount) {
            if (!target[i]) continue
            for (t in imgTypeStart[i] until imgTypeStart[i] + imgTypeCount[i]) totalMethods += tdMethodCount[t]
        }
        forEachMethod { if (it.rva > 0) resolvedMethods++ }
        d("Methods: $totalMethods, with code address: $resolvedMethods")
        if (resolvedMethods == 0) {
            throw DumpError(
                "Could not match any method to its code address.\n" +
                    "Possible reasons: the .so and the metadata come from different game versions, " +
                    "the game uses Unity older than 2019 (not supported yet), or the library is protected."
            )
        }
    }

    private fun parseMetadata() {
        val sanity = meta.getInt(0)
        if (sanity != 0xFAB11BAF.toInt()) {
            throw DumpError(
                "This is not a standard Unity metadata file (header 0x%08X). It may be encrypted or protected."
                    .format(sanity)
            )
        }
        version = meta.getInt(4)
        d("Metadata version: $version")
        if (!(version in 24..31 || version == 39)) {
            throw DumpError("Metadata version $version is not supported (24 to 31 and 39 work)")
        }
        v39 = version == 39

        val fileLen = meta.limit().toLong()
        // Reject malformed table descriptors early. Only tables used by this parser are checked,
        // so newer/unknown tables in otherwise supported files remain untouched.
        val headerEntries = intArrayOf(2, 5, 10, 19, 20, 21)
        val headerEnd = 8L + (headerEntries.maxOrNull()!! + 1L) * if (v39) 12L else 8L
        if (headerEnd > fileLen) throw DumpError("Metadata header is truncated")
        for (idx in headerEntries) {
            val off = hOff(idx).toLong()
            val size = hSize(idx).toLong()
            if (off < 0 || size < 0 || (size > 0 && (off > fileLen || size > fileLen - off))) {
                throw DumpError("Metadata table [$idx] has an invalid offset or size")
            }
            if (v39) {
                val count = hCount(idx).toLong()
                if (count < 0 || (size > 0 && count > size)) {
                    throw DumpError("Metadata table [$idx] has an invalid count")
                }
            }
        }
        if (v39) {
            val sb = StringBuilder("Header tables (offset/size/count):")
            for (i in intArrayOf(0, 2, 5, 10, 14, 19, 20)) sb.append("  [$i]=${hOff(i)}/${hSize(i)}/${hCount(i)}")
            d(sb.toString())
            if (hOff(0) != 380) d("Note: the first table starts at ${hOff(0)}, version 39 files normally start at 380")
        }
        strOff = hOff(2)
        if (strOff <= 0 || strOff >= fileLen) throw DumpError("Bad string table offset in the metadata header")

        // images table: directly after typeDefinitions (index 20), or after rgctxEntries (index 21) in old files
        var imagesIdx = -1
        var imgRec = 40
        for (cand in (if (v39) intArrayOf(20) else intArrayOf(20, 21))) {
            val off = hOff(cand)
            val size = hSize(cand)
            if (off <= 0 || size <= 0 || off.toLong() + size > fileLen) continue
            var rec = 40
            if (v39) {
                val c = hCount(cand)
                if (c <= 0 || size % c != 0) continue
                rec = size / c
                if (rec < 32 || rec > 48) continue
            } else if (size % 40 != 0) continue
            val nm = str(meta.getInt(off))
            if (nm.endsWith(".dll") || nm.endsWith(".exe")) {
                imagesIdx = cand
                imgRec = rec
                break
            }
        }
        if (imagesIdx < 0) throw DumpError("Could not find the assembly table in the metadata")
        val imgOff = hOff(imagesIdx)
        imgCount = if (v39) hCount(imagesIdx) else hSize(imagesIdx) / 40

        // index widths: fixed 4 bytes in versions 24..31, derived from the record sizes in version 39
        var dW = 4   // type definition index
        var tW = 4   // type index
        var gW = 4   // generic container index
        var tdBytes = hSize(19)
        tdOff = hOff(19)
        var tdSizeV39 = 0
        if (v39) {
            val tc = hCount(19)
            val mc = hCount(5)
            val pc = hCount(10)
            if (tc <= 0 || mc <= 0 || pc <= 0 || tdBytes % tc != 0 || hSize(5) % mc != 0 || hSize(10) % pc != 0) {
                throw DumpError("Version 39 header counts do not match the table sizes")
            }
            tdSizeV39 = tdBytes / tc
            mSize = hSize(5) / mc
            pSize = hSize(10) / pc
            dW = (imgRec - 32) / 2
            tW = pSize - 8
            gW = tdSizeV39 - 68 - 3 * tW
            val widthsOk = dW in intArrayOf(1, 2, 4) && tW in intArrayOf(1, 2, 4) && gW in intArrayOf(1, 2, 4) &&
                (imgRec - 32) % 2 == 0 && mSize == 24 + dW + tW + gW
            d(
                "Record sizes: type $tdSizeV39, method $mSize, image $imgRec, parameter $pSize -> " +
                    "index widths: type $tW, type definition $dW, generic container $gW"
            )
            if (!widthsOk) {
                throw DumpError(
                    "Unsupported version 39 layout: record sizes (type $tdSizeV39, method $mSize, image $imgRec, " +
                        "parameter $pSize) do not fit the known structure.\n" +
                        "First type record: " + hexAt(tdOff, tdSizeV39) + "\n" +
                        "First method record: " + hexAt(hOff(5), mSize) + "\n" +
                        "First image record: " + hexAt(imgOff, imgRec) + "\n" +
                        "Please send this report so support can be adjusted."
                )
            }
        } else {
            pSize = 12
        }
        mDeclW = dW
        mRetW = tW
        mGenW = gW
        pTypeW = tW

        imgName = IntArray(imgCount)
        imgTypeStart = IntArray(imgCount)
        imgTypeCount = IntArray(imgCount)
        var totalTypes = 0
        for (i in 0 until imgCount) {
            val b = imgOff + i * imgRec
            imgName[i] = meta.getInt(b)
            imgTypeStart[i] = readIdx(b + 8, dW)
            imgTypeCount[i] = meta.getInt(b + 8 + dW)
            totalTypes = maxOf(totalTypes, imgTypeStart[i] + imgTypeCount[i])
        }
        d("Assemblies: $imgCount, type definitions: $totalTypes")

        if (totalTypes <= 0) throw DumpError("The assembly table is empty")
        if (imgCount <= 0 || imgCount > 100_000 || totalTypes > 5_000_000) {
            throw DumpError("Assembly/type counts are outside safe parser limits")
        }
        for (i in 0 until imgCount) {
            val start = imgTypeStart[i]
            val count = imgTypeCount[i]
            if (start < 0 || count < 0 || start > totalTypes || count > totalTypes - start) {
                throw DumpError("Assembly table contains an invalid type range at entry $i")
            }
        }
        if (v39) {
            tdCount = hCount(19)
            tdSize = tdSizeV39
            if (totalTypes > tdCount) throw DumpError("Assembly table refers to more types than the type table holds")
        } else {
            if (tdBytes % totalTypes != 0) throw DumpError("Type table size does not match the assembly table")
            tdCount = totalTypes
            tdSize = tdBytes / totalTypes
        }
        if (tdSize < 70 || tdSize > 128 || tdOff.toLong() + tdBytes > fileLen) {
            throw DumpError("Unexpected type definition size $tdSize")
        }
        tdName = IntArray(tdCount)
        tdNs = IntArray(tdCount)
        tdDecl = IntArray(tdCount)
        tdMethodStart = IntArray(tdCount)
        tdMethodCount = IntArray(tdCount)
        var totalMethodsMeta = 0
        for (t in 0 until tdCount) {
            val b = tdOff + t * tdSize
            tdName[t] = meta.getInt(b)
            tdNs[t] = meta.getInt(b + 4)
            // declaring type: found from the end of the record before version 39, right after byvalTypeIndex in 39
            tdDecl[t] = if (v39) readIdx(b + 8 + tW, tW) else meta.getInt(b + tdSize - 76)
            tdMethodStart[t] = meta.getInt(b + tdSize - 52)
            tdMethodCount[t] = meta.getShort(b + tdSize - 24).toInt() and 0xFFFF
            totalMethodsMeta = maxOf(totalMethodsMeta, tdMethodStart[t] + tdMethodCount[t])
        }
        val first = str(tdName[0])
        d("Type definition size: $tdSize bytes, first type: \"$first\"")
        if (first != "<Module>") {
            throw DumpError(
                "Type definitions look wrong (first type is \"$first\"), unsupported layout\n" +
                    "First type record: " + hexAt(tdOff, tdSize)
            )
        }
        clsCache = arrayOfNulls(tdCount)

        methodsOff = hOff(5)
        val mBytes = hSize(5)
        if (!v39) {
            if (totalMethodsMeta <= 0 || mBytes % totalMethodsMeta != 0) {
                throw DumpError("Method table size does not match the type table")
            }
            mSize = mBytes / totalMethodsMeta
        } else if (totalMethodsMeta > hCount(5)) {
            throw DumpError("Type table refers to more methods than the method table holds")
        }
        if (mSize < 28 || mSize > 48 || methodsOff.toLong() + mBytes > fileLen) throw DumpError("Unexpected method definition size $mSize")
        paramsOff = hOff(10)
        val paramBytes = hSize(10).toLong()
        if (paramsOff < 0 || paramBytes < 0 || paramsOff.toLong() > fileLen || paramBytes > fileLen - paramsOff) {
            throw DumpError("Parameter table is outside the metadata file")
        }
        if (pSize <= 0 || paramBytes % pSize != 0) throw DumpError("Parameter table record size is inconsistent")
        for (t in 0 until tdCount) {
            val start = tdMethodStart[t]
            val count = tdMethodCount[t]
            if (start < 0 || count < 0 || start > totalMethodsMeta || count > totalMethodsMeta - start) {
                throw DumpError("Type definition $t contains an invalid method range")
            }
        }
        d("Method definition size: $mSize bytes, methods in metadata: $totalMethodsMeta")

        // layout self-check: every method must point back to the type that owns it
        var checked = 0
        for (t in 0 until tdCount) {
            if (tdMethodCount[t] == 0) continue
            val mb = methodsOff + tdMethodStart[t] * mSize
            if (readIdx(mb + 4, mDeclW) != t) {
                throw DumpError(
                    "Method table layout check failed, unsupported metadata layout\n" +
                        "Type $t record: " + hexAt(tdOff + t * tdSize, tdSize) + "\n" +
                        "Method record: " + hexAt(mb, mSize)
                )
            }
            if (++checked >= 40) break
        }

        imgMethods = IntArray(imgCount)
        for (i in 0 until imgCount) {
            var c = 0
            for (t in imgTypeStart[i] until imgTypeStart[i] + imgTypeCount[i]) c += tdMethodCount[t]
            imgMethods[i] = c
        }
    }

    private fun scanDllStrings(): HashMap<String, ArrayList<Long>> {
        val res = HashMap<String, ArrayList<Long>>()
        val lim = so.limit()
        val dot = '.'.code.toByte()
        val dd = 'd'.code.toByte()
        val ll = 'l'.code.toByte()
        val zero = 0.toByte()
        var i = 0
        while (i + 5 <= lim) {
            if (so.get(i) == dot && so.get(i + 1) == dd && so.get(i + 2) == ll && so.get(i + 3) == ll && so.get(i + 4) == zero) {
                var s = i
                while (s > 0 && i - s < 200) {
                    val c = so.get(s - 1).toInt()
                    if (c < 32 || c > 126) break
                    s--
                }
                if (s < i) {
                    val len = i + 4 - s
                    val b = ByteArray(len)
                    for (k in 0 until len) b[k] = so.get(s + k)
                    val v = elf.vaddrOf(s.toLong())
                    if (v >= 0) res.getOrPut(String(b, Charsets.US_ASCII)) { ArrayList() }.add(v)
                }
            }
            i++
        }
        return res
    }

    /** Il2CppCodeGenModule: { const char* name; uint32 methodPointerCount; const Il2CppMethodPointer* methodPointers; ... } */
    private fun locateModules() {
        modPtr = LongArray(imgCount) { -1L }
        modCount = IntArray(imgCount)
        val dll = scanDllStrings()
        d("Found ${dll.size} assembly name strings in the library")
        var found = 0
        for (i in 0 until imgCount) {
            if (!target[i]) continue
            val nm = str(imgName[i])
            val addrs = dll[nm]
            if (addrs == null) {
                d("  $nm: name not found in the library")
                continue
            }
            var best = -1L
            var bestCnt = 0
            var exact = false
            loop@ for (sv in addrs) {
                for (m in elf.findPointersTo(sv)) {
                    val cnt = elf.u32(m + elf.w)
                    val mp = elf.readPtr(m + 2L * elf.w)
                    if (cnt < 1 || cnt > 5_000_000L || mp <= 0 || elf.fileOf(mp) < 0) continue
                    if (cnt == imgMethods[i].toLong()) {
                        best = mp
                        bestCnt = cnt.toInt()
                        exact = true
                        break@loop
                    }
                    if (best < 0) {
                        best = mp
                        bestCnt = cnt.toInt()
                    }
                }
            }
            if (best < 0) {
                d("  $nm: no code module found")
                continue
            }
            modPtr[i] = best
            modCount[i] = bestCnt
            found++
            d("  $nm: ${if (exact) "ok" else "count differs"} ($bestCnt code entries, ${imgMethods[i]} methods)")
        }
        if (found == 0) {
            throw DumpError(
                "Could not find the code tables in the library.\n" +
                    "The game probably uses Unity older than 2019 (metadata 24.0/24.1), which is not supported yet, " +
                    "or the library is protected."
            )
        }
    }

    // ------------------------------------------------------------------ names and types

    private fun rootOf(t: Int): Int {
        var cur = t
        var guard = 0
        while (tdDecl[cur] in 0 until tdCount && tdDecl[cur] != cur && guard < 8) {
            cur = tdDecl[cur]
            guard++
        }
        return cur
    }

    private fun className(t: Int): String {
        if (t < 0 || t >= tdCount) return "?"
        val c = clsCache[t]
        if (c != null) return c
        val sb = StringBuilder(str(tdName[t]))
        var cur = t
        var guard = 0
        while (tdDecl[cur] in 0 until tdCount && tdDecl[cur] != cur && guard < 8) {
            cur = tdDecl[cur]
            sb.insert(0, str(tdName[cur]) + ".")
            guard++
        }
        val s = sb.toString()
        clsCache[t] = s
        return s
    }

    private fun typeName(idx: Int): String {
        if (typesPtr < 0 || idx < 0 || idx >= typesCount) return "?"
        val c = typeCache[idx]
        if (c != null) return c
        val a = elf.readPtr(typesPtr + idx.toLong() * elf.w)
        val s = if (a <= 0) "?" else typeAt(a, 0)
        typeCache[idx] = s
        return s
    }

    private fun typeAt(a: Long, depth: Int): String {
        if (depth > 6) return "?"
        val data = elf.readPtr(a)
        val bits = elf.u32(a + elf.w)
        if (bits < 0) return "?"
        val t = ((bits ushr 16) and 0xFF).toInt()
        return when (t) {
            1 -> "void"
            2 -> "bool"
            3 -> "char"
            4 -> "sbyte"
            5 -> "byte"
            6 -> "short"
            7 -> "ushort"
            8 -> "int"
            9 -> "uint"
            10 -> "long"
            11 -> "ulong"
            12 -> "float"
            13 -> "double"
            14 -> "string"
            15 -> if (data > 0) typeAt(data, depth + 1) + "*" else "ptr"
            16 -> if (data > 0) typeAt(data, depth + 1) + "&" else "ref"
            17, 18 -> className(data.toInt())
            19, 30 -> "T"
            20 -> {
                val e = if (data > 0) elf.readPtr(data) else -1L
                if (e > 0) typeAt(e, depth + 1) + "[]" else "array"
            }
            21 -> genericName(data, depth)
            22 -> "TypedReference"
            24 -> "IntPtr"
            25 -> "UIntPtr"
            27 -> "FnPtr"
            28 -> "object"
            29 -> if (data > 0) typeAt(data, depth + 1) + "[]" else "array"
            else -> "type$t"
        }
    }

    private fun genericName(gc: Long, depth: Int): String {
        if (gc <= 0) return "Generic<>"
        val gi = elf.u32(gc).toInt()
        fun viaTypeIndex(): String? {
            if (typesPtr < 0 || gi < 0 || gi >= typesCount) return null
            val ea = elf.readPtr(typesPtr + gi.toLong() * elf.w)
            if (ea <= 0) return null
            val bits = elf.u32(ea + elf.w)
            val t = ((bits ushr 16) and 0xFF).toInt()
            if (t != 17 && t != 18) return null
            return className(elf.readPtr(ea).toInt())
        }
        val base = if (version >= 27) {
            viaTypeIndex() ?: (if (gi in 0 until tdCount) className(gi) else null)
        } else {
            (if (gi in 0 until tdCount) className(gi) else null) ?: viaTypeIndex()
        }
        return (base ?: "Generic") + "<>"
    }

    // ------------------------------------------------------------------ iteration

    private fun buildRow(img: Int, t: Int, m: Int): MethodRow {
        val b = methodsOff + m * mSize
        val nameIdx = meta.getInt(b)
        val retIdx = readIdx(b + 4 + mDeclW, mRetW)
        val pStart = meta.getInt(b + mSize - 16 - mGenW)
        val token = meta.getInt(b + mSize - 12).toLong() and 0xFFFFFFFFL
        val flags = meta.getShort(b + mSize - 8).toInt() and 0xFFFF
        val pCount = meta.getShort(b + mSize - 2).toInt() and 0xFFFF

        var rva = -1L
        val mp = modPtr[img]
        if (mp > 0) {
            val idx = (token and 0xFFFFFFL).toInt() - 1
            if (idx >= 0 && idx < modCount[img]) {
                var fn = elf.readPtr(mp + idx.toLong() * elf.w)
                if (!elf.is64) fn = fn and 1L.inv()
                if (fn > 0) rva = fn
            }
        }
        val fo = if (rva > 0) elf.fileOf(rva).toLong() else -1L

        val pt = ArrayList<String>()
        val pn = ArrayList<String>()
        if (pCount in 1..64 && pStart >= 0) {
            for (p in 0 until pCount) {
                val pb = paramsOff + (pStart + p) * pSize
                if (pb + pSize > meta.limit()) break
                pn.add(str(meta.getInt(pb)))
                pt.add(typeName(readIdx(pb + 8, pTypeW)))
            }
        }
        val root = rootOf(t)
        return MethodRow(
            str(imgName[img]), str(tdNs[root]), className(t), str(nameIdx),
            rva, fo, typeName(retIdx), pt, pn, (flags and 0x10) != 0, token
        )
    }

    fun forEachMethod(f: (MethodRow) -> Unit) {
        for (i in 0 until imgCount) {
            if (!target[i]) continue
            for (t in imgTypeStart[i] until imgTypeStart[i] + imgTypeCount[i]) {
                if (t < 0 || t >= tdCount) continue
                for (k in 0 until tdMethodCount[t]) f(buildRow(i, t, tdMethodStart[t] + k))
            }
        }
    }

    fun suggestions(): List<Suggestion> {
        val all = ArrayList<Suggestion>()
        forEachMethod { r -> all.addAll(Hints.classify(r)) }
        return Hints.rank(all)
    }

    // ------------------------------------------------------------------ JSON

    private fun q(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c < ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private fun hex(v: Long): String = if (v < 0) "null" else "\"0x%X\"".format(v)

    fun writeJson(w: Writer, sugg: List<Suggestion>) {
        w.write("{\n")
        w.write("  \"tool\": \"Il2Patch\",\n")
        w.write("  \"metadataVersion\": $version,\n")
        w.write("  \"arch\": ${q(arch)},\n")
        w.write("  \"note\": \"rva = address to use in Il2Patch (RVA option on). offset = position in the .so file.\",\n")
        w.write("  \"images\": [\n")
        var firstImg = true
        for (i in 0 until imgCount) {
            if (!target[i]) continue
            if (!firstImg) w.write(",\n")
            firstImg = false
            w.write("    {\"name\": ${q(str(imgName[i]))}, \"types\": [\n")
            var firstT = true
            for (t in imgTypeStart[i] until imgTypeStart[i] + imgTypeCount[i]) {
                if (t < 0 || t >= tdCount || tdMethodCount[t] == 0) continue
                if (!firstT) w.write(",\n")
                firstT = false
                val root = rootOf(t)
                w.write("      {\"namespace\": ${q(str(tdNs[root]))}, \"name\": ${q(className(t))}, \"methods\": [\n")
                for (k in 0 until tdMethodCount[t]) {
                    val r = buildRow(i, t, tdMethodStart[t] + k)
                    if (k > 0) w.write(",\n")
                    w.write("        {\"name\": ${q(r.name)}, \"rva\": ${hex(r.rva)}, \"offset\": ${hex(r.fileOff)}, ")
                    w.write("\"returns\": ${q(r.ret)}, \"static\": ${r.isStatic}, \"token\": ${hex(r.token)}, \"params\": [")
                    for (p in r.paramTypes.indices) {
                        if (p > 0) w.write(", ")
                        w.write("{\"type\": ${q(r.paramTypes[p])}, \"name\": ${q(r.paramNames.getOrElse(p) { "" })}}")
                    }
                    w.write("]}")
                }
                w.write("\n      ]}")
            }
            w.write("\n    ]}")
        }
        w.write("\n  ],\n")
        writeSuggestionsBody(w, sugg)
        w.write("}\n")
        w.flush()
    }

    /** Only the suggestions (no class / method dump). */
    fun writeSuggestionsJson(w: Writer, sugg: List<Suggestion>) {
        w.write("{\n")
        w.write("  \"tool\": \"Il2Patch\",\n")
        w.write("  \"metadataVersion\": $version,\n")
        w.write("  \"arch\": ${q(arch)},\n")
        w.write("  \"note\": \"rva = address to use in Il2Patch (RVA option on). Guesses from names: verify before use.\",\n")
        writeSuggestionsBody(w, sugg)
        w.write("}\n")
        w.flush()
    }

    private fun writeSuggestionsBody(w: Writer, sugg: List<Suggestion>) {
        w.write("  \"suggestions\": [\n")
        for (n in sugg.indices) {
            val s = sugg[n]
            if (n > 0) w.write(",\n")
            w.write(
                "    {\"category\": ${q(s.category)}, \"class\": ${q(s.cls)}, \"method\": ${q(s.method)}, " +
                    "\"rva\": ${hex(s.rva)}, \"offset\": ${hex(s.fileOff)}, \"returns\": ${q(s.ret)}, " +
                    "\"params\": ${q(s.params)}, \"patchType\": ${q(s.kind)}, \"value\": ${q(s.value)}, " +
                    "\"batch\": ${q(s.batchLine())}, \"note\": ${q(s.note)}}"
            )
        }
        w.write("\n  ]\n")
    }
}
