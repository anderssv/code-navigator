package com.example.adapters

object SqlHealthCheck {
    fun isHealthy(url: String): Boolean {
        val connection = java.sql.DriverManager.getConnection(url)
        val closed = connection.isClosed
        connection.close()
        return !closed
    }
}
