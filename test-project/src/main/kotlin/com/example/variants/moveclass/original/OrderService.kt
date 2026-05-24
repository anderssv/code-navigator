package com.example.variants.moveclass.original

class OrderService(
    private val payment: PaymentService,
    private val inventory: InventoryService,
    private val notifier: Notifier,
) {
    fun placeOrder(itemId: String, amount: Double): String {
        val stock = inventory.checkStock(itemId)
        if (stock <= 0) return "Out of stock"
        val receipt = payment.processPayment(amount)
        notifier.send("Order placed: $receipt")
        return receipt
    }
}
