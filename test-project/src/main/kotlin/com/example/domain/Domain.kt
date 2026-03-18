package com.example.domain

interface UserRepository {
    fun findById(id: String): User?
    fun save(user: User)
}

interface NotificationSender {
    fun send(userId: String, message: String)
}

data class User(
    val id: String,
    val name: String,
    val email: String,
    val active: Boolean = true,
)

sealed class UserError {
    data class NotFound(val id: String) : UserError()
    data class ValidationFailed(val reason: String) : UserError()
}
