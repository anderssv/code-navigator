package com.example.variants.changesig

class UserService {
    fun findUsers(limit: Int, offset: Int): List<String> =
        listOf("user1", "user2").drop(offset).take(limit)

    fun findById(id: String): String = "user-$id"

    suspend fun fetchRemote(url: String, timeout: Int): String = "response"
}

class UserController {
    private val service = UserService()

    fun listUsers(): List<String> = service.findUsers(10, 0)

    fun search(query: String): List<String> = service.findUsers(20, 0)

    suspend fun loadRemote(): String = service.fetchRemote("http://example.com", 5000)
}
