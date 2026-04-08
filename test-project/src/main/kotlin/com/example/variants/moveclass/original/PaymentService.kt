package com.example.variants.moveclass.original

class PaymentService {
    fun processPayment(amount: Double): String {
        return "Processed: $amount"
    }

    fun refund(amount: Double): String {
        return "Refunded: $amount"
    }
}
