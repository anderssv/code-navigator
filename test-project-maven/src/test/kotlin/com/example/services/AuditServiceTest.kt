package com.example.services

import com.example.infra.InMemoryUserRepository

class AuditServiceTest {
    fun auditsAUser() = AuditService(InMemoryUserRepository()).auditUser("1")
}
