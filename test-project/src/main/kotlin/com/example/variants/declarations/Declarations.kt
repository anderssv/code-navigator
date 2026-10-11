package com.example.variants.declarations

/** Fixture for cnavFindDeclaration: KDoc, annotations, nesting, companions, overloads. */
@Deprecated("fixture only")
class OrderService(val repository: String) {
    private val cache = mutableMapOf<String, Int>()
    /** Place an order by id. */
    fun place(orderId: String): Int = cache.getOrPut(orderId) { orderId.length }

    fun place(orderId: String, quantity: Int): Int {
        val base = place(orderId)
        return base * quantity
    }

    class Receipt(val total: Int) {
        fun formatted(): String = "total=$total"
    }

    companion object {
        const val DEFAULT_LIMIT = 10
        fun create(): OrderService = OrderService("in-memory")
    }
}
enum class OrderStatus { NEW, PAID }
typealias OrderId = String
val defaultStatus: OrderStatus = OrderStatus.NEW
fun String.toOrderId(): OrderId = this
fun topLevelHelper(): Int = DEFAULT_LIMIT
private const val DEFAULT_LIMIT = 42
