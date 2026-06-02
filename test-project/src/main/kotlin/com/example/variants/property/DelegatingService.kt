package com.example.variants.property

class DelegatingService(apiClient: String) {
    private val apiClient = apiClient

    fun call(): String = apiClient
}
