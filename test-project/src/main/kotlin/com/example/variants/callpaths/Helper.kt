// Guard reached through a helper method: guarded at guard-depth 2, not at 1.
package com.example.variants.callpaths.helper

import com.example.variants.callpaths.Auth

class Repo {
    fun delete(id: Long) {}
}

class Controller(private val auth: Auth, private val repo: Repo) {
    fun delete(user: String, id: Long) {
        checkAccess(user)
        repo.delete(id)
    }

    // Calls the guard and the target itself: guarded at any guard-depth >= 1.
    fun deleteDirect(user: String, id: Long) {
        auth.authorize(user)
        repo.delete(id)
    }

    private fun checkAccess(user: String) = auth.authorize(user)
}
