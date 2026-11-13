package com.example.nfcreader

// Come mostrare i byte letti dalla carta

/** Esadecimale con spazi, es. "04 A1 FF". "-" se non c'è nulla. */
fun hexOf(bytes: ByteArray?): String = bytes?.joinToString(" ") { "%02X".format(it) } ?: "-"

/** Hexdump: offset, 8 byte in esadecimale, poi il testo ASCII. */
fun hexDump(bytes: ByteArray): String = bytes.asList().chunked(8).mapIndexed { row, chunk ->
    "%04X  %s  |%s|".format(
        row * 8,
        hexOf(chunk.toByteArray()).padEnd(23),
        chunk.map(::printable).joinToString("")
    )
}.joinToString("\n")

/** Solo i caratteri ASCII stampabili; il resto diventa un punto. */
fun asciiText(bytes: ByteArray): String = bytes.map(::printable).joinToString("")

/** Testo UTF-8; i byte non validi diventano il carattere di sostituzione, i controlli un punto. */
fun utf8Text(bytes: ByteArray): String =
    String(bytes, Charsets.UTF_8).map { if (it < ' ' && it != '\n') '.' else it }.joinToString("")

private fun printable(b: Byte): Char = if (b.toInt() in 0x20..0x7E) b.toInt().toChar() else '.'
