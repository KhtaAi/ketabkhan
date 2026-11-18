package com.ketabkhan.app.font

import com.ketabkhan.build.VazirmatnFontValidator
import com.ketabkhan.build.VazirmatnFontValidator.FontValidationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * In-memory unit tests for [VazirmatnFontValidator].
 * Zero font files are ever written to disk.
 */
class VazirmatnFontValidatorTest {

    @Test
    fun `accepts valid TrueType 0x00010000 in-memory font with head cmap glyf tables`() {
        val fontBytes = buildInMemorySfntFont(
            signature = byteArrayOf(0x00, 0x01, 0x00, 0x00),
            tableTags = listOf("head", "cmap", "glyf", "maxp"),
            totalSize = VazirmatnFontValidator.MIN_FONT_SIZE_BYTES + 256,
        )

        val info = VazirmatnFontValidator.validateFontBytes(fontBytes, "vazirmatn_regular.ttf")

        assertEquals("0x00010000", info.signatureLabel)
        assertEquals(4, info.tableCount)
        assertTrue(info.tableTags.containsAll(listOf("head", "cmap", "glyf")))
    }

    @Test
    fun `accepts valid OpenType OTTO and Apple true signatures`() {
        val otfBytes = buildInMemorySfntFont(
            signature = "OTTO".toByteArray(StandardCharsets.US_ASCII),
            tableTags = listOf("head", "cmap", "CFF ", "name"),
            totalSize = VazirmatnFontValidator.MIN_FONT_SIZE_BYTES,
        )
        val otfInfo = VazirmatnFontValidator.validateFontBytes(otfBytes, "vazirmatn_medium.otf")
        assertEquals("OTTO", otfInfo.signatureLabel)

        val appleBytes = buildInMemorySfntFont(
            signature = "true".toByteArray(StandardCharsets.US_ASCII),
            tableTags = listOf("head", "cmap", "glyf", "loca"),
            totalSize = VazirmatnFontValidator.MIN_FONT_SIZE_BYTES,
        )
        val appleInfo = VazirmatnFontValidator.validateFontBytes(appleBytes, "vazirmatn_bold.ttf")
        assertEquals("true", appleInfo.signatureLabel)
    }

    @Test
    fun `accepts valid ttcf collection pointing to valid inner SFNT header`() {
        val innerSfnt = buildInMemorySfntFont(
            signature = byteArrayOf(0x00, 0x01, 0x00, 0x00),
            tableTags = listOf("head", "cmap", "glyf", "maxp"),
            totalSize = VazirmatnFontValidator.MIN_FONT_SIZE_BYTES,
        )
        // TTC v1 header = 16 bytes ("ttcf", version 0x00010000, numFonts=1, offset=16)
        val headerSize = 16
        val ttc = ByteArray(headerSize + innerSfnt.size)
        val buf = ByteBuffer.wrap(ttc).order(ByteOrder.BIG_ENDIAN)
        buf.put("ttcf".toByteArray(StandardCharsets.US_ASCII))
        buf.putInt(0x00010000)
        buf.putInt(1)
        buf.putInt(headerSize)
        System.arraycopy(innerSfnt, 0, ttc, headerSize, innerSfnt.size)

        // Shift inner table offsets by headerSize so offsets remain valid inside TTC container
        val numTables = ((ttc[headerSize + 4].toInt() and 0xFF) shl 8) or (ttc[headerSize + 5].toInt() and 0xFF)
        for (i in 0 until numTables) {
            val recPos = headerSize + 12 + i * 16
            val origOffset = ByteBuffer.wrap(ttc, recPos + 8, 4).order(ByteOrder.BIG_ENDIAN).int
            ByteBuffer.wrap(ttc, recPos + 8, 4).order(ByteOrder.BIG_ENDIAN).putInt(origOffset + headerSize)
        }

        val info = VazirmatnFontValidator.validateFontBytes(ttc, "vazirmatn_collection.ttc")
        assertEquals("ttcf", info.signatureLabel)
        assertEquals(4, info.tableCount)
    }

    @Test
    fun `strips BOM whitespace and newlines before decoding valid Base64`() {
        val fontBytes = buildInMemorySfntFont(
            signature = byteArrayOf(0x00, 0x01, 0x00, 0x00),
            tableTags = listOf("head", "cmap", "glyf", "maxp"),
            totalSize = VazirmatnFontValidator.MIN_FONT_SIZE_BYTES,
        )
        val rawB64 = Base64.getEncoder().encodeToString(fontBytes)
        val wrappedWithNoise = "\uFEFF  \n" + rawB64.chunked(64).joinToString("\r\n\t ") + "\n\u200B"

        val result = VazirmatnFontValidator.decodeAndValidate(wrappedWithNoise, "vazirmatn_regular.b64")
        assertEquals(fontBytes.size, result.bytes.size)
        assertEquals("0x00010000", result.info.signatureLabel)
    }

