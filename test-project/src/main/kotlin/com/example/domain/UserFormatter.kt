package com.example.domain

class UserFormatter {
    fun formatDisplayName(user: User): String =
        "${user.name} <${user.email}>"

    fun formatShort(user: User): String =
        user.name
}
