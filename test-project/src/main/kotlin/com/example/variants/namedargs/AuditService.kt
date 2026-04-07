package com.example.variants.namedargs

import com.example.domain.UserRepository

class AuditService(
    private val repository: UserRepository,
) {
    fun auditUser(userId: String): String {
        val user = repository.findById(userId) ?: return "not found"
        return formatAuditEntry(name = user.name, email = user.email)
    }

    private fun formatAuditEntry(name: String, email: String): String =
        "audit: $name <$email>"
}
