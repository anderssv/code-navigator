// Fixture for --branches: a guard that throws, a when, and a try/catch around the service call.
package com.example.variants.branches

class Repo { fun delete(id: Long) {}; fun archive(id: Long) {} }
class Audit { fun log(message: String) {} }
class Auth { fun canDelete(user: String): Boolean = user == "admin" }
class ForbiddenException : RuntimeException()

class OrderService(private val auth: Auth, private val repo: Repo, private val audit: Audit) {
    fun remove(user: String, id: Long, hard: Boolean) {
        if (!auth.canDelete(user)) {
            throw ForbiddenException()
        }
        when (hard) {
            true -> repo.delete(id)
            false -> repo.archive(id)
        }
        audit.log("removed $id")
    }
}

class Controller(private val service: OrderService) {
    fun remove(user: String, id: Long) {
        try {
            service.remove(user, id, hard = false)
        } catch (e: ForbiddenException) {
            println("forbidden")
        }
    }
}
