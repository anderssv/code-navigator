// A suspend function: the compiler adds an anonymous continuation class (Routes$handle$1) next to it.
package com.example.variants.callpaths.suspending

import kotlinx.coroutines.delay

class Repo {
    fun delete(id: Long) {}
}

class Routes(private val repo: Repo) {
    suspend fun handle(id: Long) {
        delay(1)
        repo.delete(id)
    }
}
