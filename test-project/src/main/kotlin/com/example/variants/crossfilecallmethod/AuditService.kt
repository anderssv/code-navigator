package com.example.variants.crossfilecallmethod

import com.example.domain.UserRepository

class AuditService(
    private val repository: UserRepository,
) {
    fun auditUser(userId: String): String {
        val user = repository.findById(userId) ?: return "not found"
        return formatAuditEntry(user.name, user.email)
    }

    fun formatAuditEntry(name: String, email: String): String =
        "audit: $name <$email>"
}
