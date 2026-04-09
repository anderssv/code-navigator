package com.example.variants.companion

class UserFactory private constructor(val name: String, val email: String) {
    companion object {
        fun create(name: String, email: String): UserFactory =
            UserFactory(name, email)
    }
}
