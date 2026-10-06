package com.emberdeep.game.core

import com.emberdeep.game.testutil.A
import org.junit.Test

class CodecTest {

    @Test
    fun `matches the RFC 4648 reference vectors`() {
        A.eq("", Codec.encodeBase64("".toByteArray()), "empty")
        A.eq("Zg==", Codec.encodeBase64("f".toByteArray()), "f")
        A.eq("Zm8=", Codec.encodeBase64("fo".toByteArray()), "fo")
        A.eq("Zm9v", Codec.encodeBase64("foo".toByteArray()), "foo")
        A.eq("Zm9vYg==", Codec.encodeBase64("foob".toByteArray()), "foob")
        A.eq("Zm9vYmE=", Codec.encodeBase64("fooba".toByteArray()), "fooba")
        A.eq("Zm9vYmFy", Codec.encodeBase64("foobar".toByteArray()), "foobar")
    }

    @Test
    fun `decodes the reference vectors`() {
        A.eq("f", String(Codec.decodeBase64("Zg==")!!), "f")
        A.eq("foobar", String(Codec.decodeBase64("Zm9vYmFy")!!), "foobar")
        A.eq(0, Codec.decodeBase64("")!!.size, "empty")
        // Unpadded input is accepted (defensive: some writers omit padding).
        A.eq("foob", String(Codec.decodeBase64("Zm9vYg")!!), "unpadded")
        // Whitespace is ignored.
        A.eq("foobar", String(Codec.decodeBase64("Zm9v\nYmFy")!!), "wrapped")
    }

    @Test
    fun `round trips every length up to 300 bytes`() {
        var state = 0x1234567L
        val source = ByteArray(300)
        for (i in source.indices) {
            state = state * 6364136223846793005L + 1442695040888963407L
            source[i] = ((state ushr 33) and 0xFF).toByte()
        }
        for (length in 0..source.size) {
            val slice = source.copyOf(length)
            val encoded = Codec.encodeBase64(slice)
            val decoded = Codec.decodeBase64(encoded)
            A.notNull(decoded, "decode of length $length")
            A.eq(length, decoded!!.size, "decoded size for length $length")
            A.isTrue(slice.contentEquals(decoded), "round trip mismatch at length $length")
        }
    }

    @Test
    fun `rejects malformed input instead of throwing`() {
        A.isTrue(Codec.decodeBase64("!!!!") == null, "invalid characters must fail")
        A.isTrue(Codec.decodeBase64("Z") == null, "a single sextet is not a byte")
        A.isTrue(Codec.decodeBase64("Zm9v\u00e9") == null, "non-ascii must fail")
        A.isTrue(Codec.decodeBase64(null) == null, "null input")
    }
}
