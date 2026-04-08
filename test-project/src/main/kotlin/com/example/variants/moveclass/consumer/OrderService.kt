package com.example.variants.moveclass.consumer

import com.example.variants.moveclass.original.PaymentService

class OrderService {
    private val paymentService = PaymentService()

    fun placeOrder(amount: Double): String {
        return paymentService.processPayment(amount)
    }
}
