package com.emberdeep.game.core

/**
 * Minimal, dependency-free Base64 (RFC 4648: standard alphabet, '=' padding).
 *
 * The game deliberately avoids `android.util.Base64` here so that the whole
 * model layer stays pure JVM: it can be unit-tested and simulated headlessly,
 * and a corrupt save degrades into a `null` instead of throwing.
 *
 * The output is byte-for-byte identical to `android.util.Base64.NO_WRAP`, so
 * save files written by earlier versions remain readable.
 */
object Codec {

    private const val ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private val REVERSE = ByteArray(128).also { table ->
        java.util.Arrays.fill(table, (-1).toByte())
        for (i in ALPHABET.indices) table[ALPHABET[i].code] = i.toByte()
    }

    fun encodeBase64(src: ByteArray): String {
        if (src.isEmpty()) return ""
        val out = StringBuilder((src.size + 2) / 3 * 4)
        var i = 0
        while (i + 2 < src.size) {
            val n = ((src[i].toInt() and 0xFF) shl 16) or
                ((src[i + 1].toInt() and 0xFF) shl 8) or
                (src[i + 2].toInt() and 0xFF)
            out.append(ALPHABET[(n ushr 18) and 0x3F])
            out.append(ALPHABET[(n ushr 12) and 0x3F])
            out.append(ALPHABET[(n ushr 6) and 0x3F])
            out.append(ALPHABET[n and 0x3F])
            i += 3
        }
        when (src.size - i) {
            1 -> {
                val n = (src[i].toInt() and 0xFF) shl 16
                out.append(ALPHABET[(n ushr 18) and 0x3F])
                out.append(ALPHABET[(n ushr 12) and 0x3F])
                out.append("==")
            }
            2 -> {
                val n = ((src[i].toInt() and 0xFF) shl 16) or
                    ((src[i + 1].toInt() and 0xFF) shl 8)
                out.append(ALPHABET[(n ushr 18) and 0x3F])
                out.append(ALPHABET[(n ushr 12) and 0x3F])
                out.append(ALPHABET[(n ushr 6) and 0x3F])
                out.append('=')
            }
        }
        return out.toString()
    }

    /**
     * Decodes Base64, ignoring whitespace and tolerating missing padding.
     *
     * @return the decoded bytes, or `null` when [src] is not valid Base64
     *   (callers treat that as "corrupt data"). `null` also decodes to `null`.
     */
    fun decodeBase64(src: String?): ByteArray? {
        if (src == null) return null
        if (src.isEmpty()) return ByteArray(0)
        val out = ByteArray(src.length / 4 * 3 + 3)
        var outLen = 0
        var acc = 0
        var bits = 0
        for (ch in src) {
            if (ch == '=') break
            if (ch == '\n' || ch == '\r' || ch == ' ' || ch == '\t') continue
            val v = if (ch.code < 128) REVERSE[ch.code].toInt() else -1
            if (v < 0) return null
            acc = (acc shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out[outLen++] = ((acc ushr bits) and 0xFF).toByte()
            }
        }
        // A lone trailing sextet cannot encode a whole byte: invalid input.
        if (bits >= 6) return null
        return out.copyOf(outLen)
    }
}
