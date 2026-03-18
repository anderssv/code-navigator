package com.example.services

import com.example.domain.NotificationSender
import com.example.domain.User
import com.example.domain.UserError
import com.example.domain.UserRepository

class UserService(
    private val repository: UserRepository,
    private val notificationSender: NotificationSender,
) {
    fun resetPassword(userId: String): UserError? {
        val user = repository.findById(userId)
            ?: return UserError.NotFound(userId)
        val validated = validateResetEligibility(user)
            ?: return UserError.ValidationFailed("not eligible")
        sendResetNotification(validated)
        return null
    }

    fun deactivateUser(userId: String): UserError? {
        val user = repository.findById(userId)
            ?: return UserError.NotFound(userId)
        repository.save(user.copy(active = false))
        sendDeactivationNotification(user)
        return null
    }

    // Private method — will be called from lambda below, generating access$ bridge
    private fun validateResetEligibility(user: User): User? =
        if (user.active) user else null

    private fun sendResetNotification(user: User) {
        val message = buildNotificationMessage(user, "password-reset")
        notificationSender.send(user.id, message)
    }

    private fun sendDeactivationNotification(user: User) {
        val message = buildNotificationMessage(user, "deactivated")
        notificationSender.send(user.id, message)
    }

    private fun buildNotificationMessage(user: User, type: String): String =
        "Dear ${user.name}, your account action: $type"

    // This method uses a lambda that calls a private method — triggers access$ bridge in bytecode
    fun processUsers(userIds: List<String>): List<UserError> =
        userIds.mapNotNull { id ->
            val user = repository.findById(id)
            if (user != null) {
                extractAdditionalInfo(user)
                null
            } else {
                UserError.NotFound(id)
            }
        }

    private fun extractAdditionalInfo(user: User) {
        println("Processing: ${user.name}")
    }
}
