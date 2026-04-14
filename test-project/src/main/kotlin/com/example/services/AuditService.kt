package com.example.services

import com.example.domain.UserRepository
import com.example.infra.EventSender

class AuditService(
    private val repository: UserRepository,
) {
    private val sender = EventSender.load()

    fun auditUser(userId: String): String {
        val user = repository.findById(userId) ?: return "not found"
        val entry = formatAuditEntry(user.name, user.email)
        sender.send(entry)
        return entry
    }

    private fun formatAuditEntry(name: String, email: String): String =
        "audit: $name <$email>"
}
