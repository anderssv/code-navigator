package com.example.variants.moveclass.consumer

class ShippingService {
    fun ship(address: String): String {
        return "Shipped to $address"
    }
}
