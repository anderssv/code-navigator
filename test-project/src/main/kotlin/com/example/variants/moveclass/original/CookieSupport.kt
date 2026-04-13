package com.example.variants.moveclass.original

data class CookieConfig(val name: String, val maxAge: Int)

fun extractCookie(name: String): String {
    return "cookie:$name"
}

fun validateCookie(config: CookieConfig): Boolean {
    return config.maxAge > 0
}
