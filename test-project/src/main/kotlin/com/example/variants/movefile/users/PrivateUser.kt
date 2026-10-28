package com.example.variants.movefile.users

// Uses an identifier that is also a PRIVATE function in Texts.kt, which can never be imported.
class PrivateUser(val orderId: String) {
    fun show(): String = "id=$orderId"
}
