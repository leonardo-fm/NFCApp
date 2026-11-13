package com.example.nfcreader

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val nfc by lazy { NfcAdapter.getDefaultAdapter(this) }
    private val statusText by lazy { findViewById<TextView>(R.id.statusText) }
    private val infoText by lazy { findViewById<TextView>(R.id.infoText) }
    private val copyButton by lazy { findViewById<Button>(R.id.copyButton) }
    private val writeButton by lazy { findViewById<Button>(R.id.writeButton) }
    private val formats by lazy { findViewById<RadioGroup>(R.id.formats) }
    private val memoryText by lazy { findViewById<TextView>(R.id.memoryText) }

    // Copia salvata nell'app, resta anche dopo la chiusura
    private val copyFile by lazy { File(filesDir, "copia_nfc.bin") }

    // Letture e scritture una alla volta, fuori dal thread della grafica
    private val worker = Executors.newSingleThreadExecutor()
    private var busy = false
    private var report: CardReport? = null
    private var copy: ByteArray? = null
    private var writeMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        copy = if (copyFile.exists()) copyFile.readBytes() else null
        formats.setOnCheckedChangeListener { _, _ -> renderMemory() }
        copyButton.setOnClickListener { saveCopy() }
        writeButton.setOnClickListener { toggleWriteMode() }
        onTag(intent)
    }

    override fun onResume() {
        super.onResume()
        render()
        // While the app is open, cards go to it directly, without asking which app to use
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )
        nfc?.enableForegroundDispatch(this, pending, arrayOf(IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)), null)
    }

    override fun onPause() {
        super.onPause()
        nfc?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        onTag(intent)
    }

    private fun onTag(intent: Intent) {
        @Suppress("DEPRECATION")
        val tag: Tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return
        if (writeMode) confirmWrite(tag) else readTag(tag)
    }

    private fun readTag(tag: Tag) {
        busy = true
        render()
        worker.execute {
            val result = try {
                CardReader.read(tag)
            } catch (e: Exception) {
                CardReport("Lettura interrotta (${e.javaClass.simpleName}): tieni la carta ferma e riprova.", null)
            }
            runOnUiThread {
                report = result
                busy = false
                render()
            }
        }
    }

    private fun saveCopy() {
        val bytes = report?.memory ?: return
        copyFile.writeBytes(bytes)
        copy = bytes
        Toast.makeText(this, getString(R.string.copy_saved, bytes.size), Toast.LENGTH_SHORT).show()
        render()
    }

    private fun toggleWriteMode() {
        writeMode = !writeMode
        if (writeMode) Toast.makeText(this, R.string.write_waiting, Toast.LENGTH_LONG).show()
        render()
    }

    // Prima di scrivere si mostra l'UID della carta di destinazione e si chiede conferma
    private fun confirmWrite(tag: Tag) {
        val bytes = copy ?: return
        writeMode = false
        AlertDialog.Builder(this)
            .setTitle(R.string.write_title)
            .setMessage(getString(R.string.write_message, hexOf(tag.id)))
            .setPositiveButton(R.string.write_yes) { _, _ -> writeTag(tag, bytes) }
            .setNegativeButton(R.string.cancel, null)
            .show()
        render()
    }

    private fun writeTag(tag: Tag, bytes: ByteArray) {
        busy = true
        render()
        worker.execute {
            val message = try {
                CardWriter.write(tag, bytes)
            } catch (e: Exception) {
                getString(R.string.write_failed, e.javaClass.simpleName)
            }
            runOnUiThread {
                busy = false
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                render()
            }
        }
    }

    private fun render() {
        val adapter = nfc
        val current = report
        statusText.setText(
            when {
                adapter == null -> R.string.no_nfc
                !adapter.isEnabled -> R.string.nfc_off
                busy -> R.string.busy
                writeMode -> R.string.write_waiting
                current == null -> R.string.waiting
                else -> R.string.card_read
            }
        )
        infoText.text = current?.info ?: ""
        copyButton.isEnabled = !busy && current?.copyable == true
        writeButton.isEnabled = !busy && copy != null
        writeButton.setText(if (writeMode) R.string.cancel_write else R.string.write_copy)
        renderMemory()
    }

    private fun renderMemory() {
        val bytes = report?.memory
        memoryText.text = when {
            bytes == null -> ""
            formats.checkedRadioButtonId == R.id.formatUtf8 -> utf8Text(bytes)
            formats.checkedRadioButtonId == R.id.formatAscii -> asciiText(bytes)
            else -> hexDump(bytes)
        }
    }
}
