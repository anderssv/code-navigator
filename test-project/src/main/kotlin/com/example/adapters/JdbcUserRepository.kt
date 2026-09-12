package com.example.adapters

import com.example.domain.User
import com.example.domain.UserRepository
import java.sql.Connection

class JdbcUserRepository(private val connection: Connection) : UserRepository {
    override fun findById(id: String): User? {
        val statement = connection.prepareStatement("select id from users where id = ?")
        statement.setString(1, id)
        return if (statement.executeQuery().next()) User(id, "db", "db@example.com") else null
    }

    override fun save(user: User) {
        val statement = connection.prepareStatement("insert into users (id) values (?)")
        statement.setString(1, user.id)
        statement.executeUpdate()
    }
}