    @Test
    fun `rejects invalid Base64 payload with actionable Persian error naming file`() {
        val ex = assertFailsValidation {
            VazirmatnFontValidator.decodeAndValidate("not!valid@base64===", "vazirmatn_regular.b64")
        }
        assertTrue(ex.message!!.contains("vazirmatn_regular.b64"))
        assertTrue(ex.message!!.contains("Base64"))
    }

    @Test
    fun `rejects empty or undersized decoded font under 20KB`() {
        assertFailsValidation {
            VazirmatnFontValidator.validateFontBytes(ByteArray(0), "vazirmatn_regular.b64")
        }

        val tooSmall = buildInMemorySfntFont(
            signature = byteArrayOf(0x00, 0x01, 0x00, 0x00),
            tableTags = listOf("head", "cmap", "glyf", "maxp"),
            totalSize = VazirmatnFontValidator.MIN_FONT_SIZE_BYTES - 1,
        )
        val ex = assertFailsValidation {
            VazirmatnFontValidator.validateFontBytes(tooSmall, "vazirmatn_medium.b64")
        }
        assertTrue(ex.message!!.contains("vazirmatn_medium.b64"))
        assertTrue(ex.message!!.contains("۲۰ کیلوبایت"))
    }

    @Test
    fun `rejects invalid 4-byte signature`() {
        val invalidHeader = ByteArray(VazirmatnFontValidator.MIN_FONT_SIZE_BYTES)
        "HTML".toByteArray(StandardCharsets.US_ASCII).copyInto(invalidHeader, 0)

        val ex = assertFailsValidation {
            VazirmatnFontValidator.validateFontBytes(invalidHeader, "vazirmatn_bold.b64")
        }
        assertTrue(ex.message!!.contains("vazirmatn_bold.b64"))
        assertTrue(ex.message!!.contains("امضای"))
    }

    @Test
    fun `rejects unreasonable table count or missing required SFNT tables`() {
        val tooFewTables = buildInMemorySfntFont(
            signature = byteArrayOf(0x00, 0x01, 0x00, 0x00),
            tableTags = listOf("head", "cmap", "glyf"),
            totalSize = VazirmatnFontValidator.MIN_FONT_SIZE_BYTES,
        )
        val exCount = assertFailsValidation {
            VazirmatnFontValidator.validateFontBytes(tooFewTables, "vazirmatn_regular.b64")
        }
        assertTrue(exCount.message!!.contains("تعداد جدول"))

        val missingCmap = buildInMemorySfntFont(
            signature = byteArrayOf(0x00, 0x01, 0x00, 0x00),
            tableTags = listOf("head", "name", "glyf", "maxp"),
            totalSize = VazirmatnFontValidator.MIN_FONT_SIZE_BYTES,
        )
        val exMissing = assertFailsValidation {
            VazirmatnFontValidator.validateFontBytes(missingCmap, "vazirmatn_regular.b64")
        }
        assertTrue(exMissing.message!!.contains("cmap"))

        val missingOutlines = buildInMemorySfntFont(
            signature = byteArrayOf(0x00, 0x01, 0x00, 0x00),
            tableTags = listOf("head", "cmap", "name", "maxp"),
            totalSize = VazirmatnFontValidator.MIN_FONT_SIZE_BYTES,
        )
        val exOutlines = assertFailsValidation {
            VazirmatnFontValidator.validateFontBytes(missingOutlines, "vazirmatn_regular.b64")
        }
        assertTrue(exOutlines.message!!.contains("glyf/CFF"))
    }

    private fun assertFailsValidation(block: () -> Unit): FontValidationException {
        try {
            block()
            fail("Expected FontValidationException to be thrown")
        } catch (e: FontValidationException) {
            return e
        }
        throw AssertionError("Unreachable")
    }

    private fun buildInMemorySfntFont(
        signature: ByteArray,
        tableTags: List<String>,
        totalSize: Int,
    ): ByteArray {
        val bytes = ByteArray(totalSize)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buffer.put(signature)
        buffer.putShort(tableTags.size.toShort())
        buffer.putShort(0) // searchRange
        buffer.putShort(0) // entrySelector
        buffer.putShort(0) // rangeShift

        val dataStart = 12 + tableTags.size * 16
        tableTags.forEachIndexed { index, tag ->
            val recordOffset = 12 + index * 16
            buffer.position(recordOffset)
            val tagBytes = tag.toByteArray(StandardCharsets.US_ASCII)
            require(tagBytes.size == 4) { "SFNT tag must be 4 bytes: '$tag'" }
            buffer.put(tagBytes)
            buffer.putInt(0) // checksum
            buffer.putInt(dataStart + index * 32) // offset inside array bounds
            buffer.putInt(16) // length > 0 and within array bounds
        }
        return bytes
    }
}
