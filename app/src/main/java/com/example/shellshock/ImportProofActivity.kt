package com.example.shellshock

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import java.io.IOException

class ImportProofActivity : Activity() {

    private lateinit var etProof: EditText
    private lateinit var btnImport: Button
    private lateinit var nfcAdapter: NfcAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_proof)

        etProof = findViewById(R.id.etProof)
        btnImport = findViewById(R.id.btnImport)

        btnImport.setOnClickListener {
            val proofToken = etProof.text.toString()
            if (proofToken.isNotBlank()) {
                // Store the token and finish this activity
                SatocashWallet.pendingProofToken = proofToken
                Toast.makeText(this, "Proof token saved. Now tap a card in the main screen.", Toast.LENGTH_LONG).show()
                finish()
            } else {
                Toast.makeText(this, "Please enter a Cashu proof token", Toast.LENGTH_SHORT).show()
            }
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        setupNfcForegroundDispatch()
    }

    override fun onPause() {
        super.onPause()
        stopNfcForegroundDispatch()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent?.action) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let {
                val isoDep = IsoDep.get(it)
                if (isoDep != null) {
                    try {
                        isoDep.connect()
                        // Assume the token is passed as a string extra in the NFC intent
                        val proofToken = intent.getStringExtra("PROOF_TOKEN_EXTRA") // This extra needs to be put by the sender
                        if (proofToken != null) {
                            // Authenticate the card (assuming a default PIN or stored PIN)
                            // For simplicity, let's use a hardcoded PIN for now.
                            // In a real app, this would come from user input or secure storage.
                            val satocashWallet = SatocashWallet(SatocashNfcClient(it))
                            satocashWallet.authenticatePIN("0000").thenAccept { authenticated ->
                                if (authenticated) {
                                    satocashWallet.importProofsFromToken(proofToken).thenAccept { importedCount ->
                                        runOnUiThread {
                                            Toast.makeText(this, "Imported $importedCount proofs from NFC.", Toast.LENGTH_LONG).show()
                                            finish()
                                        }
                                    }.exceptionally { e ->
                                        runOnUiThread {
                                            Toast.makeText(this, "Error importing proofs: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                        null
                                    }
                                } else {
                                    runOnUiThread {
                                        Toast.makeText(this, "NFC Authentication failed.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }.exceptionally { e ->
                                runOnUiThread {
                                    Toast.makeText(this, "Error during NFC authentication: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                                null
                            }
                        } else {
                            Toast.makeText(this, "No proof token found in NFC intent.", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: IOException) {
                        Toast.makeText(this, "Error communicating with NFC tag: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        try {
                            isoDep.close()
                        } catch (e: IOException) {
                            // Ignore
                        }
                    }
                }
            }
        }
    }

    private fun setupNfcForegroundDispatch() {
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE)
        val filters = arrayOf(
            android.content.IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        )
        val techLists = arrayOf(arrayOf(IsoDep::class.java.name))
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, filters, techLists)
    }

    private fun stopNfcForegroundDispatch() {
        nfcAdapter.disableForegroundDispatch(this)
    }
}
