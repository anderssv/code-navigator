package com.example.infra

import com.example.domain.User
import com.example.domain.UserRepository

class InMemoryUserRepository : UserRepository {
    private val store = mutableMapOf<String, User>()

    override fun findById(id: String): User? = store[id]

    override fun save(user: User) {
        store[user.id] = user
    }
}
