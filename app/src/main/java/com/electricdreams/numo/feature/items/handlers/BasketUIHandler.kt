package com.electricdreams.numo.feature.items.handlers

import android.view.View
import com.electricdreams.numo.R
import android.widget.Button
import android.widget.TextView
import com.electricdreams.numo.core.model.Amount
import com.electricdreams.numo.core.util.BasketManager
import com.electricdreams.numo.core.util.CurrencyManager

/**
 * Handles basket UI updates including total display and checkout button text.
 * Supports both collapsed and expanded basket views.
 */
class BasketUIHandler(
    private val basketManager: BasketManager,
    private val currencyManager: CurrencyManager,
    private val basketTotalView: TextView,
    private val checkoutButton: Button,
    private val animationHandler: SelectionAnimationHandler,
    private val onBasketUpdated: () -> Unit
) {
    
    // Collapsed view elements (set via setCollapsedViews)
    private var collapsedTotalView: TextView? = null
    private var collapsedItemCountView: TextView? = null
    
    /**
     * Set the collapsed view elements for updating.
     */
    fun setCollapsedViews(totalView: TextView, itemCountView: TextView) {
        collapsedTotalView = totalView
        collapsedItemCountView = itemCountView
    }

    /**
     * Refresh basket UI based on current basket state.
     * Handles visibility animations for basket section and checkout button.
     */
    fun refreshBasket() {
        val basketItems = basketManager.getBasketItems()

        if (basketItems.isEmpty()) {
            // Hide basket section with smooth animation
            if (animationHandler.isBasketSectionVisible()) {
                animationHandler.animateBasketSectionOut()
            }
            // Hide checkout button with animation
            if (animationHandler.isCheckoutContainerVisible()) {
                animationHandler.animateCheckoutButton(false)
            }
        } else {
            // Show basket section with smooth animation
            if (!animationHandler.isBasketSectionVisible()) {
                animationHandler.animateBasketSectionIn()
            }
            // Show checkout button with animation
            if (!animationHandler.isCheckoutContainerVisible()) {
                animationHandler.animateCheckoutButton(true)
            }
            updateCheckoutButton()
        }

        updateBasketTotal()
        onBasketUpdated()
    }

    /**
     * Update the basket total display text.
     * Handles mixed fiat and sats pricing.
     * Updates both expanded and collapsed views.
     */
    fun updateBasketTotal() {
        val itemCount = basketManager.getTotalItemCount()
        val fiatTotal = basketManager.getTotalPrice()
        val satsTotal = basketManager.getTotalSatsDirectPrice()

        val formattedTotal = if (itemCount > 0) {
            val currencyCode = currencyManager.getCurrentCurrency()
            val currency = Amount.Currency.fromCode(currencyCode)

            when {
                fiatTotal > 0 && satsTotal > 0 -> {
                    val fiatAmount = Amount.fromMajorUnits(fiatTotal, currency)
                    val satsAmount = Amount(satsTotal, Amount.Currency.BTC)
                    "$fiatAmount + $satsAmount"
                }
                satsTotal > 0 -> Amount(satsTotal, Amount.Currency.BTC).toString()
                else -> Amount.fromMajorUnits(fiatTotal, currency).toString()
            }
        } else {
            "0.00"
        }

        // Update expanded view total
        basketTotalView.text = formattedTotal
        
        // Update collapsed view total and item count
        collapsedTotalView?.text = formattedTotal
        updateCollapsedItemCount(itemCount)
    }
    
    /**
     * Update the item count text in the collapsed view.
     */
    private fun updateCollapsedItemCount(count: Int) {
        collapsedItemCountView?.let { view ->
            val context = view.context
            view.text = if (count == 1) {
                context.getString(R.string.item_selection_basket_item_count, count)
            } else {
                context.getString(R.string.item_selection_basket_items_count, count)
            }
        }
    }

    /**
     * Update the checkout button text.
     * Total is already visible in the basket, so button just says "Charge".
     */
    fun updateCheckoutButton() {
        checkoutButton.text = checkoutButton.context.getString(R.string.item_selection_charge_button)
    }

    /**
     * Get basket items for adapter updates.
     */
    fun getBasketItems() = basketManager.getBasketItems()
}
