package com.example.nfcreader

import org.junit.Assert.assertEquals
import org.junit.Test

class CardWriterTest {

    @Test
    fun userAreaEndsWhereTheCapabilityContainerSays() {
        val page3 = ByteArray(16)
        page3[14] = 0x12 // NTAG213: 144 byte di memoria utente, pagine 4-39
        assertEquals(40, CardWriter.userEndPage(page3))
    }
}
