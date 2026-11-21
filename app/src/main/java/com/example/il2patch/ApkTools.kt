package com.example.il2patch

import android.content.Context
import com.android.apksig.ApkSigner
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipExtraField
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.zip.CRC32
import java.util.zip.ZipEntry

object ApkTools {
    private const val KS_ASSET = "il2patch.p12"
    private const val KS_ALIAS = "il2patch"
    private val KS_PASS = "il2patch".toCharArray()

    /** Native libraries and il2cpp metadata inside an APK, il2cpp files first. */
    fun listCandidates(apk: File): List<String> {
        java.util.zip.ZipFile(apk).use { z ->
            val names = z.entries().toList()
                .map { it.name }
                .filter { !it.endsWith("/") && (it.endsWith(".so") || it.endsWith("global-metadata.dat")) }
            return names.sortedWith(
                compareByDescending<String> { it.contains("il2cpp") || it.contains("metadata") }.thenBy { it }
            )
        }
    }

    /**
     * Copies every entry of [inApk] unchanged (compressed data is copied raw, not recompressed),
     * replaces [entryName] with [newData], keeps stored entries aligned (4096 for .so),
     * then signs the result with v1+v2+v3 using the bundled key.
     */
    fun buildSignedApk(
        context: Context,
        inApk: File,
        entryName: String,
        newData: File,
        outApk: File,
        progress: (String) -> Unit
    ) {
        val unsigned = File(context.cacheDir, "unsigned.apk")
        unsigned.delete()
        outApk.delete()

        progress("Computing checksum...")
        val crc = CRC32()
        newData.inputStream().use { i ->
            val b = ByteArray(1 shl 16)
            while (true) {
                val n = i.read(b)
                if (n < 0) break
                crc.update(b, 0, n)
            }
        }

        progress("Repacking APK...")
        var replacementWritten = false
        try {
        ZipFile(inApk).use { zf ->
            ZipArchiveOutputStream(unsigned).use { zos ->
                for (e in Collections.list(zf.entries)) {
                    val name = e.name
                    val isOldSignature = name.startsWith("META-INF/") &&
                        (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA") ||
                            name.endsWith(".EC") || name == "META-INF/MANIFEST.MF")
                    if (isOldSignature) continue

                    if (name == entryName) {
                        if (replacementWritten) throw IllegalStateException("Duplicate APK entry: $entryName")
                        replacementWritten = true
                        val ne = ZipArchiveEntry(name)
                        ne.method = ZipEntry.STORED
                        ne.size = newData.length()
                        ne.crc = crc.value
                        ne.setAlignment(if (name.endsWith(".so")) 4096 else 4)
                        zos.putArchiveEntry(ne)
                        newData.inputStream().use { it.copyTo(zos) }
                        zos.closeArchiveEntry()
                    } else {
                        val ne = ZipArchiveEntry(e)
                        ne.setExtraFields(arrayOf<ZipExtraField>())
                        if (ne.method == ZipEntry.STORED && !name.endsWith("/")) {
                            ne.setAlignment(if (name.endsWith(".so")) 4096 else 4)
                        }
                        zf.getRawInputStream(e).use { zos.addRawArchiveEntry(ne, it) }
                    }
                }
            }
        }
        if (!replacementWritten) throw IllegalArgumentException("APK entry not found: $entryName")

        progress("Signing APK...")
        val ks = KeyStore.getInstance("PKCS12")
        context.assets.open(KS_ASSET).use { ks.load(it, KS_PASS) }
        val key = ks.getKey(KS_ALIAS, KS_PASS) as PrivateKey
        val cert = ks.getCertificate(KS_ALIAS) as X509Certificate
        val signer = ApkSigner.SignerConfig.Builder("il2patch", key, listOf(cert)).build()
        ApkSigner.Builder(listOf(signer))
            .setInputApk(unsigned)
            .setOutputApk(outApk)
            .setCreatedBy("Il2Patch")
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setV4SigningEnabled(false)
            .build()
            .sign()
        } finally {
            unsigned.delete()
        }
    }
}
