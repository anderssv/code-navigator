package com.example.routes

import com.example.domain.UserError
import com.example.services.AuditService
import com.example.services.UserService
import kotlinx.coroutines.delay

class UserRoute(
    private val userService: UserService,
    private val auditService: AuditService,
) {
    // Suspend function — Kotlin compiler generates a continuation class
    // This creates a call chain: UserRoute$handleReset$1.invokeSuspend -> UserService.resetPassword
    suspend fun handleReset(userId: String): String {
        delay(1) // forces coroutine machinery
        val error = userService.resetPassword(userId)
        return formatResponse(error)
    }

    suspend fun handleDeactivate(userId: String): String {
        delay(1)
        auditService.auditUser(userId)
        val error = userService.deactivateUser(userId)
        return formatResponse(error)
    }

    suspend fun handleBatchProcess(userIds: List<String>): String {
        delay(1)
        val errors = userService.processUsers(userIds)
        return if (errors.isEmpty()) "ok" else "errors: ${errors.size}"
    }

    private fun formatResponse(error: UserError?): String =
        if (error == null) "ok" else "error: $error"
}
