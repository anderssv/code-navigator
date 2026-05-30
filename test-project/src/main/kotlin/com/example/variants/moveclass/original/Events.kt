package com.example.variants.moveclass.original

sealed class Event {
    data class OrderPlaced(val orderId: String) : Event()
    data class OrderCancelled(val orderId: String, val reason: String) : Event()
}

class EventProcessor {
    fun process(event: Event): String = when (event) {
        is Event.OrderPlaced -> "Processing order ${event.orderId}"
        is Event.OrderCancelled -> "Cancelling order ${event.orderId}: ${event.reason}"
    }
}
