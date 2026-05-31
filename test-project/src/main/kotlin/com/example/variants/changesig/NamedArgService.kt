package com.example.variants.changesig

class NamedArgService {
    fun findUsers(limit: Int, offset: Int): List<String> =
        listOf("user1", "user2").drop(offset).take(limit)
}

class NamedArgCaller {
    private val service = NamedArgService()

    fun search(): List<String> = service.findUsers(limit = 10, offset = 0)
}
