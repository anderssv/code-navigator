package com.example.variants.property

class SearchService {
    private val baseUrl = "http://example.com"

    fun search(query: String): String = "$baseUrl/search?q=$query"
}
