// Guard as a sibling call inside the service, plus an unguarded route next to it.
package com.example.variants.callpaths.guarded

import com.example.variants.callpaths.Auth

class Repo {
    fun delete(id: Long) {}
}

class Service(private val auth: Auth, private val repo: Repo) {
    fun delete(user: String, id: Long) {
        auth.authorize(user)
        repo.delete(id)
    }
}

class Purger(private val repo: Repo) {
    fun purge(id: Long) = repo.delete(id)
}

class Controller(private val service: Service, private val purger: Purger) {
    fun delete(user: String, id: Long) = service.delete(user, id)

    fun deleteOrPurge(user: String, id: Long) {
        service.delete(user, id)
        purger.purge(id)
    }
}
