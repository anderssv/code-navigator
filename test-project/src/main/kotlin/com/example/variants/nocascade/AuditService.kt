package com.example.variants.nocascade

import com.example.domain.UserRepository

class AuditService(
    private val repository: UserRepository,
) {
    fun auditUser(userId: String): String {
        val user = repository.findById(userId) ?: return "not found"
        return formatAuditEntry(user.name, user.email)
    }

    fun formatAuditEntry(name: String, email: String): String {
        return buildLine(name, email)
    }

    private fun buildLine(label: String, email: String): String =
        "audit: $label <$email>"
}
