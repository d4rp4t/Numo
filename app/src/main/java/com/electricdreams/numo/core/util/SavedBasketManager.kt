package com.electricdreams.numo.core.util

import android.content.Context
import android.content.SharedPreferences
import com.electricdreams.numo.core.model.BasketItem
import com.electricdreams.numo.core.model.Item
import com.electricdreams.numo.core.model.PriceType
import com.electricdreams.numo.core.model.SavedBasket
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manager class for persisting saved baskets.
 * Uses SharedPreferences with JSON serialization.
 */
class SavedBasketManager private constructor(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val savedBaskets: MutableList<SavedBasket> = mutableListOf()
    
    // Currently editing basket ID (null if creating new)
    var currentEditingBasketId: String? = null
        private set
    
    companion object {
        private const val PREFS_NAME = "saved_baskets"
        private const val KEY_BASKETS = "baskets"
        
        @Volatile
        private var instance: SavedBasketManager? = null
        
        @JvmStatic
        fun getInstance(context: Context): SavedBasketManager {
            return instance ?: synchronized(this) {
                instance ?: SavedBasketManager(context.applicationContext).also {
                    instance = it
                    it.loadBaskets()
                }
            }
        }
    }
    
    /**
     * Load saved baskets from storage.
     */
    private fun loadBaskets() {
        savedBaskets.clear()
        val json = prefs.getString(KEY_BASKETS, null) ?: return
        
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val basketJson = jsonArray.getJSONObject(i)
                savedBaskets.add(deserializeBasket(basketJson))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Save baskets to storage.
     */
    private fun saveBaskets() {
        try {
            val jsonArray = JSONArray()
            savedBaskets.forEach { basket ->
                jsonArray.put(serializeBasket(basket))
            }
            prefs.edit().putString(KEY_BASKETS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Get all saved baskets.
     */
    fun getSavedBaskets(): List<SavedBasket> = savedBaskets.toList()
    
    /**
     * Get a saved basket by ID.
     */
    fun getBasket(id: String): SavedBasket? = savedBaskets.find { it.id == id }
    
    /**
     * Get the index of a basket for display name fallback.
     */
    fun getBasketIndex(id: String): Int = savedBaskets.indexOfFirst { it.id == id }
    
    /**
     * Save current basket from BasketManager.
     * @param name Optional name for the basket
     * @param basketManager The basket manager containing current items
     * @return The saved basket
     */
    fun saveCurrentBasket(name: String?, basketManager: BasketManager): SavedBasket {
        val items = basketManager.getBasketItems().map { it.copy() }
        
        val existingBasket = currentEditingBasketId?.let { getBasket(it) }
        
        return if (existingBasket != null) {
            // Update existing basket
            existingBasket.name = name
            existingBasket.items = items
            existingBasket.updatedAt = System.currentTimeMillis()
            saveBaskets()
            existingBasket
        } else {
            // Create new basket
            val basket = SavedBasket(
                name = name,
                items = items
            )
            savedBaskets.add(basket)
            saveBaskets()
            basket
        }
    }
    
    /**
     * Load a saved basket into BasketManager for editing.
     * @param basketId ID of the basket to load
     * @param basketManager The basket manager to load into
     * @return true if loaded successfully
     */
    fun loadBasketForEditing(basketId: String, basketManager: BasketManager): Boolean {
        val basket = getBasket(basketId) ?: return false
        
        basketManager.clearBasket()
        basket.items.forEach { item ->
            basketManager.addItem(item.item, item.quantity)
        }
        
        currentEditingBasketId = basketId
        return true
    }
    
    /**
     * Clear the current editing state (when starting fresh or after checkout).
     */
    fun clearEditingState() {
        currentEditingBasketId = null
    }
    
    /**
     * Delete a saved basket after successful checkout.
     */
    fun deleteBasket(basketId: String): Boolean {
        val removed = savedBaskets.removeAll { it.id == basketId }
        if (removed) {
            saveBaskets()
            if (currentEditingBasketId == basketId) {
                currentEditingBasketId = null
            }
        }
        return removed
    }
    
    /**
     * Update the name of a saved basket.
     */
    fun updateBasketName(basketId: String, newName: String?): Boolean {
        val basket = getBasket(basketId) ?: return false
        basket.name = newName
        basket.updatedAt = System.currentTimeMillis()
        saveBaskets()
        return true
    }
    
    /**
     * Get total count of saved baskets.
     */
    fun getBasketCount(): Int = savedBaskets.size
    
    /**
     * Check if we're currently editing an existing basket.
     */
    fun isEditingExistingBasket(): Boolean = currentEditingBasketId != null
    
    /**
     * Get the basket currently being edited.
     */
    fun getCurrentEditingBasket(): SavedBasket? = currentEditingBasketId?.let { getBasket(it) }
    
    // ----- JSON Serialization -----
    
    private fun serializeBasket(basket: SavedBasket): JSONObject {
        return JSONObject().apply {
            put("id", basket.id)
            put("name", basket.name ?: JSONObject.NULL)
            put("createdAt", basket.createdAt)
            put("updatedAt", basket.updatedAt)
            put("items", JSONArray().apply {
                basket.items.forEach { item ->
                    put(serializeBasketItem(item))
                }
            })
        }
    }
    
    private fun deserializeBasket(json: JSONObject): SavedBasket {
        val itemsArray = json.getJSONArray("items")
        val items = mutableListOf<BasketItem>()
        for (i in 0 until itemsArray.length()) {
            items.add(deserializeBasketItem(itemsArray.getJSONObject(i)))
        }
        
        return SavedBasket(
            id = json.getString("id"),
            name = if (json.isNull("name")) null else json.getString("name"),
            createdAt = json.getLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
            items = items
        )
    }
    
    private fun serializeBasketItem(basketItem: BasketItem): JSONObject {
        return JSONObject().apply {
            put("quantity", basketItem.quantity)
            put("item", serializeItem(basketItem.item))
        }
    }
    
    private fun deserializeBasketItem(json: JSONObject): BasketItem {
        return BasketItem(
            quantity = json.getInt("quantity"),
            item = deserializeItem(json.getJSONObject("item"))
        )
    }
    
    private fun serializeItem(item: Item): JSONObject {
        return JSONObject().apply {
            put("id", item.id ?: JSONObject.NULL)
            put("uuid", item.uuid)
            put("name", item.name ?: JSONObject.NULL)
            put("variationName", item.variationName ?: JSONObject.NULL)
            put("sku", item.sku ?: JSONObject.NULL)
            put("description", item.description ?: JSONObject.NULL)
            put("category", item.category ?: JSONObject.NULL)
            put("gtin", item.gtin ?: JSONObject.NULL)
            put("price", item.price)
            put("priceSats", item.priceSats)
            put("priceType", item.priceType.name)
            put("priceCurrency", item.priceCurrency)
            put("vatEnabled", item.vatEnabled)
            put("vatRate", item.vatRate)
            put("imagePath", item.imagePath ?: JSONObject.NULL)
        }
    }
    
    private fun deserializeItem(json: JSONObject): Item {
        return Item(
            id = if (json.isNull("id")) null else json.getString("id"),
            uuid = json.optString("uuid", java.util.UUID.randomUUID().toString()),
            name = if (json.isNull("name")) null else json.getString("name"),
            variationName = if (json.isNull("variationName")) null else json.optString("variationName"),
            sku = if (json.isNull("sku")) null else json.optString("sku"),
            description = if (json.isNull("description")) null else json.optString("description"),
            category = if (json.isNull("category")) null else json.optString("category"),
            gtin = if (json.isNull("gtin")) null else json.optString("gtin"),
            price = json.optDouble("price", 0.0),
            priceSats = json.optLong("priceSats", 0L),
            priceType = try { PriceType.valueOf(json.optString("priceType", "FIAT")) } catch (e: Exception) { PriceType.FIAT },
            priceCurrency = json.optString("priceCurrency", "USD"),
            vatEnabled = json.optBoolean("vatEnabled", false),
            vatRate = json.optInt("vatRate", 0),
            imagePath = if (json.isNull("imagePath")) null else json.optString("imagePath")
        )
    }
}
