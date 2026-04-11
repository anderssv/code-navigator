package com.example.variants.moveclass.consumer

import com.example.variants.moveclass.original.InventoryService

class WarehouseService(private val inventory: InventoryService) {
    fun fulfill(itemId: String): String {
        val stock = inventory.checkStock(itemId)
        return "Fulfilling $itemId, stock: $stock"
    }
}
