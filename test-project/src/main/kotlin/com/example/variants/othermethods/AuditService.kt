package com.example.variants.othermethods

import com.example.domain.UserRepository

data class AuditEntry(val name: String, val email: String)

class AuditService(
    private val repository: UserRepository,
) {
    fun auditUser(userId: String): String {
        val user = repository.findById(userId) ?: return "not found"
        return formatAuditEntry(user.name, user.email).toString()
    }

    fun formatAuditEntry(name: String, email: String): AuditEntry =
        AuditEntry(name = name, email = email)
}
