package com.electricdreams.numo.feature.settings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.electricdreams.numo.R
import com.electricdreams.numo.core.cashu.CashuWalletManager
import com.electricdreams.numo.core.model.Amount
import com.electricdreams.numo.core.util.MintManager
import com.electricdreams.numo.feature.autowithdraw.AutoWithdrawManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cashudevkit.MeltOptions
import org.cashudevkit.QuoteState
import java.util.Date

/**
 * Activity to confirm and execute a melt (withdraw) operation
 */
class WithdrawMeltQuoteActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WithdrawMeltQuote"
    }

    private lateinit var mintUrl: String
    private lateinit var quoteId: String
    private var amount: Long = 0
    private var feeReserve: Long = 0
    private var invoice: String? = null
    private var lightningAddress: String? = null
    private var request: String = ""
    private lateinit var mintManager: MintManager

    // Views
    private lateinit var backButton: ImageButton
    private lateinit var summaryText: TextView
    private lateinit var destinationText: TextView
    private lateinit var amountText: TextView
    private lateinit var feeText: TextView
    private lateinit var totalText: TextView
    private lateinit var confirmButton: Button
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var loadingText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_withdraw_melt_quote)

        mintUrl = intent.getStringExtra("mint_url") ?: ""
        quoteId = intent.getStringExtra("quote_id") ?: ""
        amount = intent.getLongExtra("amount", 0)
        feeReserve = intent.getLongExtra("fee_reserve", 0)
        invoice = intent.getStringExtra("invoice")
        lightningAddress = intent.getStringExtra("lightning_address")
        request = intent.getStringExtra("request") ?: ""
        mintManager = MintManager.getInstance(this)

        if (mintUrl.isEmpty() || quoteId.isEmpty()) {
            Toast.makeText(
                this,
                getString(R.string.withdraw_melt_error_invalid_data),
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        initViews()
        setupListeners()
        displayQuoteInfo()
    }

    private fun initViews() {
        backButton = findViewById(R.id.back_button)
        summaryText = findViewById(R.id.summary_text)
        destinationText = findViewById(R.id.destination_text)
        amountText = findViewById(R.id.amount_text)
        feeText = findViewById(R.id.fee_text)
        totalText = findViewById(R.id.total_text)
        confirmButton = findViewById(R.id.confirm_button)
        loadingSpinner = findViewById(R.id.loading_spinner)
        loadingText = findViewById(R.id.loading_text)
    }

    private fun setupListeners() {
        backButton.setOnClickListener { finish() }
        confirmButton.setOnClickListener { confirmWithdrawal() }
    }

    private fun displayQuoteInfo() {
        // Display destination
        val destination = when {
            !lightningAddress.isNullOrBlank() -> lightningAddress!!
            !invoice.isNullOrBlank() -> {
                // Abbreviate invoice for display
                if (invoice!!.length > 24) {
                    "${invoice!!.take(12)}...${invoice!!.takeLast(12)}"
                } else {
                    invoice!!
                }
            }
            else -> getString(R.string.withdraw_melt_destination_unknown)
        }
        destinationText.text = destination

        // Display amounts
        val amountObj = Amount(amount, Amount.Currency.BTC)
        val feeObj = Amount(feeReserve, Amount.Currency.BTC)
        val totalObj = Amount(amount + feeReserve, Amount.Currency.BTC)

        amountText.text = amountObj.toString()
        feeText.text = feeObj.toString()
        totalText.text = totalObj.toString()

        // Summary text
        val mintName = mintManager.getMintDisplayName(mintUrl)
        summaryText.text = getString(
            R.string.withdraw_melt_summary,
            mintName
        )
    }

    private fun confirmWithdrawal() {
        setLoading(true)

        lifecycleScope.launch {
            var withdrawEntryId: String? = null
            val autoWithdrawManager = AutoWithdrawManager.getInstance(this@WithdrawMeltQuoteActivity)
            
            try {
                val wallet = CashuWalletManager.getWallet()
                if (wallet == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@WithdrawMeltQuoteActivity,
                            getString(R.string.withdraw_melt_error_wallet_not_initialized),
                            Toast.LENGTH_SHORT
                        ).show()
                        setLoading(false)
                    }
                    return@launch
                }

                // Create unified withdrawal history entry (manual withdrawal)
                val destinationLabel = lightningAddress ?: request
                val destinationType = when {
                    !lightningAddress.isNullOrBlank() -> "manual_address"
                    !request.isBlank() -> "manual_invoice"
                    else -> "manual_unknown"
                }

                val historyEntry = autoWithdrawManager.addManualWithdrawalEntry(
                    mintUrl = mintUrl,
                    amountSats = amount,
                    feeSats = feeReserve,
                    destination = destinationLabel ?: "",
                    destinationType = destinationType,
                    status = com.electricdreams.numo.feature.autowithdraw.WithdrawHistoryEntry.STATUS_PENDING,
                    quoteId = quoteId,
                    errorMessage = null
                )
                withdrawEntryId = historyEntry.id

                // Execute melt operation
                val melted = withContext(Dispatchers.IO) {
                    wallet.meltWithMint(org.cashudevkit.MintUrl(mintUrl), quoteId)
                }

                // Check melt state
                val state = melted.state
                Log.d(TAG, "Melt state after melt: $state")

                // Check melt state
                val meltQuote = withContext(Dispatchers.IO) {
                    wallet.checkMeltQuote(org.cashudevkit.MintUrl(mintUrl), quoteId)
                }

                Log.d(TAG, "Melt state after check: $meltQuote.state")

                withContext(Dispatchers.Main) {
                    setLoading(false)

                    when (meltQuote.state) {
                        QuoteState.PAID  -> {
                            // Update withdrawal entry to completed
                            withdrawEntryId?.let {
                                autoWithdrawManager.updateWithdrawalStatus(
                                    id = it,
                                    status = com.electricdreams.numo.feature.autowithdraw.WithdrawHistoryEntry.STATUS_COMPLETED
                                )
                            }
                            // Show success activity
                            showPaymentSuccess()
                        }
                        QuoteState.UNPAID -> {
                            // Mark as failed and show error
                            withdrawEntryId?.let {
                                autoWithdrawManager.updateWithdrawalStatus(
                                    id = it,
                                    status = com.electricdreams.numo.feature.autowithdraw.WithdrawHistoryEntry.STATUS_FAILED,
                                    errorMessage = getString(R.string.withdraw_melt_error_invoice_not_paid)
                                )
                            }
                            showPaymentError(
                                getString(R.string.withdraw_melt_error_invoice_not_paid)
                            )
                        }
                        QuoteState.PENDING -> {
                            // Keep in history as pending
                            showPaymentError(
                                getString(R.string.withdraw_melt_error_pending)
                            )
                        }
                        else -> {
                            // Mark as failed
                            withdrawEntryId?.let {
                                autoWithdrawManager.updateWithdrawalStatus(
                                    id = it,
                                    status = com.electricdreams.numo.feature.autowithdraw.WithdrawHistoryEntry.STATUS_FAILED,
                                    errorMessage = getString(R.string.withdraw_melt_error_unknown_state)
                                )
                            }
                            showPaymentError(
                                getString(R.string.withdraw_melt_error_unknown_state)
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing melt", e)
                withContext(Dispatchers.Main) {
                    setLoading(false)

                    // Mark as failed on error
                    withdrawEntryId?.let { id ->
                        autoWithdrawManager.updateWithdrawalStatus(
                            id = id,
                            status = com.electricdreams.numo.feature.autowithdraw.WithdrawHistoryEntry.STATUS_FAILED,
                            errorMessage = e.message
                        )
                    }

                    showPaymentError(
                        getString(
                            R.string.withdraw_melt_error_generic,
                            e.message ?: ""
                        )
                    )
                }
            }
        }
    }

    private fun showPaymentSuccess() {
        val intent = Intent(this, WithdrawSuccessActivity::class.java)
        intent.putExtra("amount", amount)
        val destinationLabel = lightningAddress
            ?: getString(R.string.withdraw_melt_destination_invoice_fallback)
        intent.putExtra("destination", destinationLabel)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun showPaymentError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun setLoading(loading: Boolean) {
        loadingSpinner.visibility = if (loading) View.VISIBLE else View.GONE
        loadingText.visibility = if (loading) View.VISIBLE else View.GONE
        confirmButton.isEnabled = !loading
        backButton.isEnabled = !loading
    }
}
