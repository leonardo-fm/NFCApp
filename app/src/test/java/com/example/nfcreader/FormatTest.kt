package com.example.nfcreader

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun hexDumpShowsOffsetHexAndAscii() {
        val dump = hexDump(byteArrayOf(0x48, 0x69, 0x00))
        assertEquals("0000  " + "48 69 00".padEnd(23) + "  |Hi.|", dump)
    }

    @Test
    fun hexDumpStartsANewRowEvery8Bytes() {
        val rows = hexDump(ByteArray(9) { 0x41.toByte() }).lines()
        assertEquals(2, rows.size)
        assertEquals("0008  ", rows[1].take(6))
    }

    @Test
    fun textFormatsReplaceUnprintableBytes() {
        assertEquals("è", utf8Text(byteArrayOf(0xC3.toByte(), 0xA8.toByte())))
        assertEquals("Hi.", asciiText(byteArrayOf(0x48, 0x69, 0x00)))
    }
}
