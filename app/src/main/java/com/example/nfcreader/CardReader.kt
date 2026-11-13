package com.example.nfcreader

import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Testo con i dati della carta, e la sua memoria grezza (null se non leggibile).
 * copyable: la memoria è di un NTAG / MIFARE Ultralight, quindi si può copiare e riscrivere.
 */
class CardReport(val info: String, val memory: ByteArray?, val copyable: Boolean = false)

object CardReader {

    // Chiavi di fabbrica dei settori MIFARE Classic: solo queste, nessuna ricerca di chiavi
    private val DEFAULT_KEYS = listOf(
        MifareClassic.KEY_DEFAULT,
        MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY,
        MifareClassic.KEY_NFC_FORUM,
        ByteArray(6)
    )

    // ponytail: reads stop at 1 KB; NTAG216 is 924 bytes, so this covers the common cards
    private const val MAX_PAGES = 256

    fun read(tag: Tag): CardReport {
        val info = StringBuilder()
        info.appendLine("UID: ${hexOf(tag.id)}")
        info.appendLine("Tecnologie: ${tag.techList.joinToString { it.substringAfterLast('.') }}")
        describeTechs(tag, info)
        describeRecords(tag, info)
        val (memory, note) = readMemory(tag)
        info.appendLine("Memoria: $note")
        val copyable = memory != null && MifareUltralight.get(tag) != null
        return CardReport(info.toString(), memory, copyable)
    }

    private fun describeTechs(tag: Tag, out: StringBuilder) {
        NfcA.get(tag)?.let {
            out.appendLine("NfcA: ATQA ${hexOf(it.atqa)}, SAK ${"%02X".format(it.sak.toInt())}")
        }
        NfcB.get(tag)?.let {
            out.appendLine("NfcB: app ${hexOf(it.applicationData)}, protocollo ${hexOf(it.protocolInfo)}")
        }
        NfcF.get(tag)?.let {
            out.appendLine("NfcF: sistema ${hexOf(it.systemCode)}, produttore ${hexOf(it.manufacturer)}")
        }
        NfcV.get(tag)?.let {
            out.appendLine("NfcV: DSFID ${"%02X".format(it.dsfId.toInt())}, flag ${"%02X".format(it.responseFlags.toInt())}")
        }
        IsoDep.get(tag)?.let {
            out.appendLine("IsoDep: storici ${hexOf(it.historicalBytes)}, max ${it.maxTransceiveLength} byte")
        }
        MifareClassic.get(tag)?.let {
            out.appendLine("MIFARE Classic: ${it.size} byte, ${it.sectorCount} settori, ${it.blockCount} blocchi")
        }
        Ndef.get(tag)?.let {
            val rw = if (it.isWritable) "scrivibile" else "sola lettura"
            out.appendLine("NDEF: ${it.type}, capacità ${it.maxSize} byte, $rw")
        }
    }

    private fun describeRecords(tag: Tag, out: StringBuilder) {
        try {
            val message = Ndef.get(tag)?.use { it.connect(); it.ndefMessage } ?: return
            message.records.forEachIndexed { i, r -> out.appendLine("Record ${i + 1}: ${describe(r)}") }
        } catch (e: Exception) {
            // un record malformato non deve far perdere la memoria
            out.appendLine("Record NDEF: non leggibili (${e.javaClass.simpleName})")
        }
    }

    private fun describe(r: NdefRecord): String = when {
        r.tnf == NdefRecord.TNF_WELL_KNOWN && r.type.contentEquals(NdefRecord.RTD_TEXT) ->
            "testo \"${ndefText(r.payload)}\""
        r.tnf == NdefRecord.TNF_WELL_KNOWN && r.type.contentEquals(NdefRecord.RTD_URI) ->
            "URI ${r.toUri()}"
        else -> "tipo ${hexOf(r.type)}, dati ${hexOf(r.payload)}"
    }

    // Record di testo NDEF: primo byte = flag (bit 7 UTF-16, bit 0-5 lunghezza lingua), poi lingua, poi testo
    private fun ndefText(payload: ByteArray): String {
        val langLength = payload[0].toInt() and 0x3F
        val charset = if ((payload[0].toInt() and 0x80) == 0) Charsets.UTF_8 else Charsets.UTF_16
        return String(payload, 1 + langLength, payload.size - 1 - langLength, charset)
    }

    private fun readMemory(tag: Tag): Pair<ByteArray?, String> = try {
        val ultralight = MifareUltralight.get(tag)
        val classic = MifareClassic.get(tag)
        when {
            ultralight != null -> readUltralight(ultralight)
            classic != null -> readClassic(classic)
            else -> null to "non letta, questo tipo di carta non è supportato"
        }
    } catch (e: IOException) {
        null to "lettura interrotta, tieni la carta ferma sul telefono"
    }

    // NTAG e MIFARE Ultralight: READ legge 4 pagine (16 byte) alla volta
    private fun readUltralight(tech: MifareUltralight): Pair<ByteArray?, String> = tech.use {
        it.connect()
        val out = ByteArrayOutputStream()
        for (page in 0 until MAX_PAGES step 4) {
            try {
                out.write(tech.readPages(page))
            } catch (e: IOException) {
                if (!tech.isConnected) throw e // persa la carta, non è la fine della memoria
                break // la carta rifiuta la pagina: fine della memoria
                // ponytail: a card that wraps around instead of refusing repeats its first pages
            }
        }
        val bytes = out.toByteArray()
        if (bytes.isEmpty()) null to "nessuna pagina letta"
        else bytes to "MIFARE Ultralight/NTAG, ${bytes.size} byte letti"
    }

    // MIFARE Classic: ogni settore con le chiavi di fabbrica (A, poi B); i settori chiusi restano a zero
    private fun readClassic(tech: MifareClassic): Pair<ByteArray?, String> = tech.use {
        it.connect()
        val memory = ByteArray(tech.blockCount * MifareClassic.BLOCK_SIZE)
        var locked = 0
        for (sector in 0 until tech.sectorCount) {
            val unlocked = DEFAULT_KEYS.any { key ->
                tech.authenticateSectorWithKeyA(sector, key) || tech.authenticateSectorWithKeyB(sector, key)
            }
            if (!unlocked) {
                locked++
                continue
            }
            val first = tech.sectorToBlock(sector)
            for (block in first until first + tech.getBlockCountInSector(sector)) {
                tech.readBlock(block).copyInto(memory, block * MifareClassic.BLOCK_SIZE)
            }
        }
        memory to "MIFARE Classic, ${memory.size} byte letti, $locked settori con chiavi di fabbrica non valide"
    }
}
