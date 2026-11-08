// Interface dispatch: one implementor checks access, the other doesn't.
package com.example.variants.callpaths.dispatch

import com.example.variants.callpaths.Auth

class Repo {
    fun delete(id: Long) {}
}

interface Port {
    fun delete(user: String, id: Long)
}

class SafeAdapter(private val auth: Auth, private val repo: Repo) : Port {
    override fun delete(user: String, id: Long) {
        auth.authorize(user)
        repo.delete(id)
    }
}

class UnsafeAdapter(private val repo: Repo) : Port {
    override fun delete(user: String, id: Long) = repo.delete(id)
}

class Controller(private val port: Port) {
    fun delete(user: String, id: Long) = port.delete(user, id)
}
