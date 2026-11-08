// Plain chain: Controller -> Service -> Repo.
package com.example.variants.callpaths.direct

class Repo {
    fun delete(id: Long) {}
}

class Service(private val repo: Repo) {
    fun delete(id: Long) = repo.delete(id)
}

class Controller(private val service: Service, private val repo: Repo) {
    fun delete(id: Long) = service.delete(id)

    // Reaches the target both directly and through the service: two paths of different length.
    fun deleteTwice(id: Long) {
        repo.delete(id)
        service.delete(id)
    }
}

class Health {
    fun check(): String = "ok"
}
