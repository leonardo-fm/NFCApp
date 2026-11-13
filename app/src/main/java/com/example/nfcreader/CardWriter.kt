package com.example.nfcreader

import android.nfc.Tag
import android.nfc.tech.MifareUltralight

/**
 * Scrive una copia della memoria utente su una carta NTAG o MIFARE Ultralight.
 * Scrive solo dalla pagina 4 in poi: UID, pagine di lock e configurazione restano quelle della carta di destinazione.
 * Non scrive su MIFARE Classic né su altri tipi.
 */
object CardWriter {

    /** Prima pagina dopo la memoria utente, dal byte 2 della Capability Container (pagina 3, unità di 8 byte). */
    fun userEndPage(page0to3: ByteArray): Int = 4 + (page0to3[14].toInt() and 0xFF) * 2

    /** Esito da mostrare all'utente. Lancia IOException se la carta si stacca durante la scrittura. */
    fun write(tag: Tag, copy: ByteArray): String {
        if (copy.size < 16) return "La copia è troppo corta."
        val tech = MifareUltralight.get(tag) ?: return "Questa carta non è NTAG o MIFARE Ultralight: non la scrivo."
        val end = minOf(userEndPage(copy), copy.size / 4)
        if (end <= 4) return "La copia non contiene memoria utente."
        return tech.use {
            it.connect()
            if (userEndPage(it.readPages(0)) < end) return@use "La carta è più piccola della copia: non la scrivo."
            for (page in 4 until end) {
                it.writePage(page, copy.copyOfRange(page * 4, page * 4 + 4))
            }
            // ponytail: no retry; if a write is cut off the card is half written, write the same copy again
            for (page in 4 until end step 4) {
                val n = minOf(16, (end - page) * 4)
                val back = it.readPages(page).copyOfRange(0, n)
                if (!back.contentEquals(copy.copyOfRange(page * 4, page * 4 + n))) {
                    return@use "Verifica fallita alla pagina $page: scrivi di nuovo la copia."
                }
            }
            "Copia scritta e verificata: ${(end - 4) * 4} byte dalla pagina 4."
        }
    }
}
