package com.example.variants.constants

class ApiRoutes {
    fun userEndpoint(): String = "/api/v1/users"
    fun orderEndpoint(): String = "/api/v1/orders"
    fun healthCheck(): String = "/health"
}

class ConfigKeys {
    companion object {
        const val DATABASE_URL = "jdbc:postgresql://localhost:5432/app"
        const val CACHE_TTL = "cache.ttl.seconds"
    }

    fun databaseConfig(): String = DATABASE_URL
    fun cacheConfig(): String = CACHE_TTL
}
