package com.example.variants.property

class SearchService {
    private val raClient = "http://example.com"

    fun search(query: String): String = "$raClient/search?q=$query"
}
