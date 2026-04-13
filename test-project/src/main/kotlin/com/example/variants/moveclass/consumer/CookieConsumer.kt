package com.example.variants.moveclass.consumer

import com.example.variants.moveclass.original.CookieConfig
import com.example.variants.moveclass.original.extractCookie
import com.example.variants.moveclass.original.validateCookie

class CookieConsumer {
    private val config = CookieConfig("session", 3600)

    fun process(): String {
        if (validateCookie(config)) {
            return extractCookie(config.name)
        }
        return "invalid"
    }
}
