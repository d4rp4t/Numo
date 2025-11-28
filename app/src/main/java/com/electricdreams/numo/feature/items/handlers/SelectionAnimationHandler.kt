package com.electricdreams.numo.feature.items.handlers

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.NestedScrollView

/**
 * Handles all animations for ItemSelectionActivity.
 * Includes basket section, checkout button, expand/collapse, and quantity animations.
 */
class SelectionAnimationHandler(
    private val basketSection: LinearLayout,
    private val checkoutContainer: CardView
) {
    
    // Views for expand/collapse (set via setExpandCollapseViews)
    private var collapsedView: ConstraintLayout? = null
    private var expandedView: LinearLayout? = null
    private var expandChevron: ImageView? = null
    private var collapseChevron: ImageView? = null
    private var basketScrollView: NestedScrollView? = null
    
    // State
    private var isExpanded: Boolean = false
    
    companion object {
        private const val BASKET_FADE_IN_DURATION = 350L
        private const val BASKET_FADE_OUT_DURATION = 250L
        private const val CHECKOUT_SHOW_DURATION = 400L
        private const val CHECKOUT_HIDE_DURATION = 200L
        private const val QUANTITY_BOUNCE_DURATION = 100L
        private const val EXPAND_COLLAPSE_DURATION = 300L
    }
    
    /**
     * Set the views needed for expand/collapse functionality.
     */
    fun setExpandCollapseViews(
        collapsed: ConstraintLayout,
        expanded: LinearLayout,
        expandChev: ImageView,
        collapseChev: ImageView,
        scrollView: NestedScrollView
    ) {
        collapsedView = collapsed
        expandedView = expanded
        expandChevron = expandChev
        collapseChevron = collapseChev
        basketScrollView = scrollView
        
        // Set initial state (collapsed)
        isExpanded = false
        collapsedView?.visibility = View.VISIBLE
        expandedView?.visibility = View.GONE
    }
    
    /**
     * Check if basket is currently expanded.
     */
    fun isBasketExpanded(): Boolean = isExpanded
    
    /**
     * Toggle between collapsed and expanded states with smooth Apple-like animation.
     */
    fun toggleBasketExpansion() {
        if (isExpanded) {
            collapseBasket()
        } else {
            expandBasket()
        }
    }
    
    /**
     * Expand the basket with smooth spring animation.
     * Apple-style transition: collapsed fades out while expanded fades in with height animation.
     */
    fun expandBasket() {
        if (isExpanded) return
        isExpanded = true
        
        val collapsed = collapsedView ?: return
        val expanded = expandedView ?: return
        
        // Prepare expanded view
        expanded.alpha = 0f
        expanded.visibility = View.VISIBLE
        
        // Animate chevron rotation
        expandChevron?.animate()
            ?.rotation(180f)
            ?.setDuration(EXPAND_COLLAPSE_DURATION)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()
        
        // Create spring-like interpolator for Apple feel
        val springInterpolator = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
        
        // Animate collapsed out
        val collapsedFadeOut = ObjectAnimator.ofFloat(collapsed, View.ALPHA, 1f, 0f)
        collapsedFadeOut.duration = EXPAND_COLLAPSE_DURATION / 2
        
        // Animate expanded in with scale
        expanded.scaleY = 0.95f
        val expandedFadeIn = ObjectAnimator.ofFloat(expanded, View.ALPHA, 0f, 1f)
        val expandedScale = ObjectAnimator.ofFloat(expanded, View.SCALE_Y, 0.95f, 1f)
        
        val expandedAnimSet = AnimatorSet().apply {
            playTogether(expandedFadeIn, expandedScale)
            duration = EXPAND_COLLAPSE_DURATION
            interpolator = springInterpolator
            startDelay = EXPAND_COLLAPSE_DURATION / 3
        }
        
        AnimatorSet().apply {
            play(collapsedFadeOut)
            play(expandedAnimSet)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    collapsed.visibility = View.GONE
                    collapsed.alpha = 1f
                }
            })
            start()
        }
    }
    
    /**
     * Collapse the basket with smooth animation.
     */
    fun collapseBasket() {
        if (!isExpanded) return
        isExpanded = false
        
        val collapsed = collapsedView ?: return
        val expanded = expandedView ?: return
        
        // Prepare collapsed view
        collapsed.alpha = 0f
        collapsed.visibility = View.VISIBLE
        
        // Animate chevron rotation back
        expandChevron?.animate()
            ?.rotation(0f)
            ?.setDuration(EXPAND_COLLAPSE_DURATION)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()
        
        val springInterpolator = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
        
        // Animate expanded out
        val expandedFadeOut = ObjectAnimator.ofFloat(expanded, View.ALPHA, 1f, 0f)
        val expandedScale = ObjectAnimator.ofFloat(expanded, View.SCALE_Y, 1f, 0.95f)
        
        val expandedAnimSet = AnimatorSet().apply {
            playTogether(expandedFadeOut, expandedScale)
            duration = EXPAND_COLLAPSE_DURATION / 2
        }
        
        // Animate collapsed in
        val collapsedFadeIn = ObjectAnimator.ofFloat(collapsed, View.ALPHA, 0f, 1f)
        collapsedFadeIn.duration = EXPAND_COLLAPSE_DURATION
        collapsedFadeIn.interpolator = springInterpolator
        collapsedFadeIn.startDelay = EXPAND_COLLAPSE_DURATION / 4
        
        AnimatorSet().apply {
            play(expandedAnimSet)
            play(collapsedFadeIn)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    expanded.visibility = View.GONE
                    expanded.alpha = 1f
                    expanded.scaleY = 1f
                }
            })
            start()
        }
    }
    
    /**
     * Ensure basket is collapsed when it appears (default state).
     */
    fun resetToCollapsedState() {
        isExpanded = false
        collapsedView?.apply {
            visibility = View.VISIBLE
            alpha = 1f
        }
        expandedView?.apply {
            visibility = View.GONE
            alpha = 1f
            scaleY = 1f
        }
        expandChevron?.rotation = 0f
    }

    /**
     * Smooth fade-in animation for basket section appearing.
     * Uses Apple-like spring interpolation for natural motion.
     */
    fun animateBasketSectionIn() {
        basketSection.visibility = View.VISIBLE
        basketSection.alpha = 0f
        basketSection.translationY = 50f
        basketSection.scaleY = 0.95f

        val fadeIn = ObjectAnimator.ofFloat(basketSection, View.ALPHA, 0f, 1f)
        val slideUp = ObjectAnimator.ofFloat(basketSection, View.TRANSLATION_Y, 50f, 0f)
        val scaleUp = ObjectAnimator.ofFloat(basketSection, View.SCALE_Y, 0.95f, 1f)

        AnimatorSet().apply {
            playTogether(fadeIn, slideUp, scaleUp)
            duration = BASKET_FADE_IN_DURATION
            interpolator = OvershootInterpolator(0.8f)
            start()
        }
    }

    /**
     * Smooth fade-out animation for basket section disappearing.
     * Uses decelerate for natural exit motion.
     */
    fun animateBasketSectionOut() {
        val fadeOut = ObjectAnimator.ofFloat(basketSection, View.ALPHA, 1f, 0f)
        val slideDown = ObjectAnimator.ofFloat(basketSection, View.TRANSLATION_Y, 0f, 30f)
        val scaleDown = ObjectAnimator.ofFloat(basketSection, View.SCALE_Y, 1f, 0.97f)

        AnimatorSet().apply {
            playTogether(fadeOut, slideDown, scaleDown)
            duration = BASKET_FADE_OUT_DURATION
            interpolator = DecelerateInterpolator(1.5f)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    basketSection.visibility = View.GONE
                    basketSection.translationY = 0f
                    basketSection.scaleY = 1f
                }
            })
            start()
        }
    }

    /**
     * Smooth animation for checkout button appearing/disappearing.
     * Apple-style bounce effect on appear.
     */
    fun animateCheckoutButton(show: Boolean) {
        if (show) {
            animateCheckoutIn()
        } else {
            animateCheckoutOut()
        }
    }

    private fun animateCheckoutIn() {
        checkoutContainer.visibility = View.VISIBLE
        checkoutContainer.alpha = 0f
        checkoutContainer.translationY = 80f
        checkoutContainer.scaleX = 0.9f
        checkoutContainer.scaleY = 0.9f

        val fadeIn = ObjectAnimator.ofFloat(checkoutContainer, View.ALPHA, 0f, 1f)
        val slideUp = ObjectAnimator.ofFloat(checkoutContainer, View.TRANSLATION_Y, 80f, 0f)
        val scaleX = ObjectAnimator.ofFloat(checkoutContainer, View.SCALE_X, 0.9f, 1f)
        val scaleY = ObjectAnimator.ofFloat(checkoutContainer, View.SCALE_Y, 0.9f, 1f)

        AnimatorSet().apply {
            playTogether(fadeIn, slideUp, scaleX, scaleY)
            duration = CHECKOUT_SHOW_DURATION
            interpolator = OvershootInterpolator(1.0f)
            start()
        }
    }

    private fun animateCheckoutOut() {
        val fadeOut = ObjectAnimator.ofFloat(checkoutContainer, View.ALPHA, 1f, 0f)
        val slideDown = ObjectAnimator.ofFloat(checkoutContainer, View.TRANSLATION_Y, 0f, 60f)
        val scaleX = ObjectAnimator.ofFloat(checkoutContainer, View.SCALE_X, 1f, 0.95f)
        val scaleY = ObjectAnimator.ofFloat(checkoutContainer, View.SCALE_Y, 1f, 0.95f)

        AnimatorSet().apply {
            playTogether(fadeOut, slideDown, scaleX, scaleY)
            duration = CHECKOUT_HIDE_DURATION
            interpolator = DecelerateInterpolator(1.5f)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    checkoutContainer.visibility = View.GONE
                    checkoutContainer.translationY = 0f
                    checkoutContainer.scaleX = 1f
                    checkoutContainer.scaleY = 1f
                }
            })
            start()
        }
    }

    /**
     * Animate quantity change with a bounce effect.
     */
    fun animateQuantityChange(quantityView: TextView) {
        val scaleAnim = AnimatorSet().apply {
            playSequentially(
                ObjectAnimator.ofFloat(quantityView, "scaleX", 1f, 1.2f).setDuration(QUANTITY_BOUNCE_DURATION),
                ObjectAnimator.ofFloat(quantityView, "scaleX", 1.2f, 1f).setDuration(QUANTITY_BOUNCE_DURATION)
            )
        }
        val scaleAnimY = AnimatorSet().apply {
            playSequentially(
                ObjectAnimator.ofFloat(quantityView, "scaleY", 1f, 1.2f).setDuration(QUANTITY_BOUNCE_DURATION),
                ObjectAnimator.ofFloat(quantityView, "scaleY", 1.2f, 1f).setDuration(QUANTITY_BOUNCE_DURATION)
            )
        }
        AnimatorSet().apply {
            playTogether(scaleAnim, scaleAnimY)
            start()
        }
    }

    // ----- Visibility State Helpers -----

    fun isBasketSectionVisible(): Boolean = basketSection.visibility == View.VISIBLE

    fun isCheckoutContainerVisible(): Boolean = checkoutContainer.visibility == View.VISIBLE
}
