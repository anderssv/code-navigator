package com.example.variants.companion

class UserService {
    fun createUser(name: String, email: String): UserFactory =
        UserFactory.create(name = name, email = email)
}
