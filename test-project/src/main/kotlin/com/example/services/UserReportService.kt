package com.example.services

import com.example.domain.User
import com.example.domain.UserFormatter
import com.example.domain.UserRepository

class UserReportService(
    private val repository: UserRepository,
    private val formatter: UserFormatter,
) {
    fun generateReport(userIds: List<String>): List<String> {
        val users = userIds.mapNotNull { repository.findById(it) }
        return users.map(formatter::formatDisplayName)
    }

    /**
     * Uses a lambda passed to a non-inline function, which forces
     * INVOKEDYNAMIC generation for the lambda creation.
     * The lambda body calls [UserFormatter.formatShort] from [com.example.domain].
     */
    fun transformUsers(users: List<User>, callback: (List<String>) -> Unit) {
        val result = applyTransform(users) { user ->
            formatter.formatShort(user)
        }
        callback(result)
    }

    // Non-inline function — lambdas passed here cannot be inlined
    private fun applyTransform(users: List<User>, transform: (User) -> String): List<String> =
        users.map { transform(it) }
}
